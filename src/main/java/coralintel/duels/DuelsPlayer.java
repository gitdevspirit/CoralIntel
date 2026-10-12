package coralintel.duels;

/**
 * Data model for one player's Duels profile. Completely separate from the Bedwars
 * {@code IntelPlayer}: it only knows Duels stats (overall, Classic, Bridge, Bedwars Duels).
 */
public class DuelsPlayer {
    public String name;
    /** Hypixel rank prefix taken from the tab list (e.g. "§b[MVP§9+§b]"). */
    public String rankPrefix = "";
    public volatile boolean loading = true;
    public volatile boolean statsFinal = false;
    public volatile boolean statsFetchFailed = false;
    public volatile boolean fetchInFlight = false;
    public boolean statsFromCache = false;
    public int fetchAttempts = 0;
    public boolean isNicked = false;
    /** The account exists but its Duels stats are hidden via API settings. */
    public boolean statsHidden = false;

    // Denick (Bedlify): who is likely behind a nick. Set from a background thread.
    public volatile String realName = null;
    public volatile boolean denickRequested = false;

    /** Stats of one Duels mode (or the overall Duels totals). */
    public static final class Mode {
        public int wins, losses, kills, deaths, winstreak, bestWinstreak;

        public double wlr() { return losses == 0 ? wins : (double) wins / losses; }
        public double kdr() { return deaths == 0 ? kills : (double) kills / deaths; }

        public boolean isEmpty() { return wins + losses + kills + deaths == 0; }
    }

    public final Mode overall = new Mode();
    public final Mode classic = new Mode();
    public final Mode bridge = new Mode();
    public final Mode bedwars = new Mode();

    /** True once a profile with a stats object was read (the numbers are then real, zeros included). */
    public boolean duelsLoaded = false;

    /** Stats game picker values (index 0 is the overall total). */
    public static final String[] GAMES = {"Overall", "Classic", "Bridge", "Bedwars Duels"};

    public Mode mode(int game) {
        switch (game) {
            case 1: return classic;
            case 2: return bridge;
            case 3: return bedwars;
            default: return overall;
        }
    }

    public DuelsPlayer(String name) {
        this.name = name;
    }

    /** Clears every stat back to zero (used when a fetch round comes back with nothing usable). */
    public void zero() {
        for (Mode m : new Mode[]{overall, classic, bridge, bedwars}) {
            m.wins = m.losses = m.kills = m.deaths = m.winstreak = m.bestWinstreak = 0;
        }
    }
}
