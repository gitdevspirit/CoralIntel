package coralintel.ui.clickgui;

import coralintel.CoralIntel;
import coralintel.module.BooleanSetting;
import coralintel.module.DropdownSetting;
import coralintel.module.KeybindSetting;
import coralintel.module.Module;
import coralintel.module.Setting;
import coralintel.module.SliderSetting;
import coralintel.module.TextSetting;
import coralintel.module.modules.LobbyIntel;
import coralintel.module.modules.PregameMessages;
import coralintel.module.modules.SessionStats;
import coralintel.module.modules.SnipeMessages;
import coralintel.module.modules.StreamerMode;
import coralintel.module.modules.PartyDetector;
import coralintel.property.properties.TextProperty;
import coralintel.command.CommandManager;
import coralintel.config.Config;
import coralintel.util.ChatUtil;
import net.minecraft.util.ChatAllowedCharacters;
import coralintel.ui.intel.IntelHudOverlay;
import coralintel.ui.intel.IntelManager;
import coralintel.ui.intel.ReminderManager;
import coralintel.script.ScriptInstance;
import coralintel.script.ScriptManager;
import coralintel.script.ScriptModule;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
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
    private static final String SCRIPTS = "Scripts";
    private static final String CONFIGS = "Configs";
    private final Object newConfigKey = new Object(); // identity of the "new config name" box
    private static final int PANEL_X0 = 20;
    // The third row holds Configs, the Scripts panel, and one panel per module a script registered;
    // and are synced every frame, so reloading or disabling a script removes its panels live.
    private java.util.List<PanelState> firstRowPanels = new ArrayList<>();
    private java.util.List<PanelState> secondRowPanels = new ArrayList<>();
    private final int scriptFallbackY = 20 + HEADER_H + 16 + HEADER_H + 16;
    private final Map<String, PanelState> scriptPanelMemory = new HashMap<>();
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
        /** Height drawn last frame (header + settings), used to stack the second row under the first. */
        int lastHeight = HEADER_H;
        /** Second-row panels follow the first-row panel above them until the user drags them. */
        PanelState above;
        boolean userMoved;
        /** Scrollbar drag: geometry captured when the bar was grabbed. */
        boolean draggingBar;
        int barGrab, barTrackH, barThumbH, barTop;

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

        java.util.List<PanelState> firstRow = new ArrayList<>();
        java.util.List<PanelState> secondRow = new ArrayList<>();

        for (Module module : CoralIntel.moduleManager.modules.values()) {
            if (module instanceof StreamerMode || module instanceof SessionStats || module instanceof PartyDetector
                    || module instanceof coralintel.module.modules.DuelsIntel) {
                PanelState st = new PanelState(secondRowX, secondRowY);
                panels.put(module, st);
                secondRow.add(st);
                secondRowX += columnGap;
                continue;
            }

            // Pregame + snipe messages share one "Chat" panel (built in chatRows()).
            if (module instanceof PregameMessages) {
                PanelState st = new PanelState(startX, startY);
                panels.put(CHAT, st);
                firstRow.add(st);
                startX += columnGap;
                continue;
            }
            if (module instanceof SnipeMessages) {
                continue;
            }

            PanelState st = new PanelState(startX, startY);
            panels.put(module, st);
            firstRow.add(st);
            startX += columnGap;
        }

        // Blacklist/Safelist and Reminders sit on the second row too.
        PanelState lists = new PanelState(secondRowX, secondRowY);
        panels.put(BLACKLIST_SAFELIST, lists);
        secondRow.add(lists);
        secondRowX += columnGap;
        PanelState reminders = new PanelState(secondRowX, secondRowY);
        panels.put(REMINDERS, reminders);
        secondRow.add(reminders);
        secondRowX += columnGap;

        // Second-row panel k sits in the same column as first-row panel k, so when that one opens
        // (and grows taller) the one below it is pushed down instead of being covered.
        for (int i = 0; i < secondRow.size(); i++) {
            if (i < firstRow.size()) secondRow.get(i).above = firstRow.get(i);
        }

        // Scripts panel on a third row. Third-row panels stack under whatever is above them in
        // their column (second row if there is one, else the first row), like the second row does.
        firstRowPanels = firstRow;
        secondRowPanels = secondRow;
        int thirdRowY = secondRowY + HEADER_H + 16;
        // Third row: Configs first, then Scripts, then any script module panels.
        PanelState configs = new PanelState(PANEL_X0, thirdRowY);
        configs.above = panelAboveScriptColumn(0);
        panels.put(CONFIGS, configs);
        PanelState scripts = new PanelState(PANEL_X0 + columnGap, thirdRowY);
        scripts.above = panelAboveScriptColumn(1);
        panels.put(SCRIPTS, scripts);
    }

    private PanelState panelAboveScriptColumn(int column) {
        if (column < secondRowPanels.size()) return secondRowPanels.get(column);
        if (column < firstRowPanels.size()) return firstRowPanels.get(column);
        return null;
    }

    /** Adds a panel for every module a running script registered and drops the ones that are gone. */
    private void syncScriptPanels() {
        ScriptManager manager = CoralIntel.scriptManager;
        List<ScriptModule> live = manager == null ? new ArrayList<ScriptModule>() : manager.getModules();

        java.util.Iterator<Map.Entry<Object, PanelState>> it = panels.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Object, PanelState> entry = it.next();
            if (entry.getKey() instanceof ScriptModule && !live.contains(entry.getKey())) {
                scriptPanelMemory.put(((ScriptModule) entry.getKey()).getName(), entry.getValue());
                it.remove(); // remembered, so a reloaded script's panel comes back in the same place
            }
        }

        for (ScriptModule module : live) {
            if (panels.containsKey(module)) continue;
            PanelState state = scriptPanelMemory.remove(module.getName());
            if (state == null) {
                int slot = 2; // slot 0 = Configs, slot 1 = Scripts
                for (Object key : panels.keySet()) {
                    if (key instanceof ScriptModule) slot++;
                }
                state = new PanelState(PANEL_X0 + slot * (PANEL_W + 20), 0);
                state.above = panelAboveScriptColumn(slot);
                if (state.above == null) state.y = scriptFallbackY;
            }
            panels.put(module, state);
        }
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
        final int color;
        final boolean truncate;
        SectionLabelRow(String text) { this(text, TEXT_DIM, false); }
        SectionLabelRow(String text, int color, boolean truncate) {
            this.text = text;
            this.color = color;
            this.truncate = truncate;
            this.h = 16;
        }
        void render(int x, int y, int w, int mouseX, int mouseY) {
            String shown = text;
            if (truncate && mc.fontRendererObj.getStringWidth(shown) > w) {
                while (shown.length() > 3 && mc.fontRendererObj.getStringWidth(shown + "\u2026") > w) {
                    shown = shown.substring(0, shown.length() - 1);
                }
                shown += "\u2026";
            }
            mc.fontRendererObj.drawString(shown, x, y, color, false);
        }
    }

    /** Two click-to-run buttons side by side (used by the Scripts panel). */
    private class ButtonPairRow extends Row {
        final String[] labels;
        final Runnable[] actions;

        ButtonPairRow(String label1, Runnable action1, String label2, Runnable action2) {
            this.labels = new String[]{label1, label2};
            this.actions = new Runnable[]{action1, action2};
            this.h = 16;
        }

        private int buttonX(int i, int x, int w) {
            int half = (w - 4) / 2;
            return i == 0 ? x : x + half + 4;
        }

        private int buttonW(int i, int w) {
            int half = (w - 4) / 2;
            return i == 0 ? half : w - half - 4;
        }

        void render(int x, int y, int w, int mouseX, int mouseY) {
            for (int i = 0; i < 2; i++) {
                int bx = buttonX(i, x, w);
                int bw = buttonW(i, w);
                boolean hovered = hit(mouseX, mouseY, bx, y, bw, h);
                RoundedUtils.drawRoundedRect(bx, y, bw, h, 3, hovered ? 0x33FFFFFF : 0x1AFFFFFF);
                RoundedUtils.drawRoundedOutline(bx, y, bw, h, 3, 1f, hovered ? ACCENT : 0x55FFFFFF);
                int textW = mc.fontRendererObj.getStringWidth(labels[i]);
                mc.fontRendererObj.drawString(labels[i], bx + (bw - textW) / 2, y + 4, hovered ? TEXT_ON : TEXT_DIM, false);
            }
        }

        boolean click(int x, int y, int w, int mouseX, int mouseY) {
            for (int i = 0; i < 2; i++) {
                if (hit(mouseX, mouseY, buttonX(i, x, w), y, buttonW(i, w), h)) {
                    actions[i].run();
                    return true;
                }
            }
            return false;
        }
    }

    /** One script on a single line: name, a coloured status word, and the on/off box. */
    private class ScriptRow extends Row {
        final ScriptInstance script;
        final ScriptManager manager;

        ScriptRow(ScriptInstance script, ScriptManager manager) {
            this.script = script;
            this.manager = manager;
        }

        void render(int x, int y, int w, int mouseX, int mouseY) {
            boolean hovered = hit(mouseX, mouseY, x, y, w, h);
            boolean value = script.isWanted();

            String status;
            switch (script.getStatus()) {
                case RUNNING: status = "running"; break;
                case ERROR: status = "error"; break;
                default: status = "off"; break;
            }
            int boxSize = 14;
            int statusW = mc.fontRendererObj.getStringWidth(status);
            int nameRoom = w - boxSize - statusW - 14; // keep the name clear of the status and the box
            String name = script.name;
            while (name.length() > 3 && mc.fontRendererObj.getStringWidth(name) > nameRoom) {
                name = name.substring(0, name.length() - 1);
            }
            if (!name.equals(script.name)) name += "\u2026";

            mc.fontRendererObj.drawString(name, x, y + 6, value || hovered ? TEXT_ON : TEXT_DIM, false);
            int nameW = mc.fontRendererObj.getStringWidth(name);
            mc.fontRendererObj.drawString(status, x + nameW + 6, y + 6, script.statusColor(), false);

            int boxX = x + w - boxSize;
            int boxY = y + 3;
            if (value) {
                RoundedUtils.drawRoundedRect(boxX, boxY, boxSize, boxSize, 3, 0xFF1A1A22);
                RoundedUtils.drawRoundedOutline(boxX, boxY, boxSize, boxSize, 3, 1f, ACCENT);
                mc.fontRendererObj.drawString("\u25B6", boxX + 3, boxY + 3, ACCENT, false);
            } else {
                RoundedUtils.drawRoundedRect(boxX, boxY, boxSize, boxSize, 3, hovered ? 0x33FFFFFF : 0x1AFFFFFF);
                RoundedUtils.drawRoundedOutline(boxX, boxY, boxSize, boxSize, 3, 1f, hovered ? 0x88FFFFFF : 0x55FFFFFF);
            }
        }

        boolean click(int x, int y, int w, int mouseX, int mouseY) {
            if (hit(mouseX, mouseY, x, y, w, h)) {
                manager.setEnabled(script, !script.isWanted());
                return true;
            }
            return false;
        }
    }

    /** One saved config: its name (active one highlighted) and Load / Save buttons. */
    private class ConfigRow extends Row {
        private static final int BTN_W = 34;
        final String name;

        ConfigRow(String name) {
            this.name = name;
            this.h = 16;
        }

        private int saveX(int x, int w) { return x + w - BTN_W; }
        private int loadX(int x, int w) { return saveX(x, w) - BTN_W - 4; }

        private void button(int bx, int y, String label, boolean hovered) {
            RoundedUtils.drawRoundedRect(bx, y, BTN_W, h, 3, hovered ? 0x33FFFFFF : 0x1AFFFFFF);
            RoundedUtils.drawRoundedOutline(bx, y, BTN_W, h, 3, 1f, hovered ? ACCENT : 0x55FFFFFF);
            int textW = mc.fontRendererObj.getStringWidth(label);
            mc.fontRendererObj.drawString(label, bx + (BTN_W - textW) / 2, y + 4, hovered ? TEXT_ON : TEXT_DIM, false);
        }

        void render(int x, int y, int w, int mouseX, int mouseY) {
            boolean active = name.equalsIgnoreCase(Config.lastConfig != null ? Config.lastConfig : "default");
            int nameRoom = w - BTN_W * 2 - 12;
            String shown = name;
            while (shown.length() > 3 && mc.fontRendererObj.getStringWidth(shown) > nameRoom) {
                shown = shown.substring(0, shown.length() - 1);
            }
            if (!shown.equals(name)) shown += "\u2026";
            mc.fontRendererObj.drawString(shown, x, y + 4, active ? ACCENT : TEXT_ON, false);

            button(loadX(x, w), y, "Load", hit(mouseX, mouseY, loadX(x, w), y, BTN_W, h));
            button(saveX(x, w), y, "Save", hit(mouseX, mouseY, saveX(x, w), y, BTN_W, h));
        }

        boolean click(int x, int y, int w, int mouseX, int mouseY) {
            if (hit(mouseX, mouseY, loadX(x, w), y, BTN_W, h)) {
                new Config(name, false).load();
                return true;
            }
            if (hit(mouseX, mouseY, saveX(x, w), y, BTN_W, h)) {
                new Config(name, false).save();
                return true;
            }
            return false;
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

    /** What a text box edits: a message property (saved with the main config) or a script text setting. */
    private interface TextTarget {
        String get();
        void set(String value);
        /** Messages that get sent can't start with the command prefix; script text is never sent. */
        boolean allowCommandPrefix();
        int maxLength();
    }

    private Object editingKey = null; // the property/setting being edited (rows are rebuilt each frame)
    private TextTarget editingTarget = null;
    private final StringBuilder editBuffer = new StringBuilder();

    private void startEditing(Object key, TextTarget target) {
        commitEdit();
        editingKey = key;
        editingTarget = target;
        editBuffer.setLength(0);
        String current = target.get();
        editBuffer.append(current == null ? "" : current);
    }

    private void cancelEdit() {
        editingKey = null;
        editingTarget = null;
    }

    private void commitEdit() {
        if (editingKey == null) return;

        TextTarget target = editingTarget;
        cancelEdit();
        String text = editBuffer.toString().trim();

        // A leading "." would be run as a client command instead of sent.
        if (!target.allowCommandPrefix() && text.startsWith(CommandManager.PREFIX)) {
            ChatUtil.sendFormatted("&cThis text can't start with \".\" \u2014 it would be run as a command.");
            return;
        }

        if (text.equals(target.get())) return;

        target.set(text);
    }

    private class TextRow extends Row {
        final String label;
        final Object key;
        final TextTarget target;
        final String emptyText;

        TextRow(String label, TextProperty property) {
            this.label = label;
            this.key = property;
            this.emptyText = "(empty \u2014 skipped)";
            this.h = 34;
            this.target = new TextTarget() {
                public String get() { return property.getValue(); }
                public void set(String value) {
                    property.setValue(value);
                    String name = Config.lastConfig != null ? Config.lastConfig : "default";
                    new Config(name, false).save();
                }
                public boolean allowCommandPrefix() { return false; }
                public int maxLength() { return PregameMessages.MAX_LENGTH; }
            };
        }

        /** Free-standing text box (used for the new config name). */
        TextRow(String label, Object key, String emptyText, TextTarget target) {
            this.label = label;
            this.key = key;
            this.emptyText = emptyText;
            this.h = 34;
            this.target = target;
        }

        /** Script text setting: saved with the script's own state, never sent anywhere. */
        TextRow(String label, TextSetting setting) {
            this.label = label;
            this.key = setting;
            this.emptyText = "(empty)";
            this.h = 34;
            this.target = new TextTarget() {
                public String get() { return setting.getValue(); }
                public void set(String value) { setting.setValue(value); }
                public boolean allowCommandPrefix() { return true; }
                public int maxLength() { return setting.getMaxLength(); }
            };
        }

        void render(int x, int y, int w, int mouseX, int mouseY) {
            boolean editing = editingKey == key;
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
                String value = target.get() == null ? "" : target.get();
                if (value.isEmpty()) {
                    shown = emptyText;
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
                startEditing(key, target);
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
            } else if (setting instanceof TextSetting) {
                rows.add(new TextRow(setting.getName(), (TextSetting) setting));
            }
        }
    }

    /** The Scripts panel: reload/folder buttons, then one toggle + status line per script file. */
    private List<Row> scriptRows() {
        List<Row> rows = new ArrayList<>();
        final ScriptManager manager = CoralIntel.scriptManager;
        if (manager == null) {
            rows.add(new SectionLabelRow("Scripting is not loaded"));
            return rows;
        }

        rows.add(new ButtonPairRow("Reload", manager::reloadAll, "Open folder", manager::openFolder));

        List<ScriptInstance> scripts = manager.getScripts();
        if (scripts.isEmpty()) {
            rows.add(new SectionLabelRow("Drop .lua files in the folder", TEXT_DIM, true));
        }
        for (final ScriptInstance script : scripts) {
            rows.add(new ScriptRow(script, manager));
            if (script.getStatus() == ScriptInstance.Status.ERROR) {
                SectionLabelRow error = new SectionLabelRow(script.getLastError(), script.statusColor(), true);
                error.h = 11; // just the message, tucked under its script
                rows.add(error);
            }
        }
        return rows;
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

    /** The Configs panel: a name box that saves a new config on Enter, then every saved config with Load / Save. */
    private List<Row> configRows() {
        List<Row> rows = new ArrayList<>();

        rows.add(new TextRow("Save as new config", newConfigKey, "(type a name, Enter)", new TextTarget() {
            public String get() { return ""; }
            public void set(String value) {
                if (value.isEmpty()) return;
                if (!Config.isValidName(value)) {
                    ChatUtil.sendFormatted("&cConfig name can only contain letters, numbers, - and _ (max 32).");
                    return;
                }
                new Config(value, false).save();
            }
            public boolean allowCommandPrefix() { return false; }
            public int maxLength() { return 32; }
        }));

        List<String> saved = Config.listSaved();
        rows.add(new SectionLabelRow("SAVED (" + saved.size() + ")"));
        if (saved.isEmpty()) {
            rows.add(new SectionLabelRow("None yet \u2014 name one above", TEXT_DIM, true));
        }
        for (String name : saved) rows.add(new ConfigRow(name));
        rows.add(new SectionLabelRow("Save overwrites. Highlighted = active", TEXT_DIM, true));
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
        if (panelKey == CONFIGS) return configRows();
        if (panelKey == SCRIPTS) return scriptRows();

        Module module = (Module) panelKey;
        List<Row> rows = new ArrayList<>();

        if (module instanceof ScriptModule) {
            String description = ((ScriptModule) module).getDescription();
            if (!description.isEmpty()) rows.add(new SectionLabelRow(description, TEXT_DIM, true));
        }

        addSettingRows(rows, module);

        if (module instanceof ScriptModule && module.getSettings().isEmpty()) {
            rows.add(new SectionLabelRow("No settings"));
        }

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

        if (module instanceof PartyDetector) {
            PartyDetector party = (PartyDetector) module;
            rows.add(new SectionLabelRow("SHOWN ONLY TO YOU (never sent to chat)"));
            rows.add(new TextRow("Message ({count} {players})", party.message));
            rows.add(new SectionLabelRow("Click a box, type, Enter to save"));
        }

        if (module instanceof SessionStats) {
            rows.add(new SectionLabelRow("THIS SESSION"));
            for (String line : ((SessionStats) module).guiLines()) {
                rows.add(new SectionLabelRow(line));
            }
            rows.add(new SectionLabelRow(".reset restarts it, .session prints it"));
            rows.add(new SectionLabelRow(".history shows your saved sessions"));
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

    private long openedAt;
    /** How far the whole GUI has been scrolled up (0 = top); panels' y positions already include it. */
    private float pageScroll;

    @Override
    public void initGui() {
        super.initGui();
        openedAt = System.nanoTime();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // Opening animation: the backdrop fades in while the panels grow from 92% to full size.
        float t = Math.min(1f, (System.nanoTime() - openedAt) / 180_000_000f);
        float ease = 1f - (1f - t) * (1f - t) * (1f - t);
        float scale = 0.92f + 0.08f * ease;
        drawRect(0, 0, width, height, ((int) (0x88 * ease) << 24));

        syncScriptPanels();

        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        net.minecraft.client.renderer.GlStateManager.translate(width / 2f, height / 2f, 0f);
        net.minecraft.client.renderer.GlStateManager.scale(scale, scale, 1f);
        net.minecraft.client.renderer.GlStateManager.translate(-width / 2f, -height / 2f, 0f);
        // Keep hover highlights under the cursor while the scale settles.
        int animX = (int) (width / 2f + (mouseX - width / 2f) / scale);
        int animY = (int) (height / 2f + (mouseY - height / 2f) / scale);

        // The session HUD is shown here too so it can be placed by eye - drawn first, so it sits
        // BEHIND every panel.
        SessionStats session = sessionStats();
        if (session != null) {
            session.setPlacing(isCtrlKeyDown());
            session.drawHud(false, mouseX, mouseY);
            session.setPlacing(false);
        }

        // Stack the second row under the first-row panel above it (it moves down when that one opens).
        for (PanelState st : panels.values()) {
            if (st.above != null && !st.userMoved) {
                st.y = st.above.y + st.above.lastHeight + 16;
            }
        }

        for (Map.Entry<Object, PanelState> entry : panels.entrySet()) {
            drawPanel(entry.getKey(), entry.getValue(), animX, animY);
        }
        net.minecraft.client.renderer.GlStateManager.popMatrix();

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private static SessionStats sessionStats() {
        Module module = CoralIntel.moduleManager.getModule(SessionStats.class);
        return module instanceof SessionStats ? (SessionStats) module : null;
    }

    private static final int MAX_VISIBLE_CONTENT = 400; // caps a panel's height before it scrolls
    private static final int BAR_ZONE = PAD;            // width of the clickable scrollbar strip at the panel's right edge

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
        state.lastHeight = panelH;

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
            boolean barHot = state.draggingBar
                    || hit(mouseX, mouseY, x + PANEL_W - BAR_ZONE, contentTop, BAR_ZONE, trackH);
            int barX = x + PANEL_W - 6;
            drawRect(barX, contentTop, barX + 4, contentTop + trackH, barHot ? 0x44FFFFFF : 0x22FFFFFF);
            drawRect(barX, thumbY, barX + 4, thumbY + thumbH, barHot ? 0xCCFFFFFF : ACCENT);
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

        // Right click a panel header: open / close its settings.
        if (button == 1) {
            for (Map.Entry<Object, PanelState> entry : panels.entrySet()) {
                PanelState state = entry.getValue();
                if (hit(mouseX, mouseY, (int) state.x, (int) state.y, PANEL_W, HEADER_H)) {
                    state.collapsed = !state.collapsed;
                    return;
                }
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
                int contentHeight = sumHeight(rows);
                int visibleContentHeight = Math.min(contentHeight, MAX_VISIBLE_CONTENT);

                // Scrollbar: drag the thumb, or click the track to jump there (then keep dragging).
                if (state.maxScroll > 0
                        && hit(mouseX, mouseY, x + PANEL_W - BAR_ZONE, contentTop, BAR_ZONE, visibleContentHeight)) {
                    int thumbH = Math.max(16, visibleContentHeight * visibleContentHeight / contentHeight);
                    int thumbY = contentTop + (int) ((float) state.scrollOffset / state.maxScroll
                            * (visibleContentHeight - thumbH));
                    state.barTrackH = visibleContentHeight;
                    state.barThumbH = thumbH;
                    state.barTop = contentTop;
                    state.barGrab = (mouseY >= thumbY && mouseY < thumbY + thumbH) ? mouseY - thumbY : thumbH / 2;
                    state.draggingBar = true;
                    dragScrollbar(state, mouseY);
                    return;
                }

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

    /** Maps the mouse position on the scrollbar track to the panel's scroll offset. */
    private void dragScrollbar(PanelState state, int mouseY) {
        int travel = state.barTrackH - state.barThumbH;
        if (travel <= 0) return;
        float ratio = (mouseY - state.barGrab - state.barTop) / (float) travel;
        state.scrollOffset = Math.max(0, Math.min(state.maxScroll, Math.round(ratio * state.maxScroll)));
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
        for (PanelState p : panels.values()) {
            p.dragging = false;
            p.draggingBar = false;
        }
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
            if (state.draggingBar) {
                dragScrollbar(state, mouseY);
                return;
            }
        }
        for (PanelState state : panels.values()) {
            if (state.dragging) {
                state.x = mouseX - state.dragOffX;
                state.y = mouseY - state.dragOffY;
                state.userMoved = true;
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

        // 1) a panel whose settings list overflows scrolls itself while the cursor is over it.
        for (Map.Entry<Object, PanelState> entry : panels.entrySet()) {
            PanelState state = entry.getValue();
            if (state.collapsed || state.maxScroll <= 0) continue;

            int x = (int) state.x;
            int y = (int) state.y;
            int visibleContentHeight = Math.min(sumHeight(buildRows(entry.getKey())), MAX_VISIBLE_CONTENT);
            int panelBottom = y + HEADER_H + visibleContentHeight + PAD;

            if (scaledMouseX >= x && scaledMouseX < x + PANEL_W && scaledMouseY >= y && scaledMouseY < panelBottom) {
                state.scrollOffset -= scroll > 0 ? 20 : -20;
                state.scrollOffset = Math.max(0, Math.min(state.scrollOffset, state.maxScroll));
                return;
            }
        }

        // 2) anywhere else the whole GUI scrolls, so panels below the bottom of the screen can be reached.
        float bottom = 0f;
        for (PanelState st : panels.values()) bottom = Math.max(bottom, st.y + st.lastHeight);
        float limit = Math.max(0f, bottom - pageScroll + 24f - height); // how far the page can move up
        float target = Math.max(-limit, Math.min(0f, pageScroll + (scroll > 0 ? 30f : -30f)));
        float delta = target - pageScroll;
        if (delta != 0f) {
            for (PanelState st : panels.values()) st.y += delta;
            pageScroll = target;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (editingKey != null) {
            if (keyCode == 1) {                      // Esc: cancel the edit, keep the GUI open
                cancelEdit();
            } else if (keyCode == 28 || keyCode == 156) { // Enter / numpad Enter: save
                commitEdit();
            } else if (keyCode == 14) {              // Backspace
                if (editBuffer.length() > 0) editBuffer.deleteCharAt(editBuffer.length() - 1);
            } else if (isKeyComboCtrlV(keyCode)) {   // Ctrl+V
                String clip = ChatAllowedCharacters.filterAllowedCharacters(getClipboardString());
                int room = editingTarget.maxLength() - editBuffer.length();
                if (room > 0) editBuffer.append(clip, 0, Math.min(room, clip.length()));
            } else if (ChatAllowedCharacters.isAllowedCharacter(typedChar)
                    && editBuffer.length() < editingTarget.maxLength()) {
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
