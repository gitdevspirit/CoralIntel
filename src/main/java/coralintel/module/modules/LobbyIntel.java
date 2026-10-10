package coralintel.module.modules;

import coralintel.event.EventTarget;
import coralintel.event.types.EventType;
import coralintel.events.KeyEvent;
import coralintel.events.LoadWorldEvent;
import coralintel.events.TickEvent;
import coralintel.events.PacketEvent;
import coralintel.events.Render2DEvent;
import coralintel.module.BooleanSetting;
import coralintel.module.DropdownSetting;
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
import coralintel.util.PregameUtil;
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
    public final BooleanSetting autoWho = register(new BooleanSetting("Auto /who", true));
    public final BooleanSetting trackPregameChat =
            register(new BooleanSetting("Track Pregame Chat", true));
    // Someone typing in the pregame lobby is always added to the Intel HUD right away.
    // On: they ALSO get an automatic .bw-style stats line in chat.
    public final BooleanSetting pregameChatAutoBw =
            register(new BooleanSetting("Pregame Chat: Auto .bw", false));
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
            register(new KeybindSetting("Open ClickGUI Key", Keyboard.KEY_RCONTROL));

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
    public final BooleanSetting bordicPrimary =
            register(new BooleanSetting("Bordic Stats (Keyless)", false));
    public final BooleanSetting bordicFallback =
            register(new BooleanSetting("Keyless Fallback (Bordic)", false));
    // 0 = off. Re-queuing into players seen within this many minutes reuses their
    // saved stats (disk cache) instead of fetching again.
    public final coralintel.module.SliderSetting statCacheMinutes =
            register(new coralintel.module.SliderSetting("Stat Cache (minutes, 0=off)", 15, 0, 30, 1));
    public final BooleanSetting skipSelfStats =
            register(new BooleanSetting("Skip Own Stats (Tab/HUD)", false));
    public final BooleanSetting tabShowNick =
            register(new BooleanSetting("Tab: Show [NICK] Tag", true));
    // Denick through Bedlify (needs a key: .bedlify key <key>).
    public final BooleanSetting denick =
            register(new BooleanSetting("Denick Nicks (Bedlify)", true));
    public final BooleanSetting denickChat =
            register(new BooleanSetting("Denick: Chat Alert", true));
    public final BooleanSetting tabShowRealName =
            register(new BooleanSetting("Tab: Show Denicked Name", true));
    public final BooleanSetting tabShowRealStats =
            register(new BooleanSetting("Tab: Show Denicked Stats", true));
    // Bed Wars shop: a left click on a category-page item also sends the middle click (adds it to Quick Buy).
    public final BooleanSetting shopAutoMiddle =
            register(new BooleanSetting("Shop: Auto Middle-Click (Quick Buy)", false));
    public final BooleanSetting tabShowHp =
            register(new BooleanSetting("Tab: Show HP", true));

    // Normal (non-overlay) tab list. Everything else lives in the overlay's column settings.
    public final BooleanSetting tabShowStar =
            register(new BooleanSetting("Tab: Show Star", true));
    public final BooleanSetting tabShowFkdr =
            register(new BooleanSetting("Tab: Show FKDR", true));
    public final BooleanSetting tabShowWlr =
            register(new BooleanSetting("Tab: Show WLR", true));

    private static final String[] COLOR_PALETTE_NAMES = {
            "Default", "Black", "Dark Blue", "Pink", "Red", "Green", "Purple", "White"
    };
    private static final int[] COLOR_PALETTE_BG = {
            0x07070E, 0x000000, 0x0A0A2A, 0x2A0A1A, 0x2A0A0A, 0x0A2A0F, 0x1A0A2A, 0x1A1A1A
    };
    private static final int[] COLOR_PALETTE_ACCENT = {
            0xFFFFFF, 0x000000, 0x4488FF, 0xE991B8, 0xFF5555, 0x55FF55, 0xAA55FF, 0xFFFFFF
    };

    public final DropdownSetting bgColorChoice =
            register(new DropdownSetting("HUD: Background Color", 0, COLOR_PALETTE_NAMES));
    public final DropdownSetting borderColorChoice =
            register(new DropdownSetting("HUD: Border Color", 0, COLOR_PALETTE_NAMES));
    public final DropdownSetting columnColorChoice =
            register(new DropdownSetting("HUD: Column Color", 0, COLOR_PALETTE_NAMES));

    // Visual style of the Intel HUD. Same columns and stats in every style; players are always grouped by team.
    public final DropdownSetting hudStyle =
            register(new DropdownSetting("HUD: Style", 0, "Classic", "Minimal", "Striped", "Cards", "Outline", "Heatmap"));

    /** Public so the tab mixin (different package) can read it directly. */
    public static final int[] TAB_BG_PALETTE = COLOR_PALETTE_BG;

    public final DropdownSetting tabBgColorChoice =
            register(new DropdownSetting("Tab: Background Color", 0, COLOR_PALETTE_NAMES));
    // Defaults to 33 (0x21) — matches vanilla's own hardcoded tab row alpha
    // exactly, so out of the box this changes nothing until adjusted.
    public final coralintel.module.SliderSetting tabBgOpacity =
            register(new coralintel.module.SliderSetting("Tab: Background Opacity", 33, 0, 255, 1));

    // ---- Seraph-style Bed Wars tab overlay (coralintel.ui.tab.TabOverlay) ----
    public final BooleanSetting tabOverlay =
            register(new BooleanSetting("Tab: Overlay (Bed Wars)", true));
    public final coralintel.module.SliderSetting tabOverlaySize =
            register(new coralintel.module.SliderSetting("Tab: Overlay Size %", 75, 50, 150, 5));
    public final coralintel.module.SliderSetting tabOverlayBgOpacity =
            register(new coralintel.module.SliderSetting("Tab: Overlay Background Opacity %", 50, 0, 100, 5));
    public final BooleanSetting tabOverlayShadow =
            register(new BooleanSetting("Tab: Overlay Text Shadow", true));
    public final BooleanSetting tabGrayOwnTeam =
            register(new BooleanSetting("Tab: Gray Own Team", false));
    public final BooleanSetting tabRespawnTimer =
            register(new BooleanSetting("Tab: Respawn Timer", true));
    public final BooleanSetting tabKeepDisconnected =
            register(new BooleanSetting("Tab: Keep Disconnected", true));
    public final DropdownSetting tabCol1 = register(new DropdownSetting("Tab: Column 1", 0, coralintel.ui.tab.TabOverlay.COLUMN_OPTIONS));
    public final DropdownSetting tabCol2 = register(new DropdownSetting("Tab: Column 2", 1, coralintel.ui.tab.TabOverlay.COLUMN_OPTIONS));
    public final DropdownSetting tabCol3 = register(new DropdownSetting("Tab: Column 3", 2, coralintel.ui.tab.TabOverlay.COLUMN_OPTIONS));
    public final DropdownSetting tabCol4 = register(new DropdownSetting("Tab: Column 4", 3, coralintel.ui.tab.TabOverlay.COLUMN_OPTIONS));
    public final DropdownSetting tabCol5 = register(new DropdownSetting("Tab: Column 5", 4, coralintel.ui.tab.TabOverlay.COLUMN_OPTIONS));
    public final DropdownSetting tabCol6 = register(new DropdownSetting("Tab: Column 6", 5, coralintel.ui.tab.TabOverlay.COLUMN_OPTIONS));

    /** Applies the current color-dropdown selections to the overlay — called every frame, cheap. */
    private void syncOverlayColors() {
        int bgIndex = bgColorChoice.getIndex();
        hudOverlay.setBgColorRgb(COLOR_PALETTE_BG[bgIndex]);

        int borderIndex = borderColorChoice.getIndex();
        hudOverlay.setBorderColorRgb(COLOR_PALETTE_ACCENT[borderIndex]);

        int columnIndex = columnColorChoice.getIndex();
        hudOverlay.setColumnColorRgb(COLOR_PALETTE_ACCENT[columnIndex]);
        hudOverlay.setStyle(hudStyle.getValue());
    }

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
    public final IntProperty hudColumnWidth =
            new IntProperty("hud-column-width", 100, 100, 200);
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
    // Set at "The game starts in 1 second": the pregame roster has been cleared and
    // further chat is ignored until the next world load.
    private boolean pregameClosed = false;
    private boolean pendingArenaWho = false;
    private int retryTickCounter = 0;

    // Players who typed in the PREGAME lobby chat (lowercase name -> name).
    // They are added to the roster straight away so their stats load early;
    // cleared on every world change.
    private final java.util.Map<String, String> chatTracked = new java.util.concurrent.ConcurrentHashMap<>();
    // Nicked players who typed in pregame (lowercase names). Announced once each
    // and shown on the HUD until they leave / the countdown rescan takes over.
    // Players already auto-checked with .bw this lobby (lowercase), so each is looked up once.
    private final java.util.Set<String> chatBwLooked =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private final coralintel.command.commands.BedwarsStatsCommand autoBw =
            new coralintel.command.commands.BedwarsStatsCommand();
    private final java.util.Set<String> chatNicks =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    // "[142✫] [MVP+] Name: hello" or "Name: hello". Party / guild / PM lines
    // start with "Party >", "Guild >", "To ..." so they never match.
    private static final Pattern PREGAME_CHAT =
            Pattern.compile("^(?:\\[[^\\]]*\\]\\s*)*([A-Za-z0-9_]{1,16}):\\s.+$");
    // "[MVP+] Name has quit!" (printed in the pregame lobby when someone leaves)
    private static final Pattern PREGAME_QUIT =
            Pattern.compile("^(?:\\[[^\\]]*\\]\\s*)*([A-Za-z0-9_]{1,16}) has quit!");
    // System lines that look like "Word: text" but aren't a player talking.
    private static final java.util.Set<String> NOT_PLAYER_NAMES = new java.util.HashSet<>(
            java.util.Arrays.asList("online", "tip", "note", "warning", "reminder", "team", "teams"));

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
        hudOverlay.setColumnWidthPercent(hudColumnWidth.getValue());
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
        hudColumnWidth.setValue(hudOverlay.getColumnWidthPercent());
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
            syncOverlayColors();
            hudOverlay.render();
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        // The message-based "starts in 1 second" /who races the actual
        // world transition into the arena — Hypixel loads a new world right
        // around the same moment the match begins, and if that world-load's
        // clearAll() below lands after the /who response already populated
        // the roster, it wipes it straight back out. That race is the real
        // cause of "/who doesn't load stats at the start of the game".
        //
        // This is the reliable fix: if we were mid-countdown in the previous
        // world (pendingArenaWho), treat THIS world load as "we just entered
        // the arena" and fire /who anchored to it instead — scheduled AFTER
        // clearAll() below has already run, so there's no race left to lose.
        boolean shouldSendArenaWho = pendingArenaWho && autoScan.getValue() && autoWho.getValue();
        pendingArenaWho = false;

        scannedThisSession = false;
        finalWhoSent = false;
        pregameClosed = false;
        retryTickCounter = 0;
        chatTracked.clear();
        chatNicks.clear();
        chatBwLooked.clear();
        IntelManager.getInstance().clearAll();

        if (autoKey.getValue()) {
            tryAutoDetectKey();
        }

        if (shouldSendArenaWho) {
            IntelManager.dbg("[Intel] Arena world loaded — rescanning + requesting /who in 1.5s.");

            new Thread(() -> {
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException ignored) {
                    return;
                }

                mc.addScheduledTask(() -> {
                    // Team assignments are only visible in the tab list once
                    // the match has actually started — scanLobby() is the
                    // only place that refreshes IntelPlayer.team for already-
                    // tracked players, so this is what actually fixes team
                    // colors in the overlay/tab, not just the /who call below
                    // (which only ever creates new entries with team=null
                    // and never touches existing ones).
                    IntelManager.getInstance().scanLobby();

                    if (mc.thePlayer != null) {
                        mc.thePlayer.sendChatMessage("/who");
                    }
                });
            }).start();
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() != EventType.PRE) return;
        if (mc.theWorld == null || mc.thePlayer == null) return;

        retryTickCounter++;

        // Every 0.5s: sync teams from the tab list so the HUD re-sorts as soon
        // as teams are assigned (the full rescan below only runs every 10s).
        if (retryTickCounter % 10 == 0) {
            IntelManager.getInstance().refreshTeams();
        }

        // 20 ticks/sec — every 200 ticks is 10 seconds.
        if (retryTickCounter >= 200) {
            retryTickCounter = 0;
            IntelManager.getInstance().retryFailedFetches();

            // Keeps IntelPlayer.team current throughout the match (not just
            // once at game-start) — scanLobby() safely updates existing
            // players' team without re-fetching their stats, so this is
            // cheap and keeps overlay/tab team colors accurate if anything
            // changes mid-game.
            if (autoScan.getValue()) {
                IntelManager.getInstance().scanLobby();
            }
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

        if (trackPregameChat.getValue() && packet.getType() != 2) {
            handlePregameChat(message);
        }

        if (autoScan.getValue()
                && !scannedThisSession
                && message.contains("The game starts in 10 seconds")) {

            scannedThisSession = true;
            IntelManager.dbg("[Intel] BedWars countdown detected — scanning lobby and requesting /who.");

            mc.addScheduledTask(() -> {
                if (autoKey.getValue()) {
                    tryAutoDetectKey();
                }

                // Players who typed in the pregame lobby and haven't been seen
                // leaving ("has quit!") go straight back into the roster after
                // the rebuild. They come back with whatever stats were already
                // loaded, so nothing is fetched twice. If /who runs below, its
                // list then replaces the roster, which drops anyone who left
                // without a quit message.
                java.util.List<String> typedInPregame = new java.util.ArrayList<>(chatTracked.values());
                chatTracked.clear();
                chatNicks.clear(); // the tab scan below re-detects nicks from their tab UUID

                IntelManager.getInstance().clearAll();
                IntelManager.getInstance().scanLobby();

                String self = mc.thePlayer != null ? mc.thePlayer.getName() : "";
                for (String typed : typedInPregame) {
                    if (!typed.equalsIgnoreCase(self)) {
                        IntelManager.getInstance().addManualPlayer(typed);
                    }
                }

                if (autoWho.getValue() && mc.thePlayer != null) {
                    mc.thePlayer.sendChatMessage("/who");
                }
            });
        }

        // 1 second to go: clear everyone off the HUD. Loaded stats are stashed
        // (soft clear), so the arena roster comes back with them already in.
        if (!pregameClosed && message.contains("The game starts in 1 second")) {
            pregameClosed = true;
            mc.addScheduledTask(() -> {
                chatTracked.clear();
                chatNicks.clear();
                IntelManager.getInstance().clearAll();
                IntelManager.dbg("[Intel] 1 second to go — HUD cleared.");
            });
        }

        // A second /who right as the match actually begins — team
        // assignments are only finalized by this point, so this catches
        // anyone the 10-second scan's roster missed or had stale team data
        // for. Delayed by 1s after the message so it fires just after the
        // world/team state has actually settled, not mid-transition.
        if (autoScan.getValue()
                && autoWho.getValue()
                && !finalWhoSent
                && message.contains("The game starts in 1 second")) {

            finalWhoSent = true;
            pendingArenaWho = true;
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
                manager.retainLoadedPlayers(); // keep loaded stats; /who only re-lists the roster
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

    /**
     * Pregame lobby only: someone typing in chat is added to the roster right
     * away (their stats start loading immediately), and a "has quit!" line
     * removes them again. General-lobby chat is ignored; the pregame check
     * reads the sidebar on the main thread.
     */
    private void handlePregameChat(String rawMessage) {
        final String message = rawMessage == null ? "" : rawMessage.trim();
        if (pregameClosed) return;

        Matcher quit = PREGAME_QUIT.matcher(message);
        if (quit.find()) {
            final String leaver = quit.group(1);

            String leaverKey = leaver.toLowerCase(java.util.Locale.ROOT);
            boolean wasTracked = chatTracked.remove(leaverKey) != null;
            boolean wasNick = chatNicks.remove(leaverKey);

            if (wasTracked || wasNick) {
                mc.addScheduledTask(() -> {
                    IntelManager.getInstance().forgetPlayer(leaver);
                    removePlayerFromOverlay(leaver);
                    IntelManager.dbg("[Intel] " + leaver + " left the pregame lobby — removed.");
                });
            }
            return;
        }

        Matcher chat = PREGAME_CHAT.matcher(message);
        if (!chat.matches()) {
            if (message.contains(": ")) {
                IntelManager.dbg("[Intel] chat line not recognised as a player message: "
                        + (message.length() > 90 ? message.substring(0, 90) + "..." : message));
            }
            return;
        }

        final String name = chat.group(1);
        if (NOT_PLAYER_NAMES.contains(name.toLowerCase(java.util.Locale.ROOT))) return;
        if (mc.thePlayer != null && name.equalsIgnoreCase(mc.thePlayer.getName())) return;

        mc.addScheduledTask(() -> trackChatPlayer(name));
    }

    private void trackChatPlayer(String name) {
        if (!trackPregameChat.getValue()) return;
        IntelManager.dbg("[Intel] chat from " + name + " — checking pregame lobby.");

        if (!PregameUtil.isPregameLobby()) {
            IntelManager.dbg("[Intel] chat from " + name + " ignored: pregame sidebar not detected.");
            return;
        }

        if (mc.getNetHandler() != null) {
            net.minecraft.client.network.NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(name);

            if (info != null) {
                if (IntelManager.isNpc(info)) {
                    IntelManager.dbg("[Intel] chat from " + name + " ignored: tab entry looks like an NPC.");
                    return;
                }

                // A nick's tab UUID is version 1 (Mellow's check). Never look a nick
                // up as if it were a real account — a stranger may own that name.
                // Announce it once client-side and put it on the HUD instead.
                java.util.UUID id = info.getGameProfile().getId();
                if (id != null && id.version() == 1) {
                    if (chatNicks.add(name.toLowerCase(java.util.Locale.ROOT))) {
                        ChatUtil.sendFormatted("&5[NICK] &f" + name + " &7is nicked.");
                        IntelManager.dbg("[Intel] " + name + " typed in the pregame lobby but is nicked.");
                    }

                    IntelManager.getInstance().addNickedChatter(name);
                    return;
                }
            }
        }

        boolean firstTime = chatTracked.put(name.toLowerCase(java.util.Locale.ROOT), name) == null;
        if (firstTime) {
            IntelManager.dbg("[Intel] " + name + " typed in the pregame lobby — added to roster.");
        }

        // On the HUD straight away with stats loading; stays until "starts in 1
        // second". No-op if already tracked.
        IntelManager.getInstance().addPregameChatter(name);

        // Pregame tab names are obfuscated, so the tab can't vouch for this chatter. Check their
        // account by UUID in the background: no account = nick, otherwise match their tab entry.
        boolean inTab = mc.getNetHandler() != null && mc.getNetHandler().getPlayerInfo(name) != null;
        if (firstTime && !inTab) {
            verifyChatterAccount(name);
        }

        // Optional extra: also print their stats in chat (once per player per lobby; recently
        // cached stats are reused to spare the API).
        if (pregameChatAutoBw.getValue() && chatBwLooked.add(name.toLowerCase(java.util.Locale.ROOT))) {
            IntelManager.dbg("[Intel] " + name + " typed in the pregame lobby — auto .bw.");
            autoBw.lookup(name, false, true);
        }
    }

    private void verifyChatterAccount(final String name) {
        new Thread(() -> {
            final IntelManager.AccountCheck result = IntelManager.getInstance().checkAccount(name);

            mc.addScheduledTask(() -> {
                if (pregameClosed) return;
                // They left (quit line) while the lookup was running.
                if (!chatTracked.containsKey(name.toLowerCase(java.util.Locale.ROOT))) return;

                if (result == IntelManager.AccountCheck.NOT_FOUND) {
                    if (chatNicks.add(name.toLowerCase(java.util.Locale.ROOT))) {
                        ChatUtil.sendFormatted("&5[NICK] &f" + name + " &7is nicked.");
                    }
                    IntelManager.getInstance().addNickedChatter(name);
                } else if (result == IntelManager.AccountCheck.EXISTS) {
                    IntelManager.getInstance().useTabSkinByUuid(name);
                }
            });
        }, "CoralIntel-ChatterCheck").start();
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
