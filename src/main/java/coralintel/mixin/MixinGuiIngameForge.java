package coralintel.mixin;

import coralintel.event.EventManager;
import coralintel.events.Render2DEvent;
import net.minecraftforge.client.GuiIngameForge;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Trimmed down from the original client's MixinGuiIngameForge.
 * Only fires Render2DEvent (used by IntelHudOverlay to draw the lobby-intel HUD).
 * The NickHider-related experience bar redirects from the original have been removed.
 */
@SideOnly(Side.CLIENT)
@Mixin(value = {GuiIngameForge.class}, priority = 9999)
public abstract class MixinGuiIngameForge {
    @Inject(
            method = {"renderGameOverlay"},
            at = {@At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/client/GuiIngameForge;renderTitle(IIF)V",
                    shift = At.Shift.AFTER,
                    remap = false
            )}
    )
    private void renderGameOverlay(float partialTicks, CallbackInfo callbackInfo) {
        EventManager.call(new Render2DEvent(partialTicks));
    }
}
