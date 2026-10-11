package coralintel.module.modules;

import coralintel.enums.ChatColors;
import coralintel.event.EventTarget;
import coralintel.event.types.EventType;
import coralintel.events.LoadWorldEvent;
import coralintel.events.PacketEvent;
import coralintel.events.Render2DEvent;
import coralintel.module.BooleanSetting;
import coralintel.module.Module;
import coralintel.module.SliderSetting;
import coralintel.ui.intel.IntelColors;
import coralintel.ui.intel.IntelManager;
import coralintel.ui.intel.IntelPlayer;
import coralintel.util.ChatUtil;
import coralintel.util.PrestigeUtil;
import coralintel.util.SessionHistory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.network.play.server.S45PacketTitle;
import net.minecraft.util.IChatComponent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Session stats: your own Bedwars progress since you launched the game, or since
 * the last .reset.
 *
 * Wins, losses, kills, deaths, final kills, final deaths, beds broken and beds
 * lost are counted live from the game chat (the same lines that end up in
 * latest.log), so a kill shows up the moment it happens. Each one has its own
 * counter and they are never mixed:
 *
 *  - FINAL KILL! lines  -> final kills (you are the killer) / final deaths (you are the victim)
 *  - other kill lines   -> kills / deaths
 *  - BED DESTRUCTION    -> beds broken (you broke it) / beds lost ("Your Bed")
 *  - end-of-game block  -> a win (VICTORY title / your name on the winning team) or a loss
 *
 * FKDR / BBLR / WLR are the ratios of what you gained this session, worked out
 * the same way as .daily / .monthly. Stars are worked out from the "+N Bed Wars Experience"
 * chat lines (XP gained this session on top of your starting XP), so they move live too. The "Track From Chat"
 * setting switches everything back to API differences if Hypixel ever changes
 * its chat messages.
 *
 * Active time: the session time only counts while you are inside a game; sitting in
 * the lobby, queue or pregame pauses it. It is shown next to the total session time,
 * together with the average length of the games that ran to their end.
 *
 * HUD: a small box on screen with the session time, finals / FKDR, beds / BBLR,
 * wins / WLR, kills / deaths and stars. The order of the lines is set in the
 * ClickGUI (Order: ...). Open your inventory to get a [Reset Session] button and
 * to drag the box to a new spot (the position is saved with the other settings).
 *
 * The numbers are only kept in memory.
 */
