package coralintel.ui.intel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cheater detection engine: folds every signal CoralIntel has about a player into one
 * 0-100 suspicion score, with the reasons that produced it.
 *
 * Signals:
 *  - stat anomalies (FKDR, win rate, winstreak, bed ratio) judged against the player's star level
 *  - tags: Coral/Urchin cheater, Ghost Intel, your own blacklist (and your safelist lowers it)
 *  - live AntiCheat flags (NoSlow, Scaffold, ...) seen this lobby
 *
 * This is a HEURISTIC, not proof. Strong players and people with hidden stats exist, so the score
 * only ever tells you where to look; it never blacklists or accuses anyone by itself.
 */
public final class CheatSuspicion {

    private CheatSuspicion() {
    }

    // ── Live AntiCheat flags (name -> check -> times flagged) ────────────────────

    private static final Map<String, Map<String, Integer>> FLAGS = new ConcurrentHashMap<>();

    /** Called by AntiCheat each time it flags someone. */
    public static void recordFlag(String name, String check) {
        if (name == null || check == null) return;
        FLAGS.computeIfAbsent(name.toLowerCase(Locale.ROOT), k -> new ConcurrentHashMap<>())
                .merge(check, 1, Integer::sum);
    }

    /** Forget flags (new lobby). */
    public static void clearFlags() {
        FLAGS.clear();
    }

    // ── Result ───────────────────────────────────────────────────────────────────

    public static final class Result {
        public final int score;
        public final List<String> reasons;

        Result(int score, List<String> reasons) {
            this.score = score;
            this.reasons = Collections.unmodifiableList(reasons);
        }

        /** Plain tier name for the score. */
        public String tier() {
            if (score >= 75) return "Very likely";
            if (score >= 50) return "Likely";
            if (score >= 25) return "Suspicious";
            return "No strong signs";
        }

        /** Minecraft colour code for the tier. */
        public String color() {
            if (score >= 75) return "&4";
            if (score >= 50) return "&c";
            if (score >= 25) return "&e";
            return "&a";
        }
    }

    // ── Scoring ──────────────────────────────────────────────────────────────────

    public static Result analyze(IntelPlayer p) {
        List<String> reasons = new ArrayList<>();
        double score = 0;

        // Stat anomalies need real, complete stats and enough games to mean something.
        boolean hasStats = p.statsComplete && !p.statsHidden && !p.isNicked;
        if (hasStats) {
            double star = p.starExact > 0 ? p.starExact : p.star;
            int games = p.wins + p.losses;

            // FKDR against what is plausible for the star level: the ceiling climbs with
            // stars (about 5.5 at 100✫, 10.5 at 300✫, 18 at 600✫) because better players
            // really do have higher FKDR.
            if (p.finalKills >= 150 && p.fkdr > 0) {
                double ceiling = 3.0 + star / 40.0;
                double ratio = p.fkdr / ceiling;
                if (ratio >= 2.0) {
                    score += 35;
                    reasons.add(String.format(Locale.ROOT, "FKDR %.1f is far above normal for a %d\u272B player", p.fkdr, (int) star));
                } else if (ratio >= 1.5) {
                    score += 25;
                    reasons.add(String.format(Locale.ROOT, "FKDR %.1f is very high for a %d\u272B player", p.fkdr, (int) star));
                } else if (ratio >= 1.0) {
                    score += 12;
                    reasons.add(String.format(Locale.ROOT, "FKDR %.1f is high for a %d\u272B player", p.fkdr, (int) star));
                }
            }

            // Win rate over a real sample.
            if (games >= 200) {
                double winRate = (double) p.wins / games;
                if (winRate >= 0.95) {
                    score += 30;
                    reasons.add(String.format(Locale.ROOT, "Wins %.0f%% of %d games", winRate * 100, games));
                } else if (winRate >= 0.90) {
                    score += 18;
                    reasons.add(String.format(Locale.ROOT, "Wins %.0f%% of %d games", winRate * 100, games));
                }
            }

            // Winstreaks that outrun the star level.
            if (p.winstreak >= 60) {
                score += 20;
                reasons.add("Winstreak of " + p.winstreak);
            } else if (p.winstreak >= 30 && star < 300) {
                score += 15;
                reasons.add("Winstreak of " + p.winstreak + " at only " + (int) star + "\u272B");
            }

            // Beds broken per bed lost.
            if (p.bedsBroken >= 200 && p.bedsLost > 0 && (double) p.bedsBroken / p.bedsLost >= 10.0) {
                score += 10;
                reasons.add(String.format(Locale.ROOT, "BBLR %.1f", (double) p.bedsBroken / p.bedsLost));
            }
        }

        // Hidden stats: only a small nudge, since plenty of honest players hide them.
        if (p.statsHidden) {
            score += 8;
            reasons.add("Stats are hidden (some cheaters do this, but so do many normal players)");
        }

        // Tags.
        if (p.cheater) {
            score += 45;
            reasons.add("Tagged as a cheater by Coral/Urchin");
        }
        if (p.ghostTagged) {
            score += 30;
            reasons.add("Ghost Intel tag" + (p.ghostReason != null && !p.ghostReason.isEmpty() ? ": " + p.ghostReason : ""));
        }
        if (p.blacklisted) {
            score += 20;
            reasons.add("On your blacklist" + (p.blacklistReason != null && !p.blacklistReason.isEmpty() ? ": " + p.blacklistReason : ""));
        }

        // Live AntiCheat flags from this lobby.
        Map<String, Integer> flags = p.name == null ? null : FLAGS.get(p.name.toLowerCase(Locale.ROOT));
        if (flags != null && !flags.isEmpty()) {
            int total = 0;
            for (int n : flags.values()) total += n;
            Set<String> checks = new TreeSet<>(flags.keySet());
            score += Math.min(60, 15 * checks.size());
            if (total >= 5) score += 10;
            reasons.add("AntiCheat flagged " + String.join(", ", checks) + " (" + total + "x)");
        }

        // Your own safelist is a deliberate call that they're fine.
        if (p.safelisted) {
            score -= 30;
            reasons.add("On your safelist (lowers the score)");
        }

        int finalScore = (int) Math.max(0, Math.min(100, Math.round(score)));
        return new Result(finalScore, reasons);
    }
}
