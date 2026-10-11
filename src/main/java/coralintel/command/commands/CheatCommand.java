package coralintel.command.commands;

import coralintel.command.Command;
import coralintel.ui.intel.CheatSuspicion;
import coralintel.ui.intel.IntelManager;
import coralintel.ui.intel.IntelPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * .cheat          -- everyone in the lobby with a suspicion score of 25 or more.
 * .cheat <name>   -- that player's score and why.
 * A score is a lead to check, not proof.
 */
public class CheatCommand extends Command {

    public CheatCommand() {
        super("cheat", "suspect");
        setDescription("Cheater suspicion scores. Usage: .cheat | .cheat <name>");
    }

    @Override
    public void execute(String[] args) {
        IntelManager manager = IntelManager.getInstance();

        if (args.length > 0) {
            IntelPlayer p = manager.getPlayer(args[0]);
            if (p == null) {
                reply("&cNo lobby data for &f" + args[0] + "&c yet.");
                return;
            }
            show(p, true);
            return;
        }

        List<IntelPlayer> flagged = new ArrayList<>();
        List<CheatSuspicion.Result> results = new ArrayList<>();
        for (IntelPlayer p : manager.getAllPlayers()) {
            if (p.loading) continue;
            CheatSuspicion.Result r = CheatSuspicion.analyze(p);
            if (r.score >= 25) {
                int i = 0;
                while (i < results.size() && results.get(i).score >= r.score) i++;
                flagged.add(i, p);
                results.add(i, r);
            }
        }

        if (flagged.isEmpty()) {
            reply("&aNobody in this lobby has a suspicion score of 25 or more.");
            return;
        }
        reply("&6&lSuspicious players &7(heuristic, not proof)");
        for (IntelPlayer p : flagged) show(p, false);
    }

    private void show(IntelPlayer p, boolean detail) {
        CheatSuspicion.Result r = CheatSuspicion.analyze(p);
        reply(r.color() + "\u25A0 &f" + p.name + " &8\u00bb " + r.color() + r.score + "&7/100 &8(" + r.color() + r.tier() + "&8)");
        if (detail && r.reasons.isEmpty()) reply("  &7No signals found.");
        if (detail || r.score >= 25) {
            for (String reason : r.reasons) reply("  &8\u2022 &7" + reason);
        }
    }
}
