package coralintel.command.commands;

import coralintel.CoralIntel;
import coralintel.command.Command;
import coralintel.module.BooleanSetting;
import coralintel.module.modules.LobbyIntel;
import coralintel.ui.intel.IntelColors;
import coralintel.ui.intel.IntelManager;
import coralintel.ui.intel.IntelPlayer;
import coralintel.ui.intel.StatSnapshotManager;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Locale;

/**
 * .daily [ign] / .monthly [ign] -- Bedwars stats gained over the last 24 hours
 * / 30 days, formatted like .bw.
 *
 *   .daily <ign>   one player (fetched fresh, like .bw)
 *   .daily         everyone currently tracked in LobbyIntel (no extra API calls)
 *   .daily all     same as no argument
 *
 * Hypixel only exposes lifetime totals, so the change is current totals minus a
 * locally saved snapshot (see StatSnapshotManager). A player needs a snapshot
 * from before the window for a full result; with a newer one the line says
 * "partial", and with none there is no baseline yet. Which fields are shown
 * follows the same "BW: Show ..." settings as .bw.
 */
public class PeriodStatsCommand extends Command {

    private final String label;
    private final long windowMs;

    public PeriodStatsCommand(String name, String label, String windowText, long windowMs) {
        super(name);
        this.label = label;
        this.windowMs = windowMs;
        setDescription(label + " Bedwars stats (last " + windowText + "). Usage: ." + name
                + " [ign]  (no ign = everyone in the lobby)");
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0 || (args.length == 1 && args[0].equalsIgnoreCase("all"))) {
            showLobby();
            return;
        }

        if (args.length != 1 || !args[0].matches("[A-Za-z0-9_]{1,16}")) {
            reply("&cUsage: &f." + getName() + " [ign]");
            return;
        }

        final String ign = args[0];
        reply("&7Fetching " + label.toLowerCase(Locale.ROOT) + " Bedwars stats for &f" + ign + "&7...");

