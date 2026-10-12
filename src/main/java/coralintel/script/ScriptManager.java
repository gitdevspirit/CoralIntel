package coralintel.script;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import coralintel.module.BooleanSetting;
import coralintel.module.Setting;
import coralintel.module.SliderSetting;
import coralintel.module.TextSetting;
import org.luaj.vm2.LuaValue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Finds, loads, runs and persists scripts.
 *
 * Threading: every script runs on the game's main thread. The only methods that may be called
 * from another thread are the queue* methods (the network thread hands chat and title packets
 * over through them); everything else is main thread only.
 */
public final class ScriptManager {
    static final String EV_ENABLE = "$enable";
    static final String EV_DISABLE = "$disable";
    static final String EV_CHANGE = "$change";

    private static final String[] EVENTS = {"chat", "tick", "world_load", "player_join", "player_leave", "title"};
    private static final String[] EXAMPLES = {"gg_reminder.lua", "fkdr_alert.lua", "game_recap.lua",
            "bed_alert.lua", "win_streak.lua", "game_timer.lua", "lobby_summary.lua", "blacklist_alert.lua"};
    private static final String PREFIX = "&7[&bCoralIntel&7]&r ";
    private static final Pattern SCRIPT_NAME = Pattern.compile("^[A-Za-z0-9_-]{1,40}$");
    private static final Pattern CODES = Pattern.compile("(?i)[&\u00a7][0-9A-FK-OR]");
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOG = Logger.getLogger("CoralIntel");

    private static final int MAX_QUEUE = 512;
    private static final long TICK_BUDGET_NS = 100L * 1000000L;
    private static final int TAB_CHECK_EVERY = 5;   // ticks
    private static final int SAVE_CHECK_EVERY = 40; // ticks
    private static final int MAX_PENDING_CHAT = 30;

    final ScriptHost host;
    final File scriptsDir;
    final File dataDir;
    private final File stateFile;
    private JsonObject state = new JsonObject();
    private boolean stateDirty;

    private final List<ScriptInstance> scripts = new ArrayList<ScriptInstance>();
    private final ConcurrentLinkedQueue<Ev> queue = new ConcurrentLinkedQueue<Ev>();
    private final AtomicInteger queueSize = new AtomicInteger();
    private final Set<String> pendingChanges = new HashSet<String>();
    private final List<String> pendingChat = new ArrayList<String>();
    private Set<String> lastTab;
    private int tickCount;

    private static final class Ev {
        final String name;
        final Object[] args;
        final ScriptModule module; // for $enable / $disable / $change
        final String setting;      // for $change

        Ev(String name, Object[] args, ScriptModule module, String setting) {
            this.name = name;
            this.args = args;
            this.module = module;
            this.setting = setting;
        }
    }

    public ScriptManager(ScriptHost host, File configDir) {
        this.host = host;
        this.scriptsDir = new File(configDir, "scripts");
        this.dataDir = new File(scriptsDir, "data");
        this.stateFile = new File(configDir, "script-state.json");
    }

    // -- Startup / reload -------------------------------------------------------

    /** Loads saved state, installs the bundled examples on the very first run, then loads every script. */
    public void init() {
        loadState();
        if (!scriptsDir.exists()) {
            scriptsDir.mkdirs();
            installExamples();
        }
        loadAll();
    }

    /** Unloads everything, re-scans the folder and loads again (settings and enabled flags are kept). */
    public void reloadAll() {
        for (ScriptInstance s : scripts) s.unload();
        saveState();
        loadAll();
        int running = 0, errors = 0;
        for (ScriptInstance s : scripts) {
            if (s.status == ScriptInstance.Status.RUNNING) running++;
            else if (s.status == ScriptInstance.Status.ERROR) errors++;
        }
        say(PREFIX + "&aReloaded &f" + scripts.size() + " &7script" + (scripts.size() == 1 ? "" : "s")
                + " &8(&a" + running + " running&8" + (errors > 0 ? ", &c" + errors + " with errors" : "") + "&8)");
    }

