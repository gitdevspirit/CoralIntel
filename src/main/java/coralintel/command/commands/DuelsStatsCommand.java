package coralintel.command.commands;

import coralintel.CoralIntel;
import coralintel.command.Command;
import coralintel.duels.DuelsManager;
import coralintel.duels.DuelsPlayer;
import coralintel.duels.DuelsRank;
import coralintel.module.modules.DuelsIntel;
import net.minecraft.client.Minecraft;

import java.util.Locale;

/**
 * .duels <ign> [overall|classic|bridge|bwduels] : one-off Duels stat lookup, independent of the lobby roster.
 * Without a mode it prints the overall line plus Classic, Bridge and Bedwars Duels. What each line shows is
 * controlled by the "Duels: Show ..." settings on the DuelsIntel module.
 */
public class DuelsStatsCommand extends Command {

    public DuelsStatsCommand() {
        super("duels", "duel", "dstats");
        setDescription("Look up a player's Duels stats and rank. Usage: .duels <ign> [overall|classic|bridge|bwduels]");
    }

    @Override
    public void execute(String[] args) {
        if (args.length < 1 || args.length > 2 || !args[0].matches("[A-Za-z0-9_]{1,16}")) {
            reply("&cUsage: &f.duels <ign> [overall|classic|bridge|bwduels]");
            return;
        }

        int game = -1; // -1 = every mode
        if (args.length == 2) {
            game = parseGame(args[1]);
            if (game == -2) {
                reply("&cUnknown mode. &7Try: &foverall, classic, bridge, bwduels");
                return;
            }
        }

        lookup(args[0], true, game);
    }

    /** Lookup used by DuelsIntel to auto-check players who talk in chat. */
    public void lookup(String ign, boolean announce) {
        lookup(ign, announce, -1);
    }

    private void lookup(final String ign, boolean announce, final int game) {
        if (announce) reply("&7Fetching Duels stats for &f" + ign + "&7...");

        new Thread(() -> {
            final DuelsPlayer player = DuelsManager.getInstance().fetchStandalone(ign);
            Minecraft.getMinecraft().addScheduledTask(() -> send(ign, player, game));
        }, "duels-stats-lookup").start();
    }

    private static int parseGame(String s) {
        switch (s.toLowerCase(Locale.ROOT)) {
            case "all": case "overall": case "total": return 0;
            case "classic": return 1;
            case "bridge": return 2;
            case "bwduels": case "bwduel": case "bedwars": case "bw": return 3;
            default: return -2;
        }
    }

    private void send(String ign, DuelsPlayer p, int game) {
        DuelsIntel intel = (DuelsIntel) CoralIntel.moduleManager.getModule(DuelsIntel.class);

        if (!p.duelsLoaded) {
            if (p.statsHidden) {
                reply("&e" + ign + " &7has hidden their stats via API Settings.");
            } else {
                reply("&cNo Duels stats loaded for &f" + ign + "&c (check the API key, or try again in a few seconds).");
            }
            return;
        }

        if (game >= 0) {
            reply(line(ign, DuelsPlayer.GAMES[game], p.mode(game), intel));
        } else {
            for (int g = 0; g < DuelsPlayer.GAMES.length; g++) {
                DuelsPlayer.Mode m = p.mode(g);
                if (g > 0 && m.isEmpty()) continue; // skip modes they never played
                reply(line(ign, DuelsPlayer.GAMES[g], m, intel));
            }
        }

        int next = DuelsRank.winsToNext(p.overall.wins);
        if (next > 0) {
            reply("&7" + ign + " needs &f" + next + " &7more overall wins for "
                    + DuelsRank.COLORS[DuelsRank.index(p.overall.wins) + 1].replace('\u00A7', '&')
                    + DuelsRank.NAMES[DuelsRank.index(p.overall.wins) + 1] + "&7.");
        }
    }

    private String line(String ign, String label, DuelsPlayer.Mode m, DuelsIntel intel) {
        boolean rank = intel == null || intel.chatShowRank.getValue();
        boolean wl = intel == null || intel.chatShowWins.getValue();
        boolean wlr = intel == null || intel.chatShowWlr.getValue();
        boolean kdr = intel == null || intel.chatShowKdr.getValue();
        boolean ws = intel == null || intel.chatShowStreak.getValue();

        StringBuilder sb = new StringBuilder("&b").append(ign).append(" &7(").append(label).append(") &7\u00bb");
        if (rank) sb.append(" ").append(DuelsRank.colored(m.wins).replace('\u00A7', '&'));
        if (wl) sb.append(" &7W/L &f").append(m.wins).append("&7/&f").append(m.losses);
        if (wlr) sb.append(String.format(Locale.ROOT, " &7WLR &f%.2f", m.wlr()));
        if (kdr) sb.append(String.format(Locale.ROOT, " &7KDR &f%.2f", m.kdr()));
        if (ws) sb.append(" &7WS &f").append(m.winstreak).append("&7 (best &f").append(m.bestWinstreak).append("&7)");
        return sb.toString();
    }
}
