package coralintel.command.commands;

import coralintel.CoralIntel;
import coralintel.command.Command;
import coralintel.module.modules.LobbyIntel;
import coralintel.ui.intel.IntelManager;
import coralintel.ui.intel.IntelPlayer;
import net.minecraft.client.Minecraft;

/**
 * .bw <ign> — one-off Bedwars stat lookup, independent of the lobby scan.
 * Which fields get printed is controlled by the "BW: Show ..." settings
 * on the LobbyIntel module.
 */
public class BedwarsStatsCommand extends Command {

    public BedwarsStatsCommand() {
        super("bw", "bwstats");
        setDescription("Look up a player's Bedwars stats. Usage: .bw <ign>");
    }

    @Override
    public void execute(String[] args) {
        if (args.length != 1 || !args[0].matches("[A-Za-z0-9_]{1,16}")) {
            reply("&cUsage: &f.bw <ign>");
            return;
        }

        String ign = args[0];
        reply("&7Fetching Bedwars stats for &f" + ign + "&7...");

        new Thread(() -> {
            IntelPlayer player = IntelManager.getInstance().fetchStandaloneStats(ign);
            Minecraft.getMinecraft().addScheduledTask(() -> sendStats(ign, player));
        }, "bw-stats-lookup").start();
    }

    private void sendStats(String ign, IntelPlayer player) {
        boolean hasAnyData = player.star != 0
                || player.finalKills != 0
                || player.wins != 0
                || player.fkdr != 0
                || player.wlr != 0;

        LobbyIntel intel = (LobbyIntel) CoralIntel.moduleManager.getModule("LobbyIntel");

        if (!hasAnyData) {
            reply("&cNo Bedwars stats found for &f" + ign
                    + "&c (nicked, never played, or API unreachable).");

            if (player.cheater && intel != null && intel.bwShowTag.getValue()) {
                String badge = player.getTagBadge();
                String detail = player.urchinTag != null ? player.urchinTag : badge;
                reply("&d[" + badge + "] &7" + detail);
            }

            return;
        }

        StringBuilder line = new StringBuilder();
        line.append("&b").append(ign).append("&7 » ");

        boolean wroteAny = false;

        if (enabled(intel, intel == null ? null : intel.bwShowStar)) {
            String starCode = coralintel.ui.intel.IntelColors.nearestCode(
                    coralintel.ui.intel.IntelColors.getPrestigeColor(player.star));
            line.append(starCode).append(player.star).append("&7\u272A  ");
            wroteAny = true;
        }
        if (enabled(intel, intel == null ? null : intel.bwShowFkdr)) {
            String fkdrCode = coralintel.ui.intel.IntelColors.nearestCode(
                    coralintel.ui.intel.IntelColors.getStatColor(player.fkdr, 3, 6));
            line.append("&7FKDR ").append(fkdrCode).append(fmt(player.fkdr)).append("  ");
            wroteAny = true;
        }
        if (enabled(intel, intel == null ? null : intel.bwShowWlr)) {
            String wlrCode = coralintel.ui.intel.IntelColors.nearestCode(
                    coralintel.ui.intel.IntelColors.getStatColor(player.wlr, 2, 4));
            line.append("&7WLR ").append(wlrCode).append(fmt(player.wlr)).append("  ");
            wroteAny = true;
        }
        if (enabled(intel, intel == null ? null : intel.bwShowBblr)) {
            double bblr = player.bedsLost == 0
                    ? player.bedsBroken
                    : (double) player.bedsBroken / player.bedsLost;
            line.append("&7BBLR &f").append(fmt(bblr)).append("  ");
            wroteAny = true;
        }
        if (enabled(intel, intel == null ? null : intel.bwShowFinalKills)) {
            line.append("&7Finals &f").append(player.finalKills).append("  ");
            wroteAny = true;
        }
        if (intel != null && intel.bwShowFinalDeaths.getValue()) {
            line.append("&7Final Deaths &f").append(player.finalDeaths).append("  ");
            wroteAny = true;
        }
        if (intel != null && intel.bwShowKills.getValue()) {
            line.append("&7Kills &f").append(player.kills).append("  ");
            wroteAny = true;
        }
        if (intel != null && intel.bwShowDeaths.getValue()) {
            line.append("&7Deaths &f").append(player.deaths).append("  ");
            wroteAny = true;
        }
        if (enabled(intel, intel == null ? null : intel.bwShowBedsBroken)) {
            line.append("&7Beds &f").append(player.bedsBroken).append("  ");
            wroteAny = true;
        }
        if (intel != null && intel.bwShowBedsLost.getValue()) {
            line.append("&7Beds Lost &f").append(player.bedsLost).append("  ");
            wroteAny = true;
        }
        if (enabled(intel, intel == null ? null : intel.bwShowWinstreak)) {
            line.append("&7WS &f").append(player.winstreak).append("  ");
            wroteAny = true;
        }
        if (intel != null && intel.bwShowWins.getValue()) {
            line.append("&7Wins &f").append(player.wins).append("  ");
            wroteAny = true;
        }
        if (intel != null && intel.bwShowLosses.getValue()) {
            line.append("&7Losses &f").append(player.losses).append("  ");
            wroteAny = true;
        }

        if (!wroteAny) {
            reply("&cAll .bw stat fields are disabled — check LobbyIntel settings.");
            return;
        }

        reply(line.toString().trim());

        if (player.cheater && intel != null && intel.bwShowTag.getValue()) {
            String badge = player.getTagBadge();
            String detail = player.urchinTag != null ? player.urchinTag : badge;
            String tagCode = badge.equals("C") ? "§6"
                    : coralintel.ui.intel.IntelColors.nearestCode(player.getTagColor());
            reply(tagCode + "[" + badge + "] &7" + detail);
        }
    }

    /** Defaults to true (shown) when the module reference or setting is unavailable. */
    private boolean enabled(LobbyIntel intel, coralintel.module.BooleanSetting setting) {
        return intel == null || setting == null || setting.getValue();
    }

    private String fmt(double value) {
        return String.format("%.2f", value);
    }
}
