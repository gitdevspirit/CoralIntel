package coralintel.render;

import coralintel.event.EventManager;
import coralintel.events.KeyEvent;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;

/**
 * Bridges Forge's KeyInputEvent into our KeyEvent.
 * Replaces the old @Redirect on Minecraft.runTick's setKeyBindState call, which
 * clashed with any other mod (e.g. Spirit Visuals) redirecting the same call.
 */
public class KeyInputBridge {
    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (Keyboard.getEventKeyState() && Minecraft.getMinecraft().currentScreen == null) {
            int key = Keyboard.getEventKey();
            if (key == 0) key = Keyboard.getEventCharacter() + 256;
            EventManager.call(new KeyEvent(key));
        }
    }
}
