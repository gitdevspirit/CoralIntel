package coralintel.script;

import coralintel.command.Command;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** One script file: its Lua environment, what it registered, and its status. Main thread only. */
public final class ScriptInstance {
    public enum Status { RUNNING, DISABLED, ERROR }

    static final long EVENT_BUDGET_MS = 50;
    static final long LOAD_BUDGET_MS = 250;
    static final int MAX_SOURCE_BYTES = 256 * 1024;
    static final int MAX_COMMANDS = 20;
    static final int MAX_MODULES = 8;
    static final int MAX_SETTINGS = 24;
    static final int MAX_LISTENERS = 64;
    static final int MAX_HUD_LABELS = 16;
    static final int MAX_CHAT_PER_SECOND = 10;
    static final int MAX_SOUNDS_PER_SECOND = 5;

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final Pattern COMMAND_NAME = Pattern.compile("^[a-z0-9_-]{1,24}$");

    public final String name;
    final File file;
    final ScriptManager mgr;

    Status status = Status.DISABLED;
    String lastError = "";
    boolean wanted = true; // the saved on/off switch (the ClickGUI toggle)

    ScriptSandbox sandbox;
    private ScriptData data;
    final List<Command> commands = new ArrayList<Command>();
    final List<ScriptModule> modules = new ArrayList<ScriptModule>();
    final List<Listener> listeners = new ArrayList<Listener>();
    final Map<String, HudLabel> hud = new LinkedHashMap<String, HudLabel>();
    private int nextListenerId = 1;

    private long chatWindow, soundWindow;
    private int chatCount, soundCount;

    static final class Listener {
        final int id;
        final String event;
        final LuaValue fn;
        final ScriptModule scope; // null = always; otherwise only while that module is on

        Listener(int id, String event, LuaValue fn, ScriptModule scope) {
            this.id = id;
            this.event = event;
            this.fn = fn;
            this.scope = scope;
        }
    }

    static final class HudLabel {
        String text;
        int x, y, color;
    }

    ScriptInstance(ScriptManager mgr, String name, File file) {
        this.mgr = mgr;
        this.name = name;
        this.file = file;
    }

    // -- Status for the UI and .scripts -------------------------------------

    public boolean isWanted() {
        return wanted;
    }

    public Status getStatus() {
        return status;
    }

    public String getLastError() {
        return lastError;
    }

    public String statusText() {
        switch (status) {
            case RUNNING: return "running";
            case ERROR: return "error: " + lastError;
            default: return "disabled";
        }
    }

    /** ARGB colour for the status line. */
    public int statusColor() {
        switch (status) {
            case RUNNING: return 0xFF55FF55;
            case ERROR: return 0xFFFF5555;
            default: return 0xFF888899;
        }
    }

    // -- Lifecycle ----------------------------------------------------------

    /** Reads, compiles and runs the script's top level. On any failure the script is unloaded and marked ERROR. */
    void load() {
        unload();
        lastError = "";
        try {
            String source = readSource();
            sandbox = new ScriptSandbox();
            ScriptApi.install(this);
            LuaValue chunk = sandbox.compile(source, "@" + file.getName());
            sandbox.call(chunk, LOAD_BUDGET_MS, LuaValue.NONE);
        } catch (Throwable t) {
            fail(t, LOAD_BUDGET_MS);
            return;
        }
        status = Status.RUNNING;
        for (ScriptModule m : new ArrayList<ScriptModule>(modules)) {
            if (m.isEnabled()) mgr.queueModuleEvent(m, ScriptManager.EV_ENABLE);
        }
    }

    /** Removes everything this script registered and drops its environment. Keeps status unless RUNNING. */
    void unload() {
        if (sandbox != null || !modules.isEmpty()) mgr.captureState(this);
        for (Command c : commands) mgr.host.unregisterCommand(c);
        commands.clear();
        modules.clear();
        listeners.clear();
        hud.clear();
        if (data != null) data.save();
        data = null;
        sandbox = null;
        if (status == Status.RUNNING) status = Status.DISABLED;
    }

    /** A script failed: unload only this one, remember why, and tell the player. */
    void fail(Throwable t, long budgetMs) {
        lastError = describe(t, budgetMs);
        unload();
        status = Status.ERROR;
        mgr.notifyError(this);
    }

    /** Calls a script function under the event time budget. Returns false (and disables the script) on any error. */
    boolean invoke(LuaValue fn, Varargs args) {
        if (status != Status.RUNNING || sandbox == null) return false;
        try {
            sandbox.call(fn, EVENT_BUDGET_MS, args);
            return true;
        } catch (Throwable t) {
            fail(t, EVENT_BUDGET_MS);
            return false;
        }
    }

