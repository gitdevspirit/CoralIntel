package coralintel.command.commands;

import coralintel.command.Command;
import coralintel.util.SessionHistory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * .history [count]  -- your saved sessions, newest first (default 5, max 20).
 * .history clear    -- deletes the saved history.
 */
public class HistoryCommand extends Command {

    public HistoryCommand() {
        super("history", "sessions");
        setDescription("Show your saved session history. Usage: .history [count] | .history clear");
    }

    @Override
    public void execute(String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("clear")) {
            SessionHistory.clear();
            reply("&aSession history cleared.");
            return;
        }

        int count = 5;
        if (args.length > 0) {
            try {
                count = Math.max(1, Math.min(20, Integer.parseInt(args[0])));
            } catch (NumberFormatException e) {
                reply("&cUsage: .history [count] | .history clear");
                return;
            }
        }

        List<SessionHistory.Record> all = SessionHistory.load();
        if (all.isEmpty()) {
            reply("&7No saved sessions yet. A session is saved after each game.");
            return;
        }

        SimpleDateFormat date = new SimpleDateFormat("MMM d HH:mm", Locale.ENGLISH);
        reply("&6&lSession history &7(last " + Math.min(count, all.size()) + " of " + all.size() + ")");

        for (int i = all.size() - 1, shown = 0; i >= 0 && shown < count; i--, shown++) {
            SessionHistory.Record r = all.get(i);
            reply("&7" + date.format(new Date(r.id))
                    + " &8| &f" + minutes(r.durationMs) + " &7(" + minutes(r.activeMs) + " active)"
                    + " &8| &f" + r.wins + "W " + r.losses + "L"
                    + " &8| &fFK " + r.finalKills + " &7FKDR &f" + fmt(r.fkdr)
                    + " &8| &fBeds " + r.bedsBroken + " &7BBLR &f" + fmt(r.bblr)
                    + " &8| &e+" + fmt(r.stars) + "\u272B");
        }

        int wins = 0, games = 0, finals = 0, finalDeaths = 0;
        double stars = 0;
        for (SessionHistory.Record r : all) {
            wins += r.wins;
            games += r.games;
            finals += r.finalKills;
            finalDeaths += r.finalDeaths;
            stars += r.stars;
        }
        double fkdr = finalDeaths == 0 ? finals : (double) finals / finalDeaths;
        reply("&6All time &8| &f" + all.size() + " sessions &8| &f" + games + " games, " + wins
                + " wins &8| &fFKDR " + fmt(fkdr) + " &8| &e+" + fmt(stars) + "\u272B");
    }

    private static String minutes(long ms) {
        long m = Math.max(0L, ms / 60000L);
        return m < 60 ? m + "m" : (m / 60) + "h " + (m % 60) + "m";
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
