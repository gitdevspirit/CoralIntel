package coralintel.command;

import coralintel.event.EventTarget;
import coralintel.events.PacketEvent;
import coralintel.event.types.EventType;
import coralintel.util.ChatUtil;
import net.minecraft.network.play.client.C01PacketChatMessage;

import java.util.ArrayList;
import java.util.List;

public class CommandManager {

    /** The prefix that triggers commands — change this if you want a different prefix */
    public static final String PREFIX = ".";

    private final List<Command> commands = new ArrayList<>();

    public void register(Command command) {
        commands.add(command);
    }

    public List<Command> getCommands() {
        return commands;
    }

    /**
     * Intercepts outgoing chat packets. If the message starts with the prefix
     * it is parsed as a command and the packet is cancelled so it never reaches the server.
     */
    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() != EventType.SEND) return;
        if (!(event.getPacket() instanceof C01PacketChatMessage)) return;

        C01PacketChatMessage packet = (C01PacketChatMessage) event.getPacket();
        String message = packet.getMessage();

        if (!message.startsWith(PREFIX)) return;

        // Cancel the packet so it is never sent to the server
        event.setCancelled(true);

        // Strip the prefix and split on whitespace
        String raw = message.substring(PREFIX.length()).trim();
        if (raw.isEmpty()) return;

        String[] parts = raw.split("\\s+");
        String commandName = parts[0].toLowerCase();

        // Build args array (everything after the command name)
        String[] args = new String[parts.length - 1];
        System.arraycopy(parts, 1, args, 0, args.length);

        // Find and execute matching command
        for (Command cmd : commands) {
            for (String alias : cmd.getAliases()) {
                if (alias.equalsIgnoreCase(commandName)) {
                    try {
                        cmd.execute(args);
                    } catch (Exception e) {
                        ChatUtil.sendFormatted("&cCommand error: &f" + e.getMessage());
                    }
                    return;
                }
            }
        }

        ChatUtil.sendFormatted("&cUnknown command. Use &f.c help &cfor a list.");
    }
}
