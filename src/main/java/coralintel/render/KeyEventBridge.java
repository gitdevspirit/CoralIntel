package coralintel.render;

import coralintel.CoralIntel;
import coralintel.event.EventManager;
import coralintel.events.KeyEvent;
import coralintel.module.modules.LobbyIntel;
import coralintel.util.ChatUtil;
import coralintel.util.KeyBindUtil;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

/**
 * Turns Forge's own key event into CoralIntel's KeyEvent.
 *
 * This used to be a Mixin @Redirect on Minecraft.runTick, but only ONE mod can redirect a
 * given call: when two mods did it, the one that lost never got any key presses, so its
 * keybinds (and the GUIs they open) silently died. Forge's event is shared by every mod,
 * so nothing here can conflict.
 *
 * Same behaviour as before: a key press with no screen open.
 *
 * If another mod uses the same key for its own GUI (both default to Right Shift, for
 * example), the later one to open wins the screen. Instead of leaving you guessing, a
 * one-time chat hint explains it and how to rebind.
 */
public class KeyEventBridge {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private int pendingKey = 0;
    private int pendingTicks = 0;
    private boolean warned = false;

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (!Keyboard.getEventKeyState()) return;     // presses only, not releases
        if (mc.currentScreen != null) return;          // same as before: only with no screen open

        int key = Keyboard.getEventKey();
        if (key == Keyboard.KEY_NONE) return;

        EventManager.call(new KeyEvent(key));

        pendingKey = key;
        pendingTicks = 2; // let the other mods' handlers for this same key run first
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || pendingKey == 0) return;
        if (--pendingTicks > 0) return;

        int key = pendingKey;
        pendingKey = 0;

        if (warned || mc.currentScreen == null) return;
        if (mc.currentScreen.getClass().getName().startsWith("coralintel.")) return; // our own GUI opened fine

        if (CoralIntel.moduleManager == null) return;
        if (!(CoralIntel.moduleManager.getModule(LobbyIntel.class) instanceof LobbyIntel)) return;
        LobbyIntel intel = (LobbyIntel) CoralIntel.moduleManager.getModule(LobbyIntel.class);

        if (key == intel.clickGuiKeybind.getKeyCode() || key == intel.guiKeybind.getKeyCode()) {
            warned = true;
            ChatUtil.sendFormatted(CoralIntel.clientName + "&e" + KeyBindUtil.getKeyName(key)
                    + " &7also opened another mod's screen, so it won. Give CoralIntel its own key with &f.bind clickgui <key> &7(ClickGUI) or &f.bind gui <key>&7. &f.clickgui &7always works.");
        }
    }
}
