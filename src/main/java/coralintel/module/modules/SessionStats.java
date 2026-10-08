package coralintel.module.modules;

import coralintel.event.EventTarget;
import coralintel.events.LoadWorldEvent;
import coralintel.module.BooleanSetting;
import coralintel.module.Module;
import coralintel.ui.intel.IntelColors;
import coralintel.ui.intel.IntelManager;
import coralintel.ui.intel.IntelPlayer;
import coralintel.util.ChatUtil;
import net.minecraft.client.Minecraft;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Session stats: your own Bedwars wins, kills, FKDR and BBLR gained since you
 * launched the game, or since the last .reset.
 *
 *  - The baseline is your lifetime totals, fetched a few seconds after the first
 *    world loads (retried on later world loads if that fetch fails).
 *  - Everything shown is "current totals minus baseline". FKDR / BBLR are the
 *    ratios of what you gained this session (finals / final deaths, beds broken /
 *    beds lost), worked out the same way as .daily / .monthly.
 *  - After a world change (e.g. back to the lobby after a game) your stats are
 *    re-fetched and a one-line summary is printed if you played a game since the
 *    last one. .session prints it on demand, .reset restarts the session.
 *
 * Hypixel's API lags a little behind the game, so a game you just finished can
 * take a minute or two to show up. The numbers are only kept in memory.
 */
