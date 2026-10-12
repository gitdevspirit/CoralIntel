package coralintel.module.modules;

import coralintel.command.commands.DuelsStatsCommand;
import coralintel.duels.DuelsHudOverlay;
import coralintel.duels.DuelsManager;
import coralintel.event.EventTarget;
import coralintel.event.types.EventType;
import coralintel.events.KeyEvent;
import coralintel.events.LoadWorldEvent;
import coralintel.events.PacketEvent;
import coralintel.events.Render2DEvent;
import coralintel.events.TickEvent;
import coralintel.module.BooleanSetting;
import coralintel.module.DropdownSetting;
import coralintel.module.KeybindSetting;
import coralintel.module.Module;
import coralintel.module.SliderSetting;
import coralintel.ui.intel.IntelManager;
import coralintel.util.ChatUtil;
import coralintel.util.PregameUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.util.IChatComponent;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Duels lobby intel. The Duels counterpart of LobbyIntel: its own roster, HUD, stat cache and settings, and it
 * shows Duels stats (rank, WLR, KDR, winstreaks) for Overall, Classic, Bridge and Bedwars Duels. It never touches
 * the Bedwars roster or Bedwars stats.
 */
public class DuelsIntel extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    // ---- lobby tracking
    public final BooleanSetting autoScan = register(new BooleanSetting("Auto Scan (Tab)", true));
    public final BooleanSetting trackChat = register(new BooleanSetting("Track Lobby Chat", true));
    public final BooleanSetting chatAutoStats = register(new BooleanSetting("Chat: Auto .duels", false));
    public final BooleanSetting skipSelfStats = register(new BooleanSetting("Skip Own Stats", false));
    public final BooleanSetting denick = register(new BooleanSetting("Denick Nicks (Bedlify)", true));
    public final BooleanSetting nickAlert = register(new BooleanSetting("Nick Chat Alert", true));

    // ---- stat sources
    public final BooleanSetting bordicPrimary = register(new BooleanSetting("Bordic Stats (Keyless)", false));
    public final BooleanSetting bordicFallback = register(new BooleanSetting("Keyless Fallback (Bordic)", false));
    /** 0 = off. Re-queuing into players seen within this many minutes reuses their saved stats. */
    public final SliderSetting statCacheMinutes =
            register(new SliderSetting("Stat Cache (minutes, 0=off)", 15, 0, 30, 1));

    // ---- HUD
    public final BooleanSetting hudEnabled = register(new BooleanSetting("HUD: Enabled", true));
    public final KeybindSetting hudKeybind = register(new KeybindSetting("HUD Toggle Key", 0));
    public final DropdownSetting hudGame =
            register(new DropdownSetting("HUD: Stats Game", 0, "Overall", "Classic", "Bridge", "Bedwars Duels"));
    public final DropdownSetting hudSort =
            register(new DropdownSetting("HUD: Sort", 0, "Wins", "WLR", "KDR", "Name"));
    public final DropdownSetting hudStyle =
            register(new DropdownSetting("HUD: Style", 0, "Classic", "Minimal", "Glass", "Outline"));
    public final DropdownSetting hudColor =
            register(new DropdownSetting("HUD: Color", 0, "Default", "Black", "Dark Blue", "Pink", "Red", "Green",
                    "Purple", "Orange"));
    public final SliderSetting hudX = register(new SliderSetting("HUD: X", 10, 0, 1920, 1));
    public final SliderSetting hudY = register(new SliderSetting("HUD: Y", 100, 0, 1080, 1));
    public final SliderSetting hudScale = register(new SliderSetting("HUD: Scale %", 100, 50, 200, 5));
    public final SliderSetting hudMaxPlayers = register(new SliderSetting("HUD: Max Players", 10, 1, 20, 1));
    public final SliderSetting hudBgOpacity = register(new SliderSetting("HUD: Background Opacity", 180, 0, 255, 5));
    public final SliderSetting hudColumnWidth = register(new SliderSetting("HUD: Column Width %", 100, 100, 200, 5));
    public final BooleanSetting hudShowRank = register(new BooleanSetting("HUD: Show Rank", true));
    public final BooleanSetting hudShowWins = register(new BooleanSetting("HUD: Show Wins", true));
    public final BooleanSetting hudShowWlr = register(new BooleanSetting("HUD: Show WLR", true));
    public final BooleanSetting hudShowKdr = register(new BooleanSetting("HUD: Show KDR", true));
    public final BooleanSetting hudShowStreak = register(new BooleanSetting("HUD: Show Winstreak", true));
    public final BooleanSetting hudShowBest = register(new BooleanSetting("HUD: Show Best Winstreak", false));

    // ---- .duels chat output
    public final BooleanSetting chatShowRank = register(new BooleanSetting("Duels: Show Rank", true));
    public final BooleanSetting chatShowWins = register(new BooleanSetting("Duels: Show Wins/Losses", true));
    public final BooleanSetting chatShowWlr = register(new BooleanSetting("Duels: Show WLR", true));
    public final BooleanSetting chatShowKdr = register(new BooleanSetting("Duels: Show KDR", true));
    public final BooleanSetting chatShowStreak = register(new BooleanSetting("Duels: Show Winstreaks", true));

    private static final int[] COLOR_BG = {0x07070E, 0x000000, 0x0A0A2A, 0x2A0A1A, 0x2A0A0A, 0x0A2A0F, 0x1A0A2A, 0x2A1A06};
    private static final int[] COLOR_ACCENT = {0xFFFFFF, 0xAAAAAA, 0x4488FF, 0xE991B8, 0xFF5555, 0x55FF55, 0xAA55FF, 0xFFAA00};

    private final DuelsHudOverlay hud = new DuelsHudOverlay();
    private final DuelsStatsCommand autoStats = new DuelsStatsCommand();

    // Players who typed in chat this world (lowercase name -> name).
    private final java.util.Map<String, String> chatTracked = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<String> chatNicks =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private final java.util.Set<String> chatLooked =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private final java.util.Set<String> nickAnnounced =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    private static final Pattern CHAT_LINE =
            Pattern.compile("^(?:\\[[^\\]]*\\]\\s*)*([A-Za-z0-9_]{1,16}):\\s.+$");
    private static final Pattern QUIT_LINE =
            Pattern.compile("^(?:\\[[^\\]]*\\]\\s*)*([A-Za-z0-9_]{1,16}) has quit!");
    private static final java.util.Set<String> NOT_PLAYER_NAMES = new java.util.HashSet<>(
            java.util.Arrays.asList("online", "tip", "note", "warning", "reminder", "team", "teams"));

    private int tickCounter = 0;
    private boolean pregameLinesSeen = false;

    public DuelsIntel() {
        super("DuelsIntel", true);
    }

    public DuelsHudOverlay getHud() {
        return hud;
    }

    /** Pushes the current settings onto the overlay. Cheap; called every frame. */
    private void syncHud() {
        hud.enabled = hudEnabled.getValue();
        hud.x = (int) hudX.getValue();
        hud.y = (int) hudY.getValue();
        hud.scale = (float) (hudScale.getValue() / 100.0);
        hud.maxPlayers = (int) hudMaxPlayers.getValue();
        hud.bgOpacity = (int) hudBgOpacity.getValue();
        hud.columnWidthPercent = (int) hudColumnWidth.getValue();
        hud.game = hudGame.getIndex();
        hud.sort = hudSort.getValue().toLowerCase(Locale.ROOT);
        hud.style = hudStyle.getValue().toLowerCase(Locale.ROOT);
        int c = Math.max(0, Math.min(COLOR_BG.length - 1, hudColor.getIndex()));
        hud.bgRgb = COLOR_BG[c];
        hud.accentRgb = COLOR_ACCENT[c];
        hud.showRank = hudShowRank.getValue();
        hud.showWins = hudShowWins.getValue();
        hud.showWlr = hudShowWlr.getValue();
        hud.showKdr = hudShowKdr.getValue();
        hud.showStreak = hudShowStreak.getValue();
        hud.showBestStreak = hudShowBest.getValue();
    }

    // ------------------------------------------------------------------ events

    @EventTarget
    public void onKey(KeyEvent event) {
        if (!isEnabled() || mc.currentScreen != null) return;
        int key = hudKeybind.getKeyCode();
        if (key != 0 && event.getKey() == key) {
            hudEnabled.setValue(!hudEnabled.getValue());
            ChatUtil.sendFormatted("&7[Duels] HUD: " + (hudEnabled.getValue() ? "&a&lON" : "&c&lOFF"));
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!isEnabled() || mc.currentScreen != null) return;
        syncHud();
        if (hud.isEnabled()) hud.render();
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        tickCounter = 0;
        pregameLinesSeen = false;
        chatTracked.clear();
        chatNicks.clear();
        chatLooked.clear();
        nickAnnounced.clear();
        DuelsManager.getInstance().clearAll();
        if (!isEnabled() || !autoScan.getValue()) return;

        // The tab list fills in a moment after the world loads.
        new Thread(() -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ignored) {
                return;
            }
            mc.addScheduledTask(() -> DuelsManager.getInstance().scanLobby());
        }, "CoralIntel-DuelsScan").start();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.theWorld == null || mc.thePlayer == null) return;

        tickCounter++;
        // Every 10 seconds: pick up new tab entries and retry players whose stats didn't load.
        if (tickCounter >= 200) {
            tickCounter = 0;
            DuelsManager.getInstance().retryUnloaded();
            if (autoScan.getValue()) DuelsManager.getInstance().scanLobby();
        }
        announceNicks();
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!isEnabled() || event.getType() != EventType.RECEIVE) return;
        if (!(event.getPacket() instanceof S02PacketChat)) return;

        S02PacketChat packet = (S02PacketChat) event.getPacket();
        if (packet.getType() == 2) return; // action bar
        IChatComponent component = packet.getChatComponent();
        if (component == null) return;

        final String plain = net.minecraft.util.StringUtils.stripControlCodes(component.getUnformattedText()).trim();
        if (plain.contains("The game starts in") || plain.matches("^[+\\-\\u2212\\u2013]\\s*\\(\\d+/\\d+\\).*")) {
            pregameLinesSeen = true;
        }
        if (!trackChat.getValue()) return;

        Matcher quit = QUIT_LINE.matcher(plain);
        if (quit.find()) {
            final String leaver = quit.group(1);
            String key = leaver.toLowerCase(Locale.ROOT);
            boolean was = chatTracked.remove(key) != null;
            chatNicks.remove(key);
            if (was) mc.addScheduledTask(() -> DuelsManager.getInstance().forgetPlayer(leaver));
            return;
        }

        Matcher chat = CHAT_LINE.matcher(plain);
        if (!chat.matches()) return;
        final String name = chat.group(1);
        if (NOT_PLAYER_NAMES.contains(name.toLowerCase(Locale.ROOT))) return;
        if (mc.thePlayer != null && name.equalsIgnoreCase(mc.thePlayer.getName())) return;

        mc.addScheduledTask(() -> {
            try {
                trackChatter(name);
            } catch (Throwable t) {
                IntelManager.dbg("[Duels] chat tracking crashed for " + name + ": " + t);
            }
        });
    }

    // ------------------------------------------------------------------ chatters

    private void trackChatter(String name) {
        // Only in a lobby where chat is a roster signal (pregame lobby, or the lobby lines were seen).
        if (!PregameUtil.isPregameLobby() && !pregameLinesSeen) return;

        String key = name.toLowerCase(Locale.ROOT);
        NetworkPlayerInfo info = mc.getNetHandler() != null ? mc.getNetHandler().getPlayerInfo(name) : null;
        if (info != null) {
            if (IntelManager.isNpc(info)) return;
            java.util.UUID id = info.getGameProfile().getId();
            if (id != null && id.version() == 1) { // a nick's tab UUID is version 1
                chatNicks.add(key);
                DuelsManager.getInstance().addNickedChatter(name);
                return;
            }
        }

        boolean first = chatTracked.put(key, name) == null;
        if (!DuelsManager.getInstance().addPlayer(name)) return;

        boolean inTab = info != null;
        if (first && !inTab) verifyChatterAccount(name);

        if (chatAutoStats.getValue() && chatLooked.add(key)) {
            autoStats.lookup(name, false);
        }
    }

    /** Pregame tab names can be hidden: check the chatter's account by UUID instead (no account = nick). */
    private void verifyChatterAccount(final String name) {
        new Thread(() -> {
            final IntelManager.AccountCheck result = IntelManager.getInstance().checkAccount(name);
            mc.addScheduledTask(() -> {
                if (!chatTracked.containsKey(name.toLowerCase(Locale.ROOT))) return;
                if (result == IntelManager.AccountCheck.NOT_FOUND) {
                    chatNicks.add(name.toLowerCase(Locale.ROOT));
                    DuelsManager.getInstance().addNickedChatter(name);
                }
            });
        }, "CoralIntel-DuelsChatterCheck").start();
    }

    /** One client-side line per nick per world. */
    private void announceNicks() {
        if (!nickAlert.getValue()) return;
        for (coralintel.duels.DuelsPlayer p : DuelsManager.getInstance().getPlayers()) {
            if (p.isNicked && nickAnnounced.add(p.name.toLowerCase(Locale.ROOT))) {
                ChatUtil.sendFormatted("&5[NICK] &f" + p.name + " &7is nicked.");
            }
        }
    }
}
