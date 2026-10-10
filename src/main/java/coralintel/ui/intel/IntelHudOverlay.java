package coralintel.ui.intel;

import coralintel.CoralIntel;
import coralintel.ui.clickgui.GuiColors;
import coralintel.ui.clickgui.RoundedUtils;
import coralintel.util.PrestigeUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class IntelHudOverlay {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final int LINE_HEIGHT = 18;
    private static final int HEAD_SIZE = 14;
    private static final int PADDING = 6;
    private static final int HEADER_HEIGHT = 12;
    private static final int BORDER_RADIUS = 4;

    private int bgOpacity = 200;
    private int borderOpacity = 100;
    /** Stat column width as a percentage of the default (100 = original widths). NAME stays fixed. */
    private int columnWidthPercent = 100;
    private int columnLineOpacity = 26; // matches the original hardcoded 0x1A alpha
    private int bgColorRgb = 0x07070E;
    private int borderColorRgb = 0xFFFFFF;
    private int columnColorRgb = 0xFFFFFF;
    private static final int ACCENT = GuiColors.ACCENT;
    private static final int TEXT_BRIGHT = 0xFFDDDDEE;
    private static final int TEXT_DIM = 0xFF888899;

    private boolean enabled = true;
    private int posX = 10;
    private int posY = 100;
    private float scale = 1.0f;
    private int maxPlayers = 10;

    private boolean showHeads = true;
    private boolean showStar = true;
    private boolean showLevel = false;
    private boolean showFkdr = true;
    private boolean showWlr = false;
    private boolean showStreak = false;
    private boolean showThreat = true;
    private boolean showUrchin = true;
    private boolean showTeamColor = true;
    private String sortMode = "threat";
    /**
     * Visual style: "classic", "minimal", "striped", "cards", "outline" or "heatmap".
     * Styles only change how the panel looks — the columns, stats and players are identical.
     */
    private String style = "classic";
    private static final int TEAM_GAP = 3;
    private static final int CARD_GAP = 8;

    private List<IntelPlayer> players = new ArrayList<>();

    private final java.util.Map<String, ResourceLocation> skinCache =
            new java.util.HashMap<>();

    private final java.util.Map<String, Boolean> skinIsSheet =
            new java.util.HashMap<>();

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setPosition(int x, int y) {
        this.posX = x;
        this.posY = y;
    }

    public void setScale(float scale) {
        this.scale = Math.max(0.5f, Math.min(2.0f, scale));
    }

    public void setMaxPlayers(int max) {
        this.maxPlayers = Math.max(1, Math.min(20, max));
    }

    public void setShowHeads(boolean show) {
        this.showHeads = show;
    }

    public void setShowStar(boolean show) {
        this.showStar = show;
    }

    public void setShowLevel(boolean show) {
        this.showLevel = show;
    }

    public void setShowFkdr(boolean show) {
        this.showFkdr = show;
    }

    public void setShowWlr(boolean show) {
        this.showWlr = show;
    }

    public void setShowStreak(boolean show) {
        this.showStreak = show;
    }

    public void setShowThreat(boolean show) {
        this.showThreat = show;
    }

    public void setShowUrchin(boolean show) {
        this.showUrchin = show;
    }

    public void setShowTeamColor(boolean show) {
        this.showTeamColor = show;
    }

    public void setStyle(String style) {
        this.style = style == null ? "classic" : style.toLowerCase(java.util.Locale.ROOT);
    }

    public String getStyle() {
        return style;
    }

    public void setSortMode(String mode) {
        this.sortMode = mode;
        sortPlayers();
    }

    public void setBgOpacity(int opacity) {
        this.bgOpacity = Math.max(0, Math.min(255, opacity));
    }

    public void setBorderOpacity(int opacity) {
        this.borderOpacity = Math.max(0, Math.min(255, opacity));
    }

    public void setColumnWidthPercent(int percent) {
        this.columnWidthPercent = Math.max(100, Math.min(200, percent));
    }

    public int getColumnWidthPercent() {
        return columnWidthPercent;
    }

    /** Scales a default stat-column width by the Column Width setting. */
    private int cw(int base) {
        return Math.round(base * columnWidthPercent / 100f);
    }

    public void setColumnLineOpacity(int opacity) {
        this.columnLineOpacity = Math.max(0, Math.min(255, opacity));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getPosX() {
        return posX;
    }

    public int getPosY() {
        return posY;
    }

    public float getScale() {
        return scale;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public boolean getShowHeads() {
        return showHeads;
    }

    public boolean getShowStar() {
        return showStar;
    }

    public boolean getShowLevel() {
        return showLevel;
    }

    public boolean getShowFkdr() {
        return showFkdr;
    }

    public boolean getShowWlr() {
        return showWlr;
    }

    public boolean getShowStreak() {
        return showStreak;
    }

    public boolean getShowThreat() {
        return showThreat;
    }

    public boolean getShowUrchin() {
        return showUrchin;
    }

    public boolean getShowTeamColor() {
        return showTeamColor;
    }

    public String getSortMode() {
        return sortMode;
    }

    public int getBgOpacity() {
        return bgOpacity;
    }

    public int getBorderOpacity() {
        return borderOpacity;
    }

    public int getColumnLineOpacity() {
        return columnLineOpacity;
    }

    public void setBgColorRgb(int rgb) { this.bgColorRgb = rgb & 0xFFFFFF; }

    public void setBorderColorRgb(int rgb) { this.borderColorRgb = rgb & 0xFFFFFF; }

    public void setColumnColorRgb(int rgb) { this.columnColorRgb = rgb & 0xFFFFFF; }

    public void setPlayers(List<IntelPlayer> players) {
        this.players = new ArrayList<>(players);
        sortPlayers();
    }

    public void cacheSkin(String name, ResourceLocation skin, boolean isSheet) {
        skinCache.put(name, skin);
        skinIsSheet.put(name, isSheet);
    }

    private static int teamRank(String team) {
        if (team == null) return 99;
        switch (team.toLowerCase()) {
            case "red":    return 0;
            case "blue":   return 1;
            case "green":  return 2;
            case "yellow": return 3;
            case "aqua":   return 4;
            case "white":  return 5;
            case "pink":   return 6;
            case "gray":   return 7;
            default:       return 98;
        }
    }

    /**
     * The player whose stats this row shows. Normally the row's own player; for a nick whose real
     * account was found (Bedlify) and whose stats have loaded, the REAL player's stats, so a
     * denicked player gets a full row here instead of "NICK" and dashes (same as the tab list).
     */
    private static IntelPlayer statsOf(IntelPlayer row) {
        try {
            if (row != null && row.isNicked) {
                coralintel.module.modules.LobbyIntel li = (coralintel.module.modules.LobbyIntel)
                        CoralIntel.moduleManager.getModule(coralintel.module.modules.LobbyIntel.class);
                IntelPlayer real = row.realStats;
                if (li != null && li.hudShowRealStats.getValue() && real != null && !real.loading) {
                    return real;
                }
            }
        } catch (Exception ignored) {
        }
        return row;
    }

    /** Sort keys captured once per sort, so stats landing on other threads can't change them mid-sort. */
    private static class SortKey {
        final IntelPlayer player;
        final double threat;
        final double fkdr;
        final int star;
        final int teamRank;
        final String name;

        SortKey(IntelPlayer player) {
            this.player = player;
            IntelPlayer stats = statsOf(player);
            this.threat = stats.threatScore;
            this.fkdr = stats.fkdr;
            this.star = stats.star;
            this.teamRank = teamRank(player.team);
            this.name = player.name == null ? "" : player.name;
        }
    }

    private void sortPlayers() {
        if (players.size() < 2) return;

        List<SortKey> keys = new ArrayList<>(players.size());
        for (IntelPlayer player : players) {
            keys.add(new SortKey(player));
        }

        Comparator<SortKey> byName = (a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.name, b.name);
        Comparator<SortKey> byThreat = (a, b) -> Double.compare(b.threat, a.threat);
        Comparator<SortKey> cmp;

        switch (sortMode) {
            case "fkdr":
                cmp = ((Comparator<SortKey>) (a, b) -> Double.compare(b.fkdr, a.fkdr)).thenComparing(byName);
                break;

            case "star":
                cmp = ((Comparator<SortKey>) (a, b) -> Integer.compare(b.star, a.star)).thenComparing(byName);
                break;

            case "name":
                cmp = byName;
                break;

            case "team":
                // Group by team (red, blue, green, yellow, aqua, white, pink,
                // gray; unassigned last), highest threat first inside each.
                cmp = ((Comparator<SortKey>) (a, b) -> Integer.compare(a.teamRank, b.teamRank))
                        .thenComparing(byThreat)
                        .thenComparing(byName);
                break;

            case "threat":
            default:
                cmp = byThreat.thenComparing(byName);
                break;
        }

        // Every HUD mode groups by team first; the chosen sort only orders players inside a team
        // (before teams exist, everyone is one group, so it orders the whole lobby).
        keys.sort(Comparator.<SortKey>comparingInt(k -> k.teamRank).thenComparing(cmp));

        List<IntelPlayer> sorted = new ArrayList<>(keys.size());
        for (SortKey key : keys) {
            sorted.add(key.player);
        }
        players = sorted;
    }

    private List<IntelPlayer> getDisplayPlayers() {
        List<IntelPlayer> base = getDisplayPlayersBase();
        List<IntelPlayer> result = new ArrayList<>(base.size());

        for (IntelPlayer player : base) {
            // Streamer mode: your own row (name, stats, tags) is not drawn.
            if (coralintel.module.modules.StreamerMode.hidesStatsFor(player.name)) continue;
            result.add(player);
        }

        return result;
    }

    private List<IntelPlayer> getDisplayPlayersBase() {
        coralintel.module.modules.LobbyIntel lobbyIntel =
                (coralintel.module.modules.LobbyIntel) CoralIntel.moduleManager.getModule(
                        coralintel.module.modules.LobbyIntel.class
                );

        boolean hideTeammates =
                lobbyIntel != null && lobbyIntel.hideTeammates.getValue();

        if (!hideTeammates) {
            return new ArrayList<>(players);
        }

        String myTeam = null;
        String myName = mc.thePlayer != null ? mc.thePlayer.getName() : null;

        if (myName != null) {
            for (IntelPlayer player : players) {
                if (player.name.equals(myName)) {
                    myTeam = player.team;
                    break;
                }
            }
        }

        List<IntelPlayer> filtered = new ArrayList<>();

        for (IntelPlayer player : players) {
            if (myName != null && player.name.equals(myName)) continue;

            if (myTeam != null
                    && player.team != null
                    && player.team.equals(myTeam)) {
                continue;
            }

            filtered.add(player);
        }

        return filtered;
    }

    public void render() {
        if (!enabled || players.isEmpty()) return;

        // Re-sort every frame (cheap, <= 80 rows) so the order always matches
        // the CURRENT teams/stats — teams get assigned after the roster was
        // last pushed, and that used to leave the list in its old order.
        sortPlayers();

        List<IntelPlayer> displayPlayers = getDisplayPlayers();

        if (displayPlayers.isEmpty()) return;

        renderPanel(displayPlayers);
    }

    private void renderPanel(List<IntelPlayer> displayPlayers) {
        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, 1.0f);

        int scaledX = (int) (posX / scale);
        int scaledY = (int) (posY / scale);

        int displayCount = Math.min(displayPlayers.size(), maxPlayers);
        int width = calculateWidth();
        int teamGaps = 0;
        for (int i = 1; i < displayCount; i++) {
            if (teamBreak(displayPlayers.get(i - 1), displayPlayers.get(i))) teamGaps++;
        }
        int contentHeight = (LINE_HEIGHT * displayCount) + (teamGap() * teamGaps) + (PADDING * 2);
        int totalHeight = HEADER_HEIGHT + contentHeight;

        int dividerY = scaledY + HEADER_HEIGHT;
        int headerLineColor = (columnLineOpacity << 24) | columnColorRgb;
        drawPanelChrome(scaledX, scaledY, width, totalHeight, displayPlayers, displayCount);

        int headerY = scaledY + 3;
        int x = scaledX + PADDING;

        List<Integer> columnBoundaries = new ArrayList<>();

        if (showHeads) x += HEAD_SIZE + 4;

        drawText("NAME", x, headerY, 0xFFFFFFFF);
        x += 120;
        columnBoundaries.add(x);

        if (showStar) {
            int headerWidth = mc.fontRendererObj.getStringWidth("✫");
            drawText("✫", x + (cw(35) - headerWidth) / 2, headerY, 0xFFFFFFFF);
            x += cw(35);
            columnBoundaries.add(x);
        }

        if (showLevel) {
            int headerWidth = mc.fontRendererObj.getStringWidth("LVL");
            drawText("LVL", x + (cw(35) - headerWidth) / 2, headerY, 0xFFFFFFFF);
            x += cw(35);
            columnBoundaries.add(x);
        }

        if (showFkdr) {
            int headerWidth = mc.fontRendererObj.getStringWidth("FKDR");
            drawText("FKDR", x + (cw(40) - headerWidth) / 2, headerY, 0xFFFFFFFF);
            x += cw(40);
            columnBoundaries.add(x);
        }

        if (showWlr) {
            int headerWidth = mc.fontRendererObj.getStringWidth("WLR");
            drawText("WLR", x + (cw(35) - headerWidth) / 2, headerY, 0xFFFFFFFF);
            x += cw(35);
            columnBoundaries.add(x);
        }

        if (showStreak) {
            int headerWidth = mc.fontRendererObj.getStringWidth("WS");
            drawText("WS", x + (cw(30) - headerWidth) / 2, headerY, 0xFFFFFFFF);
            x += cw(30);
            columnBoundaries.add(x);
        }

        if (showUrchin) {
            int headerWidth = mc.fontRendererObj.getStringWidth("TAGS");
            drawText("TAGS", x + (cw(35) - headerWidth) / 2, headerY, 0xFFFFFFFF);
            x += cw(35);
            columnBoundaries.add(x);
        }

        if (showThreat) {
            int headerWidth = mc.fontRendererObj.getStringWidth("THREAT");
            drawText("THREAT", x + (cw(45) - headerWidth) / 2, headerY, 0xFFFFFFFF);
        }

        // Column separator lines, spanning the content area below the header.
        if (!columnBoundaries.isEmpty() && style.equals("classic")) {
            // Drop the last boundary — no line needed after the final column.
            columnBoundaries.remove(columnBoundaries.size() - 1);

            for (int boundaryX : columnBoundaries) {
                int columnLineColor = (columnLineOpacity << 24) | columnColorRgb;
                fillRect(boundaryX - 3, dividerY + 2, 1, totalHeight - HEADER_HEIGHT - 4, columnLineColor);
            }
        }

        int y = scaledY + HEADER_HEIGHT + PADDING;

        for (int i = 0; i < displayCount; i++) {
            if (i > 0 && teamBreak(displayPlayers.get(i - 1), displayPlayers.get(i))) y += teamGap();
            boolean lastInTeam = i == displayCount - 1
                    || teamBreak(displayPlayers.get(i), displayPlayers.get(i + 1));
            drawRowBackground(displayPlayers.get(i), i, scaledX, y, width, lastInTeam);
            drawPlayerLine(
                    displayPlayers.get(i),
                    scaledX + PADDING,
                    y
            );

            y += LINE_HEIGHT;
        }

        GlStateManager.popMatrix();
    }

    /** Space between team groups — the Cards style needs more room for the separate boxes. */
    private int teamGap() {
        return style.equals("cards") ? CARD_GAP : TEAM_GAP;
    }

    /** Draws the panel background/frame for the current style (text and columns are drawn afterwards). */
    private void drawPanelChrome(int x, int y, int width, int totalHeight,
                                 List<IntelPlayer> rows, int count) {
        int bg = (bgOpacity << 24) | bgColorRgb;
        int border = (borderOpacity << 24) | borderColorRgb;
        int line = (columnLineOpacity << 24) | columnColorRgb;

        switch (style) {
            case "minimal":
                // No panel at all: floating rows (see drawRowBackground) and a hairline under the header.
                fillRect(x + 2, y + HEADER_HEIGHT, width - 4, 1, line);
                break;

            case "striped": {
                // Square panel, tinted header bar, alternating row stripes.
                fillRect(x, y, width, totalHeight, bg);
                fillRect(x, y, width, HEADER_HEIGHT, (Math.min(255, columnLineOpacity * 3) << 24) | columnColorRgb);
                drawSquareOutline(x, y, width, totalHeight, border);
                break;
            }

            case "cards": {
                // The header and every team get their own rounded box.
                RoundedUtils.drawRoundedRect(x, y, width, HEADER_HEIGHT, BORDER_RADIUS, bg);
                RoundedUtils.drawRoundedOutline(x, y, width, HEADER_HEIGHT, BORDER_RADIUS, 1.0f, border);
                int ry = y + HEADER_HEIGHT + PADDING;
                int groupTop = ry;
                for (int i = 0; i < count; i++) {
                    if (i > 0 && teamBreak(rows.get(i - 1), rows.get(i))) {
                        drawCard(x, groupTop, width, ry, bg, border);
                        ry += CARD_GAP;
                        groupTop = ry;
                    }
                    ry += LINE_HEIGHT;
                }
                drawCard(x, groupTop, width, ry, bg, border);
                break;
            }

            case "outline": {
                // See-through panel with a bold frame.
                RoundedUtils.drawRoundedRect(x, y, width, totalHeight, BORDER_RADIUS,
                        (Math.min(bgOpacity, 60) << 24) | bgColorRgb);
                RoundedUtils.drawRoundedOutline(x, y, width, totalHeight, BORDER_RADIUS, 1.5f,
                        (Math.max(borderOpacity, 170) << 24) | borderColorRgb);
                fillRect(x + 2, y + HEADER_HEIGHT, width - 4, 1, line);
                break;
            }

            case "heatmap":
            case "classic":
            default:
                RoundedUtils.drawRoundedRect(x, y, width, totalHeight, BORDER_RADIUS, bg);
                RoundedUtils.drawRoundedOutline(x, y, width, totalHeight, BORDER_RADIUS, 1.5f, border);
                fillRect(x + 2, y + HEADER_HEIGHT, width - 4, 1, line);
                break;
        }
    }

    private void drawCard(int x, int groupTop, int width, int groupBottom, int bg, int border) {
        int top = groupTop - 2;
        int height = (groupBottom - groupTop) + 4;
        RoundedUtils.drawRoundedRect(x, top, width, height, BORDER_RADIUS, bg);
        RoundedUtils.drawRoundedOutline(x, top, width, height, BORDER_RADIUS, 1.0f, border);
    }

    private void drawSquareOutline(int x, int y, int width, int height, int color) {
        fillRect(x, y, width, 1, color);
        fillRect(x, y + height - 1, width, 1, color);
        fillRect(x, y, 1, height, color);
        fillRect(x + width - 1, y, 1, height, color);
    }

    /** Per-row decoration for the current style. y is the top of the row. */
    private void drawRowBackground(IntelPlayer player, int index, int x, int y, int width, boolean lastInTeam) {
        switch (style) {
            case "minimal":
                RoundedUtils.drawRoundedRect(x + 1, y, width - 2, LINE_HEIGHT - 1, 2,
                        (Math.max(30, bgOpacity / 2) << 24) | bgColorRgb);
                break;

            case "striped":
                if (index % 2 == 1) {
                    fillRect(x + 1, y, width - 2, LINE_HEIGHT, (22 << 24) | columnColorRgb);
                }
                break;

            case "outline":
                if (!lastInTeam) {
                    fillRect(x + PADDING, y + LINE_HEIGHT - 1, width - PADDING * 2, 1,
                            (columnLineOpacity << 24) | columnColorRgb);
                }
                break;

            case "heatmap":
                IntelPlayer shown = statsOf(player);
                if (!shown.loading) {
                    int threat = getThreatColor((int) shown.threatScore) & 0xFFFFFF;
                    RoundedUtils.drawRoundedRect(x + 2, y, width - 4, LINE_HEIGHT - 1, 2, (55 << 24) | threat);
                }
                break;

            default:
                break;
        }
    }

    /** True when two neighbouring rows belong to different teams (a small gap is drawn between teams). */
    private static boolean teamBreak(IntelPlayer a, IntelPlayer b) {
        String ta = a.team == null ? "" : a.team;
        String tb = b.team == null ? "" : b.team;
        return !ta.equalsIgnoreCase(tb);
    }

    private int calculateWidth() {
        int width = PADDING * 2;

        if (showHeads) width += HEAD_SIZE + 4;

        width += 120;

        if (showStar) width += cw(35);
        if (showLevel) width += cw(35);
        if (showFkdr) width += cw(40);
        if (showWlr) width += cw(35);
        if (showStreak) width += cw(30);
        if (showUrchin) width += cw(35);
        if (showThreat) width += cw(45);

        return width;
    }

    private void drawPlayerLine(IntelPlayer row, int x, int y) {
        // Name, head and team come from the lobby row; stats come from the real player when denicked.
        IntelPlayer player = statsOf(row);
        int currentX = x;

        if (showHeads) {
            drawPlayerHead(row.name, currentX, y + 2, HEAD_SIZE);
            currentX += HEAD_SIZE + 4;
        }

        int nameColor = player.cheater ? 0xFFFF4444 : TEXT_BRIGHT;

        if (player.threatScore >= 75) {
            nameColor = ACCENT;
        }

        // In an active match (team assigned) — color the name by team,
        // same as BedWarsTag and the tab list do. Team color always wins over
        // the cheater/high-threat name color; those still show in the tag and
        // threat columns. In the lobby (no team yet) — prefix the Hypixel
        // rank instead, since there's no team to show.
        String displayName = row.name;

        if (row.team != null && !row.team.isEmpty()) {
            if (showTeamColor) {
                nameColor = getTeamColor(row.team);
            }
        } else if (row.rankPrefix != null && !row.rankPrefix.isEmpty()) {
            displayName = stripColorCodes(row.rankPrefix) + " " + row.name;
        }

        if (player != row && row.realName != null) {
            displayName = mc.fontRendererObj.trimStringToWidth(
                    displayName + " \u00A77(\u00A7e" + row.realName + "\u00A77)", 116);
        }

        drawText(displayName, currentX, y + 4, nameColor);
        currentX += 120;

        if (showStar) {
            // Per-character prestige colors from PrestigeUtil (Nevada table);
            // the embedded §-codes override the base color below.
            String text = player.loading ? "-" : PrestigeUtil.formatCompact(player.star);
            int color = player.loading ? TEXT_DIM : getPrestigeColor(player.star);
            int textWidth = mc.fontRendererObj.getStringWidth(text);

            drawText(text, currentX + (cw(35) - textWidth) / 2, y + 4, color);
            currentX += cw(35);
        }

        if (showLevel) {
            String text = player.loading ? "-" : String.valueOf(player.level);
            int textWidth = mc.fontRendererObj.getStringWidth(text);

            drawText(
                    text,
                    currentX + (cw(35) - textWidth) / 2,
                    y + 4,
                    player.loading ? TEXT_DIM : 0xFFFFFFFF
            );

            currentX += cw(35);
        }

        if (showFkdr) {
            String text = player.loading || player.fkdr < 0
                    ? "-"
                    : String.format("%.1f", player.fkdr);

            int color = player.loading
                    ? TEXT_DIM
                    : getStatColor(player.fkdr, 3.0, 6.0);

            int textWidth = mc.fontRendererObj.getStringWidth(text);
            drawText(text, currentX + (cw(40) - textWidth) / 2, y + 4, color);
            currentX += cw(40);
        }

        if (showWlr) {
            String text = player.loading || player.wlr < 0
                    ? "-"
                    : String.format("%.1f", player.wlr);

            int color = player.loading
                    ? TEXT_DIM
                    : getStatColor(player.wlr, 2.0, 4.0);

            int textWidth = mc.fontRendererObj.getStringWidth(text);
            drawText(text, currentX + (cw(35) - textWidth) / 2, y + 4, color);
            currentX += cw(35);
        }

        if (showStreak) {
            String text = player.loading || player.winstreak < 0
                    ? "-"
                    : String.valueOf(player.winstreak);

            int color = player.loading
                    ? TEXT_DIM
                    : player.winstreak >= 10
                            ? 0xFFFFCC44
                            : player.winstreak >= 5
                                    ? 0xFF44DD66
                                    : TEXT_DIM;

            int textWidth = mc.fontRendererObj.getStringWidth(text);
            drawText(text, currentX + (cw(30) - textWidth) / 2, y + 4, color);
            currentX += cw(30);
        }

        if (showUrchin) {
            String displayText = "";
            int displayColor = TEXT_DIM;

            if (player.isNicked && !player.loading) {
                displayText = "NICK";
                displayColor = 0xFFAA00AA;
            } else if (player.cheater || player.blacklisted) {
                displayText = player.getTagBadge();
                displayColor = player.getTagColor();
            }

            if (player.ghostTagged && player.ghostType != null) {
                String ghostIcon = "A";
                int ghostColor = 0xFFFF69B4;
                String type = player.ghostType.toLowerCase();

                if (type.contains("account")) {
                    ghostIcon = "A";
                    ghostColor = 0xFFFF69B4;
                } else if (type.contains("caution")) {
                    ghostIcon = "C";
                    ghostColor = 0xFFFFAA00;
                } else if (type.contains("closet")) {
                    ghostIcon = "CC";
                    ghostColor = 0xFFFF8800;
                } else if (type.contains("blatant")) {
                    ghostIcon = "BC";
                    ghostColor = 0xFFCCAA00;
                } else if (type.contains("sniper")) {
                    ghostIcon = "S";
                    ghostColor = 0xFFFF0000;
                } else if (type.contains("verified")) {
                    ghostIcon = "VC";
                    ghostColor = 0xFFFF00AA;
                } else {
                    ghostIcon = "G";
                    ghostColor = 0xFF00FFFF;
                }

                displayText = displayText.isEmpty()
                        ? ghostIcon
                        : displayText + "/" + ghostIcon;

                displayColor = ghostColor;
            }

            if (!displayText.isEmpty()) {
                int textWidth = mc.fontRendererObj.getStringWidth(displayText);
                drawText(
                        displayText,
                        currentX + (cw(35) - textWidth) / 2,
                        y + 4,
                        displayColor
                );
            }

            currentX += cw(35);
        }

        if (showThreat) {
            String text = player.loading
                    ? "-"
                    : String.valueOf((int) player.threatScore);

            int textWidth = mc.fontRendererObj.getStringWidth(text);

            drawText(
                    text,
                    currentX + (cw(45) - textWidth) / 2,
                    y + 4,
                    player.loading ? TEXT_DIM : getThreatColor((int) player.threatScore)
            );
        }
    }

    private void drawPlayerHead(String name, int x, int y, int size) {
        try {
            ResourceLocation skin = skinCache.get(name);

            if (skin == null && mc.getNetHandler() != null) {
                NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(name);

                if (info != null) {
                    skin = info.getLocationSkin();

                    if (skin != null) {
                        skinCache.put(name, skin);
                        skinIsSheet.put(name, true);
                    }
                }
            }

            if (skin == null) {
                skin = new ResourceLocation("textures/entity/steve.png");
                skinIsSheet.put(name, true);
            }

            GlStateManager.pushMatrix();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(
                    GL11.GL_SRC_ALPHA,
                    GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE,
                    GL11.GL_ZERO
            );
            GlStateManager.enableAlpha();
            GlStateManager.color(1f, 1f, 1f, 1f);
            mc.getTextureManager().bindTexture(skin);

            boolean isSheet = skinIsSheet.getOrDefault(name, true);

            if (isSheet) {
                Gui.drawScaledCustomSizeModalRect(
                        x, y, 8f, 8f, 8, 8, size, size, 64f, 64f
                );

                Gui.drawScaledCustomSizeModalRect(
                        x, y, 40f, 8f, 8, 8, size, size, 64f, 64f
                );
            } else {
                Gui.drawScaledCustomSizeModalRect(
                        x, y, 0f, 0f, 16, 16, size, size, 16f, 16f
                );
            }

            GlStateManager.color(1f, 1f, 1f, 1f);
            GlStateManager.popMatrix();
        } catch (Exception ignored) {
        }
    }

    private int getTeamColor(String team) {
        switch (team.toLowerCase()) {
            case "red":
                return 0xFFFF4444;
            case "blue":
                return 0xFF4488FF;
            case "green":
                return 0xFF44FF66;
            case "yellow":
                return 0xFFFFFF44;
            case "aqua":
                return 0xFF44FFFF;
            case "white":
                return 0xFFEEEEEE;
            case "pink":
                return 0xFFFF88CC;
            case "gray":
            default:
                return 0xFF888888;
        }
    }

    private int getThreatColor(int score) {
        return IntelColors.getThreatColor(score);
    }

    private int getStatColor(double value, double mid, double high) {
        return IntelColors.getStatColor(value, mid, high);
    }

    private int getPrestigeColor(int star) {
        return IntelColors.getPrestigeColor(star);
    }

    private void drawText(String text, int x, int y, int color) {
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        mc.fontRendererObj.drawString(text, x, y, color, true);
    }

    /**
     * Strips embedded §-formatting codes from a string. Used for the rank
     * prefix in the name column so its own color codes don't bleed into
     * (and override) the cheater/high-threat highlight color that follows it.
     */
    private String stripColorCodes(String text) {
        return text.replaceAll("(?i)\u00A7[0-9A-FK-OR]", "");
    }

    private void fillRect(int x, int y, int width, int height, int color) {
        // GlStateManager caches state: if anything toggled blending with raw GL the cache can say
        // "enabled" while real GL blend is off, so Gui.drawRect writes fully opaque (low opacity
        // then looks solid black). Force the real state on before drawing, like RoundedUtils does.
        GlStateManager.enableBlend();
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_BLEND);
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        Gui.drawRect(x, y, x + width, y + height, color);
    }

    private void drawRoundedRect(
            int x,
            int y,
            int x2,
            int y2,
            int radius,
            int color
    ) {
        RoundedUtils.drawRoundedRect(x, y, x2 - x, y2 - y, radius, color);
    }
}
