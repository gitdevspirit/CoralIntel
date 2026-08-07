package coralintel.command.commands;

import coralintel.command.Command;
import coralintel.ui.intel.BlacklistManager;
import coralintel.ui.intel.IntelManager;
import coralintel.ui.intel.IntelPlayer;

/**
 * .blacklist <player> [reason...] — personal KOS-style blacklist, saved to
 * disk (survives restarts). Shows as a blue "B" tag with the highest threat
 * priority in the tab list, HUD overlay, and .bw output. If you run into
 * them again in a later lobby, LobbyIntel notifies you with the reason.
 * .blacklist list — shows everyone currently on it.
 */
public class BlacklistCommand extends Command {

    public BlacklistCommand() {
        super("blacklist", "kos", "bl");
        setDescription("Blacklist a player. Usage: .blacklist <player> [reason]  or  .blacklist list");
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            reply("&cUsage: &f.blacklist <player> [reason]  &7or&c  .blacklist list");
            return;
        }

        if (args[0].equalsIgnoreCase("list")) {
            printList();
            return;
        }

        if (!args[0].matches("[A-Za-z0-9_]{1,16}")) {
            reply("&cUsage: &f.blacklist <player> [reason]");
            return;
        }

        String name = args[0];
        String reason = args.length > 1
                ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length))
                : "No reason given";

        boolean alreadyBlacklisted = BlacklistManager.getInstance().isBlacklisted(name);
        BlacklistManager.getInstance().blacklist(name, reason);

        // If they're already in the current lobby roster, update their live
        // IntelPlayer immediately instead of waiting for the next scan.
        IntelPlayer player = IntelManager.getInstance().getPlayer(name);
        if (player != null) {
            player.blacklisted = true;
            player.blacklistReason = reason;
            player.computeThreat();
        }

        if (alreadyBlacklisted) {
            reply("&aUpdated blacklist reason for &f" + name + "&a: &7" + reason);
        } else {
            reply("&aBlacklisted &f" + name + " &7— " + reason);
        }
    }

    private void printList() {
        java.util.Map<String, String> all = BlacklistManager.getInstance().getAll();

        if (all.isEmpty()) {
            reply("&7Blacklist is empty.");
            return;
        }

        reply("&7Blacklisted players (&f" + all.size() + "&7):");
        for (java.util.Map.Entry<String, String> entry : all.entrySet()) {
            reply("  &9" + entry.getKey() + " &8— &7" + entry.getValue());
        }
    }
}
