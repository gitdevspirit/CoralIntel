package coralintel.command.commands;

import coralintel.CoralIntel;
import coralintel.command.Command;
import coralintel.command.CommandManager;

import java.util.List;

/** .chelp: lists every CoralIntel command (built from the registry, so new commands appear automatically). */
public class HelpCommand extends Command {
    public HelpCommand() {
        super("chelp");
        setDescription("List every CoralIntel command.");
    }

    @Override
    public void execute(String[] args) {
        List<Command> commands = CoralIntel.commandManager.getCommands();

        reply("&b&lCoralIntel commands &7(" + commands.size() + ", prefix &f" + CommandManager.PREFIX + "&7)");

        for (Command cmd : commands) {
            StringBuilder names = new StringBuilder("&f").append(CommandManager.PREFIX).append(cmd.getName());

            String[] aliases = cmd.getAliases();
            if (aliases.length > 1) {
                names.append(" &8(");
                for (int i = 1; i < aliases.length; i++) {
                    if (i > 1) names.append("&8, ");
                    names.append("&7").append(CommandManager.PREFIX).append(aliases[i]);
                }
                names.append("&8)");
            }

            reply(names + " &7- " + cmd.getDescription());
        }
    }
}
