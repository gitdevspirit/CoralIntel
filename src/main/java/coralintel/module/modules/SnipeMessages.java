package coralintel.module.modules;

import coralintel.event.EventTarget;
import coralintel.event.types.EventType;
import coralintel.events.LoadWorldEvent;
import coralintel.events.PacketEvent;
import coralintel.module.DropdownSetting;
import coralintel.module.Module;
import coralintel.module.SliderSetting;
import coralintel.property.properties.TextProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.util.IChatComponent;

/**
 * Auto-sends ONE snipe message to all chat with /shout (in-game chat is
 * team-only, so a plain message wouldn't reach everyone). Edit it in-game with
 * .sm1 (or .sm); it is a TextProperty, so Config persists it with the rest of
 * the module settings. An empty message sends nothing.
 *
 * "Send At" picks the trigger: the moment the game starts (default), or the
 * same 10-second countdown the pregame messages use. "Message Delay" waits that
 * many seconds after the trigger before sending.
 */
public class SnipeMessages extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    /** Same limit as the pregame messages (the "/shout " prefix is added on top). */
    public static final int MAX_LENGTH = PregameMessages.MAX_LENGTH;

    private static final String GAME_START = "Protect your bed and destroy the enemy beds.";
    private static final String COUNTDOWN = "The game starts in 10 seconds";

    public final DropdownSetting sendAt =
            register(new DropdownSetting("Send At", 0, "Game Start", "10s Countdown"));
    public final SliderSetting messageDelay =
            register(new SliderSetting("Message Delay (s)", 1.0, 0.0, 10.0, 0.1));

    // Must stay a public Property<?> field: CoralIntel.init() finds it by reflection.
    public final TextProperty message1 = new TextProperty("snm-1", "");

    private boolean sentThisLobby = false;
    private volatile int generation = 0;

    public SnipeMessages() {
        super("SnipeMessages", true);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!isEnabled() || sentThisLobby) return;
        if (event.getType() != EventType.RECEIVE) return;
        if (!(event.getPacket() instanceof S02PacketChat)) return;

        S02PacketChat packet = (S02PacketChat) event.getPacket();
        if (packet.getType() == 2) return; // action bar

        IChatComponent component = packet.getChatComponent();
        if (component == null) return;

        String text = component.getUnformattedText().trim();
        // Player chat always has "Name:", so a player typing the trigger can't set it off.
        if (text.indexOf(':') >= 0) return;

        boolean hit = sendAt.getIndex() == 0 ? text.contains(GAME_START) : text.startsWith(COUNTDOWN);
        if (!hit) return;

        String raw = message1.getValue();
        String body = raw == null ? "" : raw.trim();
        if (body.isEmpty()) return;
        if (body.length() > MAX_LENGTH) body = body.substring(0, MAX_LENGTH);

        sentThisLobby = true;

        final int gen = ++generation;
        final long delayMs = Math.round(messageDelay.getValue() * 1000.0);
        final String message = "/shout " + body;

        new Thread(() -> {
            if (delayMs > 0) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ignored) {
                    return;
                }
            }

            if (gen != generation) return;

            mc.addScheduledTask(() -> {
                if (gen == generation && mc.thePlayer != null) {
                    mc.thePlayer.sendChatMessage(message);
                }
            });
        }, "CoralIntel-Snipe").start();
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        sentThisLobby = false;
        generation++;
    }
}
