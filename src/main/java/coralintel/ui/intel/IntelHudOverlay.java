package coralintel.ui.intel;

import coralintel.CoralIntel;
import coralintel.ui.clickgui.GuiColors;
import coralintel.ui.clickgui.RoundedUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
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
    private int columnLineOpacity = 26; // matches the original hardcoded 0x1A alpha
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

    public void setSortMode(String mode) {
        this.sortMode = mode;
    }

    public void setBgOpacity(int opacity) {
        this.bgOpacity = Math.max(0, Math.min(255, opacity));
    }

    public void setBorderOpacity(int opacity) {
        this.borderOpacity = Math.max(0, Math.min(255, opacity));
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

    public void handleClick(int mx, int my) {
    }

    public void setPlayers(List<IntelPlayer> players) {
        this.players = new ArrayList<>(players);
        sortPlayers();
    }

    public void cacheSkin(String name, ResourceLocation skin, boolean isSheet) {
        skinCache.put(name, skin);
        skinIsSheet.put(name, isSheet);
    }

    private void sortPlayers() {
        switch (sortMode) {
            case "threat":
                players.sort((a, b) -> Double.compare(b.threatScore, a.threatScore));
                break;

            case "fkdr":
                players.sort((a, b) -> Double.compare(b.fkdr, a.fkdr));
                break;

            case "name":
                players.sort(Comparator.comparing(p -> p.name));
                break;
        }
    }

    private List<IntelPlayer> getDisplayPlayers() {
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

        List<IntelPlayer> displayPlayers = getDisplayPlayers();

        if (displayPlayers.isEmpty()) return;

        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, 1.0f);

        int scaledX = (int) (posX / scale);
        int scaledY = (int) (posY / scale);

        int displayCount = Math.min(displayPlayers.size(), maxPlayers);
        int width = calculateWidth();
        int contentHeight = (LINE_HEIGHT * displayCount) + (PADDING * 2);
        int totalHeight = HEADER_HEIGHT + contentHeight;

        int bgColor = (bgOpacity << 24) | 0x07070E;

        drawRoundedRect(
                scaledX,
                scaledY,
                scaledX + width,
                scaledY + totalHeight,
                BORDER_RADIUS,
                bgColor
        );

        // Border — drawn as an actual outline on top of the fill, so it wraps
        // every edge of the panel (including the bottom) evenly, using the
        // real borderOpacity setting instead of a hardcoded alpha.
        int borderColor = (borderOpacity << 24) | 0xFFFFFF;
        RoundedUtils.drawRoundedOutline(
                scaledX,
                scaledY,
                width,
                totalHeight,
                BORDER_RADIUS,
                1.5f,
                borderColor
        );

        int dividerY = scaledY + HEADER_HEIGHT;
        int headerLineColor = (columnLineOpacity << 24) | 0xFFFFFF;
        fillRect(scaledX + 2, dividerY, width - 4, 1, headerLineColor);

        int headerY = scaledY + 3;
        int x = scaledX + PADDING;

        List<Integer> columnBoundaries = new ArrayList<>();

        if (showHeads) x += HEAD_SIZE + 4;
        if (showTeamColor) x += 3;

        drawText("NAME", x, headerY, 0xFFFFFFFF);
        x += 120;
        columnBoundaries.add(x);

        if (showStar) {
            int headerWidth = mc.fontRendererObj.getStringWidth("✫");
            drawText("✫", x + (35 - headerWidth) / 2, headerY, 0xFFFFFFFF);
            x += 35;
            columnBoundaries.add(x);
        }

        if (showLevel) {
            int headerWidth = mc.fontRendererObj.getStringWidth("LVL");
            drawText("LVL", x + (35 - headerWidth) / 2, headerY, 0xFFFFFFFF);
            x += 35;
            columnBoundaries.add(x);
        }

        if (showFkdr) {
            int headerWidth = mc.fontRendererObj.getStringWidth("FKDR");
            drawText("FKDR", x + (40 - headerWidth) / 2, headerY, 0xFFFFFFFF);
            x += 40;
            columnBoundaries.add(x);
        }

        if (showWlr) {
            int headerWidth = mc.fontRendererObj.getStringWidth("WLR");
            drawText("WLR", x + (35 - headerWidth) / 2, headerY, 0xFFFFFFFF);
            x += 35;
            columnBoundaries.add(x);
        }

        if (showStreak) {
            int headerWidth = mc.fontRendererObj.getStringWidth("WS");
            drawText("WS", x + (30 - headerWidth) / 2, headerY, 0xFFFFFFFF);
            x += 30;
            columnBoundaries.add(x);
        }

        if (showUrchin) {
            int headerWidth = mc.fontRendererObj.getStringWidth("TAGS");
            drawText("TAGS", x + (35 - headerWidth) / 2, headerY, 0xFFFFFFFF);
            x += 35;
            columnBoundaries.add(x);
        }

        if (showThreat) {
            int headerWidth = mc.fontRendererObj.getStringWidth("THREAT");
            drawText("THREAT", x + (45 - headerWidth) / 2, headerY, 0xFFFFFFFF);
        }

        // Column separator lines, spanning the content area below the header.
        if (!columnBoundaries.isEmpty()) {
            // Drop the last boundary — no line needed after the final column.
            columnBoundaries.remove(columnBoundaries.size() - 1);

            for (int boundaryX : columnBoundaries) {
                int columnLineColor = (columnLineOpacity << 24) | 0xFFFFFF;
                fillRect(boundaryX - 3, dividerY + 2, 1, totalHeight - HEADER_HEIGHT - 4, columnLineColor);
            }
        }

        int y = scaledY + HEADER_HEIGHT + PADDING;

        for (int i = 0; i < displayCount; i++) {
            drawPlayerLine(
                    displayPlayers.get(i),
                    scaledX + PADDING,
                    y
            );

            y += LINE_HEIGHT;
        }

        GlStateManager.popMatrix();
    }

    private int calculateWidth() {
        int width = PADDING * 2;

        if (showHeads) width += HEAD_SIZE + 4;
        if (showTeamColor) width += 3;

        width += 120;

        if (showStar) width += 35;
        if (showLevel) width += 35;
        if (showFkdr) width += 40;
        if (showWlr) width += 35;
        if (showStreak) width += 30;
        if (showUrchin) width += 35;
        if (showThreat) width += 45;

        return width;
    }

    private void drawPlayerLine(IntelPlayer player, int x, int y) {
        int currentX = x;

        if (showTeamColor && player.team != null && !player.team.isEmpty()) {
            fillRect(currentX, y + 2, 2, HEAD_SIZE, getTeamColor(player.team));
            currentX += 3;
        }

        if (showHeads) {
            drawPlayerHead(player.name, currentX, y + 2, HEAD_SIZE);
            currentX += HEAD_SIZE + 4;
        }

        int nameColor = player.cheater ? 0xFFFF4444 : TEXT_BRIGHT;

        if (player.threatScore >= 75) {
            nameColor = ACCENT;
        }

        drawText(player.name, currentX, y + 4, nameColor);
        currentX += 120;

        if (showStar) {
            String text = player.loading ? "-" : "✫" + player.star;
            int color = player.loading ? TEXT_DIM : getPrestigeColor(player.star);
            int textWidth = mc.fontRendererObj.getStringWidth(text);

            drawText(text, currentX + (35 - textWidth) / 2, y + 4, color);
            currentX += 35;
        }

        if (showLevel) {
            String text = player.loading ? "-" : String.valueOf(player.level);
            int textWidth = mc.fontRendererObj.getStringWidth(text);

            drawText(
                    text,
                    currentX + (35 - textWidth) / 2,
                    y + 4,
                    player.loading ? TEXT_DIM : 0xFFFFFFFF
            );

            currentX += 35;
        }

        if (showFkdr) {
            String text = player.loading || player.fkdr < 0
                    ? "-"
                    : String.format("%.1f", player.fkdr);

            int color = player.loading
                    ? TEXT_DIM
                    : getStatColor(player.fkdr, 3.0, 6.0);

            int textWidth = mc.fontRendererObj.getStringWidth(text);
            drawText(text, currentX + (40 - textWidth) / 2, y + 4, color);
            currentX += 40;
        }

        if (showWlr) {
            String text = player.loading || player.wlr < 0
                    ? "-"
                    : String.format("%.1f", player.wlr);

            int color = player.loading
                    ? TEXT_DIM
                    : getStatColor(player.wlr, 2.0, 4.0);

            int textWidth = mc.fontRendererObj.getStringWidth(text);
            drawText(text, currentX + (35 - textWidth) / 2, y + 4, color);
            currentX += 35;
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
            drawText(text, currentX + (30 - textWidth) / 2, y + 4, color);
            currentX += 30;
        }

        if (showUrchin) {
            String displayText = "";
            int displayColor = TEXT_DIM;

            if (player.cheater) {
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
                        currentX + (35 - textWidth) / 2,
                        y + 4,
                        displayColor
                );
            }

            currentX += 35;
        }

        if (showThreat) {
            String text = player.loading
                    ? "-"
                    : String.valueOf((int) player.threatScore);

            int textWidth = mc.fontRendererObj.getStringWidth(text);

            drawText(
                    text,
                    currentX + (45 - textWidth) / 2,
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

    private void fillRect(int x, int y, int width, int height, int color) {
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
