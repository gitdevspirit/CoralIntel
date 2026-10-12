package coralintel.duels;

import coralintel.ui.clickgui.RoundedUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Duels lobby HUD: one row per player with the Duels rank, WLR, KDR and winstreaks of the chosen game
 * (Overall, Classic, Bridge or Bedwars Duels). Own class so it never shares layout or state with the Bedwars HUD.
 */
public class DuelsHudOverlay {
    private final Minecraft mc = Minecraft.getMinecraft();

    // Settings, pushed by the DuelsIntel module every frame.
    public boolean enabled = true;
    public int x = 10, y = 100;
    public float scale = 1.0f;
    public int maxPlayers = 10;
    public int bgOpacity = 180;
    public int bgRgb = 0x07070E;
    public int accentRgb = 0xFFFFFF;
    public String style = "classic";     // classic, minimal, glass, outline
    public String sort = "wins";         // wins, wlr, kdr, name
    public int game = 0;                 // index into DuelsPlayer.GAMES
    public boolean showRank = true, showWins = true, showWlr = true, showKdr = true, showStreak = true,
            showBestStreak = false;
    public int columnWidthPercent = 100;
    public boolean showHeads = false;    // reserved: heads are not drawn in the Duels HUD yet

    private static final int ROW_H = 12;
    private static final int PAD = 4;

    public boolean isEnabled() {
        return enabled;
    }

    public void render() {
        if (!enabled) return;
        List<DuelsPlayer> list = DuelsManager.getInstance().getPlayers();
        if (list.isEmpty()) return;

        List<DuelsPlayer> rows = sorted(list);
        if (rows.size() > maxPlayers) rows = new ArrayList<>(rows.subList(0, maxPlayers));

        FontRenderer fr = mc.fontRendererObj;
        float colScale = columnWidthPercent / 100f;

        // Column layout: name, then the enabled stat columns, each as wide as its widest cell + gap.
        List<String> heads = new ArrayList<>();
        if (showRank) heads.add("Rank");
        if (showWins) heads.add("Wins");
        if (showWlr) heads.add("WLR");
        if (showKdr) heads.add("KDR");
        if (showStreak) heads.add("WS");
        if (showBestStreak) heads.add("BWS");

        int nameW = fr.getStringWidth("Name");
        for (DuelsPlayer p : rows) nameW = Math.max(nameW, fr.getStringWidth(nameText(p)));

        int[] colW = new int[heads.size()];
        for (int c = 0; c < heads.size(); c++) {
            int w = fr.getStringWidth(heads.get(c));
            for (DuelsPlayer p : rows) w = Math.max(w, fr.getStringWidth(strip(cell(p, heads.get(c)))));
            colW[c] = (int) ((w + 8) * colScale);
        }

        int totalW = PAD * 2 + nameW + 6;
        for (int w : colW) totalW += w;
        int totalH = PAD * 2 + ROW_H * (rows.size() + 1);

        ScaledResolution sr = new ScaledResolution(mc);
        GlStateManager.pushMatrix();
        GlStateManager.translate(Math.min(x, sr.getScaledWidth() - 20), Math.min(y, sr.getScaledHeight() - 20), 0);
        GlStateManager.scale(scale, scale, 1f);

        drawBackground(totalW, totalH);

        int headerColor = 0xFF000000 | accentRgb;
        int cy = PAD;
        fr.drawStringWithShadow("Name", PAD, cy, headerColor);
        int cx = PAD + nameW + 6;
        for (int c = 0; c < heads.size(); c++) {
            fr.drawStringWithShadow(heads.get(c), cx, cy, headerColor);
            cx += colW[c];
        }
        cy += ROW_H;

        int rowIndex = 0;
        for (DuelsPlayer p : rows) {
            if (style.equals("classic") && rowIndex % 2 == 1) {
                RoundedUtils.drawRoundedRect(2, cy - 1, totalW - 4, ROW_H, 2, 0x22FFFFFF);
            }
            fr.drawStringWithShadow(nameText(p), PAD, cy, 0xFFFFFFFF);
            cx = PAD + nameW + 6;
            for (int c = 0; c < heads.size(); c++) {
                fr.drawStringWithShadow(cell(p, heads.get(c)), cx, cy, 0xFFFFFFFF);
                cx += colW[c];
            }
            cy += ROW_H;
            rowIndex++;
        }

        GlStateManager.popMatrix();
        GlStateManager.resetColor();
    }

