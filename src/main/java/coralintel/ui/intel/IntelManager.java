package coralintel.ui.intel;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import coralintel.CoralIntel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

public class IntelManager {

    /** One entry in the recent-flags log, shown in the ClickGUI's LobbyIntel panel. */
    public static class FlagRecord {
        public final String name;
        public final String message;
        public final int color;
        public final long time;

        public FlagRecord(String name, String message, int color, long time) {
            this.name = name;
            this.message = message;
            this.color = color;
            this.time = time;
        }
    }

    private static final int MAX_RECENT_FLAGS = 8;
    private final List<FlagRecord> recentFlags = new CopyOnWriteArrayList<>();

    /** Most recent flags first — read by the ClickGUI's notification section. */
    public List<FlagRecord> getRecentFlags() {
        return recentFlags;
    }

    /**
     * Lets other flag sources (e.g. the AntiCheat module's real-time
     * movement/combat checks) feed into the same notification log the
     * Coral and blacklist flags use, so everything shows up in one place
     * in the ClickGUI regardless of where it came from.
     */
    public void addRecentFlag(String name, String message, int color) {
        recentFlags.add(0, new FlagRecord(name, message, color, System.currentTimeMillis()));
        while (recentFlags.size() > MAX_RECENT_FLAGS) {
            recentFlags.remove(recentFlags.size() - 1);
        }
    }

    /** After this many fetch rounds with no usable stats, a player's stats are just set to 0. */
    public static final int MAX_FETCH_ATTEMPTS = 3;

    /**
     * Rounds allowed for a player who came back only partially loaded (e.g. just
     * the star). They keep refreshing in the loading look until every stat is in;
     * this cap only stops genuinely-hidden accounts from refreshing forever.
     */
    public static final int MAX_PARTIAL_ATTEMPTS = 5;

    private static boolean hasNoStats(IntelPlayer p) {
        return p.star == 0 && p.finalKills == 0 && p.wins == 0 && p.fkdr == 0 && p.wlr == 0;
    }

    /**
     * One fetch round for a player, with attempt tracking. A player only counts
     * as fully loaded once EVERY stat came back (statsComplete) — a lone star is
     * not enough. Until then the row stays in its loading look and the 10s retry
     * loop keeps refreshing it. Rounds that return nothing at all are zeroed on
     * the 3rd; partial results (e.g. star only) get MAX_PARTIAL_ATTEMPTS rounds
     * and then keep whatever was found.
     */
    private void runStatsFetch(IntelPlayer p) {
        p.fetchInFlight = true;
        try {
            try {
                fetchHypixel(p);
                p.computeThreat();
                p.statsFetchFailed = false;
            } catch (Exception exception) {
                p.statsFetchFailed = true;
                dbg("[Intel] stats fetch failed for " + p.name + ": " + exception);
            }

            if (p.statsSkipped) {
                p.loading = true; // keep the "skipped" look even if a fetch was in flight
                return;
            }

            // "No stats" is the correct, stable answer for nicks — nothing to retry.
            if (p.isNicked) {
                p.loading = false;
                p.statsFinal = true;
                return;
            }

            if (p.statsComplete && !p.statsFetchFailed) {
                p.loading = false;
                p.statsFinal = true; // fully loaded: every stat is in
                if (!p.statsFromCache) {
                    StatSnapshotManager.getInstance().record(p); // baseline for .daily / .monthly
                }
                return;
            }

            boolean empty = hasNoStats(p);
            p.fetchAttempts++;
            int cap = empty ? MAX_FETCH_ATTEMPTS : MAX_PARTIAL_ATTEMPTS;

            if (p.fetchAttempts >= cap) {
                if (empty) {
                    p.star = 0; p.level = 0; p.fkdr = 0; p.wlr = 0; p.winstreak = 0;
                    p.finalKills = 0; p.finalDeaths = 0; p.bedsBroken = 0; p.bedsLost = 0;
                    p.kills = 0; p.deaths = 0; p.wins = 0; p.losses = 0;
                }
                p.loading = false;
                p.statsFetchFailed = false;
                p.statsFinal = true;
                p.computeThreat();
                dbg("[Intel] " + p.name + (empty ? " returned no stats " : " only partially loaded ")
                        + p.fetchAttempts + " times — giving up"
                        + (empty ? " (stats set to 0)." : " (keeping what was found)."));
            } else {
                p.loading = true; // not fully loaded yet — stay in the loading look, retry loop continues
            }
        } finally {
            p.fetchInFlight = false;
        }
    }

    private boolean isSkipSelfEnabled() {
        try {
            coralintel.module.modules.LobbyIntel lobbyIntel =
                    (coralintel.module.modules.LobbyIntel) CoralIntel.moduleManager.getModule("LobbyIntel");
            return lobbyIntel != null && lobbyIntel.skipSelfStats.getValue();
        } catch (Exception e) {
            return false;
        }
    }

    /** Mellow's nick check: real accounts have version-4 UUIDs; a nick's tab UUID is version 1. */
    private static boolean isNickUuid(java.util.UUID uuid) {
        return uuid != null && uuid.version() == 1;
    }

    private void markNicked(IntelPlayer p) {
        p.isNicked = true;
        p.loading = false;
        p.statsFetchFailed = false;
        p.statsFinal = true;
        p.computeThreat();
        requestDenick(p);
    }

    /** Starts a background Bedlify lookup for a nicked player (if denicking is on and a key is set). */
    private void requestDenick(IntelPlayer p) {
        coralintel.module.modules.LobbyIntel intel = (coralintel.module.modules.LobbyIntel)
                CoralIntel.moduleManager.getModule(coralintel.module.modules.LobbyIntel.class);
        if (intel == null || !intel.denick.getValue()) return;

        BedlifyManager.getInstance().denickPlayer(p, this::pushUpdate);
    }

    /**
     * Looks up every nicked player in the roster.
     * @param force also redo players that were already looked up (used by .denick)
     * @return how many lookups were started
     */
    public int denickAll(boolean force) {
        int started = 0;
        for (IntelPlayer p : new ArrayList<>(combined())) {
            if (!p.isNicked) continue;
            if (!force && (p.realName != null || p.denickRequested)) continue;

            p.denickRequested = false;
            BedlifyManager.getInstance().denickPlayer(p, this::pushUpdate);
            started++;
        }
        return started;
    }

    /**
     * Re-attempts the stats fetch for anyone still showing no data — covers
     * both explicit fetch failures (network hiccup, timeout) and the quieter
     * case of a fetch that completed without error but still found nothing
     * (e.g. a transient Mojang UUID lookup miss). Skips players where "no
     * stats" is actually the correct, stable answer (hidden via API
     * Settings, or nicked) since retrying those can't help.
     */
    public void retryFailedFetches() {
        List<IntelPlayer> toCheck = new ArrayList<>(combined());
        toCheck.addAll(parked.values());

        for (IntelPlayer player : toCheck) {
            // statsFinal covers fully loaded / nicked / gave up — only players
            // that are NOT fully loaded (and not already being fetched) get
            // re-fetched, so partial rows (e.g. star only) keep refreshing.
            if (player.fetchInFlight || player.statsFinal || player.statsSkipped
                    || player.isNicked) {
                continue;
            }

            player.loading = true;
            player.fetchInFlight = true;

            pool.submit(() -> {
                try {
                    runStatsFetch(player);
                } finally {
                    pushUpdate();
                }
            });
        }
    }


