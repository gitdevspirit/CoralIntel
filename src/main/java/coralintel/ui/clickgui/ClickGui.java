package coralintel.ui.clickgui;

import coralintel.CoralIntel;
import coralintel.module.BooleanSetting;
import coralintel.module.DropdownSetting;
import coralintel.module.KeybindSetting;
import coralintel.module.Module;
import coralintel.module.Setting;
import coralintel.module.SliderSetting;
import coralintel.module.modules.LobbyIntel;
import coralintel.module.modules.PregameMessages;
import coralintel.module.modules.SessionStats;
import coralintel.module.modules.SnipeMessages;
import coralintel.module.modules.StreamerMode;
import coralintel.property.properties.TextProperty;
import coralintel.command.CommandManager;
import coralintel.config.Config;
import coralintel.util.ChatUtil;
import net.minecraft.util.ChatAllowedCharacters;
import coralintel.ui.intel.IntelHudOverlay;
import coralintel.ui.intel.IntelManager;
import coralintel.ui.intel.ReminderManager;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

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

    private final Map<Object, PanelState> panels = new LinkedHashMap<>();
    private static final String BLACKLIST_SAFELIST = "Blacklist/Safelist";
    // One "Chat" panel holds both message modules as sub-sections.
    private static final String CHAT = "Chat";
    private static final String REMINDERS = "Reminders";
    private boolean pregameOpen = true;
    private boolean snipeOpen = true;
    private SliderRow draggingSlider = null;
    // Ctrl+click / Ctrl+drag in the ClickGUI moves the session HUD.
    private boolean movingHud = false;
    private KeybindSetting listeningKeybind = null;

    private static class PanelState {
        float x, y;
        boolean collapsed = true;
        boolean dragging = false;
        float dragOffX, dragOffY;
        int scrollOffset = 0;
        int maxScroll = 0;

        PanelState(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    public ClickGui() {
        // Side-by-side columns rather than a vertical stack — that way,
        // expanding one panel can never overlap the next one regardless of
        // how tall its settings list turns out to be. Panels start collapsed
        // so opening the GUI shows a clean row of headers; click one to
        // expand it, drag by the header to reposition.
        int startX = 20;
        int startY = 20;
        int columnGap = PANEL_W + 20;

        // Streamer Mode and Session Stats get their own second row, just under the
        // header row, so the main row of panels keeps its layout. Both rows are
        // draggable like any panel.
        int secondRowY = startY + HEADER_H + 16;
        int secondRowX = startX;

        for (Module module : CoralIntel.moduleManager.modules.values()) {
            if (module instanceof StreamerMode || module instanceof SessionStats) {
                panels.put(module, new PanelState(secondRowX, secondRowY));
                secondRowX += columnGap;
                continue;
            }

            // Pregame + snipe messages share one "Chat" panel (built in chatRows()).
            if (module instanceof PregameMessages) {
                panels.put(CHAT, new PanelState(startX, startY));
                startX += columnGap;
                continue;
            }
            if (module instanceof SnipeMessages) {
                continue;
            }

            panels.put(module, new PanelState(startX, startY));
            startX += columnGap;
        }

        panels.put(BLACKLIST_SAFELIST, new PanelState(startX, startY));
        panels.put(REMINDERS, new PanelState(secondRowX, secondRowY));
    }

    @Override
    public void onGuiClosed() {
        commitEdit(); // don't lose a half-finished edit when the GUI closes
        super.onGuiClosed();
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

    /** Clickable sub-section title inside a panel (used by the Chat panel). */
    private class SubHeaderRow extends Row {
        final String title;
        final BooleanSupplier open;
        final Runnable toggle;

        SubHeaderRow(String title, BooleanSupplier open, Runnable toggle) {
            this.title = title;
            this.open = open;
            this.toggle = toggle;
            this.h = 16;
        }

        void render(int x, int y, int w, int mouseX, int mouseY) {
            boolean hovered = hit(mouseX, mouseY, x, y, w, h);
            RoundedUtils.drawRoundedRect(x, y, w, h, 3, hovered ? 0x33FFFFFF : 0x1AFFFFFF);
            mc.fontRendererObj.drawString(title, x + 5, y + 4, ACCENT, false);
            mc.fontRendererObj.drawString(open.getAsBoolean() ? "\u25BC" : "\u25B6",
                    x + w - 12, y + 4, TEXT_DIM, false);
        }

        boolean click(int x, int y, int w, int mouseX, int mouseY) {
            if (hit(mouseX, mouseY, x, y, w, h)) {
                toggle.run();
                return true;
            }
            return false;
        }
    }

    /** One reminder — click the x to drop it (after you've tagged them in Coral). */
    private class ReminderRow extends Row {
        final ReminderManager.Reminder reminder;
        int removeX, removeY;

        ReminderRow(ReminderManager.Reminder reminder) {
            this.reminder = reminder;
            this.h = 22;
        }

        void render(int x, int y, int w, int mouseX, int mouseY) {
            removeX = x + w - 12;
            removeY = y;

            boolean removeHovered = hit(mouseX, mouseY, removeX - 2, removeY, 14, 20);

            mc.fontRendererObj.drawString(reminder.name, x, y, 0xFFFFCC55, false);
            if (reminder.seen > 1) {
                mc.fontRendererObj.drawString("x" + reminder.seen,
                        x + mc.fontRendererObj.getStringWidth(reminder.name) + 5, y, TEXT_DIM, false);
            }
            mc.fontRendererObj.drawString("\u2715", removeX, removeY,
                    removeHovered ? 0xFFFF5555 : TEXT_DIM, false);

            String note = reminder.latestNote();
            String shown = note.isEmpty() ? "(no note)" : note;
            int maxW = w - 16;
            while (shown.length() > 3 && mc.fontRendererObj.getStringWidth(shown + "\u2026") > maxW) {
                shown = shown.substring(0, shown.length() - 1);
            }
            if (!shown.equals(note) && !note.isEmpty()) shown += "\u2026";

            mc.fontRendererObj.drawString(shown, x, y + 10, TEXT_DIM, false);
        }

        boolean click(int x, int y, int w, int mouseX, int mouseY) {
            if (hit(mouseX, mouseY, removeX - 2, removeY, 14, 20)) {
                ReminderManager.getInstance().remove(reminder.name);
                return true;
            }
            return false;
        }
    }

    /** Read-only row showing one recent cheater-flag notification. */
    private class FlagLogRow extends Row {
        final IntelManager.FlagRecord flag;

        FlagLogRow(IntelManager.FlagRecord flag) {
            this.flag = flag;
            this.h = 22;
        }

        void render(int x, int y, int w, int mouseX, int mouseY) {
            mc.fontRendererObj.drawString(flag.name, x, y, TEXT_ON, false);

            String message = flag.message;
            while (message.length() > 3 && mc.fontRendererObj.getStringWidth(message + "\u2026") > w) {
                message = message.substring(0, message.length() - 1);
            }
            if (!message.equals(flag.message)) message += "\u2026";

            mc.fontRendererObj.drawString(message, x, y + 10, flag.color, false);
        }
    }

    /** One entry in the safelist section — click the × to remove. */
    private class SafelistRow extends Row {
        final String name;
        final String reason;
        int removeX, removeY;

        SafelistRow(String name, String reason) {
            this.name = name;
            this.reason = reason;
            this.h = 22;
        }

        void render(int x, int y, int w, int mouseX, int mouseY) {
            removeX = x + w - 12;
            removeY = y;

            boolean removeHovered = hit(mouseX, mouseY, removeX - 2, removeY, 14, 20);

            mc.fontRendererObj.drawString(name, x, y, TEXT_ON, false);
            mc.fontRendererObj.drawString("\u2715", removeX, removeY,
                    removeHovered ? 0xFFFF5555 : TEXT_DIM, false);

            String shown = reason;
            int maxW = w - 16;
            while (shown.length() > 3 && mc.fontRendererObj.getStringWidth(shown + "\u2026") > maxW) {
                shown = shown.substring(0, shown.length() - 1);
            }
            if (!shown.equals(reason)) shown += "\u2026";

            mc.fontRendererObj.drawString(shown, x, y + 10, TEXT_DIM, false);
        }

        boolean click(int x, int y, int w, int mouseX, int mouseY) {
            if (hit(mouseX, mouseY, removeX - 2, removeY, 14, 20)) {
                coralintel.ui.intel.SafelistManager.getInstance().unsafelist(name);

                coralintel.ui.intel.IntelPlayer live = IntelManager.getInstance().getPlayer(name);
                if (live != null) {
                    live.safelisted = false;
                    live.safelistReason = null;
                    live.computeThreat();
                }
                return true;
            }
            return false;
        }
    }

    /** One entry in the blacklist section — click the × to remove. */
    private class BlacklistRow extends Row {
        final String name;
        final String reason;
        int removeX, removeY;

        BlacklistRow(String name, String reason) {
            this.name = name;
            this.reason = reason;
            this.h = 22;
        }

        void render(int x, int y, int w, int mouseX, int mouseY) {
            removeX = x + w - 12;
            removeY = y;

            boolean removeHovered = hit(mouseX, mouseY, removeX - 2, removeY, 14, 20);

            mc.fontRendererObj.drawString(name, x, y, 0xFF6699FF, false);
            mc.fontRendererObj.drawString("\u2715", removeX, removeY,
                    removeHovered ? 0xFFFF5555 : TEXT_DIM, false);

            String shown = reason;
            int maxW = w - 16;
            while (shown.length() > 3 && mc.fontRendererObj.getStringWidth(shown + "\u2026") > maxW) {
                shown = shown.substring(0, shown.length() - 1);
            }
            if (!shown.equals(reason)) shown += "\u2026";

            mc.fontRendererObj.drawString(shown, x, y + 10, TEXT_DIM, false);
        }

        boolean click(int x, int y, int w, int mouseX, int mouseY) {
            if (hit(mouseX, mouseY, removeX - 2, removeY, 14, 20)) {
                coralintel.ui.intel.BlacklistManager.getInstance().unblacklist(name);

                coralintel.ui.intel.IntelPlayer live = IntelManager.getInstance().getPlayer(name);
                if (live != null) {
                    live.blacklisted = false;
                    live.blacklistReason = null;
                    live.computeThreat();
                }
                return true;
            }
            return false;
        }
    }

    // ── Editable text rows (pregame messages) ───────────────────────────────
    // Rows are rebuilt every frame, so the in-progress edit lives here on the
    // screen, not on the row. Click a box to edit; Enter or clicking away
    // saves (and writes the config file); Esc cancels.

    private TextProperty editingText = null;
    private final StringBuilder editBuffer = new StringBuilder();

    private void startEditing(TextProperty property) {
        commitEdit();
        editingText = property;
        editBuffer.setLength(0);
        String current = property.getValue();
        editBuffer.append(current == null ? "" : current);
    }

    private void commitEdit() {
        if (editingText == null) return;

        TextProperty property = editingText;
        editingText = null;
        String text = editBuffer.toString().trim();

        // A leading "." would be run as a client command instead of sent.
        if (text.startsWith(CommandManager.PREFIX)) {
            ChatUtil.sendFormatted("&cThis text can't start with \".\" \u2014 it would be run as a command.");
            return;
        }

        if (text.equals(property.getValue())) return;

        property.setValue(text);
        String name = Config.lastConfig != null ? Config.lastConfig : "default";
        new Config(name, false).save();
    }

    private class TextRow extends Row {
        final String label;
        final TextProperty property;

        TextRow(String label, TextProperty property) {
            this.label = label;
            this.property = property;
            this.h = 34;
        }

        void render(int x, int y, int w, int mouseX, int mouseY) {
            boolean editing = editingText == property;
            int boxY = y + 11;
            int boxH = 18;
            boolean hovered = hit(mouseX, mouseY, x, boxY, w, boxH);

            mc.fontRendererObj.drawString(label, x, y, editing ? ACCENT : TEXT_DIM, false);

            RoundedUtils.drawRoundedRect(x, boxY, w, boxH, 3, hovered || editing ? 0x33FFFFFF : 0x1AFFFFFF);
            RoundedUtils.drawRoundedOutline(x, boxY, w, boxH, 3, 1f,
                    editing ? ACCENT : (hovered ? 0x88FFFFFF : 0x55FFFFFF));

            int maxW = w - 10;
            String shown;
            int color;

            if (editing) {
                // Keep the END of the text visible while typing, plus a blinking cursor.
                shown = editBuffer.toString();
                while (shown.length() > 0 && mc.fontRendererObj.getStringWidth(shown + "_") > maxW) {
                    shown = shown.substring(1);
                }
                shown += (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "";
                color = TEXT_ON;
            } else {
                String value = property.getValue() == null ? "" : property.getValue();
                if (value.isEmpty()) {
                    shown = "(empty \u2014 skipped)";
                    color = TEXT_DIM;
                } else {
                    shown = value;
                    while (shown.length() > 3 && mc.fontRendererObj.getStringWidth(shown + "\u2026") > maxW) {
                        shown = shown.substring(0, shown.length() - 1);
                    }
                    if (!shown.equals(value)) shown += "\u2026";
                    color = TEXT_ON;
                }
            }

            mc.fontRendererObj.drawString(shown, x + 5, boxY + 5, color, false);
        }

        boolean click(int x, int y, int w, int mouseX, int mouseY) {
            if (hit(mouseX, mouseY, x, y + 11, w, 18)) {
                startEditing(property);
                return true;
            }
            return false;
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
            boolean value = get.getAsBoolean();

            // Label brightens when the setting is ON, not just on hover —
            // gives an at-a-glance read of every row's state, not just the
            // one under the cursor.
            int labelColor = value ? TEXT_ON : (hovered ? TEXT_ON : TEXT_DIM);
            mc.fontRendererObj.drawString(label, x, y + 6, labelColor, false);

            int boxSize = 14;
            int boxX = x + w - boxSize;
            int boxY = y + 3;

            if (value) {
                // Dark box with a pink arrow — the "on" indicator.
                RoundedUtils.drawRoundedRect(boxX, boxY, boxSize, boxSize, 3, 0xFF1A1A22);
                RoundedUtils.drawRoundedOutline(boxX, boxY, boxSize, boxSize, 3, 1f, ACCENT);
                mc.fontRendererObj.drawString("\u25B6", boxX + 3, boxY + 3, ACCENT, false);
            } else {
                RoundedUtils.drawRoundedRect(boxX, boxY, boxSize, boxSize, 3,
                        hovered ? 0x33FFFFFF : 0x1AFFFFFF);
                RoundedUtils.drawRoundedOutline(boxX, boxY, boxSize, boxSize, 3, 1f,
                        hovered ? 0x88FFFFFF : 0x55FFFFFF);
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
            // Computed directly from the passed-in geometry rather than relying
            // on fields render() sets — mouseClicked() rebuilds a fresh Row list
            // that's never actually rendered, so those fields would still be 0.
            barX = x;
            barY = y + 11;
            barW = w;

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
            barX = x;
            barY = y + 11;
            barW = w;

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
        rows.add(new SliderRow("Column Width %", hud::getColumnWidthPercent, hud::setColumnWidthPercent, 100, 200));
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

    /** Rows for every visible Setting a module registered. */
    private void addSettingRows(List<Row> rows, Module module) {
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
    }

    /** The Chat panel: Pregame Messages and Snipe Messages as two collapsible sub-sections. */
    private List<Row> chatRows() {
        List<Row> rows = new ArrayList<>();

        Module pgmModule = CoralIntel.moduleManager.getModule(PregameMessages.class);
        if (pgmModule instanceof PregameMessages) {
            PregameMessages pgm = (PregameMessages) pgmModule;
            rows.add(new SubHeaderRow("Pregame Messages", () -> pregameOpen, () -> pregameOpen = !pregameOpen));
            if (pregameOpen) {
                rows.add(new ToggleRow("Enabled", pgm::isEnabled, pgm::setEnabled));
                addSettingRows(rows, pgm);
                rows.add(new SectionLabelRow("Sent at 10s, spaced by Message Delay"));
                rows.add(new TextRow("Message 1 (.pgm1)", pgm.message1));
                rows.add(new TextRow("Message 2 (.pgm2)", pgm.message2));
                rows.add(new TextRow("Message 3 (.pgm3)", pgm.message3));
            }
        }

        Module snipeModule = CoralIntel.moduleManager.getModule(SnipeMessages.class);
        if (snipeModule instanceof SnipeMessages) {
            SnipeMessages snipe = (SnipeMessages) snipeModule;
            rows.add(new SubHeaderRow("Snipe Messages", () -> snipeOpen, () -> snipeOpen = !snipeOpen));
            if (snipeOpen) {
                rows.add(new ToggleRow("Enabled", snipe::isEnabled, snipe::setEnabled));
                addSettingRows(rows, snipe);
                rows.add(new SectionLabelRow("Type .sm to send it (via /shout)"));
                rows.add(new TextRow("Snipe Message (.sm1)", snipe.message1));
            }
        }

        rows.add(new SectionLabelRow("Click a box, type, Enter to save"));
        return rows;
    }

    private List<Row> reminderRows() {
        List<Row> rows = new ArrayList<>();
        List<ReminderManager.Reminder> all = ReminderManager.getInstance().getAll();

        rows.add(new SectionLabelRow("REMINDERS (" + all.size() + ")"));

        if (all.isEmpty()) {
            rows.add(new SectionLabelRow("Empty \u2014 .remind <player> [note]"));
        } else {
            for (ReminderManager.Reminder r : all) {
                rows.add(new ReminderRow(r));
            }
            rows.add(new SectionLabelRow("Tag them in Coral, then click x"));
        }

        return rows;
    }

    private List<Row> buildRows(Object panelKey) {
        if (panelKey == BLACKLIST_SAFELIST) {
            List<Row> rows = new ArrayList<>();
            rows.addAll(blacklistRows());
            rows.addAll(safelistRows());
            return rows;
        }

        if (panelKey == CHAT) return chatRows();
        if (panelKey == REMINDERS) return reminderRows();

        Module module = (Module) panelKey;
        List<Row> rows = new ArrayList<>();

        addSettingRows(rows, module);

        if (module instanceof LobbyIntel) {
            rows.addAll(hudOverlayRows((LobbyIntel) module));
            rows.addAll(notificationRows());
        }

        if (module instanceof StreamerMode) {
            StreamerMode streamer = (StreamerMode) module;
            rows.add(new SectionLabelRow("YOUR NAMETAG (used when Own Nametag = Custom)"));
            rows.add(new TextRow("Custom Nametag Text", streamer.nametagText));
            rows.add(new SectionLabelRow("Click a box, type, Enter to save"));
        }

        if (module instanceof SessionStats) {
            rows.add(new SectionLabelRow("THIS SESSION"));
            for (String line : ((SessionStats) module).guiLines()) {
                rows.add(new SectionLabelRow(line));
            }
            rows.add(new SectionLabelRow(".reset restarts it, .session prints it"));
            rows.add(new SectionLabelRow("Ctrl+click here to place the HUD"));
            rows.add(new SectionLabelRow("(or drag it in your inventory)"));
        }

        return rows;
    }

    private List<Row> notificationRows() {
        List<Row> rows = new ArrayList<>();
        List<IntelManager.FlagRecord> flags = IntelManager.getInstance().getRecentFlags();

        rows.add(new SectionLabelRow("RECENT FLAGS"));

        if (flags.isEmpty()) {
            rows.add(new SectionLabelRow("None yet this session"));
        } else {
            for (IntelManager.FlagRecord flag : flags) {
                rows.add(new FlagLogRow(flag));
            }
        }

        return rows;
    }

    private List<Row> blacklistRows() {
        List<Row> rows = new ArrayList<>();
        java.util.Map<String, String> entries = coralintel.ui.intel.BlacklistManager.getInstance().getAll();

        rows.add(new SectionLabelRow("BLACKLIST (" + entries.size() + ")"));

        if (entries.isEmpty()) {
            rows.add(new SectionLabelRow("Empty — .blacklist <player> to add"));
        } else {
            for (java.util.Map.Entry<String, String> entry : entries.entrySet()) {
                rows.add(new BlacklistRow(entry.getKey(), entry.getValue()));
            }
        }

        return rows;
    }

    private List<Row> safelistRows() {
        List<Row> rows = new ArrayList<>();
        java.util.Map<String, String> entries = coralintel.ui.intel.SafelistManager.getInstance().getAll();

        rows.add(new SectionLabelRow("SAFELIST (" + entries.size() + ")"));

        if (entries.isEmpty()) {
            rows.add(new SectionLabelRow("Empty — .safelist <player> to add"));
        } else {
            for (java.util.Map.Entry<String, String> entry : entries.entrySet()) {
                rows.add(new SafelistRow(entry.getKey(), entry.getValue()));
            }
        }

        return rows;
    }

    // ── Rendering ────────────────────────────────────────────────────────────

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawRect(0, 0, width, height, 0x88000000);

        for (Map.Entry<Object, PanelState> entry : panels.entrySet()) {
            drawPanel(entry.getKey(), entry.getValue(), mouseX, mouseY);
        }

        // The session HUD is shown here too, on top of the panels, so it can be placed by eye.
        SessionStats session = sessionStats();
        if (session != null) {
            session.setPlacing(isCtrlKeyDown());
            session.drawHud(false, mouseX, mouseY);
            session.setPlacing(false);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private static SessionStats sessionStats() {
        Module module = CoralIntel.moduleManager.getModule(SessionStats.class);
        return module instanceof SessionStats ? (SessionStats) module : null;
    }

    private static final int MAX_VISIBLE_CONTENT = 400; // caps a panel's height before it scrolls

    private void drawPanel(Object panelKey, PanelState state, int mouseX, int mouseY) {
        int x = (int) state.x;
        int y = (int) state.y;

        List<Row> rows = state.collapsed ? new ArrayList<>() : buildRows(panelKey);
        int contentHeight = 0;
        for (Row r : rows) contentHeight += r.h + 4;

        int visibleContentHeight = Math.min(contentHeight, MAX_VISIBLE_CONTENT);
        state.maxScroll = Math.max(0, contentHeight - visibleContentHeight);
        state.scrollOffset = Math.max(0, Math.min(state.scrollOffset, state.maxScroll));

        int panelH = HEADER_H + (state.collapsed ? 0 : visibleContentHeight + PAD);

        RoundedUtils.drawRoundedRect(x, y, PANEL_W, panelH, 5, BG_PANEL);
        RoundedUtils.drawRoundedRect(x, y, PANEL_W, HEADER_H, 5, BG_HEADER);

        boolean isModule = panelKey instanceof Module;
        String panelName = isModule ? ((Module) panelKey).getName() : (String) panelKey;
        boolean masterOn = isModule && ((Module) panelKey).isEnabled();

        int nameX = x + PAD;

        // The on/off dot only makes sense for an actual toggleable module —
        // Blacklist/Safelist is just a data manager, always "active".
        if (isModule) {
            int dotSize = 8;
            int dotX = x + PAD;
            int dotY = y + (HEADER_H - dotSize) / 2;
            RoundedUtils.drawRoundedRect(dotX, dotY, dotSize, dotSize, 2, masterOn ? ACCENT : 0x33FFFFFF);
            if (!masterOn) {
                RoundedUtils.drawRoundedOutline(dotX, dotY, dotSize, dotSize, 2, 1f, 0x55FFFFFF);
            }
            nameX = dotX + dotSize + 6;
        }

        mc.fontRendererObj.drawString(panelName, nameX, y + 8,
                !isModule ? ACCENT : (masterOn ? ACCENT : TEXT_DIM), false);

        String arrow = state.collapsed ? "\u25B6" : "\u25BC";
        mc.fontRendererObj.drawString(arrow, x + PANEL_W - 16, y + 8, TEXT_DIM, false);

        if (state.collapsed) return;

        int rx = x + PAD;
        int rw = PANEL_W - PAD * 2;
        int contentTop = y + HEADER_H + 6;

        ScaledResolution sr = new ScaledResolution(mc);
        int scaleFactor = sr.getScaleFactor();
        int scaledHeight = sr.getScaledHeight();

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(
                x * scaleFactor,
                (scaledHeight - contentTop - visibleContentHeight) * scaleFactor,
                PANEL_W * scaleFactor,
                visibleContentHeight * scaleFactor
        );

        int ry = contentTop - state.scrollOffset;

        for (Row row : rows) {
            if (ry + row.h >= contentTop && ry <= contentTop + visibleContentHeight) {
                row.render(rx, ry, rw, mouseX, mouseY);
            }
            ry += row.h + 4;
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        // Scroll indicator
        if (state.maxScroll > 0) {
            int trackH = visibleContentHeight;
            int thumbH = Math.max(16, trackH * visibleContentHeight / contentHeight);
            int thumbY = contentTop + (int) ((float) state.scrollOffset / state.maxScroll * (trackH - thumbH));
            drawRect(x + PANEL_W - 4, contentTop, x + PANEL_W - 2, contentTop + trackH, 0x22FFFFFF);
            drawRect(x + PANEL_W - 4, thumbY, x + PANEL_W - 2, thumbY + thumbH, ACCENT);
        }
    }

    // ── Input ────────────────────────────────────────────────────────────────

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        // Clicking anywhere saves an in-progress text edit; clicking a text
        // box then re-opens it for editing via TextRow.click().
        commitEdit();

        // Ctrl+click anywhere puts the session HUD there (and drag keeps moving it).
        if (button == 0 && isCtrlKeyDown()) {
            SessionStats session = sessionStats();
            if (session != null && session.beginMove(mouseX, mouseY)) {
                movingHud = true;
                return;
            }
        }

        if (button == 0) {
            for (Map.Entry<Object, PanelState> entry : panels.entrySet()) {
                PanelState state = entry.getValue();
                int x = (int) state.x;
                int y = (int) state.y;

                if (hit(mouseX, mouseY, x, y, PANEL_W, HEADER_H)) {
                    // Left ~20px (the on/off dot) toggles the module itself
                    // — only applies to actual modules, not Blacklist/Safelist.
                    // Right-most 16px (the arrow) toggles collapse. Anything
                    // else in the header drags the panel.
                    if (entry.getKey() instanceof Module && mouseX < x + PAD + 8 + 6) {
                        ((Module) entry.getKey()).toggle();
                    } else if (mouseX >= x + PANEL_W - 20) {
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
                int contentTop = y + HEADER_H + 6;
                int visibleContentHeight = Math.min(sumHeight(rows), MAX_VISIBLE_CONTENT);
                int ry = contentTop - state.scrollOffset;

                for (Row row : rows) {
                    // Only rows actually visible within the scrolled/clipped
                    // viewport are clickable — otherwise a row scrolled out
                    // of view could still eat a click meant for the panel below it.
                    boolean visible = ry + row.h >= contentTop && ry <= contentTop + visibleContentHeight;
                    if (visible && row.click(rx, ry, rw, mouseX, mouseY)) {
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
        if (movingHud) {
            movingHud = false;
            SessionStats session = sessionStats();
            if (session != null) session.endMove();
        }
        draggingSlider = null;
        draggingGenericSlider = null;
        for (PanelState p : panels.values()) p.dragging = false;
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long timeSinceLastClick) {
        if (movingHud) {
            SessionStats session = sessionStats();
            if (session != null) session.moveTo(mouseX, mouseY);
            return;
        }
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
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();

        int scroll = Mouse.getEventDWheel();
        if (scroll == 0) return;

        // Scroll whichever expanded panel the cursor is currently over.
        int scaledMouseX = Mouse.getEventX() * width / mc.displayWidth;
        int scaledMouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;

        for (Map.Entry<Object, PanelState> entry : panels.entrySet()) {
            PanelState state = entry.getValue();
            if (state.collapsed) continue;

            int x = (int) state.x;
            int y = (int) state.y;
            int visibleContentHeight = Math.min(sumHeight(buildRows(entry.getKey())), MAX_VISIBLE_CONTENT);
            int panelBottom = y + HEADER_H + visibleContentHeight + PAD;

            if (scaledMouseX >= x && scaledMouseX < x + PANEL_W && scaledMouseY >= y && scaledMouseY < panelBottom) {
                state.scrollOffset -= scroll > 0 ? 20 : -20;
                state.scrollOffset = Math.max(0, Math.min(state.scrollOffset, state.maxScroll));
                break;
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (editingText != null) {
            if (keyCode == 1) {                      // Esc: cancel the edit, keep the GUI open
                editingText = null;
            } else if (keyCode == 28 || keyCode == 156) { // Enter / numpad Enter: save
                commitEdit();
            } else if (keyCode == 14) {              // Backspace
                if (editBuffer.length() > 0) editBuffer.deleteCharAt(editBuffer.length() - 1);
            } else if (isKeyComboCtrlV(keyCode)) {   // Ctrl+V
                String clip = ChatAllowedCharacters.filterAllowedCharacters(getClipboardString());
                int room = PregameMessages.MAX_LENGTH - editBuffer.length();
                if (room > 0) editBuffer.append(clip, 0, Math.min(room, clip.length()));
            } else if (ChatAllowedCharacters.isAllowedCharacter(typedChar)
                    && editBuffer.length() < PregameMessages.MAX_LENGTH) {
                editBuffer.append(typedChar);
            }
            return;
        }

        if (listeningKeybind != null) {
            // ESC cancels the rebind instead of closing the GUI.
            if (keyCode == 1) listeningKeybind.cancelListening();
            else listeningKeybind.setKeyCode(keyCode);
            listeningKeybind = null;
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    private int sumHeight(List<Row> rows) {
        int total = 0;
        for (Row r : rows) total += r.h + 4;
        return total;
    }

    private boolean hit(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }
}
