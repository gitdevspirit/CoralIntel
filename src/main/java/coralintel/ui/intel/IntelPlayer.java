package coralintel.ui.intel;

/**
 * Data model for a single player's intelligence profile.
 */
public class IntelPlayer {
    public String  name;
    public String  team;
    /** Hypixel rank prefix (e.g. "§b[MVP§9+§b]"), extracted from the tab-list display name. */
    public String  rankPrefix = "";
    public boolean loading    = true;

    // Hypixel BedWars stats
    public int    level       = 0;
    public int    star        = 0;
    public double fkdr        = 0;
    public double wlr         = 0;
    public int    winstreak   = 0;
    public int    finalKills  = 0;
    public int    finalDeaths = 0;
    public int    bedsBroken  = 0;
    public int    bedsLost    = 0;
    public int    kills       = 0;
    public int    deaths      = 0;
    public int    wins        = 0;
    public int    losses      = 0;
    public boolean isNicked   = false;

    // Coral / Urchin
    public boolean cheater      = false;
    public String  urchinTag    = null;
    public String  urchinType   = null;
    public String  urchinReason = null;

    // Ghost Intel
    public boolean ghostTagged = false;
    public String  ghostType   = null;
    public String  ghostReason = null;

    // Spirit Client Role
    public PlayerRole role = null;

    // Computed
    public double threatScore = 0;

    public IntelPlayer(String name, String team) {
        this.name = name;
        this.team = team;
        this.role = RoleManager.getInstance().getRole(name);
    }

    /**
     * Compact Coral tag label — single source of truth used by the HUD
     * overlay, LobbyIntel GUI, tab list, and .bw command so they never
     * diverge from each other again. Checks urchinType (which now folds in
     * icon + text + tooltip, since Cubelify's "icon" field alone is just a
     * Material Design icon id and never contains classification words) plus
     * urchinReason and urchinTag as fallbacks, in case the classification
     * word only shows up in one of them for a given tag source.
     */
    public String getTagBadge() {
        if (!cheater) return "";

        String basis = (
                (urchinType   != null ? urchinType   : "") + " " +
                (urchinReason != null ? urchinReason : "") + " " +
                (urchinTag    != null ? urchinTag    : "")
        ).toLowerCase();

        if (basis.contains("blatant"))   return "BC";
        if (basis.contains("confirmed")) return "CC";
        if (basis.contains("closet"))    return "C";
        if (basis.contains("sniper"))    return "S";
        if (basis.contains("caution"))   return "R"; // replay needed — reviewed manually before flagging further

        // Flagged by Coral but none of the known severity words matched —
        // still show a code rather than a static placeholder.
        return "C";
    }

    /** ARGB color matching {@link #getTagBadge()}'s classification. */
    public int getTagColor() {
        String badge = getTagBadge();
        switch (badge) {
            case "BC": return 0xFFFF3344; // blatant — red
            case "CC": return 0xFFDD44DD; // confirmed — magenta
            case "S":  return 0xFFFF1122; // sniper — bright red
            case "R":  return 0xFFFF69B4; // caution / replay needed — pink
            case "C":  return 0xFFFF8844; // closet / unclassified — orange
            default:   return 0xFFAAAAAA;
        }
    }

    private static final Object[][] KEYWORD_SCORES = {
        { "blatant", false, 85 },
        { "blatant scaffold", false, 85 },
        { "fly", true, 80 },
        { "bhop", true, 75 },
        { "bunnyhop", false, 75 },
        { "full hop", false, 75 },
        { "speed", true, 75 },
        { "esp", true, 80 },
        { "xray", false, 80 },
        { "x-ray", false, 80 },
        { "wallhack", false, 80 },
        { "aimbot", false, 85 },
        { "killaura", false, 65 },
        { "kill aura", false, 65 },

        { "autoblock", false, 65 },
        { "reach", true, 58 },
        { "velocity", false, 55 },
        { "velo", true, 55 },
        { "jump reset", false, 55 },
        { "anti-kb", false, 55 },
        { "antikb", false, 55 },

        { "autoclicker", false, 35 },
        { "autoclick", false, 35 },
        { "legit scaffold", false, 30 },
        { "legitscaff", false, 30 },
        { "legitscaf", false, 30 },
        { "eagle", true, 25 },
        { "fastplace", false, 28 },
        { "safewalk", false, 25 },
        { "2q", false, 20 },
        { "3q", false, 22 },
        { "4q", false, 25 },
        { "boosting", false, 20 },
        { "queuing", false, 20 }
    };

    public void computeThreat() {
        double statsScore = 0;
        statsScore += Math.min(40, fkdr * 6.0);
        statsScore += Math.min(20, wlr * 5.0);
        statsScore += Math.min(20, winstreak * 0.8);
        statsScore += Math.min(10, level / 100.0 * 10);
        statsScore += Math.min(10, finalKills / 1000.0 * 10);
        statsScore = Math.min(100, statsScore);

        if (cheater) {
            double typeBase = 40;

            if (urchinType != null) {
                if (urchinType.contains("blatant")) typeBase = 80;
                else if (urchinType.contains("confirmed")) typeBase = 65;
                else if (urchinType.contains("closet")) typeBase = 50;
                else if (urchinType.contains("sniper")) typeBase = 45;
                else if (urchinType.contains("account")) typeBase = 35;
                else if (urchinType.contains("caution")) typeBase = 30;
                else if (urchinType.contains("info")) typeBase = 20;
            }

            java.util.List<Double> found = new java.util.ArrayList<>();

            if (urchinReason != null) {
                String reason = urchinReason;

                for (Object[] keyword : KEYWORD_SCORES) {
                    String word = (String) keyword[0];
                    boolean boundary = (boolean) keyword[1];
                    double value = ((Number) keyword[2]).doubleValue();

                    boolean hit = boundary
                            ? hasWord(reason, word)
                            : reason.contains(word);

                    if (hit) found.add(value);
                }
            }

            double cheatScore;

            if (found.isEmpty()) {
                cheatScore = typeBase;
            } else {
                double average = 0;

                for (double score : found) {
                    average += score;
                }

                average /= found.size();
                cheatScore = average * 0.7 + typeBase * 0.3;
            }

            cheatScore = Math.max(cheatScore, 20);
            threatScore = Math.max(cheatScore, statsScore);
        } else {
            threatScore = statsScore;
        }

        loading = false;
    }

    private boolean hasWord(String text, String word) {
        int index = text.indexOf(word);

        while (index >= 0) {
            boolean beforeOk = index == 0
                    || !Character.isLetterOrDigit(text.charAt(index - 1));

            boolean afterOk = index + word.length() >= text.length()
                    || !Character.isLetterOrDigit(text.charAt(index + word.length()));

            if (beforeOk && afterOk) return true;

            index = text.indexOf(word, index + 1);
        }

        return false;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) return true;
        }

        return false;
    }
}
