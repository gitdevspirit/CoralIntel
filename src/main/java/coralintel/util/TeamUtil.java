package coralintel.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.ScorePlayerTeam;

import java.awt.*;

/**
 * Trimmed from the original client's TeamUtil — only keeps getTeamColor(),
 * which is all BedwarsTag needs. The friend/target/bot/shop detection helpers
 * from the original (tied to combat modules) have been left out.
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
}
