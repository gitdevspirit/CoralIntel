package coralintel.ui.intel;

import coralintel.event.EventTarget;
import coralintel.event.types.EventType;
import coralintel.events.LoadWorldEvent;
import coralintel.events.PacketEvent;
import coralintel.util.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.util.IChatComponent;

import java.util.List;

/**
 * When a Bedwars game ends, reminds you to tag the players from your .remind list who were in it
 * (met in the lobby, or noted during the game), so you do it while it's fresh.
 *
 * The end of the game is the same "1st Killer" / "Reward Summary" block SessionStats uses.
 * Leaving a game early (a world change) just forgets who was in it, with no alert.
 */
public class ReminderAlerts {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int MAX_SHOWN = 10;

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() != EventType.RECEIVE) return;
        if (!(event.getPacket() instanceof S02PacketChat)) return;

        S02PacketChat packet = (S02PacketChat) event.getPacket();
        if (packet.getType() == 2) return; // action bar

        IChatComponent component = packet.getChatComponent();
        if (component == null) return;

        String text = component.getUnformattedText().trim();
        if (text.indexOf(':') >= 0) return; // player chat, never a game event

        if (!text.startsWith("1st Killer") && !text.contains("Reward Summary")) return;

        final List<ReminderManager.Reminder> players = ReminderManager.getInstance().takeInGame();
        if (players.isEmpty()) return;

        new Thread(() -> {
            try {
                Thread.sleep(2500L); // after the end-of-game chat spam, so it isn't buried
            } catch (InterruptedException ignored) {
                return;
            }

            mc.addScheduledTask(() -> show(players));
        }, "CoralIntel-ReminderAlert").start();
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        ReminderManager.getInstance().clearInGame();
    }

    private static void show(List<ReminderManager.Reminder> players) {
        ChatUtil.sendFormatted("&e[Reminder] Game over \u2014 tag " + (players.size() == 1 ? "this player" : "these players")
                + " in Coral:");

        int shown = 0;
        for (ReminderManager.Reminder r : players) {
            if (shown++ >= MAX_SHOWN) break;
            String note = r.latestNote();
            ChatUtil.sendFormatted("  &f" + r.name + (note.isEmpty() ? "" : " &7\u2014 " + note));
        }

        if (players.size() > MAX_SHOWN) {
            ChatUtil.sendFormatted("  &8...and " + (players.size() - MAX_SHOWN) + " more (&7.remind list&8)");
        }

        ChatUtil.sendFormatted("&7Run &f.remind done <player> &7once they're tagged.");
    }
}
