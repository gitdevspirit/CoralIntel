package coralintel.command.commands;

import coralintel.command.Command;
import coralintel.ui.intel.IntelManager;
import coralintel.ui.intel.IntelPlayer;
import coralintel.ui.intel.SafelistManager;

/**
 * .safelist <player> [reason...] — marks a player as trusted/vouched-for,
 * saved to disk (survives restarts). Safelisted players are excluded from
 * Coral's cheater tag and threat elevation, and won't trigger cheater-flag
 * notifications. Auto-populated when you get a final kill on someone (see
 * LobbyIntel's FINAL KILL chat handler).
 * .safelist list — shows everyone currently on it.
 */
public class SafelistCommand extends Command {

    public SafelistCommand() {
        super("safelist", "sl");
        setDescription("Safelist a player. Usage: .safelist <player> [reason]  or  .safelist list");
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            reply("&cUsage: &f.safelist <player> [reason]  &7or&c  .safelist list");
            return;
        }

        if (args[0].equalsIgnoreCase("list")) {
            printList();
            return;
        }

        if (!args[0].matches("[A-Za-z0-9_]{1,16}")) {
            reply("&cUsage: &f.safelist <player> [reason]");
            return;
        }

        String name = args[0];
        String reason = args.length > 1
                ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length))
                : "No reason given";

        boolean alreadySafelisted = SafelistManager.getInstance().isSafelisted(name);
        SafelistManager.getInstance().safelist(name, reason);

        IntelPlayer player = IntelManager.getInstance().getPlayer(name);
        if (player != null) {
            player.safelisted = true;
            player.safelistReason = reason;
            player.computeThreat();
        }

        if (alreadySafelisted) {
            reply("&aUpdated safelist reason for &f" + name + "&a: &7" + reason);
        } else {
            reply("&aSafelisted &f" + name + " &7— " + reason);
        }
    }

    private void printList() {
        java.util.Map<String, String> all = SafelistManager.getInstance().getAll();

        if (all.isEmpty()) {
            reply("&7Safelist is empty.");
            return;
        }

        reply("&7Safelisted players (&f" + all.size() + "&7):");
        for (java.util.Map.Entry<String, String> entry : all.entrySet()) {
            reply("  &a" + entry.getKey() + " &8— &7" + entry.getValue());
        }
    }
}
