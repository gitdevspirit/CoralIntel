package coralintel.ui.intel;

import coralintel.ui.clickgui.GuiColors;
import coralintel.ui.clickgui.RoundedUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class IntelGui extends GuiScreen {

    private static final Minecraft mc = Minecraft.getMinecraft();

    // Layout
    private static final int HDR       = 44;
    private static final int SRCH      = 28;
    private static final int FTR       = 24;
    private static final int COL_HDR   = 18;   // column header row height
    private static final int CARD_H    = 42;
    private static final int CARD_GAP  = 2;
    private static final int CARD_PAD  = 6;    // horizontal padding each side
    private static final int HEAD_SIZE = 20;
    private static final int SCROLL_SPD = 18;

    // Column centres are computed at draw time as % of listW to avoid overlap
    // See colFkdr(lw), colWlr(lw), colStreak(lw)
    private int colFkdr(int lw)   { return (int)(lw * 0.52f); }
    private int colWlr(int lw)    { return (int)(lw * 0.63f); }
    private int colStreak(int lw) { return (int)(lw * 0.74f); }
    private int colThreat(int lw) { return (int)(lw * 0.87f); }

    // Colours
    private static final int BG_FULL  = 0xBB000008;
    private static final int BG_HDR   = 0xEE07070E;
    private static final int BG_SRCH  = 0xEE050509;
    private static final int BG_LIST  = 0xAA070710;
    private static final int BG_CARD  = 0xCC0C0C1A;
    private static final int BG_HOV   = 0xCC121228;
    private static final int BG_SEL   = 0xCC151535;
    private static final int BG_DETAIL= 0xEE08080F;

    private static final int COL_DIVIDER  = 0x18FFFFFF;
    private static final int COL_DIM      = 0xFF404055;
    private static final int COL_MID      = 0xFF8888BB;
    private static final int COL_BRIGHT   = 0xFFDDDDEE;
    private static final int COL_ACCENT   = GuiColors.ACCENT; // E991B8 pink
    private static final int COL_GOLD     = 0xFFFFCC44;
    private static final int COL_RED      = 0xFFFF3355;
    private static final int COL_REDSUB   = 0xAAFF3355;

    private static final String[] SORT_LABELS = {"Threat","FKDR","WLR","Streak","Name"};

    private int  sortMode  = 0;
    private int  scrollOff = 0;
    private int  maxScroll = 0;
    private IntelPlayer selected = null;
    private List<IntelPlayer> players = new ArrayList<>();

    private String  searchText  = "";
    private boolean searchFocus = false;
    
    // Settings panel
    private boolean showSettings = false;
    private static final int SETTINGS_WIDTH = 280;
    private boolean settingsMouseDown = false;
    private int settingsDragTarget = -1; // Which slider is being dragged (-1 = none)
    private int settingsScrollOff = 0;
    private int settingsMaxScroll = 0;

    // Skin cache — keyed by player name -> ResourceLocation
    private final java.util.Map<String, ResourceLocation> skinCache     = new java.util.HashMap<>();
    // true = full 64x64 skin sheet (lobby player), false = pre-cropped 16x16 face (downloaded)
    private final java.util.Map<String, Boolean>          skinIsSheet   = new java.util.HashMap<>();

    /** Called by IntelManager when lobby tab-list skins are available — instant, no download needed */
    public void cacheLobbyPlayerSkin(String name, ResourceLocation skin) {
        skinCache.put(name, skin);
        skinIsSheet.put(name, true);
        skinFetchState.put(name, "done");
    }

    public void setPlayers(List<IntelPlayer> p) {
        players = new ArrayList<>(p);
        sortPlayers();
        if (selected == null && !players.isEmpty()) selected = players.get(0);
        else if (selected != null) {
            // keep selection pointer valid
            for (IntelPlayer np : players) if (np.name.equals(selected.name)) { selected = np; break; }
        }
    }

    @Override public boolean doesGuiPauseGame() { return false; }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        ScaledResolution sr = new ScaledResolution(mc);
        int sw = sr.getScaledWidth(), sh = sr.getScaledHeight();

        fillRect(0, 0, sw, sh, BG_FULL);

        int settingsW = 0; // No longer using side panel
        int detailW = selected != null ? Math.min(sw / 3, 320) : 0;
        int listW   = sw - detailW - settingsW;

        drawHeader(sw, mx, my);
        drawSearchBar(listW, mx, my);
        drawList(listW, sh, mx, my, sr);
        if (selected != null) drawDetail(listW, detailW, sh);
        // Settings now handled by popup GUI - no side panel rendering needed
        drawFooter(sw, sh);

        super.drawScreen(mx, my, pt);
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private void drawHeader(int sw, int mx, int my) {
        fillRect(0, 0, sw, HDR, BG_HDR);
        // Bottom accent line
        fillRect(0, HDR - 1, sw, 1, 0x44E991B8);

        // Title
        gl();
        GlStateManager.pushMatrix();
        GlStateManager.translate(14, 10, 0);
        GlStateManager.scale(1.1f, 1.1f, 1f);
        mc.fontRendererObj.drawString("LOBBY INTEL", 0, 0, COL_ACCENT, false);
        GlStateManager.popMatrix();

        mc.fontRendererObj.drawString(
            players.size() + " players  \u2022  " +
            (IntelManager.getInstance().isFetching() ? "fetching\u2026" : "ready"),
            14, 26, COL_DIM, false);

        // Sort tabs - centred
        int tabW = 54, tabH = 20, tabGap = 4;
        int totalW = SORT_LABELS.length * (tabW + tabGap) - tabGap;
        int bx = sw / 2 - totalW / 2;
        for (int i = 0; i < SORT_LABELS.length; i++) {
            int by = (HDR - tabH) / 2;
            boolean active = sortMode == i;
            boolean hov = mx >= bx && mx < bx + tabW && my >= by && my < by + tabH;
            int bg = active ? 0xDD100D1C : hov ? 0x22FFFFFF : 0x0DFFFFFF;
            RoundedUtils.drawRoundedRect(bx, by, tabW, tabH, tabH / 2f, bg);
            if (active) RoundedUtils.drawRoundedOutline(bx, by, tabW, tabH, tabH / 2f, 1.2f, COL_ACCENT);
            gl();
            int tc = active ? COL_ACCENT : hov ? COL_BRIGHT : COL_MID;
            int lw = mc.fontRendererObj.getStringWidth(SORT_LABELS[i]);
            mc.fontRendererObj.drawString(SORT_LABELS[i], bx + (tabW - lw) / 2f, by + (tabH - 8) / 2f, tc, false);
            bx += tabW + tabGap;
        }

        // Refresh button - top right
        String ref = "\u21BB  Refresh";
        int rw = mc.fontRendererObj.getStringWidth(ref) + 14;
        int rx = sw - rw - 10, ry = (HDR - 16) / 2;
        boolean rHov = mx >= rx && mx < rx + rw && my >= ry && my < ry + 16;
        RoundedUtils.drawRoundedRect(rx, ry, rw, 16, 4, rHov ? 0x33FFFFFF : 0x11FFFFFF);
        if (rHov) RoundedUtils.drawRoundedOutline(rx, ry, rw, 16, 4, 1f, 0x33FFFFFF);
        gl(); mc.fontRendererObj.drawString(ref, rx + 7f, ry + 4f, rHov ? COL_BRIGHT : COL_MID, false);
        
        // Settings button - left of refresh
        String settings = "\u2699";
        int sx = rx - 26, sy = ry;
        boolean sHov = mx >= sx && mx < sx + 20 && my >= sy && my < sy + 16;
        boolean sActive = false; // No longer using side panel
        RoundedUtils.drawRoundedRect(sx, sy, 20, 16, 4, sActive ? 0x55E991B8 : sHov ? 0x33FFFFFF : 0x11FFFFFF);
        if (sActive) RoundedUtils.drawRoundedOutline(sx, sy, 20, 16, 4, 1f, COL_ACCENT);
        else if (sHov) RoundedUtils.drawRoundedOutline(sx, sy, 20, 16, 4, 1f, 0x33FFFFFF);
        gl(); mc.fontRendererObj.drawString(settings, sx + 6f, sy + 3f, sActive ? COL_ACCENT : sHov ? COL_BRIGHT : COL_MID, false);
    }

    // ── Search bar ────────────────────────────────────────────────────────────

    private void drawSearchBar(int lw, int mx, int my) {
        int sy = HDR;
        fillRect(0, sy, lw, SRCH, BG_SRCH);
        fillRect(0, sy + SRCH - 1, lw, 1, COL_DIVIDER);

        // Search icon + input field
        int bx = 10, by = sy + 5, bw = 220, bh = SRCH - 10;
        boolean hov = mx >= bx && mx < bx + bw && my >= by && my < by + bh;
        int border = searchFocus ? COL_ACCENT : hov ? 0x55FFFFFF : 0x22FFFFFF;
        RoundedUtils.drawRoundedRect(bx, by, bw, bh, 3, 0x22000000);
        RoundedUtils.drawRoundedOutline(bx, by, bw, bh, 3, 1f, border);

        gl();
        // Search glyph
        mc.fontRendererObj.drawString("\u2315", bx + 4f, by + (bh - 8) / 2f, COL_DIM, false);

        String placeholder = "Search / add player\u2026";
        String shown = searchText.isEmpty() && !searchFocus ? placeholder : searchText;
        if (searchFocus && (System.currentTimeMillis() % 1000) < 500) shown = searchText + "|";
        int tc = searchText.isEmpty() ? COL_DIM : COL_BRIGHT;
        mc.fontRendererObj.drawString(shown, bx + 14f, by + (bh - 8) / 2f, tc, false);

        // Add button
        if (!searchText.isEmpty()) {
            int abx = bx + bw + 6;
            int abw = mc.fontRendererObj.getStringWidth("+ Add") + 12;
            boolean ahov = mx >= abx && mx < abx + abw && my >= by && my < by + bh;
            RoundedUtils.drawRoundedRect(abx, by, abw, bh, 3, ahov ? 0x55E991B8 : 0x22E991B8);
            RoundedUtils.drawRoundedOutline(abx, by, abw, bh, 3, 1f, ahov ? COL_ACCENT : 0x33E991B8);
            gl(); mc.fontRendererObj.drawString("+ Add", abx + 6f, by + (bh - 8) / 2f, ahov ? COL_ACCENT : COL_MID, false);
        }
    }

    // ── List ──────────────────────────────────────────────────────────────────

    private void drawList(int lw, int sh, int mx, int my, ScaledResolution sr) {
        int ly = HDR + SRCH;
        int lh = sh - ly - FTR;
        fillRect(0, ly, lw, lh, BG_LIST);

        // Column headers row
        int hY = ly + 4;
        fillRect(0, hY, lw, COL_HDR, 0x0AFFFFFF);
        gl();
        int nameX = CARD_PAD + HEAD_SIZE + 10;
        mc.fontRendererObj.drawString("PLAYER", nameX, hY + 5, COL_DIM, false);
        drawCentred("FKDR",   colFkdr(lw),   hY + 5, COL_DIM);
        drawCentred("WLR",    colWlr(lw),    hY + 5, COL_DIM);
        drawCentred("STREAK", colStreak(lw), hY + 5, COL_DIM);
        drawCentred("THREAT", colThreat(lw), hY + 5, COL_DIM);
        fillRect(0, hY + COL_HDR - 1, lw, 1, COL_DIVIDER);

        int cY = ly + COL_HDR + 4;
        int cH = lh - COL_HDR - 4;
        int tot = Math.max(1, players.size() * (CARD_H + CARD_GAP));
        maxScroll = Math.max(0, tot - cH);
        scrollOff = Math.max(0, Math.min(scrollOff, maxScroll));

        int scale = sr.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(0, (sr.getScaledHeight() - cY - cH) * scale, lw * scale, cH * scale);

        for (int i = 0; i < players.size(); i++) {
            int cy = cY + i * (CARD_H + CARD_GAP) - scrollOff;
            if (cy + CARD_H < cY || cy > cY + cH) continue;
            IntelPlayer p = players.get(i);
            boolean hov = mx >= CARD_PAD && mx < lw - CARD_PAD && my >= cy && my < cy + CARD_H;
            boolean sel = p == selected;
            drawCard(p, CARD_PAD, cy, lw - CARD_PAD * 2, CARD_H, hov, sel);
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        // Scrollbar
        if (maxScroll > 0) {
            int sbH = Math.max(20, cH * cH / tot);
            int sbY = cY + (int)((float) scrollOff / maxScroll * (cH - sbH));
            fillRect(lw - 3, cY, 2, cH, 0x11FFFFFF);
            fillRect(lw - 3, sbY, 2, sbH, 0x66E991B8);
        }
    }

    private void drawCard(IntelPlayer p, int cx, int cy, int cw, int ch, boolean hov, boolean sel) {
        int bg = sel ? BG_SEL : hov ? BG_HOV : BG_CARD;
        RoundedUtils.drawRoundedRect(cx, cy, cw, ch, 5, bg);
        if (sel) RoundedUtils.drawRoundedOutline(cx, cy, cw, ch, 5, 1f, 0x66E991B8);

        int tc = threatCol((int) p.threatScore);

        // Left threat stripe
        RoundedUtils.drawRoundedRect(cx, cy + 4, 3, ch - 8, 2, tc);

        // Player head
        int headX = cx + 10, headY = cy + (ch - HEAD_SIZE) / 2;
        drawPlayerHead(p.name, headX, headY, HEAD_SIZE);

        gl();
        int nameX = cx + 10 + HEAD_SIZE + 7;

        // Name line
        int nameCol = p.cheater ? COL_RED : COL_BRIGHT;
        String namePrefix = p.cheater ? "\u26D4 " : "";
        mc.fontRendererObj.drawString(namePrefix + p.name, nameX, cy + 8, nameCol, false);

        // Star below name — uses the Bedwars star count (not network level)
        String lvlStr = p.loading ? "loading\u2026" : "\u2605 " + p.star;
        mc.fontRendererObj.drawString(
                lvlStr, nameX, cy + 19,
                p.loading ? COL_DIM : IntelColors.getPrestigeColor(p.star),
                false
        );

        // Urchin tag badge below level with icon - using letter codes
        if (p.urchinTag != null) {
            String icon = p.getTagBadge();
            int iconColor = p.getTagColor();
            
            // Truncate long tag for card display
            String tag = p.urchinTag;
            if (mc.fontRendererObj.getStringWidth(tag) > 120) { // Slightly shorter to fit icon
                while (tag.length() > 3 && mc.fontRendererObj.getStringWidth(tag + "\u2026") > 120)
                    tag = tag.substring(0, tag.length() - 1);
                tag += "\u2026";
            }
            
            // Draw badge background
            int iconWidth = mc.fontRendererObj.getStringWidth(icon) + 4;
            int tagWidth = mc.fontRendererObj.getStringWidth(tag) + 4;
            int totalWidth = iconWidth + tagWidth + 2;
            
            // Icon background
            RoundedUtils.drawRoundedRect(nameX, cy + 30, iconWidth, 9, 2, iconColor & 0x66FFFFFF);
            // Tag background
            RoundedUtils.drawRoundedRect(nameX + iconWidth + 1, cy + 30, tagWidth, 9, 2, 0x33FF2244);
            
            gl(); 
            mc.fontRendererObj.drawString(icon, nameX + 2, cy + 31, iconColor, false);
            mc.fontRendererObj.drawString(tag, nameX + iconWidth + 3, cy + 31, COL_RED, false);
        }

        // Team colour dot (top right of card)
        if (p.team != null)
            RoundedUtils.drawRoundedRect(cx + cw - 10, cy + 5, 6, 6, 3, teamCol(p.team));

        // Remove ✕ for manual players (bottom right)
        if (IntelManager.getInstance().isManual(p)) {
            int xbx = cx + cw - 12, xby = cy + ch - 12;
            boolean xhov = hov; // brightens when card is hovered
            gl(); mc.fontRendererObj.drawString("\u00D7", xbx, xby, xhov ? COL_RED : COL_REDSUB, false);
        }

        if (p.loading) {
            gl();
            int _lw = cx + cw + CARD_PAD; // approximate listW from card bounds
            drawCentred("\u2014", colFkdr(_lw),   cy + ch/2 - 4, COL_DIM);
            drawCentred("\u2014", colWlr(_lw),    cy + ch/2 - 4, COL_DIM);
            drawCentred("\u2014", colStreak(_lw), cy + ch/2 - 4, COL_DIM);
            return;
        }

        // Stats — vertically centred in card, centred under column headers
        int sy = cy + ch / 2 - 4;
        int _lw2 = cx + cw + CARD_PAD;
        gl();
        drawCentred(fmt(p.fkdr),                colFkdr(_lw2),   sy, statCol(p.fkdr, 3, 6));
        drawCentred(fmt(p.wlr),                 colWlr(_lw2),    sy, statCol(p.wlr, 2, 4));
        drawCentred(String.valueOf(p.winstreak), colStreak(_lw2), sy, statCol(p.winstreak, 10, 30));

        // Threat score number only (no bar)
        drawCentred(String.valueOf((int) p.threatScore), colThreat(_lw2), sy, tc);
    }

    // ── Detail panel ──────────────────────────────────────────────────────────

    private void drawDetail(int px, int pw, int sh) {
        int py = HDR;
        int ph = sh - HDR - FTR;
        fillRect(px, py, pw, ph, BG_DETAIL);
        // Left border accent
        fillRect(px, py, 1, ph, 0x44E991B8);

        IntelPlayer p = selected;
        if (p == null) return;

        int x = px + 16, y = py + 16;
        int innerW = pw - 32;
        int tc = threatCol((int) p.threatScore);

        // Large head
        drawPlayerHead(p.name, x, y, 36);

        // Name scaled
        gl();
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 44, y + 2, 0);
        GlStateManager.scale(1.3f, 1.3f, 1f);
        mc.fontRendererObj.drawString(p.name, 0, 0, p.cheater ? COL_RED : COL_BRIGHT, false);
        GlStateManager.popMatrix();

        // Star + team
        String sub = "\u2605 " + p.star + (p.team != null ? "   [" + p.team + "]" : "");
        mc.fontRendererObj.drawString(sub, x + 44f, y + 22f, IntelColors.getPrestigeColor(p.star), false);
        y += 48;

        // Urchin tag — wrapped lines inside a background box
        if (p.urchinTag != null) {
            List<String> tagLines = wrap("\u26D4  " + p.urchinTag, innerW - 10);
            int boxH = tagLines.size() * 11 + 6;
            RoundedUtils.drawRoundedRect(x, y, innerW, boxH, 3, 0x33FF2244);
            RoundedUtils.drawRoundedOutline(x, y, innerW, boxH, 3, 1f, 0x55FF2244);
            gl();
            int ty = y + 4;
            for (String line : tagLines) {
                mc.fontRendererObj.drawString(line, x + 5f, ty, COL_RED, false);
                ty += 11;
            }
            y += boxH + 6;
        }

        // Divider
        fillRect(x, y, innerW, 1, COL_DIVIDER); y += 8;

        // Threat bar
        gl(); mc.fontRendererObj.drawString("THREAT", x, y, COL_DIM, false);
        String scoreStr = (int) p.threatScore + " / 100";
        mc.fontRendererObj.drawString(scoreStr,
            x + innerW - mc.fontRendererObj.getStringWidth(scoreStr), y, tc, false);
        y += 10;
        fillRect(x, y, innerW, 5, 0x22FFFFFF);
        int fw = (int)(Math.min(1f, p.threatScore / 100f) * innerW);
        if (fw > 0) fillRect(x, y, fw, 5, tc);
        y += 12;

        if (p.loading) {
            gl(); mc.fontRendererObj.drawString("Fetching stats\u2026", x, y + 4, COL_DIM, false);
            return;
        }

        fillRect(x, y, innerW, 1, COL_DIVIDER); y += 8;

        // Stats rows
        y = dRow(x, y, innerW, "Final K/D",   fmt(p.fkdr),                  statCol(p.fkdr, 3, 6));
        y = dRow(x, y, innerW, "Win/Loss",     fmt(p.wlr),                   statCol(p.wlr, 2, 4));
        y = dRow(x, y, innerW, "Win Streak",   String.valueOf(p.winstreak),   statCol(p.winstreak, 10, 30));
        y = dRow(x, y, innerW, "Final Kills",  String.valueOf(p.finalKills),  COL_BRIGHT);
        y = dRow(x, y, innerW, "Beds Broken",  String.valueOf(p.bedsBroken),  COL_BRIGHT);
        y = dRow(x, y, innerW, "Total Wins",   String.valueOf(p.wins),        COL_BRIGHT);

        y += 4; fillRect(x, y, innerW, 1, COL_DIVIDER); y += 8;

        // Intel recommendation
        gl(); mc.fontRendererObj.drawString("INTEL", x, y, COL_DIM, false); y += 12;
        for (String line : wrap(recommend(p), innerW)) {
            gl(); mc.fontRendererObj.drawString(line, x, y, COL_MID, false); y += 11;
        }
    }

    // ── Settings Panel ────────────────────────────────────────────────────────

    private void drawSettings(int x, int w, int sh, int mx, int my) {
        fillRect(x, 0, w, sh, BG_DETAIL);
        fillRect(x, 0, 1, sh, COL_DIVIDER);

        int innerX = x + 12;
        int innerW = w - 24;
        int startY = 14;
        int y = startY - settingsScrollOff;

        // Enable scissor test for clipping
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        ScaledResolution sr = new ScaledResolution(mc);
        int scale = sr.getScaleFactor();
        GL11.glScissor(x * scale, 0, w * scale, sh * scale);

        // Title
        gl();
        GlStateManager.pushMatrix();
        GlStateManager.translate(innerX, y, 0);
        GlStateManager.scale(1.1f, 1.1f, 1f);
        mc.fontRendererObj.drawString("HUD OVERLAY", 0, 0, COL_ACCENT, false);
        GlStateManager.popMatrix();
        y += 22;

        fillRect(innerX, y, innerW, 1, COL_DIVIDER);
        y += 10;

        // Get HUD overlay instance
        coralintel.module.modules.LobbyIntel lobbyIntel = (coralintel.module.modules.LobbyIntel) 
            CoralIntel.moduleManager.getModule("LobbyIntel");
        if (lobbyIntel == null) {
            gl(); mc.fontRendererObj.drawString("LobbyIntel module not found", innerX, y, COL_DIM, false);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            return;
        }
        IntelHudOverlay hud = lobbyIntel.getHudOverlay();
        if (hud == null) {
            gl(); mc.fontRendererObj.drawString("HUD overlay not initialized", innerX, y, COL_DIM, false);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            return;
        }

        int contentStartY = y;

        // Enable/Disable toggle
        y = drawToggle(innerX, y, innerW, "Enabled", hud.isEnabled(), mx, my);

        y += 8;
        fillRect(innerX, y, innerW, 1, COL_DIVIDER);
        y += 10;

        // Position section
        gl(); mc.fontRendererObj.drawString("POSITION", innerX, y, COL_DIM, false); y += 14;
        y = drawSlider(innerX, y, innerW, "X Position", hud.getPosX(), 0, 1920, mx, my);
        y = drawSlider(innerX, y, innerW, "Y Position", hud.getPosY(), 0, 1080, mx, my);

        y += 8;
        fillRect(innerX, y, innerW, 1, COL_DIVIDER);
        y += 10;

        // Display section
        gl(); mc.fontRendererObj.drawString("DISPLAY", innerX, y, COL_DIM, false); y += 14;
        y = drawSlider(innerX, y, innerW, "Scale", (int)(hud.getScale() * 100), 50, 200, mx, my);
        y = drawSlider(innerX, y, innerW, "Max Players", hud.getMaxPlayers(), 1, 20, mx, my);
        y = drawSlider(innerX, y, innerW, "Background Opacity", hud.getBgOpacity(), 0, 255, mx, my);

        y += 8;
        fillRect(innerX, y, innerW, 1, COL_DIVIDER);
        y += 10;

        // Columns section
        gl(); mc.fontRendererObj.drawString("COLUMNS", innerX, y, COL_DIM, false); y += 14;
        y = drawToggle(innerX, y, innerW, "Player Heads", hud.getShowHeads(), mx, my);
        y = drawToggle(innerX, y, innerW, "FKDR", hud.getShowFkdr(), mx, my);
        y = drawToggle(innerX, y, innerW, "WLR", hud.getShowWlr(), mx, my);
        y = drawToggle(innerX, y, innerW, "Winstreak", hud.getShowStreak(), mx, my);
        y = drawToggle(innerX, y, innerW, "Threat Score", hud.getShowThreat(), mx, my);
        y = drawToggle(innerX, y, innerW, "Team Colors", hud.getShowTeamColor(), mx, my);

        y += 8;
        fillRect(innerX, y, innerW, 1, COL_DIVIDER);
        y += 10;

        // Sort section
        gl(); mc.fontRendererObj.drawString("SORTING", innerX, y, COL_DIM, false); y += 14;
        String[] sortModes = {"Threat", "FKDR", "Name"};
        String currentMode = hud.getSortMode();
        int modeIndex = currentMode.equals("threat") ? 0 : currentMode.equals("fkdr") ? 1 : 2;
        y = drawDropdown(innerX, y, innerW, "Sort By", sortModes, modeIndex, mx, my);

        // Calculate max scroll
        int contentHeight = y - contentStartY;
        int visibleHeight = sh - (startY + FTR);
        settingsMaxScroll = Math.max(0, contentHeight - visibleHeight);

        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private int drawToggle(int x, int y, int w, String label, boolean value, int mx, int my) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + 16;
        
        gl(); mc.fontRendererObj.drawString(label, x, y + 4, hov ? COL_BRIGHT : COL_MID, false);
        
        int toggleX = x + w - 32;
        int toggleY = y + 2;
        int toggleW = 32;
        int toggleH = 12;
        
        // Background
        int bgColor = value ? 0x88E991B8 : 0x44444455;
        RoundedUtils.drawRoundedRect(toggleX, toggleY, toggleW, toggleH, toggleH / 2f, bgColor);
        
        // Knob
        int knobX = value ? toggleX + toggleW - toggleH : toggleX;
        int knobColor = value ? COL_ACCENT : 0xFF666677;
        RoundedUtils.drawRoundedRect(knobX, toggleY, toggleH, toggleH, toggleH / 2f, knobColor);
        
        return y + 20;
    }

    private int drawSlider(int x, int y, int w, String label, int value, int min, int max, int mx, int my) {
        gl(); mc.fontRendererObj.drawString(label, x, y, COL_DIM, false);
        String valStr = String.valueOf(value);
        mc.fontRendererObj.drawString(valStr, x + w - mc.fontRendererObj.getStringWidth(valStr), y, COL_BRIGHT, false);
        y += 10;
        
        int barY = y + 2;
        int barH = 4;
        
        // Background
        fillRect(x, barY, w, barH, 0x33FFFFFF);
        
        // Filled portion
        float pct = (value - min) / (float)(max - min);
        int fillW = (int)(w * pct);
        fillRect(x, barY, fillW, barH, COL_ACCENT);
        
        // Knob
        int knobX = x + fillW - 4;
        int knobY = barY - 2;
        fillRect(knobX, knobY, 8, 8, COL_ACCENT);
        
        return y + 16;
    }

    private int drawDropdown(int x, int y, int w, String label, String[] options, int selected, int mx, int my) {
        gl(); mc.fontRendererObj.drawString(label, x, y, COL_DIM, false);
        y += 10;
        
        int btnH = 18;
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + btnH;
        
        RoundedUtils.drawRoundedRect(x, y, w, btnH, 3, hov ? 0x33FFFFFF : 0x22000000);
        RoundedUtils.drawRoundedOutline(x, y, w, btnH, 3, 1f, hov ? COL_ACCENT : 0x33FFFFFF);
        
        gl(); mc.fontRendererObj.drawString(options[selected], x + 6f, y + 5f, COL_BRIGHT, false);
        mc.fontRendererObj.drawString("\u25BC", x + w - 12f, y + 5f, COL_DIM, false);
        
        return y + 22;
    }

    // ── Footer ────────────────────────────────────────────────────────────────

    private void drawFooter(int sw, int sh) {
        int fy = sh - FTR;
        fillRect(0, fy, sw, FTR, BG_HDR);
        fillRect(0, fy, sw, 1, COL_DIVIDER);
        gl();
        String hint = "ESC  close    SCROLL  navigate    CLICK  inspect    R  refresh    ENTER  add player";
        mc.fontRendererObj.drawString(hint, 12, fy + (FTR - 8) / 2f, COL_DIM, false);
    }

    // ── Player head ───────────────────────────────────────────────────────────

    // Tracks in-flight skin downloads: name -> "pending", "done", or fail timestamp (ms)
    private final java.util.Map<String, Object> skinFetchState = new java.util.HashMap<>();

    private void drawPlayerHead(String name, int x, int y, int size) {
        try {
            ResourceLocation skin = null;

            // 1. Use unified skin cache (pre-populated for lobby players, downloaded for manual)
            skin = skinCache.get(name);

            // 1b. Fallback: try live network lookup (catches late-joining players)
            if (skin == null && mc.getNetHandler() != null) {
                NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(name);
                if (info != null) {
                    skin = info.getLocationSkin();
                    if (skin != null) { skinCache.put(name, skin); skinIsSheet.put(name, true); skinFetchState.put(name, "done"); }
                }
            }

            // 3. Kick off async download if we have UUID but no skin yet
            if (skin == null) {
                String uuid = IntelManager.getInstance().getCachedUuid(name);
                Object state = skinFetchState.get(name);
                boolean canFetch = !"pending".equals(state) && !"done".equals(state)
                        && (!(state instanceof Long) || System.currentTimeMillis() - (Long)state > 10_000L);
                if (uuid != null && canFetch) {
                    skinFetchState.put(name, "pending");
                    final String uuidRaw  = uuid.replace("-", ""); // no dashes for session server
                    final String nameFinal = name;
                    new Thread(() -> {
                        try {
                            IntelManager.dbg("[Skin] fetching profile for " + nameFinal + " uuid=" + uuidRaw);
                            // Step A: ask Mojang session server for the skin texture URL
                            String profileUrl = "https://sessionserver.mojang.com/session/minecraft/profile/" + uuidRaw;
                            java.net.HttpURLConnection pc = (java.net.HttpURLConnection)
                                    new java.net.URL(profileUrl).openConnection();
                            pc.setConnectTimeout(5000); pc.setReadTimeout(5000);
                            pc.setRequestProperty("User-Agent", "Spirit-Client/1.0");
                            int profileCode = pc.getResponseCode();
                            IntelManager.dbg("[Skin] sessionserver response=" + profileCode);
                            if (profileCode != 200) { skinFetchState.put(nameFinal, System.currentTimeMillis()); return; }

                            com.google.gson.JsonObject profile = new com.google.gson.JsonParser()
                                    .parse(IntelManager.readStreamStatic(pc.getInputStream())).getAsJsonObject();
                            String skinUrl = null;
                            if (profile.has("properties")) {
                                for (com.google.gson.JsonElement el : profile.getAsJsonArray("properties")) {
                                    com.google.gson.JsonObject prop = el.getAsJsonObject();
                                    if ("textures".equals(prop.get("name").getAsString())) {
                                        // value is base64-encoded JSON
                                        String decoded = new String(java.util.Base64.getDecoder()
                                                .decode(prop.get("value").getAsString()));
                                        com.google.gson.JsonObject tex = new com.google.gson.JsonParser()
                                                .parse(decoded).getAsJsonObject();
                                        skinUrl = tex.getAsJsonObject("textures")
                                                     .getAsJsonObject("SKIN")
                                                     .get("url").getAsString();
                                        break;
                                    }
                                }
                            }
                            IntelManager.dbg("[Skin] skinUrl=" + skinUrl);
                            if (skinUrl == null) { skinFetchState.put(nameFinal, System.currentTimeMillis()); return; }

                            // Step B: download the actual 64x64 skin PNG from Mojang's CDN
                            java.net.HttpURLConnection sc = (java.net.HttpURLConnection)
                                    new java.net.URL(skinUrl).openConnection();
                            sc.setConnectTimeout(5000); sc.setReadTimeout(5000);
                            sc.setRequestProperty("User-Agent", "Spirit-Client/1.0");
                            if (sc.getResponseCode() != 200) { skinFetchState.put(nameFinal, System.currentTimeMillis()); return; }

                            java.awt.image.BufferedImage fullSkin = javax.imageio.ImageIO.read(sc.getInputStream());
                            if (fullSkin == null) { skinFetchState.put(nameFinal, System.currentTimeMillis()); return; }

                            // Step C: crop just the 8x8 face (UV 8,8 on 64x64 sheet) + hat layer (40,8)
                            int res = 16; // render at 16px internally for crispness
                            java.awt.image.BufferedImage face = new java.awt.image.BufferedImage(res, res, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                            java.awt.Graphics2D g = face.createGraphics();
                            // base face layer: pixels 8-15, 8-15 on 64x64 sheet
                            g.drawImage(fullSkin, 0, 0, res, res, 8, 8, 16, 16, null);
                            // hat/overlay layer: pixels 40-47, 8-15
                            g.drawImage(fullSkin, 0, 0, res, res, 40, 8, 48, 16, null);
                            g.dispose();

                            final net.minecraft.client.renderer.texture.DynamicTexture dt =
                                    new net.minecraft.client.renderer.texture.DynamicTexture(face);

                            // Step D: register texture on MC main thread
                            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> {
                                ResourceLocation loc = mc.getTextureManager()
                                        .getDynamicTextureLocation("intel_face_" + nameFinal, dt);
                                skinCache.put(nameFinal, loc);
                                skinIsSheet.put(nameFinal, false); // Mark as pre-cropped
                                skinFetchState.put(nameFinal, "done");
                            });
                        } catch (Exception e) {
                            skinFetchState.put(nameFinal, System.currentTimeMillis());
                        }
                    }, "IntelFace-" + name).start();
                }
            }

            // 4. Fallback: Steve
            if (skin == null) skin = new ResourceLocation("textures/entity/steve.png");

            // Proper GL state for rendering
            GlStateManager.pushMatrix();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            GlStateManager.enableAlpha();
            GlStateManager.color(1f, 1f, 1f, 1f);
            mc.getTextureManager().bindTexture(skin);

            boolean isSheet = skinIsSheet.getOrDefault(name, true); // Default to sheet for safety
            if (isSheet) {
                // Full 64x64 skin sheet — draw face region (8,8) with hat overlay (40,8)
                // Base face layer
                Gui.drawScaledCustomSizeModalRect(x, y, 8f, 8f, 8, 8, size, size, 64f, 64f);
                // Hat/overlay layer
                Gui.drawScaledCustomSizeModalRect(x, y, 40f, 8f, 8, 8, size, size, 64f, 64f);
            } else {
                // Pre-cropped 16x16 face texture — draw full image
                Gui.drawScaledCustomSizeModalRect(x, y, 0f, 0f, 16, 16, size, size, 16f, 16f);
            }
            
            GlStateManager.color(1f, 1f, 1f, 1f);
            GlStateManager.popMatrix();
        } catch (Exception ignored) {}
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int dw = Mouse.getEventDWheel();
        if (dw != 0) {
            if (showSettings) {
                // Scroll in settings panel
                settingsScrollOff -= dw > 0 ? SCROLL_SPD : -SCROLL_SPD;
                settingsScrollOff = Math.max(0, Math.min(settingsScrollOff, settingsMaxScroll));
            } else {
                // Scroll in player list
                scrollOff -= dw > 0 ? SCROLL_SPD : -SCROLL_SPD;
                scrollOff = Math.max(0, Math.min(scrollOff, maxScroll));
            }
        }
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        ScaledResolution sr = new ScaledResolution(mc);
        int sw = sr.getScaledWidth(), sh = sr.getScaledHeight();
        int detailW = selected != null ? Math.min(sw / 3, 320) : 0;
        int listW   = sw - detailW;

        // Sort tabs
        int tabW = 54, tabH = 20, tabGap = 4;
        int totalW = SORT_LABELS.length * (tabW + tabGap) - tabGap;
        int bx = sw / 2 - totalW / 2;
        for (int i = 0; i < SORT_LABELS.length; i++) {
            int by = (HDR - tabH) / 2;
            if (mx >= bx && mx < bx + tabW && my >= by && my < by + tabH) {
                sortMode = i; sortPlayers(); return;
            }
            bx += tabW + tabGap;
        }

        // Refresh button
        String ref = "\u21BB  Refresh";
        int rw = mc.fontRendererObj.getStringWidth(ref) + 14;
        int rx = sw - rw - 10, ry = (HDR - 16) / 2;
        if (mx >= rx && mx < rx + rw && my >= ry && my < ry + 16) {
            IntelManager.getInstance().refresh(); return;
        }
        
        // Settings button
        int sx = rx - 26, sy = ry;
        if (mx >= sx && mx < sx + 20 && my >= sy && my < sy + 16) {
            // Open centered popup settings GUI
            coralintel.module.modules.LobbyIntel lobbyIntel = (coralintel.module.modules.LobbyIntel) 
                CoralIntel.moduleManager.getModule("LobbyIntel");
            if (lobbyIntel != null && lobbyIntel.getHudOverlay() != null) {
                mc.displayGuiScreen(new IntelHudSettingsGui(lobbyIntel.getHudOverlay(), this));
            }
            return;
        }
        
        // Search bar focus
        int sbx = 10, sby = HDR + 5, sbw = 220, sbh = SRCH - 10;
        searchFocus = mx >= sbx && mx < sbx + sbw && my >= sby && my < sby + sbh;

        // Add button
        if (!searchText.isEmpty()) {
            int abx = sbx + sbw + 6, abw = mc.fontRendererObj.getStringWidth("+ Add") + 12;
            if (mx >= abx && mx < abx + abw && my >= sby && my < sby + sbh) {
                IntelManager.getInstance().addManualPlayer(searchText.trim());
                searchText = ""; return;
            }
        }

        // Cards
        int cY = HDR + SRCH + COL_HDR + 4;
        for (int i = 0; i < players.size(); i++) {
            int cy = cY + i * (CARD_H + CARD_GAP) - scrollOff;
            if (my < cy || my >= cy + CARD_H || mx < CARD_PAD || mx >= listW - CARD_PAD) continue;
            IntelPlayer p = players.get(i);
            int cw = listW - CARD_PAD * 2;
            // X button (manual)
            if (IntelManager.getInstance().isManual(p)) {
                int xbx = CARD_PAD + cw - 12;
                int xby = cy + CARD_H - 12;
                if (mx >= xbx && mx < xbx + 10 && my >= xby && my < xby + 10) {
                    IntelManager.getInstance().removeManualPlayer(p.name);
                    if (selected == p) selected = null;
                    return;
                }
            }
            selected = p; return;
        }

        super.mouseClicked(mx, my, button);
    }
    
    @Override
    protected void mouseClickMove(int mx, int my, int button, long timeSinceLastClick) {
        if (!showSettings) return;
        
        ScaledResolution sr = new ScaledResolution(mc);
        int sw = sr.getScaledWidth();
        int settingsX = sw - SETTINGS_WIDTH + 12;
        int settingsW = SETTINGS_WIDTH - 24;
        
        coralintel.module.modules.LobbyIntel lobbyIntel = (coralintel.module.modules.LobbyIntel) 
            CoralIntel.moduleManager.getModule("LobbyIntel");
        if (lobbyIntel == null) return;
        IntelHudOverlay hud = lobbyIntel.getHudOverlay();
        if (hud == null) return;
        
        int startY = 14;
        int y = startY - settingsScrollOff;
        
        // Title
        y += 22;
        
        // Divider after title
        y += 10;
        
        // Enabled toggle - drawToggle returns y+20
        y += 20;
        
        // Divider after enabled
        y += 8;
        y += 10;
        
        // "POSITION" label
        y += 14;
        
        // X Position slider - drawSlider returns y+16
        if (my >= y && my < y + 16 && mx >= settingsX && mx < settingsX + settingsW) {
            int val = (int)((mx - settingsX) / (float)settingsW * 1920);
            hud.setPosition(Math.max(0, Math.min(1920, val)), hud.getPosY());
            return;
        }
        y += 16;
        
        // Y Position slider - drawSlider returns y+16
        if (my >= y && my < y + 16 && mx >= settingsX && mx < settingsX + settingsW) {
            int val = (int)((mx - settingsX) / (float)settingsW * 1080);
            hud.setPosition(hud.getPosX(), Math.max(0, Math.min(1080, val)));
            return;
        }
        y += 16;
        
        // Divider after position
        y += 8;
        y += 10;
        
        // "DISPLAY" label
        y += 14;
        
        // Scale slider - drawSlider returns y+16
        if (my >= y && my < y + 16 && mx >= settingsX && mx < settingsX + settingsW) {
            int val = 50 + (int)((mx - settingsX) / (float)settingsW * 150);
            hud.setScale(Math.max(0.5f, Math.min(2.0f, val / 100f)));
            return;
        }
        y += 16;
        
        // Max Players slider - drawSlider returns y+16
        if (my >= y && my < y + 16 && mx >= settingsX && mx < settingsX + settingsW) {
            int val = 1 + (int)((mx - settingsX) / (float)settingsW * 19);
            hud.setMaxPlayers(Math.max(1, Math.min(20, val)));
            return;
        }
        y += 16;
        
        // Background Opacity slider - drawSlider returns y+16
        if (my >= y && my < y + 16 && mx >= settingsX && mx < settingsX + settingsW) {
            int val = (int)((mx - settingsX) / (float)settingsW * 255);
            hud.setBgOpacity(Math.max(0, Math.min(255, val)));
            return;
        }
    }

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        if (searchFocus) {
            if (key == 14) { if (!searchText.isEmpty()) searchText = searchText.substring(0, searchText.length()-1); return; }
            if (key == 28) { if (!searchText.isEmpty()) { IntelManager.getInstance().addManualPlayer(searchText.trim()); searchText = ""; } return; }
            if (key == 1)  { searchFocus = false; return; }
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-') { if (searchText.length() < 16) searchText += c; }
            return;
        }
        if (key == 19) { IntelManager.getInstance().refresh(); return; }
        if (key == 1)  { mc.displayGuiScreen(null); return; }
        super.keyTyped(c, key);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void sortPlayers() {
        switch (sortMode) {
            case 0: players.sort((a,b) -> Double.compare(b.threatScore, a.threatScore)); break;
            case 1: players.sort((a,b) -> Double.compare(b.fkdr, a.fkdr));              break;
            case 2: players.sort((a,b) -> Double.compare(b.wlr, a.wlr));                break;
            case 3: players.sort((a,b) -> Integer.compare(b.winstreak, a.winstreak));   break;
            case 4: players.sort(Comparator.comparing(p -> p.name));                     break;
        }
    }

    private int dRow(int x, int y, int innerW, String label, String val, int col) {
        gl();
        mc.fontRendererObj.drawString(label, x, y, COL_DIM, false);
        mc.fontRendererObj.drawString(val, x + innerW - mc.fontRendererObj.getStringWidth(val), y, col, false);
        return y + 13;
    }

    private int threatCol(int s) {
        if (s >= 75) return 0xFFFF2244;
        if (s >= 50) return 0xFFFF7722;
        if (s >= 25) return 0xFFFFCC22;
        return 0xFF44DD66;
    }

    private int statCol(double v, double mid, double high) {
        return IntelColors.getStatColor(v, mid, high);
    }

    private int teamCol(String t) {
        switch (t) {
            case "red":    return 0xFFFF4444;
            case "blue":   return 0xFF4488FF;
            case "green":  return 0xFF44FF66;
            case "yellow": return 0xFFFFFF44;
            case "aqua":   return 0xFF44FFFF;
            case "white":  return 0xFFEEEEEE;
            case "pink":   return 0xFFFF88CC;
            default:       return 0xFF888888;
        }
    }

    private String fmt(double v) {
        if (v < 0) return "\u2014";
        return v == (long) v ? String.valueOf((long) v) : String.format("%.2f", v);
    }

    // Derives 0=low/1=medium/2=high from the averaged threat score already computed
    private int urchinThreatLevel(IntelPlayer p) {
        double t = p.threatScore;
        if (t >= 65) return 2;
        if (t >= 40) return 1;
        return 0;
    }

    /** Word-boundary safe — "ac" won't match "place" or "black" */
    private boolean hasWord(String text, String word) {
        int idx = text.indexOf(word);
        while (idx >= 0) {
            boolean pre  = idx == 0 || !Character.isLetterOrDigit(text.charAt(idx - 1));
            boolean post = idx + word.length() >= text.length()
                    || !Character.isLetterOrDigit(text.charAt(idx + word.length()));
            if (pre && post) return true;
            idx = text.indexOf(word, idx + 1);
        }
        return false;
    }

    // Severity tiers for cheat detection — higher = reported first
    private static final Object[][] CHEAT_TIERS = {
        // {severity, reason_keywords[], type_keywords[], message}
        {3, new String[]{"scaffold","bridg","blatant scaffold"}, new String[]{"blatant"},
            "HIGH THREAT | Blatant scaffolder. Near-instant bridges everywhere. Rush their bed before they cross."},
        {3, new String[]{"ab","autoblock","hopping","full hop"}, new String[]{},
            "HIGH THREAT | Autoblock / hopping. Near-perfect blocking every hit. Use bow and rush bed — avoid sword fights."},
        {3, new String[]{"fly","bhop","bunnyhop","speed"}, new String[]{},
            "HIGH THREAT | Movement hacks. Expect instant rushes. Fortify your bed and defend early."},
        {3, new String[]{"esp","visual","xray","x-ray","wallhack"}, new String[]{},
            "HIGH THREAT | ESP / visuals. They see through walls and track you. Cover your bed on all sides."},
        {3, new String[]{"aimbot"}, new String[]{},
            "HIGH THREAT | Aimbot. Avoid all direct fights. Rush bed and disengage immediately."},
        {2, new String[]{"ka","killaura","kill aura"}, new String[]{},
            "MEDIUM THREAT | KillAura (KA). Auto-targets and attacks. Avoid open 1v1s — use terrain and rush bed."},
        {2, new String[]{"aa","aim assist","aimassist"}, new String[]{},
            "MEDIUM THREAT | Aim assist (AA). Improved accuracy. You can outmanoeuvre — don't let them set up shots."},
        {2, new String[]{"reach"}, new String[]{},
            "MEDIUM THREAT | Extended reach. They win range trades. Stay point-blank or use a bow."},
        {2, new String[]{"jr","jrv","jump reset","velo","velocity","anti-kb","antikb"}, new String[]{},
            "MEDIUM THREAT | Velocity / jump reset (JRV). Takes reduced knockback. Focus bed destruction over PvP."},
        {2, new String[]{"sniper"}, new String[]{"sniper"},
            "MEDIUM THREAT | Known sniper. Use covered tunnels and avoid open bridges."},
        {1, new String[]{"hop"}, new String[]{},
            "MEDIUM THREAT | Hopping. Reduces knockback taken. Don't rely on void plays — rush bed instead."},
        {1, new String[]{"ac","autoclick","autoclicker","cps"}, new String[]{},
            "LOW-MED THREAT | Autoclicker (AC). Higher CPS but no aim advantage. Gap fights and use knockback."},
        {1, new String[]{"legitscaff","legitscaf","fastplace"}, new String[]{},
            "LOW-MED THREAT | Legit-scaffold / fastplace. Bridges faster than normal. Cut their routes early."},
        {1, new String[]{"eagle"}, new String[]{},
            "LOW-MED THREAT | Eagle bridge. Faster aerial bridging. Play standard and deny their crossing."},
        {0, new String[]{"2q","3q","4q","boosting","queue"}, new String[]{},
            "LOW THREAT | Queue sniper / booster. Stats inflated via boosting — don't be misled by numbers."},
    };

    // Per-cheat advice snippets — scanned in severity order, ALL matches collected
    private static final Object[][] CHEAT_ADVICE = {
        // {severity, reason_kws[], type_kws[], short_label, advice}
        {3, new String[]{"aimbot"},                      new String[]{}, "Aimbot",       "don't fight in the open — rush bed"},
        {3, new String[]{"blatant scaffold","scaffold","bridg"}, new String[]{"blatant"}, "Blatant scaffold", "rush bed before they bridge across"},
        {3, new String[]{"ab","autoblock","full hop","hopping"}, new String[]{""},        "Autoblock/hop",    "avoid sword fights, use bow"},
        {3, new String[]{"hop"},                         new String[]{}, "Hop",          "don't rely on void traps"},
        {3, new String[]{"fly","bhop","bunnyhop","speed"},new String[]{}, "Movement",    "fortify bed immediately"},
        {3, new String[]{"esp","visual","xray","x-ray","wallhack"}, new String[]{}, "ESP","cover bed on all sides"},
        {2, new String[]{"ka","killaura","kill aura"},   new String[]{}, "KillAura",     "avoid open 1v1s, use terrain"},
        {2, new String[]{"aa","aim assist","aimassist"}, new String[]{}, "Aim assist",   "outmanoeuvre, don't stand still"},
        {2, new String[]{"reach"},                       new String[]{}, "Reach",        "get point-blank or use a bow"},
        {2, new String[]{"jr","jrv","jump reset","velo","velocity","anti-kb","antikb"}, new String[]{}, "JR/Velo", "no void traps, focus bed rush"},
        {2, new String[]{"sniper"},                      new String[]{"sniper"}, "Sniper", "use covered tunnels"},
        {1, new String[]{"ac","autoclick","autoclicker","cps"}, new String[]{}, "AC",    "gap fights, use knockback"},
        {1, new String[]{"legitscaff","legitscaf","fastplace"}, new String[]{}, "Legitscaff", "defend your bed and watch for fast bridges"},
        {1, new String[]{"eagle"},                       new String[]{}, "Eagle",        "deny their crossing"},
        {0, new String[]{"2q","3q","4q","boosting","queue"}, new String[]{}, "Booster",  "stats are inflated"},
    };

    private String recommend(IntelPlayer p) {
        if (p.cheater && p.urchinTag != null) {
            String type   = p.urchinType   != null ? p.urchinType   : "";
            String reason = p.urchinReason != null ? p.urchinReason : "";

            // Collect ALL matching cheat tiers, sorted by severity desc
            java.util.List<Object[]> matches = new java.util.ArrayList<>();
            for (Object[] tier : CHEAT_ADVICE) {
                String[] rWords = (String[]) tier[1];
                String[] tWords = (String[]) tier[2];
                boolean matched = false;
                for (String kw : rWords) {
                    boolean hit = (kw.contains(" ") || kw.length() > 6)
                            ? reason.contains(kw) : hasWord(reason, kw);
                    if (hit) { matched = true; break; }
                }
                if (!matched) for (String kw : tWords) if (type.contains(kw)) { matched = true; break; }
                if (matched) matches.add(tier);
            }

            if (!matches.isEmpty()) {
                // Threat header based on averaged score
                int tl = urchinThreatLevel(p);
                String header = tl == 2 ? "HIGH THREAT" : tl == 1 ? "MEDIUM THREAT" : "LOW THREAT";

                // List every detected cheat with its advice, highest severity first
                StringBuilder sb = new StringBuilder(header).append(" | ");
                java.util.List<String> parts = new java.util.ArrayList<>();
                for (Object[] m : matches) {
                    String label  = (String) m[3];
                    String advice = (String) m[4];
                    parts.add(label + ": " + advice);
                }
                sb.append(String.join(". ", parts)).append(".");
                return sb.toString();
            }

            // Generic fallback
            int tl = urchinThreatLevel(p);
            if (tl == 2) return "HIGH THREAT | Blatant cheater. Do not engage. Rush bed and escape.";
            if (tl == 1) return "MEDIUM THREAT | Confirmed cheater. Avoid prolonged fights. Focus bed destruction.";
            return "LOW THREAT | Flagged by Urchin. Play with extra awareness.";
        }
        if (p.threatScore >= 75) return "High priority. Rush their bed early before they gear up. Avoid 1v1 without advantage.";
        if (p.threatScore >= 50) return "Solid player. Contest mid early and engage with armor advantage.";
        if (p.threatScore >= 25) return "Average. Farm first, safe to deprioritise until mid-game.";
        return "Low threat. Easy target — rush early for free resources and a quick bed.";
    }

    private boolean containsAny(String text, String... keywords) {
        for (String k : keywords) if (text.contains(k)) return true;
        return false;
    }

    private List<String> wrap(String text, int maxW) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String w : text.split(" ")) {
            String test = cur.length() == 0 ? w : cur + " " + w;
            if (mc.fontRendererObj.getStringWidth(test) > maxW) {
                out.add(cur.toString()); cur = new StringBuilder(w);
            } else cur = new StringBuilder(test);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    private void drawCentred(String text, int centreX, int y, int color) {
        int w = mc.fontRendererObj.getStringWidth(text);
        gl(); mc.fontRendererObj.drawString(text, centreX - w / 2f, y, color, false);
    }

    private void fillRect(int x, int y, int w, int h, int color) {
        Gui.drawRect(x, y, x + w, y + h, color);
    }

    private void gl() {
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }
}
