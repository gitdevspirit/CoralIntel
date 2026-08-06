package coralintel.command.commands;

import coralintel.CoralIntel;
import coralintel.command.Command;
import coralintel.module.Module;
import coralintel.module.KeybindSetting;
import coralintel.module.modules.LobbyIntel;
import coralintel.util.KeyBindUtil;
import org.lwjgl.input.Keyboard;

/**
 * .bind hud <key>     — set the key that shows/hides the HUD overlay
 * .bind gui <key>     — set the key that opens/closes the LobbyIntel GUI
 * .bind clickgui <key>— set the key that opens/closes the ClickGUI
 * .bind tag <key>     — set the key that toggles the BedWarsTag module on/off
 * .bind ac <key>      — set the key that toggles the AntiCheat module on/off
 * .bind list          — show current binds
 *
 * Key names match LWJGL's Keyboard constants without the "KEY_" prefix,
 * e.g. H, L, F, GRAVE, LSHIFT, RETURN, SPACE, F6, NUMPAD0, RSHIFT ...
 */
public class BindCommand extends Command {

    private static final String[] TARGETS = {"hud", "gui", "clickgui", "tag", "ac"};

    public BindCommand() {
        super("bind");
        setDescription("Rebind CoralIntel keys. Usage: .bind <hud|gui|clickgui|tag|ac> <key>  or  .bind list");
    }

    @Override
    public void execute(String[] args) {
        LobbyIntel lobbyIntel = (LobbyIntel) CoralIntel.moduleManager.getModule("LobbyIntel");
        Module bedwarsTag = CoralIntel.moduleManager.getModule("BedWarsTag");
        Module antiCheat = CoralIntel.moduleManager.getModule("AntiCheat");

        if (lobbyIntel == null) {
            reply("&cLobbyIntel module not found.");
            return;
        }

        if (args.length == 0) {
            printUsage(lobbyIntel, bedwarsTag, antiCheat);
            return;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("list")) {
            printUsage(lobbyIntel, bedwarsTag, antiCheat);
            return;
        }

        boolean known = false;
        for (String t : TARGETS) if (t.equals(sub)) known = true;

        if (!known) {
            reply("&cUnknown bind target &f'" + args[0] + "'&c. Use &fhud&c, &fgui&c, &fclickgui&c, &ftag&c, or &fac&c.");
            return;
        }

        if (args.length < 2) {
            reply("&cUsage: &f.bind " + sub + " <key>");
            return;
        }

        String keyName = args[1].toUpperCase();
        int keyCode = Keyboard.getKeyIndex(keyName);

        if (keyCode == Keyboard.KEY_NONE) {
            reply("&cUnknown key &f'" + args[1] + "'&c. Try a name like &fH&c, &fL&c, &fF6&c, &fGRAVE&c, &fRETURN&c.");
            return;
        }

        if (sub.equals("tag") || sub.equals("ac")) {
            Module target = sub.equals("tag") ? bedwarsTag : antiCheat;
            String moduleName = sub.equals("tag") ? "BedWarsTag" : "AntiCheat";

            if (target == null) {
                reply("&c" + moduleName + " module not found.");
                return;
            }
            target.setKey(keyCode);
            reply("&a[Intel] " + moduleName + " toggle key set to &f" + KeyBindUtil.getKeyName(keyCode)
                    + " &7(press it in-game to turn it on/off)");
            return;
        }

        KeybindSetting target = sub.equals("hud") ? lobbyIntel.hudKeybind
                : sub.equals("gui") ? lobbyIntel.guiKeybind
                : lobbyIntel.clickGuiKeybind;
        target.setKeyCode(keyCode);

        String label = sub.equals("hud") ? "HUD toggle" : sub.equals("gui") ? "Open GUI" : "Open ClickGUI";
        reply("&a[Intel] " + label + " key set to &f" + KeyBindUtil.getKeyName(keyCode));
    }

    private void printUsage(LobbyIntel lobbyIntel, Module bedwarsTag, Module antiCheat) {
        reply("&7[Intel] Current binds:");
        reply("  &fHUD toggle: &d" + KeyBindUtil.getKeyName(lobbyIntel.hudKeybind.getKeyCode()));
        reply("  &fOpen GUI:   &d" + KeyBindUtil.getKeyName(lobbyIntel.guiKeybind.getKeyCode()));
        reply("  &fClickGUI:   &d" + KeyBindUtil.getKeyName(lobbyIntel.clickGuiKeybind.getKeyCode()));
        reply("  &fBedWarsTag: &d" + (bedwarsTag == null ? "n/a" : KeyBindUtil.getKeyName(bedwarsTag.getKey())));
        reply("  &fAntiCheat:  &d" + (antiCheat == null ? "n/a" : KeyBindUtil.getKeyName(antiCheat.getKey())));
        reply("&7Usage: &f.bind <hud|gui|clickgui|tag|ac> <key>");
    }
}
