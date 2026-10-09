package coralintel.command.commands;

import coralintel.command.Command;
import coralintel.ui.intel.ReminderManager;

import java.util.Arrays;
import java.util.List;

/**
 * .remind — personal notes about possible cheaters, to tag in Coral later.
 *
 *   .remind <player> [note...]   note a player (adds another note if already listed)
 *   .remind list                 everyone you've noted, newest first
 *   .remind view <player>        every note on one player
 *   .remind done <player>        drop them once you've tagged them
 *   .remind clear confirm        wipe the whole list
 *
 * If a noted player turns up in a later lobby you get an alert with your note.
 * Use ".remind add <player>" for a player whose name is one of the words above.
 */
public class ReminderCommand extends Command {
    private static final int LIST_LIMIT = 15;

    public ReminderCommand() {
        super("remind", "reminder", "reminders", "rem");
        setDescription("Note a possible cheater to tag later. Usage: .remind <player> [note] | list | view <player> | done <player>");
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            list();
            return;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "list":
            case "ls":
                list();
                return;

            case "view":
            case "show":
                if (args.length < 2 || !validName(args[1])) {
                    reply("&cUsage: &f.remind view <player>");
                    return;
                }
                view(args[1]);
                return;

            case "done":
            case "tagged":
            case "remove":
            case "del":
                if (args.length < 2 || !validName(args[1])) {
                    reply("&cUsage: &f.remind done <player>");
                    return;
                }
                if (ReminderManager.getInstance().remove(args[1])) {
                    reply("&aRemoved &f" + args[1] + " &afrom your reminders.");
                } else {
                    reply("&f" + args[1] + " &7isn't on your reminders.");
                }
                return;

            case "clear":
                if (args.length >= 2 && args[1].equalsIgnoreCase("confirm")) {
                    reply("&aCleared &f" + ReminderManager.getInstance().clear() + " &areminders.");
                } else {
                    reply("&cThis wipes every reminder. Run &f.remind clear confirm &cto do it.");
                }
                return;

            case "add":
                if (args.length < 2) {
                    reply("&cUsage: &f.remind add <player> [note]");
                    return;
                }
                add(args[1], Arrays.copyOfRange(args, 2, args.length));
                return;

            default:
                add(args[0], Arrays.copyOfRange(args, 1, args.length));
        }
    }

    private void add(String name, String[] noteWords) {
        if (!validName(name)) {
            reply("&cUsage: &f.remind <player> [note]");
            return;
        }

        String note = String.join(" ", noteWords).trim();
        boolean existed = ReminderManager.getInstance().has(name);
        ReminderManager.Reminder r = ReminderManager.getInstance().add(name, note);

        String shown = note.isEmpty() ? "" : " &7\u2014 " + r.latestNote();
        if (existed) {
            reply("&aUpdated reminder for &f" + r.name + "&a" + shown + " &8(" + r.notes.size()
                    + (r.notes.size() == 1 ? " note)" : " notes)"));
        } else {
            reply("&aReminder saved for &f" + r.name + "&a" + shown);
        }
        reply("&7Tag them in Coral later, then &f.remind done " + r.name + "&7.");
    }

    private void list() {
        List<ReminderManager.Reminder> all = ReminderManager.getInstance().getAll();

        if (all.isEmpty()) {
            reply("&7No reminders yet. Use &f.remind <player> [note] &7after a suspicious game.");
            return;
        }

        reply("&eReminders &7(" + all.size() + ") \u2014 tag these in Coral:");

        int shown = 0;
        for (ReminderManager.Reminder r : all) {
            if (shown++ >= LIST_LIMIT) break;

            String note = r.latestNote();
            reply("  &f" + r.name + " &8x" + r.seen + " &7\u2014 "
                    + (note.isEmpty() ? "&8(no note)" : note) + " &8(" + ago(r.lastSeen) + ")");
        }

        if (all.size() > LIST_LIMIT) {
            reply("  &8...and " + (all.size() - LIST_LIMIT) + " more (see the Reminders panel in the ClickGUI)");
        }
    }

    private void view(String name) {
        ReminderManager.Reminder r = ReminderManager.getInstance().get(name);

        if (r == null) {
            reply("&f" + name + " &7isn't on your reminders.");
            return;
        }

        reply("&e" + r.name + " &7\u2014 met &f" + r.seen + "&7x, first noted " + ago(r.created)
                + ", last seen " + ago(r.lastSeen));

        if (r.notes.isEmpty()) {
            reply("  &8(no notes)");
            return;
        }

        for (int i = 0; i < r.notes.size(); i++) {
            ReminderManager.Note n = r.notes.get(i);
            reply("  &8" + (i + 1) + ". &f" + n.text + " &8(" + ago(n.at) + ")");
        }
    }

    private static boolean validName(String name) {
        return name != null && name.matches("[A-Za-z0-9_]{1,16}");
    }

    private static String ago(long time) {
        long s = Math.max(0L, (System.currentTimeMillis() - time) / 1000L);
        if (s < 60) return "just now";
        long m = s / 60;
        if (m < 60) return m + "m ago";
        long h = m / 60;
        if (h < 24) return h + "h ago";
        return (h / 24) + "d ago";
    }
}
