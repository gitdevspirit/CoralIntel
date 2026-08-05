package coralintel.ui.intel;

import coralintel.CoralIntel;
import coralintel.module.modules.LobbyIntel;
import coralintel.ui.clickgui.GuiColors;
import coralintel.ui.clickgui.RoundedUtils;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;

public class IntelHudSettingsGui extends GuiScreen {

    private final IntelHudOverlay hudOverlay;
    private final GuiScreen parent;

    private static final int POPUP_W = 280;
    private static final int POPUP_H = 520;
    private static final int PAD = 12;
    private static final int SLIDER_H = 28;
    private static final int TOGGLE_H = 24;

    private static final int BG_POPUP = 0xEE0A0A12;
    private static final int BG_HEADER = 0xFF15151D;
    private static final int ACCENT = GuiColors.ACCENT;
    private static final int TEXT_ON = 0xFFDDDDEE;
    private static final int TEXT_DIM = 0xFF888899;
    private static final int DIVIDER = 0x33FFFFFF;

    private static final int S_POS_X = 0;
    private static final int S_POS_Y = 1;
    private static final int S_SCALE = 2;
    private static final int S_MAX_P = 3;
    private static final int S_BG_OP = 4;
    private static final int S_BORDER = 5;
    private static final int S_COLUMN_LINE = 6;

    private int scrollOffset = 0;
    private int maxScroll = 0;
    private boolean dragging = false;
    private int dragId = -1;
    private int dragBarX = 0;
    private int dragBarW = 0;

    public IntelHudSettingsGui(IntelHudOverlay hudOverlay, GuiScreen parent) {
        this.hudOverlay = hudOverlay;
        this.parent = parent;
    }

    private int barW(int innerWidth, int max) {
        return innerWidth - mc.fontRendererObj.getStringWidth(String.valueOf(max)) - 8;
    }

    private int pixelToVal(int mouseX, int barX, int barWidth, int min, int max) {
        float percentage = Math.max(
                0f,
                Math.min(1f, (mouseX - barX) / (float) barWidth)
        );

        return min + Math.round(percentage * (max - min));
    }