    public static final List<String> debugLog = new ArrayList<>();

    public static void dbg(String message) {
        synchronized (debugLog) {
            debugLog.add(message);

            if (debugLog.size() > 200) {
                debugLog.remove(0);
            }
        }
    }

    public static String hypixelApiKey = "";
    public static String urchinApiKey = "";
    public static String ghostApiKey = "";

    private static final IntelManager INSTANCE = new IntelManager();

    public static IntelManager getInstance() {
        return INSTANCE;
    }

    private static final String CORAL_CUBELIFY_URL =
            "https://api.urchin.gg/v3/cubelify";

    private static final String GHOST_URL =
            "https://ghost-intel-bot-production.up.railway.app/api/tags";

    private static final long HYPIXEL_INTERVAL_MS = 600L;

    private final ExecutorService pool = Executors.newFixedThreadPool(6);
    private final Semaphore hypixelSlots = new Semaphore(1);
    private final AtomicLong lastHypixelRequest = new AtomicLong(0);

    private final Map<String, String> uuidCache = new HashMap<>();
    private final List<IntelPlayer> players = new CopyOnWriteArrayList<>();
    private final List<IntelPlayer> manualPlayers = new CopyOnWriteArrayList<>();

    // Fully-loaded players stashed whenever the roster is rebuilt (new world,
    // /who, team assignment) so they come back WITH their stats instead of
    // being fetched again — the roster is just re-ordered/re-teamed.
    private static final long RETAIN_MS = 10 * 60 * 1000L;

    private static class Retained {
        final IntelPlayer player;
        final long at = System.currentTimeMillis();

        Retained(IntelPlayer player) {
            this.player = player;
        }
    }

    private final Map<String, Retained> retained = new java.util.concurrent.ConcurrentHashMap<>();

    /** Stashes every player whose stats are fully loaded so a roster rebuild can reuse them. */
    public void retainLoadedPlayers() {
        long now = System.currentTimeMillis();

        for (IntelPlayer p : combined()) {
            if (p.statsFinal && p.statsComplete && !p.isNicked
                    && !p.statsSkipped && !p.fetchInFlight) {
                retained.put(p.name.toLowerCase(), new Retained(p));
            }
        }

        // Parked chatters are kept whatever their load state: the fetch is still
        // writing into this same object, so the reused player finishes on its own.
        for (IntelPlayer p : parked.values()) {
            if (!p.isNicked && !p.statsSkipped) {
                retained.put(p.name.toLowerCase(), new Retained(p));
            }
        }
        parked.clear();

        retained.values().removeIf(r -> now - r.at > RETAIN_MS);
    }

    /** Returns (and removes) a stashed, still-fresh player for this name, or null. */
    private IntelPlayer takeRetained(String name) {
        Retained r = retained.remove(name.toLowerCase());

        if (r == null || System.currentTimeMillis() - r.at > RETAIN_MS) {
            return null;
        }

        return r.player;
    }

    // ── Pregame chat chatters ─────────────────────────────────────────────
    // Pregame chatters stay on the HUD until "The game starts in 1 second". Players
    // that get "parked" (removed from the display) keep loading in the background
    // on the same IntelPlayer object so they can be reused later.
    private final Map<String, IntelPlayer> parked = new java.util.concurrent.ConcurrentHashMap<>();

    /** Adds a pregame chatter to the roster right away; their stats start loading immediately. */
    public boolean addPregameChatter(String name) {
        boolean alreadyShown = isOnRoster(name);
        dbg("[Intel] adding chatter " + name + (alreadyShown ? " — already on the roster, nothing to add." : " to the HUD."));
        addManualPlayer(name);
        return isOnRoster(name);
    }

