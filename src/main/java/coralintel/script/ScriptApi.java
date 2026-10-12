package coralintel.script;

import coralintel.module.Setting;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The Lua-facing API. Everything a script can do to the outside world is built here: there is no
 * function for sending chat or commands to the server, touching files, or reaching the network,
 * so scripts simply cannot. Values handed out are copies; scripts never hold a game object.
 */
final class ScriptApi {
    private static final Pattern SOUND_NAME = Pattern.compile("^[a-z0-9_.]{1,64}$");
    private static final int MAX_CHAT_LENGTH = 256;
    private static final int MAX_HUD_TEXT = 200;

    private interface Fn {
        Varargs call(Varargs args);
    }

    private static LuaValue fn(final Fn f) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return f.call(args);
            }
        };
    }

    private ScriptApi() {
    }

    static void install(final ScriptInstance inst) {
        Globals g = inst.sandbox.globals();
        final ScriptManager mgr = inst.mgr;
        final ScriptHost host = mgr.host;

        // -- command ----------------------------------------------------------
        LuaTable command = new LuaTable();
        command.set("register", fn(new Fn() {
            public Varargs call(Varargs a) {
                inst.registerCommand(a.checkjstring(1), a.checkfunction(2), a.optjstring(3, ""));
                return LuaValue.NONE;
            }
        }));
        g.set("command", command);

        // -- module -----------------------------------------------------------
        LuaTable module = new LuaTable();
        module.set("register", fn(new Fn() {
            public Varargs call(Varargs a) {
                return registerModule(inst, a.checktable(1));
            }
        }));
        g.set("module", module);

        // -- events -----------------------------------------------------------
        LuaTable events = new LuaTable();
        events.set("on", fn(new Fn() {
            public Varargs call(Varargs a) {
                return LuaValue.valueOf(inst.addListener(a.checkjstring(1), a.checkfunction(2), null));
            }
        }));
        events.set("off", fn(new Fn() {
            public Varargs call(Varargs a) {
                return LuaValue.valueOf(inst.removeListener(a.checkint(1)));
            }
        }));
        g.set("events", events);

        // -- chat / print -----------------------------------------------------
        LuaTable chat = new LuaTable();
        chat.set("print", fn(new Fn() {
            public Varargs call(Varargs a) {
                chatPrint(inst, a.checkjstring(1));
                return LuaValue.NONE;
            }
        }));
        g.set("chat", chat);
        g.set("print", fn(new Fn() {
            public Varargs call(Varargs a) {
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i <= a.narg(); i++) {
                    if (i > 1) sb.append(' ');
                    sb.append(a.arg(i).tojstring());
                }
                chatPrint(inst, "&8[" + inst.name + "] &7" + sb);
                return LuaValue.NONE;
            }
        }));

        // -- sound ------------------------------------------------------------
        LuaTable sound = new LuaTable();
        sound.set("play", fn(new Fn() {
            public Varargs call(Varargs a) {
                String n = a.checkjstring(1);
                if (!SOUND_NAME.matcher(n).matches()) throw new LuaError("sound.play: invalid sound name");
                if (inst.allowSound()) host.sound(n);
                return LuaValue.NONE;
            }
        }));
        g.set("sound", sound);

        // -- hud --------------------------------------------------------------
        LuaTable hud = new LuaTable();
        hud.set("text", fn(new Fn() {
            public Varargs call(Varargs a) {
                String id = a.checkjstring(1);
                String text = a.checkjstring(2);
                if (id.isEmpty() || id.length() > 32) throw new LuaError("hud.text: id must be 1-32 characters");
                if (text.length() > MAX_HUD_TEXT) text = text.substring(0, MAX_HUD_TEXT);
                int x = a.checkint(3);
                int y = a.checkint(4);
                int color = 0xFFFFFFFF;
                if (!a.isnil(5)) {
                    color = (int) (long) a.checkdouble(5);
                    if ((color & 0xFF000000) == 0) color |= 0xFF000000; // 0xRRGGBB means opaque
                }
                ScriptInstance.HudLabel label = inst.hud.get(id);
                if (label == null) {
                    if (inst.hud.size() >= ScriptInstance.MAX_HUD_LABELS) {
                        throw new LuaError("hud.text: too many labels (max " + ScriptInstance.MAX_HUD_LABELS + ")");
                    }
                    label = new ScriptInstance.HudLabel();
                    inst.hud.put(id, label);
                }
                label.text = text;
                label.x = x;
                label.y = y;
                label.color = color;
                return LuaValue.NONE;
            }
        }));
        hud.set("remove", fn(new Fn() {
            public Varargs call(Varargs a) {
                inst.hud.remove(a.checkjstring(1));
                return LuaValue.NONE;
            }
        }));
        hud.set("clear", fn(new Fn() {
            public Varargs call(Varargs a) {
                inst.hud.clear();
                return LuaValue.NONE;
            }
        }));
        g.set("hud", hud);

        // -- session ----------------------------------------------------------
        LuaTable session = new LuaTable();
        session.set("get", fn(new Fn() {
            public Varargs call(Varargs a) {
                return toLua(host.session());
            }
        }));
        g.set("session", session);

        // -- lobby ------------------------------------------------------------
        LuaTable lobby = new LuaTable();
        lobby.set("players", fn(new Fn() {
            public Varargs call(Varargs a) {
                return toLua(host.lobbyPlayers());
            }
        }));
        lobby.set("player", fn(new Fn() {
            public Varargs call(Varargs a) {
                return toLua(host.lobbyPlayer(a.checkjstring(1)));
            }
        }));
        lobby.set("suspicion", fn(new Fn() {
            public Varargs call(Varargs a) {
                return toLua(host.suspicion(a.checkjstring(1)));
            }
        }));
        lobby.set("me", fn(new Fn() {
            public Varargs call(Varargs a) {
                return toLua(host.ownName());
            }
        }));
        g.set("lobby", lobby);

        // -- data -------------------------------------------------------------
        LuaTable data = new LuaTable();
        data.set("get", fn(new Fn() {
            public Varargs call(Varargs a) {
                return inst.data().get(a.checkjstring(1), a.arg(2));
            }
        }));
        data.set("set", fn(new Fn() {
            public Varargs call(Varargs a) {
                inst.data().set(a.checkjstring(1), a.arg(2));
                return LuaValue.NONE;
            }
        }));
        data.set("delete", fn(new Fn() {
            public Varargs call(Varargs a) {
                return LuaValue.valueOf(inst.data().delete(a.checkjstring(1)));
            }
        }));
        data.set("keys", fn(new Fn() {
            public Varargs call(Varargs a) {
                return inst.data().keys();
            }
        }));
        g.set("data", data);

        // -- os (clock and date only) ------------------------------------------
        LuaTable os = new LuaTable();
        os.set("time", fn(new Fn() {
            public Varargs call(Varargs a) {
                return LuaValue.valueOf(System.currentTimeMillis() / 1000L);
            }
        }));
        os.set("clock", fn(new Fn() {
            public Varargs call(Varargs a) {
                return LuaValue.valueOf(System.nanoTime() / 1.0e9);
            }
        }));
        os.set("date", fn(new Fn() {
            public Varargs call(Varargs a) {
                return LuaValue.valueOf(formatDate(a.optjstring(1, "%Y-%m-%d %H:%M:%S")));
            }
        }));
        g.set("os", os);
    }

    // -- module.register ------------------------------------------------------------

    private static LuaValue registerModule(final ScriptInstance inst, LuaTable t) {
        String name = str(t, "name", null);
        String description = str(t, "description", "");
        boolean enabled = t.get("enabled").optboolean(false);
        final ScriptModule mod = inst.registerModule(name, description, enabled);

        LuaValue settings = t.get("settings");
        if (!settings.isnil()) {
            if (!settings.istable()) throw new LuaError("module.register: 'settings' must be a list");
            LuaTable list = settings.checktable();
            int n = list.length();
            if (n > ScriptInstance.MAX_SETTINGS) {
                throw new LuaError("module.register: too many settings (max " + ScriptInstance.MAX_SETTINGS + ")");
            }
            Set<String> seen = new HashSet<String>();
            for (int i = 1; i <= n; i++) {
                LuaValue entry = list.get(i);
                if (!entry.istable()) throw new LuaError("module.register: setting #" + i + " must be a table");
                addSetting(mod, entry.checktable(), seen);
            }
        }

        inst.mgr.applyModuleState(inst, mod);

        LuaTable h = new LuaTable();
        h.set("get", fn(new Fn() {
            public Varargs call(Varargs a) {
                Setting s = mod.find(a.checkjstring(1));
                if (s == null) throw new LuaError("unknown setting '" + a.checkjstring(1) + "'");
                return mod.valueOf(s);
            }
        }));
        h.set("set", fn(new Fn() {
            public Varargs call(Varargs a) {
                mod.setFromScript(a.checkjstring(1), a.arg(2));
                return LuaValue.NONE;
            }
        }));
        h.set("enabled", fn(new Fn() {
            public Varargs call(Varargs a) {
                return LuaValue.valueOf(mod.isEnabled());
            }
        }));
        h.set("onEnable", fn(new Fn() {
            public Varargs call(Varargs a) {
                mod.onEnable = a.checkfunction(1);
                return LuaValue.NONE;
            }
        }));
        h.set("onDisable", fn(new Fn() {
            public Varargs call(Varargs a) {
                mod.onDisable = a.checkfunction(1);
                return LuaValue.NONE;
            }
        }));
        h.set("onChange", fn(new Fn() {
            public Varargs call(Varargs a) {
                mod.onChange = a.checkfunction(1);
                return LuaValue.NONE;
            }
        }));
        h.set("on", fn(new Fn() {
            public Varargs call(Varargs a) {
                return LuaValue.valueOf(inst.addListener(a.checkjstring(1), a.checkfunction(2), mod));
            }
        }));
        return h;
    }

    private static void addSetting(ScriptModule mod, LuaTable s, Set<String> seen) {
        String type = str(s, "type", null);
        String name = str(s, "name", null);
        if (name.isEmpty() || name.length() > 32) throw new LuaError("module.register: setting name must be 1-32 characters");
        if (!seen.add(name)) throw new LuaError("module.register: duplicate setting '" + name + "'");

        if (type.equals("toggle")) {
            mod.addToggle(name, s.get("default").optboolean(false));
        } else if (type.equals("slider")) {
            double min = num(s, "min");
            double max = num(s, "max");
            double step = num(s, "step");
            if (!(max > min)) throw new LuaError("module.register: slider '" + name + "' needs max > min");
            if (!(step > 0)) throw new LuaError("module.register: slider '" + name + "' needs step > 0");
            LuaValue d = s.get("default");
            mod.addSlider(name, d.isnil() ? min : d.checkdouble(), min, max, step);
        } else if (type.equals("text")) {
            mod.addText(name, str(s, "default", ""));
        } else {
            throw new LuaError("module.register: setting type must be toggle, slider or text (got '" + type + "')");
        }
    }

    private static String str(LuaTable t, String key, String fallback) {
        LuaValue v = t.get(key);
        if (v.isnil()) {
            if (fallback == null) throw new LuaError("module.register: '" + key + "' is required");
            return fallback;
        }
        if (v.type() != LuaValue.TSTRING) throw new LuaError("module.register: '" + key + "' must be a string");
        return v.tojstring();
    }

    private static double num(LuaTable t, String key) {
        LuaValue v = t.get(key);
        if (!v.isnumber()) throw new LuaError("module.register: slider '" + key + "' must be a number");
        return v.todouble();
    }

    // -- helpers ------------------------------------------------------------------

    private static void chatPrint(ScriptInstance inst, String msg) {
        msg = msg.replace('\n', ' ').replace('\r', ' ');
        if (msg.length() > MAX_CHAT_LENGTH) msg = msg.substring(0, MAX_CHAT_LENGTH);
        if (inst.allowChat()) inst.mgr.say(msg);
    }

    private static String formatDate(String format) {
        StringBuilder out = new StringBuilder();
        Date now = new Date();
        for (int i = 0; i < format.length(); i++) {
            char c = format.charAt(i);
            if (c != '%' || i + 1 >= format.length()) {
                out.append(c);
                continue;
            }
            char f = format.charAt(++i);
            String pattern;
            switch (f) {
                case 'Y': pattern = "yyyy"; break;
                case 'y': pattern = "yy"; break;
                case 'm': pattern = "MM"; break;
                case 'd': pattern = "dd"; break;
                case 'H': pattern = "HH"; break;
                case 'M': pattern = "mm"; break;
                case 'S': pattern = "ss"; break;
                case '%': out.append('%'); continue;
                default: out.append('%').append(f); continue;
            }
            out.append(new SimpleDateFormat(pattern, Locale.ROOT).format(now));
        }
        return out.toString();
    }

    /** Plain Java data (Map / List / String / Number / Boolean) to Lua. */
    @SuppressWarnings("unchecked")
    static LuaValue toLua(Object o) {
        if (o == null) return LuaValue.NIL;
        if (o instanceof String) return LuaValue.valueOf((String) o);
        if (o instanceof Boolean) return LuaValue.valueOf((Boolean) o);
        if (o instanceof Integer) return LuaValue.valueOf(((Integer) o).intValue());
        if (o instanceof Number) return LuaValue.valueOf(((Number) o).doubleValue());
        if (o instanceof Map) {
            LuaTable t = new LuaTable();
            for (Map.Entry<String, Object> e : ((Map<String, Object>) o).entrySet()) {
                if (e.getValue() != null) t.set(e.getKey(), toLua(e.getValue()));
            }
            return t;
        }
        if (o instanceof Collection) {
            LuaTable t = new LuaTable();
            int i = 1;
            for (Object item : (Collection<Object>) o) t.set(i++, toLua(item));
            return t;
        }
        return LuaValue.valueOf(String.valueOf(o));
    }
}
