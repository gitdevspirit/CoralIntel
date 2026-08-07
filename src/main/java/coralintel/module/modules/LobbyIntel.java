package coralintel.module.modules;

import coralintel.event.EventTarget;
import coralintel.event.types.EventType;
import coralintel.events.KeyEvent;
import coralintel.events.LoadWorldEvent;
import coralintel.events.TickEvent;
import coralintel.events.PacketEvent;
import coralintel.events.Render2DEvent;
import coralintel.module.BooleanSetting;
import coralintel.module.Module;
import coralintel.module.KeybindSetting;
import coralintel.property.properties.*;
import coralintel.ui.intel.IntelGui;
import coralintel.ui.clickgui.ClickGui;
import coralintel.ui.intel.IntelHudOverlay;
import coralintel.ui.intel.IntelManager;
import coralintel.ui.intel.IntelPlayer;
import coralintel.ui.intel.SafelistManager;
import coralintel.util.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.util.IChatComponent;
import org.lwjgl.input.Keyboard;

import java.io.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LobbyIntel extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final BooleanSetting autoScan = register(new BooleanSetting("Auto Scan on Join", true));
    public final BooleanSetting focusMode = register(new BooleanSetting("Focus Mode", false));
    public final coralintel.module.SliderSetting focusCount =
            register(new coralintel.module.SliderSetting("Focus Count", 10, 1, 30, 1));
    public final BooleanSetting autoKey = register(new BooleanSetting("Auto Detect API Key", true));
    public final BooleanSetting notifyCheaters =
            register(new BooleanSetting("Notify Cheaters", false));
    public final BooleanSetting hideTeammates =
            register(new BooleanSetting("Hide Teammates", false));
    public final BooleanSetting tabStats =
            register(new BooleanSetting("Tab Stats", true));
    public final KeybindSetting hudKeybind =
            register(new KeybindSetting("HUD Toggle Key", Keyboard.KEY_H));
    public final KeybindSetting guiKeybind =
            register(new KeybindSetting("Open GUI Key", Keyboard.KEY_L));
    public final KeybindSetting clickGuiKeybind =
            register(new KeybindSetting("Open ClickGUI Key", Keyboard.KEY_RSHIFT));

    // .bw command — which fields to include in the chat output
    public final BooleanSetting bwShowStar =
            register(new BooleanSetting("BW: Show Star", true));
    public final BooleanSetting bwShowFkdr =
            register(new BooleanSetting("BW: Show FKDR", true));
    public final BooleanSetting bwShowWlr =
            register(new BooleanSetting("BW: Show WLR", true));
    public final BooleanSetting bwShowBblr =
            register(new BooleanSetting("BW: Show BBLR", true));
    public final BooleanSetting bwShowFinalKills =
            register(new BooleanSetting("BW: Show Final Kills", true));
    public final BooleanSetting bwShowFinalDeaths =
            register(new BooleanSetting("BW: Show Final Deaths", false));
    public final BooleanSetting bwShowKills =
            register(new BooleanSetting("BW: Show Kills", false));
    public final BooleanSetting bwShowDeaths =
            register(new BooleanSetting("BW: Show Deaths", false));
    public final BooleanSetting bwShowBedsBroken =
            register(new BooleanSetting("BW: Show Beds Broken", true));
    public final BooleanSetting bwShowBedsLost =
            register(new BooleanSetting("BW: Show Beds Lost", false));
    public final BooleanSetting bwShowWinstreak =
            register(new BooleanSetting("BW: Show Winstreak", true));
    public final BooleanSetting bwShowWins =
            register(new BooleanSetting("BW: Show Wins", false));
    public final BooleanSetting bwShowLosses =
            register(new BooleanSetting("BW: Show Losses", false));
    public final BooleanSetting bwShowTag =
            register(new BooleanSetting("BW: Show Cheater Tag", true));

    public final BooleanSetting tabShowTag =
            register(new BooleanSetting("Tab: Show Cheater Tag", true));

    // Tab list — cloned from the .bw field set above, same defaults.
    public final BooleanSetting tabShowStar =
            register(new BooleanSetting("Tab: Show Star", true));
    public final BooleanSetting tabShowFkdr =
            register(new BooleanSetting("Tab: Show FKDR", true));
    public final BooleanSetting tabShowWlr =
            register(new BooleanSetting("Tab: Show WLR", true));
    public final BooleanSetting tabShowBblr =
            register(new BooleanSetting("Tab: Show BBLR", false));
    public final BooleanSetting tabShowFinalKills =
            register(new BooleanSetting("Tab: Show Final Kills", false));
    public final BooleanSetting tabShowFinalDeaths =
            register(new BooleanSetting("Tab: Show Final Deaths", false));
    public final BooleanSetting tabShowKills =
            register(new BooleanSetting("Tab: Show Kills", false));
    public final BooleanSetting tabShowDeaths =
            register(new BooleanSetting("Tab: Show Deaths", false));
    public final BooleanSetting tabShowBedsBroken =
            register(new BooleanSetting("Tab: Show Beds Broken", false));
    public final BooleanSetting tabShowBedsLost =
            register(new BooleanSetting("Tab: Show Beds Lost", false));
    public final BooleanSetting tabShowWinstreak =
            register(new BooleanSetting("Tab: Show Winstreak", false));
    public final BooleanSetting tabShowWins =
            register(new BooleanSetting("Tab: Show Wins", false));
    public final BooleanSetting tabShowLosses =
            register(new BooleanSetting("Tab: Show Losses", false));

    public final BooleanProperty hudEnabled = new BooleanProperty("hud-enabled", true);
    public final IntProperty hudPosX = new IntProperty("hud-x", 10, 0, 3840);
    public final IntProperty hudPosY = new IntProperty("hud-y", 100, 0, 2160);
    public final FloatProperty hudScale = new FloatProperty("hud-scale", 1.0f, 0.5f, 2.0f);
    public final IntProperty hudMaxPlayers = new IntProperty("hud-max-players", 10, 1, 20);
    public final IntProperty hudBgOpacity = new IntProperty("hud-bg-opacity", 180, 0, 255);
    public final IntProperty hudBorderOpacity =
            new IntProperty("hud-border-opacity", 100, 0, 255);
    public final IntProperty hudColumnLineOpacity =
            new IntProperty("hud-column-line-opacity", 26, 0, 255);
    public final BooleanProperty hudShowHeads =
            new BooleanProperty("hud-show-heads", true);
    public final BooleanProperty hudShowStar =
            new BooleanProperty("hud-show-star", true);
    public final BooleanProperty hudShowLevel =
            new BooleanProperty("hud-show-level", false);
    public final BooleanProperty hudShowFkdr =
            new BooleanProperty("hud-show-fkdr", true);
    public final BooleanProperty hudShowWlr =
            new BooleanProperty("hud-show-wlr", true);
    public final BooleanProperty hudShowStreak =
            new BooleanProperty("hud-show-streak", true);
    public final BooleanProperty hudShowThreat =
            new BooleanProperty("hud-show-threat", true);
    public final BooleanProperty hudShowUrchin =
            new BooleanProperty("hud-show-urchin", true);
    public final BooleanProperty hudShowTeamColor =
            new BooleanProperty("hud-show-team-color", true);
    public final TextProperty hudSortMode =
            new TextProperty("hud-sort-mode", "threat");
    public final TextProperty hudColumnOrder =
            new TextProperty("hud-column-order", "name,star,fkdr,urchin,threat");

    public final TextProperty logPath = new TextProperty(
            "log-path",
            System.getProperty("user.home") + detectDefaultLogPath()
    );

    public final TextProperty savedApiKey =
            new TextProperty("hypixel-api-key", "");

    private final IntelGui gui = new IntelGui();
    private final IntelHudOverlay hudOverlay = new IntelHudOverlay();
    private boolean scannedThisSession = false;
    private boolean finalWhoSent = false;
    private int retryTickCounter = 0;

    public LobbyIntel() {
        super("LobbyIntel", true);

        IntelManager.getInstance().setGui(gui);
        IntelManager.getInstance().setHudOverlay(hudOverlay);

        loadApiKeyFromFile();

        if (IntelManager.hypixelApiKey.isEmpty()) {
            tryAutoDetectKey();
        }

        loadHudSettings();
    }

    public void saveApiKeyToFile() {
        if (IntelManager.hypixelApiKey.isEmpty()) return;

        try {
            File dir = new File("./config/CoralIntel/");
            dir.mkdirs();

            File keyFile = new File(dir, "intel-key.txt");
            PrintWriter writer = new PrintWriter(new FileWriter(keyFile));
            writer.println(IntelManager.hypixelApiKey);
            writer.close();
        } catch (Exception ignored) {
        }
    }

    private void loadApiKeyFromFile() {
        try {
            File keyFile = new File("./config/CoralIntel/intel-key.txt");
            if (!keyFile.exists()) return;

            BufferedReader reader = new BufferedReader(new FileReader(keyFile));
            String key = reader.readLine();
            reader.close();

            if (key != null) {
                key = key.trim();

                if (key.matches(
                        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
                )) {
                    IntelManager.hypixelApiKey = key;
                    IntelManager.dbg("[Intel] Loaded API key from file");
                }
            }
        } catch (Exception ignored) {
        }
    }

    public void loadHudSettings() {
        hudOverlay.setEnabled(hudEnabled.getValue());
        hudOverlay.setPosition(hudPosX.getValue(), hudPosY.getValue());
        hudOverlay.setScale(hudScale.getValue());
        hudOverlay.setMaxPlayers(hudMaxPlayers.getValue());
        hudOverlay.setBgOpacity(hudBgOpacity.getValue());
        hudOverlay.setBorderOpacity(hudBorderOpacity.getValue());
        hudOverlay.setColumnLineOpacity(hudColumnLineOpacity.getValue());
        hudOverlay.setShowHeads(hudShowHeads.getValue());
        hudOverlay.setShowStar(hudShowStar.getValue());
        hudOverlay.setShowLevel(hudShowLevel.getValue());
        hudOverlay.setShowFkdr(hudShowFkdr.getValue());
        hudOverlay.setShowWlr(hudShowWlr.getValue());
        hudOverlay.setShowStreak(hudShowStreak.getValue());
        hudOverlay.setShowThreat(hudShowThreat.getValue());
        hudOverlay.setShowUrchin(hudShowUrchin.getValue());
        hudOverlay.setShowTeamColor(hudShowTeamColor.getValue());
        hudOverlay.setSortMode(hudSortMode.getValue());
    }

    public void saveHudSettings() {
        hudEnabled.setValue(hudOverlay.isEnabled());
        hudPosX.setValue(hudOverlay.getPosX());
        hudPosY.setValue(hudOverlay.getPosY());
        hudScale.setValue(hudOverlay.getScale());
        hudMaxPlayers.setValue(hudOverlay.getMaxPlayers());
        hudBgOpacity.setValue(hudOverlay.getBgOpacity());
        hudBorderOpacity.setValue(hudOverlay.getBorderOpacity());
        hudColumnLineOpacity.setValue(hudOverlay.getColumnLineOpacity());
        hudShowHeads.setValue(hudOverlay.getShowHeads());
        hudShowStar.setValue(hudOverlay.getShowStar());
        hudShowLevel.setValue(hudOverlay.getShowLevel());
        hudShowFkdr.setValue(hudOverlay.getShowFkdr());
        hudShowWlr.setValue(hudOverlay.getShowWlr());
        hudShowStreak.setValue(hudOverlay.getShowStreak());
        hudShowThreat.setValue(hudOverlay.getShowThreat());
        hudShowUrchin.setValue(hudOverlay.getShowUrchin());
        hudShowTeamColor.setValue(hudOverlay.getShowTeamColor());
        hudSortMode.setValue(hudOverlay.getSortMode());
    }

    @Override
    public void onEnabled() {
        mc.addScheduledTask(() -> mc.displayGuiScreen(gui));

        if (IntelManager.getInstance().getPlayers().isEmpty()) {
            IntelManager.getInstance().scanLobby();
        } else {
            gui.setPlayers(IntelManager.getInstance().getPlayers());
        }
    }

    @Override
    public void onDisabled() {
        setEnabled(true);
    }

    @EventTarget
    public void onKey(KeyEvent event) {
        int key = event.getKey();

        if (key == hudKeybind.getKeyCode()) {
            boolean newState = !hudOverlay.isEnabled();
            hudOverlay.setEnabled(newState);

            String status = newState ? "&a&lON" : "&c&lOFF";
            ChatUtil.sendFormatted("&7[Intel] HUD Overlay: " + status);
            return;
        }

        if (key == guiKeybind.getKeyCode()) {
            // Toggle open/closed — press again while the GUI is open to close it.
            if (mc.currentScreen instanceof IntelGui) {
                mc.displayGuiScreen(null);
            } else if (mc.currentScreen == null) {
                if (IntelManager.getInstance().getPlayers().isEmpty()) {
                    IntelManager.getInstance().scanLobby();
                } else {
                    gui.setPlayers(IntelManager.getInstance().getPlayers());
                }
                mc.displayGuiScreen(gui);
            }
            return;
        }

        if (key == clickGuiKeybind.getKeyCode()) {
            if (mc.currentScreen instanceof ClickGui) {
                mc.displayGuiScreen(null);
            } else if (mc.currentScreen == null) {
                mc.displayGuiScreen(new ClickGui());
            }
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.currentScreen == null && hudOverlay.isEnabled()) {
            hudOverlay.render();
        }
    }

    private boolean wasMouseDown = false;

    @EventTarget
    public void onRender2DClick(Render2DEvent event) {
        if (mc.currentScreen != null) return;

        boolean down = org.lwjgl.input.Mouse.isButtonDown(0);

        if (down && !wasMouseDown) {
            net.minecraft.client.gui.ScaledResolution resolution =
                    new net.minecraft.client.gui.ScaledResolution(mc);

            int mouseX = org.lwjgl.input.Mouse.getX()
                    * resolution.getScaledWidth()
                    / mc.displayWidth;

            int mouseY = resolution.getScaledHeight()
                    - org.lwjgl.input.Mouse.getY()
                    * resolution.getScaledHeight()
                    / mc.displayHeight
                    - 1;

            hudOverlay.handleClick(mouseX, mouseY);
        }

        wasMouseDown = down;
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        scannedThisSession = false;
        finalWhoSent = false;
        retryTickCounter = 0;
        IntelManager.getInstance().clearAll();

        if (autoKey.getValue()) {
            tryAutoDetectKey();
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() != EventType.PRE) return;
        if (mc.theWorld == null || mc.thePlayer == null) return;

        retryTickCounter++;

        // 20 ticks/sec — every 200 ticks is 10 seconds.
        if (retryTickCounter >= 200) {
            retryTickCounter = 0;
            IntelManager.getInstance().retryFailedFetches();
        }
    }

    public IntelHudOverlay getHudOverlay() {
        return hudOverlay;
    }

    public IntelGui getGui() {
        return gui;
    }

    public void tryAutoDetectKey() {
        if (!autoKey.getValue()) return;

        new Thread(() -> {
            try {
                File log = new File(logPath.getValue());
                if (!log.exists()) return;

                Pattern pattern = Pattern.compile(
                        "(?:Your new API key is|API key set to|api key is)\\s+"
                                + "([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-"
                                + "[0-9a-f]{4}-[0-9a-f]{12})",
                        Pattern.CASE_INSENSITIVE
                );

                String lastKey = null;

                try (RandomAccessFile file = new RandomAccessFile(log, "r")) {
                    long length = file.length();
                    long start = Math.max(0, length - 512 * 1024);

                    file.seek(start);

                    byte[] bytes = new byte[(int) (length - start)];
                    file.readFully(bytes);

                    String content = new String(bytes, "UTF-8");
                    Matcher matcher = pattern.matcher(content);

                    while (matcher.find()) {
                        lastKey = matcher.group(1);
                    }
                }

                if (lastKey != null && !lastKey.equals(IntelManager.hypixelApiKey)) {
                    IntelManager.hypixelApiKey = lastKey;
                    savedApiKey.setValue(lastKey);
                    saveApiKeyToFile();

                    Minecraft.getMinecraft().addScheduledTask(() ->
                            ChatUtil.sendFormatted(
                                    "&7[Intel] &aAuto-detected Hypixel API key from log."
                            )
                    );
                }
            } catch (Exception ignored) {
            }
        }).start();
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() != EventType.RECEIVE) return;
        if (!(event.getPacket() instanceof S02PacketChat)) return;

        S02PacketChat packet = (S02PacketChat) event.getPacket();
        IChatComponent component = packet.getChatComponent();
        if (component == null) return;

        String message = component.getUnformattedText();

        if (autoScan.getValue()
                && !scannedThisSession
                && message.contains("The game starts in 10 seconds")) {

            scannedThisSession = true;
            IntelManager.dbg("[Intel] BedWars countdown detected — scanning lobby and requesting /who.");

            mc.addScheduledTask(() -> {
                if (autoKey.getValue()) {
                    tryAutoDetectKey();
                }

                IntelManager.getInstance().clearAll();
                IntelManager.getInstance().scanLobby();

                if (mc.thePlayer != null) {
                    mc.thePlayer.sendChatMessage("/who");
                }
            });
        }

        // A second /who right as the match actually begins — team
        // assignments are only finalized by this point, so this catches
        // anyone the 10-second scan's roster missed or had stale team data
        // for. Delayed by 1s after the message so it fires just after the
        // world/team state has actually settled, not mid-transition.
        if (autoScan.getValue()
                && !finalWhoSent
                && message.contains("The game starts in 1 second")) {

            finalWhoSent = true;
            IntelManager.dbg("[Intel] Final countdown detected — requesting /who in 1s.");

            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                    return;
                }

                mc.addScheduledTask(() -> {
                    if (mc.thePlayer != null) {
                        mc.thePlayer.sendChatMessage("/who");
                    }
                });
            }).start();
        }

        if (message.contains("FINAL KILL!")) {
            Pattern killPattern = Pattern.compile(
                    "^([A-Za-z0-9_]+) (?:was |fell |drowned|died|hit |got )"
            );

            Matcher matcher = killPattern.matcher(message.trim());

            if (matcher.find()) {
                String killedPlayer = matcher.group(1);
                removePlayerFromOverlay(killedPlayer);
                IntelManager.dbg("[Intel] Final kill: " + killedPlayer);

                // If it was YOU who got the final kill, auto-safelist the
                // player you just eliminated.
                if (mc.thePlayer != null) {
                    Pattern killerPattern = Pattern.compile("by ([A-Za-z0-9_]{1,16})");
                    Matcher killerMatcher = killerPattern.matcher(message);
                    String myName = mc.thePlayer.getName();

                    if (killerMatcher.find() && killerMatcher.group(1).equalsIgnoreCase(myName)) {
                        String reason = "Final killed by you";
                        boolean alreadySafelisted = SafelistManager.getInstance().isSafelisted(killedPlayer);
                        SafelistManager.getInstance().safelist(killedPlayer, reason);

                        IntelPlayer live = IntelManager.getInstance().getPlayer(killedPlayer);
                        if (live != null) {
                            live.safelisted = true;
                            live.safelistReason = reason;
                            live.computeThreat();
                        }

                        if (!alreadySafelisted) {
                            ChatUtil.sendFormatted("&a[Intel] Auto-safelisted &f" + killedPlayer
                                    + " &7— final killed by you");
                        }
                    }
                }
            }
        }

        if (message.startsWith("ONLINE:")) {
            String playerList = message.substring(7).trim();
            String[] parts = playerList.split(",\\s*");

            java.util.List<String> realNames = new java.util.ArrayList<>();

            for (String raw : parts) {
                String name = raw.replaceAll("[^a-zA-Z0-9_]", "").trim();

                if (!name.isEmpty()) {
                    realNames.add(name);
                }
            }

            if (!realNames.isEmpty()) {
                IntelManager manager = IntelManager.getInstance();
                manager.getPlayers().clear();
                manager.clearManualPlayers();

                for (String name : realNames) {
                    String self = mc.thePlayer != null ? mc.thePlayer.getName() : "";

                    if (!name.equalsIgnoreCase(self)) {
                        manager.addManualPlayer(name);
                    }
                }

                ChatUtil.sendFormatted(
                        "&7[Intel] &aLoaded "
                                + realNames.size()
                                + " players from /who."
                );

                IntelManager.dbg("[Intel] /who replaced list: " + realNames);
            }
        }
    }

    private void removePlayerFromOverlay(String playerName) {
        IntelManager manager = IntelManager.getInstance();
        boolean removed = false;

        for (int index = manager.getPlayers().size() - 1; index >= 0; index--) {
            if (manager.getPlayers().get(index).name.equalsIgnoreCase(playerName)) {
                manager.getPlayers().remove(index);
                removed = true;
                break;
            }
        }

        manager.removeManualPlayer(playerName);

        if (removed) {
            java.util.List<IntelPlayer> refreshed =
                    new java.util.ArrayList<>(manager.getPlayers());

            if (getGui() != null) {
                getGui().setPlayers(refreshed);
            }

            if (getHudOverlay() != null) {
                getHudOverlay().setPlayers(refreshed);
            }
        }
    }

    private boolean addPlayerToOverlay(String playerName) {
        IntelManager manager = IntelManager.getInstance();

        for (IntelPlayer player : manager.getPlayers()) {
            if (player.name.equalsIgnoreCase(playerName)) {
                return false;
            }
        }

        manager.addManualPlayer(playerName);
        return true;
    }

    private static String detectDefaultLogPath() {
        String os = System.getProperty("os.name", "").toLowerCase();

        if (os.contains("mac")) {
            return "/Library/Application Support/PrismLauncher/instances/Forge/minecraft/logs/latest.log";
        }

        if (os.contains("win")) {
            return "\\AppData\\Roaming\\PrismLauncher\\instances\\Forge\\minecraft\\logs\\latest.log";
        }

        return "/.local/share/PrismLauncher/instances/Forge/minecraft/logs/latest.log";
    }
}
