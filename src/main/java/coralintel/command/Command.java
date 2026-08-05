package coralintel.command;

import coralintel.util.ChatUtil;

public abstract class Command {

    private final String[] aliases;
    private String description = "No description.";

    public Command(String... aliases) {
        this.aliases = aliases;
    }

    protected void setDescription(String desc) {
        this.description = desc;
    }

    /** Called with the arguments AFTER the command name. e.g. ".b Sprint R" → args = ["Sprint", "R"] */
    public abstract void execute(String[] args);

    public String[] getAliases() {
        return aliases;
    }

    /** Primary name is the first alias */
    public String getName() {
        return aliases[0];
    }

    public String getDescription() {
        return description;
    }

    /** Convenience — sends a colour-formatted chat message to the player */
    protected void reply(String message) {
        ChatUtil.sendFormatted(message);
    }
}
