package coralintel.command.commands;

import coralintel.command.Command;
import coralintel.ui.intel.IntelDebugGui;
import coralintel.ui.intel.IntelManager;
import net.minecraft.client.Minecraft;

public class IntelDebugCommand extends Command {

    public IntelDebugCommand() {
        super("inteldebug", "idebug");
        setDescription("Debug intel fetch. .idebug <name> | .idebug log | .idebug clear");
    }

    @Override
    public void execute(String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("clear")) {
            synchronized (IntelManager.debugLog) { IntelManager.debugLog.clear(); }
            reply("&aDebug log cleared.");
            return;
        }

        if (args.length > 0 && !args[0].equalsIgnoreCase("log")) {
            // Trigger fetch for named player, then show log after 5s
            String name = args[0];
            synchronized (IntelManager.debugLog) { IntelManager.debugLog.clear(); }
            IntelManager.dbg("[Debug] triggered for: " + name);
            reply("&7Fetching &f" + name + "&7... opening log in 5s");
            IntelManager.getInstance().addManualPlayer(name);
            new Thread(() -> {
                try { Thread.sleep(5000); } catch (Exception ignored) {}
                showLog();
            }).start();
            return;
        }

        showLog();
    }

    private void showLog() {
        synchronized (IntelManager.debugLog) {
            String content = IntelManager.debugLog.isEmpty()
                    ? "No debug output yet.\nTry: .idebug <playername>"
                    : String.join("\n", IntelManager.debugLog);
            Minecraft.getMinecraft().addScheduledTask(() ->
                    Minecraft.getMinecraft().displayGuiScreen(
                            new IntelDebugGui("Intel Debug Log", content)));
        }
    }
}
