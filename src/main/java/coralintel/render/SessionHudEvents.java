package coralintel.render;

import coralintel.CoralIntel;
import coralintel.module.Module;
import coralintel.module.modules.SessionStats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Mouse;

/**
 * Forge GUI events for the session HUD: draws it (with the [Reset Session]
 * button) on top of the inventory screen, and handles clicking the button and
 * dragging the box. Does nothing on any other screen.
 */
public class SessionHudEvents {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private static SessionStats module() {
        if (CoralIntel.moduleManager == null) return null;
        Module module = CoralIntel.moduleManager.getModule(SessionStats.class);
        return module instanceof SessionStats ? (SessionStats) module : null;
    }

    @SubscribeEvent
    public void onDrawScreen(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.gui instanceof GuiInventory)) return;

        SessionStats stats = module();
        if (stats != null) {
            stats.drawHud(true, event.mouseX, event.mouseY);
        }
    }

    @SubscribeEvent
    public void onMouse(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (!(event.gui instanceof GuiInventory)) return;

        SessionStats stats = module();
        if (stats == null || !stats.isEnabled()) return;

        int x = Mouse.getEventX() * event.gui.width / mc.displayWidth;
        int y = event.gui.height - Mouse.getEventY() * event.gui.height / mc.displayHeight - 1;
        int button = Mouse.getEventButton();

        if (button == 0 && Mouse.getEventButtonState()) {
            if (stats.onInventoryPress(x, y)) {
                event.setCanceled(true);
            }
        } else if (button == 0) {
            if (stats.onInventoryRelease()) {
                event.setCanceled(true);
            }
        } else if (button == -1) {
            if (stats.onInventoryDrag(x, y)) {
                event.setCanceled(true);
            }
        }
    }
}
