package coralintel.command.commands;

import coralintel.command.Command;
import net.minecraft.client.Minecraft;

import java.util.Locale;

/**
 * Duels queue shortcuts: .dq <mode> sends /play for that Duels mode.
 * (Bedwars Duels is also available as .q bwduels.)
 */
public class DuelsQueueCommand extends Command {

    public DuelsQueueCommand() {
        super("dq", "dqueue");
        setDescription("Queue a Duels mode. Usage: .dq <classic|bridge|bwduels|sumo|uhc|op|nodebuff|skywars|combo>");
    }

    @Override
    public void execute(String[] args) {
        String play = args.length == 1 ? resolve(args[0]) : null;
        if (play == null) {
            reply("&cUsage: &f.dq <classic|bridge|bwduels|sumo|uhc|op|nodebuff|skywars|combo>");
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        mc.thePlayer.sendChatMessage("/play " + play);
    }

    private static String resolve(String mode) {
        switch (mode.toLowerCase(Locale.ROOT)) {
            case "classic": return "duels_classic_duel";
            case "bridge": return "duels_bridge_duel";
            case "bwduels": case "bwduel": return "duels_bedwars_two_one_duels";
            case "sumo": return "duels_sumo_duel";
            case "uhc": return "duels_uhc_duel";
            case "op": return "duels_op_duel";
            case "nodebuff": case "potion": return "duels_potion_duel";
            case "skywars": case "sw": return "duels_sw_duel";
            case "combo": return "duels_combo_duel";
            default: return null;
        }
    }
}
