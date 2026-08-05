package coralintel.render;

import coralintel.event.EventManager;
import coralintel.events.Render3DEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Bridges Forge's RenderWorldLastEvent into our Render3DEvent.
 * RenderWorldLastEvent fires at the end of world rendering with the
 * 3D GL matrix still active — exactly what BedwarsTag needs to draw
 * its floating stat tag above player heads.
 */
public class RenderEventBridge {
    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        EventManager.call(new Render3DEvent(event.partialTicks));
    }
}
