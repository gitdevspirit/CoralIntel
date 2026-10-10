package coralintel.command.commands;

import coralintel.command.Command;
import coralintel.ui.intel.BedlifyManager;
import coralintel.ui.intel.IntelManager;

/**
 * .denick — find out who is behind a nick (Bedlify).
 *
 *   .denick <nick>   look one nick up
 *   .denick          look up every nicked player in your lobby again
 */
public class DenickCommand extends Command {

    public DenickCommand() {
        super("denick");
        setDescription("Find who is behind a nick (Bedlify). Usage: .denick [nick]");
    }

    @Override
    public void execute(String[] args) {
        BedlifyManager bedlify = BedlifyManager.getInstance();

        if (!bedlify.hasKey()) {
            reply("&cNo Bedlify key set. Use &f.bedlify key <key> &cfirst.");
            return;
        }

        if (args.length == 0) {
            int n = IntelManager.getInstance().denickAll(true);
            if (n == 0) {
                reply("&7No nicked players in the lobby. Use &f.denick <nick> &7to look one up.");
            } else {
                reply("&7Looking up " + n + (n == 1 ? " nicked player" : " nicked players") + "...");
            }
            return;
        }

        final String nick = args[0];
        if (!nick.matches("[A-Za-z0-9_]{1,16}")) {
            reply("&cThat isn't a valid nick.");
            return;
        }

        reply("&7Looking up &f" + nick + "&7...");
        bedlify.lookupAsync(nick, result -> {
            if (result.error != null) {
                reply("&c[Bedlify] &7" + result.error);
            } else {
                BedlifyManager.announce(nick, result);
            }
        });
    }
}
