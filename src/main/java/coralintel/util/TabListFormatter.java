package coralintel.util;

import coralintel.CoralIntel;
import coralintel.module.modules.LobbyIntel;
import coralintel.ui.intel.IntelColors;
import coralintel.ui.intel.IntelManager;
import coralintel.ui.intel.IntelPlayer;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

import java.util.Locale;

/**
 * All of the tab-list formatting logic. It lives here, not in the mixin, on purpose: a mixin
 * class gets its methods merged into Minecraft's GuiPlayerTabOverlay, where a helper with the
 * same name as another mod's helper would collide. Plain static methods in our own class can't.
 * The mixin (MixinGuiPlayerTabOverlay) is only a thin set of entry points into this class.
 */
public final class TabListFormatter {
    private TabListFormatter() {
    }

    /** Colour for each tab row's background (vanilla's is a hardcoded translucent black). */
    public static int tabBackground(int color) {
        LobbyIntel lobbyIntel = (LobbyIntel) CoralIntel.moduleManager.getModule(LobbyIntel.class);

        if (lobbyIntel != null && lobbyIntel.tabStats.getValue()) {
            int rgb = LobbyIntel.TAB_BG_PALETTE[lobbyIntel.tabBgColorChoice.getIndex()];
            int alpha = (int) lobbyIntel.tabBgOpacity.getValue();
            return (alpha << 24) | rgb;
        }

        return color;
    }

    /**
     * Hands the server's tab header to the Bed Wars overlay (it draws it itself) and leaves the
     * vanilla header untouched.
     */
    public static IChatComponent seraphHeader(IChatComponent header) {
        coralintel.ui.tab.TabOverlay.noteHeader(header);
        return header;
    }

    /** The tab-list line for one player: vanilla's name plus CoralIntel's stats, tags and colours. */
    public static String decorateName(String vanillaName, NetworkPlayerInfo info) {
        LobbyIntel lobbyIntel = (LobbyIntel) CoralIntel.moduleManager.getModule(LobbyIntel.class);
        if (lobbyIntel == null || !lobbyIntel.tabStats.getValue()) {
            return vanillaName;
        }

        // Team color in an active BedWars match; otherwise leave the rank
        // color the server already sent (lobby state — no team assigned).
        String coloredName = applyTeamColor(vanillaName, info);

        // Streamer mode: your own row can show a custom name and/or no stats.
        String ownIgn = info.getGameProfile().getName();
        String alias = coralintel.module.modules.StreamerMode.aliasFor(ownIgn);
        if (!alias.equals(ownIgn)) {
            coloredName = coloredName.replace(ownIgn, alias);
        }
        if (coralintel.module.modules.StreamerMode.hidesStatsFor(ownIgn)) {
            return coloredName;
        }

        IntelPlayer player = IntelManager.getInstance()
                .getPlayer(info.getGameProfile().getName());

        if (player == null || player.loading) {
            return coloredName;
        }

        // Denicked (Bedlify): once the real player's stats are loaded, show THEIR stats on the
        // nick's row (name stays, with the real name next to it).
        IntelPlayer nickRow = null;
        if (player.isNicked && lobbyIntel.tabShowRealStats.getValue()) {
            IntelPlayer real = player.realStats;
            if (real != null && !real.loading) {
                nickRow = player;
                player = real;
            }
        }

        // Nicked players have no real stats — show a [NICK] tag in the tab
        // list instead of a meaningless 0-star badge and zeroed stats.
        if (nickRow == null && player.isNicked && lobbyIntel.tabShowNick.getValue()
                && !coralintel.module.modules.StreamerMode.hidesTagsFor(ownIgn)) {
            // Denicked (Bedlify): the likely real name, if we found one.
            String real = lobbyIntel.tabShowRealName.getValue() ? player.realName : null;
            // A nick can still carry a tag of its own (e.g. your blacklist), so show that too.
            StringBuilder nickBadges = new StringBuilder();
            if (lobbyIntel.tabShowTag.getValue()) {
                for (IntelPlayer.Badge badge : coralintel.module.modules.StreamerMode.badgesFor(player)) {
                    nickBadges.append(" ").append(badge.tabCode()).append(badge.text).append("\u00A7r");
                }
            }
            String nickBadge = nickBadges.toString();
            return NICK_TAG + coloredName + (real != null ? " \u00A77(\u00A7e" + real + "\u00A77)" : "") + nickBadge;
        }

        if (nickRow != null && lobbyIntel.tabShowRealName.getValue() && nickRow.realName != null) {
            coloredName = coloredName + " \u00A77(\u00A7e" + nickRow.realName + "\u00A77)\u00A7r";
        }
        // Star and the cheater-tag badge sit to the LEFT of the name; HP, FKDR and WLR stay on the right.
        // Denicked rows keep the [NICK] tag; the tag badge is the nick's own (blacklist) if it has
        // one, otherwise the real player's.
        java.util.List<IntelPlayer.Badge> badges;
        String nickTag = "";
        if (nickRow != null) {
            if (lobbyIntel.tabShowNick.getValue()
                    && !coralintel.module.modules.StreamerMode.hidesTagsFor(ownIgn)) {
                nickTag = NICK_TAG;
            }
            badges = coralintel.module.modules.StreamerMode.badgesFor(nickRow, player);
        } else {
            badges = coralintel.module.modules.StreamerMode.badgesFor(player);
        }
        String prefix = buildStatsPrefix(lobbyIntel, player, badges);
        String stats = buildStatsSuffix(info, lobbyIntel, player);
        return nickTag + prefix + coloredName + stats;
    }

