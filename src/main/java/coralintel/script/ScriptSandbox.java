package coralintel.script;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LoadState;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.BaseLib;
import org.luaj.vm2.lib.DebugLib;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.PackageLib;
import org.luaj.vm2.lib.StringLib;
import org.luaj.vm2.lib.TableLib;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.JseMathLib;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.IllegalFormatException;
import java.util.Locale;

/**
 * One locked-down Lua environment.
 *
 * Capability-based: a script can only reach what is put into its globals. No Java bridge
 * (luajava) is loaded, so there is no file, network or reflection access to take away. On top of
 * that:
 *  - the stock globals that load code or touch the system are removed (dofile, loadfile, load,
 *    require, package, io, os, debug, coroutine, collectgarbage, ...);
 *  - a bytecode-count hook aborts any call that runs past its time budget. It throws a
 *    java.lang.Error (not an Exception), which LuaJ's pcall/xpcall do not catch, so a script
 *    cannot swallow the abort;
 *  - LuaJ shares ONE string metatable across every script in the JVM. It is built once here,
 *    never handed to a script (getmetatable on a string returns nil), and string.rep is capped.
 */
final class ScriptSandbox {

    /** Thrown by the hook when a call runs out of time. An Error on purpose: pcall can't catch it. */
    static final class Timeout extends Error {
        Timeout() {
            super("time limit", null, false, false);
        }
    }

    static final int MAX_STRING = 1 << 16;
    private static final int HOOK_EVERY = 1000; // bytecode instructions between deadline checks

    private static final String[] REMOVED = {
            "dofile", "loadfile", "load", "loadstring", "collectgarbage", "require", "package",
            "module", "debug", "coroutine", "io", "os", "luajava", "newproxy", "gcinfo"
    };

    static {
        // The first StringLib loaded in the JVM installs the string metatable for all strings.
        // Do that here, with a table no script can reach, before any script environment exists.
        Globals master = new Globals();
        master.load(new BaseLib());
        master.load(new PackageLib());
        master.load(new StringLib());
        hardenStringTable(master.get("string").checktable());
    }

    private final Globals globals;
    private volatile long deadline;
    private int depth;

