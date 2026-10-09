package coralintel.module.modules;

import coralintel.module.Module;
import coralintel.property.properties.TextProperty;
import net.minecraft.client.Minecraft;

/**
 * Your snipe message, sent to ALL chat with /shout (in-game chat is team-only, so a plain
 * message wouldn't reach everyone). It is never sent automatically: only when you run .sm.
 * Set the text with .sm1 <text> (or in the ClickGUI Chat panel). It is a TextProperty, so
 * Config persists it with the rest of the module settings. The Enabled toggle switches .sm off.
 */
public class SnipeMessages extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    /** Same limit as the pregame messages (the "/shout " prefix is added on top). */
    public static final int MAX_LENGTH = PregameMessages.MAX_LENGTH;

    // Must stay a public Property<?> field: CoralIntel.init() finds it by reflection.
    public final TextProperty message1 = new TextProperty("snm-1", "");

    public SnipeMessages() {
        super("SnipeMessages", true);
    }

    /** Sends the message with /shout now. @return null on success, otherwise why it wasn't sent. */
    public String send() {
        if (!isEnabled()) return "SnipeMessages is turned off. Enable it in the ClickGUI (Chat panel).";
        if (mc.thePlayer == null) return "You're not in a world.";

        String raw = message1.getValue();
        String body = raw == null ? "" : raw.trim();
        if (body.isEmpty()) return "No snipe message set. Use .sm1 <text> first.";
        if (body.length() > MAX_LENGTH) body = body.substring(0, MAX_LENGTH);

        mc.thePlayer.sendChatMessage("/shout " + body);
        return null;
    }
}
