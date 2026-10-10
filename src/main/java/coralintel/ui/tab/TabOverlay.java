package coralintel.ui.tab;

import coralintel.CoralIntel;
import coralintel.event.EventTarget;
import coralintel.events.TickEvent;
import coralintel.event.types.EventType;
import coralintel.module.modules.LobbyIntel;
import coralintel.module.modules.StreamerMode;
import coralintel.ui.intel.IntelManager;
import coralintel.ui.intel.IntelPlayer;
import coralintel.util.PrestigeUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EnumPlayerModelParts;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldSettings;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Seraph-style Bed Wars tab list. While you hold Tab in a running Bed Wars game this replaces the
 * vanilla list with its own panel: a translucent dark box with a label band (Stars, Name, WS, FKDR,
 * Finals, KDR ... plus HP), one row per player (head, stat columns, name), the server's tab header
 * and footer, and a right-hand status column that counts respawns down and marks disconnects (DC).
 *
 * Ported from the "Tab Stats" plugin. Differences: the stats come from CoralIntel's own scan
 * (IntelManager) instead of a Hypixel API key, heads come from the tab list's own skin textures
 * instead of downloaded PNGs, and only Bed Wars is handled (CoralIntel is a Bed Wars tool).
 *
 * Nothing here draws unless LobbyIntel's "Tab Stats" and "Tab: Overlay" settings are on and a
 * running Bed Wars game is detected, so lobbies and every other mode keep the vanilla tab list
 * (with CoralIntel's padded-text stats, as before).
 */
public final class TabOverlay {
    private static final Minecraft mc = Minecraft.getMinecraft();

    /** Order matters: index N of a column setting maps to COLUMNS[N]; the last entry is "Off". */
    public static final String[] COLUMN_OPTIONS = {
            "Stars", "Name", "Winstreak", "FKDR", "Finals", "KDR", "Wins", "WLR", "Beds", "BBLR", "Off"
    };

    private enum Col {
        STARS("Stars"), NAME("Name"), WS("WS"), FKDR("FKDR"), FINALS("Finals"), KDR("KDR"),
        WINS("Wins"), WLR("WLR"), BEDS("Beds"), BBLR("BBLR");

        final String header;

        Col(String header) {
            this.header = header;
        }
    }

    // Layout, in "units" (one unit is one Minecraft font pixel at 100% size).
    private static final float TOP = 10f;
    private static final float PAD = 8f;
    private static final float GAP = 17f;
    private static final float NAME_GAP = 32f;
    private static final float HEAD = 8f;
    private static final float HEAD_GAP = 8f;
    private static final float NAME_TAIL = 2f;
    private static final float ROW = 35f / 3f;
    private static final float ROW_TEXT = 2f;
    private static final float LINE = 15f;
    private static final float LINE_TEXT = 2.5f;
    /** Panel height is this many units per GUI pixel of screen height (reference design). */
    private static final double UNITS_PER_HEIGHT = 440.0;

    private static final int PANEL_COLOR = 0x80000000;
    private static final int LABEL_BAND_COLOR = 0x10FFFFFF;
    private static final int ROWS_COLOR = 0x08FFFFFF;
    private static final int MISSING_HEAD_COLOR = 0xFF3C3C3C;
    private static final String HEADER_COLOR = "§f";
    private static final String LIST_LABEL = "HP";

    private static final int RESPAWN_SECONDS = 5;
    private static final int RECONNECT_RESPAWN_SECONDS = 10;
    private static final long RESPAWN_CONFIRM_GRACE_MS = 500L;
    private static final long GHOST_WINDOW_MS = 20000L;

    private static final Pattern LETTER_PREFIX = Pattern.compile("^\\p{Lu}\\s+$");
    private static final Pattern NAME_PATTERN = Pattern.compile("^\\w{1,16}$");
    private static final Pattern TEAM_ELIMINATED = Pattern.compile("^TEAM ELIMINATED > (\\w+) Team");
    private static final Pattern RECONNECTED = Pattern.compile("^(\\w+) reconnected\\.$");
    private static final Pattern DISCONNECTED = Pattern.compile("^(\\w+) disconnected\\.");
    private static final Pattern FIRST_WORD = Pattern.compile("^(\\w+) ");
    private static final Pattern TEAM_LINE = Pattern.compile("^\\p{Lu}\\s+\\w+:.*");
    private static final Pattern PREGAME_PLAYERS = Pattern.compile("^Players:\\s*\\d+\\s*/\\s*\\d+.*");
    private static final Pattern COLOR_CODE = Pattern.compile("§([0-9a-fA-F])");

    private static final Map<String, String> TEAM_COLORS = new HashMap<>();
    private static final String[] DEATH_PHRASES = {
            " was ", "fell into the void", "hit the ground too hard",
            "burned to death", "drowned", "went up in flames", " died"
    };

    static {
        TEAM_COLORS.put("Red", "§c");
        TEAM_COLORS.put("Blue", "§9");
        TEAM_COLORS.put("Green", "§a");
        TEAM_COLORS.put("Yellow", "§e");
        TEAM_COLORS.put("Aqua", "§b");
        TEAM_COLORS.put("White", "§f");
        TEAM_COLORS.put("Pink", "§d");
        TEAM_COLORS.put("Gray", "§8");
    }

    // ---- server tab header / footer (legacy "§" strings), fed by the tab mixin ----
    private static volatile String header;
    private static volatile String footer;

    public static void noteHeader(IChatComponent component) {
        header = component == null ? null : component.getFormattedText();
    }

    public static void noteFooter(IChatComponent component) {
        footer = component == null ? null : component.getFormattedText();
    }

    public static void clearHeaderFooter() {
        header = null;
        footer = null;
    }

    // ---- per-game state ----
    private static final class Snap {
        String name;
        UUID uuid;
        ResourceLocation skin;
        boolean hat;
        String teamName = "";
        String prefix = "";
        String suffix = "";
        boolean spectator;
    }

    private static final class Row {
        Snap snap;
        IntelPlayer stats;
        String nameText;
        Integer hp;
        String status;
        String[] cells;
    }

    private final Map<String, Snap> snaps = new LinkedHashMap<>();
    private final Map<String, Snap> ghosts = new LinkedHashMap<>();
    private final Map<String, Long> respawnEnd = new HashMap<>();
    private final Map<String, Long> confirmAt = new HashMap<>();
    private final Set<String> disconnected = new HashSet<>();
    private final Set<String> eliminated = new HashSet<>();
    private final Set<String> present = new HashSet<>();

    private WorldClient lastWorld;
    private boolean wasInGame;
    private long gameStartedAt;
    private boolean inGameCached;
    private long inGameCheckedAt;

    private static LobbyIntel lobbyIntel() {
        if (CoralIntel.moduleManager == null) return null;
        return (LobbyIntel) CoralIntel.moduleManager.getModule(LobbyIntel.class);
    }

    private static boolean enabled(LobbyIntel li) {
        return li != null && li.tabStats.getValue() && li.tabOverlay.getValue();
    }

    // ------------------------------------------------------------------------------------
    // per-tick bookkeeping (snapshots, timers, ghosts)
    // ------------------------------------------------------------------------------------

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() != EventType.PRE || mc.theWorld == null || mc.thePlayer == null) return;

        if (mc.theWorld != lastWorld) {
            lastWorld = mc.theWorld;
            clearGame();
            wasInGame = false;
            inGameCached = false;
            inGameCheckedAt = 0L;
        }

        LobbyIntel li = lobbyIntel();
        if (!enabled(li)) {
            if (wasInGame) clearGame();
            wasInGame = false;
            return;
        }

        boolean inGame = inBedwarsGame();
        if (inGame != wasInGame) {
            clearGame();
            if (inGame) gameStartedAt = System.currentTimeMillis();
            wasInGame = inGame;
        }
        if (!inGame) return;

        snapshotPlayers();
        detectGhosts();
        runTimers();
    }

    private void clearGame() {
        snaps.clear();
        ghosts.clear();
        respawnEnd.clear();
        confirmAt.clear();
        disconnected.clear();
        eliminated.clear();
        present.clear();
        gameStartedAt = 0L;
    }

    /** Tab-list entries for this server. */
    private static Collection<NetworkPlayerInfo> tabEntries() {
        if (mc.thePlayer == null || mc.thePlayer.sendQueue == null) return Collections.<NetworkPlayerInfo>emptyList();
        return mc.thePlayer.sendQueue.getPlayerInfoMap();
    }

    private static String plain(String text) {
        return text == null ? "" : EnumChatFormatting.getTextWithoutFormattingCodes(text);
    }

    private static boolean isLetterPrefix(String prefix) {
        return LETTER_PREFIX.matcher(plain(prefix)).matches();
    }

    private void snapshotPlayers() {
        present.clear();
        for (NetworkPlayerInfo info : new ArrayList<>(tabEntries())) {
            if (IntelManager.isNpc(info)) continue;
            String name = info.getGameProfile().getName();
            UUID uuid = info.getGameProfile().getId();
            if (name == null || uuid == null) continue;

            ScorePlayerTeam team = info.getPlayerTeam();
            String prefix = team == null ? "" : team.getColorPrefix();
            Snap snap = snaps.get(name);
            // New entries only count if they carry a Bed Wars team letter; this keeps lobby NPCs,
            // spectators and staff out of the table.
            if (snap == null && !isLetterPrefix(prefix)) continue;
            if (eliminated.contains(name)) continue;

            if (snap == null) {
                snap = new Snap();
                snap.name = name;
                snaps.put(name, snap);
                ghosts.remove(name);
                disconnected.remove(name);
            }
            snap.uuid = uuid;
            snap.skin = info.getLocationSkin();
            snap.teamName = team == null ? snap.teamName : team.getRegisteredName();
            if (team != null) {
                snap.prefix = team.getColorPrefix();
                snap.suffix = team.getColorSuffix();
            }
            snap.spectator = info.getGameType() == WorldSettings.GameType.SPECTATOR;
            EntityPlayer entity = mc.theWorld.getPlayerEntityByUUID(uuid);
            snap.hat = entity != null && entity.isWearing(EnumPlayerModelParts.HAT);
            present.add(name);
        }
    }

    /**
     * Hypixel keeps players who left before the start on their team, so right after the start any
     * team member missing from the tab is shown as DC.
     */
    private void detectGhosts() {
        if (gameStartedAt == 0L || System.currentTimeMillis() - gameStartedAt > GHOST_WINDOW_MS) return;
        Scoreboard sb = mc.theWorld.getScoreboard();
        String me = mc.thePlayer.getName();
        for (ScorePlayerTeam team : sb.getTeams()) {
            if (!isLetterPrefix(team.getColorPrefix())) continue;
            for (Object o : team.getMembershipCollection()) {
                String member = String.valueOf(o);
                if (!NAME_PATTERN.matcher(member).matches() || member.equals(me)) continue;
                if (present.contains(member) || snaps.containsKey(member) || ghosts.containsKey(member)
                        || eliminated.contains(member)) continue;
                Snap ghost = new Snap();
                ghost.name = member;
                ghost.teamName = team.getRegisteredName();
                ghost.prefix = team.getColorPrefix();
                ghost.suffix = team.getColorSuffix();
                ghosts.put(member, ghost);
                disconnected.add(member);
            }
        }
    }

    private void runTimers() {
        long now = System.currentTimeMillis();

        Iterator<Map.Entry<String, Long>> respawns = respawnEnd.entrySet().iterator();
        while (respawns.hasNext()) {
            Map.Entry<String, Long> e = respawns.next();
            if (now >= e.getValue()) {
                confirmAt.put(e.getKey(), now + RESPAWN_CONFIRM_GRACE_MS);
                respawns.remove();
            }
        }

        Iterator<Map.Entry<String, Long>> confirms = confirmAt.entrySet().iterator();
        while (confirms.hasNext()) {
            Map.Entry<String, Long> e = confirms.next();
            if (now < e.getValue()) continue;
            confirms.remove();
            String name = e.getKey();
            // Back from the countdown but still not in the tab list: they have disconnected.
            if (!respawnEnd.containsKey(name) && known(name) && !present.contains(name)) {
                disconnected.add(name);
            }
        }
    }

    private boolean known(String name) {
        return snaps.containsKey(name) || ghosts.containsKey(name);
    }

    // ------------------------------------------------------------------------------------
    // game detection (sidebar)
    // ------------------------------------------------------------------------------------

    /** A running (not pre-game) Bed Wars game, judged from the sidebar. Re-checked every 250 ms. */
    private boolean inBedwarsGame() {
        long now = System.currentTimeMillis();
        if (now - inGameCheckedAt < 250L) return inGameCached;
        inGameCheckedAt = now;
        inGameCached = false;
        try {
            Scoreboard sb = mc.theWorld.getScoreboard();
            ScoreObjective sidebar = sb.getObjectiveInDisplaySlot(1);
            if (sidebar == null) return false;
            String title = plain(sidebar.getDisplayName()).toUpperCase();
            if (!title.contains("BED WARS")) return false;

            boolean ingameLine = false;
            boolean pregame = false;
            for (Score score : sb.getSortedScores(sidebar)) {
                String owner = score.getPlayerName();
                if (owner == null || owner.startsWith("#")) continue;
                String line = plain(ScorePlayerTeam.formatPlayerName(sb.getPlayersTeam(owner), owner)).trim();
                if (PREGAME_PLAYERS.matcher(line).matches() || line.startsWith("Waiting") || line.startsWith("Starting in")) {
                    pregame = true;
                }
                if (line.startsWith("Map:") || line.startsWith("Mode:")
                        || (TEAM_LINE.matcher(line).matches()
                        && (line.contains("✓") || line.contains("✗") || line.matches(".*:\\s*\\d+.*")))) {
                    ingameLine = true;
                }
            }
            inGameCached = ingameLine && !pregame;
        } catch (Exception ignored) {
            inGameCached = false;
        }
        return inGameCached;
    }

    // ------------------------------------------------------------------------------------
    // chat events: deaths, respawns, disconnects, eliminations
    // ------------------------------------------------------------------------------------

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        if (!wasInGame || event.type == 2 || event.message == null) return;
        try {
            String message = plain(event.message.getUnformattedText()).trim();
            if (message.equals("You will respawn in 10 seconds!")) {
                onRejoin(false);
            } else if (message.equals("Your bed was destroyed so you are a spectator!")) {
                onRejoin(true);
            } else {
                handleGameChat(message);
            }
        } catch (Exception ignored) {
        }
    }

    private void onRejoin(boolean spectator) {
        if (mc.thePlayer == null) return;
        String me = mc.thePlayer.getName();
        if (spectator) {
            markEliminated(me);
        } else {
            trackRespawn(me, RECONNECT_RESPAWN_SECONDS);
        }
    }

    private void handleGameChat(String message) {
        // Player chat always contains ":".
        if (message.indexOf(':') >= 0) return;

        Matcher eliminatedTeam = TEAM_ELIMINATED.matcher(message);
        if (eliminatedTeam.find()) {
            String color = TEAM_COLORS.get(eliminatedTeam.group(1));
            if (color != null) markTeamEliminated(color);
            return;
        }

        Matcher reconnected = RECONNECTED.matcher(message);
        if (reconnected.find()) {
            if (known(reconnected.group(1))) trackRespawn(reconnected.group(1), RECONNECT_RESPAWN_SECONDS);
            return;
        }

        Matcher left = DISCONNECTED.matcher(message);
        if (left.find()) {
            String name = left.group(1);
            if (known(name)) {
                disconnected.add(name);
                if (message.endsWith("FINAL KILL!")) markEliminated(name);
            }
            return;
        }

        Matcher first = FIRST_WORD.matcher(message);
        if (!first.find() || !known(first.group(1))) return;
        String subject = first.group(1);
        if (message.endsWith("FINAL KILL!")) {
            markEliminated(subject);
        } else if (isDeathMessage(message)) {
            trackRespawn(subject, RESPAWN_SECONDS);
        }
    }

    private static boolean isDeathMessage(String message) {
        if (!message.endsWith(".")) return false;
        for (String phrase : DEATH_PHRASES) {
            if (message.contains(phrase)) return true;
        }
        return false;
    }

    private void trackRespawn(String name, int seconds) {
        if (eliminated.contains(name)) return;
        disconnected.remove(name);
        confirmAt.remove(name);
        respawnEnd.put(name, System.currentTimeMillis() + seconds * 1000L);
    }

    private void markEliminated(String name) {
        ghosts.remove(name);
        respawnEnd.remove(name);
        confirmAt.remove(name);
        disconnected.remove(name);
        snaps.remove(name);
        eliminated.add(name);
    }

    private void markTeamEliminated(String teamColor) {
        List<String> names = new ArrayList<>(snaps.keySet());
        names.addAll(ghosts.keySet());
        for (String name : names) {
            Snap snap = snaps.containsKey(name) ? snaps.get(name) : ghosts.get(name);
            if (snap != null && teamColor.equals(teamColorOf(snap.prefix))) markEliminated(name);
        }
    }

    /** The last colour code in a team prefix ("§c§lR §r§c" gives "§c"), or "" if none. */
    private static String teamColorOf(String prefix) {
        String color = "";
        Matcher m = COLOR_CODE.matcher(prefix == null ? "" : prefix);
        while (m.find()) color = "§" + m.group(1).toLowerCase();
        return color;
    }

    private String statusText(LobbyIntel li, String name) {
        if (disconnected.contains(name)) {
            return li.tabKeepDisconnected.getValue() ? "§cDC" : null;
        }
        Long end = respawnEnd.get(name);
        if (end != null && li.tabRespawnTimer.getValue()) {
            long remaining = (long) Math.ceil((end - System.currentTimeMillis()) / 1000.0);
            return "§c" + Math.max(0L, remaining) + "s";
        }
        return null;
    }

    // ------------------------------------------------------------------------------------
    // stat columns
    // ------------------------------------------------------------------------------------

    private static final String[] RAMP = {"f", "a", "e", "6", "c", "4"};

    private static String ramp(double value, double... thresholds) {
        String color = "7";
        for (int i = 0; i < thresholds.length; i++) {
            if (value >= thresholds[i]) color = RAMP[i];
        }
        return "§" + color;
    }

    private static double ratio(double pos, double neg) {
        return neg == 0 ? pos : pos / neg;
    }

    private static String ratioText(double value, double... thresholds) {
        return ramp(value, thresholds) + String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static String countText(int value, double... thresholds) {
        return ramp(value, thresholds) + value;
    }

    private String cell(Col col, IntelPlayer p, boolean hidden) {
        if (hidden || p == null || p.loading) return col == Col.STARS ? "§8[-✫]" : "§8-";
        if (p.isNicked || p.statsHidden) return col == Col.STARS ? "§7[?✫]" : "§7?";
        switch (col) {
            case STARS:
                return PrestigeUtil.format(p.star);
            case WS:
                return countText(p.winstreak, 1, 5, 10, 25, 50, 100);
            case FKDR:
                return ratioText(p.fkdr, 1, 3, 6, 10, 20, 50);
            case FINALS:
                return countText(p.finalKills, 1000, 2500, 5000, 10000, 25000, 50000);
            case KDR:
                return ratioText(ratio(p.kills, p.deaths), 1, 1.5, 2.5, 4, 6, 10);
            case WINS:
                return countText(p.wins, 100, 500, 1000, 2500, 5000, 10000);
            case WLR:
                return ratioText(p.wlr, 1, 1.5, 2.5, 4, 6, 10);
            case BEDS:
                return countText(p.bedsBroken, 500, 1000, 2500, 5000, 10000, 25000);
            case BBLR:
                return ratioText(ratio(p.bedsBroken, p.bedsLost), 1, 1.5, 2.5, 4, 6, 10);
            default:
                return "";
        }
    }

    /** Column slots from the settings: "Off" skipped, a second Name collapses, Name added if absent. */
    private static List<Col> columns(LobbyIntel li) {
        int[] picks = {
                li.tabCol1.getIndex(), li.tabCol2.getIndex(), li.tabCol3.getIndex(),
                li.tabCol4.getIndex(), li.tabCol5.getIndex(), li.tabCol6.getIndex()
        };
        List<Col> list = new ArrayList<>();
        boolean hasName = false;
        for (int pick : picks) {
            if (pick < 0 || pick >= Col.values().length) continue; // "Off"
            Col col = Col.values()[pick];
            if (col == Col.NAME) {
                if (hasName) continue;
                hasName = true;
            }
            list.add(col);
        }
        if (!hasName) list.add(Col.NAME);
        return list;
    }

    // ------------------------------------------------------------------------------------
    // rows
    // ------------------------------------------------------------------------------------

    private List<Row> buildRows(LobbyIntel li) {
        List<Snap> all = new ArrayList<>();
        for (Snap snap : snaps.values()) {
            if (eliminated.contains(snap.name)) continue;
            // Players Hypixel removed from the tab (dead / disconnected) are drawn from their snapshot.
            if (present.contains(snap.name) || statusText(li, snap.name) != null) all.add(snap);
        }
        for (Snap ghost : ghosts.values()) {
            if (!present.contains(ghost.name) && !eliminated.contains(ghost.name) && statusText(li, ghost.name) != null) {
                all.add(ghost);
            }
        }
        if (all.isEmpty()) return Collections.<Row>emptyList();

        // Group by Bed Wars team letter; groups ordered by their lowest team name.
        final Map<Snap, String> groupOf = new HashMap<>();
        final Map<String, String> groupOrder = new HashMap<>();
        final Map<Snap, Boolean> lastOf = new HashMap<>();
        for (Snap snap : all) {
            String group = isLetterPrefix(snap.prefix) ? "letter:" + plain(snap.prefix) : snap.teamName;
            groupOf.put(snap, group);
            String order = groupOrder.get(group);
            if (order == null || snap.teamName.compareTo(order) < 0) groupOrder.put(group, snap.teamName);
            lastOf.put(snap, snap.spectator && !respawnEnd.containsKey(snap.name));
        }
        Collections.sort(all, new Comparator<Snap>() {
            @Override
            public int compare(Snap a, Snap b) {
                boolean la = lastOf.get(a), lb = lastOf.get(b);
                if (la != lb) return la ? 1 : -1;
                int byOrder = groupOrder.get(groupOf.get(a)).compareTo(groupOrder.get(groupOf.get(b)));
                if (byOrder != 0) return byOrder;
                int byGroup = groupOf.get(a).compareTo(groupOf.get(b));
                if (byGroup != 0) return byGroup;
                int byTeam = a.teamName.compareTo(b.teamName);
                if (byTeam != 0) return byTeam;
                return a.name.compareToIgnoreCase(b.name);
            }
        });

        Scoreboard sb = mc.theWorld.getScoreboard();
        ScoreObjective listObjective = sb.getObjectiveInDisplaySlot(0);
        List<Col> cols = columns(li);
        String ownTeam = ownTeamColor();

        List<Row> rows = new ArrayList<>();
        for (Snap snap : all) {
            Row row = new Row();
            row.snap = snap;
            row.stats = IntelManager.getInstance().getPlayer(snap.name);
            row.status = statusText(li, snap.name);
            if (listObjective != null) {
                Map<ScoreObjective, Score> scores = sb.getObjectivesForEntity(snap.name);
                Score score = scores == null ? null : scores.get(listObjective);
                row.hp = score == null ? 0 : score.getScorePoints();
            }

            boolean hideStats = StreamerMode.hidesStatsFor(snap.name);
            boolean gray = li.tabGrayOwnTeam.getValue() && ownTeam != null && ownTeam.equals(teamColorOf(snap.prefix));
            row.cells = new String[cols.size()];
            for (int i = 0; i < cols.size(); i++) {
                Col col = cols.get(i);
                if (col == Col.NAME) {
                    row.cells[i] = nameText(li, row);
                } else {
                    String value = cell(col, row.stats, hideStats);
                    row.cells[i] = gray ? "§8" + plain(value) : value;
                }
            }
            rows.add(row);
        }
        return rows;
    }

    private String ownTeamColor() {
        if (mc.thePlayer == null) return null;
        Snap me = snaps.get(mc.thePlayer.getName());
        if (me == null) return null;
        String color = teamColorOf(me.prefix);
        return color.isEmpty() ? null : color;
    }

    private String nameText(LobbyIntel li, Row row) {
        Snap snap = row.snap;
        String shownName = StreamerMode.aliasFor(snap.name);
        StringBuilder text = new StringBuilder();
        text.append(snap.prefix).append(shownName).append(snap.suffix);

        IntelPlayer p = row.stats;
        if (p != null && !p.loading && !StreamerMode.hidesTagsFor(snap.name)) {
            if (p.isNicked && li.tabShowNick.getValue()) {
                text.append(" §5[NICK]");
                if (li.tabShowRealName.getValue() && p.realName != null) {
                    text.append(" §7(§e").append(p.realName).append("§7)"); // denicked via Bedlify
                }
            } else if (li.tabShowTag.getValue()) {
                String badge = StreamerMode.badgeFor(p);
                if (!badge.isEmpty()) {
                    String code = badge.equals("CC") ? "§6" : coralintel.ui.intel.IntelColors.nearestCode(p.getTagColor());
                    text.append(" ").append(code).append(badge);
                }
            }
        }
        return text.toString();
    }

    // ------------------------------------------------------------------------------------
    // drawing
    // ------------------------------------------------------------------------------------

    private static final class Placed {
        float x, w;
        Col col;
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Pre event) {
        if (event.type != RenderGameOverlayEvent.ElementType.PLAYER_LIST) return;
        LobbyIntel li = lobbyIntel();
        if (!enabled(li) || !wasInGame || mc.theWorld == null || mc.thePlayer == null) return;

        List<Row> rows = buildRows(li);
        if (rows.isEmpty()) return;

        event.setCanceled(true);
        try {
            draw(li, rows, event.resolution);
        } finally {
            GlStateManager.enableAlpha();
            GlStateManager.disableBlend();
            GlStateManager.color(1f, 1f, 1f, 1f);
        }
    }

    private static float measure(String text) {
        return text == null ? 0f : mc.fontRendererObj.getStringWidth(text);
    }

    private static List<String> splitLines(String text) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;
        for (String line : text.split("\n", -1)) lines.add(line);
        while (!lines.isEmpty() && plain(lines.get(lines.size() - 1)).trim().isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    private void draw(LobbyIntel li, List<Row> rows, ScaledResolution sr) {
        List<Col> cols = columns(li);
        boolean anyHp = false;
        boolean anyStatus = false;
        for (Row row : rows) {
            anyHp = anyHp || row.hp != null;
            anyStatus = anyStatus || row.status != null;
        }

        // ---- column layout ----
        float x = PAD;
        List<Placed> placed = new ArrayList<>();
        for (int c = 0; c < cols.size(); c++) {
            Col col = cols.get(c);
            float width = measure(col.header);
            if (col == Col.NAME) {
                float widest = 0f;
                for (Row row : rows) widest = Math.max(widest, measure(row.cells[c]));
                width = Math.max(width, HEAD + HEAD_GAP + widest + NAME_TAIL);
                if (c > 0) x += NAME_GAP;
            } else {
                for (Row row : rows) width = Math.max(width, measure(row.cells[c]));
                if (c > 0) x += GAP;
            }
            Placed p = new Placed();
            p.x = x;
            p.w = width;
            p.col = col;
            placed.add(p);
            x += width;
        }

        float hpX = 0f, hpW = 0f;
        if (anyHp) {
            hpW = measure(LIST_LABEL);
            for (Row row : rows) if (row.hp != null) hpW = Math.max(hpW, measure(hpText(row.hp)));
            x += GAP;
            hpX = x;
            x += hpW;
        }
        float statusX = 0f, statusW = 0f;
        if (anyStatus) {
            for (Row row : rows) if (row.status != null) statusW = Math.max(statusW, measure(row.status));
            x += GAP;
            statusX = x;
            x += statusW;
        }
        float tableWidth = x + PAD;

        List<String> headerLines = splitLines(header);
        List<String> footerLines = splitLines(footer);
        float width = tableWidth;
        for (String line : headerLines) width = Math.max(width, measure(line) + 2 * PAD);
        for (String line : footerLines) width = Math.max(width, measure(line) + 2 * PAD);

        float labelsY = headerLines.size() * LINE;
        float rowsY = labelsY + ROW;
        float footerY = rowsY + rows.size() * ROW;
        float height = footerY + footerLines.size() * LINE;
        float tableX = (width - tableWidth) / 2f;

        // ---- scale + origin ----
        float scale = (float) (sr.getScaledHeight() / UNITS_PER_HEIGHT * li.tabOverlaySize.getValue() / 100.0);
        float originX = Math.round((sr.getScaledWidth() - width * scale) / 2f);
        float originY = TOP * scale;
        boolean shadow = li.tabOverlayShadow.getValue();

        GlStateManager.pushMatrix();
        GlStateManager.translate(originX, originY, 0f);
        GlStateManager.scale(scale, scale, 1f);

        // Background opacity slider: 0 = no background at all, 50 = original look.
        float bg = (float) li.tabOverlayBgOpacity.getValue() / 50f;
        if (bg > 0f) {
            rect(0, 0, width, height, scaleAlpha(PANEL_COLOR, bg));
            rect(0, labelsY, width, labelsY + ROW, scaleAlpha(LABEL_BAND_COLOR, bg));
            rect(0, rowsY, width, rowsY + rows.size() * ROW, scaleAlpha(ROWS_COLOR, bg));
        }

        for (int i = 0; i < headerLines.size(); i++) {
            centered(headerLines.get(i), 0, width, i * LINE + LINE_TEXT, shadow);
        }

        for (Placed p : placed) {
            centered(HEADER_COLOR + p.col.header, tableX + p.x, p.w, labelsY + ROW_TEXT, shadow);
        }
        if (anyHp) centered(HEADER_COLOR + LIST_LABEL, tableX + hpX, hpW, labelsY + ROW_TEXT, shadow);

        for (int r = 0; r < rows.size(); r++) {
            Row row = rows.get(r);
            float capTop = rowsY + r * ROW + ROW_TEXT;
            for (int c = 0; c < placed.size(); c++) {
                Placed p = placed.get(c);
                float cx = tableX + p.x;
                if (p.col == Col.NAME) {
                    drawHead(row.snap, cx, capTop);
                    text(row.cells[c], cx + HEAD + HEAD_GAP, capTop, shadow);
                } else {
                    centered(row.cells[c], cx, p.w, capTop, shadow);
                }
            }
            if (anyHp && row.hp != null) centered(hpText(row.hp), tableX + hpX, hpW, capTop, shadow);
            if (anyStatus && row.status != null) centered(row.status, tableX + statusX, statusW, capTop, shadow);
        }

        for (int i = 0; i < footerLines.size(); i++) {
            centered(footerLines.get(i), 0, width, footerY + i * LINE + LINE_TEXT, shadow);
        }

        GlStateManager.popMatrix();
    }

    private static String hpText(int score) {
        return "§a" + score;
    }

    private static void text(String value, float x, float capTop, boolean shadow) {
        // Capitals start one pixel below the top of the font's cell.
        mc.fontRendererObj.drawString(value, x, capTop - 1f, 0xFFFFFFFF, shadow);
    }

    private static void centered(String value, float x, float w, float capTop, boolean shadow) {
        text(value, x + (w - measure(value)) / 2f, capTop, shadow);
    }

    private static void drawHead(Snap snap, float x, float capTop) {
        if (snap.skin == null) {
            rect(x, capTop, x + HEAD, capTop + HEAD, MISSING_HEAD_COLOR);
            return;
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, capTop, 0f);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1f, 1f, 1f, 1f);
        mc.getTextureManager().bindTexture(snap.skin);
        Gui.drawScaledCustomSizeModalRect(0, 0, 8f, 8f, 8, 8, 8, 8, 64f, 64f);
        if (snap.hat) {
            Gui.drawScaledCustomSizeModalRect(0, 0, 40f, 8f, 8, 8, 8, 8, 64f, 64f);
        }
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private static int scaleAlpha(int argb, float factor) {
        int a = Math.min(255, Math.round(((argb >>> 24) & 0xFF) * factor));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    /** Filled rectangle with fractional coordinates (Gui.drawRect only takes ints). */
    private static void rect(float x1, float y1, float x2, float y2, int argb) {
        float a = (argb >> 24 & 255) / 255f;
        float r = (argb >> 16 & 255) / 255f;
        float g = (argb >> 8 & 255) / 255f;
        float b = (argb & 255) / 255f;
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(r, g, b, a);
        wr.begin(7, DefaultVertexFormats.POSITION);
        wr.pos(x1, y2, 0.0).endVertex();
        wr.pos(x2, y2, 0.0).endVertex();
        wr.pos(x2, y1, 0.0).endVertex();
        wr.pos(x1, y1, 0.0).endVertex();
        tessellator.draw();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }
}
