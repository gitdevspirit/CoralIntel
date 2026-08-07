package coralintel.mixin;

import coralintel.CoralIntel;
import coralintel.module.modules.BedwarsTag;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Suppresses vanilla's built-in floating nametag for players while
 * BedWarsTag is on — otherwise you'd see both the vanilla nametag and
 * BedWarsTag's own star/name/health/FKDR tag stacked on top of each other.
 * Trimmed from the original client's version, which also handled its
 * NameTags and ESP modules; neither of those exist in this standalone build.
 */
@SideOnly(Side.CLIENT)
@Mixin(
        value = {RendererLivingEntity.class},
        priority = 9991
)
public abstract class MixinRendererLivingEntity<T extends EntityLivingBase> extends Render<T> {
    protected MixinRendererLivingEntity(RenderManager renderManager) {
        super(renderManager);
    }

    @Inject(
            method = {"canRenderName(Lnet/minecraft/entity/EntityLivingBase;)Z"},
            at = {@At("HEAD")},
            cancellable = true
    )
    private void canRenderName(T entityLivingBase, CallbackInfoReturnable<Boolean> callbackInfoReturnable) {
        if (CoralIntel.moduleManager == null) return;
        if (!(entityLivingBase instanceof EntityPlayer)) return;

        BedwarsTag bedwarsTag = (BedwarsTag) CoralIntel.moduleManager.getModule(BedwarsTag.class);

        // Only suppress the vanilla nametag when BedwarsTag will actually
        // draw its own replacement for THIS specific player this frame —
        // matching its exact per-player skip conditions (self/dead/distance/
        // intel-only), not just "is the module on". Otherwise a player
        // skipped by one of those filters would end up with no nametag at
        // all: vanilla suppressed unconditionally, custom one never drawn.
        if (bedwarsTag != null && bedwarsTag.willRenderTagFor((EntityPlayer) entityLivingBase)) {
            callbackInfoReturnable.setReturnValue(false);
        }
    }
}
