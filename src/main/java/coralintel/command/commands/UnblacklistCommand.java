package coralintel.command.commands;

import coralintel.command.Command;
import coralintel.ui.intel.BlacklistManager;
import coralintel.ui.intel.IntelManager;
import coralintel.ui.intel.IntelPlayer;

/** .unblacklist <player> — removes a player from the persisted blacklist. */
public class UnblacklistCommand extends Command {

    public UnblacklistCommand() {
        super("unblacklist", "unkos", "unbl");
        setDescription("Removes a player from the blacklist. Usage: .unblacklist <player>");
    }

    @Override
    public void execute(String[] args) {
        if (args.length != 1 || !args[0].matches("[A-Za-z0-9_]{1,16}")) {
            reply("&cUsage: &f.unblacklist <player>");
            return;
        }

        String name = args[0];
        boolean removed = BlacklistManager.getInstance().unblacklist(name);

        if (!removed) {
            reply("&e" + name + " &7isn't on the blacklist.");
            return;
        }

        IntelPlayer player = IntelManager.getInstance().getPlayer(name);
        if (player != null) {
            player.blacklisted = false;
            player.blacklistReason = null;
            player.computeThreat();
        }

        reply("&aRemoved &f" + name + " &afrom the blacklist.");
    }
}
