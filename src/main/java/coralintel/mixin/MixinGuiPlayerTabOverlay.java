package coralintel.mixin;

import coralintel.CoralIntel;
import coralintel.module.modules.LobbyIntel;
import coralintel.ui.intel.IntelManager;
import coralintel.ui.intel.IntelPlayer;
import net.minecraft.client.gui.GuiPlayerTabOverlay;
import net.minecraft.client.network.NetworkPlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Adds LobbyIntel's cached stats to the vanilla tab list without replacing it. */
@Mixin(GuiPlayerTabOverlay.class)
public abstract class MixinGuiPlayerTabOverlay {

    @Shadow public abstract String getPlayerName(NetworkPlayerInfo networkPlayerInfoIn);

    @Redirect(
            method = "renderPlayerlist",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiPlayerTabOverlay;getPlayerName(Lnet/minecraft/client/network/NetworkPlayerInfo;)Ljava/lang/String;"
            )
    )
    private String appendLobbyIntelStats(GuiPlayerTabOverlay overlay, NetworkPlayerInfo info) {
        String vanillaName = this.getPlayerName(info);

        LobbyIntel lobbyIntel = (LobbyIntel) CoralIntel.moduleManager.getModule(LobbyIntel.class);
        if (lobbyIntel == null || !lobbyIntel.tabStats.getValue()) {
            return vanillaName;
        }

        IntelPlayer player = IntelManager.getInstance()
                .getPlayer(info.getGameProfile().getName());

        if (player == null || player.loading) {
            return vanillaName;
        }

        String starCode = coralintel.ui.intel.IntelColors.nearestCode(
                coralintel.ui.intel.IntelColors.getPrestigeColor(player.star));
        String fkdrCode = coralintel.ui.intel.IntelColors.nearestCode(
                coralintel.ui.intel.IntelColors.getStatColor(player.fkdr, 3, 6));
        String wlrCode = coralintel.ui.intel.IntelColors.nearestCode(
                coralintel.ui.intel.IntelColors.getStatColor(player.wlr, 2, 4));

        StringBuilder stats = new StringBuilder(" §8| ").append(starCode).append("\u272A")
                .append(player.star)
                .append(" §7FKDR ").append(fkdrCode)
                .append(String.format(java.util.Locale.ROOT, "%.1f", player.fkdr))
                .append(" §7WLR ").append(wlrCode)
                .append(String.format(java.util.Locale.ROOT, "%.1f", player.wlr));

        String tag = player.getTagBadge();
        if (!tag.isEmpty() && lobbyIntel.tabShowTag.getValue()) {
            // Closet cheater specifically renders gold in the tab list;
            // everything else uses the nearest code to its usual color.
            String tagCode = tag.equals("C") ? "§6"
                    : coralintel.ui.intel.IntelColors.nearestCode(player.getTagColor());
            stats.append(" ").append(tagCode).append(tag);
        }

        return vanillaName + stats;
    }
}
