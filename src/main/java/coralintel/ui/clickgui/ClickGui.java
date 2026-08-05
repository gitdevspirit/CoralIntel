package coralintel.ui.clickgui;

import coralintel.CoralIntel;
import coralintel.module.BooleanSetting;
import coralintel.module.DropdownSetting;
import coralintel.module.KeybindSetting;
import coralintel.module.Module;
import coralintel.module.Setting;
import coralintel.module.SliderSetting;
import coralintel.module.modules.LobbyIntel;
import coralintel.ui.intel.IntelHudOverlay;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * A real click-GUI: one draggable, collapsible panel per registered module,
 * listing every Setting that module has registered (BooleanSetting → toggle,
 * SliderSetting → slider, DropdownSetting → cycle button, KeybindSetting →
 * click-to-rebind). The LobbyIntel panel additionally gets a "HUD OVERLAY"
 * section with the actual overlay controls (position, scale, both opacities,
 * every column toggle, sort mode) — those live as Property objects rather
 * than Settings, so they don't show up from the generic Setting loop and are
 * appended by hand instead.
 *
 * Open with the ClickGUI keybind (see LobbyIntel.clickGuiKeybind / .bind click).
 */
public class ClickGui extends GuiScreen {

    private static final int PANEL_W = 230;
    private static final int HEADER_H = 24;
    private static final int ROW_H = 20;
    private static final int PAD = 8;

    private static final int BG_PANEL = 0xEE0A0A12;
    private static final int BG_HEADER = 0xFF15151D;
    private static final int ACCENT = GuiColors.ACCENT;
    private static final int TEXT_ON = 0xFFDDDDEE;
    private static final int TEXT_DIM = 0xFF888899;

    private final Map<Module, PanelState> panels = new LinkedHashMap<>();
    private SliderRow draggingSlider = null;
    private KeybindSetting listeningKeybind = null;

    private static class PanelState {
        float x, y;
        boolean collapsed = false;
        boolean dragging = false;
        float dragOffX, dragOffY;