    ScriptData data() {
        if (data == null) data = new ScriptData(new File(mgr.dataDir, name + ".json"));
        return data;
    }

    void saveData() {
        if (data != null) data.save();
    }

    boolean dataDirty() {
        return data != null && data.isDirty();
    }

    // -- Registration (called from the script API during load or later) ---------

    int addListener(String event, LuaValue fn, ScriptModule scope) {
        if (!ScriptManager.isKnownEvent(event)) {
            throw new LuaError("unknown event '" + event + "' (use chat, tick, world_load, player_join, player_leave, title)");
        }
        if (listeners.size() >= MAX_LISTENERS) throw new LuaError("too many event listeners (max " + MAX_LISTENERS + ")");
        Listener l = new Listener(nextListenerId++, event, fn, scope);
        listeners.add(l);
        return l.id;
    }

    boolean removeListener(int id) {
        for (int i = 0; i < listeners.size(); i++) {
            if (listeners.get(i).id == id) {
                listeners.remove(i);
                return true;
            }
        }
        return false;
    }

    void registerCommand(String rawName, final LuaValue fn, String description) {
        final String cmdName = rawName.toLowerCase(Locale.ROOT);
        if (!COMMAND_NAME.matcher(cmdName).matches()) {
            throw new LuaError("command name must be 1-24 characters: a-z, 0-9, _ or -");
        }
        if (commands.size() >= MAX_COMMANDS) throw new LuaError("too many commands (max " + MAX_COMMANDS + ")");
        if (mgr.host.commandExists(cmdName) || mgr.scriptCommandExists(cmdName)) {
            throw new LuaError("command '" + cmdName + "' already exists");
        }
        final ScriptInstance self = this;
        Command c = new Command(cmdName) {
            {
                setDescription("[script " + self.name + "] " + (description.isEmpty() ? "Script command." : description));
            }

            @Override
            public void execute(String[] args) {
                LuaTable t = new LuaTable();
                for (int i = 0; i < args.length; i++) t.set(i + 1, LuaValue.valueOf(args[i]));
                self.invoke(fn, t);
            }
        };
        commands.add(c);
        mgr.host.registerCommand(c);
    }

    ScriptModule registerModule(String moduleName, String description, boolean enabled) {
        if (moduleName.isEmpty() || moduleName.length() > 32) throw new LuaError("module name must be 1-32 characters");
        if (modules.size() >= MAX_MODULES) throw new LuaError("too many modules (max " + MAX_MODULES + ")");
        if (mgr.host.moduleNameTaken(moduleName) || mgr.scriptModuleNameTaken(moduleName)) {
            throw new LuaError("a module named '" + moduleName + "' already exists");
        }
        ScriptModule m = new ScriptModule(this, moduleName, description, enabled);
        modules.add(m);
        return m;
    }

    // -- Output limits -------------------------------------------------------

    boolean allowChat() {
        long now = System.currentTimeMillis();
        if (now - chatWindow >= 1000) {
            chatWindow = now;
            chatCount = 0;
        }
        chatCount++;
        if (chatCount == MAX_CHAT_PER_SECOND + 1) {
            mgr.say("&7[&b" + name + "&7] &echat output limited to " + MAX_CHAT_PER_SECOND + " lines per second");
        }
        return chatCount <= MAX_CHAT_PER_SECOND;
    }

    boolean allowSound() {
        long now = System.currentTimeMillis();
        if (now - soundWindow >= 1000) {
            soundWindow = now;
            soundCount = 0;
        }
        return ++soundCount <= MAX_SOUNDS_PER_SECOND;
    }

    // -- Helpers ---------------------------------------------------------------

    private String readSource() throws Exception {
        if (file.length() > MAX_SOURCE_BYTES) throw new LuaError("script file is larger than " + (MAX_SOURCE_BYTES / 1024) + " KB");
        InputStream in = new FileInputStream(file);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            String s = new String(out.toByteArray(), UTF8);
            if (s.startsWith("\uFEFF")) s = s.substring(1);
            if (s.startsWith("\u001b")) throw new LuaError("precompiled Lua chunks are not allowed");
            return s;
        } finally {
            in.close();
        }
    }

    static String describe(Throwable t, long budgetMs) {
        if (t instanceof ScriptSandbox.Timeout) return "exceeded its time limit (" + budgetMs + " ms)";
        if (t instanceof StackOverflowError) return "stack overflow (recursion too deep)";
        if (t instanceof OutOfMemoryError) return "ran out of memory";
        String msg = t.getMessage();
        if (msg == null || msg.isEmpty()) msg = t.getClass().getSimpleName();
        int nl = msg.indexOf('\n'); // LuaError appends a traceback
        if (nl >= 0) msg = msg.substring(0, nl);
        return msg.trim();
    }
}
