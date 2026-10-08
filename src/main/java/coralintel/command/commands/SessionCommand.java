package coralintel.command.commands;

import coralintel.CoralIntel;
import coralintel.command.Command;
import coralintel.module.Module;
import coralintel.module.modules.SessionStats;

/**
 * .session -- prints your session stats (wins, kills, FKDR, BBLR gained).
 * .reset   -- restarts the session from your current stats.
 *
 * Both are registered from this one class (see CoralIntel.init()).
 */
public class SessionCommand extends Command {

    private final boolean reset;

    public SessionCommand(boolean reset) {
        super(reset ? new String[]{"reset"} : new String[]{"session", "sess"});
        this.reset = reset;
        setDescription(reset
                ? "Restart your session stats from now. Usage: .reset"
                : "Show wins, kills, FKDR and BBLR gained this session. Usage: .session");
    }

    @Override
    public void execute(String[] args) {
        Module module = CoralIntel.moduleManager.getModule(SessionStats.class);
        if (!(module instanceof SessionStats)) {
            reply("&cSession stats aren't available.");
            return;
        }

        SessionStats stats = (SessionStats) module;

        if (!stats.isEnabled()) {
            reply("&cSessionStats is turned off. Enable it in the ClickGUI.");
            return;
        }

        if (reset) {
            stats.start(true);
        } else {
            stats.refresh(true);
        }
    }
}
