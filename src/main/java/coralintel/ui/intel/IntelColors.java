package coralintel.ui.intel;

/**
 * Single source of truth for FKDR/WLR/star-prestige colors, shared by the
 * HUD overlay, the LobbyIntel GUI, the vanilla tab list, and the .bw
 * command — so none of them can drift out of sync with each other again.
 */
public final class IntelColors {

    private IntelColors() {
    }

    /** Standard Minecraft 1.8 legacy color palette, index-matched to CODE_CHAR. */
    private static final int[] CODE_RGB = {
            0xFF000000, 0xFF0000AA, 0xFF00AA00, 0xFF00AAAA,
            0xFFAA0000, 0xFFAA00AA, 0xFFFFAA00, 0xFFAAAAAA,
            0xFF555555, 0xFF5555FF, 0xFF55FF55, 0xFF55FFFF,
            0xFFFF5555, 0xFFFF55FF, 0xFFFFFF55, 0xFFFFFFFF
    };

    private static final char[] CODE_CHAR = {
            '0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
    };

    /**
     * Nearest vanilla §-color code for an arbitrary ARGB color. Used by
     * surfaces (like the vanilla tab list) that can only render the 16
     * legacy formatting codes, not raw RGB.
     */
    public static String nearestCode(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;

        int best = 0;
        int bestDist = Integer.MAX_VALUE;

        for (int i = 0; i < CODE_RGB.length; i++) {
            int cr = (CODE_RGB[i] >> 16) & 0xFF;
            int cg = (CODE_RGB[i] >> 8) & 0xFF;
            int cb = CODE_RGB[i] & 0xFF;

            int dist = (r - cr) * (r - cr) + (g - cg) * (g - cg) + (b - cb) * (b - cb);

            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }

        return "\u00A7" + CODE_CHAR[best];
    }

    /** Red/orange/yellow/green tiered color for a stat like FKDR or WLR. */
    public static int getStatColor(double value, double mid, double high) {
        if (value < 0) return 0xFFAAAAAA;
        if (value >= high) return 0xFFFF3344;
        if (value >= mid) return 0xFFFF9933;
        if (value >= mid / 2) return 0xFFFFEE44;

        return 0xFF44CC66;
    }

    public static int getThreatColor(int score) {
        if (score >= 75) return 0xFFFF2244;
        if (score >= 50) return 0xFFFF7722;
        if (score >= 25) return 0xFFFFCC22;

        return 0xFF44DD66;
    }

    /** Hypixel Bedwars star prestige color, matching the game's own tier colors. */
    public static int getPrestigeColor(int star) {
        if (star >= 5000) return 0xFF00FFFF;
        if (star >= 4900) return 0xFFFF0000;
        if (star >= 4800) return 0xFFFF00FF;
        if (star >= 4700) return 0xFF00AA00;
        if (star >= 4600) return 0xFF00AAAA;
        if (star >= 4500) return 0xFF0000AA;
        if (star >= 4400) return 0xFFAA0000;
        if (star >= 4300) return 0xFF555555;
        if (star >= 4200) return 0xFFAAAAAA;
        if (star >= 4100) return 0xFFFFFFFF;
        if (star >= 4000) return 0xFF55FF55;

        if (star >= 3900) return 0xFF00FFFF;
        if (star >= 3800) return 0xFFFF0000;
        if (star >= 3700) return 0xFFFF00FF;
        if (star >= 3600) return 0xFF00AA00;
        if (star >= 3500) return 0xFF00AAAA;
        if (star >= 3400) return 0xFF0000AA;
        if (star >= 3300) return 0xFFAA0000;
        if (star >= 3200) return 0xFF555555;
        if (star >= 3100) return 0xFFAAAAAA;
        if (star >= 3000) return 0xFFFFFFFF;

        if (star >= 2900) return 0xFF00FFFF;
        if (star >= 2800) return 0xFFFF0000;
        if (star >= 2700) return 0xFFFF00FF;
        if (star >= 2600) return 0xFF00AA00;
        if (star >= 2500) return 0xFF00AAAA;
        if (star >= 2400) return 0xFF0000AA;
        if (star >= 2300) return 0xFFAA0000;
        if (star >= 2200) return 0xFF555555;
        if (star >= 2100) return 0xFFAAAAAA;
        if (star >= 2000) return 0xFFFFFFFF;

        if (star >= 1900) return 0xFF00FFFF;
        if (star >= 1800) return 0xFFFF0000;
        if (star >= 1700) return 0xFFFF00FF;
        if (star >= 1600) return 0xFF00AA00;
        if (star >= 1500) return 0xFF00AAAA;
        if (star >= 1400) return 0xFF0000AA;
        if (star >= 1300) return 0xFFAA0000;
        if (star >= 1200) return 0xFF555555;
        if (star >= 1100) return 0xFFAAAAAA;
        if (star >= 1000) return 0xFFFFFFFF;

        if (star >= 900) return 0xFFFF00FF;
        if (star >= 800) return 0xFF5555FF;
        if (star >= 700) return 0xFF55FFFF;
        if (star >= 600) return 0xFF55FF55;
        if (star >= 500) return 0xFFFF5555;
        if (star >= 400) return 0xFF0000AA;
        if (star >= 300) return 0xFF00AAAA;
        if (star >= 200) return 0xFFFFAA00;
        if (star >= 100) return 0xFFFFFFFF;

        return 0xFFAAAAAA;
    }
}
