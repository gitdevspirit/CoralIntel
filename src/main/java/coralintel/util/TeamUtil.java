package coralintel.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.ScorePlayerTeam;

import java.awt.*;

/**
 * Trimmed from the original client's TeamUtil — only keeps getTeamColor()
 * and isSameTeam(), which is all BedwarsTag/AntiCheat need. The friend/
 * target/bot/shop detection helpers from the original (tied to combat
 * modules) have been left out.
 */
public class TeamUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static Color getTeamColor(EntityPlayer player, float alpha) {
        int colorCode = 0xFFFFFF;
        ScorePlayerTeam playerTeam = (ScorePlayerTeam) player.getTeam();
        if (playerTeam != null) {
            String colorPrefix = FontRenderer.getFormatFromString(playerTeam.getColorPrefix());
            if (colorPrefix.length() >= 2) {
                colorCode = mc.fontRendererObj.getColorCode(colorPrefix.charAt(1));
            }
        }
        return new Color(colorCode & 0xFFFFFF | (int) (alpha * 255) << 24, true);
    }

    public static boolean isSameTeam(EntityPlayer player) {
        if (player == mc.thePlayer) {
            return true;
        }
        if (mc.getNetHandler() == null || mc.thePlayer == null) {
            return false;
        }
        NetworkPlayerInfo selfInfo = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
        if (selfInfo == null) {
            return false;
        }
        ScorePlayerTeam selfTeam = selfInfo.getPlayerTeam();
        if (selfTeam == null) {
            return false;
        }
        NetworkPlayerInfo targetInfo = mc.getNetHandler().getPlayerInfo(player.getUniqueID());
        if (targetInfo == null) {
            return false;
        }
        ScorePlayerTeam targetTeam = targetInfo.getPlayerTeam();
        if (targetTeam == null) {
            return false;
        }
        return selfTeam.getColorPrefix().equals(targetTeam.getColorPrefix());
    }
}
