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
    // True when Hypixel found the account but its Bedwars stats specifically
    // are hidden via API Settings — distinct from "player not found at all".
    // Correlates strongly with players trying to evade stat-checkers.
    public boolean statsHidden  = false;
    // Set when a stats fetch throws (network hiccup, timeout, etc.) — used
    // by LobbyIntel's periodic retry so these players get another attempt
    // instead of being stuck unpopulated for the rest of the lobby.
    public boolean statsFetchFailed = false;
    public String  urchinTag    = null;
    public String  urchinType   = null;
    public String  urchinReason = null;

    // Ghost Intel
    public boolean ghostTagged = false;
    public String  ghostType   = null;
    public String  ghostReason = null;

    // Spirit Client Role
    public PlayerRole role = null;

    // Personal blacklist — .blacklist / .unblacklist, persisted across restarts
    public boolean blacklisted     = false;
    public String  blacklistReason = null;

    // Personal safelist — .safelist / .unsafelist, persisted across restarts
    public boolean safelisted      = false;
    public String  safelistReason  = null;

    // Computed
    public double threatScore = 0;

    public IntelPlayer(String name, String team) {
        this.name = name;
        this.team = team;
        this.role = RoleManager.getInstance().getRole(name);
        this.blacklistReason = BlacklistManager.getInstance().getReason(name);
        this.blacklisted = this.blacklistReason != null;
        this.safelistReason = SafelistManager.getInstance().getReason(name);
        this.safelisted = this.safelistReason != null;
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
        // Personal blacklist takes priority over everything else — it's a
        // deliberate call the person made themselves, not an API guess.
        if (blacklisted) return "B";

        // Safelist suppresses Coral's own classification — you've vouched
        // for this player. Blacklist above still wins if somehow both apply.
        if (safelisted) return "";

        if (!cheater) return "";

        String basis = (
                (urchinType   != null ? urchinType   : "") + " " +
                (urchinReason != null ? urchinReason : "") + " " +
                (urchinTag    != null ? urchinTag    : "")
        ).toLowerCase();

        if (basis.contains("blatant"))   return "BC";
        if (basis.contains("confirmed")) return "CCC";
        if (basis.contains("closet"))    return "CC";
        if (basis.contains("sniper"))    return "S";
        if (basis.contains("caution"))   return "R"; // replay needed — reviewed manually before flagging further

        // Flagged by Coral but none of the known severity words matched —
        // still show a code rather than a static placeholder.
        return "CC";
    }

    /** ARGB color matching {@link #getTagBadge()}'s classification. */
    public int getTagColor() {
        String badge = getTagBadge();
        switch (badge) {
            case "B":   return 0xFF4499FF; // blacklisted — blue
            case "BC":  return 0xFFFF3344; // blatant — red
            case "CCC": return 0xFF9932CC; // confirmed — dark purple
            case "S":   return 0xFFFF1122; // sniper — bright red
            case "R":   return 0xFF55FF55; // caution / replay needed — green
            case "CC":  return 0xFFFF8844; // closet / unclassified — orange
            default:    return 0xFFAAAAAA;
        }
    }

    /**
     * Short, human-readable explanation matching {@link #getTagBadge()}'s
     * classification — used anywhere a tag is shown to the person (tooltip,
     * .bw command, chat notification), instead of whatever raw text the
     * Coral/Ghost Intel API happened to return (which is often just a
     * detection-method name like "AutoBlock" regardless of severity).
     */
    public String getTagMessage() {
        switch (getTagBadge()) {
            case "B":   return "Blacklisted: " + (blacklistReason != null ? blacklistReason : "No reason given");
            case "BC":  return "Blatant cheater, be wary — might be hopping";
            case "CCC": return "Confirmed cheater, be wary — might be hopping";
            case "CC":  return "Using closet cheats, be wary";
            case "S":   return "Sniper, stay alert — probably hopping!";
            case "R":   return "Replay needed — flagged for manual review";
            default:    return "";
        }
    }

    /**
     * Same as {@link #getTagMessage()}, but also appends what Coral itself
     * actually tagged the player for (its raw classification text) — so the
     * friendly explanation doesn't hide the underlying source. Blacklist
     * ("B") has no Coral classification behind it, so it's left as-is.
     */
    public String getFullTagMessage() {
        String friendly = getTagMessage();
        String badge = getTagBadge();

        if (badge.equals("B") || badge.isEmpty()) {
            return friendly;
        }

        String raw = urchinTag != null ? urchinTag : urchinType;

        if (raw == null || raw.isEmpty() || raw.equalsIgnoreCase(friendly)) {
            return friendly;
        }

        return friendly.isEmpty() ? raw : friendly + " (Coral: " + raw + ")";
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
        // FKDR is weighted well above the other stats — it's the strongest
        // single signal for how dangerous a player actually is.
        statsScore += Math.min(55, fkdr * 9.0);
        statsScore += Math.min(15, wlr * 4.0);
        statsScore += Math.min(15, winstreak * 0.6);
        statsScore += Math.min(8, level / 100.0 * 8);
        statsScore += Math.min(7, finalKills / 1000.0 * 7);
        statsScore = Math.min(100, statsScore);

        if (cheater && !safelisted) {
            double typeBase = 40;

            if (urchinType != null) {
                if (urchinType.contains("blatant")) typeBase = 80;
                else if (urchinType.contains("confirmed")) typeBase = 65;
                else if (urchinType.contains("sniper")) typeBase = 90;
                else if (urchinType.contains("closet")) typeBase = 50;
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

            // Sniper is treated as an especially dangerous classification —
            // hold it near 90 regardless of what the keyword blend above
            // landed on, rather than letting a low-scoring reason keyword
            // (e.g. "reach") water it down.
            if (urchinType != null && urchinType.contains("sniper")) {
                cheatScore = Math.max(cheatScore, 88);
            }

            cheatScore = Math.max(cheatScore, 20);
            threatScore = Math.max(cheatScore, statsScore);
        } else {
            threatScore = statsScore;
        }

        // Personal blacklist overrides everything above it — the person
        // decided this player is dangerous themselves, so it floors threat
        // high regardless of what stats/Coral would otherwise say.
        if (blacklisted) {
            threatScore = Math.max(threatScore, 85);
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