public class SessionStats extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final long BASELINE_DELAY_MS = 4000L;
    private static final long AUTO_DELAY_MS = 8000L;
    /** At most one automatic refresh per this long (spares the API key). */
    private static final long AUTO_MIN_GAP_MS = 120_000L;

    public final BooleanSetting showWins =
            register(new BooleanSetting("Show Wins", true));
    public final BooleanSetting showKills =
            register(new BooleanSetting("Show Kills", true));
    public final BooleanSetting showFkdr =
            register(new BooleanSetting("Show FKDR", true));
    public final BooleanSetting showBblr =
            register(new BooleanSetting("Show BBLR", true));
    public final BooleanSetting autoSummary =
            register(new BooleanSetting("Summary After Games", true));

    private static final class Totals {
        final int wins, losses, kills, finalKills, finalDeaths, bedsBroken, bedsLost;

        Totals(IntelPlayer p) {
            this.wins = p.wins;
            this.losses = p.losses;
            this.kills = p.kills;
            this.finalKills = p.finalKills;
            this.finalDeaths = p.finalDeaths;
            this.bedsBroken = p.bedsBroken;
            this.bedsLost = p.bedsLost;
        }
    }

    private static final class Gained {
        int wins, games, kills, finalKills, finalDeaths, bedsBroken, bedsLost;
        double fkdr, bblr;

        boolean isEmpty() {
            return games == 0 && kills == 0 && finalKills == 0 && finalDeaths == 0
                    && bedsBroken == 0 && bedsLost == 0;
        }
    }

    private volatile Totals baseline;
    private volatile Totals latest;
    private volatile long startedAt;
    private volatile long lastAutoAt;
    private volatile int announcedGames;
    private final AtomicBoolean busy = new AtomicBoolean(false);

    public SessionStats() {
        super("SessionStats", true);
    }

    @Override
    public void onEnabled() {
        if (baseline == null && mc.thePlayer != null) {
            start(false);
        }
    }

    // ── Session control ──────────────────────────────────────────────────

    /** Starts (or restarts) the session from your current lifetime stats. */
    public void start(final boolean announce) {
        final String name = ownName();
        if (name == null) {
            if (announce) say("&cCouldn't work out your username.");
            return;
        }

        if (!busy.compareAndSet(false, true)) {
            if (announce) say("&7Still fetching your stats, try again in a moment.");
            return;
        }

        if (announce) say("&7Fetching your stats...");

        new Thread(() -> {
            try {
                Totals totals = fetchTotals(name);
                if (totals != null) {
                    baseline = totals;
                    latest = totals;
                    startedAt = System.currentTimeMillis();
                    announcedGames = 0;
                }

                final boolean ok = totals != null;
                if (announce) {
                    mc.addScheduledTask(() -> say(ok
                            ? "&aSession started. &7Counting from your current stats."
                            : "&cCouldn't load your stats, so the session wasn't started."));
                }
            } finally {
                busy.set(false);
            }
        }, "CoralIntel-Session-Start").start();
    }

    /**
     * Re-fetches your stats. A manual refresh always prints the summary; an
     * automatic one only prints when you've played a game since the last summary.
     */
    public void refresh(final boolean manual) {
        if (baseline == null) {
            start(manual);
            return;
        }

        final String name = ownName();
        if (name == null) return;

        if (!busy.compareAndSet(false, true)) {
            if (manual) say("&7Still fetching your stats, try again in a moment.");
            return;
        }

        if (manual) say("&7Fetching your current stats...");

        new Thread(() -> {
            try {
                Totals totals = fetchTotals(name);
                if (totals != null) {
                    latest = totals;
                }

                final boolean ok = totals != null;
                mc.addScheduledTask(() -> {
                    if (!ok) {
                        if (manual) say("&cCouldn't load your stats.");
                        return;
                    }

                    int games = gained().games;
                    boolean newGame = games > announcedGames;
                    boolean allowed = autoSummary.getValue() && !StreamerMode.hidesStatsFor(name);

                    if (manual || (newGame && allowed)) {
                        announcedGames = games;
                        say(summaryLine());
                    }
                });
            } finally {
                busy.set(false);
            }
        }, "CoralIntel-Session-Refresh").start();
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        if (!isEnabled()) return;

        final boolean first = baseline == null;
        long now = System.currentTimeMillis();

        if (!first) {
            if (!autoSummary.getValue() || now - lastAutoAt < AUTO_MIN_GAP_MS) return;
            lastAutoAt = now;
        }

        final long delay = first ? BASELINE_DELAY_MS : AUTO_DELAY_MS;

        new Thread(() -> {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException ignored) {
                return;
            }

            if (!isEnabled()) return;

            if (first) {
                if (baseline == null) start(false);
            } else {
                refresh(false);
            }
        }, "CoralIntel-Session-Auto").start();
    }

    // ── Text ─────────────────────────────────────────────────────────────

    /** Colored chat line, or a "not started" notice. */
    public String summaryLine() {
        if (baseline == null || latest == null) {
            return "&7No session yet. Run &f.reset &7to start one.";
        }

        Gained g = gained();
        StringBuilder line = new StringBuilder("&bThis session &7» ");

        if (g.isEmpty()) {
            return line.append("&7no games yet &8(").append(duration()).append(")").toString();
        }

        if (showWins.getValue()) {
            line.append("&7Wins &f+").append(g.wins).append("  ");
        }
        if (showKills.getValue()) {
            line.append("&7Kills &f+").append(g.kills).append("  ");
        }
        if (showFkdr.getValue()) {
            String code = IntelColors.nearestCode(IntelColors.getStatColor(g.fkdr, 3, 6));
            line.append("&7FKDR ").append(code).append(fmt(g.fkdr)).append("  ");
        }
        if (showBblr.getValue()) {
            line.append("&7BBLR &f").append(fmt(g.bblr)).append("  ");
        }

        line.append("&8(").append(g.games).append(g.games == 1 ? " game, " : " games, ")
                .append(duration()).append(")");
        return line.toString();
    }

    /** Two plain-text lines for the ClickGUI panel (kept short to fit the panel). */
    public String[] guiLines() {
        if (baseline == null || latest == null) {
            return new String[]{"Not started yet", "Starts after you join a server"};
        }

        Gained g = gained();
        StringBuilder first = new StringBuilder();
        StringBuilder second = new StringBuilder();

        if (showWins.getValue()) first.append("Wins +").append(g.wins).append("  ");
        if (showKills.getValue()) first.append("Kills +").append(g.kills).append("  ");
        first.append("Games ").append(g.games);

        if (showFkdr.getValue()) second.append("FKDR ").append(fmt(g.fkdr)).append("  ");
        if (showBblr.getValue()) second.append("BBLR ").append(fmt(g.bblr)).append("  ");
        second.append("(").append(duration()).append(")");

        return new String[]{first.toString(), second.toString()};
    }

    // ── Internals ────────────────────────────────────────────────────────

    private Gained gained() {
        Totals b = baseline;
        Totals l = latest;
        Gained g = new Gained();
        if (b == null || l == null) return g;

        g.wins = diff(l.wins, b.wins);
        g.games = g.wins + diff(l.losses, b.losses);
        g.kills = diff(l.kills, b.kills);
        g.finalKills = diff(l.finalKills, b.finalKills);
        g.finalDeaths = diff(l.finalDeaths, b.finalDeaths);
        g.bedsBroken = diff(l.bedsBroken, b.bedsBroken);
        g.bedsLost = diff(l.bedsLost, b.bedsLost);
        g.fkdr = g.finalDeaths == 0 ? g.finalKills : (double) g.finalKills / g.finalDeaths;
        g.bblr = g.bedsLost == 0 ? g.bedsBroken : (double) g.bedsBroken / g.bedsLost;
        return g;
    }

    private static int diff(int now, int then) {
        return Math.max(0, now - then);
    }

    private String duration() {
        long minutes = Math.max(0L, (System.currentTimeMillis() - startedAt) / 60000L);
        if (minutes < 60) return minutes + "m";
        return (minutes / 60) + "h " + (minutes % 60) + "m";
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String ownName() {
        return mc.getSession() == null ? null : mc.getSession().getUsername();
    }

    private static Totals fetchTotals(String name) {
        IntelPlayer player = IntelManager.getInstance().fetchStandaloneStats(name, false);
        return player != null && player.statsComplete ? new Totals(player) : null;
    }

    private static void say(String message) {
        ChatUtil.sendFormatted(message);
    }
}