    private void loadAll() {
        scripts.clear();
        queue.clear();
        queueSize.set(0);
        pendingChanges.clear();

        File[] files = scriptsDir.listFiles();
        List<File> lua = new ArrayList<File>();
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".lua")) lua.add(f);
            }
        }
        Collections.sort(lua, new Comparator<File>() {
            public int compare(File a, File b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });

        Set<String> seen = new HashSet<String>();
        for (File f : lua) {
            String base = f.getName().substring(0, f.getName().length() - 4);
            ScriptInstance s = new ScriptInstance(this, base, f);
            if (!SCRIPT_NAME.matcher(base).matches()) {
                s.status = ScriptInstance.Status.ERROR;
                s.lastError = "invalid file name (use letters, digits, _ and -)";
                scripts.add(s);
                continue;
            }
            if (!seen.add(base.toLowerCase(Locale.ROOT))) continue; // case-only duplicate
            JsonObject saved = scriptState(base, false);
            s.wanted = saved == null || !saved.has("enabled") || saved.get("enabled").getAsBoolean();
            scripts.add(s);
            if (s.wanted) s.load();
        }
        lastTab = null;
    }

    private void installExamples() {
        for (String name : EXAMPLES) {
            InputStream in = ScriptManager.class.getResourceAsStream("/coralintel/scripts/" + name);
            if (in == null) continue;
            OutputStream out = null;
            try {
                out = new FileOutputStream(new File(scriptsDir, name));
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            } catch (Exception e) {
                LOG.warning("Could not install example script " + name + ": " + e);
                continue;
            } finally {
                try { in.close(); } catch (Exception ignored) { }
                if (out != null) try { out.close(); } catch (Exception ignored) { }
            }
            // Examples start switched off; the user turns on the ones they want.
            scriptState(name.substring(0, name.length() - 4), true).addProperty("enabled", false);
        }
        saveState();
    }

    // -- Queries (commands, ClickGUI) --------------------------------------------

    public List<ScriptInstance> getScripts() {
        return Collections.unmodifiableList(scripts);
    }

    public ScriptInstance find(String name) {
        for (ScriptInstance s : scripts) {
            if (s.name.equalsIgnoreCase(name)) return s;
        }
        return null;
    }

    /** Every module registered by a running script, in script order. */
    public List<ScriptModule> getModules() {
        List<ScriptModule> out = new ArrayList<ScriptModule>();
        for (ScriptInstance s : scripts) {
            if (s.status == ScriptInstance.Status.RUNNING) out.addAll(s.modules);
        }
        return out;
    }

    public File getScriptsDir() {
        return scriptsDir;
    }

    /** The on/off switch: turning on (re)loads the script, turning off unloads it. */
    public void setEnabled(ScriptInstance s, boolean on) {
        s.wanted = on;
        scriptState(s.name, true).addProperty("enabled", on);
        if (on) {
            s.load();
        } else {
            s.unload();
            s.status = ScriptInstance.Status.DISABLED;
            s.lastError = "";
        }
        stateDirty = true;
    }

    /** Prints the folder path and tries to open it in the system file manager. */
    public void openFolder() {
        say(PREFIX + "&7Scripts folder: &f" + scriptsDir.getAbsolutePath());
        host.openFolder(scriptsDir);
    }

    // -- Output --------------------------------------------------------------------

    /** Client-side chat line; held back (not lost) while there is no world to show it in. */
    void say(String formatted) {
        if (host.canChat()) {
            host.chat(formatted);
        } else if (pendingChat.size() < MAX_PENDING_CHAT) {
            pendingChat.add(formatted);
        }
    }

    void notifyError(ScriptInstance s) {
        LOG.warning("Script '" + s.name + "' disabled: " + s.lastError);
        say(PREFIX + "&cScript &f" + s.name + " &chas an error and was disabled: &f" + s.lastError
                + " &8(see .scripts)");
    }

    // -- Registry checks ---------------------------------------------------------------

    boolean scriptCommandExists(String name) {
        for (ScriptInstance s : scripts) {
            for (coralintel.command.Command c : s.commands) {
                for (String alias : c.getAliases()) {
                    if (alias.equalsIgnoreCase(name)) return true;
                }
            }
        }
        return false;
    }

    boolean scriptModuleNameTaken(String name) {
        for (ScriptInstance s : scripts) {
            for (ScriptModule m : s.modules) {
                if (m.getName().equalsIgnoreCase(name)) return true;
            }
        }
        return false;
    }

    static boolean isKnownEvent(String name) {
        return Arrays.asList(EVENTS).contains(name);
    }

    // -- Events in (thread-safe) ---------------------------------------------------------

    public void queueChat(String plain, String formatted) {
        enqueue(new Ev("chat", new Object[]{plain, formatted}, null, null));
    }

    public void queueTitle(String kind, String text) {
        enqueue(new Ev("title", new Object[]{kind, text}, null, null));
    }

    /** Main thread. */
    public void queueWorldLoad() {
        lastTab = new HashSet<String>(); // a new world: everyone in it counts as joining
        enqueue(new Ev("world_load", new Object[0], null, null));
    }

    void queueModuleEvent(ScriptModule m, String type) {
        enqueue(new Ev(type, new Object[0], m, null));
    }

    private void enqueue(Ev e) {
        while (queueSize.get() >= MAX_QUEUE && queue.poll() != null) queueSize.decrementAndGet();
        queue.add(e);
        queueSize.incrementAndGet();
    }

    // -- Callbacks from modules (main thread) ------------------------------------------------

    void moduleToggled(ScriptModule m) {
        stateDirty = true;
        queueModuleEvent(m, m.isEnabled() ? EV_ENABLE : EV_DISABLE);
    }

    void settingChanged(ScriptModule m, Setting s) {
        stateDirty = true;
        if (m.onChange == null) return;
        String key = m.getName() + "\u0000" + s.getName();
        if (!pendingChanges.add(key)) return; // a change is already queued; it will read the latest value
        enqueue(new Ev(EV_CHANGE, new Object[0], m, s.getName()));
    }

    void markStateDirty() {
        stateDirty = true;
    }

    // -- Per-tick work (main thread) ------------------------------------------------------------

    public void onTick() {
        tickCount++;

        if (!pendingChat.isEmpty() && host.canChat()) {
            for (String line : new ArrayList<String>(pendingChat)) host.chat(line);
            pendingChat.clear();
        }

        if (tickCount % TAB_CHECK_EVERY == 0) diffTabList();

        long start = System.nanoTime();
        Ev e;
        while ((e = queue.poll()) != null) {
            queueSize.decrementAndGet();
            dispatch(e);
            if (System.nanoTime() - start > TICK_BUDGET_NS) break; // the rest waits for the next tick
        }

        dispatchToListeners("tick", new Object[0]);

        if (tickCount % SAVE_CHECK_EVERY == 0) saveIfDirty();
    }

    private void diffTabList() {
        Set<String> now = host.tabNames();
        if (lastTab != null) {
            for (String n : now) {
                if (!lastTab.contains(n)) enqueue(new Ev("player_join", new Object[]{n}, null, null));
            }
            for (String n : lastTab) {
                if (!now.contains(n)) enqueue(new Ev("player_leave", new Object[]{n}, null, null));
            }
        }
        lastTab = new HashSet<String>(now);
    }

    private void dispatch(Ev e) {
        if (e.module != null) {
            dispatchModuleEvent(e);
            return;
        }
        dispatchToListeners(e.name, e.args);
    }

    private void dispatchModuleEvent(Ev e) {
        ScriptModule m = e.module;
        if (e.name.equals(EV_CHANGE)) pendingChanges.remove(m.getName() + "\u0000" + e.setting);
        ScriptInstance s = m.owner;
        if (s.status != ScriptInstance.Status.RUNNING || !s.modules.contains(m)) return;

        if (e.name.equals(EV_ENABLE)) {
            if (m.onEnable != null && m.isEnabled()) s.invoke(m.onEnable, LuaValue.NONE);
        } else if (e.name.equals(EV_DISABLE)) {
            if (m.onDisable != null && !m.isEnabled()) s.invoke(m.onDisable, LuaValue.NONE);
        } else if (e.name.equals(EV_CHANGE)) {
            Setting setting = m.find(e.setting);
            if (m.onChange != null && setting != null) {
                s.invoke(m.onChange, LuaValue.varargsOf(LuaValue.valueOf(e.setting), m.valueOf(setting)));
            }
        }
    }

    private void dispatchToListeners(String event, Object[] args) {
        for (ScriptInstance s : new ArrayList<ScriptInstance>(scripts)) {
            if (s.status != ScriptInstance.Status.RUNNING) continue;
            for (ScriptInstance.Listener l : new ArrayList<ScriptInstance.Listener>(s.listeners)) {
                if (s.status != ScriptInstance.Status.RUNNING) break;
                if (!l.event.equals(event)) continue;
                if (l.scope != null && !l.scope.isEnabled()) continue;
                LuaValue[] lua = new LuaValue[args.length];
                for (int i = 0; i < args.length; i++) lua[i] = LuaValue.valueOf(String.valueOf(args[i]));
                s.invoke(l.fn, LuaValue.varargsOf(lua));
            }
        }
    }

    /** Draws every running script's HUD labels. */
    public void renderHud() {
        for (ScriptInstance s : scripts) {
            if (s.status != ScriptInstance.Status.RUNNING || s.hud.isEmpty()) continue;
            for (ScriptInstance.HudLabel l : s.hud.values()) host.drawText(l.text, l.x, l.y, l.color);
        }
    }

    // -- Persistence ---------------------------------------------------------------------------------

    private void loadState() {
        if (!stateFile.isFile()) return;
        Reader r = null;
        try {
            r = new InputStreamReader(new FileInputStream(stateFile), UTF8);
            JsonElement root = new JsonParser().parse(r);
            if (root != null && root.isJsonObject()) state = root.getAsJsonObject();
        } catch (Exception e) {
            LOG.warning("Could not read " + stateFile.getName() + ": " + e);
        } finally {
            if (r != null) try { r.close(); } catch (Exception ignored) { }
        }
    }

    private JsonObject child(JsonObject parent, String key, boolean create) {
        JsonElement e = parent.get(key);
        if (e != null && e.isJsonObject()) return e.getAsJsonObject();
        if (!create) return null;
        JsonObject o = new JsonObject();
        parent.add(key, o);
        return o;
    }

    private JsonObject scriptState(String name, boolean create) {
        JsonObject all = child(state, "scripts", create);
        return all == null ? null : child(all, name, create);
    }

    /** Applies saved on/off and setting values to a module that was just registered. */
    void applyModuleState(ScriptInstance inst, ScriptModule mod) {
        JsonObject sc = scriptState(inst.name, false);
        JsonObject mods = sc == null ? null : child(sc, "modules", false);
        JsonObject mo = mods == null ? null : child(mods, mod.getName(), false);
        if (mo == null) return;
        JsonElement en = mo.get("enabled");
        if (en != null && en.isJsonPrimitive() && en.getAsJsonPrimitive().isBoolean()) mod.applyEnabled(en.getAsBoolean());
        JsonObject st = child(mo, "settings", false);
        if (st == null) return;
        for (Setting s : mod.getSettings()) {
            JsonElement saved = st.get(s.getName());
            if (saved != null) mod.loadValue(s, saved);
        }
    }

    /** Copies a script's live module state into the state tree (merging, so entries of modules it hasn't registered yet survive). */
    void captureState(ScriptInstance inst) {
        JsonObject sc = scriptState(inst.name, true);
        sc.addProperty("enabled", inst.wanted);
        if (inst.modules.isEmpty()) return;
        JsonObject mods = child(sc, "modules", true);
        for (ScriptModule m : inst.modules) {
            JsonObject mo = new JsonObject();
            mo.addProperty("enabled", m.isEnabled());
            JsonObject st = new JsonObject();
            for (Setting s : m.getSettings()) {
                if (s instanceof BooleanSetting) st.addProperty(s.getName(), ((BooleanSetting) s).getValue());
                else if (s instanceof SliderSetting) st.addProperty(s.getName(), ((SliderSetting) s).getValue());
                else if (s instanceof TextSetting) st.addProperty(s.getName(), ((TextSetting) s).getValue());
            }
            mo.add("settings", st);
            mods.add(m.getName(), mo);
        }
    }

    public void saveState() {
        for (ScriptInstance s : scripts) {
            if (s.status == ScriptInstance.Status.RUNNING) captureState(s);
        }
        Writer w = null;
        try {
            File parent = stateFile.getParentFile();
            if (parent != null) parent.mkdirs();
            w = new OutputStreamWriter(new FileOutputStream(stateFile), UTF8);
            w.write(GSON.toJson(state));
            stateDirty = false;
        } catch (Exception e) {
            LOG.warning("Could not save " + stateFile.getName() + ": " + e);
        } finally {
            if (w != null) try { w.close(); } catch (Exception ignored) { }
        }
    }

    private void saveIfDirty() {
        if (stateDirty) saveState();
        for (ScriptInstance s : scripts) {
            if (s.dataDirty()) s.saveData();
        }
    }

    /** Called when the game closes. */
    public void shutdown() {
        saveState();
        for (ScriptInstance s : scripts) s.saveData();
    }
}
