package coralintel.util;

/**
 * Bedwars prestige formatting, ported from Nevada (Salmontree/Nevada,
 * BedwarsUtil.formatLevel) so the tab list, HUD overlay, 3D BedwarsTag,
 * GUI and .bw command all render the exact same per-character prestige
 * colors as the in-game chart.
 *
 * Two fixes vs. the Nevada original:
 *  - 900-999 is dark purple (Nevada fell through to the 800 blue).
 *  - 1900-1999 had a malformed "§✪✫" sequence; it is now a proper
 *    dark-purple number with a pink star like the other 1100-1900 tiers.
 *
 * Everything is built from a "color char per character" model, so any
 * surface can ask for the full "[1234✪]", the bare "1234✪", just the
 * number, or just the glyph, and the colors always stay lined up.
 */
public final class PrestigeUtil {

    private PrestigeUtil() {
    }

    /** Cycling tiers, highest first: {minStar, glyph, color codes cycled per character of "[N✪]"}. */
    private static final Object[][] CYCLE = {
            {5000, '\u2725', "859980"},
            {4900, '\u2725', "affaa8"},
            {4800, '\u2725', "7ceeb3"},
            {4700, '\u2725', "989"},
            {4600, '\u2725', "beee57"},
            {4500, '\u2725', "fb33333"},
            {4400, '\u2725', "aee5d"},
            {4300, '\u2725', "78553"},
            {4200, '\u2725', "93bf77"},
            {4100, '\u2725', "e6cdc7"},
            {4000, '\u2725', "7cc66e"},
            {3900, '\u2725', "caa397"},
            {3800, '\u2725', "8955c1"},
            {3700, '\u2725', "8cb33"},
            {3600, '\u2725', "aab9988"},
            {3500, '\u2725', "c48aa"},
            {3400, '\u2725', "7d55888"},
            {3300, '\u2725', "99dcc8"},
            {3200, '\u2725', "c774ccc"},
            {3100, '\u2725', "9366e"},
            {3000, '\u269D', "eeecc"},
            {2900, '\u269D', "b7799"},
            {2800, '\u269D', "788ee"},
            {2700, '\u269D', "ef878"},
            {2600, '\u269D', "cccdd"},
            {2500, '\u269D', "fa788"},
            {2400, '\u269D', "bff778"},
            {2300, '\u269D', "5dd"},
            {2200, '\u269D', "eff37"},
            {2100, '\u269D', "feeee"},
            {2000, '\u272A', "8ff788"},
    };

    /** 1000-1099 rainbow (checked after the static 1100-1900 tiers). */
    private static final String RAINBOW_1000 = "c6eabd5";

    /** Static 1100-1900 tiers: {minStar, numberColor, starColor}; brackets are always gray. */
    private static final Object[][] STATIC_STAR = {
            {1900, '5', 'd'},
            {1800, '9', '1'},
            {1700, 'd', '5'},
            {1600, 'c', '4'},
            {1500, '3', '9'},
            {1400, 'a', '2'},
            {1300, 'b', '3'},
            {1200, 'e', '6'},
            {1100, 'f', '7'},
    };

    /** Single-color tiers below 1000: {minStar, color}. */
    private static final Object[][] SINGLE = {
            {900, '5'}, {800, '9'}, {700, 'd'}, {600, '4'}, {500, '3'},
            {400, '2'}, {300, 'b'}, {200, '6'}, {100, 'f'}, {0, '7'},
    };

    /** The glyph that follows the number for a given star count. */
    public static char glyph(int star) {
        if (star >= 3100) return '\u2725'; // ✥
        if (star >= 2100) return '\u269D'; // ⚝
        if (star >= 1100) return '\u272A'; // ✪
        return '\u272B';                   // ✫
    }

    /** Color code char for every character of "[" + star + glyph + "]". */
    private static char[] colors(int star) {
        String digits = String.valueOf(Math.max(0, star));
        int len = digits.length() + 3;
        char[] out = new char[len];

        for (Object[] t : CYCLE) {
            if (star >= (Integer) t[0]) {
                String pattern = (String) t[2];
                for (int i = 0; i < len; i++) out[i] = pattern.charAt(i % pattern.length());
                return out;
            }
        }

        for (Object[] t : STATIC_STAR) {
            if (star >= (Integer) t[0]) {
                java.util.Arrays.fill(out, (Character) t[1]);
                out[0] = '7';
                out[len - 1] = '7';
                out[len - 2] = (Character) t[2]; // glyph
                return out;
            }
        }

        if (star >= 1000) {
            for (int i = 0; i < len; i++) out[i] = RAINBOW_1000.charAt(i % RAINBOW_1000.length());
            return out;
        }

        for (Object[] t : SINGLE) {
            if (star >= (Integer) t[0]) {
                java.util.Arrays.fill(out, (Character) t[1]);
                return out;
            }
        }

        java.util.Arrays.fill(out, '7');
        return out;
    }

    private static String build(int star, int from, int to) {
        String digits = String.valueOf(Math.max(0, star));
        String full = "[" + digits + glyph(star) + "]";
        char[] cols = colors(star);

        StringBuilder sb = new StringBuilder();
        char last = 0;
        for (int i = from; i < to; i++) {
            if (cols[i] != last) {
                sb.append('\u00A7').append(cols[i]);
                last = cols[i];
            }
            sb.append(full.charAt(i));
        }
        return sb.toString();
    }

    /** Full chat-style level: "[1234✪]" with per-character prestige colors. */
    public static String format(int star) {
        return build(star, 0, String.valueOf(Math.max(0, star)).length() + 3);
    }

    /** Same colors without the brackets: "1234✪" — for tight columns. */
    public static String formatCompact(int star) {
        return build(star, 1, String.valueOf(Math.max(0, star)).length() + 2);
    }

    /** Just the colored digits. */
    public static String number(int star) {
        return build(star, 1, String.valueOf(Math.max(0, star)).length() + 1);
    }

    /** Just the colored glyph. */
    public static String glyphColored(int star) {
        int n = String.valueOf(Math.max(0, star)).length();
        return build(star, n + 1, n + 2);
    }

    /** Legacy color code char of the number's first digit — the tier's "main" color. */
    public static char mainCode(int star) {
        return colors(star)[1];
    }
}
