package coralintel.command.commands;

import coralintel.CoralIntel;
import coralintel.command.Command;
import coralintel.config.Config;
import coralintel.module.modules.PregameMessages;
import coralintel.property.properties.TextProperty;

/**
 * .pgm1 / .pgm2 / .pgm3 — view or set pregame message 1/2/3.
 *
 *   .pgm1                 show the current message
 *   .pgm1 <text...>       set it (saved to the config file immediately)
 *   .pgm1 clear           blank it, so that slot is skipped
 */
public class PregameMessageCommand extends Command {
    private final int index;

    public PregameMessageCommand(int index) {
        super("pgm" + index);
        this.index = index;
        setDescription("Set pregame message " + index + ". Usage: .pgm" + index + " <text|clear>");
    }

    @Override
    public void execute(String[] args) {
        PregameMessages module =
                (PregameMessages) CoralIntel.moduleManager.getModule(PregameMessages.class);
        if (module == null) return;

        TextProperty property = module.getMessageProperty(index);

        if (args.length == 0) {
            String current = property.getValue();
            reply("&7Pregame message " + index + ": "
                    + (current == null || current.trim().isEmpty()
                    ? "&c(empty — skipped)"
                    : "&f" + current));
            reply("&7Set it with &f.pgm" + index + " <text> &7or blank it with &f.pgm" + index + " clear&7.");
            return;
        }

        String text = String.join(" ", args).trim();

        if (text.equalsIgnoreCase("clear") || text.equalsIgnoreCase("off")) {
            property.setValue("");
            save();
            reply("&aPregame message " + index + " cleared.");
            return;
        }

        // The command parser would treat a leading "." as another client
        // command, so the message would never reach chat.
        if (text.startsWith(coralintel.command.CommandManager.PREFIX)) {
            reply("&cA pregame message can't start with \".\" — it would be run as a command.");
            return;
        }

        if (text.length() > PregameMessages.MAX_LENGTH) {
            text = text.substring(0, PregameMessages.MAX_LENGTH);
            reply("&eTrimmed to " + PregameMessages.MAX_LENGTH + " characters (Hypixel's chat limit).");
        }

        property.setValue(text);
        save();
        reply("&aPregame message " + index + " set to: &f" + text);
    }

    private void save() {
        String name = Config.lastConfig != null ? Config.lastConfig : "default";
        new Config(name, false).save();
    }
}
