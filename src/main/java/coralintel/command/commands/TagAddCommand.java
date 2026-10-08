package coralintel.command.commands;

import coralintel.command.Command;
import coralintel.ui.intel.CoralApi;
import coralintel.ui.intel.IntelManager;
import net.minecraft.client.Minecraft;

/**
 * .tadd <player> <tag> <reason...> — adds a Coral tag to a player.
 * Example: .tadd Notch sniper queue sniped me twice
 *
 * Coral's add-tag endpoint is admin-only, so this only works with a key that
 * has that access; otherwise Coral's rejection is shown.
 */
public class TagAddCommand extends Command {

    public TagAddCommand() {
        super("tadd", "tagadd", "coraladd");
        setDescription("Add a Coral tag. Usage: .tadd <player> <tag> <reason>");
    }

    @Override
    public void execute(String[] args) {
        if (args.length < 3
                || !args[0].matches("[A-Za-z0-9_]{1,16}")
                || !args[1].matches("[A-Za-z0-9_\\-]{1,32}")) {
            reply("&cUsage: &f.tadd <player> <tag> <reason>");
            return;
        }

        if (IntelManager.urchinApiKey.isEmpty()) {
            reply("&cNo Coral key set. Use &f.coralkey <key> &cfirst.");
            return;
        }

        final String ign = args[0];
        final String type = args[1].toLowerCase();
        final String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));

        reply("&7Adding Coral tag &f" + type + " &7to &f" + ign + "&7...");

        new Thread(() -> {
            String uuid = IntelManager.getInstance().resolveUuid(ign);
            CoralApi.AddResult result = uuid == null
                    ? null
                    : CoralApi.addTag(uuid, type, reason, IntelManager.urchinApiKey);

            Minecraft.getMinecraft().addScheduledTask(() -> finish(ign, type, reason, uuid, result));
        }, "coral-tadd").start();
    }

    private void finish(String ign, String type, String reason, String uuid, CoralApi.AddResult result) {
        if (uuid == null) {
            reply("&cNo Minecraft account found named &f" + ign + "&c.");
            return;
        }

        if (!result.ok) {
            reply("&c" + result.message.replace("&", "").replace("\u00A7", ""));
            return;
        }

        reply("&aAdded Coral tag &f" + type + " &ato &f" + ign + "&a: &7" + reason);

        // If they're in the current lobby, pull their updated tags right away.
        IntelManager.getInstance().refreshCoralTags(ign);
    }
}
