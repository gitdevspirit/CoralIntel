package coralintel.command.commands;

import coralintel.CoralIntel;
import coralintel.command.Command;
import coralintel.module.modules.LobbyIntel;

import java.io.File;

/**
 * .intelpath <path>  — set the Prism log path for API key auto-detection
 * .intelpath         — show current path
 * .intelpath scan    — manually trigger a key scan now
 */
public class IntelPathCommand extends Command {

    public IntelPathCommand() {
        super("intelpath", "ipath");
        setDescription("Set the Prism log path for Hypixel API key auto-detection.");
    }

    @Override
    public void execute(String[] args) {
        LobbyIntel module = (LobbyIntel) CoralIntel.moduleManager.modules.get(LobbyIntel.class);
        if (module == null) { reply("&cLobbyIntel module not found."); return; }

        if (args.length == 0) {
            reply("&7Log path: &f" + module.logPath.getValue());
            File f = new File(module.logPath.getValue());
            reply(f.exists() ? "&aFile exists." : "&cFile not found.");
            return;
        }

        if (args[0].equalsIgnoreCase("scan")) {
            reply("&7Scanning log for API key...");
            module.tryAutoDetectKey();
            return;
        }

        // Join all args in case path has spaces
        String path = String.join(" ", args);
        module.logPath.setValue(path);
        File f = new File(path);
        reply("&7Log path set to: &f" + path);
        reply(f.exists() ? "&aFile found. Run &f.ipath scan &ato detect key now." : "&cWarning: file not found at that path.");
    }
}
