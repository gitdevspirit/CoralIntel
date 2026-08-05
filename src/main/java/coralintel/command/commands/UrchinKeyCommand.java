package coralintel.command.commands;

import coralintel.command.Command;
import coralintel.ui.intel.IntelManager;

/**
 * .coralkey <api-key> — set Coral API key
 * .coralkey           — show whether a key is set
 */
public class UrchinKeyCommand extends Command {

    public UrchinKeyCommand() {
        super("coralkey", "urchinkey", "ukey");
        setDescription("Set your Coral API key for tag detection. Usage: .coralkey <key>");
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            if (IntelManager.urchinApiKey.isEmpty()) {
                reply("&cNo Coral key set. Use &f.coralkey <key> &cto set one.");
            } else {
                String key = IntelManager.urchinApiKey;
                String masked = key.substring(0, Math.min(4, key.length()))
                        + "****"
                        + key.substring(Math.max(0, key.length() - 4));
                reply("&7Coral key set: &f" + masked);
            }
            return;
        }

        IntelManager.urchinApiKey = args[0].trim();
        IntelManager.getInstance().saveUrchinKeyToFile();
        reply("&aCoral API key set and saved.");
    }
}
