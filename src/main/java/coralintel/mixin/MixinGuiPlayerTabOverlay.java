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
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
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
                    target = "Lnet/minecraft/client/gui/Gui;drawRect(IIIII)V"
            )
    )
    private void redirectTabBackground(int left, int top, int right, int bottom, int color) {
        // Vanilla's own per-row tab background is a hardcoded translucent
        // black (0x21000000). This is the least-verified piece of this
        // change — I don't have decompiled 1.8.9 source to confirm this is
        // the ONLY drawRect call inside renderPlayerlist, so if the tab
        // list looks wrong after this, that's the first place to check.
        LobbyIntel lobbyIntel = (LobbyIntel) CoralIntel.moduleManager.getModule(LobbyIntel.class);

        if (lobbyIntel != null && lobbyIntel.tabStats.getValue()) {
            int rgb = LobbyIntel.TAB_BG_PALETTE[lobbyIntel.tabBgColorChoice.getIndex()];
            int alpha = (int) lobbyIntel.tabBgOpacity.getValue();
            color = (alpha << 24) | rgb;
        }

        net.minecraft.client.gui.Gui.drawRect(left, top, right, bottom, color);
    }

    /**
     * Experimental — injects a column-title line ("Player | HP Star FKDR
     * WLR Tags") into the tab list's header text when Seraph Style is on,
     * using the exact same fixed column widths as buildSeraphStatsSuffix()
     * so every row's values land directly under their label. This is the
     * least-verified piece of everything here: "setHeader" taking a single
     * IChatComponent is my best-confidence guess at the 1.8.9 method
     * signature, but I don't have decompiled source to confirm it against.
     * If this doesn't compile or the header just doesn't show up, this is
     * the method to look at first.
     */
    @ModifyVariable(method = "setHeader", at = @At("HEAD"), argsOnly = true)
    private IChatComponent injectSeraphHeader(IChatComponent header) {
        LobbyIntel lobbyIntel = (LobbyIntel) CoralIntel.moduleManager.getModule(LobbyIntel.class);
        if (lobbyIntel == null || !lobbyIntel.tabStats.getValue() || !lobbyIntel.seraphStyle.getValue()) {
            return header;
        }

        String columnLine = applyHeaderOffset(buildSeraphHeaderLine(lobbyIntel), lobbyIntel);
        String existing = header != null ? header.getFormattedText() : "";
        String combined = existing.isEmpty() ? columnLine : existing + "\n" + columnLine;
        return new ChatComponentText(combined);
    }

    /**
     * Since vanilla centers the whole header line as one block, the content
     * inside it only shifts by HALF of whatever padding you add to one
     * side — adding it symmetrically to the centering math cancels itself
     * out. So a requested shift of N pixels needs 2N pixels of actual
     * padding, on the leading side to move right, or the trailing side to
     * move left.
     */
    private String applyHeaderOffset(String columnLine, LobbyIntel intel) {
        int offset = (int) intel.seraphHeaderOffset.getValue();
        if (offset == 0) return columnLine;

        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        int targetPadWidth = Math.abs(offset) * 2;

        // Build the padding by measuring its own accumulated width via
        // getStringWidth (never getCharWidth — mixing those two was the
        // root cause of every alignment issue here), so it actually comes
        // out to the requested pixel width instead of just an approximation.
        StringBuilder pad = new StringBuilder();
        while (mc.fontRendererObj.getStringWidth(pad.toString()) < targetPadWidth) {
            pad.append(' ');
        }

        return offset > 0 ? (pad + columnLine) : (columnLine + pad);
    }

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

        String stats;
        if (lobbyIntel.seraphStyle.getValue()) {
            coloredName = fitPixelsLeft(coloredName, NAME_COL_WIDTH);
            stats = buildSeraphStatsSuffix(info, lobbyIntel, player);
        } else {
            stats = buildStatsSuffix(info, lobbyIntel, player);
        }
        return coloredName + stats;
    }

    /**
     * Builds the stats suffix for the tab list, field-by-field,
     * driven by the same per-field toggles the .bw command uses (cloned
     * onto the module as tabShow* settings) — so the tab list can show
     * exactly the same set of fields as .bw, independently configured.
     */
    private String buildStatsSuffix(NetworkPlayerInfo info, LobbyIntel intel, IntelPlayer player) {
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

        return "  " + stats.toString().trim();
    }

    // Shared between buildSeraphHeaderLine() and buildSeraphStatsSuffix() —
    // both MUST use the exact same widths or the header and the values
    // underneath it drift apart. Fixed rather than measured dynamically
    // (e.g. off the longest current name) so the header — which is only
    // rebuilt whenever the server refreshes header/footer text, not every
    // frame — can never fall out of sync with what the rows are using.
    private static final int NAME_COL_WIDTH = 130;
    private static final int HP_COL_WIDTH = 34;
    private static final int STAR_COL_WIDTH = 50;
    private static final int FKDR_COL_WIDTH = 52;
    private static final int WLR_COL_WIDTH = 48;
    private static final int TAGS_COL_WIDTH = 44;

    private String buildSeraphHeaderLine(LobbyIntel intel) {
        StringBuilder line = new StringBuilder();
        line.append(padPixelsCenter("§e§lPlayer", NAME_COL_WIDTH));
        line.append("  ");

        // Centered within each column — same fixed widths as the rows use,
        // so the column boundaries still match; the labels just sit in the
        // middle of that space now instead of at either edge.
        if (intel.tabShowHp.getValue()) {
            line.append(padPixelsCenter("§eHP", HP_COL_WIDTH)).append(" ");
        }
        line.append(padPixelsCenter("§eStar", STAR_COL_WIDTH)).append(" ");
        line.append(padPixelsCenter("§eFKDR", FKDR_COL_WIDTH)).append(" ");
        line.append(padPixelsCenter("§eWLR", WLR_COL_WIDTH)).append(" ");
        line.append(padPixelsCenter("§eTags", TAGS_COL_WIDTH));

        return line.toString();
    }

    /**
     * "Seraph Style" — raw values only (no repeated per-row field labels,
     * those live in the header now), each right-aligned into a fixed pixel-
     * width column shared with buildSeraphHeaderLine() above.
     */
    private String buildSeraphStatsSuffix(NetworkPlayerInfo info, LobbyIntel intel, IntelPlayer player) {
        StringBuilder stats = new StringBuilder("  ");

        if (intel.tabShowHp.getValue()) {
            String hpStr = "-";
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
            if (mc.theWorld != null && info.getGameProfile().getId() != null) {
                net.minecraft.entity.player.EntityPlayer entity =
                        mc.theWorld.getPlayerEntityByUUID(info.getGameProfile().getId());
                if (entity != null) {
                    hpStr = String.valueOf((int) Math.ceil(entity.getHealth()));
                }
            }
            stats.append("§f").append(padPixelsCenter(hpStr, HP_COL_WIDTH)).append(" ");
        }

        String starCode = IntelColors.nearestCode(IntelColors.getPrestigeColor(player.star));
        // Splitting the glyph and the number into their own fixed-width
        // sub-slots (instead of centering "✩19" as one glued unit) fixes a
        // real remaining issue: a 2-digit number pushes more content after
        // the glyph than a 1-digit one, so even with the OUTER column
        // perfectly centered, the star icon itself still lands at a
        // different X per row depending on how many digits follow it.
        int starIconWidth = 16;
        int starNumWidth = Math.max(10, STAR_COL_WIDTH - starIconWidth);
        stats.append(starCode).append(padPixelsCenter("\u272A", starIconWidth));
        stats.append(padPixelsCenter(String.valueOf(player.star), starNumWidth)).append(" ");

        String fkdrCode = IntelColors.nearestCode(IntelColors.getStatColor(player.fkdr, 3, 6));
        stats.append(fkdrCode).append(padPixelsCenter(fmt(player.fkdr), FKDR_COL_WIDTH)).append(" ");

        String wlrCode = IntelColors.nearestCode(IntelColors.getStatColor(player.wlr, 2, 4));
        stats.append(wlrCode).append(padPixelsCenter(fmt(player.wlr), WLR_COL_WIDTH)).append(" ");

        String tag = player.getTagBadge();
        String tagCode = tag.isEmpty() ? "§7" : (tag.equals("C") ? "§6" : IntelColors.nearestCode(player.getTagColor()));
        stats.append(tagCode).append(padPixelsCenter(tag.isEmpty() ? "-" : tag, TAGS_COL_WIDTH));

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
     *
     * Adds spaces one at a time, re-measuring the WHOLE string with
     * getStringWidth() each time — never getCharWidth(). Those two report
     * different things (getStringWidth includes Minecraft's standard +1px
     * gap between every character, getCharWidth doesn't), so computing a
     * space count via one and measuring the deficit via the other was
     * quietly wrong by a few pixels on every single column, which is
     * exactly the "still not aligned" result.
     */
    private String padPixels(String text, int targetPixelWidth) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        StringBuilder sb = new StringBuilder(text);

        while (mc.fontRendererObj.getStringWidth(sb.toString()) < targetPixelWidth) {
            sb.insert(0, ' ');
        }
        // Last space may have overshot the target — drop it if removing it
        // still leaves us at or under the target, whichever is the closer fit.
        if (sb.length() > text.length()) {
            String oneLess = sb.substring(1);
            int overshoot = mc.fontRendererObj.getStringWidth(sb.toString()) - targetPixelWidth;
            int undershoot = targetPixelWidth - mc.fontRendererObj.getStringWidth(oneLess);
            if (undershoot >= 0 && undershoot <= overshoot) {
                return oneLess;
            }
        }
        return sb.toString();
    }

    /** Same idea as padPixels, but left-aligned — text stays put, trailing spaces added after it. */
    private String padPixelsLeft(String text, int targetPixelWidth) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        StringBuilder sb = new StringBuilder(text);

        while (mc.fontRendererObj.getStringWidth(sb.toString()) < targetPixelWidth) {
            sb.append(' ');
        }
        if (sb.length() > text.length()) {
            String oneLess = sb.substring(0, sb.length() - 1);
            int overshoot = mc.fontRendererObj.getStringWidth(sb.toString()) - targetPixelWidth;
            int undershoot = targetPixelWidth - mc.fontRendererObj.getStringWidth(oneLess);
            if (undershoot >= 0 && undershoot <= overshoot) {
                return oneLess;
            }
        }
        return sb.toString();
    }

    /**
     * Like padPixelsLeft, but also handles the case padding alone can't:
     * text WIDER than the target. Without this, a long name (like
     * "XXXtencation_") just runs past the column with nothing added —
     * padPixelsLeft only ever adds space, it can't shorten anything — which
     * pushes that one row's entire stats block to the right of every other
     * row's. This truncates with an ellipsis so every row's name column
     * ends at exactly the same pixel width no matter how long the name is,
     * which is what actually keeps every column aligned regardless of name
     * length.
     */
    private String fitPixelsLeft(String text, int targetPixelWidth) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();

        if (mc.fontRendererObj.getStringWidth(text) <= targetPixelWidth) {
            return padPixelsLeft(text, targetPixelWidth);
        }

        String ellipsis = "\u2026";

        // Strip one visible character at a time and re-measure the WHOLE
        // remaining string (plus ellipsis) via getStringWidth — same fix as
        // above: measuring per-character via getCharWidth and summing
        // doesn't match what getStringWidth reports for the same text, so
        // that would under/overshoot the actual target width.
        StringBuilder visible = new StringBuilder();
        StringBuilder codes = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00A7' && i + 1 < text.length()) {
                codes.append(c).append(text.charAt(i + 1));
                i++;
            } else {
                visible.append(c);
            }
        }

        while (visible.length() > 0
                && mc.fontRendererObj.getStringWidth(codes + visible.toString() + ellipsis) > targetPixelWidth) {
            visible.deleteCharAt(visible.length() - 1);
        }

        return codes + visible.toString() + ellipsis;
    }

    /** Centers text within a fixed pixel width — spaces split evenly on both sides. */
    private String padPixelsCenter(String text, int targetPixelWidth) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();

        int deficit = targetPixelWidth - mc.fontRendererObj.getStringWidth(text);
        if (deficit <= 0) return text;

        // Alternate adding a space to each side and re-measure the WHOLE
        // string every step, rather than pre-computing a space count from a
        // different (inconsistent) width metric.
        StringBuilder left = new StringBuilder();
        StringBuilder right = new StringBuilder();
        boolean addLeft = true;

        while (mc.fontRendererObj.getStringWidth(left + text + right.toString()) < targetPixelWidth) {
            if (addLeft) left.append(' '); else right.append(' ');
            addLeft = !addLeft;
        }

        String result = left + text + right.toString();
        // The last addition may have overshot — check if backing it off
        // (from whichever side just grew) lands closer to the target.
        String shrunk = !addLeft
                ? left.substring(0, Math.max(0, left.length() - 1)) + text + right.toString()
                : left + text + right.substring(0, Math.max(0, right.length() - 1));

        int overshoot = mc.fontRendererObj.getStringWidth(result) - targetPixelWidth;
        int undershoot = targetPixelWidth - mc.fontRendererObj.getStringWidth(shrunk);
        return (undershoot >= 0 && undershoot <= overshoot) ? shrunk : result;
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
