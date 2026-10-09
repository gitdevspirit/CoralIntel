package coralintel.mixin;

import coralintel.CoralIntel;
import coralintel.init.Initializer;
import coralintel.event.EventManager;
import coralintel.events.LoadWorldEvent;
import coralintel.events.TickEvent;
import coralintel.event.types.EventType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Trimmed down from the original client's MixinMinecraft.
 * Only keeps what CoralIntel actually needs:
 *  - bootstraps the mod on startGame()
 *  - fires LoadWorldEvent so LobbyIntel can reset per-lobby state
 *  - fires TickEvent
 *
 * COMPATIBILITY RULES (so CoralIntel never fights another mod over Minecraft):
 *  - Only plain @Inject here. No @Redirect / @Overwrite / @ModifyConstant: only one
 *    mod can win those on the same spot, and the loser silently stops working.
 *  - Every handler is named "coralintel$..." so it can't collide with another mod's.
 *  - Key presses are NOT hooked here any more: see render/KeyEventBridge (Forge's own
 *    key event), which any number of mods can listen to at once.
 */
@SideOnly(Side.CLIENT)
@Mixin(value = {Minecraft.class}, priority = 9999)
public abstract class MixinMinecraft {
    @Shadow
    public PlayerControllerMP playerController;
    @Shadow
    public WorldClient theWorld;
    @Shadow
    public EntityPlayerSP thePlayer;
    @Shadow
    public GuiScreen currentScreen;

    @Inject(
            method = {"startGame"},
            at = {@At("HEAD")}
    )
    private void coralintel$startGame(CallbackInfo callbackInfo) {
        new Initializer();
    }

    @Inject(
            method = {"startGame"},
            at = {@At("RETURN")}
    )
    private void coralintel$postStartGame(CallbackInfo callbackInfo) {
        new CoralIntel();
    }

    @Inject(
            method = {"loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V"},
            at = {@At("HEAD")}
    )
    private void coralintel$loadWorld(WorldClient worldClient, String string, CallbackInfo callbackInfo) {
        EventManager.call(new LoadWorldEvent());
    }

    @Inject(
            method = {"runTick"},
            at = {@At("HEAD")}
    )
    private void coralintel$runTick(CallbackInfo callbackInfo) {
        if (this.theWorld != null && this.thePlayer != null) {
            EventManager.call(new TickEvent(EventType.PRE));
        }
    }

    @Inject(
            method = {"runTick"},
            at = {@At("RETURN")}
    )
    private void coralintel$postRunTick(CallbackInfo callbackInfo) {
        if (this.theWorld != null && this.thePlayer != null) {
            EventManager.call(new TickEvent(EventType.POST));
        }
    }
}
