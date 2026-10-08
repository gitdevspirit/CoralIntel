package coralintel.module.modules;

import coralintel.CoralIntel;
import coralintel.module.BooleanSetting;
import coralintel.module.DropdownSetting;
import coralintel.module.Module;
import coralintel.property.properties.TextProperty;
import coralintel.ui.intel.IntelPlayer;
import net.minecraft.client.Minecraft;

/**
 * Streamer mode: keeps YOUR OWN information off the screen. Everyone else is shown
 * exactly as before.
 *
 *  - Hide Own Stats : no stats on your tab row, your Intel HUD row, or your floating tag.
 *  - Hide Own Tags  : no cheater / [NICK] badge on any of those.
 *  - Own Nametag    : Normal / Custom (shows the text below instead of your IGN on your
 *                     floating tag and tab row) / Disabled (no floating tag for you).
 *
 * Purely visual: nothing is fetched differently and nothing is sent to the server.
 */
public class StreamerMode extends Module {

    public final BooleanSetting hideStats =
            register(new BooleanSetting("Hide Own Stats", true));
    public final BooleanSetting hideTags =
            register(new BooleanSetting("Hide Own Tags", true));
    public final DropdownSetting nametagMode =
            register(new DropdownSetting("Own Nametag", 0, new String[]{"Normal", "Custom", "Disabled"}));

    /** Edited from the ClickGUI (text box under the settings). */
    public final TextProperty nametagText = new TextProperty("streamer-nametag", "Streamer");

    public StreamerMode() {
        super("StreamerMode", false);
    }

    private static StreamerMode get() {
        if (CoralIntel.moduleManager == null) return null;
        Module module = CoralIntel.moduleManager.getModule(StreamerMode.class);
        return module instanceof StreamerMode ? (StreamerMode) module : null;
    }

    public static boolean isSelf(String name) {
        Minecraft mc = Minecraft.getMinecraft();
        return name != null && mc.thePlayer != null && name.equalsIgnoreCase(mc.thePlayer.getName());
    }

    private static boolean activeFor(String name) {
        StreamerMode mode = get();
        return mode != null && mode.isEnabled() && isSelf(name);
    }

    /** True when this name is you and your stats must not be shown. */
    public static boolean hidesStatsFor(String name) {
        return activeFor(name) && get().hideStats.getValue();
    }

    /** True when this name is you and your tags must not be shown. */
    public static boolean hidesTagsFor(String name) {
        return activeFor(name) && get().hideTags.getValue();
    }

    /** True when your floating nametag must not be drawn at all. */
    public static boolean disablesOwnNametag() {
        StreamerMode mode = get();
        return mode != null && mode.isEnabled() && mode.nametagMode.getIndex() == 2;
    }

    /** The tag badge to show for this player — empty when it is you and tags are hidden. */
    public static String badgeFor(IntelPlayer player) {
        if (player == null) return "";
        return hidesTagsFor(player.name) ? "" : player.getTagBadge();
    }

    /** Your IGN, or the custom text when Own Nametag is "Custom". Other names pass through. */
    public static String aliasFor(String name) {
        StreamerMode mode = get();
        if (mode == null || !mode.isEnabled() || !isSelf(name) || mode.nametagMode.getIndex() != 1) {
            return name;
        }

        String text = mode.nametagText.getValue();
        return text == null || text.trim().isEmpty() ? name : text;
    }
}
