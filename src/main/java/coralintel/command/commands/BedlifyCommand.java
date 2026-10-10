package coralintel.command.commands;

import coralintel.command.Command;
import coralintel.ui.intel.BedlifyManager;
import coralintel.ui.intel.IntelManager;

/**
 * .bedlify — manage your Bedlify API key (used to denick nicks).
 *
 *   .bedlify key <key>   save your key (kept only in config/CoralIntel/bedlify.json)
 *   .bedlify clear       forget it
 *   .bedlify             show whether a key is set
 *
 * Like every .command, what you type here is handled locally and never sent to the server.
 */
public class BedlifyCommand extends Command {

    public BedlifyCommand() {
        super("bedlify");
        setDescription("Set your Bedlify API key for denicking. Usage: .bedlify key <key> | clear | status");
    }

    @Override
    public void execute(String[] args) {
        BedlifyManager bedlify = BedlifyManager.getInstance();
        String sub = args.length == 0 ? "status" : args[0].toLowerCase();

        switch (sub) {
            case "key":
            case "set":
                if (args.length < 2 || !BedlifyManager.looksLikeKey(args[1])) {
                    reply("&cUsage: &f.bedlify key <your key>");
                    return;
                }
                bedlify.setKey(args[1]);
                reply("&aBedlify key saved &8(" + bedlify.maskedKey() + ")&a. Nicks in your lobby will now be denicked.");
                int n = IntelManager.getInstance().denickAll(false);
                if (n > 0) reply("&7Looking up " + n + (n == 1 ? " nicked player" : " nicked players") + "...");
                return;

            case "clear":
            case "remove":
            case "off":
                bedlify.clearKey();
                reply("&aBedlify key removed.");
                return;

            default:
                if (bedlify.hasKey()) {
                    int left = bedlify.rateRemaining();
                    reply("&7Bedlify key: &a" + bedlify.maskedKey()
                            + (left >= 0 ? " &8(" + left + " requests left this minute)" : ""));
                } else {
                    reply("&7No Bedlify key set. Join &fbedlify.xyz &7in Minecraft to get a code, DM it to the Bedlify bot on Discord with &f/link <code>&7, then run &f.bedlify key <key>&7.");
                }
        }
    }
}
