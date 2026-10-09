package coralintel.mixin;

import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The method names carry a "coralintel$" prefix on purpose: another mod can add its own
 * accessor for the same field to RenderManager, and two accessors with the same method
 * name would collide. The explicit field name keeps them pointing at the right field.
 */
@SideOnly(Side.CLIENT)
@Mixin({RenderManager.class})
public interface IAccessorRenderManager {
    @Accessor("renderPosX")
    double coralintel$getRenderPosX();

    @Accessor("renderPosY")
    double coralintel$getRenderPosY();

    @Accessor("renderPosZ")
    double coralintel$getRenderPosZ();
}
