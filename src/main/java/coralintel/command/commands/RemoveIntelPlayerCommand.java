package coralintel.command.commands;

import coralintel.command.Command;
import coralintel.ui.intel.IntelManager;

/** Removes a player previously added to LobbyIntel through search or /who. */
public class RemoveIntelPlayerCommand extends Command {

    public RemoveIntelPlayerCommand() {
        super("remove", "intelremove", "rintel");
        setDescription("Remove a manually-added LobbyIntel player. Usage: .remove <player>");
    }

    @Override
    public void execute(String[] args) {
        if (args.length != 1) {
            reply("&cUsage: &f.remove <player>");
            return;
        }

        String name = args[0].trim();

        if (IntelManager.getInstance().removeManualPlayer(name)) {
            reply("&aRemoved &f" + name + " &afrom LobbyIntel.");
        } else {
            reply("&e" + name + " &7is not a manually-added LobbyIntel player.");
        }
    }
}