public class SessionStats extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final long BASELINE_DELAY_MS = 4000L;
    private static final long AUTO_DELAY_MS = 8000L;
    /** At most one automatic refresh per this long (spares the API key). */
    private static final long AUTO_MIN_GAP_MS = 120_000L;

    // Session timing first: total time, active (in-game) time and average game length.
    public final BooleanSetting showTime =
            register(new BooleanSetting("Show Session Time", true));
    public final BooleanSetting showActive =
            register(new BooleanSetting("Show Active Time", true));
    public final BooleanSetting showAvgGame =
            register(new BooleanSetting("Show Avg Game Length", true));

    public final BooleanSetting trackFromChat =
            register(new BooleanSetting("Track From Chat", true));

    // Each toggle controls BOTH the HUD and the chat summary (where the stat exists there).
    public final BooleanSetting showWins =
            register(new BooleanSetting("Show Wins", true));
    public final BooleanSetting showKills =
            register(new BooleanSetting("Show Kills", true));
    public final BooleanSetting showFkdr =
            register(new BooleanSetting("Show FKDR", true));
    public final BooleanSetting showBblr =
            register(new BooleanSetting("Show BBLR", true));
    public final BooleanSetting showDeaths =
            register(new BooleanSetting("Show Deaths", true));
    public final BooleanSetting showWlr =
            register(new BooleanSetting("Show WLR", true));
    public final BooleanSetting showStars =
            register(new BooleanSetting("Show Stars", true));
    // HUD only:
    public final BooleanSetting showFinals =
            register(new BooleanSetting("Show Finals", true));
    public final BooleanSetting showBeds =
            register(new BooleanSetting("Show Beds", true));

    public final BooleanSetting autoSummary =
            register(new BooleanSetting("Summary After Games", true));

    public final BooleanSetting showHud =
            register(new BooleanSetting("Show HUD", true));
    // Right-aligned HUD: text lines up on the right and the box's right edge stays put.
    public final BooleanSetting savedMessage =
            register(new BooleanSetting("Saved Message", true));
    public final BooleanSetting alignRight =
            register(new BooleanSetting("Align Right", false));
    public final BooleanSetting hudBackground =
            register(new BooleanSetting("HUD Background", true));
    // Line order for the HUD: lowest number goes on top (ties keep the default order).
    public final SliderSetting orderTime =
            register(new SliderSetting("Order: Session / Active Time", 1, 1, 7, 1));
    public final SliderSetting orderAvgGame =
            register(new SliderSetting("Order: Avg Game Length", 2, 1, 7, 1));
    public final SliderSetting orderFinals =
            register(new SliderSetting("Order: Finals / FKDR", 3, 1, 7, 1));
    public final SliderSetting orderBeds =
            register(new SliderSetting("Order: Beds / BBLR", 4, 1, 7, 1));
    public final SliderSetting orderWins =
            register(new SliderSetting("Order: Wins / WLR", 5, 1, 7, 1));
    public final SliderSetting orderKills =
            register(new SliderSetting("Order: Kills / Deaths", 6, 1, 7, 1));
    public final SliderSetting orderStars =
            register(new SliderSetting("Order: Stars", 7, 1, 7, 1));
    // Saved with the other settings but dragged in the inventory, so not shown in the ClickGUI.
    private final SliderSetting hudX =
            register(new SliderSetting("HUD X", 6, 0, 4000, 1, () -> false));
    private final SliderSetting hudY =
            register(new SliderSetting("HUD Y", 40, 0, 4000, 1, () -> false));

    private static final class Totals {
        final int wins, losses, kills, deaths, finalKills, finalDeaths, bedsBroken, bedsLost;
        /** Star level with the fraction when known (falls back to the whole star). */
        final double star;

        Totals(SessionHistory.Totals t) {
            this.wins = t.wins;
            this.losses = t.losses;
            this.kills = t.kills;
            this.deaths = t.deaths;
            this.finalKills = t.finalKills;
            this.finalDeaths = t.finalDeaths;
            this.bedsBroken = t.bedsBroken;
            this.bedsLost = t.bedsLost;
            this.star = t.star;
        }

        SessionHistory.Totals toData() {
            SessionHistory.Totals t = new SessionHistory.Totals();
            t.wins = wins;
            t.losses = losses;
            t.kills = kills;
            t.deaths = deaths;
            t.finalKills = finalKills;
            t.finalDeaths = finalDeaths;
            t.bedsBroken = bedsBroken;
            t.bedsLost = bedsLost;
            t.star = star;
            return t;
        }

        Totals(IntelPlayer p) {
            this.wins = p.wins;
            this.losses = p.losses;
            this.kills = p.kills;
            this.deaths = p.deaths;
            this.star = p.starExact > 0 ? p.starExact : p.star;
            this.finalKills = p.finalKills;
            this.finalDeaths = p.finalDeaths;
            this.bedsBroken = p.bedsBroken;
            this.bedsLost = p.bedsLost;
        }
    }

    private static final class Gained {
        int wins, losses, games, kills, deaths, finalKills, finalDeaths, bedsBroken, bedsLost;
        int starNow; // whole star level right now, only used to pick the glyph
        double fkdr, bblr, wlr, stars;

        boolean isEmpty() {
            return games == 0 && kills == 0 && deaths == 0 && finalKills == 0 && finalDeaths == 0
                    && bedsBroken == 0 && bedsLost == 0;
        }
    }

    private volatile Totals baseline;
    private volatile Totals latest;
    private volatile long startedAt = System.currentTimeMillis();
    private volatile long lastAutoAt;
    private volatile int announcedGames;
    private final AtomicBoolean busy = new AtomicBoolean(false);

    // Live counters filled from chat. Everything below is guarded by lock.
    private final Object lock = new Object();
    private int cWins, cLosses, cKills, cDeaths, cFinalKills, cFinalDeaths, cBedsBroken, cBedsLost;
    private long cXp; // Bed Wars XP gained, summed from the "+N Bed Wars Experience" chat lines
    private boolean gameActive;
    private boolean sawVictory;
    private final Set<String> winners = new HashSet<>();

    // Active time: only the time spent inside a game. Guarded by lock.
    private long activeAccumMs;   // time from games that already ended
    private long activeSince;     // when the running game started counting; 0 = not in a game
    private long gameStartedAt;   // when the running game really started; 0 = unknown (joined mid-game)
    private int timedGames;       // games that ran to their end, with a known start
    private long timedGamesMs;    // their total length

    public SessionStats() {
        super("SessionStats", true);
        restoreCurrent();
        // Keep the running session on disk when the game is closed.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            saveHistory();
            saveCurrent();
        }, "CoralIntel-Session-Save"));
    }

    private volatile long lastPersistAt;

    /** Picks the session up where the last run left it (until .reset starts a new one). */
    private void restoreCurrent() {
        try {
            SessionHistory.Current c = SessionHistory.loadCurrent();
            if (c == null) return;

            synchronized (lock) {
                cWins = c.wins;
                cLosses = c.losses;
                cKills = c.kills;
                cDeaths = c.deaths;
                cFinalKills = c.finalKills;
                cFinalDeaths = c.finalDeaths;
                cBedsBroken = c.bedsBroken;
                cBedsLost = c.bedsLost;
                cXp = c.xp;
                activeAccumMs = c.activeMs;
                activeSince = 0L;   // not in a game right after launch
                gameStartedAt = 0L;
                timedGames = c.timedGames;
                timedGamesMs = c.timedGamesMs;
            }
            startedAt = c.startedAt;
            if (c.baseline != null) baseline = new Totals(c.baseline);
            if (c.latest != null) latest = new Totals(c.latest);
        } catch (Throwable ignored) {
            // A broken file just means a fresh session.
        }
    }

    /** Writes the live session to disk so a restart carries on from here. */
    private void saveCurrent() {
        try {
            SessionHistory.Current c = new SessionHistory.Current();
            Totals b = baseline;
            Totals l = latest;
            synchronized (lock) {
                c.wins = cWins;
                c.losses = cLosses;
                c.kills = cKills;
                c.deaths = cDeaths;
                c.finalKills = cFinalKills;
                c.finalDeaths = cFinalDeaths;
                c.bedsBroken = cBedsBroken;
                c.bedsLost = cBedsLost;
                c.xp = cXp;
                c.timedGames = timedGames;
                c.timedGamesMs = timedGamesMs;
            }
            c.activeMs = activeMs();
            c.startedAt = startedAt;
            c.savedAt = System.currentTimeMillis();
            c.baseline = b == null ? null : b.toData();
            c.latest = l == null ? null : l.toData();
            SessionHistory.saveCurrent(c);
        } catch (Throwable ignored) {
            // best-effort
        }
        lastPersistAt = System.currentTimeMillis();
    }

    /** Called on every chat line: saves at most every 10 seconds, off the network thread. */
    private void saveCurrentSoon() {
        long now = System.currentTimeMillis();
        if (now - lastPersistAt < 10000L) return;
        lastPersistAt = now;
        new Thread(this::saveCurrent, "CoralIntel-Session-Persist").start();
    }

    /** Writes this session's summary to the history file (replaces its earlier entry). */
    private boolean saveHistory() {
        try {
            if (startedAt <= 0L) return false;
            Gained g = gained();
            if (g.isEmpty()) return false; // nothing happened, nothing to keep

            SessionHistory.Record r = new SessionHistory.Record();
            long now = System.currentTimeMillis();
            r.id = startedAt;
            r.endedAt = now;
            r.durationMs = now - startedAt;
            r.activeMs = activeMs();
            r.avgGameMs = avgGameMs();
            r.games = g.games;
            r.wins = g.wins;
            r.losses = g.losses;
            r.kills = g.kills;
            r.deaths = g.deaths;
            r.finalKills = g.finalKills;
            r.finalDeaths = g.finalDeaths;
            r.bedsBroken = g.bedsBroken;
            r.bedsLost = g.bedsLost;
            r.stars = g.stars;
            r.fkdr = g.fkdr;
            r.bblr = g.bblr;
            r.wlr = g.wlr;
            SessionHistory.upsert(r);
            return true;
        } catch (Throwable ignored) {
            // History is best-effort.
            return false;
        }
    }

    /** The "saved" line: a short, tag-free notice in your chat (nothing is sent to the server). */
    private void announceSaved(boolean ending) {
        if (!savedMessage.getValue() || mc.thePlayer == null) return;

        final String text;
        if (ending) {
            text = "&8&m   &r &b&lSESSION &8\u00bb &a\u2714 &7Previous session saved &8\u2022 &f.history &7to view";
        } else {
            Gained g = gained();
            boolean hide = StreamerMode.hidesStatsFor(ownName());
            text = "&8&m   &r &b&lSESSION &8\u00bb &a\u2714 &7Saved"
                    + (hide ? "" : " &8\u2022 &f" + g.games + " &7game" + (g.games == 1 ? "" : "s")
                    + " &8\u2022 &e+" + String.format(Locale.ROOT, "%.2f", g.stars) + "\u272B");
        }
        mc.addScheduledTask(() -> say(text));
    }

    @Override
    public void onEnabled() {
        if (baseline == null && mc.thePlayer != null) {
            captureBaseline(false);
        }
    }

    // -- Session control --------------------------------------------------

    /** Restarts the session: counters to zero, timer to now, stars baseline re-fetched. */
    public void start(final boolean announce) {
        boolean savedOld = saveHistory(); // keep the session that is ending before the counters are cleared
        if (savedOld) announceSaved(true);
        synchronized (lock) {
            cWins = cLosses = cKills = cDeaths = 0;
            cFinalKills = cFinalDeaths = cBedsBroken = cBedsLost = 0;
            cXp = 0L;
            winners.clear();
            sawVictory = false;

            activeAccumMs = 0L;
            if (activeSince != 0L) activeSince = System.currentTimeMillis();
            timedGames = 0;
            timedGamesMs = 0L;
        }
        startedAt = System.currentTimeMillis();
        announcedGames = 0;
        saveCurrent();

        Totals known = latest;
        if (known != null) {
            baseline = known; // stars restart right away; a fresh fetch follows below
        }

        if (trackFromChat.getValue()) {
            if (announce) say("&aSession reset. &7Counting from now.");
            captureBaseline(false);
        } else {
            captureBaseline(announce);
        }
    }

    /** Fetches your lifetime stats as the starting point for stars (and for API mode). */
    private void captureBaseline(final boolean announce) {
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
                    saveCurrent();
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
     * .session: prints the summary. With chat tracking it prints right away from the
     * live counters and refreshes stars in the background; in API mode it re-fetches
     * first. Automatic refreshes only print (API mode) when a game was played.
     */
    public void refresh(final boolean manual) {
        final boolean chat = trackFromChat.getValue();

        if (manual && chat) {
            say(summaryLine());
        }

        if (baseline == null) {
            captureBaseline(manual && !chat);
            return;
        }

        final String name = ownName();
        if (name == null) return;

        if (!busy.compareAndSet(false, true)) {
            if (manual && !chat) say("&7Still fetching your stats, try again in a moment.");
            return;
        }

        if (manual && !chat) say("&7Fetching your current stats...");

        new Thread(() -> {
            try {
                Totals totals = fetchTotals(name);
                if (totals != null) {
                    latest = totals;
                    saveCurrent();
                }

                final boolean ok = totals != null;
                mc.addScheduledTask(() -> {
                    if (chat) return; // chat mode prints at the end of each game; this only updates stars

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
        // A new world means the previous game is over (or was never entered).
        synchronized (lock) {
            gameActive = false;
            sawVictory = false;
            winners.clear();
        }
        endActive(false); // left the game (or never entered one): active time pauses

        if (!isEnabled()) return;

        final boolean first = baseline == null;
        long now = System.currentTimeMillis();

        if (!first) {
            // Stars come from the API, so keep them fresh for the HUD.
            boolean wantsRefresh = autoSummary.getValue() || showHud.getValue();
            if (!wantsRefresh || now - lastAutoAt < AUTO_MIN_GAP_MS) return;
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
                if (baseline == null) captureBaseline(false);
            } else {
                refresh(false);
            }
        }, "CoralIntel-Session-Auto").start();
    }

    // -- Live tracking from chat ------------------------------------------

    private static final Pattern CODES = Pattern.compile("\u00a7.");

    // "BED DESTRUCTION > Red Bed was destroyed by Name!"  /  "... > Your Bed was destroyed by Name!"
    private static final Pattern BED_PATTERN = Pattern.compile(
            "^BED DESTRUCTION > (\\w+) (?:Bed|Square|Star|Heart) .*\\b(?:by|for|to|seeing) (\\w{1,16})[!.']");

    // Final kill lines: "<victim> was killed by <killer>. FINAL KILL!"
    private static final Pattern FINAL_VICTIM = Pattern.compile("^(\\w{1,16})(?:'| )");
    private static final Pattern FINAL_NUMBERED = Pattern.compile("^\\w{1,16} was (\\w{1,16})'s final #[\\d,]+\\.");
    private static final Pattern KILLER_PLAIN = Pattern.compile(
            "\\b(?:by|fighting|to|for|with|from|of|against|meet) (\\w{1,16})[.!']");

    // Normal kill lines keep their colours: "(color)<victim> (grey)was killed by (color)<killer>(grey)."
    private static final Pattern KILL_RAW = Pattern.compile(
            "^\u00a7[0-9a-f](\\w{1,16}) \u00a77.*\\b(?:by|for|to|with|from|of|against|fighting|meet) \u00a7[0-9a-f](\\w{1,16})\u00a77[.!]$");
    // Deaths with no player killer: "(color)<victim> (grey)fell into the void."
    private static final Pattern DEATH_RAW = Pattern.compile("^\u00a7[0-9a-f](\\w{1,16}) \u00a77(.*)$");
    private static final Pattern NOT_A_DEATH = Pattern.compile(
            "(?i)\\b(?:disconnected|reconnected|joined|quit|respawn\\w*|eliminated)\\b");

    // End of game: one "Red - [MVP+] Name" line per member of the winning team.
    private static final Pattern WINNER_LINE = Pattern.compile(
            "^(?:Red|Blue|Green|Yellow|Aqua|White|Pink|Gray) - (?:\\[[^\\]]*\\]\\s*)?(\\w{1,16})$");

    // "+25 Bed Wars Experience (Final Kill)" / "+150 Bed Wars Experience (Game Win)"
    private static final Pattern XP_PATTERN = Pattern.compile(
            "^\\+([\\d,]+) Bed Wars Experience\\b");

    private void trackXp(String formatted) {
        String plain = CODES.matcher(formatted).replaceAll("").trim();
        Matcher m = XP_PATTERN.matcher(plain);
        if (!m.find()) return;
        try {
            long xp = Long.parseLong(m.group(1).replace(",", ""));
            synchronized (lock) {
                cXp += xp;
            }
        } catch (NumberFormatException ignored) {
            // not a number we can use
        }
    }

    /** Exact Bed Wars level for an experience total (same curve Hypixel uses). */
    private static double levelFromXp(long experience) {
        if (experience <= 0) return 0;
        final long prestigeExperience = 487000L;
        double level = (experience / prestigeExperience) * 100;
        long remaining = experience % prestigeExperience;
        int[] early = {500, 1000, 2000, 3500, 5000};
        for (int cost : early) {
            if (remaining < cost) return level + (double) remaining / cost;
            remaining -= cost;
            level++;
        }
        return level + remaining / 5000.0;
    }

    /** Inverse of levelFromXp: how much experience a (fractional) level is worth. */
    private static long xpFromLevel(double level) {
        if (level <= 0) return 0;
        final long prestigeExperience = 487000L;
        long prestiges = (long) (level / 100);
        double rem = level - prestiges * 100;
        long xp = prestiges * prestigeExperience;
        int[] early = {500, 1000, 2000, 3500};
        for (int cost : early) {
            if (rem < 1) return xp + Math.round(rem * cost);
            xp += cost;
            rem -= 1;
        }
        return xp + Math.round(rem * 5000);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!isEnabled()) return;
        if (event.getType() != EventType.RECEIVE) return;

        try {
            if (event.getPacket() instanceof S02PacketChat) {
                S02PacketChat packet = (S02PacketChat) event.getPacket();
                if (packet.getType() == 2) return; // action bar, not chat

                IChatComponent component = packet.getChatComponent();
                if (component != null) {
                    String formatted = component.getFormattedText();
                    trackGameState(formatted); // active time works with or without chat tracking
                    trackXp(formatted);        // stars come from the XP lines, with or without chat tracking
                    if (trackFromChat.getValue()) handleChat(formatted);
                    saveCurrentSoon();
                }
            } else if (trackFromChat.getValue() && event.getPacket() instanceof S45PacketTitle) {
                S45PacketTitle title = (S45PacketTitle) event.getPacket();
                if (title.getType() == S45PacketTitle.Type.TITLE && title.getMessage() != null) {
                    String text = CODES.matcher(title.getMessage().getUnformattedText()).replaceAll("");
                    if (text.contains("VICTORY")) {
                        synchronized (lock) {
                            if (gameActive) sawVictory = true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            IntelManager.dbg("[Session] chat parse error: " + e);
        }
    }

    /** Starts / stops the active-time clock from the game's own chat lines. */
    private void trackGameState(String formatted) {
        String plain = CODES.matcher(formatted.replace("\u00a7r", "")).replaceAll("").trim();

        // Player chat always has "Name:"; game events never do.
        if (plain.isEmpty() || plain.indexOf(':') >= 0) return;

        if (plain.contains("Protect your bed and destroy the enemy beds.")) {
            beginActive(true);
        } else if (plain.contains("You will respawn because you still have a bed!")
                || plain.startsWith("BED DESTRUCTION >")) {
            beginActive(false); // already in a game: reconnected or joined late
        } else if (plain.startsWith("1st Killer") || plain.contains("Reward Summary")) {
            endActive(true);
        }
    }

    private void beginActive(boolean realStart) {
        synchronized (lock) {
            long now = System.currentTimeMillis();
            if (activeSince == 0L) {
                activeSince = now;
                gameStartedAt = realStart ? now : 0L;
            } else if (realStart) {
                // A new game started before the old one was closed out: close it, don't time it.
                activeAccumMs += now - activeSince;
                activeSince = now;
                gameStartedAt = now;
            }
        }
    }

    /** @param finished true when the game ran to its end (its length then counts toward the average) */
    private void endActive(boolean finished) {
        synchronized (lock) {
            if (activeSince == 0L) return;

            long now = System.currentTimeMillis();
            activeAccumMs += now - activeSince;
            if (finished && gameStartedAt != 0L) {
                timedGames++;
                timedGamesMs += now - gameStartedAt;
            }
            activeSince = 0L;
            gameStartedAt = 0L;
        }
    }

    private long activeMs() {
        synchronized (lock) {
            return activeAccumMs + (activeSince != 0L ? System.currentTimeMillis() - activeSince : 0L);
        }
    }

    /** Average length of the games that ran to their end this session; -1 when there are none yet. */
    private long avgGameMs() {
        synchronized (lock) {
            return timedGames == 0 ? -1L : timedGamesMs / timedGames;
        }
    }

    private void handleChat(String formatted) {
        String raw = formatted.replace("\u00a7r", "");
        String plain = CODES.matcher(raw).replaceAll("").trim();

        // Player chat, shouts and party chat always have "Name:"; game events never do.
        if (plain.isEmpty() || plain.indexOf(':') >= 0) return;

        // A game is starting (or you are back in one after a respawn / reconnect).
        if (plain.contains("Protect your bed and destroy the enemy beds.")) {
            synchronized (lock) {
                gameActive = true;
                sawVictory = false;
                winners.clear();
            }
            return;
        }
        if (plain.contains("You will respawn because you still have a bed!")) {
            synchronized (lock) {
                gameActive = true;
            }
            return;
        }

        // Beds. Only exist in a game, so they also mark one as running (e.g. after a reconnect).
        if (plain.startsWith("BED DESTRUCTION >")) {
            Matcher m = BED_PATTERN.matcher(plain);
            if (m.find()) {
                boolean yours = m.group(1).equalsIgnoreCase("Your");
                boolean broken = !yours && isMe(m.group(2));
                synchronized (lock) {
                    gameActive = true;
                    if (yours) cBedsLost++;
                    else if (broken) cBedsBroken++;
                }
                if (yours || broken) {
                    IntelManager.dbg("[Session] " + (yours ? "bed lost" : "bed broken") + ": " + plain);
                }
            }
            return;
        }

        // Final kills / final deaths. Kept apart from normal kills / deaths below.
        if (plain.endsWith("FINAL KILL!")) {
            handleFinalKill(plain);
            return;
        }

        boolean active;
        synchronized (lock) {
            active = gameActive;
        }
        if (!active) return;

        // End of game: winners first, then the "1st Killer" block / reward summary.
        Matcher winner = WINNER_LINE.matcher(plain);
        if (winner.find()) {
            synchronized (lock) {
                winners.add(winner.group(1).toLowerCase(Locale.ROOT));
            }
            return;
        }
        if (plain.startsWith("1st Killer") || plain.contains("Reward Summary")) {
            finishGame();
            return;
        }

        // Normal kills / deaths (not final).
        Matcher kill = KILL_RAW.matcher(raw);
        if (kill.find()) {
            boolean killedMe = isMe(kill.group(1));
            boolean killerMe = !killedMe && isMe(kill.group(2));
            synchronized (lock) {
                gameActive = true;
                if (killedMe) cDeaths++;
                else if (killerMe) cKills++;
            }
            if (killedMe || killerMe) {
                IntelManager.dbg("[Session] " + (killedMe ? "death" : "kill") + ": " + plain);
            }
            return;
        }

        // Died with no player involved (void, fall, a golem...). Only counts when it's you.
        Matcher death = DEATH_RAW.matcher(raw);
        if (death.find() && isMe(death.group(1))) {
            String rest = CODES.matcher(death.group(2)).replaceAll("").trim();
            if ((rest.endsWith(".") || rest.endsWith("!")) && !NOT_A_DEATH.matcher(rest).find()) {
                synchronized (lock) {
                    cDeaths++;
                }
                IntelManager.dbg("[Session] death: " + plain);
            }
        }
    }

    private void handleFinalKill(String plain) {
        String body = plain.substring(0, plain.length() - "FINAL KILL!".length()).trim();

        Matcher victimMatcher = FINAL_VICTIM.matcher(body);
        if (!victimMatcher.find()) return;
        String victim = victimMatcher.group(1);

        String killer = null;
        Matcher numbered = FINAL_NUMBERED.matcher(body);
        if (numbered.find()) {
            killer = numbered.group(1);
        } else {
            Matcher killerMatcher = KILLER_PLAIN.matcher(body);
            while (killerMatcher.find()) {
                killer = killerMatcher.group(1); // the last "by Name." wins
            }
        }

        boolean victimMe = isMe(victim);
        boolean killerMe = !victimMe && killer != null && isMe(killer);

        synchronized (lock) {
            gameActive = true;
            if (victimMe) cFinalDeaths++;
            else if (killerMe) cFinalKills++;
        }

        if (victimMe || killerMe) {
            IntelManager.dbg("[Session] " + (victimMe ? "final death" : "final kill") + ": " + plain);
        }
    }

    private void finishGame() {
        boolean counted;
        boolean won;

        synchronized (lock) {
            if (!gameActive) return;
            gameActive = false;

            boolean onWinningTeam = false;
            for (String name : winners) {
                if (isMe(name)) {
                    onWinningTeam = true;
                    break;
                }
            }

            won = sawVictory || onWinningTeam;
            counted = sawVictory || !winners.isEmpty();
            if (counted) {
                if (won) cWins++;
                else cLosses++;
            }

            winners.clear();
            sawVictory = false;
        }

        IntelManager.dbg("[Session] game over: " + (counted ? (won ? "win" : "loss") : "result unknown, not counted"));
        afterGame();
    }

    /** Prints the summary a moment after the end-of-game spam, then refreshes stars. */
    private void afterGame() {
        new Thread(() -> {
            try {
                Thread.sleep(1500L);
            } catch (InterruptedException ignored) {
                return;
            }

            if (!isEnabled()) return;

            mc.addScheduledTask(() -> {
                String name = ownName();
                if (autoSummary.getValue() && !StreamerMode.hidesStatsFor(name)) {
                    say(summaryLine());
                }
            });

            try {
                Thread.sleep(9000L);
            } catch (InterruptedException ignored) {
                return;
            }

            if (isEnabled()) refresh(false); // stars only; Hypixel's API may need longer
        }, "CoralIntel-Session-GameEnd").start();
        new Thread(() -> {
            try {
                Thread.sleep(2500L); // after the last game result has been counted
            } catch (InterruptedException ignored) {
                return;
            }
            if (isEnabled()) {
                boolean saved = saveHistory();
                saveCurrent();
                if (saved) announceSaved(false);
            }
        }, "CoralIntel-Session-History").start();
    }

    /** Is this name you? Also checks your tab-list name in case you're nicked. */
    private static boolean isMe(String name) {
        if (name == null) return false;

        String real = ownName();
        if (real != null && name.equalsIgnoreCase(real)) return true;

        try {
            if (mc.thePlayer != null && mc.getNetHandler() != null) {
                NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
                if (info != null && info.getGameProfile() != null
                        && name.equalsIgnoreCase(info.getGameProfile().getName())) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }

        return false;
    }

    // -- Text -------------------------------------------------------------

    /** Colored chat line, or a "not started" notice. */
    public String summaryLine() {
        if (!hasSession()) {
            return "&7No session yet. Run &f.reset &7to start one.";
        }

        Gained g = gained();
        StringBuilder line = new StringBuilder("&bThis session &7\u00bb ");

        if (g.isEmpty()) {
            return line.append("&7no games yet &8(").append(duration())
                    .append(showActive.getValue() ? ", " + fmtMinutes(activeMs()) + " active" : "")
                    .append(")").toString();
        }

        if (showWins.getValue()) {
            line.append("&7Wins &f+").append(g.wins).append("  ");
        }
        if (showKills.getValue()) {
            line.append("&7Kills &f+").append(g.kills).append("  ");
        }
        if (showDeaths.getValue()) {
            line.append("&7Deaths &f+").append(g.deaths).append("  ");
        }
        if (showFinals.getValue()) {
            line.append("&7Finals &f+").append(g.finalKills).append("  ");
        }
        if (showFkdr.getValue()) {
            String code = IntelColors.nearestCode(IntelColors.getStatColor(g.fkdr, 3, 6));
            line.append("&7FKDR ").append(code).append(fmt(g.fkdr)).append("  ");
        }
        if (showBeds.getValue()) {
            line.append("&7Beds &f+").append(g.bedsBroken).append("  ");
        }
        if (showBblr.getValue()) {
            line.append("&7BBLR &f").append(fmt(g.bblr)).append("  ");
        }
        if (showWlr.getValue()) {
            String code = IntelColors.nearestCode(IntelColors.getStatColor(g.wlr, 2, 4));
            line.append("&7WLR ").append(code).append(fmt(g.wlr)).append("  ");
        }

        if (showStars.getValue()) {
            line.append("&7Stars &f+").append(fmt(g.stars)).append(PrestigeUtil.glyphColored(g.starNow)).append("  ");
        }

        line.append("&8(").append(g.games).append(g.games == 1 ? " game, " : " games, ")
                .append(duration());
        if (showActive.getValue()) line.append(", ").append(fmtMinutes(activeMs())).append(" active");
        if (showAvgGame.getValue() && avgGameMs() >= 0) {
            line.append(", avg ").append(fmtClock(avgGameMs()));
        }
        line.append(")");
        return line.toString();
    }

    /** Two plain-text lines for the ClickGUI panel (kept short to fit the panel). */
    public String[] guiLines() {
        if (!hasSession()) {
            return new String[]{"Not started yet", "Starts after you join a server"};
        }

        Gained g = gained();
        StringBuilder timing = new StringBuilder();
        StringBuilder first = new StringBuilder();
        StringBuilder second = new StringBuilder();

        if (showTime.getValue()) timing.append("Session ").append(duration()).append("  ");
        if (showActive.getValue()) timing.append("Active ").append(fmtMinutes(activeMs())).append("  ");
        if (showAvgGame.getValue()) {
            long avg = avgGameMs();
            timing.append("Avg game ").append(avg < 0 ? "--" : fmtClock(avg));
        }

        if (showWins.getValue()) first.append("Wins +").append(g.wins).append("  ");
        if (showKills.getValue()) first.append("Kills +").append(g.kills).append("  ");
        first.append("Games ").append(g.games);

        if (showFkdr.getValue()) second.append("FKDR ").append(fmt(g.fkdr)).append("  ");
        if (showBblr.getValue()) second.append("BBLR ").append(fmt(g.bblr)).append("  ");

        if (timing.toString().trim().isEmpty()) {
            return new String[]{first.toString(), second.toString().trim()};
        }
        return new String[]{timing.toString().trim(), first.toString(), second.toString().trim()};
    }

    // -- HUD --------------------------------------------------------------

    private static final String RESET_LABEL = "[Reset Session]";

    // Bounds from the last draw (scaled GUI pixels), used for clicking and dragging.
    private int boxX, boxY, boxW, boxH;
    private int btnX, btnY, btnW, btnH;
    private boolean dragging;
    private int dragOffX, dragOffY;
    /** True while the ClickGUI has Ctrl held: the box gets an outline so it's easy to see. */
    private boolean placing;

    /** Normal in-game drawing; the inventory draws it itself (see SessionHudEvents). */
    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.currentScreen != null && !(mc.currentScreen instanceof GuiChat)) return;
        if (mc.gameSettings.showDebugInfo) return;
        drawHud(false, 0, 0);
    }

    /** Draws the box, plus the reset button when the inventory is open. */
    public void drawHud(boolean inventory, int mouseX, int mouseY) {
        btnW = 0;
        boxW = 0;

        if (!isEnabled() || !showHud.getValue() || mc.thePlayer == null) return;

        FontRenderer font = mc.fontRendererObj;
        List<String> lines = hudLines();
        if (lines.isEmpty()) return; // every line is turned off

        int pad = 4;
        int lineH = font.FONT_HEIGHT + 2;
        int textW = 0;
        for (String line : lines) {
            textW = Math.max(textW, font.getStringWidth(line));
        }

        int w = textW + pad * 2;
        int h = lines.size() * lineH - 2 + pad * 2;

        ScaledResolution sr = new ScaledResolution(mc);
        // Right-aligned: HUD X is the box's RIGHT edge, so it grows leftwards as lines change.
        int wantX = alignRight.getValue() ? (int) hudX.getValue() - w : (int) hudX.getValue();
        int x = clamp(wantX, 0, Math.max(0, sr.getScaledWidth() - w));
        int y = clamp((int) hudY.getValue(), 0, Math.max(0, sr.getScaledHeight() - h));

        if (hudBackground.getValue()) {
            Gui.drawRect(x, y, x + w, y + h, 0x90000000);
            if (alignRight.getValue()) {
                Gui.drawRect(x + w - 1, y, x + w, y + h, 0xFF55FFFF);
            } else {
                Gui.drawRect(x, y, x + 1, y + h, 0xFF55FFFF);
            }
        }

        if (placing && !inventory) {
            int c = 0xFF55FFFF;
            Gui.drawRect(x - 1, y - 1, x + w + 1, y, c);
            Gui.drawRect(x - 1, y + h, x + w + 1, y + h + 1, c);
            Gui.drawRect(x - 1, y, x, y + h, c);
            Gui.drawRect(x + w, y, x + w + 1, y + h, c);
        }

        for (int i = 0; i < lines.size(); i++) {
            String text = lines.get(i);
            int tx = alignRight.getValue() ? x + w - pad - font.getStringWidth(text) : x + pad;
            font.drawStringWithShadow(text, tx, y + pad + i * lineH, 0xFFFFFFFF);
        }

        boxX = x;
        boxY = y;
        boxW = w;
        boxH = h;

        if (!inventory) return;

        int bw = font.getStringWidth(RESET_LABEL) + 8;
        int bh = font.FONT_HEIGHT + 4;
        int bx = alignRight.getValue() ? x + w - bw : x;
        int by = y + h + 3;
        if (by + bh > sr.getScaledHeight()) {
            by = y - bh - 3; // no room underneath, put it above the box
        }

        boolean hover = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh;
        Gui.drawRect(bx, by, bx + bw, by + bh, hover ? 0xD0404050 : 0xA0000000);
        font.drawStringWithShadow((hover ? "\u00a7a" : "\u00a7f") + RESET_LABEL, bx + 4, by + 2, 0xFFFFFFFF);

        btnX = bx;
        btnY = by;
        btnW = bw;
        btnH = bh;
    }

    // -- Placing the HUD from the ClickGUI (Ctrl+click / Ctrl+drag) -------

    public void setPlacing(boolean placing) {
        this.placing = placing;
    }

    /**
     * Ctrl+click in the ClickGUI. Clicking on the box grabs it where you clicked;
     * clicking anywhere else puts the box's center on the cursor. Keep dragging to
     * fine-tune. @return false when the HUD isn't showing (nothing to move).
     */
    public boolean beginMove(int mx, int my) {
        if (!isEnabled() || !showHud.getValue() || boxW <= 0) return false;

        boolean inside = mx >= boxX && mx < boxX + boxW && my >= boxY && my < boxY + boxH;
        dragOffX = inside ? mx - boxX : boxW / 2;
        dragOffY = inside ? my - boxY : boxH / 2;
        dragging = true;
        onInventoryDrag(mx, my);
        return true;
    }

    public void moveTo(int mx, int my) {
        onInventoryDrag(mx, my);
    }

    public void endMove() {
        dragging = false;
    }

    /** Mouse press in the inventory. @return true if it was ours (the click is then swallowed). */
    public boolean onInventoryPress(int mx, int my) {
        if (boxW <= 0) return false;

        if (btnW > 0 && mx >= btnX && mx < btnX + btnW && my >= btnY && my < btnY + btnH) {
            start(true);
            return true;
        }

        if (mx >= boxX && mx < boxX + boxW && my >= boxY && my < boxY + boxH) {
            dragging = true;
            dragOffX = mx - boxX;
            dragOffY = my - boxY;
            return true;
        }

        return false;
    }

    /** Mouse moved with the button held. @return true while a drag is in progress. */
    public boolean onInventoryDrag(int mx, int my) {
        if (!dragging) return false;
        int left = mx - dragOffX;
        hudX.setValue(alignRight.getValue() ? left + boxW : left); // right mode stores the right edge
        hudY.setValue(my - dragOffY);
        return true;
    }

    /** Mouse released. @return true if a drag just ended. */
    public boolean onInventoryRelease() {
        boolean was = dragging;
        dragging = false;
        return was;
    }

    /** The HUD lines (only the stats that are switched on), color-formatted, in the ClickGUI order. */
    private List<String> hudLines() {
        List<String> out = new ArrayList<>();

        if (!hasSession()) {
            out.add(ChatColors.formatColor("&7Session: &fstarting..."));
            return out;
        }

        Gained g = gained();

        // null = that whole line is switched off. The right-hand stat on each line
        // (Deaths, FKDR, BBLR, WLR) is always green.
        long avg = avgGameMs();
        String[] text = {
                join(showTime.getValue() ? "&7Session Time: &b" + duration() : null,
                        showActive.getValue() ? "&7Active: &b" + fmtMinutes(activeMs()) : null),
                showAvgGame.getValue() ? "&7Avg Game: &b" + (avg < 0 ? "--" : fmtClock(avg)) : null,
                join(showFinals.getValue() ? "&7Finals: &f" + g.finalKills : null,
                        showFkdr.getValue() ? "&7FKDR: &a" + fmt(g.fkdr) : null),
                join(showBeds.getValue() ? "&7Beds: &f" + g.bedsBroken : null,
                        showBblr.getValue() ? "&7BBLR: &a" + fmt(g.bblr) : null),
                join(showWins.getValue() ? "&7Wins: &f" + g.wins : null,
                        showWlr.getValue() ? "&7WLR: &a" + fmt(g.wlr) : null),
                join(showKills.getValue() ? "&7Kills: &f" + g.kills : null,
                        showDeaths.getValue() ? "&7Deaths: &a" + g.deaths : null),
                showStars.getValue()
                        ? "&7Stars: &f+" + fmt(g.stars) + PrestigeUtil.glyphColored(g.starNow) : null
        };
        double[] order = {
                orderTime.getValue(), orderAvgGame.getValue(), orderFinals.getValue(), orderBeds.getValue(),
                orderWins.getValue(), orderKills.getValue(), orderStars.getValue()
        };

        // Selection sort on (order value, default position): tiny list, keeps ties stable.
        boolean[] used = new boolean[text.length];
        for (int n = 0; n < text.length; n++) {
            int best = -1;
            for (int i = 0; i < text.length; i++) {
                if (used[i] || text[i] == null) continue;
                if (best == -1 || order[i] < order[best]) {
                    best = i;
                }
            }
            if (best == -1) break;
            used[best] = true;
            out.add(ChatColors.formatColor(text[best]));
        }

        return out;
    }

    /** Joins the parts that are on with a dim slash; null when none are. */
    private static String join(String first, String second) {
        if (first == null) return second;
        if (second == null) return first;
        return first + " &8/ " + second;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // -- Internals --------------------------------------------------------

    /** True once there is something to show: always with chat tracking, else once stats loaded. */
    private boolean hasSession() {
        return trackFromChat.getValue() || (baseline != null && latest != null);
    }

    private Gained gained() {
        Totals b = baseline;
        Totals l = latest;
        Gained g = new Gained();

        if (trackFromChat.getValue()) {
            // Live counters from chat. Each stat has its own counter.
            synchronized (lock) {
                g.wins = cWins;
                g.losses = cLosses;
                g.kills = cKills;
                g.deaths = cDeaths;
                g.finalKills = cFinalKills;
                g.finalDeaths = cFinalDeaths;
                g.bedsBroken = cBedsBroken;
                g.bedsLost = cBedsLost;
            }
        } else if (b != null && l != null) {
            // API mode: current lifetime totals minus the baseline.
            g.wins = diff(l.wins, b.wins);
            g.losses = diff(l.losses, b.losses);
            g.kills = diff(l.kills, b.kills);
            g.deaths = diff(l.deaths, b.deaths);
            g.finalKills = diff(l.finalKills, b.finalKills);
            g.finalDeaths = diff(l.finalDeaths, b.finalDeaths);
            g.bedsBroken = diff(l.bedsBroken, b.bedsBroken);
            g.bedsLost = diff(l.bedsLost, b.bedsLost);
        }

        // Stars come from the "+N Bed Wars Experience" chat lines: the XP gained this
        // session added to the XP you started with. The API difference is only the
        // fallback while no XP line has been seen yet (or Track From Chat is off).
        long xpGained;
        synchronized (lock) {
            xpGained = cXp;
        }
        if (b != null && b.star > 0 && (xpGained > 0 || trackFromChat.getValue())) {
            long baseXp = xpFromLevel(b.star);
            double nowLevel = levelFromXp(baseXp + xpGained);
            g.stars = Math.max(0.0, nowLevel - b.star);
            g.starNow = (int) nowLevel;
        } else if (b != null && l != null) {
            g.stars = Math.max(0.0, l.star - b.star);
            g.starNow = (int) l.star;
        }

        g.games = g.wins + g.losses;
        g.fkdr = g.finalDeaths == 0 ? g.finalKills : (double) g.finalKills / g.finalDeaths;
        g.bblr = g.bedsLost == 0 ? g.bedsBroken : (double) g.bedsBroken / g.bedsLost;
        g.wlr = g.losses == 0 ? g.wins : (double) g.wins / g.losses;
        return g;
    }

    private static int diff(int now, int then) {
        return Math.max(0, now - then);
    }

    private String duration() {
        return fmtMinutes(System.currentTimeMillis() - startedAt);
    }

    /** 48m / 1h 12m. */
    private static String fmtMinutes(long ms) {
        long minutes = Math.max(0L, ms / 60000L);
        if (minutes < 60) return minutes + "m";
        return (minutes / 60) + "h " + (minutes % 60) + "m";
    }

    /** 14m 10s, for the average game length. */
    private static String fmtClock(long ms) {
        long seconds = Math.max(0L, ms / 1000L);
        return (seconds / 60) + "m " + (seconds % 60) + "s";
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
