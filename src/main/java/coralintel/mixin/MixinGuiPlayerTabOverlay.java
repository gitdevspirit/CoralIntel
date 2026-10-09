package coralintel.mixin;

import coralintel.util.TabListFormatter;
import net.minecraft.client.gui.GuiPlayerTabOverlay;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.util.IChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds LobbyIntel's cached stats to the vanilla tab list without replacing it.
 *
 * Built so another tab-list mod (or a future CoralIntel update) can never fight it:
 *  - no @Redirect / @Overwrite: two mods can't both redirect the same call, but any number of
 *    @Inject / @ModifyArg / @ModifyVariable handlers stack on top of each other;
 *  - every handler is named "coralintel$..." and holds no logic of its own, it only calls
 *    TabListFormatter (a normal class), so nothing gets merged into the game class that
 *    could share a name with another mod's code.
 */
@Mixin(GuiPlayerTabOverlay.class)
public abstract class MixinGuiPlayerTabOverlay {

    /** Per-row background colour (the 5th argument of the row's drawRect call). */
    @ModifyArg(
            method = "renderPlayerlist",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Gui;drawRect(IIIII)V"
            ),
            index = 4
    )
    private int coralintel$tabBackground(int color) {
        return TabListFormatter.tabBackground(color);
    }

    /** Seraph-style column titles in the tab header. */
    @ModifyVariable(method = "setHeader", at = @At("HEAD"), argsOnly = true)
    private IChatComponent coralintel$seraphHeader(IChatComponent header) {
        return TabListFormatter.seraphHeader(header);
    }

    /**
     * Stats / tags next to each player's name. Hooking the end of getPlayerName itself (instead of
     * redirecting its calls inside renderPlayerlist) covers every place the name is measured or
     * drawn, and lets other mods adjust the same name before or after us.
     */
    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void coralintel$decorateName(NetworkPlayerInfo info, CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(TabListFormatter.decorateName(cir.getReturnValue(), info));
    }
}
