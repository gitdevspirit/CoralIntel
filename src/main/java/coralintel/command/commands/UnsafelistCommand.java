package coralintel.command.commands;

import coralintel.command.Command;
import coralintel.ui.intel.IntelManager;
import coralintel.ui.intel.IntelPlayer;
import coralintel.ui.intel.SafelistManager;

/** .unsafelist <player> — removes a player from the persisted safelist. */
public class UnsafelistCommand extends Command {

    public UnsafelistCommand() {
        super("unsafelist", "unsl");
        setDescription("Removes a player from the safelist. Usage: .unsafelist <player>");
    }

    @Override
    public void execute(String[] args) {
        if (args.length != 1 || !args[0].matches("[A-Za-z0-9_]{1,16}")) {
            reply("&cUsage: &f.unsafelist <player>");
            return;
        }

        String name = args[0];
        boolean removed = SafelistManager.getInstance().unsafelist(name);

        if (!removed) {
            reply("&e" + name + " &7isn't on the safelist.");
            return;
        }

        IntelPlayer player = IntelManager.getInstance().getPlayer(name);
        if (player != null) {
            player.safelisted = false;
            player.safelistReason = null;
            player.computeThreat();
        }

        reply("&aRemoved &f" + name + " &afrom the safelist.");
    }
}
