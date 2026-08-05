package coralintel.command.commands;

import coralintel.CoralIntel;
import coralintel.command.Command;
import coralintel.module.Module;
import coralintel.module.KeybindSetting;
import coralintel.module.modules.LobbyIntel;
import coralintel.util.KeyBindUtil;
import org.lwjgl.input.Keyboard;

/**
 * .bind hud <key>   — set the key that shows/hides the HUD overlay
 * .bind gui <key>   — set the key that opens/closes the LobbyIntel GUI
 * .bind tag <key>   — set the key that toggles the BedWarsTag module on/off
 * .bind list        — show current binds
 *
 * Key names match LWJGL's Keyboard constants without the "KEY_" prefix,
 * e.g. H, L, F, GRAVE, LSHIFT, RETURN, SPACE, F6, NUMPAD0 ...
 */
public class BindCommand extends Command {

    public BindCommand() {
        super("bind");
        setDescription("Rebind CoralIntel keys. Usage: .bind <hud|gui|tag> <key>  or  .bind list");
    }

    @Override
    public void execute(String[] args) {
        LobbyIntel lobbyIntel = (LobbyIntel) CoralIntel.moduleManager.getModule("LobbyIntel");
        Module bedwarsTag = CoralIntel.moduleManager.getModule("BedWarsTag");

        if (lobbyIntel == null) {
            reply("&cLobbyIntel module not found.");
            return;
        }

        if (args.length == 0) {
            printUsage(lobbyIntel, bedwarsTag);
            return;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("list")) {
            printUsage(lobbyIntel, bedwarsTag);
            return;
        }

        if (!sub.equals("hud") && !sub.equals("gui") && !sub.equals("tag")) {
            reply("&cUnknown bind target &f'" + args[0] + "'&c. Use &fhud&c, &fgui&c, or &ftag&c.");
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

        if (sub.equals("tag")) {
            if (bedwarsTag == null) {
                reply("&cBedWarsTag module not found.");
                return;
            }
            bedwarsTag.setKey(keyCode);
            reply("&a[Intel] BedWarsTag toggle key set to &f" + KeyBindUtil.getKeyName(keyCode)
                    + " &7(press it in-game to turn the tag on/off)");
            return;
        }

        KeybindSetting target = sub.equals("hud") ? lobbyIntel.hudKeybind : lobbyIntel.guiKeybind;
        target.setKeyCode(keyCode);

        String label = sub.equals("hud") ? "HUD toggle" : "Open GUI";
        reply("&a[Intel] " + label + " key set to &f" + KeyBindUtil.getKeyName(keyCode));
    }

    private void printUsage(LobbyIntel lobbyIntel, Module bedwarsTag) {
        reply("&7[Intel] Current binds:");
        reply("  &fHUD toggle: &d" + KeyBindUtil.getKeyName(lobbyIntel.hudKeybind.getKeyCode()));
        reply("  &fOpen GUI:   &d" + KeyBindUtil.getKeyName(lobbyIntel.guiKeybind.getKeyCode()));
        reply("  &fBedWarsTag: &d" + (bedwarsTag == null ? "n/a" : KeyBindUtil.getKeyName(bedwarsTag.getKey())));
        reply("&7Usage: &f.bind <hud|gui|tag> <key>");
    }
}