        new Thread(() -> {
            final IntelPlayer player = IntelManager.getInstance().fetchStandaloneStats(ign);
            final StatSnapshotManager.Delta delta = player.statsComplete
                    ? StatSnapshotManager.getInstance().compute(player, windowMs)
                    : null;

            Minecraft.getMinecraft().addScheduledTask(() -> showOne(ign, player, delta));
        }, "period-stats-lookup").start();
    }

    // ── One player ───────────────────────────────────────────────────────

    private void showOne(String ign, IntelPlayer player, StatSnapshotManager.Delta delta) {
        if (!player.statsComplete) {
            if (player.isNicked) {
                reply("&5[NICK] &f" + ign + " &7appears to be nicked.");
            } else if (player.statsHidden) {
                reply("&e" + ign + " &7has hidden their Bedwars stats via API Settings.");
            } else {
                reply("&cNo Bedwars stats found for &f" + ign
                        + "&c (nicked, never played, or API unreachable).");
            }
            return;
        }

        if (delta == null) {
            reply("&f" + ign + " &7has no " + label.toLowerCase(Locale.ROOT) + " baseline yet. A snapshot has been saved"
                    + " now; run &f." + getName() + " " + ign + " &7again later (it needs a saved snapshot from"
                    + " before the " + label.toLowerCase(Locale.ROOT) + " window).");
            return;
        }

        reply(formatLine(player.name, delta));
    }

    // ── Whole lobby ──────────────────────────────────────────────────────

    private void showLobby() {
        List<IntelPlayer> all = IntelManager.getInstance().getAllPlayers();

        if (all.isEmpty()) {
            reply("&cNobody is tracked right now. Use &f." + getName() + " <ign>&c, or run it in a lobby.");
            return;
        }

        reply("&b" + label + " stats &7(" + all.size() + " players)");

        int shown = 0;
        int noBaseline = 0;
        int notLoaded = 0;

        for (IntelPlayer player : all) {
            if (player.isNicked) {
                continue;
            }

            if (!player.statsComplete) {
                notLoaded++;
                continue;
            }

            StatSnapshotManager.Delta delta = StatSnapshotManager.getInstance().compute(player, windowMs);

            if (delta == null) {
                noBaseline++;
                continue;
            }

            reply(formatLine(player.name, delta));
            shown++;
        }

        if (noBaseline > 0) {
            reply("&7" + noBaseline + " player" + (noBaseline == 1 ? "" : "s")
                    + " have no baseline yet (first time seen, or not seen recently).");
        }
        if (notLoaded > 0) {
            reply("&7" + notLoaded + " player" + (notLoaded == 1 ? "" : "s")
                    + " still loading; run &f." + getName() + " &7again in a moment.");
        }
        if (shown == 0 && noBaseline == 0 && notLoaded == 0) {
            reply("&7Nothing to show.");
        }
    }

    // ── Formatting ───────────────────────────────────────────────────────

    private String formatLine(String ign, StatSnapshotManager.Delta d) {
        LobbyIntel intel = (LobbyIntel) CoralIntel.moduleManager.getModule("LobbyIntel");

        StringBuilder line = new StringBuilder();
        line.append("&b").append(ign).append("&7 » &7(").append(label).append(", since ")
                .append(age(d.baselineAgeMs)).append(" ago").append(d.partial ? ", partial" : "").append(")  ");

        if (d.isEmpty()) {
            line.append("&7no games played");
            return line.toString();
        }

        int games = d.wins + d.losses;
        line.append("&7Games &f").append(games).append("  ");

        double fkdr = d.finalDeaths == 0 ? d.finalKills : (double) d.finalKills / d.finalDeaths;
        double wlr = d.losses == 0 ? d.wins : (double) d.wins / d.losses;
        double bblr = d.bedsLost == 0 ? d.bedsBroken : (double) d.bedsBroken / d.bedsLost;

        if (show(intel, intel == null ? null : intel.bwShowFkdr, true)) {
            String code = IntelColors.nearestCode(IntelColors.getStatColor(fkdr, 3, 6));
            line.append("&7FKDR ").append(code).append(fmt(fkdr)).append("  ");
        }
        if (show(intel, intel == null ? null : intel.bwShowWlr, true)) {
            String code = IntelColors.nearestCode(IntelColors.getStatColor(wlr, 2, 4));
            line.append("&7WLR ").append(code).append(fmt(wlr)).append("  ");
        }
        if (show(intel, intel == null ? null : intel.bwShowBblr, true)) {
            line.append("&7BBLR &f").append(fmt(bblr)).append("  ");
        }
        if (show(intel, intel == null ? null : intel.bwShowFinalKills, true)) {
            line.append("&7Finals &f+").append(d.finalKills).append("  ");
        }
        if (show(intel, intel == null ? null : intel.bwShowFinalDeaths, false)) {
            line.append("&7Final Deaths &f+").append(d.finalDeaths).append("  ");
        }
        if (show(intel, intel == null ? null : intel.bwShowKills, false)) {
            line.append("&7Kills &f+").append(d.kills).append("  ");
        }
        if (show(intel, intel == null ? null : intel.bwShowDeaths, false)) {
            line.append("&7Deaths &f+").append(d.deaths).append("  ");
        }
        if (show(intel, intel == null ? null : intel.bwShowBedsBroken, true)) {
            line.append("&7Beds &f+").append(d.bedsBroken).append("  ");
        }
        if (show(intel, intel == null ? null : intel.bwShowBedsLost, false)) {
            line.append("&7Beds Lost &f+").append(d.bedsLost).append("  ");
        }
        if (show(intel, intel == null ? null : intel.bwShowWins, false)) {
            line.append("&7Wins &f+").append(d.wins).append("  ");
        }
        if (show(intel, intel == null ? null : intel.bwShowLosses, false)) {
            line.append("&7Losses &f+").append(d.losses).append("  ");
        }

        return line.toString().trim();
    }

    /** Uses the setting when available, otherwise the given default. */
    private static boolean show(LobbyIntel intel, BooleanSetting setting, boolean defaultValue) {
        return intel == null || setting == null ? defaultValue : setting.getValue();
    }

    private static String age(long ms) {
        long minutes = ms / 60000L;
        if (minutes < 90) {
            return minutes + "m";
        }

        long hours = minutes / 60;
        if (hours < 48) {
            return hours + "h";
        }

        return (hours / 24) + "d";
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
