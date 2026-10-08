package coralintel.command.commands;

import coralintel.command.Command;
import coralintel.ui.intel.CoralApi;
import coralintel.ui.intel.IntelColors;
import coralintel.ui.intel.IntelManager;
import coralintel.ui.intel.IntelPlayer;
import net.minecraft.client.Minecraft;

/**
 * .view <player> — prints every Coral tag on a player (type, reason), colored
 * the same way the HUD / tab list color them.
 */
public class ViewCommand extends Command {

    public ViewCommand() {
        super("view", "coralview", "cview");
        setDescription("View a player's Coral tags. Usage: .view <player>");
    }

    @Override
    public void execute(String[] args) {
        if (args.length != 1 || !args[0].matches("[A-Za-z0-9_]{1,16}")) {
            reply("&cUsage: &f.view <player>");
            return;
        }

        if (IntelManager.urchinApiKey.isEmpty()) {
            reply("&cNo Coral key set. Use &f.coralkey <key> &cfirst.");
            return;
        }

        final String ign = args[0];
        reply("&7Looking up Coral tags for &f" + ign + "&7...");

        new Thread(() -> {
            String uuid = IntelManager.getInstance().resolveUuid(ign);
            CoralApi.LookupResult result = uuid == null
                    ? null
                    : CoralApi.lookup(uuid, ign, IntelManager.urchinApiKey);

            Minecraft.getMinecraft().addScheduledTask(() -> show(ign, uuid, result));
        }, "coral-view").start();
    }

    private void show(String ign, String uuid, CoralApi.LookupResult result) {
        if (uuid == null) {
            reply("&cNo Minecraft account found named &f" + ign + "&c.");
            return;
        }

        if (!result.isSuccess()) {
            reply("&c" + clean(result.error));
            return;
        }

        if (result.tags.isEmpty()) {
            reply("&f" + ign + " &7has no Coral tags.");
            return;
        }

        reply("&bCoral tags for &f" + ign + " &7(" + result.tags.size() + "):");

        for (CoralApi.Tag tag : result.tags) {
            // Reuse IntelPlayer's classification so the badge/color match the HUD.
            IntelPlayer classifier = new IntelPlayer(ign, null);
            classifier.urchinTag = tag.text;
            classifier.urchinType = (tag.icon + " " + tag.text + " " + tag.reason).toLowerCase();
            classifier.urchinReason = tag.reason.toLowerCase();

            String badge = classifier.getTagBadge();
            String detail = clean(tag.text)
                    + (tag.reason.isEmpty() || tag.reason.equalsIgnoreCase(tag.text)
                    ? "" : " &7— &f" + clean(tag.reason));

            if (badge.isEmpty()) {
                reply("&7- &f" + detail);
            } else {
                String code = badge.equals("CC")
                        ? "\u00A76"
                        : IntelColors.nearestCode(classifier.getTagColor());
                reply(code + "[" + badge + "] &f" + detail);
            }
        }
    }

    /** Strips formatting characters from API text so it can't inject color codes. */
    private static String clean(String text) {
        return text == null ? "" : text.replace("&", "").replace("\u00A7", "");
    }
}