    private void applySlider(int id, int mouseX, int barX, int barWidth) {
        switch (id) {
            case S_POS_X:
                hudOverlay.setPosition(
                        pixelToVal(mouseX, barX, barWidth, 0, 1920),
                        hudOverlay.getPosY()
                );
                break;

            case S_POS_Y:
                hudOverlay.setPosition(
                        hudOverlay.getPosX(),
                        pixelToVal(mouseX, barX, barWidth, 0, 1080)
                );
                break;

            case S_SCALE:
                hudOverlay.setScale(
                        pixelToVal(mouseX, barX, barWidth, 50, 200) / 100f
                );
                break;

            case S_MAX_P:
                hudOverlay.setMaxPlayers(
                        pixelToVal(mouseX, barX, barWidth, 1, 80)
                );
                break;

            case S_BG_OP:
                hudOverlay.setBgOpacity(
                        pixelToVal(mouseX, barX, barWidth, 0, 255)
                );
                break;

            case S_BORDER:
                hudOverlay.setBorderOpacity(
                        pixelToVal(mouseX, barX, barWidth, 0, 255)
                );
                break;

            case S_COLUMN_LINE:
                hudOverlay.setColumnLineOpacity(
                        pixelToVal(mouseX, barX, barWidth, 0, 255)
                );
                break;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawRect(0, 0, width, height, 0xCC000000);

        ScaledResolution sr = new ScaledResolution(mc);
        int scaledWidth = sr.getScaledWidth();
        int scaledHeight = sr.getScaledHeight();

        int popupX = (scaledWidth - POPUP_W) / 2;
        int popupY = (scaledHeight - POPUP_H) / 2;

        RoundedUtils.drawRoundedRect(
                popupX, popupY, POPUP_W, POPUP_H, 6, BG_POPUP
        );

        RoundedUtils.drawRoundedRect(
                popupX, popupY, POPUP_W, 36, 6, BG_HEADER
        );

        drawRect(
                popupX,
                popupY + 30,
                popupX + POPUP_W,
                popupY + 36,
                BG_HEADER
        );

        GlStateManager.pushMatrix();
        GlStateManager.translate(popupX + PAD, popupY + 12, 0);
        GlStateManager.scale(1.1f, 1.1f, 1f);
        mc.fontRendererObj.drawString("HUD Overlay Settings", 0, 0, ACCENT, false);
        GlStateManager.popMatrix();

        int closeX = popupX + POPUP_W - 28;
        int closeY = popupY + 10;

        boolean closeHovered = mouseX >= closeX
                && mouseX < closeX + 18
                && mouseY >= closeY
                && mouseY < closeY + 18;

        mc.fontRendererObj.drawString(
                "X",
                closeX + 5,
                closeY + 5,
                closeHovered ? 0xFFFF4444 : TEXT_ON,
                false
        );

        GlStateManager.pushMatrix();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);

        int scaleFactor = sr.getScaleFactor();
        int contentY = popupY + 46;
        int contentHeight = POPUP_H - 56;

        GL11.glScissor(
                popupX * scaleFactor,
                (scaledHeight - contentY - contentHeight) * scaleFactor,
                POPUP_W * scaleFactor,
                contentHeight * scaleFactor
        );

        int y = contentY - scrollOffset + PAD;
        int innerX = popupX + PAD;
        int innerWidth = POPUP_W - PAD * 2;

        y = drawToggle(
                innerX,
                y,
                innerWidth,
                "Enabled",
                hudOverlay.isEnabled(),
                mouseX,
                mouseY
        );

        y += 6;
        div(innerX, y, innerWidth);
        y += 12;

        label(innerX, y, "POSITION");
        y += 16;

        y = slider(
                innerX, y, innerWidth,
                "X Position",
                hudOverlay.getPosX(),
                0, 1920,
                S_POS_X,
                mouseX, mouseY
        );

        y = slider(
                innerX, y, innerWidth,
                "Y Position",
                hudOverlay.getPosY(),
                0, 1080,
                S_POS_Y,
                mouseX, mouseY
        );

        y += 6;
        div(innerX, y, innerWidth);
        y += 12;

        label(innerX, y, "DISPLAY");
        y += 16;

        y = slider(
                innerX, y, innerWidth,
                "Scale %",
                (int) (hudOverlay.getScale() * 100),
                50, 200,
                S_SCALE,
                mouseX, mouseY
        );

        y = slider(
                innerX, y, innerWidth,
                "Max Players",
                hudOverlay.getMaxPlayers(),
                1, 80,
                S_MAX_P,
                mouseX, mouseY
        );

        y = slider(
                innerX, y, innerWidth,
                "Background Opacity",
                hudOverlay.getBgOpacity(),
                0, 255,
                S_BG_OP,
                mouseX, mouseY
        );

        y = slider(
                innerX, y, innerWidth,
                "Border Opacity",
                hudOverlay.getBorderOpacity(),
                0, 255,
                S_BORDER,
                mouseX, mouseY
        );

        y = slider(
                innerX, y, innerWidth,
                "Column Line Opacity",
                hudOverlay.getColumnLineOpacity(),
                0, 255,
                S_COLUMN_LINE,
                mouseX, mouseY
        );

        y += 6;
        div(innerX, y, innerWidth);
        y += 12;

        label(innerX, y, "COLUMNS");
        y += 20;

        y = drawToggle(innerX, y, innerWidth, "Player Heads",
                hudOverlay.getShowHeads(), mouseX, mouseY);

        y = drawToggle(innerX, y, innerWidth, "Star",
                hudOverlay.getShowStar(), mouseX, mouseY);

        y = drawToggle(innerX, y, innerWidth, "Network Level",
                hudOverlay.getShowLevel(), mouseX, mouseY);

        y = drawToggle(innerX, y, innerWidth, "FKDR",
                hudOverlay.getShowFkdr(), mouseX, mouseY);

        y = drawToggle(innerX, y, innerWidth, "WLR",
                hudOverlay.getShowWlr(), mouseX, mouseY);

        y = drawToggle(innerX, y, innerWidth, "Winstreak",
                hudOverlay.getShowStreak(), mouseX, mouseY);

        y = drawToggle(innerX, y, innerWidth, "Tags",
                hudOverlay.getShowUrchin(), mouseX, mouseY);

        y = drawToggle(innerX, y, innerWidth, "Threat Score",
                hudOverlay.getShowThreat(), mouseX, mouseY);

        y = drawToggle(innerX, y, innerWidth, "Team Colors",
                hudOverlay.getShowTeamColor(), mouseX, mouseY);

        y += 8;
        div(innerX, y, innerWidth);
        y += 16;

        label(innerX, y, "SORTING");
        y += 20;

        y = dropdown(
                innerX,
                y,
                innerWidth,
                "Sort By",
                new String[]{"Threat", "FKDR", "Star", "Name"},
                hudOverlay.getSortMode().equals("threat")
                        ? 0
                        : hudOverlay.getSortMode().equals("fkdr")
                                ? 1
                                : hudOverlay.getSortMode().equals("star")
                                        ? 2
                                        : 3,
                mouseX,
                mouseY
        );

        int totalContentHeight = y - contentY + scrollOffset + PAD * 4;
        maxScroll = Math.max(0, totalContentHeight - contentHeight);

        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GlStateManager.popMatrix();

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void label(int x, int y, String text) {
        mc.fontRendererObj.drawString(text, x, y, TEXT_DIM, false);
    }

    private void div(int x, int y, int width) {
        drawRect(x, y, x + width, y + 1, DIVIDER);
    }

    private int slider(
            int x,
            int y,
            int width,
            String label,
            int value,
            int min,
            int max,
            int id,
            int mouseX,
            int mouseY
    ) {
        mc.fontRendererObj.drawString(label, x, y, TEXT_ON, false);
        y += 10;

        String valueText = String.valueOf(value);

        mc.fontRendererObj.drawString(
                valueText,
                x + width - mc.fontRendererObj.getStringWidth(valueText),
                y,
                ACCENT,
                false
        );

        int barWidth = barW(width, max);
        int barHeight = 8;

        drawRect(x, y, x + barWidth, y + barHeight, 0x66444444);

        float percentage = (value - min) / (float) (max - min);
        int fillWidth = Math.max(2, (int) (barWidth * percentage));

        drawRect(x, y, x + fillWidth, y + barHeight, ACCENT);

        int knobX = x + fillWidth - 5;
        int knobY = y - 2;

        drawRect(knobX, knobY, knobX + 10, knobY + 12, 0xFFFFFFFF);
        drawRect(knobX + 2, knobY + 2, knobX + 8, knobY + 10, ACCENT);

        return y + 20;
    }

    private int drawToggle(
            int x,
            int y,
            int width,
            String label,
            boolean value,
            int mouseX,
            int mouseY
    ) {
        boolean hovered = mouseX >= x
                && mouseX < x + width
                && mouseY >= y
                && mouseY < y + TOGGLE_H;

        mc.fontRendererObj.drawString(
                label,
                x,
                y + 8,
                hovered ? TEXT_ON : TEXT_DIM,
                false
        );

        int toggleWidth = 40;
        int toggleHeight = 20;
        int toggleX = x + width - toggleWidth;
        int toggleY = y + 4;

        RoundedUtils.drawRoundedRect(
                toggleX,
                toggleY,
                toggleWidth,
                toggleHeight,
                toggleHeight / 2,
                value
                        ? (ACCENT & 0x00FFFFFF) | 0x44000000
                        : 0x33444455
        );

        int knobSize = 16;
        int knobX = value
                ? toggleX + toggleWidth - knobSize - 2
                : toggleX + 2;

        RoundedUtils.drawRoundedRect(
                knobX,
                toggleY + 2,
                knobSize,
                knobSize,
                knobSize / 2,
                value ? ACCENT : 0xFF666677
        );

        return y + TOGGLE_H;
    }

    private int dropdown(
            int x,
            int y,
            int width,
            String label,
            String[] options,
            int selected,
            int mouseX,
            int mouseY
    ) {
        mc.fontRendererObj.drawString(label, x, y, TEXT_DIM, false);
        y += 12;

        boolean hovered = mouseX >= x
                && mouseX < x + width
                && mouseY >= y
                && mouseY < y + 24;

        RoundedUtils.drawRoundedRect(
                x,
                y,
                width,
                24,
                4,
                hovered ? 0x44FFFFFF : 0x22FFFFFF
        );

        mc.fontRendererObj.drawString(options[selected], x + 12, y + 8, TEXT_ON, false);
        mc.fontRendererObj.drawString("v", x + width - 20, y + 8, TEXT_DIM, false);

        return y + 32;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        ScaledResolution sr = new ScaledResolution(mc);
        int scaledWidth = sr.getScaledWidth();
        int scaledHeight = sr.getScaledHeight();

        int popupX = (scaledWidth - POPUP_W) / 2;
        int popupY = (scaledHeight - POPUP_H) / 2;

        int closeX = popupX + POPUP_W - 28;
        int closeY = popupY + 10;

        if (mouseX >= closeX
                && mouseX < closeX + 18
                && mouseY >= closeY
                && mouseY < closeY + 18) {
            saveAndClose();
            return;
        }

        if (mouseX < popupX
                || mouseX > popupX + POPUP_W
                || mouseY < popupY
                || mouseY > popupY + POPUP_H) {
            saveAndClose();
            return;
        }

        int y = popupY + 46 - scrollOffset + PAD;
        int innerX = popupX + PAD;
        int innerWidth = POPUP_W - PAD * 2;

        if (hit(mouseX, mouseY, innerX, y, innerWidth, TOGGLE_H)) {
            hudOverlay.setEnabled(!hudOverlay.isEnabled());
            return;
        }

        y += TOGGLE_H + 6 + 12 + 16;

        if (tryDrag(mouseX, mouseY, innerX, innerWidth, y, S_POS_X, 1920)) return;
        y += SLIDER_H;

        if (tryDrag(mouseX, mouseY, innerX, innerWidth, y, S_POS_Y, 1080)) return;
        y += SLIDER_H;

        y += 6 + 12 + 16;

        if (tryDrag(mouseX, mouseY, innerX, innerWidth, y, S_SCALE, 200)) return;
        y += SLIDER_H;

        if (tryDrag(mouseX, mouseY, innerX, innerWidth, y, S_MAX_P, 20)) return;
        y += SLIDER_H;

        if (tryDrag(mouseX, mouseY, innerX, innerWidth, y, S_BG_OP, 255)) return;
        y += SLIDER_H;

        if (tryDrag(mouseX, mouseY, innerX, innerWidth, y, S_BORDER, 255)) return;
        y += SLIDER_H;

        y += 6 + 12 + 16 + 20;

        if (hit(mouseX, mouseY, innerX, y, innerWidth, TOGGLE_H)) {
            hudOverlay.setShowHeads(!hudOverlay.getShowHeads());
            return;
        }
        y += TOGGLE_H;

        if (hit(mouseX, mouseY, innerX, y, innerWidth, TOGGLE_H)) {
            hudOverlay.setShowStar(!hudOverlay.getShowStar());
            return;
        }
        y += TOGGLE_H;

        if (hit(mouseX, mouseY, innerX, y, innerWidth, TOGGLE_H)) {
            hudOverlay.setShowLevel(!hudOverlay.getShowLevel());
            return;
        }
        y += TOGGLE_H;

        if (hit(mouseX, mouseY, innerX, y, innerWidth, TOGGLE_H)) {
            hudOverlay.setShowFkdr(!hudOverlay.getShowFkdr());
            return;
        }
        y += TOGGLE_H;

        if (hit(mouseX, mouseY, innerX, y, innerWidth, TOGGLE_H)) {
            hudOverlay.setShowWlr(!hudOverlay.getShowWlr());
            return;
        }
        y += TOGGLE_H;

        if (hit(mouseX, mouseY, innerX, y, innerWidth, TOGGLE_H)) {
            hudOverlay.setShowStreak(!hudOverlay.getShowStreak());
            return;
        }
        y += TOGGLE_H;

        if (hit(mouseX, mouseY, innerX, y, innerWidth, TOGGLE_H)) {
            hudOverlay.setShowUrchin(!hudOverlay.getShowUrchin());
            return;
        }
        y += TOGGLE_H;

        if (hit(mouseX, mouseY, innerX, y, innerWidth, TOGGLE_H)) {
            hudOverlay.setShowThreat(!hudOverlay.getShowThreat());
            return;
        }
        y += TOGGLE_H;

        if (hit(mouseX, mouseY, innerX, y, innerWidth, TOGGLE_H)) {
            hudOverlay.setShowTeamColor(!hudOverlay.getShowTeamColor());
            return;
        }

        y += TOGGLE_H + 8 + 16 + 20;

        if (hit(mouseX, mouseY, innerX, y + 12, innerWidth, 24)) {
            String mode = hudOverlay.getSortMode();

            hudOverlay.setSortMode(
                    mode.equals("threat")
                            ? "fkdr"
                            : mode.equals("fkdr")
                                    ? "star"
                                    : mode.equals("star")
                                            ? "name"
                                            : "threat"
            );
        }

        super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean tryDrag(
            int mouseX,
            int mouseY,
            int innerX,
            int innerWidth,
            int y,
            int id,
            int max
    ) {
        int barX = innerX;
        int barWidth = barW(innerWidth, max);

        if (mouseX >= barX
                && mouseX <= barX + barWidth
                && mouseY >= y + 10
                && mouseY < y + 18) {
            applySlider(id, mouseX, barX, barWidth);

            dragging = true;
            dragId = id;
            dragBarX = barX;
            dragBarW = barWidth;

            return true;
        }

        return false;
    }

    private boolean hit(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x
                && mouseX < x + width
                && mouseY >= y
                && mouseY < y + height;
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        dragging = false;
        dragId = -1;

        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void mouseClickMove(
            int mouseX,
            int mouseY,
            int button,
            long timeSinceLastClick
    ) {
        if (dragging && dragId >= 0) {
            applySlider(dragId, mouseX, dragBarX, dragBarW);
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();

        int scroll = Mouse.getEventDWheel();

        if (scroll != 0) {
            scrollOffset -= scroll > 0 ? 20 : -20;
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void saveAndClose() {
        LobbyIntel lobbyIntel =
                (LobbyIntel) CoralIntel.moduleManager.getModule("LobbyIntel");

        if (lobbyIntel != null) {
            lobbyIntel.saveHudSettings();
        }

        mc.displayGuiScreen(parent);
    }
}
