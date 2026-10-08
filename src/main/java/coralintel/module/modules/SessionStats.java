package coralintel.module.modules;

import coralintel.enums.ChatColors;
import coralintel.event.EventTarget;
import coralintel.events.LoadWorldEvent;
import coralintel.events.Render2DEvent;
import coralintel.module.BooleanSetting;
import coralintel.module.Module;
import coralintel.module.SliderSetting;
import coralintel.ui.intel.IntelColors;
import coralintel.ui.intel.IntelManager;
import coralintel.ui.intel.IntelPlayer;
import coralintel.util.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiInventory;

import java.util.ArrayList;
import java.util.List;
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
 * HUD: a small box on screen with the session time, finals / FKDR, beds / BBLR
 * and wins / WLR. The order of the four lines is set in the ClickGUI (Order: ...).
 * Open your inventory to get a [Reset Session] button and to drag the box to
 * a new spot (the position is saved with the other settings).
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

    public final BooleanSetting showHud =
            register(new BooleanSetting("Show HUD", true));
    // Line order for the HUD: lowest number goes on top (ties keep the default order).
    public final SliderSetting orderTime =
            register(new SliderSetting("Order: Session Time", 1, 1, 4, 1));
    public final SliderSetting orderFinals =
            register(new SliderSetting("Order: Finals / FKDR", 2, 1, 4, 1));
    public final SliderSetting orderBeds =
            register(new SliderSetting("Order: Beds / BBLR", 3, 1, 4, 1));
    public final SliderSetting orderWins =
            register(new SliderSetting("Order: Wins / WLR", 4, 1, 4, 1));
    // Saved with the other settings but dragged in the inventory, so not shown in the ClickGUI.
    private final SliderSetting hudX =
            register(new SliderSetting("HUD X", 6, 0, 4000, 1, () -> false));
    private final SliderSetting hudY =
            register(new SliderSetting("HUD Y", 40, 0, 4000, 1, () -> false));

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
        int wins, losses, games, kills, finalKills, finalDeaths, bedsBroken, bedsLost;
        double fkdr, bblr, wlr;

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
            // The HUD needs fresh numbers too, not just the chat summary.
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

    // ── HUD ──────────────────────────────────────────────────────────────

    private static final String RESET_LABEL = "[Reset Session]";

    // Bounds from the last draw (scaled GUI pixels), used for clicking and dragging.
    private int boxX, boxY, boxW, boxH;
    private int btnX, btnY, btnW, btnH;
    private boolean dragging;
    private int dragOffX, dragOffY;

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

        int pad = 4;
        int lineH = font.FONT_HEIGHT + 2;
        int textW = 0;
        for (String line : lines) {
            textW = Math.max(textW, font.getStringWidth(line));
        }

        int w = textW + pad * 2;
        int h = lines.size() * lineH - 2 + pad * 2;

        ScaledResolution sr = new ScaledResolution(mc);
        int x = clamp((int) hudX.getValue(), 0, Math.max(0, sr.getScaledWidth() - w));
        int y = clamp((int) hudY.getValue(), 0, Math.max(0, sr.getScaledHeight() - h));

        Gui.drawRect(x, y, x + w, y + h, 0x90000000);
        Gui.drawRect(x, y, x + 1, y + h, 0xFF55FFFF);

        for (int i = 0; i < lines.size(); i++) {
            font.drawStringWithShadow(lines.get(i), x + pad, y + pad + i * lineH, 0xFFFFFFFF);
        }

        boxX = x;
        boxY = y;
        boxW = w;
        boxH = h;

        if (!inventory) return;

        int bw = font.getStringWidth(RESET_LABEL) + 8;
        int bh = font.FONT_HEIGHT + 4;
        int bx = x;
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
        hudX.setValue(mx - dragOffX);
        hudY.setValue(my - dragOffY);
        return true;
    }

    /** Mouse released. @return true if a drag just ended. */
    public boolean onInventoryRelease() {
        boolean was = dragging;
        dragging = false;
        return was;
    }

    /** The four HUD lines, already color-formatted, in the order set in the ClickGUI. */
    private List<String> hudLines() {
        List<String> out = new ArrayList<>();

        if (baseline == null || latest == null) {
            out.add(ChatColors.formatColor("&7Session: &fstarting..."));
            return out;
        }

        Gained g = gained();
        String fkdrCode = IntelColors.nearestCode(IntelColors.getStatColor(g.fkdr, 3, 6));
        String wlrCode = IntelColors.nearestCode(IntelColors.getStatColor(g.wlr, 2, 4));

        String[] text = {
                "&7Session Time: &b" + duration(),
                "&7Finals: &f" + g.finalKills + " &8/ &7FKDR: " + fkdrCode + fmt(g.fkdr),
                "&7Beds: &f" + g.bedsBroken + " &8/ &7BBLR: &f" + fmt(g.bblr),
                "&7Wins: &f" + g.wins + " &8/ &7WLR: " + wlrCode + fmt(g.wlr)
        };
        double[] order = {
                orderTime.getValue(), orderFinals.getValue(), orderBeds.getValue(), orderWins.getValue()
        };

        // Selection sort on (order value, default position): tiny list, keeps ties stable.
        boolean[] used = new boolean[4];
        for (int n = 0; n < 4; n++) {
            int best = -1;
            for (int i = 0; i < 4; i++) {
                if (!used[i] && (best == -1 || order[i] < order[best])) {
                    best = i;
                }
            }
            used[best] = true;
            out.add(ChatColors.formatColor(text[best]));
        }

        return out;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // ── Internals ────────────────────────────────────────────────────────

    private Gained gained() {
        Totals b = baseline;
        Totals l = latest;
        Gained g = new Gained();
        if (b == null || l == null) return g;

        g.wins = diff(l.wins, b.wins);
        g.losses = diff(l.losses, b.losses);
        g.games = g.wins + g.losses;
        g.kills = diff(l.kills, b.kills);
        g.finalKills = diff(l.finalKills, b.finalKills);
        g.finalDeaths = diff(l.finalDeaths, b.finalDeaths);
        g.bedsBroken = diff(l.bedsBroken, b.bedsBroken);
        g.bedsLost = diff(l.bedsLost, b.bedsLost);
        g.fkdr = g.finalDeaths == 0 ? g.finalKills : (double) g.finalKills / g.finalDeaths;
        g.bblr = g.bedsLost == 0 ? g.bedsBroken : (double) g.bedsBroken / g.bedsLost;
        g.wlr = g.losses == 0 ? g.wins : (double) g.wins / g.losses;
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