    ScriptSandbox() {
        Globals g = new Globals();
        g.load(new BaseLib());
        g.load(new PackageLib()); // TableLib needs it in LuaJ 3.0.1; removed again below
        g.load(new TableLib());
        g.load(new StringLib());
        g.load(new JseMathLib());
        g.load(new DebugLib());
        LoadState.install(g);
        LuaC.install(g);
        g.STDOUT = new PrintStream(new ByteArrayOutputStream());

        // Install the time-limit hook, then take the debug library away from the script.
        LuaValue sethook = g.get("debug").get("sethook");
        sethook.invoke(LuaValue.varargsOf(new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue event, LuaValue arg) {
                if (System.nanoTime() - deadline > 0) throw new Timeout();
                return NIL;
            }
        }, LuaValue.valueOf(""), LuaValue.valueOf(HOOK_EVERY)));

        for (String name : REMOVED) g.set(name, LuaValue.NIL);

        hardenStringTable(g.get("string").checktable());

        final LuaValue originalGetMetatable = g.get("getmetatable");
        g.set("getmetatable", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue v) {
                return v.type() == LuaValue.TSTRING ? NIL : originalGetMetatable.call(v);
            }
        });

        this.globals = g;
    }

    Globals globals() {
        return globals;
    }

    LuaValue compile(String source, String chunkName) {
        return globals.load(source, chunkName);
    }

    /**
     * Calls fn with a time budget. Nested calls (a callback fired from inside another one) share
     * the outer call's deadline. Anything the script throws, including Timeout and
     * StackOverflowError, propagates to the caller.
     */
    Varargs call(LuaValue fn, long budgetMs, Varargs args) {
        if (depth == 0) deadline = System.nanoTime() + budgetMs * 1_000_000L;
        depth++;
        try {
            return fn.invoke(args);
        } finally {
            depth--;
        }
    }

    private static void hardenStringTable(LuaTable string) {
        final LuaValue originalRep = string.get("rep");
        string.set("rep", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs a) {
                LuaString s = a.checkstring(1);
                int n = a.checkint(2);
                LuaString sep = a.optstring(3, LuaValue.EMPTYSTRING);
                long total = (long) Math.max(0, n) * s.length() + (long) Math.max(0, n - 1) * sep.length();
                if (total > MAX_STRING) throw new LuaError("string.rep: result too large (max " + MAX_STRING + " chars)");
                return originalRep.invoke(a);
            }
        });
        string.set("dump", LuaValue.NIL);
        string.set("format", safeFormat(string.get("format")));
    }

    /**
     * string.format with correct %f / %e / %g handling and capped width/precision.
     *
     * LuaJ 3.0.1 ignores the precision in %.2f (prints 3.14159), so floating point conversions are
     * done here with Java's formatter; every other conversion is handed to LuaJ one directive at
     * a time. Width and precision are limited to 2 digits, as in real Lua, which also stops
     * "%99999999d"-style allocation bombs.
     */
    private static LuaValue safeFormat(final LuaValue original) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs a) {
                String fmt = a.checkjstring(1);
                StringBuilder out = new StringBuilder();
                int arg = 2;
                int i = 0;
                while (i < fmt.length()) {
                    char c = fmt.charAt(i);
                    if (c != '%') {
                        out.append(c);
                        i++;
                        continue;
                    }
                    int j = i + 1;
                    while (j < fmt.length() && "-+ #0".indexOf(fmt.charAt(j)) >= 0) j++;
                    int widthStart = j;
                    while (j < fmt.length() && Character.isDigit(fmt.charAt(j))) j++;
                    boolean tooLong = j - widthStart > 2;
                    if (j < fmt.length() && fmt.charAt(j) == '.') {
                        j++;
                        int precStart = j;
                        while (j < fmt.length() && Character.isDigit(fmt.charAt(j))) j++;
                        if (j - precStart > 2) tooLong = true;
                    }
                    if (j >= fmt.length()) throw new LuaError("invalid format string to 'format'");
                    if (tooLong) throw new LuaError("invalid format (width or precision too long)");
                    char conv = fmt.charAt(j);
                    String spec = fmt.substring(i, j + 1);
                    i = j + 1;

                    if (conv == '%') {
                        out.append('%');
                    } else if ("feEgG".indexOf(conv) >= 0) {
                        out.append(formatFloat(spec, conv, a.checkdouble(arg++)));
                    } else {
                        out.append(original.invoke(LuaValue.varargsOf(LuaValue.valueOf(spec), a.arg(arg++))).tojstring());
                    }
                }
                return LuaValue.valueOf(out.toString());
            }
        };
    }

    private static String formatFloat(String spec, char conv, double d) {
        try {
            if (conv == 'g' || conv == 'G') return formatG(spec, conv, d);
            return String.format(Locale.ROOT, spec, d);
        } catch (IllegalFormatException e) {
            throw new LuaError("invalid format '" + spec + "'");
        }
    }

    /** C-style %g: shortest of %e / %f for the precision, trailing zeros removed. */
    private static String formatG(String spec, char conv, double d) {
        int dot = spec.indexOf('.');
        int prec = 6;
        if (dot >= 0) {
            String digits = spec.substring(dot + 1, spec.length() - 1);
            prec = digits.isEmpty() ? 0 : Integer.parseInt(digits);
        }
        if (prec == 0) prec = 1;
        String flagsAndWidth = dot >= 0 ? spec.substring(0, dot) : spec.substring(0, spec.length() - 1);
        boolean keepZeros = flagsAndWidth.indexOf('#') >= 0;

        String body;
        if (d == 0 || Double.isNaN(d) || Double.isInfinite(d)) {
            body = Double.isNaN(d) || Double.isInfinite(d) ? String.valueOf(d) : "0";
        } else {
            int exp = (int) Math.floor(Math.log10(Math.abs(d)));
            String e = String.format(Locale.ROOT, "%." + (prec - 1) + "e", d);
            exp = Integer.parseInt(e.substring(e.indexOf('e') + 1)); // exponent after rounding
            if (exp < -4 || exp >= prec) {
                String mant = e.substring(0, e.indexOf('e'));
                if (!keepZeros && mant.indexOf('.') >= 0) mant = mant.replaceAll("0+$", "").replaceAll("\\.$", "");
                body = mant + e.substring(e.indexOf('e'));
            } else {
                body = String.format(Locale.ROOT, "%." + Math.max(0, prec - 1 - exp) + "f", d);
                if (!keepZeros && body.indexOf('.') >= 0) body = body.replaceAll("0+$", "").replaceAll("\\.$", "");
            }
        }
        if (conv == 'G') body = body.toUpperCase(Locale.ROOT);
        // apply sign flags and width by letting Java pad the finished text
        boolean left = flagsAndWidth.indexOf('-') >= 0;
        boolean plus = flagsAndWidth.indexOf('+') >= 0 && d >= 0;
        boolean space = flagsAndWidth.indexOf(' ') >= 0 && d >= 0 && !plus;
        if (plus) body = "+" + body; else if (space) body = " " + body;
        String w = flagsAndWidth.replaceAll("[^0-9]", "").replaceFirst("^0+", "");
        int width = w.isEmpty() ? 0 : Integer.parseInt(w);
        StringBuilder sb = new StringBuilder(body);
        while (sb.length() < width) {
            if (left) sb.append(' ');
            else sb.insert(0, ' ');
        }
        return sb.toString();
    }
}
