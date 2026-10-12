package coralintel.script;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A script's own saved data: one small JSON file that only this class ever opens. The script
 * picks keys and values, never paths. Strings, numbers, booleans and nested tables of those.
 */
final class ScriptData {
    static final int MAX_KEYS = 256;
    static final int MAX_BYTES = 64 * 1024;
    static final int MAX_KEY_LENGTH = 64;
    static final int MAX_DEPTH = 4;

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final File file;
    private final Map<String, JsonElement> map = new LinkedHashMap<String, JsonElement>();
    private boolean dirty;

    ScriptData(File file) {
        this.file = file;
        load();
    }

    private void load() {
        if (!file.isFile()) return;
        Reader reader = null;
        try {
            reader = new InputStreamReader(new FileInputStream(file), UTF8);
            JsonElement root = new JsonParser().parse(reader);
            if (root != null && root.isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject().entrySet()) {
                    map.put(e.getKey(), e.getValue());
                }
            }
        } catch (Exception ignored) {
            // unreadable data file: start empty
        } finally {
            if (reader != null) try { reader.close(); } catch (Exception ignored) { }
        }
    }

    LuaValue get(String key, LuaValue fallback) {
        JsonElement e = map.get(key);
        return e == null ? fallback : fromJson(e);
    }

    void set(String key, LuaValue value) {
        checkKey(key);
        if (value.isnil()) {
            delete(key);
            return;
        }
        JsonElement json = toJson(value, 0);
        if (!map.containsKey(key) && map.size() >= MAX_KEYS) {
            throw new LuaError("data: too many keys (max " + MAX_KEYS + ")");
        }
        JsonElement old = map.put(key, json);
        if (serialize().length() > MAX_BYTES) {
            if (old == null) map.remove(key); else map.put(key, old);
            throw new LuaError("data: too large (max " + (MAX_BYTES / 1024) + " KB)");
        }
        dirty = true;
    }

    boolean delete(String key) {
        boolean had = map.remove(key) != null;
        if (had) dirty = true;
        return had;
    }

    LuaTable keys() {
        LuaTable t = new LuaTable();
        int i = 1;
        for (String k : map.keySet()) t.set(i++, LuaValue.valueOf(k));
        return t;
    }

    boolean isDirty() {
        return dirty;
    }

    void save() {
        if (!dirty) return;
        Writer writer = null;
        try {
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            writer = new OutputStreamWriter(new FileOutputStream(file), UTF8);
            writer.write(serialize());
            dirty = false;
        } catch (Exception ignored) {
            // best effort; stays dirty and is retried
        } finally {
            if (writer != null) try { writer.close(); } catch (Exception ignored) { }
        }
    }

    private String serialize() {
        JsonObject o = new JsonObject();
        for (Map.Entry<String, JsonElement> e : map.entrySet()) o.add(e.getKey(), e.getValue());
        return GSON.toJson(o);
    }

    private static void checkKey(String key) {
        if (key.isEmpty() || key.length() > MAX_KEY_LENGTH) {
            throw new LuaError("data: key must be 1-" + MAX_KEY_LENGTH + " characters");
        }
    }

    private static JsonElement toJson(LuaValue v, int depth) {
        switch (v.type()) {
            case LuaValue.TSTRING:
                return new JsonPrimitive(v.tojstring());
            case LuaValue.TBOOLEAN:
                return new JsonPrimitive(v.toboolean());
            case LuaValue.TNUMBER: {
                double d = v.todouble();
                if (Double.isNaN(d) || Double.isInfinite(d)) throw new LuaError("data: number is not finite");
                return v.isint() ? new JsonPrimitive(v.toint()) : new JsonPrimitive(d);
            }
            case LuaValue.TTABLE: {
                if (depth >= MAX_DEPTH) throw new LuaError("data: tables nested too deep (max " + MAX_DEPTH + ")");
                LuaTable t = v.checktable();
                int n = t.length();
                int count = 0;
                boolean array = true;
                LuaValue k = LuaValue.NIL;
                while (true) {
                    Varargs next = t.next(k);
                    k = next.arg1();
                    if (k.isnil()) break;
                    count++;
                    if (!(k.isint() && k.toint() >= 1 && k.toint() <= n)) array = false;
                }
                if (array && count == n) {
                    JsonArray arr = new JsonArray();
                    for (int i = 1; i <= n; i++) arr.add(toJson(t.get(i), depth + 1));
                    return arr;
                }
                JsonObject obj = new JsonObject();
                k = LuaValue.NIL;
                while (true) {
                    Varargs next = t.next(k);
                    k = next.arg1();
                    if (k.isnil()) break;
                    obj.add(k.tojstring(), toJson(next.arg(2), depth + 1));
                }
                return obj;
            }
            default:
                throw new LuaError("data: can't store a " + v.typename());
        }
    }

    private static LuaValue fromJson(JsonElement e) {
        if (e == null || e instanceof JsonNull) return LuaValue.NIL;
        if (e.isJsonPrimitive()) {
            JsonPrimitive p = e.getAsJsonPrimitive();
            if (p.isBoolean()) return LuaValue.valueOf(p.getAsBoolean());
            if (p.isNumber()) return LuaValue.valueOf(p.getAsDouble());
            return LuaValue.valueOf(p.getAsString());
        }
        if (e.isJsonArray()) {
            LuaTable t = new LuaTable();
            int i = 1;
            for (JsonElement item : e.getAsJsonArray()) t.set(i++, fromJson(item));
            return t;
        }
        LuaTable t = new LuaTable();
        for (Map.Entry<String, JsonElement> en : e.getAsJsonObject().entrySet()) {
            t.set(LuaValue.valueOf(en.getKey()), fromJson(en.getValue()));
        }
        return t;
    }
}
