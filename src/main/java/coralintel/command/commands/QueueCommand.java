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
 *
 * Duels: ".q duels" (or "d") queues the classic 1v1 duel; ".q duels <mode>" picks another.
 *   duels / classic / 1v1 -> /play duels_classic_duel
 *   uhc                   -> /play duels_uhc_duel
 *   sumo                  -> /play duels_sumo_duel
 *   bridge                -> /play duels_bridge_duel
 *   op                    -> /play duels_op_duel
 *   combo                 -> /play duels_combo_duel
 *   bow                   -> /play duels_bow_duel
 *   nodebuff              -> /play duels_potion_duel
 *   bedwars               -> /play duels_bedwars_two_one_duels
 */
public class QueueCommand extends Command {

    public QueueCommand() {
        super("q", "queue");
        setDescription("Queue Bedwars or Duels. Usage: /q <1s|2s|3s|4s|duels [mode]>");
    }

    @Override
    public void execute(String[] args) {
        String play = null;
        if (args.length >= 1 && isDuels(args[0])) {
            play = args.length == 1 ? "duels_classic_duel" : resolveDuels(args[1]);
            if (play == null) {
                reply("&cUnknown duel mode. &7Try: &fclassic, uhc, sumo, bridge, op, combo, bow, nodebuff, bedwars");
                return;
            }
        } else if (args.length == 1) {
            play = resolve(args[0]);
        }

        if (play == null) {
            reply("&cUsage: &f/q <1s|2s|3s|4s|duels [mode]> &7(solos, doubles, 3v3v3v3, 4v4v4v4, duels)");
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;

        mc.thePlayer.sendChatMessage("/play " + play);
    }

    private static boolean isDuels(String word) {
        String w = word.toLowerCase();
        return w.equals("duels") || w.equals("duel") || w.equals("d");
    }

    private static String resolveDuels(String mode) {
        switch (mode.toLowerCase()) {
            case "classic": case "1v1": case "duels": return "duels_classic_duel";
            case "uhc":                 return "duels_uhc_duel";
            case "sumo":                return "duels_sumo_duel";
            case "bridge":              return "duels_bridge_duel";
            case "op":                  return "duels_op_duel";
            case "combo":               return "duels_combo_duel";
            case "bow":                 return "duels_bow_duel";
            case "nodebuff": case "potion": return "duels_potion_duel";
            case "bedwars": case "bw":  return "duels_bedwars_two_one_duels";
            default:                    return null;
        }
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