    private static final String NICK_TAG = "\u00A75[NICK] ";

    /**
     * Star + cheater-tag badge — rendered to the LEFT of the player's name
     * in the default (non-Seraph) tab format.
     */
    private static String buildStatsPrefix(LobbyIntel intel, IntelPlayer player) {
        return buildStatsPrefix(intel, player, coralintel.module.modules.StreamerMode.badgesFor(player));
    }

    /** Same, with an explicit badge list (a denicked row shows the nick's and the real player's tags). */
    private static String buildStatsPrefix(LobbyIntel intel, IntelPlayer player, java.util.List<IntelPlayer.Badge> badges) {
        StringBuilder prefix = new StringBuilder();
        boolean wroteAny = false;

        if (intel.tabShowStar.getValue()) {
            // Full per-character prestige color + glyph (Nevada table): "[1234✪]"
            prefix.append(PrestigeUtil.format(player.star)).append("\u00A7r ");
            wroteAny = true;
        }

        if (intel.tabShowTag.getValue()) {
            for (IntelPlayer.Badge badge : badges) {
                // Closet cheater specifically renders gold in the tab list;
                // everything else uses the nearest code to its usual color.
                // §r after each badge so the tag color can never bleed onto the
                // name — the team color (or the rank colors) must stay in charge.
                prefix.append(badge.tabCode()).append(badge.text).append("\u00A7r ");
                wroteAny = true;
            }
        }

        return wroteAny ? prefix.toString() : "";
    }

    /**
     * Builds the RIGHT-side stats (everything except star and the cheater
     * tag, which now render to the LEFT of the name via buildStatsPrefix()),
     * field-by-field, driven by the same per-field toggles the .bw command
     * uses (cloned onto the module as tabShow* settings) — so the tab list
     * can show exactly the same set of fields as .bw, independently
     * configured.
     */
    private static String buildStatsSuffix(NetworkPlayerInfo info, LobbyIntel intel, IntelPlayer player) {
        StringBuilder stats = new StringBuilder();
        boolean wroteAny = false;

        if (intel.tabShowHp.getValue()) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
            if (mc.theWorld != null && info.getGameProfile().getId() != null) {
                net.minecraft.entity.player.EntityPlayer entity =
                        mc.theWorld.getPlayerEntityByUUID(info.getGameProfile().getId());
                if (entity != null) {
                    stats.append("§7HP §f").append((int) Math.ceil(entity.getHealth())).append(" ");
                    wroteAny = true;
                }
            }
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

        if (!wroteAny) {
            return "";
        }

        return "  " + stats.toString().trim();
    }

    /**
     * When a scoreboard team is assigned (an active BedWars match), strips
     * any leading color code from the name and prepends the team's color
     * instead. In the lobby, no team is assigned yet, so the name is left
     * exactly as the server sent it (its normal rank color).
     */
    private static String applyTeamColor(String vanillaName, NetworkPlayerInfo info) {
        ScorePlayerTeam team = info.getPlayerTeam();
        if (team == null) {
            return vanillaName;
        }

        String colorPrefix = FontRenderer.getFormatFromString(team.getColorPrefix());
        if (colorPrefix.length() < 2) {
            return vanillaName;
        }

        char colorChar = colorPrefix.charAt(1);
        String teamCode = "\u00A7" + colorChar;

        // Color the player's NAME by team and leave the rank prefix alone, so
        // "[MVP+] Steve" keeps its rank colors but "Steve" is team colored.
        String playerName = info.getGameProfile().getName();
        int nameAt = playerName == null ? -1 : vanillaName.lastIndexOf(playerName);
        if (nameAt >= 0) {
            return vanillaName.substring(0, nameAt)
                    + teamCode + playerName
                    + vanillaName.substring(nameAt + playerName.length());
        }

        String stripped = vanillaName.replaceFirst("^(\u00A7[0-9a-fk-or])+", "");
        return teamCode + stripped;
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
