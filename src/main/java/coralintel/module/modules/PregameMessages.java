package coralintel.module.modules;

import coralintel.event.EventTarget;
import coralintel.event.types.EventType;
import coralintel.events.LoadWorldEvent;
import coralintel.events.PacketEvent;
import coralintel.module.Module;
import coralintel.module.SliderSetting;
import coralintel.property.properties.TextProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.util.IChatComponent;

/**
 * Auto-sends up to three pregame chat messages when the Bedwars countdown
 * hits 10 seconds — message 1 immediately, then message 2 and message 3 each
 * "Message Delay" seconds after the previous one (3s by default, adjustable in
 * the ClickGUI; keep it low enough that all three land before the game
 * starts). Edit them in-game with .pgm1 / .pgm2 / .pgm3; they are
 * TextProperty fields, so Config persists them to
 * ./config/CoralIntel/default.json with the rest of the module settings.
 * An empty message is skipped.
 */
public class PregameMessages extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    /** Hypixel's chat length limit. */
    public static final int MAX_LENGTH = 100;

    // Must be a system message that STARTS with this — a player typing it in
    // chat would show up as "[rank] name: ..." and not match.
    private static final String TRIGGER = "The game starts in 10 seconds";

    // Fields must stay public Property<?> instances: CoralIntel.init() finds
    // them by reflection and Config saves/loads them by property name.
    /** Gap between consecutive messages, in seconds (saved with the module settings). */
    public final SliderSetting messageDelay =
            register(new SliderSetting("Message Delay (s)", 3.0, 0.5, 10.0, 0.1));

    public final TextProperty message1 = new TextProperty("pgm-1", "gl hf everyone!");
    public final TextProperty message2 = new TextProperty("pgm-2", "may the best team win, have fun!");
    public final TextProperty message3 = new TextProperty("pgm-3", "good luck, see you at the end!");

    private boolean sentThisLobby = false;
    // Bumped on every world load so a half-finished sequence is cancelled
    // instead of firing into the next lobby/arena.
    private volatile int generation = 0;

    public PregameMessages() {
        super("PregameMessages", true);
    }

    /** 1-based accessor used by the .pgm1/.pgm2/.pgm3 commands. */
    public TextProperty getMessageProperty(int index) {
        switch (index) {
            case 1:  return message1;
            case 2:  return message2;
            default: return message3;
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!isEnabled() || sentThisLobby) return;
        if (event.getType() != EventType.RECEIVE) return;
        if (!(event.getPacket() instanceof S02PacketChat)) return;

        S02PacketChat packet = (S02PacketChat) event.getPacket();
        if (packet.getType() == 2) return; // action bar, not chat

        IChatComponent component = packet.getChatComponent();
        if (component == null) return;

        if (!component.getUnformattedText().trim().startsWith(TRIGGER)) return;

        sentThisLobby = true;

        final int gen = ++generation;
        final long delayMs = Math.round(messageDelay.getValue() * 1000.0);
        final String[] messages = {
                message1.getValue(), message2.getValue(), message3.getValue()
        };

        new Thread(() -> {
            boolean first = true;

            for (final String raw : messages) {
                String text = raw == null ? "" : raw.trim();
                if (text.isEmpty()) continue;
                if (text.length() > MAX_LENGTH) text = text.substring(0, MAX_LENGTH);
                final String message = text;

                if (!first) {
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ignored) {
                        return;
                    }
                }
                first = false;

                if (gen != generation) return;

                mc.addScheduledTask(() -> {
                    if (gen == generation && mc.thePlayer != null) {
                        mc.thePlayer.sendChatMessage(message);
                    }
                });
            }
        }, "CoralIntel-Pregame").start();
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        sentThisLobby = false;
        generation++;
    }
}
