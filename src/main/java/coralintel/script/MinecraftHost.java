package coralintel.script;

import coralintel.CoralIntel;
import coralintel.command.Command;
import coralintel.enums.ChatColors;
import coralintel.module.Module;
import coralintel.module.modules.SessionStats;
import coralintel.ui.intel.CheatSuspicion;
import coralintel.ui.intel.IntelManager;
import coralintel.ui.intel.IntelPlayer;
import coralintel.util.ChatUtil;
import coralintel.util.SoundUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The real game behind ScriptHost. Everything here runs on the main thread and only reads game data. */
public final class MinecraftHost implements ScriptHost {
    // ClickGUI panels that aren't modules but still own a title.
    private static final List<String> RESERVED_TITLES = Arrays.asList("Chat", "Reminders", "Blacklist/Safelist", "Scripts");

    private static Minecraft mc() {
        return Minecraft.getMinecraft();
    }

    @Override
    public void chat(String formatted) {
        ChatUtil.sendFormatted(formatted);
    }

    @Override
    public boolean canChat() {
        return mc().thePlayer != null;
    }

    @Override
    public void sound(String name) {
        SoundUtil.playSound(name);
    }

    @Override
    public List<String> lobbyPlayers() {
        List<String> names = new ArrayList<>();
        for (IntelPlayer p : IntelManager.getInstance().getAllPlayers()) {
            if (p != null && p.name != null) names.add(p.name);
        }
        return names;
    }

    @Override
    public Map<String, Object> lobbyPlayer(String name) {
        IntelPlayer p = IntelManager.getInstance().getPlayer(name);
        if (p == null) return null;

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", p.name);
        if (p.team != null) m.put("team", p.team);
        m.put("star", p.star);
        m.put("fkdr", p.fkdr);
        m.put("wlr", p.wlr);
        m.put("winstreak", p.winstreak);
        m.put("final_kills", p.finalKills);
        m.put("final_deaths", p.finalDeaths);
        m.put("beds_broken", p.bedsBroken);
        m.put("beds_lost", p.bedsLost);
        m.put("kills", p.kills);
        m.put("deaths", p.deaths);
        m.put("wins", p.wins);
        m.put("losses", p.losses);
        m.put("nicked", p.isNicked);
        // "loaded": the lobby tracker considers this player's stats final (loaded, hidden, nicked or given up on).
        m.put("loaded", p.statsFinal && !p.loading);
        m.put("stats_hidden", p.statsHidden);
        List<String> tags = new ArrayList<>();
        for (IntelPlayer.Badge badge : p.getAllBadges()) tags.add(badge.text);
        m.put("tags", tags);
        m.put("blacklisted", p.blacklisted);
        m.put("safelisted", p.safelisted);
        return m;
    }

    @Override
    public Map<String, Object> suspicion(String name) {
        IntelPlayer p = IntelManager.getInstance().getPlayer(name);
        if (p == null) return null;
        CheatSuspicion.Result r = CheatSuspicion.analyze(p);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("score", r.score);
        m.put("tier", r.tier());
        m.put("reasons", new ArrayList<>(r.reasons));
        return m;
    }

    @Override
    public Map<String, Object> session() {
        Module m = CoralIntel.moduleManager == null ? null : CoralIntel.moduleManager.getModule(SessionStats.class);
        if (m instanceof SessionStats) return ((SessionStats) m).scriptSnapshot();
        return new LinkedHashMap<>();
    }

    @Override
    public String ownName() {
        return mc().getSession() == null ? null : mc().getSession().getUsername();
    }

    @Override
    public Set<String> tabNames() {
        Set<String> out = new LinkedHashSet<>();
        NetHandlerPlayClient net = mc().getNetHandler();
        if (net == null) return out;
        for (NetworkPlayerInfo info : net.getPlayerInfoMap()) {
            if (IntelManager.isNpc(info)) continue;
            out.add(info.getGameProfile().getName());
        }
        return out;
    }

    @Override
    public void drawText(String text, int x, int y, int color) {
        mc().fontRendererObj.drawStringWithShadow(ChatColors.formatColor(text), x, y, color);
    }

    @Override
    public boolean commandExists(String name) {
        for (Command c : CoralIntel.commandManager.getCommands()) {
            for (String alias : c.getAliases()) {
                if (alias.equalsIgnoreCase(name)) return true;
            }
        }
        return false;
    }

    @Override
    public void registerCommand(Command command) {
        CoralIntel.commandManager.register(command);
    }

    @Override
    public void unregisterCommand(Command command) {
        CoralIntel.commandManager.unregister(command);
    }

    @Override
    public boolean moduleNameTaken(String name) {
        for (String title : RESERVED_TITLES) {
            if (title.equalsIgnoreCase(name)) return true;
        }
        for (Module m : CoralIntel.moduleManager.modules.values()) {
            if (m.getName().equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    @Override
    public void openFolder(File dir) {
        try {
            if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().open(dir);
        } catch (Throwable ignored) {
            // no desktop here: the path was already printed in chat
        }
    }
}
