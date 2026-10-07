package coralintel.command.commands;

import coralintel.command.Command;
import net.minecraft.client.Minecraft;

/**
 * Bedwars queue shortcuts — usable as "/q 4s" (intercepted by CommandManager
 * before it reaches the server) or ".q 4s".
 *
 *   1s -> /play bedwars_eight_one
 *   2s -> /play bedwars_eight_two
 *   3s -> /play bedwars_four_three
 *   4s -> /play bedwars_four_four
 */
public class QueueCommand extends Command {

    public QueueCommand() {
        super("q", "queue");
        setDescription("Queue Bedwars. Usage: /q <1s|2s|3s|4s>");
    }

    @Override
    public void execute(String[] args) {
        String play = args.length == 1 ? resolve(args[0]) : null;

        if (play == null) {
            reply("&cUsage: &f/q <1s|2s|3s|4s> &7(solos, doubles, 3v3v3v3, 4v4v4v4)");
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;

        mc.thePlayer.sendChatMessage("/play " + play);
    }

    private static String resolve(String mode) {
        switch (mode.toLowerCase()) {
            case "1s": case "1": return "bedwars_eight_one";
            case "2s": case "2": return "bedwars_eight_two";
            case "3s": case "3": return "bedwars_four_three";
            case "4s": case "4": return "bedwars_four_four";
            default:   return null;
        }
    }
}
