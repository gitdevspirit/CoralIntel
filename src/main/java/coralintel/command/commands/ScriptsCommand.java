package coralintel.command.commands;

import coralintel.CoralIntel;
import coralintel.command.Command;
import coralintel.script.ScriptInstance;
import coralintel.script.ScriptManager;

import java.util.List;

/**
 * .scripts                  list scripts with their status
 * .scripts reload           reload every script
 * .scripts enable <name>    turn a script on
 * .scripts disable <name>   turn a script off
 */
public class ScriptsCommand extends Command {
    public ScriptsCommand() {
        super("scripts");
        setDescription("Lua scripts. Usage: .scripts | .scripts reload | .scripts enable <name> | .scripts disable <name>");
    }

    @Override
    public void execute(String[] args) {
        ScriptManager manager = CoralIntel.scriptManager;
        if (manager == null) {
            reply("&cScripting is not loaded.");
            return;
        }

        if (args.length == 0) {
            list(manager);
            return;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("reload") && args.length == 1) {
            manager.reloadAll();
        } else if ((sub.equals("enable") || sub.equals("disable")) && args.length == 2) {
            ScriptInstance script = manager.find(args[1]);
            if (script == null) {
                reply("&cNo script named &f" + args[1] + "&c. Use &f.scripts &cto list them.");
                return;
            }
            boolean on = sub.equals("enable");
            manager.setEnabled(script, on);
            if (!on) {
                reply("&7Script &f" + script.name + " &7disabled.");
            } else if (script.getStatus() == ScriptInstance.Status.RUNNING) {
                reply("&7Script &f" + script.name + " &aenabled&7.");
            }
            // on a load error the manager already printed why
        } else {
            reply("&cUsage: &f.scripts &7| &f.scripts reload &7| &f.scripts enable <name> &7| &f.scripts disable <name>");
        }
    }

    private void list(ScriptManager manager) {
        List<ScriptInstance> scripts = manager.getScripts();
        reply("&b&lScripts &7(" + scripts.size() + ") in &f" + manager.getScriptsDir().getPath());
        if (scripts.isEmpty()) {
            reply("&7No scripts yet. Put &f.lua &7files in that folder, then run &f.scripts reload&7.");
            return;
        }
        for (ScriptInstance s : scripts) {
            String status;
            switch (s.getStatus()) {
                case RUNNING: status = "&arunning"; break;
                case ERROR: status = "&cerror &8- &7" + s.getLastError(); break;
                default: status = "&7disabled"; break;
            }
            reply("&f" + s.name + " &8» " + status);
        }
    }
}
