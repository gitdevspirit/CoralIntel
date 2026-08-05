package coralintel.command.commands;

import coralintel.command.Command;
import coralintel.ui.intel.IntelManager;

/** Adds a player to LobbyIntel and starts fetching their available stats. */
public class AddIntelPlayerCommand extends Command {

    public AddIntelPlayerCommand() {
        super("add", "inteladd", "aintel");
        setDescription("Add a player to LobbyIntel. Usage: .add <player>");
    }

    @Override
    public void execute(String[] args) {
        if (args.length != 1 || !args[0].matches("[A-Za-z0-9_]{1,16}")) {
            reply("&cUsage: &f.add <player>");
            return;
        }

        String name = args[0];

        if (IntelManager.getInstance().getPlayer(name) != null) {
            reply("&e" + name + " &7is already in LobbyIntel.");
            return;
        }

        IntelManager.getInstance().addManualPlayer(name);
        reply("&aAdded &f" + name + " &ato LobbyIntel.");
    }
}
