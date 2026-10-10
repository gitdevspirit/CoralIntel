package coralintel.command.commands;

import coralintel.CoralIntel;
import coralintel.command.Command;
import coralintel.module.BooleanSetting;
import coralintel.module.DropdownSetting;
import coralintel.module.SliderSetting;
import coralintel.module.modules.LobbyIntel;
import coralintel.ui.tab.TabOverlay;

import java.util.Locale;

/**
 * .tab — change the Bed Wars tab overlay from chat, no ClickGUI needed.
 *
 *   .tab                      show the current settings
 *   .tab on|off               turn the overlay on / off
 *   .tab size <50-150>        overlay size in %
 *   .tab bg <0-100>           background opacity in % (0 = no background)
 *   .tab shadow|gray|respawn|dc   toggle text shadow / gray own team / respawn timer / keep DC rows
 *   .tab col <1-6> <stat>     set a column (Stars, Name, Winstreak, FKDR, Finals, KDR, Wins, WLR, Beds, BBLR, Off)
 *   .tab reset                restore the default columns
 */
public class TabCommand extends Command {

    public TabCommand() {
        super("tab", "tabo");
        setDescription("Change the tab overlay. Usage: .tab [on|off|size|bg|shadow|gray|respawn|dc|col|reset]");
    }

    @Override
    public void execute(String[] args) {
        LobbyIntel li = CoralIntel.moduleManager == null ? null
                : (LobbyIntel) CoralIntel.moduleManager.getModule(LobbyIntel.class);
        if (li == null) {
            reply("&cLobbyIntel isn't loaded.");
            return;
        }

        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status":
                status(li);
                return;
            case "on":
            case "off":
                li.tabOverlay.setValue(sub.equals("on"));
                reply("&7Tab overlay: " + flag(li.tabOverlay.getValue()));
                return;
            case "size":
                number(li.tabOverlaySize, args, "Size", "%");
                return;
            case "bg":
            case "background":
            case "opacity":
                number(li.tabOverlayBgOpacity, args, "Background", "%");
                return;
            case "shadow":
                toggle(li.tabOverlayShadow, "Text shadow");
                return;
            case "gray":
            case "grey":
                toggle(li.tabGrayOwnTeam, "Gray own team");
                return;
            case "respawn":
                toggle(li.tabRespawnTimer, "Respawn timer");
                return;
            case "dc":
                toggle(li.tabKeepDisconnected, "Keep disconnected");
                return;
            case "col":
            case "column":
                column(li, args);
                return;
            case "reset":
                DropdownSetting[] cols = columns(li);
                int[] defaults = {0, 1, 2, 3, 4, 5};
                for (int i = 0; i < cols.length; i++) cols[i].setIndex(defaults[i]);
                reply("&aColumns reset: &fStars, Name, Winstreak, FKDR, Finals, KDR");
                return;
            default:
                reply("&cUnknown option. &7Try &f.tab &7for the list.");
        }
    }

    private void status(LobbyIntel li) {
        reply("&7Tab overlay: " + flag(li.tabOverlay.getValue())
                + " &8| &7Size &f" + (int) li.tabOverlaySize.getValue() + "%"
                + " &8| &7Background &f" + (int) li.tabOverlayBgOpacity.getValue() + "%");
        reply("&7Shadow " + flag(li.tabOverlayShadow.getValue())
                + " &8| &7Gray own team " + flag(li.tabGrayOwnTeam.getValue())
                + " &8| &7Respawn " + flag(li.tabRespawnTimer.getValue())
                + " &8| &7DC " + flag(li.tabKeepDisconnected.getValue()));

        StringBuilder sb = new StringBuilder();
        for (DropdownSetting d : columns(li)) {
            if (sb.length() > 0) sb.append("&8, ");
            sb.append("&f").append(d.getValue());
        }
        reply("&7Columns: " + sb);
        reply("&8.tab on|off|size|bg|shadow|gray|respawn|dc|col <1-6> <stat>|reset");
    }

    private void number(SliderSetting setting, String[] args, String label, String unit) {
        if (args.length < 2) {
            reply("&7" + label + ": &f" + (int) setting.getValue() + unit
                    + " &8(" + (int) setting.getMin() + "-" + (int) setting.getMax() + ")");
            return;
        }
        try {
            double v = Double.parseDouble(args[1].replace("%", ""));
            v = Math.max(setting.getMin(), Math.min(setting.getMax(), v));
            setting.setValue(v);
            reply("&7" + label + ": &f" + (int) setting.getValue() + unit);
        } catch (NumberFormatException e) {
            reply("&cUsage: &f.tab " + args[0] + " <" + (int) setting.getMin() + "-" + (int) setting.getMax() + ">");
        }
    }

    private void toggle(BooleanSetting setting, String label) {
        setting.toggle();
        reply("&7" + label + ": " + flag(setting.getValue()));
    }

    private void column(LobbyIntel li, String[] args) {
        DropdownSetting[] cols = columns(li);
        if (args.length < 3) {
            reply("&cUsage: &f.tab col <1-6> <stat>");
            reply("&7Stats: &f" + String.join(", ", TabOverlay.COLUMN_OPTIONS));
            return;
        }
        int slot;
        try {
            slot = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            slot = 0;
        }
        if (slot < 1 || slot > cols.length) {
            reply("&cColumn must be 1-" + cols.length + ".");
            return;
        }
        String wanted = args[2].toLowerCase(Locale.ROOT);
        // Short names people will type.
        if (wanted.equals("ws")) wanted = "winstreak";
        for (int i = 0; i < TabOverlay.COLUMN_OPTIONS.length; i++) {
            if (TabOverlay.COLUMN_OPTIONS[i].toLowerCase(Locale.ROOT).equals(wanted)) {
                cols[slot - 1].setIndex(i);
                reply("&7Column " + slot + ": &f" + TabOverlay.COLUMN_OPTIONS[i]);
                return;
            }
        }
        reply("&cUnknown stat. &7Stats: &f" + String.join(", ", TabOverlay.COLUMN_OPTIONS));
    }

    private static DropdownSetting[] columns(LobbyIntel li) {
        return new DropdownSetting[]{li.tabCol1, li.tabCol2, li.tabCol3, li.tabCol4, li.tabCol5, li.tabCol6};
    }

    private static String flag(boolean on) {
        return on ? "&aon" : "&coff";
    }
}
