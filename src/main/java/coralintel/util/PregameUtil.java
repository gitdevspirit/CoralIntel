package coralintel.util;

import net.minecraft.client.Minecraft;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.StringUtils;

import java.util.Collection;
import java.util.Locale;

/**
 * Detects whether the player is currently in a Bedwars PREGAME lobby (the
 * waiting lobby before a match), as opposed to the general Bedwars lobby or
 * an active match.
 *
 * Hypixel's pregame sidebar has both a "Map:" line and a "Players: x/y" line.
 * The general lobby sidebar has neither, and the in-match sidebar has no
 * "Map:" / "Players:" lines, so requiring both is a reliable pregame check.
 */
public final class PregameUtil {
    private PregameUtil() {
    }

    public static boolean isPregameLobby() {
        Minecraft mc = Minecraft.getMinecraft();

        if (mc.theWorld == null) {
            return false;
        }

        Scoreboard scoreboard = mc.theWorld.getScoreboard();
        if (scoreboard == null) {
            return false;
        }

        ScoreObjective sidebar = scoreboard.getObjectiveInDisplaySlot(1);
        if (sidebar == null) {
            return false;
        }

        boolean hasMap = false;
        boolean hasPlayers = false;

        Collection<Score> scores = scoreboard.getSortedScores(sidebar);
        for (Score score : scores) {
            String owner = score.getPlayerName();
            if (owner == null) {
                continue;
            }

            String raw = ScorePlayerTeam.formatPlayerName(scoreboard.getPlayersTeam(owner), owner);
            String line = StringUtils.stripControlCodes(raw).trim().toLowerCase(Locale.ROOT);

            if (line.startsWith("map:")) {
                hasMap = true;
            } else if (line.startsWith("players:")) {
                hasPlayers = true;
            }
        }

        return hasMap && hasPlayers;
    }
}
