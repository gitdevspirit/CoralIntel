package coralintel.module.modules;

import coralintel.event.EventTarget;
import coralintel.event.types.EventType;
import coralintel.events.LoadWorldEvent;
import coralintel.events.TickEvent;
import coralintel.module.BooleanSetting;
import coralintel.module.Module;
import coralintel.module.SliderSetting;
import coralintel.ui.intel.CheatSuspicion;
import coralintel.ui.intel.IntelManager;
import coralintel.ui.intel.IntelPlayer;
import coralintel.util.ChatUtil;
import coralintel.util.SoundUtil;
import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lobby alerts for the cheater detection engine. Once a player's stats have loaded, their
 * suspicion score is checked every second; at or above the threshold you get one message in your
 * own chat (never sent to the server). They're alerted again only if the score climbs by 20+
 * (for example when AntiCheat catches a new check), so it never spams.
 */
public class CheatEngine extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final SliderSetting alertThreshold =
            register(new SliderSetting("Alert Score", 50, 25, 90, 5));
    public final BooleanSetting soundAlert =
            register(new BooleanSetting("Sound Alert", true));
    public final BooleanSetting showReasons =
            register(new BooleanSetting("Show Reasons", true));

    private final Map<String, Integer> alerted = new HashMap<>();
    private int ticks;

    public CheatEngine() {
        super("CheatEngine", true);
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent e) {
        alerted.clear();
        CheatSuspicion.clearFlags();
        ticks = 0;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (++ticks < 20) return; // once a second is plenty
        ticks = 0;

        String self = mc.thePlayer.getName();
        List<IntelPlayer> players = IntelManager.getInstance().getAllPlayers();
        for (IntelPlayer p : players) {
            if (p == null || p.name == null || p.loading) continue;
            if (p.name.equalsIgnoreCase(self)) continue;

            CheatSuspicion.Result r = CheatSuspicion.analyze(p);
            if (r.score < (int) alertThreshold.getValue()) continue;

            String key = p.name.toLowerCase(Locale.ROOT);
            Integer last = alerted.get(key);
            if (last != null && r.score < last + 20) continue;
            alerted.put(key, r.score);

            ChatUtil.sendFormatted("&8&m   &r " + r.color() + "&lCHEAT ALERT &8\u00bb &f" + p.name
                    + " &8\u2022 " + r.color() + r.score + "&7/100 &8(" + r.color() + r.tier() + "&8) &7\u2022 &f.cheat " + p.name);
            if (showReasons.getValue()) {
                int shown = 0;
                for (String reason : r.reasons) {
                    if (shown++ >= 3) break;
                    ChatUtil.sendFormatted("  &8\u2022 &7" + reason);
                }
            }
            if (soundAlert.getValue()) SoundUtil.playSound("note.pling");
        }
    }
}