        PanelState(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    public ClickGui() {
        int startX = 20;
        int startY = 20;
        for (Module module : CoralIntel.moduleManager.modules.values()) {
            panels.put(module, new PanelState(startX, startY));
            startY += 30; // collapsed headers stack downward until dragged apart
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    // ── Row model — shared across every panel ───────────────────────────────

    private abstract class Row {
        int h = ROW_H;
        abstract void render(int x, int y, int w, int mouseX, int mouseY);
        boolean click(int x, int y, int w, int mouseX, int mouseY) { return false; }
    }

    private class SectionLabelRow extends Row {
        final String text;
        SectionLabelRow(String text) { this.text = text; this.h = 16; }
        void render(int x, int y, int w, int mouseX, int mouseY) {
            mc.fontRendererObj.drawString(text, x, y, TEXT_DIM, false);
        }
    }

    private class ToggleRow extends Row {
        final String label;
        final BooleanSupplier get;
        final Consumer<Boolean> set;

        ToggleRow(String label, BooleanSupplier get, Consumer<Boolean> set) {
            this.label = label;
            this.get = get;
            this.set = set;
        }

        void render(int x, int y, int w, int mouseX, int mouseY) {
            boolean hovered = hit(mouseX, mouseY, x, y, w, h);
            mc.fontRendererObj.drawString(label, x, y + 6, hovered ? TEXT_ON : TEXT_DIM, false);

            boolean value = get.getAsBoolean();
            int boxSize = 12;
            int boxX = x + w - boxSize;
            int boxY = y + 4;

            RoundedUtils.drawRoundedRect(boxX, boxY, boxSize, boxSize, 3,
                    value ? ACCENT : 0x33FFFFFF);
            if (!value) {
                RoundedUtils.drawRoundedOutline(boxX, boxY, boxSize, boxSize, 3, 1f, 0x55FFFFFF);
            }
        }

        boolean click(int x, int y, int w, int mouseX, int mouseY) {
            if (hit(mouseX, mouseY, x, y, w, h)) {
                set.accept(!get.getAsBoolean());
                return true;
            }
            return false;
        }
    }

    private class GenericSliderRow extends Row {
        final SliderSetting setting;
        int barX, barY, barW;

        GenericSliderRow(SliderSetting setting) {
            this.setting = setting;
            this.h = 24;
        }

        void render(int x, int y, int w, int mouseX, int mouseY) {
            double value = setting.getValue();
            mc.fontRendererObj.drawString(setting.getName(), x, y, TEXT_DIM, false);

            String valueText = isWholeStep() ? String.valueOf((int) value) : String.format("%.2f", value);
            mc.fontRendererObj.drawString(valueText, x + w - mc.fontRendererObj.getStringWidth(valueText),
                    y, ACCENT, false);

            barX = x;
            barY = y + 11;
            barW = w;
            int barH = 6;

            drawRect(barX, barY, barX + barW, barY + barH, 0x44444455);
            float pct = setting.getPercent();
            int fillW = Math.max(2, (int) (barW * pct));
            drawRect(barX, barY, barX + fillW, barY + barH, ACCENT);
        }

        boolean click(int x, int y, int w, int mouseX, int mouseY) {
            if (mouseX >= barX && mouseX <= barX + barW && mouseY >= barY - 2 && mouseY < barY + 8) {
                apply(mouseX);
                draggingGenericSlider = this;
                return true;
            }
            return false;
        }

        void apply(int mouseX) {
            float pct = Math.max(0f, Math.min(1f, (mouseX - barX) / (float) barW));
            setting.setValue(setting.getMin() + pct * (setting.getMax() - setting.getMin()));
        }

        private boolean isWholeStep() {
            return setting.getMin() == Math.floor(setting.getMin())
                    && setting.getMax() == Math.floor(setting.getMax())
                    && setting.getValue() == Math.floor(setting.getValue());
        }
    }

    private GenericSliderRow draggingGenericSlider = null;

    private ToggleRow toggle(BooleanSetting setting) {
        return new ToggleRow(setting.getName(), setting::getValue, setting::setValue);
    }

    private class SliderRow extends Row {
        final String label;
        final IntSupplier get;
        final IntConsumer set;
        final int min, max;
        int barX, barY, barW;

        SliderRow(String label, IntSupplier get, IntConsumer set, int min, int max) {
            this.label = label;
            this.get = get;
            this.set = set;
            this.min = min;
            this.max = max;
            this.h = 24;
        }

        void render(int x, int y, int w, int mouseX, int mouseY) {
            int value = get.getAsInt();
            mc.fontRendererObj.drawString(label, x, y, TEXT_DIM, false);

            String valueText = String.valueOf(value);
            mc.fontRendererObj.drawString(valueText, x + w - mc.fontRendererObj.getStringWidth(valueText),
                    y, ACCENT, false);

            barX = x;
            barY = y + 11;
            barW = w;
            int barH = 6;

            drawRect(barX, barY, barX + barW, barY + barH, 0x44444455);
            float pct = (value - min) / (float) (max - min);
            int fillW = Math.max(2, (int) (barW * pct));
            drawRect(barX, barY, barX + fillW, barY + barH, ACCENT);
        }

        boolean click(int x, int y, int w, int mouseX, int mouseY) {
            if (mouseX >= barX && mouseX <= barX + barW && mouseY >= barY - 2 && mouseY < barY + 8) {
                apply(mouseX);
                draggingSlider = this;
                return true;
            }
            return false;
        }

        void apply(int mouseX) {
            float pct = Math.max(0f, Math.min(1f, (mouseX - barX) / (float) barW));
            set.accept(min + Math.round(pct * (max - min)));
        }
    }

    private class DropdownRow extends Row {
        final DropdownSetting setting;

        DropdownRow(DropdownSetting setting) {
            this.setting = setting;
        }

        void render(int x, int y, int w, int mouseX, int mouseY) {
            boolean hovered = hit(mouseX, mouseY, x, y, w, h);
            mc.fontRendererObj.drawString(setting.getName(), x, y + 6, TEXT_DIM, false);

            String value = setting.getValue();
            int valW = mc.fontRendererObj.getStringWidth(value);
            mc.fontRendererObj.drawString(value, x + w - valW, y + 6, hovered ? ACCENT : TEXT_ON, false);
        }

        boolean click(int x, int y, int w, int mouseX, int mouseY) {
            if (hit(mouseX, mouseY, x, y, w, h)) {
                setting.next();
                return true;
            }
            return false;
        }
    }

    private class KeybindRow extends Row {
        final KeybindSetting setting;

        KeybindRow(KeybindSetting setting) {
            this.setting = setting;
        }

        void render(int x, int y, int w, int mouseX, int mouseY) {
            boolean hovered = hit(mouseX, mouseY, x, y, w, h);
            mc.fontRendererObj.drawString(setting.getName(), x, y + 6, TEXT_DIM, false);

            String value = setting.getDisplayName();
            int valW = mc.fontRendererObj.getStringWidth(value);
            int color = setting.isListening() ? ACCENT : (hovered ? TEXT_ON : 0xFFAAAAAA);
            mc.fontRendererObj.drawString(value, x + w - valW, y + 6, color, false);
        }

        boolean click(int x, int y, int w, int mouseX, int mouseY) {
            if (hit(mouseX, mouseY, x, y, w, h)) {
                if (listeningKeybind != null) listeningKeybind.cancelListening();
                setting.startListening();
                listeningKeybind = setting;
                return true;
            }
            return false;
        }
    }

    // ── HUD Overlay extra section (Property-backed, not Settings) ───────────

    private List<Row> hudOverlayRows(LobbyIntel intel) {
        IntelHudOverlay hud = intel.getHudOverlay();
        List<Row> rows = new ArrayList<>();

        rows.add(new SectionLabelRow("HUD OVERLAY"));
        rows.add(new ToggleRow("Enabled", hud::isEnabled, hud::setEnabled));
        rows.add(new SliderRow("X Position", hud::getPosX, v -> hud.setPosition(v, hud.getPosY()), 0, 1920));
        rows.add(new SliderRow("Y Position", hud::getPosY, v -> hud.setPosition(hud.getPosX(), v), 0, 1080));
        rows.add(new SliderRow("Scale %", () -> (int) (hud.getScale() * 100), v -> hud.setScale(v / 100f), 50, 200));
        rows.add(new SliderRow("Max Players", hud::getMaxPlayers, hud::setMaxPlayers, 1, 80));
        rows.add(new SliderRow("Background Opacity", hud::getBgOpacity, hud::setBgOpacity, 0, 255));
        rows.add(new SliderRow("Border Opacity", hud::getBorderOpacity, hud::setBorderOpacity, 0, 255));
        rows.add(new SliderRow("Column Line Opacity", hud::getColumnLineOpacity, hud::setColumnLineOpacity, 0, 255));
        rows.add(new ToggleRow("Player Heads", hud::getShowHeads, hud::setShowHeads));
        rows.add(new ToggleRow("Star", hud::getShowStar, hud::setShowStar));
        rows.add(new ToggleRow("Network Level", hud::getShowLevel, hud::setShowLevel));
        rows.add(new ToggleRow("FKDR", hud::getShowFkdr, hud::setShowFkdr));
        rows.add(new ToggleRow("WLR", hud::getShowWlr, hud::setShowWlr));
        rows.add(new ToggleRow("Winstreak", hud::getShowStreak, hud::setShowStreak));
        rows.add(new ToggleRow("Tags", hud::getShowUrchin, hud::setShowUrchin));
        rows.add(new ToggleRow("Threat Score", hud::getShowThreat, hud::setShowThreat));
        rows.add(new ToggleRow("Team Colors", hud::getShowTeamColor, hud::setShowTeamColor));

        return rows;
    }

    private List<Row> buildRows(Module module) {
        List<Row> rows = new ArrayList<>();

        for (Setting setting : module.getSettings()) {
            if (!setting.isVisible()) continue;

            if (setting instanceof BooleanSetting) {
                rows.add(toggle((BooleanSetting) setting));
            } else if (setting instanceof SliderSetting) {
                rows.add(new GenericSliderRow((SliderSetting) setting));
            } else if (setting instanceof DropdownSetting) {
                rows.add(new DropdownRow((DropdownSetting) setting));
            } else if (setting instanceof KeybindSetting) {
                rows.add(new KeybindRow((KeybindSetting) setting));
            }
        }

        if (module instanceof LobbyIntel) {
            rows.addAll(hudOverlayRows((LobbyIntel) module));
        }

        return rows;
    }

    // ── Rendering ────────────────────────────────────────────────────────────

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawRect(0, 0, width, height, 0x88000000);

        for (Map.Entry<Module, PanelState> entry : panels.entrySet()) {
            drawPanel(entry.getKey(), entry.getValue(), mouseX, mouseY);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawPanel(Module module, PanelState state, int mouseX, int mouseY) {
        int x = (int) state.x;
        int y = (int) state.y;

        List<Row> rows = state.collapsed ? new ArrayList<>() : buildRows(module);
        int contentHeight = 0;
        for (Row r : rows) contentHeight += r.h + 4;

        int panelH = HEADER_H + (state.collapsed ? 0 : contentHeight + PAD);

        RoundedUtils.drawRoundedRect(x, y, PANEL_W, panelH, 5, BG_PANEL);
        RoundedUtils.drawRoundedRect(x, y, PANEL_W, HEADER_H, 5, BG_HEADER);

        boolean masterOn = module.isEnabled();
        mc.fontRendererObj.drawString(module.getName(), x + PAD, y + 8,
                masterOn ? ACCENT : TEXT_DIM, false);

        String arrow = state.collapsed ? "\u25B6" : "\u25BC";
        mc.fontRendererObj.drawString(arrow, x + PANEL_W - 16, y + 8, TEXT_DIM, false);

        if (state.collapsed) return;

        int rx = x + PAD;
        int rw = PANEL_W - PAD * 2;
        int ry = y + HEADER_H + 6;

        for (Row row : rows) {
            row.render(rx, ry, rw, mouseX, mouseY);
            ry += row.h + 4;
        }
    }

    // ── Input ────────────────────────────────────────────────────────────────

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        if (button == 0) {
            for (Map.Entry<Module, PanelState> entry : panels.entrySet()) {
                PanelState state = entry.getValue();
                int x = (int) state.x;
                int y = (int) state.y;

                if (hit(mouseX, mouseY, x, y, PANEL_W, HEADER_H)) {
                    // Right-most 16px of the header toggles collapse; the rest drags the panel.
                    if (mouseX >= x + PANEL_W - 20) {
                        state.collapsed = !state.collapsed;
                    } else {
                        state.dragging = true;
                        state.dragOffX = mouseX - state.x;
                        state.dragOffY = mouseY - state.y;
                    }
                    return;
                }

                if (state.collapsed) continue;

                List<Row> rows = buildRows(entry.getKey());
                int rx = x + PAD;
                int rw = PANEL_W - PAD * 2;
                int ry = y + HEADER_H + 6;

                for (Row row : rows) {
                    if (row.click(rx, ry, rw, mouseX, mouseY)) {
                        return;
                    }
                    ry += row.h + 4;
                }
            }
        }

        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        draggingSlider = null;
        draggingGenericSlider = null;
        for (PanelState p : panels.values()) p.dragging = false;
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long timeSinceLastClick) {
        if (draggingSlider != null) {
            draggingSlider.apply(mouseX);
            return;
        }
        if (draggingGenericSlider != null) {
            draggingGenericSlider.apply(mouseX);
            return;
        }
        for (PanelState state : panels.values()) {
            if (state.dragging) {
                state.x = mouseX - state.dragOffX;
                state.y = mouseY - state.dragOffY;
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (listeningKeybind != null) {
            // ESC cancels the rebind instead of closing the GUI.
            int resolved = keyCode == 1 ? 0 : keyCode;
            listeningKeybind.setKeyCode(resolved);
            listeningKeybind = null;
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    private boolean hit(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }
}
