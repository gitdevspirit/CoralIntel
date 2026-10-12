package coralintel.script;

import coralintel.event.EventTarget;
import coralintel.event.types.EventType;
import coralintel.events.LoadWorldEvent;
import coralintel.events.PacketEvent;
import coralintel.events.Render2DEvent;
import coralintel.events.TickEvent;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.network.play.server.S45PacketTitle;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StringUtils;

/**
 * Feeds game events to the ScriptManager. Chat and title packets arrive on the network thread,
 * so they are only copied into the manager's queue here; scripts run later on the main thread.
 * Nothing here can change or cancel a packet.
 */
public final class ScriptEvents {
    private final ScriptManager manager;

    public ScriptEvents(ScriptManager manager) {
        this.manager = manager;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() == EventType.PRE) manager.onTick();
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        manager.queueWorldLoad();
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        manager.renderHud();
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() != EventType.RECEIVE) return;
        try {
            Packet<?> packet = event.getPacket();
            if (packet instanceof S02PacketChat) {
                S02PacketChat chat = (S02PacketChat) packet;
                if (chat.getType() == 2) return; // action bar, not chat
                IChatComponent component = chat.getChatComponent();
                if (component == null) return;
                String formatted = component.getFormattedText();
                manager.queueChat(StringUtils.stripControlCodes(formatted), formatted);
            } else if (packet instanceof S45PacketTitle) {
                S45PacketTitle title = (S45PacketTitle) packet;
                IChatComponent message = title.getMessage();
                if (message == null) return;
                S45PacketTitle.Type type = title.getType();
                if (type == S45PacketTitle.Type.TITLE) {
                    manager.queueTitle("title", StringUtils.stripControlCodes(message.getFormattedText()));
                } else if (type == S45PacketTitle.Type.SUBTITLE) {
                    manager.queueTitle("subtitle", StringUtils.stripControlCodes(message.getFormattedText()));
                }
            }
        } catch (Throwable ignored) {
            // never let a script feature break packet handling
        }
    }
}