    private void drawBackground(int w, int h) {
        int alpha = Math.max(0, Math.min(255, bgOpacity));
        int bg = (alpha << 24) | (bgRgb & 0xFFFFFF);
        switch (style) {
            case "minimal":
                RoundedUtils.drawRoundedRect(0, 0, w, h, 2, (alpha / 2 << 24) | (bgRgb & 0xFFFFFF));
                break;
            case "glass":
                RoundedUtils.drawRoundedRect(0, 0, w, h, 6, ((int) (alpha * 0.6f) << 24) | (bgRgb & 0xFFFFFF));
                RoundedUtils.drawRoundedOutline(0, 0, w, h, 6, 1f, 0x55FFFFFF);
                break;
            case "outline":
                RoundedUtils.drawRoundedRect(0, 0, w, h, 4, bg);
                RoundedUtils.drawRoundedOutline(0, 0, w, h, 4, 1.5f, 0xFF000000 | accentRgb);
                break;
            case "classic":
            default:
                RoundedUtils.drawRoundedRect(0, 0, w, h, 4, bg);
                RoundedUtils.drawRoundedOutline(0, 0, w, h, 4, 1f, 0x66FFFFFF);
                break;
        }
    }

    // ------------------------------------------------------------------ cells

    private String nameText(DuelsPlayer p) {
        if (p.isNicked) {
            String real = p.realName;
            return "\u00A75[NICK] \u00A7f" + p.name + (real != null ? " \u00A77(" + real + ")" : "");
        }
        String prefix = p.rankPrefix == null || p.rankPrefix.isEmpty() ? "\u00A77" : p.rankPrefix + " ";
        return prefix + p.name;
    }

    private String cell(DuelsPlayer p, String column) {
        if (p.isNicked) return "\u00A78-";
        if (p.loading || !p.duelsLoaded) {
            if (p.statsHidden) return "\u00A7c?";
            return "\u00A78...";
        }
        DuelsPlayer.Mode m = p.mode(game);
        switch (column) {
            case "Rank":
                return DuelsRank.colored(m.wins);
            case "Wins":
                return "\u00A7f" + m.wins;
            case "WLR":
                return ratio(m.wlr(), 1, 2, 4);
            case "KDR":
                return ratio(m.kdr(), 1, 2, 4);
            case "WS":
                return count(m.winstreak, 3, 10, 25);
            case "BWS":
                return count(m.bestWinstreak, 5, 15, 40);
            default:
                return "";
        }
    }

    private static String ratio(double value, double good, double great, double insane) {
        String color = value >= insane ? "\u00A74" : value >= great ? "\u00A7c" : value >= good ? "\u00A7e" : "\u00A77";
        return color + String.format(Locale.ROOT, "%.2f", value);
    }

    private static String count(int value, int good, int great, int insane) {
        String color = value >= insane ? "\u00A74" : value >= great ? "\u00A7c" : value >= good ? "\u00A7e" : "\u00A77";
        return color + value;
    }

    private static String strip(String s) {
        return net.minecraft.util.StringUtils.stripControlCodes(s);
    }

    // ------------------------------------------------------------------ sorting

    /** Sort values captured once per sort, so stats landing on other threads can't change them mid-sort. */
    private static final class SortKey {
        final DuelsPlayer player;
        final String name;
        final int wins;
        final double wlr, kdr;

        SortKey(DuelsPlayer p, int game) {
            DuelsPlayer.Mode m = p.mode(game);
            this.player = p;
            this.name = p.name == null ? "" : p.name;
            this.wins = m.wins;
            this.wlr = m.wlr();
            this.kdr = m.kdr();
        }
    }

    private List<DuelsPlayer> sorted(List<DuelsPlayer> list) {
        List<SortKey> keys = new ArrayList<>(list.size());
        for (DuelsPlayer p : list) keys.add(new SortKey(p, game));

        Comparator<SortKey> byName = (a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.name, b.name);
        Comparator<SortKey> cmp;
        switch (sort) {
            case "wlr":
                cmp = ((Comparator<SortKey>) (a, b) -> Double.compare(b.wlr, a.wlr)).thenComparing(byName);
                break;
            case "kdr":
                cmp = ((Comparator<SortKey>) (a, b) -> Double.compare(b.kdr, a.kdr)).thenComparing(byName);
                break;
            case "name":
                cmp = byName;
                break;
            case "wins":
            default:
                cmp = ((Comparator<SortKey>) (a, b) -> Integer.compare(b.wins, a.wins)).thenComparing(byName);
                break;
        }
        keys.sort(cmp);

        List<DuelsPlayer> out = new ArrayList<>(keys.size());
        for (SortKey k : keys) out.add(k.player);
        return out;
    }
}
