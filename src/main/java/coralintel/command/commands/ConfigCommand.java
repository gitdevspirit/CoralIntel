package coralintel.command.commands;

import coralintel.CoralIntel;
import coralintel.command.Command;
import coralintel.command.CommandManager;
import coralintel.config.Config;

import java.util.Arrays;
import java.util.List;

/**
 * .c s [name]  — save current settings to a config file (default: "default")
 * .c l [name]  — load settings from a config file (default: "default")
 * .c list      — list saved config files
 * .c help      — list every CoralIntel command
 */
public class ConfigCommand extends Command {
    // Data files that share the config directory; .c must not overwrite them
    private static final List<String> RESERVED_NAMES = Arrays.asList("blacklist", "safelist", "roles", "snapshots", "reminders", "bedlify");

    public ConfigCommand() {
        super("c", "config");
        setDescription("Save/load settings, or list commands. Usage: .c <s|l|list|help> [name]");
    }

    /** Lists every registered command with its aliases and description (built from the registry, so new commands appear automatically). */
    private void sendHelp() {
        java.util.List<Command> commands = CoralIntel.commandManager.getCommands();

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

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            reply("&cUsage: &f.c <s|l|list|help> [name]");
            return;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("help") || sub.equals("h") || sub.equals("?")) {
            sendHelp();
            return;
        }

        String name = args.length > 1 ? args[1] : "default";

        if (!name.matches("[A-Za-z0-9_-]{1,32}") || RESERVED_NAMES.contains(name.toLowerCase())) {
            reply("&cConfig name can only contain letters, numbers, - and _.");
            return;
        }

        switch (sub) {
            case "s":
            case "save": {
                Config config = new Config(name, false);
                config.save();
                break;
            }
            case "l":
            case "load": {
                Config config = new Config(name, false);
                config.load();
                break;
            }
            case "list": {
                java.io.File dir = new java.io.File("./config/CoralIntel/");
                java.io.File[] files = dir.listFiles((d, fname) -> fname.endsWith(".json") && !RESERVED_NAMES.contains(fname.substring(0, fname.length() - 5)));

                if (files == null || files.length == 0) {
                    reply("&7No saved configs yet.");
                    return;
                }

                StringBuilder names = new StringBuilder();
                for (java.io.File f : files) {
                    if (names.length() > 0) names.append("&7, &f");
                    names.append(f.getName().replace(".json", ""));
                }
                reply("&7Saved configs: &f" + names);
                break;
            }
            default:
                reply("&cUnknown option &f'" + sub + "'&c. Use &fs&c, &fl&c, or &flist&c.");
        }
    }
}
