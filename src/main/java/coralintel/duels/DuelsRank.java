package coralintel.duels;

/**
 * Duels ranking titles, derived from a win count. The thresholds follow Hypixel's Duels
 * title ladder (Rookie 50 wins ... Ascended 100,000 wins). Players below the first title are
 * "Unranked".
 */
public final class DuelsRank {
    private DuelsRank() {
    }

    public static final String[] NAMES = {
            "Unranked", "Rookie", "Iron", "Gold", "Diamond", "Master", "Legend",
            "Grandmaster", "Godlike", "Celestial", "Divine", "Ascended"
    };

    /** Wins needed for each title in NAMES (index 0 = no title). */
    public static final int[] WINS = {
            0, 50, 100, 250, 500, 1000, 2000, 5000, 10000, 25000, 50000, 100000
    };

    /** Minecraft colour code per title. */
    public static final String[] COLORS = {
            "\u00A77", "\u00A78", "\u00A7f", "\u00A76", "\u00A73", "\u00A72", "\u00A74",
            "\u00A7e", "\u00A75", "\u00A7b", "\u00A7d", "\u00A7c"
    };

    /** Index into NAMES for a win count. */
    public static int index(int wins) {
        int result = 0;
        for (int i = 0; i < WINS.length; i++) {
            if (wins >= WINS[i]) result = i;
        }
        return result;
    }

    public static String name(int wins) {
        return NAMES[index(wins)];
    }

    /** The title coloured, e.g. "§6Gold". */
    public static String colored(int wins) {
        int i = index(wins);
        return COLORS[i] + NAMES[i];
    }

    /** Wins still needed for the next title, or -1 at the top. */
    public static int winsToNext(int wins) {
        int i = index(wins);
        return i >= WINS.length - 1 ? -1 : WINS[i + 1] - wins;
    }
}
