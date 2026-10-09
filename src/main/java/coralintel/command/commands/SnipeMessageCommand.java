package coralintel.command.commands;

import coralintel.CoralIntel;
import coralintel.command.Command;
import coralintel.command.CommandManager;
import coralintel.config.Config;
import coralintel.module.modules.SnipeMessages;
import coralintel.property.properties.TextProperty;

/**
 * .sm  — send your snipe message to all chat (/shout). The only way it is ever sent.
 * .sm1 — view or set that message.
 *
 *   .sm                  send it now
 *   .sm1                 show the current message
 *   .sm1 <text...>       set it (saved to the config file immediately)
 *   .sm1 clear           blank it
 */
public class SnipeMessageCommand extends Command {

    private final boolean send;

    public SnipeMessageCommand(boolean send) {
        super(send ? new String[]{"sm"} : new String[]{"sm1"});
        this.send = send;
        setDescription(send
                ? "Send your snipe message to all chat with /shout. Usage: .sm"
                : "Set your snipe message. Usage: .sm1 <text|clear>");
    }

    @Override
    public void execute(String[] args) {
        SnipeMessages module =
                (SnipeMessages) CoralIntel.moduleManager.getModule(SnipeMessages.class);
        if (module == null) return;

        TextProperty property = module.message1;

        if (send) {
            String problem = module.send();
            if (problem != null) reply("&c" + problem);
            return;
        }

        if (args.length == 0) {
            String current = property.getValue();
            reply("&7Snipe message: "
                    + (current == null || current.trim().isEmpty()
                    ? "&c(empty \u2014 nothing is sent)"
                    : "&f" + current));
            reply("&7Type &f.sm &7to send it with /shout"
                    + (module.isEnabled() ? "" : " &c(SnipeMessages is off)")
                    + "&7. Change it with &f.sm1 <text> &7or &f.sm1 clear&7.");
            return;
        }

        String text = String.join(" ", args).trim();

        if (text.equalsIgnoreCase("clear") || text.equalsIgnoreCase("off")) {
            property.setValue("");
            save();
            reply("&aSnipe message cleared.");
            return;
        }

        if (text.startsWith(CommandManager.PREFIX)) {
            reply("&cA snipe message can't start with \".\" \u2014 it would be run as a command.");
            return;
        }

        if (text.length() > SnipeMessages.MAX_LENGTH) {
            text = text.substring(0, SnipeMessages.MAX_LENGTH);
            reply("&eTrimmed to " + SnipeMessages.MAX_LENGTH + " characters.");
        }

        property.setValue(text);
        save();
        reply("&aSnipe message set to: &f" + text);
    }

    private void save() {
        String name = Config.lastConfig != null ? Config.lastConfig : "default";
        new Config(name, false).save();
    }
}