    private boolean isOnRoster(String name) {
        for (IntelPlayer p : combined()) {
            if (p.name.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    /** Adds a nicked pregame chatter to the HUD. No stats lookup — a nick's name can match a stranger's account. */
    public void addNickedChatter(String name) {
        for (IntelPlayer p : combined()) {
            if (p.name.equalsIgnoreCase(name)) {
                if (!p.isNicked) {
                    markNicked(p);
                    pushUpdate();
                }
                return;
            }
        }

        IntelPlayer player = new IntelPlayer(name, null);
        markNicked(player);
        manualPlayers.add(player);
        pushUpdate();
    }

    /** Drops every trace of a player (parked / stashed) — used when they leave the pregame lobby. */
    public void forgetPlayer(String name) {
        String key = name.toLowerCase();
        parked.remove(key);
        retained.remove(key);
    }

    private volatile boolean fetching = false;

    private IntelGui gui;
    private IntelHudOverlay hudOverlay;

    private IntelManager() {
        loadUrchinKeyFromFile();
    }

    public void saveUrchinKeyToFile() {
        try {
            File dir = new File("./config/CoralIntel/");
            dir.mkdirs();

            File keyFile = new File(dir, "coral-key.txt");

            if (urchinApiKey.isEmpty()) {
                keyFile.delete();
                return;
            }

            try (PrintWriter writer = new PrintWriter(new FileWriter(keyFile))) {
                writer.println(urchinApiKey);
            }
        } catch (Exception exception) {
            dbg("[Intel] failed to save Coral key: " + exception);
        }
    }

    private void loadUrchinKeyFromFile() {
        try {
            File keyFile = new File("./config/CoralIntel/coral-key.txt");
            if (!keyFile.exists()) return;

            try (BufferedReader reader = new BufferedReader(new FileReader(keyFile))) {
                String key = reader.readLine();

                if (key != null && !key.trim().isEmpty()) {
                    urchinApiKey = key.trim();
                    dbg("[Intel] Loaded Coral key from file");
                }
            }
        } catch (Exception exception) {
            dbg("[Intel] failed to load Coral key: " + exception);
        }
    }

    public boolean isFetching() {
        return fetching;
    }

    public void setGui(IntelGui gui) {
        this.gui = gui;
    }

    public void setHudOverlay(IntelHudOverlay hud) {
        this.hudOverlay = hud;
    }

    public List<IntelPlayer> getPlayers() {
        return players;
    }

    /** Snapshot of everyone tracked right now: tab-scanned players plus manually added / chat-tracked ones. */
    public List<IntelPlayer> getAllPlayers() {
        return combined();
    }

    public IntelPlayer getPlayer(String name) {
        if (name == null) return null;

        for (IntelPlayer player : players) {
            if (name.equalsIgnoreCase(player.name)) {
                return player;
            }
        }

        for (IntelPlayer player : manualPlayers) {
            if (name.equalsIgnoreCase(player.name)) {
                return player;
            }
        }

        return null;
    }

    /** Hypixel NPC profiles use a version-2 UUID; name/display checks cover
     * servers that expose an NPC without that UUID convention. */
    public static boolean isNpc(NetworkPlayerInfo info) {
        if (info == null || info.getGameProfile() == null) return true;
        String name = info.getGameProfile().getName();
        if (name == null || name.isEmpty() || name.equalsIgnoreCase("NPC") || name.startsWith("!")) return true;

        java.util.UUID uuid = info.getGameProfile().getId();
        if (uuid != null && uuid.version() == 2) return true;

        if (info.getDisplayName() != null) {
            String displayed = info.getDisplayName().getUnformattedText();
            if (displayed != null && displayed.toLowerCase(java.util.Locale.ROOT).contains("[npc]")) return true;
        }
        return false;
    }

    public void addManualPlayer(String name) {
        Minecraft minecraft = Minecraft.getMinecraft();
        NetworkPlayerInfo info = minecraft.getNetHandler() == null
                ? null : minecraft.getNetHandler().getPlayerInfo(name);
        if (info != null && isNpc(info)) return;

        // Check BOTH lists — this used to only check manualPlayers, so
        // anyone already tracked from a tab scan (in `players`) would get a
        // second, fully-duplicate IntelPlayer + stats fetch created here
        // whenever /who also reported them. That doubled the API load for
        // every player scanLobby() already had, which is exactly what was
        // driving the rate-limit issue — not a per-player problem, a
        // systematic 2x multiplier on every single fetch.
        for (IntelPlayer player : players) {
            if (player.name.equalsIgnoreCase(name)) {
                return;
            }
        }
        for (IntelPlayer player : manualPlayers) {
            if (player.name.equalsIgnoreCase(name)) {
                return;
            }
        }

        IntelPlayer recycled = parked.remove(name.toLowerCase());
        if (recycled == null) {
            recycled = takeRetained(name);
        }
        final IntelPlayer player = recycled != null ? recycled : new IntelPlayer(name, null);
        manualPlayers.add(player);

        List<IntelPlayer> combined = combined();

        if (gui != null) {
            gui.setPlayers(combined);
        }

        if (hudOverlay != null) {
            hudOverlay.setPlayers(combined);
        }

        // Already loaded earlier this session — reuse its stats as-is.
        if (recycled != null) {
            return;
        }

        pool.submit(() -> {
            try {
                fetchAndCacheUuid(player.name);
            } catch (Exception exception) {
                dbg("[Intel] UUID fetch failed for " + player.name
                        + ": " + exception);
            } finally {
                pushUpdate();
            }
        });

        // Cheat-tag lookup and the actual Hypixel stats fetch run as fully
        // independent tasks now (matching scanLobby()'s pattern) — they
        // used to be chained in one try block, so a Coral/Urchin API
        // hiccup (rate limit, timeout, bad response) would silently abort
        // before the Hypixel stats fetch ever ran, permanently leaving
        // this player's star/FKDR/etc. unpopulated.
        pool.submit(() -> {
            try {
                fetchUrchinBatch(java.util.Collections.singletonList(player));
            } catch (Exception exception) {
                dbg("[Intel] Coral fetch failed for " + player.name + ": " + exception);
            } finally {
                pushUpdate();
            }
        });

        player.fetchInFlight = true;
        pool.submit(() -> {
            try {
                runStatsFetch(player);
            } finally {
                pushUpdate();
            }
        });
    }

    /**
     * Chat/GUI/HUD state must only ever be touched from the main client
     * thread — this hops back via addScheduledTask so background fetch
     * callbacks (which run on the pool's worker threads) don't race the
     * render thread.
     */
    private void pushUpdate() {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            List<IntelPlayer> refreshed = combined();

            if (gui != null) {
                gui.setPlayers(refreshed);
            }

            if (hudOverlay != null) {
                hudOverlay.setPlayers(refreshed);
            }
        });
    }

    /**
     * Removes a player that was added by search or by the automatic /who scan.
     *
     * @return true when a matching manually-added player was removed
     */
    public boolean removeManualPlayer(String name) {
        boolean removed = manualPlayers.removeIf(
                player -> player.name.equalsIgnoreCase(name)
        );
        parked.remove(name.toLowerCase());

        List<IntelPlayer> combined = combined();

        if (gui != null) {
            gui.setPlayers(combined);
        }

        if (hudOverlay != null) {
            hudOverlay.setPlayers(combined);
        }

        return removed;
    }

    public boolean isManual(IntelPlayer player) {
        return manualPlayers.contains(player);
    }

    private List<IntelPlayer> combined() {
        List<IntelPlayer> result = new ArrayList<>(players);

        for (IntelPlayer manual : manualPlayers) {
            boolean alreadyPresent = false;

            for (IntelPlayer player : players) {
                if (player.name.equalsIgnoreCase(manual.name)) {
                    alreadyPresent = true;
                    break;
                }
            }

            if (!alreadyPresent) {
                result.add(manual);
            }
        }

        return result;
    }

    /**
     * Lightweight, frequent team sync: re-reads each tracked player's team from
     * the tab list and pushes an update only when something changed. Teams are
     * assigned when the match starts, which is NOT in step with the 10s
     * rescan — without this the HUD kept the pre-match order for up to 10s.
     * Call from the main client thread.
     */
    public void refreshTeams() {
        Minecraft minecraft = Minecraft.getMinecraft();

        if (minecraft.getNetHandler() == null) {
            return;
        }

        boolean changed = false;

        for (NetworkPlayerInfo info : minecraft.getNetHandler().getPlayerInfoMap()) {
            String name = info.getGameProfile().getName();

            if (name == null || isNpc(info)) {
                continue;
            }

            IntelPlayer tracked = getPlayer(name);

            if (tracked == null) {
                continue;
            }

            String team = detectTeam(info);

            if (!java.util.Objects.equals(tracked.team, team)) {
                tracked.team = team;
                changed = true;
            }
        }

        if (changed) {
            List<IntelPlayer> combined = combined();

            if (gui != null) {
                gui.setPlayers(combined);
            }

            if (hudOverlay != null) {
                hudOverlay.setPlayers(combined);
            }
        }
    }

    public void scanLobby() {
        fetching = true;

        Minecraft minecraft = Minecraft.getMinecraft();

        if (minecraft.getNetHandler() == null) {
            fetching = false;
            return;
        }

        // Re-use existing IntelPlayer objects (and their already-fetched
        // stats) for names still present in the lobby, instead of wiping
        // everyone back to a blank/loading state on every rescan. Only
        // players who are genuinely new get queued for a fresh fetch —
        // this is what was causing stats to flash empty and reloads to
        // take 10+ seconds on a full lobby (everyone re-queued through the
        // single-slot, 600ms-interval Hypixel limiter every time).
        Map<String, IntelPlayer> existingByName = new HashMap<>();
        for (IntelPlayer existing : players) {
            existingByName.put(existing.name.toLowerCase(), existing);
        }
        // A name already tracked via /who keeps its object (and any stats /
        // in-flight fetch) when it shows up in the tab list — no second fetch.
        for (IntelPlayer manual : manualPlayers) {
            existingByName.putIfAbsent(manual.name.toLowerCase(), manual);
        }

        List<IntelPlayer> newRoster = new ArrayList<>();
        List<IntelPlayer> needsFetch = new ArrayList<>();

        for (NetworkPlayerInfo info : minecraft.getNetHandler().getPlayerInfoMap()) {
            String name = info.getGameProfile().getName();

            if (isNpc(info)) {
                continue;
            }

            String team = detectTeam(info);
            IntelPlayer existing = existingByName.get(name.toLowerCase());

            if (existing == null) {
                // Seen earlier this session with stats fully loaded: bring it
                // back with those stats; only its team/position changes.
                existing = takeRetained(name);
            }

            java.util.UUID tabUuid = info.getGameProfile().getId();
            boolean nickedUuid = isNickUuid(tabUuid);
            boolean skipSelf = minecraft.thePlayer != null
                    && name.equalsIgnoreCase(minecraft.thePlayer.getName())
                    && isSkipSelfEnabled();

            IntelPlayer player;
            if (existing != null) {
                player = existing;
                player.team = team;

                if (nickedUuid && !player.isNicked) {
                    markNicked(player);
                } else if (!nickedUuid && skipSelf && !player.statsSkipped) {
                    player.statsSkipped = true;
                    player.loading = true;
                } else if (!skipSelf && player.statsSkipped) {
                    // Option turned back off — start loading own stats.
                    player.statsSkipped = false;
                    player.statsFinal = false;
                    player.fetchAttempts = 0;
                    player.loading = true;
                    needsFetch.add(player);
                }
            } else {
                player = new IntelPlayer(name, team);

                if (nickedUuid) {
                    markNicked(player);         // no lookups for nicks at all
                } else if (skipSelf) {
                    player.statsSkipped = true; // stays in the loading look, never fetched
                } else {
                    needsFetch.add(player);
                }

                if (player.blacklisted) {
                    notifyBlacklisted(player);
                }

                notifyReminder(player);
            }

            player.rankPrefix = extractRankPrefix(info, name);

            newRoster.add(player);

            java.util.UUID uuid = info.getGameProfile().getId();

            if (uuid != null) {
                synchronized (uuidCache) {
                    uuidCache.put(name, uuid.toString());
                }
            }
        }

        players.clear();
        players.addAll(newRoster);
        manualPlayers.removeIf(newRoster::contains);

        if (gui != null) {
            for (NetworkPlayerInfo info : minecraft.getNetHandler().getPlayerInfoMap()) {
                if (isNpc(info)) continue;
                String name = info.getGameProfile().getName();
                net.minecraft.util.ResourceLocation skin = info.getLocationSkin();

                if (name != null && skin != null) {
                    gui.cacheLobbyPlayerSkin(name, skin);

                    if (hudOverlay != null) {
                        hudOverlay.cacheSkin(name, skin, true);
                    }
                }
            }

            gui.setPlayers(combined());
        }

        if (hudOverlay != null) {
            hudOverlay.setPlayers(combined());
        }

        if (!needsFetch.isEmpty()) {
            final List<IntelPlayer> batch = new ArrayList<>(needsFetch);

            pool.submit(() -> {
                try {
                    fetchUrchinBatch(batch);
                    fetchGhostBatch(batch);
                } catch (Exception exception) {
                    dbg("[Intel] tag batch failed: " + exception);
                } finally {
                    // Chat messages and GUI/HUD state must only ever be
                    // touched from the main client thread — calling them
                    // directly from this background pool thread races with
                    // the render thread iterating the same chat/player
                    // lists and was causing intermittent crashes/errors
                    // that looked timing-dependent (worse the slower or
                    // more concurrent the network fetches were).
                    Minecraft.getMinecraft().addScheduledTask(() -> {
                        for (IntelPlayer player : batch) {
                            if ((player.cheater || player.ghostTagged) && !player.safelisted) {
                                notifyCheater(player);
                            }
                        }

                        List<IntelPlayer> refreshed = combined();

                        if (gui != null) {
                            gui.setPlayers(refreshed);
                        }

                        if (hudOverlay != null) {
                            hudOverlay.setPlayers(refreshed);
                        }
                    });
                }
            });
        }

        for (IntelPlayer player : needsFetch) {
            final IntelPlayer current = player;
            current.fetchInFlight = true;

            pool.submit(() -> {
                try {
                    runStatsFetch(current);
                } finally {
                    Minecraft.getMinecraft().addScheduledTask(() -> {
                        List<IntelPlayer> refreshed = combined();

                        if (gui != null) {
                            gui.setPlayers(refreshed);
                        }

                        if (hudOverlay != null) {
                            hudOverlay.setPlayers(refreshed);
                        }
                    });
                }
            });
        }

        fetching = false;
    }

    public void refresh() {
        scanLobby();
    }

    public void clearAll() {
        // Soft clear: loaded stats are kept aside and reused when the same
        // players show up again (pregame -> arena, countdown rescans, /who).
        retainLoadedPlayers();
        StatCache.getInstance().flush();

        players.clear();
        manualPlayers.clear();

        if (gui != null) {
            gui.setPlayers(new ArrayList<>());
        }

        if (hudOverlay != null) {
            hudOverlay.setPlayers(new ArrayList<>());
        }
    }

    /**
     * Wipes only the manually-added roster (players added via /who or the
     * add-player command), leaving the tab-scanned list untouched. Used by
     * /who's handler, which replaces its own results wholesale each time it
     * runs rather than accumulating duplicates across multiple /who calls.
     */
    public void clearManualPlayers() {
        manualPlayers.clear();
    }

    public enum AccountCheck { EXISTS, NOT_FOUND, UNKNOWN }

    /**
     * Blocking Mojang lookup (call off the main thread) that tells "no such account" — which for a
     * pregame chatter means a nick — apart from "couldn't check" (rate limit, network). On success the
     * UUID is cached so the stats fetch reuses it instead of asking again.
     */
    public AccountCheck checkAccount(String name) {
        synchronized (uuidCache) {
            if (uuidCache.containsKey(name)) return AccountCheck.EXISTS;
        }

        try {
            HttpURLConnection connection = (HttpURLConnection)
                    new URL("https://api.mojang.com/users/profiles/minecraft/" + name).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("User-Agent", "Spirit-Client/1.0");

            int code = connection.getResponseCode();

            if (code == 200) {
                JsonObject object = new JsonParser().parse(readStream(connection.getInputStream())).getAsJsonObject();
                if (object.has("id")) {
                    String uuid = object.get("id").getAsString().replaceAll(
                            "^(.{8})(.{4})(.{4})(.{4})(.{12})$", "$1-$2-$3-$4-$5");
                    synchronized (uuidCache) {
                        uuidCache.put(name, uuid);
                    }
                    dbg("[UUID] chatter " + name + " -> " + uuid);
                    return AccountCheck.EXISTS;
                }
                return AccountCheck.UNKNOWN;
            }

            if (code == 204 || code == 404) {
                dbg("[UUID] chatter " + name + " has no Mojang account (nick).");
                return AccountCheck.NOT_FOUND;
            }

            dbg("[UUID] chatter check for " + name + " inconclusive: HTTP " + code);
        } catch (Exception exception) {
            dbg("[UUID] chatter check for " + name + " failed: " + exception.getMessage());
        }

        return AccountCheck.UNKNOWN;
    }

    /**
     * With the pregame tab list obfuscated, a chatter can't be found in the tab by name. Once their
     * real UUID is known, look the tab entry up by UUID and borrow its skin for the HUD head.
     * Main thread only.
     */
    public void useTabSkinByUuid(String name) {
        String uuid;
        synchronized (uuidCache) {
            uuid = uuidCache.get(name);
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (uuid == null || minecraft.getNetHandler() == null) return;

        try {
            NetworkPlayerInfo info = minecraft.getNetHandler().getPlayerInfo(java.util.UUID.fromString(uuid));
            if (info == null || info.getLocationSkin() == null) return;

            if (hudOverlay != null) hudOverlay.cacheSkin(name, info.getLocationSkin(), true);
            if (gui != null) gui.cacheLobbyPlayerSkin(name, info.getLocationSkin());
            dbg("[Intel] matched " + name + " to their tab entry by UUID.");
        } catch (IllegalArgumentException ignored) {
        }
    }

    /** Resolves an IGN to a dashed UUID (cached). Blocking — call off the main thread. */
    public String resolveUuid(String name) {
        return fetchAndCacheUuid(name);
    }

    private String fetchAndCacheUuid(String name) {
        synchronized (uuidCache) {
            String cached = uuidCache.get(name);

            if (cached != null) {
                return cached;
            }
        }

        try {
            String json = get(
                    "https://api.mojang.com/users/profiles/minecraft/" + name,
                    null,
                    null
            );

            if (json != null) {
                JsonObject object = new JsonParser().parse(json).getAsJsonObject();

                if (object.has("id")) {
                    String rawUuid = object.get("id").getAsString();

                    String uuid = rawUuid.replaceAll(
                            "^(.{8})(.{4})(.{4})(.{4})(.{12})$",
                            "$1-$2-$3-$4-$5"
                    );

                    synchronized (uuidCache) {
                        uuidCache.put(name, uuid);
                    }

                    dbg("[UUID] Mojang OK: " + uuid);
                    return uuid;
                }
            }
        } catch (Exception exception) {
            dbg("[UUID] Mojang error: " + exception.getMessage());
        }

        try {
            String json = get(
                    "https://playerdb.co/api/player/minecraft/" + name,
                    null,
                    null
            );

            if (json != null) {
                JsonObject object = new JsonParser().parse(json).getAsJsonObject();

                if (object.has("data")) {
                    JsonObject data = object.getAsJsonObject("data");

                    if (data.has("player")) {
                        JsonObject player = data.getAsJsonObject("player");

                        if (player.has("id")) {
                            String uuid = player.get("id").getAsString();

                            synchronized (uuidCache) {
                                uuidCache.put(name, uuid);
                            }

                            dbg("[UUID] playerdb OK: " + uuid);
                            return uuid;
                        }
                    }
                }
            }
        } catch (Exception exception) {
            dbg("[UUID] playerdb error: " + exception.getMessage());
        }

        dbg("[UUID] failed for: " + name);
        return null;
    }

    /**
     * Fetches a single player's Bedwars stats without touching the lobby
     * player list, GUI, or HUD — used by commands like .bw that just want
     * a one-off lookup. Also pulls the Coral cheater tag if a Coral key
     * is configured, so the tag can be shown alongside the stats.
     */
    public IntelPlayer fetchStandaloneStats(String name) {
        return fetchStandaloneStats(name, false);
    }

    /** @param allowCache true to reuse fresh disk-cached stats (auto lookups); typed commands pass false. */
    public IntelPlayer fetchStandaloneStats(String name, boolean allowCache) {
        IntelPlayer player = new IntelPlayer(name, null);

        if (allowCache && statCacheTtlMs() > 0 && StatCache.getInstance().apply(player, statCacheTtlMs())) {
            player.statsComplete = true;
            player.statsFromCache = true;
        }

        // One-off lookups have no 10s retry loop behind them, so retry here:
        // a rate limit, a Mojang hiccup or a partial answer (star only) gets
        // another go instead of being printed as-is. Bordic is always part of
        // the chain for these lookups, whatever the HUD's keyless settings say.
        for (int attempt = 1; attempt <= STANDALONE_ATTEMPTS && !player.statsFromCache; attempt++) {
            boolean rateLimited = false;

            try {
                fetchHypixel(player, true);
            } catch (RateLimitedException exception) {
                rateLimited = true;
                dbg("[Intel] standalone stats rate limited for " + name + " (attempt " + attempt + ")");
            } catch (Exception exception) {
                dbg("[Intel] standalone stats fetch failed for " + name
                        + ": " + exception);
            }

            if (player.statsComplete) {
                break;
            }

            // Two independent sources both saw the account without Bedwars
            // stats: that's a hidden profile, not a failed fetch.
            if (player.statsHidden && !rateLimited && attempt >= 2) {
                break;
            }

            if (attempt < STANDALONE_ATTEMPTS) {
                try {
                    Thread.sleep((rateLimited ? 1500L : 700L) * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        player.loading = false;

        if (player.statsComplete && !player.statsFromCache) {
            StatSnapshotManager.getInstance().record(player); // baseline for .daily / .monthly
        }

        try {
            fetchUrchinBatch(java.util.Collections.singletonList(player));
            player.computeThreat();
        } catch (Exception exception) {
            dbg("[Intel] standalone Coral fetch failed for " + name
                    + ": " + exception);
        }

        return player;
    }

    private static final int STANDALONE_ATTEMPTS = 3;

    private void fetchHypixel(IntelPlayer player) {
        fetchHypixel(player, false);
    }

    /**
     * @param forceBordic true for one-off lookups (.bw / .daily / .monthly): Bordic is
     *                    always tried as a fallback, independent of the HUD's keyless settings.
     * @throws RateLimitedException only when a source was rate limited AND nothing else
     *                    managed to return every stat, so callers can retry.
     */
    private void fetchHypixel(IntelPlayer player, boolean forceBordic) {
        boolean bordicTried = false;
        RateLimitedException limited = null;

        // Each round starts clean: "complete" is only ever earned by a source
        // that returns every stat, and "hidden" is only set by a source that
        // saw the account without its Bedwars stats.
        player.statsComplete = false;
        player.statsHidden = false;
        player.statsFromCache = false;

        // Seen recently? Reuse the saved stats — no API calls at all. One-off lookups
        // (.bw / .daily / .monthly) always fetch fresh data.
        if (!forceBordic && !player.isNicked && statCacheTtlMs() > 0
                && StatCache.getInstance().apply(player, statCacheTtlMs())) {
            player.statsComplete = true;
            player.statsFromCache = true;
            dbg("[Intel] " + player.name + " stats served from cache.");
            return;
        }

        // Optional: Bordic first (keyless). Skipped for nicked players: a nick's
        // name can coincide with a real account's, so a by-name lookup would
        // attach that stranger's stats.
        if (!player.isNicked && isBordicPrimary()) {
            bordicTried = true;
            try {
                fetchBordic(player);
            } catch (RateLimitedException e) {
                limited = e;
            }
        }

        // A rate limit on one source must not abort the chain: remember it and
        // carry on to the keyless sources below.
        if (!player.statsComplete && !hypixelApiKey.isEmpty()) {
            try {
                fetchHypixelApi(player);
            } catch (RateLimitedException e) {
                limited = e;
            }
        }

        // Fallback chain until a source returns EVERY stat (not just the star):
        // Bordic (keyless, opt-in) -> Slothpixel (keyless).
        if (!player.statsComplete && !player.isNicked && !bordicTried
                && (forceBordic || isBordicFallbackEnabled())) {
            try {
                fetchBordic(player);
            } catch (RateLimitedException e) {
                limited = e;
            }
        }

        if (!player.statsComplete && !player.isNicked) {
            fetchSlothpixel(player);
        }

        if (limited != null && !player.statsComplete) {
            throw limited;
        }

        if (player.statsComplete && !player.isNicked) {
            StatCache.getInstance().put(player);
        }
        // loading is decided by runStatsFetch: it stays true until fully loaded.
    }

    // ── Bordic (keyless Bedwars stats) ───────────────────────────────────
    // Same method Mellow (Roxiun/Mellow) uses: GET the Hypixel cache by UUID, no
    // API key. The response is the normal Hypixel player JSON
    // ({"success":true,"player":{...}}), so it goes through applyHypixelProfile()
    // exactly like a real Hypixel API response. Raw responses are cached for
    // 120s (successes only), like Mellow's BordicApi.
    private static final String BORDIC_URL = "https://api.bordic.xyz/v3/cache/hypixel?uuid=";
    private static final long BORDIC_CACHE_TTL_MS = 120_000L;
    private final java.util.Map<String, Object[]> bordicCache = new java.util.HashMap<>();

    private long statCacheTtlMs() {
        try {
            coralintel.module.modules.LobbyIntel lobbyIntel =
                    (coralintel.module.modules.LobbyIntel) CoralIntel.moduleManager.getModule("LobbyIntel");
            return lobbyIntel == null ? 0L : (long) (lobbyIntel.statCacheMinutes.getValue() * 60_000L);
        } catch (Exception e) {
            return 0L;
        }
    }

    private boolean isBordicFallbackEnabled() {
        try {
            coralintel.module.modules.LobbyIntel lobbyIntel =
                    (coralintel.module.modules.LobbyIntel) CoralIntel.moduleManager.getModule("LobbyIntel");
            return lobbyIntel != null && lobbyIntel.bordicFallback.getValue();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isBordicPrimary() {
        try {
            coralintel.module.modules.LobbyIntel lobbyIntel =
                    (coralintel.module.modules.LobbyIntel) CoralIntel.moduleManager.getModule("LobbyIntel");
            return lobbyIntel != null && lobbyIntel.bordicPrimary.getValue();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean fetchBordic(IntelPlayer player) {
        try {
            String uuid = fetchAndCacheUuid(player.name);
            if (uuid == null) {
                return false;
            }

            String json = null;
            synchronized (bordicCache) {
                Object[] cached = bordicCache.get(uuid);
                if (cached != null && System.currentTimeMillis() - (Long) cached[1] < BORDIC_CACHE_TTL_MS) {
                    json = (String) cached[0];
                }
            }

            if (json == null) {
                json = get(BORDIC_URL + uuid, null, null);
                if (json == null) {
                    return false;
                }
            }

            JsonObject root = new JsonParser().parse(json).getAsJsonObject();

            if (!root.has("success") || !root.get("success").getAsBoolean()) {
                return false;
            }

            if (!root.has("player") || !root.get("player").isJsonObject()) {
                return false;
            }

            boolean ok = applyHypixelProfile(root.getAsJsonObject("player"), player);

            if (ok) {
                synchronized (bordicCache) {
                    bordicCache.put(uuid, new Object[]{json, System.currentTimeMillis()});
                }
            }

            return ok;
        } catch (RateLimitedException exception) {
            throw exception;
        } catch (Exception exception) {
            dbg("[Intel] Bordic fetch failed for " + player.name + ": " + exception);
            return false;
        }
    }

    private boolean fetchSlothpixel(IntelPlayer player) {
        try {
            String json = get(
                    "https://api.slothpixel.me/api/players/" + player.name,
                    null,
                    null
            );

            if (json == null) {
                return false;
            }

            JsonObject root = new JsonParser().parse(json).getAsJsonObject();

            if (root.has("error")) {
                return false;
            }

            if (root.has("level")) {
                player.level = (int) root.get("level").getAsDouble();
            }

            JsonObject bedwars = null;

            if (root.has("stats")) {
                JsonObject stats = root.getAsJsonObject("stats");

                if (stats.has("Bedwars")) {
                    bedwars = stats.getAsJsonObject("Bedwars");
                }
            }

            if (bedwars == null) {
                // Don't zero out player.star here — if this is reached as a
                // fallback after fetchHypixelApi already populated it (e.g.
                // from the achievements endpoint), overwriting it with 0
                // was silently destroying legitimately-fetched data.
                return false;
            }

            player.star = bedwars.has("level")
                    ? bedwars.get("level").getAsInt()
                    : 0;

            int finalKills = bwInt(bedwars, "final_kills_bedwars");
            int rawFinalDeaths = bwInt(bedwars, "final_deaths_bedwars");

            int finalDeaths = rawFinalDeaths;
            if (finalDeaths == 0) finalDeaths = 1;

            int wins = bwInt(bedwars, "wins_bedwars");
            int rawLosses = bwInt(bedwars, "losses_bedwars");

            int losses = rawLosses;
            if (losses == 0) losses = 1;

            player.finalKills = finalKills;
            player.finalDeaths = rawFinalDeaths;
            player.bedsBroken = bwInt(bedwars, "beds_broken_bedwars");
            player.bedsLost = bwInt(bedwars, "beds_lost_bedwars");
            player.kills = bwInt(bedwars, "kills_bedwars");
            player.deaths = bwInt(bedwars, "deaths_bedwars");
            player.wins = wins;
            player.losses = rawLosses;
            player.winstreak = bwInt(bedwars, "winstreak");
            player.fkdr = (double) finalKills / finalDeaths;
            player.wlr = (double) wins / losses;

            player.statsHidden = false;
            player.statsComplete = root.has("level");

            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean fetchHypixelApi(IntelPlayer player) {
        try {
            hypixelSlots.acquire();

            try {
                long now = System.currentTimeMillis();
                long wait = HYPIXEL_INTERVAL_MS - (now - lastHypixelRequest.get());

                if (wait > 0) {
                    Thread.sleep(wait);
                }

                lastHypixelRequest.set(System.currentTimeMillis());
            } finally {
                hypixelSlots.release();
            }

            String uuid = fetchAndCacheUuid(player.name);

            if (uuid == null) {
                return false;
            }

            String json;
            try {
                json = get(
                        "https://api.hypixel.net/v2/player?uuid=" + uuid,
                        "API-Key",
                        hypixelApiKey
                );
            } catch (RateLimitedException e) {
                // Extra backoff on top of the normal spacing — push the next
                // request further out so we don't immediately hit the limit
                // again, then let this propagate as a retryable failure
                // (statsFetchFailed=true) instead of silently returning
                // false like every other kind of failure.
                lastHypixelRequest.set(System.currentTimeMillis() + 4000);
                dbg("[Intel] Hypixel API rate limited on " + player.name + " — backing off.");
                throw e;
            }

            if (json == null) {
                return false;
            }

            JsonObject root = new JsonParser().parse(json).getAsJsonObject();

            if (!root.has("success") || !root.get("success").getAsBoolean()) {
                return false;
            }

            if (!root.has("player") || root.get("player").isJsonNull()) {
                return false;
            }

            return applyHypixelProfile(root.getAsJsonObject("player"), player);
        } catch (RateLimitedException exception) {
            throw exception; // was swallowed here, so a 429 looked like "no stats"
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Maps a Hypixel-format player object (as returned by api.hypixel.net and by
     * Bordic's Hypixel cache) onto the IntelPlayer. Shared by both sources.
     */
    private boolean applyHypixelProfile(JsonObject profile, IntelPlayer player) {
        boolean hasLevel = profile.has("networkExp");

        if (hasLevel) {
            double networkExp = profile.get("networkExp").getAsDouble();

            player.level = (int) (
                    (Math.sqrt(networkExp + 15312.5) - 88.38) / 35.35
            );
        }

        JsonObject stats = profile.has("stats") && profile.get("stats").isJsonObject()
                ? profile.getAsJsonObject("stats")
                : null;

        JsonObject bedwars = stats != null && stats.has("Bedwars") && stats.get("Bedwars").isJsonObject()
                ? stats.getAsJsonObject("Bedwars")
                : null;

        int newStar;

        if (bedwars != null && bwInt(bedwars, "Experience") > 0) {
            newStar = getBedWarsLevelFromExp(bwInt(bedwars, "Experience"));
            player.starExact = getBedWarsExactLevelFromExp(bwInt(bedwars, "Experience"));
        } else {
            JsonObject achievements = profile.has("achievements") && profile.get("achievements").isJsonObject()
                    ? profile.getAsJsonObject("achievements")
                    : null;

            newStar = achievements != null ? bwInt(achievements, "bedwars_level") : 0;
        }

        // Never replace a star an earlier source already found with a 0.
        if (newStar > 0 || player.star == 0) {
            player.star = newStar;
        }

        if (bedwars == null) {
            // Account exists, but this player has hidden their Bedwars
            // stats via Hypixel's API Settings — not the same as "not
            // found". Whatever we already got (star, from achievements)
            // stays; don't fall through to Slothpixel, since it proxies
            // the same underlying data and would hit the identical
            // privacy restriction.
            player.statsHidden = true;
            return true;
        }

        int finalKills = bwInt(bedwars, "final_kills_bedwars");
        int rawFinalDeaths = bwInt(bedwars, "final_deaths_bedwars");

        int finalDeaths = rawFinalDeaths;
        if (finalDeaths == 0) finalDeaths = 1;

        int wins = bwInt(bedwars, "wins_bedwars");
        int rawLosses = bwInt(bedwars, "losses_bedwars");

        int losses = rawLosses;
        if (losses == 0) losses = 1;

        player.finalKills = finalKills;
        player.finalDeaths = rawFinalDeaths;
        player.bedsBroken = bwInt(bedwars, "beds_broken_bedwars");
        player.bedsLost = bwInt(bedwars, "beds_lost_bedwars");
        player.kills = bwInt(bedwars, "kills_bedwars");
        player.deaths = bwInt(bedwars, "deaths_bedwars");
        player.wins = wins;
        player.losses = rawLosses;
        player.winstreak = bwInt(bedwars, "winstreak");
        player.fkdr = (double) finalKills / finalDeaths;
        player.wlr = (double) wins / losses;

        // Fully loaded only when the Bedwars stats AND the network level are in.
        player.statsHidden = false;
        player.statsComplete = hasLevel;

        return true;
    }

    /** Null-safe: a null / non-numeric field used to throw and abort the whole profile (star kept, every other stat lost). */
    private int bwInt(JsonObject bedwars, String key) {
        try {
            if (!bedwars.has(key) || !bedwars.get(key).isJsonPrimitive()) {
                return 0;
            }

            double value = bedwars.get(key).getAsDouble();
            return (int) Math.max(0, Math.min(Integer.MAX_VALUE, value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private void fetchUrchinBatch(List<IntelPlayer> batch) {
        if (batch.isEmpty() || urchinApiKey.isEmpty()) {
            return;
        }

        for (IntelPlayer player : batch) {
            try {
                String uuid = fetchAndCacheUuid(player.name);

                if (uuid == null) {
                    continue;
                }

                String url = CORAL_CUBELIFY_URL
                        + "?uuid=" + uuid
                        + "&name=" + player.name
                        + "&sources=MANUAL"
                        + "&key=" + urchinApiKey;

                String response = get(url, null, null);

                if (response == null) {
                    continue;
                }

                JsonObject root = new JsonParser()
                        .parse(response)
                        .getAsJsonObject();

                if (!root.has("tags") || !root.get("tags").isJsonArray()) {
                    continue;
                }

                com.google.gson.JsonArray tags = root.getAsJsonArray("tags");

                if (tags.size() == 0) {
                    continue;
                }

                JsonObject tag = tags.get(0).getAsJsonObject();

                String icon = tag.has("icon") && !tag.get("icon").isJsonNull()
                        ? tag.get("icon").getAsString()
                        : "";

                String reason = tag.has("tooltip") && !tag.get("tooltip").isJsonNull()
                        ? tag.get("tooltip").getAsString()
                        : "";

                String text = tag.has("text") && !tag.get("text").isJsonNull()
                        ? tag.get("text").getAsString()
                        : (reason.isEmpty() ? icon : reason);

                // The Cubelify "icon" field is a Material Design icon id
                // (e.g. "mdi-account-alert"), NOT a classification string —
                // the actual human-readable severity lives in tooltip/text.
                // Classify against all three so keyword matching (closet /
                // confirmed / blatant / sniper) actually has something to match.
                String classifyBasis = (icon + " " + text + " " + reason).toLowerCase();

                boolean positiveTag = classifyBasis.contains("verified")
                        || classifyBasis.contains("clean")
                        || classifyBasis.contains("trusted")
                        || (classifyBasis.contains("legit") && !classifyBasis.contains("legitscaf"));

                if (positiveTag) {
                    // Not a cheat flag — e.g. a "Verified" clean-record tag.
                    player.cheater = false;
                    continue;
                }

                player.cheater = true;
                player.urchinTag = text + (reason.isEmpty() || reason.equalsIgnoreCase(text)
                        ? "" : " — " + reason);
                player.urchinType = classifyBasis;
                player.urchinReason = reason.toLowerCase();
                player.computeThreat();
            } catch (Exception exception) {
                // Per-player isolation — one bad UUID lookup or malformed
                // response used to abort the WHOLE batch (the try/catch used
                // to wrap the entire for-loop), silently skipping every
                // player after the failing one, including their unrelated
                // Hypixel stats fetch downstream. Now it only skips that
                // one player's cheat-tag lookup.
                dbg("[Coral] " + player.name + " error: " + exception.getMessage());
            }
        }
    }

    private void fetchGhostBatch(List<IntelPlayer> batch) {
        if (batch.isEmpty() || ghostApiKey.isEmpty()) {
            return;
        }

        for (IntelPlayer player : batch) {
            try {
                String url = GHOST_URL + "/" + player.name + "?key=" + ghostApiKey;
                String json = get(url, null, null);

                if (json == null) {
                    continue;
                }

                JsonObject root = new JsonParser().parse(json).getAsJsonObject();

                if (!root.has("tags") || !root.get("tags").isJsonArray()) {
                    continue;
                }

                com.google.gson.JsonArray tags = root.getAsJsonArray("tags");

                if (tags.size() == 0) {
                    continue;
                }

                JsonObject tag = tags.get(0).getAsJsonObject();

                player.ghostTagged = true;
                player.ghostType = tag.has("type")
                        ? tag.get("type").getAsString()
                        : "tagged";

                player.ghostReason = tag.has("reason")
                        ? tag.get("reason").getAsString()
                        : "";
            } catch (Exception exception) {
                dbg("[Ghost] " + player.name + " error: " + exception.getMessage());
            }
        }
    }

    public String getCachedUuid(String name) {
        synchronized (uuidCache) {
            return uuidCache.get(name);
        }
    }

    /**
     * Fires the instant you queue into a lobby with someone on your
     * blacklist — this is local/synchronous (no API call needed, unlike
     * notifyCheater), so it doesn't wait on the Coral/Ghost Intel fetch.
     */
    private void notifyBlacklisted(IntelPlayer player) {
        try {
            String reason = player.blacklistReason != null ? player.blacklistReason : "No reason given";

            coralintel.util.ChatUtil.sendFormatted(
                    "&9⚑ &f" + player.name + " &7is on your blacklist &7— " + reason
            );

            recentFlags.add(0, new FlagRecord(
                    player.name,
                    "Blacklisted: " + reason,
                    player.getTagColor(),
                    System.currentTimeMillis()
            ));
            while (recentFlags.size() > MAX_RECENT_FLAGS) {
                recentFlags.remove(recentFlags.size() - 1);
            }
        } catch (Exception ignored) {
        }
    }

    /** Alerts you (once per encounter) when someone from your .remind list is in the lobby. */
    private void notifyReminder(IntelPlayer player) {
        try {
            ReminderManager.getInstance().markInGame(player.name); // for the end-of-game "tag them" alert
            ReminderManager.Reminder reminder = ReminderManager.getInstance().recordEncounter(player.name);
            if (reminder == null) return;

            String note = reminder.latestNote();
            coralintel.util.ChatUtil.sendFormatted(
                    "&e[Reminder] &f" + player.name + " &7is on your list &8(met " + reminder.seen + "x) &7\u2014 "
                            + (note.isEmpty() ? "no note" : note)
            );
        } catch (Exception ignored) {
        }
    }

    private void notifyCheater(IntelPlayer player) {
        try {
            coralintel.module.modules.LobbyIntel lobbyIntel =
                    (coralintel.module.modules.LobbyIntel) CoralIntel.moduleManager
                            .getModule("LobbyIntel");

            if (lobbyIntel == null || !lobbyIntel.notifyCheaters.getValue()) {
                return;
            }

            String source = "";

            if (player.ghostTagged && player.cheater) {
                source = "Ghost Intel + Coral";
            } else if (player.ghostTagged) {
                source = "Ghost Intel";
            } else if (player.cheater) {
                source = "Coral";
            }

            if (!source.isEmpty()) {
                String message = player.getFullTagMessage();
                String suffix = message.isEmpty() ? "" : " &7— " + message;

                coralintel.util.ChatUtil.sendFormatted(
                        "&d⚑ &f" + player.name + " &7flagged by &d" + source + suffix
                );

                recentFlags.add(0, new FlagRecord(
                        player.name,
                        message.isEmpty() ? "Flagged by " + source : message,
                        player.getTagColor(),
                        System.currentTimeMillis()
                ));
                while (recentFlags.size() > MAX_RECENT_FLAGS) {
                    recentFlags.remove(recentFlags.size() - 1);
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Thrown when the API responds 429 (rate limited) — distinct from a
     * genuine "not found" (which silently returning null already models
     * fine). Previously a 429 was indistinguishable from "player not found",
     * which under load (a full 16-player lobby hitting the API in a burst)
     * silently killed stats for whoever got rate-limited, with no retry.
     * Callers catch this the same as any other Exception, which now
     * correctly marks statsFetchFailed=true so the 10-second retry loop
     * picks it back up, instead of the failure just being invisible.
     */
    private static class RateLimitedException extends RuntimeException {
        RateLimitedException(String url) {
            super("Rate limited: " + url);
        }
    }

    private String get(String url, String headerKey, String headerValue) {
        try {
            HttpURLConnection connection =
                    (HttpURLConnection) new URL(url).openConnection();

            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("User-Agent", "Spirit-Client/1.0");

            if (headerKey != null) {
                connection.setRequestProperty(headerKey, headerValue);
            }

            int code = connection.getResponseCode();

            if (code == 429) {
                throw new RateLimitedException(url);
            }

            if (code != 200) {
                return null;
            }

            return readStream(connection.getInputStream());
        } catch (RateLimitedException e) {
            throw e;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String readStream(InputStream input) throws IOException {
        return readStreamStatic(input);
    }

    public static String readStreamStatic(InputStream input) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        StringBuilder result = new StringBuilder();

        String line;

        while ((line = reader.readLine()) != null) {
            result.append(line);
        }

        reader.close();
        return result.toString();
    }

    /** Same table as getBedWarsLevelFromExp, but keeps the progress through the current level. */
    private static double getBedWarsExactLevelFromExp(int experience) {
        if (experience <= 0) {
            return 0;
        }

        final int prestigeExperience = 487000;

        double level = (experience / prestigeExperience) * 100;
        int remaining = experience % prestigeExperience;

        int[] earlyLevelCosts = {500, 1000, 2000, 3500, 5000};

        for (int cost : earlyLevelCosts) {
            if (remaining < cost) {
                return level + (double) remaining / cost;
            }

            remaining -= cost;
            level++;
        }

        return level + remaining / 5000.0;
    }

    private static int getBedWarsLevelFromExp(int experience) {
        if (experience <= 0) {
            return 0;
        }

        // 500 + 1,000 + 2,000 + 3,500 + ninety-six 5,000-XP levels.
        final int prestigeExperience = 487000;

        int level = (experience / prestigeExperience) * 100;
        int remaining = experience % prestigeExperience;

        int[] earlyLevelCosts = {500, 1000, 2000, 3500, 5000};

        for (int cost : earlyLevelCosts) {
            if (remaining < cost) {
                return level;
            }

            remaining -= cost;
            level++;
        }

        return level + remaining / 5000;
    }

    /**
     * Pulls the Hypixel rank prefix (e.g. "§b[MVP§9+§b]") out of the tab-list
     * display name by finding where the raw username starts and taking
     * everything before it. Returns "" for non-donor players / no match.
     */
    private String extractRankPrefix(NetworkPlayerInfo info, String rawName) {
        try {
            if (info.getDisplayName() == null) return "";

            String formatted = info.getDisplayName().getFormattedText();
            if (formatted == null || rawName == null) return "";

            int idx = formatted.indexOf(rawName);
            if (idx <= 0) return "";

            return formatted.substring(0, idx).trim();
        } catch (Exception exception) {
            return "";
        }
    }

    /**
     * Team from the scoreboard team's color prefix (how Mellow does it). The
     * old version searched the display name for color codes, which also
     * matched rank colors ([MVP+] etc.) and gave wrong teams.
     */
    private String detectTeam(NetworkPlayerInfo info) {
        try {
            net.minecraft.scoreboard.ScorePlayerTeam team = info.getPlayerTeam();
            if (team == null) return null;

            String prefix = team.getColorPrefix();
            if (prefix == null) return null;

            for (int i = 0; i + 1 < prefix.length(); i++) {
                if (prefix.charAt(i) != '\u00A7') continue;
                switch (Character.toLowerCase(prefix.charAt(i + 1))) {
                    case 'c': return "red";
                    case '9': return "blue";
                    case 'a': return "green";
                    case 'e': return "yellow";
                    case 'b': return "aqua";
                    case 'f': return "white";
                    case 'd': return "pink";
                    case '7':
                    case '8': return "gray";
                    default: break; // style code (bold etc.) — keep looking
                }
            }
        } catch (Exception ignored) {
        }

        return null;
    }
}
