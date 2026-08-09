package coralintel.mixin;

import coralintel.CoralIntel;
import coralintel.module.modules.LobbyIntel;
import coralintel.ui.intel.IntelColors;
import coralintel.ui.intel.IntelManager;
import coralintel.ui.intel.IntelPlayer;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiPlayerTabOverlay;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.scoreboard.ScorePlayerTeam;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Locale;

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

        // Team color in an active BedWars match; otherwise leave the rank
        // color the server already sent (lobby state — no team assigned).
        String coloredName = applyTeamColor(vanillaName, info);

        IntelPlayer player = IntelManager.getInstance()
                .getPlayer(info.getGameProfile().getName());

        if (player == null || player.loading) {
            return coloredName;
        }

        String stats = lobbyIntel.seraphStyle.getValue()
                ? buildSeraphStatsSuffix(info, player)
                : buildStatsSuffix(lobbyIntel, player);
        return coloredName + stats;
    }

    /**
     * Builds the " §8| ..." stats suffix for the tab list, field-by-field,
     * driven by the same per-field toggles the .bw command uses (cloned
     * onto the module as tabShow* settings) — so the tab list can show
     * exactly the same set of fields as .bw, independently configured.
     */
    private String buildStatsSuffix(LobbyIntel intel, IntelPlayer player) {
        StringBuilder stats = new StringBuilder();
        boolean wroteAny = false;

        if (intel.tabShowStar.getValue()) {
            String starCode = IntelColors.nearestCode(IntelColors.getPrestigeColor(player.star));
            stats.append(starCode).append("\u272A").append(player.star).append(" ");
            wroteAny = true;
        }
        if (intel.tabShowFkdr.getValue()) {
            String fkdrCode = IntelColors.nearestCode(IntelColors.getStatColor(player.fkdr, 3, 6));
            stats.append("§7FKDR ").append(fkdrCode).append(fmt(player.fkdr)).append(" ");
            wroteAny = true;
        }
        if (intel.tabShowWlr.getValue()) {
            String wlrCode = IntelColors.nearestCode(IntelColors.getStatColor(player.wlr, 2, 4));
            stats.append("§7WLR ").append(wlrCode).append(fmt(player.wlr)).append(" ");
            wroteAny = true;
        }
        if (intel.tabShowBblr.getValue()) {
            double bblr = player.bedsLost == 0
                    ? player.bedsBroken
                    : (double) player.bedsBroken / player.bedsLost;
            stats.append("§7BBLR §f").append(fmt(bblr)).append(" ");
            wroteAny = true;
        }
        if (intel.tabShowFinalKills.getValue()) {
            stats.append("§7FK §f").append(player.finalKills).append(" ");
            wroteAny = true;
        }
        if (intel.tabShowFinalDeaths.getValue()) {
            stats.append("§7FD §f").append(player.finalDeaths).append(" ");
            wroteAny = true;
        }
        if (intel.tabShowKills.getValue()) {
            stats.append("§7K §f").append(player.kills).append(" ");
            wroteAny = true;
        }
        if (intel.tabShowDeaths.getValue()) {
            stats.append("§7D §f").append(player.deaths).append(" ");
            wroteAny = true;
        }
        if (intel.tabShowBedsBroken.getValue()) {
            stats.append("§7Beds §f").append(player.bedsBroken).append(" ");
            wroteAny = true;
        }
        if (intel.tabShowBedsLost.getValue()) {
            stats.append("§7BedsL §f").append(player.bedsLost).append(" ");
            wroteAny = true;
        }
        if (intel.tabShowWinstreak.getValue()) {
            stats.append("§7WS §f").append(player.winstreak).append(" ");
            wroteAny = true;
        }
        if (intel.tabShowWins.getValue()) {
            stats.append("§7Wins §f").append(player.wins).append(" ");
            wroteAny = true;
        }
        if (intel.tabShowLosses.getValue()) {
            stats.append("§7Losses §f").append(player.losses).append(" ");
            wroteAny = true;
        }

        String tag = player.getTagBadge();
        if (!tag.isEmpty() && intel.tabShowTag.getValue()) {
            // Closet cheater specifically renders gold in the tab list;
            // everything else uses the nearest code to its usual color.
            String tagCode = tag.equals("C") ? "§6" : IntelColors.nearestCode(player.getTagColor());
            stats.append(tagCode).append(tag).append(" ");
            wroteAny = true;
        }

        if (!wroteAny) {
            return "";
        }

        return " §8| " + stats.toString().trim();
    }

    /**
     * "Seraph Style" — a padded, column-like layout similar to the reference
     * client's tab list (Stars/HP/FKDR/etc. lined up as a grid), with our
     * own tag badge substituted for its verification column. True pixel-
     * perfect alignment isn't possible from a single per-player redirect
     * like this (would need two passes across the whole roster), but digits
     * are fixed-width in Minecraft's default font, so right-padding each
     * numeric field to a fixed character count gets close in practice.
     */
    private String buildSeraphStatsSuffix(NetworkPlayerInfo info, IntelPlayer player) {
        StringBuilder stats = new StringBuilder(" §8| ");

        String hpStr = "-";
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        if (mc.theWorld != null && info.getGameProfile().getId() != null) {
            net.minecraft.entity.player.EntityPlayer entity =
                    mc.theWorld.getPlayerEntityByUUID(info.getGameProfile().getId());
            if (entity != null) {
                hpStr = String.valueOf((int) Math.ceil(entity.getHealth()));
            }
        }
        stats.append("§7HP §f").append(padPixels(hpStr, 18)).append(" ");

        String starCode = IntelColors.nearestCode(IntelColors.getPrestigeColor(player.star));
        stats.append(starCode).append("\u272A").append(padPixels(String.valueOf(player.star), 24)).append(" ");

        String fkdrCode = IntelColors.nearestCode(IntelColors.getStatColor(player.fkdr, 3, 6));
        stats.append("§7FKDR ").append(fkdrCode).append(padPixels(fmt(player.fkdr), 28)).append(" ");

        String wlrCode = IntelColors.nearestCode(IntelColors.getStatColor(player.wlr, 2, 4));
        stats.append("§7WLR ").append(wlrCode).append(padPixels(fmt(player.wlr), 28)).append(" ");

        String tag = player.getTagBadge();
        String tagCode = tag.isEmpty() ? "§7" : (tag.equals("C") ? "§6" : IntelColors.nearestCode(player.getTagColor()));
        stats.append("§7Tags ").append(tagCode).append(padPixels(tag.isEmpty() ? "-" : tag, 18));

        return stats.toString();
    }

    /** Right-aligns a string within a fixed character width by left-padding with spaces. */
    /**
     * Right-aligns text within a fixed PIXEL width, not a character count.
     * Minecraft's default font isn't monospace — even digits aside, the
     * space character itself is narrower than a digit, and "-" is narrower
     * still. Padding by character count (the old approach) meant every row
     * with a different mix of those characters actually landed at a
     * different real pixel offset, which is exactly the ragged/misaligned
     * look in the reference screenshot. Measuring actual string width and
     * padding with however many real spaces are needed to reach a target
     * pixel width fixes it regardless of what characters appear.
     */
    private String padPixels(String text, int targetPixelWidth) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        int spaceWidth = mc.fontRendererObj.getCharWidth(' ');
        if (spaceWidth <= 0) spaceWidth = 4;

        int deficit = targetPixelWidth - mc.fontRendererObj.getStringWidth(text);
        if (deficit <= 0) return text;

        int spaces = deficit / spaceWidth;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < spaces; i++) sb.append(' ');
        sb.append(text);
        return sb.toString();
    }

    /**
     * When a scoreboard team is assigned (an active BedWars match), strips
     * any leading color code from the name and prepends the team's color
     * instead. In the lobby, no team is assigned yet, so the name is left
     * exactly as the server sent it (its normal rank color).
     */
    private String applyTeamColor(String vanillaName, NetworkPlayerInfo info) {
        ScorePlayerTeam team = info.getPlayerTeam();
        if (team == null) {
            return vanillaName;
        }

        String colorPrefix = FontRenderer.getFormatFromString(team.getColorPrefix());
        if (colorPrefix.length() < 2) {
            return vanillaName;
        }

        char colorChar = colorPrefix.charAt(1);
        String stripped = vanillaName.replaceFirst("^(§[0-9a-fk-or])+", "");
        return "§" + colorChar + stripped;
    }

    private String fmt(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
