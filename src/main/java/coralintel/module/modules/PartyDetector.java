package coralintel.module.modules;

import coralintel.event.EventTarget;
import coralintel.event.types.EventType;
import coralintel.events.PacketEvent;
import coralintel.module.Module;
import coralintel.module.SliderSetting;
import coralintel.property.properties.TextProperty;
import coralintel.util.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.util.IChatComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Party detection. Watches the "<Name> joined the party." lines and, when enough
 * players join at the same time, prints a message to YOUR chat only. Nothing is ever
 * sent to the server, so it never reaches party or all chat.
 *
 *  - Players To Trigger : how many must join within the time window (1 = every join).
 *  - Within (seconds)   : the window those joins have to fall in.
 *  - Message            : what is shown; {count} and {players} are filled in.
 *
 * After it fires, the count starts over, so the next burst needs a fresh set of joins.
 */
public class PartyDetector extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final SliderSetting playersToTrigger =
            register(new SliderSetting("Players To Trigger", 2, 1, 10, 1));
    public final SliderSetting windowSeconds =
            register(new SliderSetting("Within (seconds)", 3, 1, 10, 1));

    // Must stay a public Property<?> field: CoralIntel.init() finds it by reflection.
    public final TextProperty message =
            new TextProperty("party-message", "&d&lParty &7\u00bb &f{count} players joined at once: &d{players}");

    private static final Pattern CODES = Pattern.compile("\u00a7.");
    // "[MVP+] Name joined the party."  /  "Name joined the party."
    private static final Pattern JOINED = Pattern.compile(
            "^(?:\\[[^\\]]+\\]\\s*)?(\\w{1,16}) joined the party\\.$");

    private final Object lock = new Object();
    private final List<Long> times = new ArrayList<>();
    private final List<String> names = new ArrayList<>();

    public PartyDetector() {
        super("PartyDetector", true);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!isEnabled()) return;
        if (event.getType() != EventType.RECEIVE) return;
        if (!(event.getPacket() instanceof S02PacketChat)) return;

        S02PacketChat packet = (S02PacketChat) event.getPacket();
        if (packet.getType() == 2) return; // action bar

        IChatComponent component = packet.getChatComponent();
        if (component == null) return;

        // Player chat always has "Name:"; party system lines never do, so nobody can
        // fake a join by typing it.
        String plain = CODES.matcher(component.getFormattedText()).replaceAll("").trim();
        if (plain.indexOf(':') >= 0) return;

        Matcher m = JOINED.matcher(plain);
        if (!m.matches()) return;

        onJoin(m.group(1));
    }

    private void onJoin(String name) {
        long now = System.currentTimeMillis();
        long window = (long) (windowSeconds.getValue() * 1000.0);
        List<String> fired = null;

        synchronized (lock) {
            for (int i = times.size() - 1; i >= 0; i--) {
                if (now - times.get(i) > window) {
                    times.remove(i);
                    names.remove(i);
                }
            }

            // The same player can't count twice for one burst.
            if (!names.contains(name)) {
                times.add(now);
                names.add(name);
            }

            if (names.size() >= (int) playersToTrigger.getValue()) {
                fired = new ArrayList<>(names);
                times.clear();
                names.clear();
            }
        }

        if (fired != null) {
            final String text = build(fired);
            mc.addScheduledTask(() -> ChatUtil.sendFormatted(text));
        }
    }

    private String build(List<String> players) {
        String template = message.getValue();
        if (template == null || template.trim().isEmpty()) template = "&d{count} players joined: {players}";

        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < players.size(); i++) {
            if (i > 0) joined.append("&7, &d");
            joined.append(players.get(i));
        }
        return template
                .replace("{count}", String.valueOf(players.size()))
                .replace("{players}", joined.toString());
    }
}
