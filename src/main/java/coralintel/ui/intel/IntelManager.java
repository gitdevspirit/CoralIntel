package coralintel.ui.intel;

import com.google.gson.JsonElement;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

public class IntelManager {

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
    private final List<IntelPlayer> players = new ArrayList<>();
    private final List<IntelPlayer> manualPlayers = new ArrayList<>();

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

    public IntelPlayer getPlayer(String name) {
        if (name == null) return null;

        for (IntelPlayer player : new ArrayList<>(players)) {
            if (name.equalsIgnoreCase(player.name)) {
                return player;
            }
        }

        for (IntelPlayer player : new ArrayList<>(manualPlayers)) {
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

        for (IntelPlayer player : manualPlayers) {
            if (player.name.equalsIgnoreCase(name)) {
                return;
            }
        }

        IntelPlayer player = new IntelPlayer(name, null);
        manualPlayers.add(player);

        List<IntelPlayer> combined = combined();

        if (gui != null) {
            gui.setPlayers(combined);
        }

        if (hudOverlay != null) {
            hudOverlay.setPlayers(combined);
        }

        pool.submit(() -> {
            try {
                fetchAndCacheUuid(player.name);
            } catch (Exception exception) {
                dbg("[Intel] UUID fetch failed for " + player.name
                        + ": " + exception);
            } finally {
                List<IntelPlayer> refreshed = combined();

                if (gui != null) {
                    gui.setPlayers(refreshed);
                }

                if (hudOverlay != null) {
                    hudOverlay.setPlayers(refreshed);
                }
            }
        });

        pool.submit(() -> {
            try {
                fetchUrchinBatch(java.util.Collections.singletonList(player));
                fetchHypixel(player);
                player.computeThreat();
            } catch (Exception exception) {
                player.loading = false;
                dbg("[Intel] add-player fetch failed for " + player.name
                        + ": " + exception);
            } finally {
                List<IntelPlayer> refreshed = combined();

                if (gui != null) {
                    gui.setPlayers(refreshed);
                }

                if (hudOverlay != null) {
                    hudOverlay.setPlayers(refreshed);
                }
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

        List<IntelPlayer> newRoster = new ArrayList<>();
        List<IntelPlayer> needsFetch = new ArrayList<>();

        for (NetworkPlayerInfo info : minecraft.getNetHandler().getPlayerInfoMap()) {
            String name = info.getGameProfile().getName();

            if (isNpc(info)) {
                continue;
            }

            String team = detectTeam(info);
            IntelPlayer existing = existingByName.get(name.toLowerCase());

            IntelPlayer player;
            if (existing != null) {
                player = existing;
                player.team = team;
            } else {
                player = new IntelPlayer(name, team);
                needsFetch.add(player);
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

            gui.setPlayers(new ArrayList<>(players));
        }

        if (hudOverlay != null) {
            hudOverlay.setPlayers(new ArrayList<>(players));
        }

        if (!needsFetch.isEmpty()) {
            final List<IntelPlayer> batch = new ArrayList<>(needsFetch);

            pool.submit(() -> {
                try {
                    fetchUrchinBatch(batch);
                    fetchGhostBatch(batch);

                    for (IntelPlayer player : batch) {
                        if (player.cheater || player.ghostTagged) {
                            notifyCheater(player);
                        }
                    }
                } catch (Exception exception) {
                    dbg("[Intel] tag batch failed: " + exception);
                } finally {
                    List<IntelPlayer> refreshed = new ArrayList<>(players);

                    if (gui != null) {
                        gui.setPlayers(refreshed);
                    }

                    if (hudOverlay != null) {
                        hudOverlay.setPlayers(refreshed);
                    }
                }
            });
        }

        for (IntelPlayer player : needsFetch) {
            final IntelPlayer current = player;

            pool.submit(() -> {
                try {
                    fetchHypixel(current);
                    current.computeThreat();
                } catch (Exception exception) {
                    current.loading = false;
                    dbg("[Intel] stats fetch failed for " + current.name
                            + ": " + exception);
                } finally {
                    List<IntelPlayer> refreshed = new ArrayList<>(players);

                    if (gui != null) {
                        gui.setPlayers(refreshed);
                    }

                    if (hudOverlay != null) {
                        hudOverlay.setPlayers(refreshed);
                    }
                }
            });
        }

        fetching = false;
    }

    /**
     * Forces a full re-fetch of every player currently in the lobby,
     * ignoring cached stats. Use sparingly — this is what re-triggers the
     * full sequential rate-limited fetch for the whole lobby.
     */
    public void forceRefreshAll() {
        players.clear();
        scanLobby();
    }

    public void refresh() {
        scanLobby();
    }

    public void clearAll() {
        players.clear();

        if (gui != null) {
            gui.setPlayers(new ArrayList<>());
        }

        if (hudOverlay != null) {
            hudOverlay.setPlayers(new ArrayList<>());
        }
    }

    public void resetInvalidKeyFlag() {
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
        IntelPlayer player = new IntelPlayer(name, null);

        try {
            fetchHypixel(player);
        } catch (Exception exception) {
            player.loading = false;
            dbg("[Intel] standalone stats fetch failed for " + name
                    + ": " + exception);
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

    private void fetchHypixel(IntelPlayer player) {
        boolean gotStats = false;

        if (!hypixelApiKey.isEmpty()) {
            gotStats = fetchHypixelApi(player);
        }

        if (!gotStats) {
            fetchSlothpixel(player);
        }

        player.loading = false;
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
                player.star = 0;
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

            String json = get(
                    "https://api.hypixel.net/v2/player?uuid=" + uuid,
                    "API-Key",
                    hypixelApiKey
            );

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

            JsonObject profile = root.getAsJsonObject("player");

            if (profile.has("networkExp")) {
                double networkExp = profile.get("networkExp").getAsDouble();

                player.level = (int) (
                        (Math.sqrt(networkExp + 15312.5) - 88.38) / 35.35
                );
            }

            JsonObject stats = profile.has("stats")
                    ? profile.getAsJsonObject("stats")
                    : null;

            JsonObject bedwars = stats != null && stats.has("Bedwars")
                    ? stats.getAsJsonObject("Bedwars")
                    : null;

            if (bedwars != null && bedwars.has("Experience")) {
                player.star = getBedWarsLevelFromExp(
                        bedwars.get("Experience").getAsInt()
                );
            } else {
                JsonObject achievements = profile.has("achievements")
                        ? profile.getAsJsonObject("achievements")
                        : null;

                player.star = achievements != null && achievements.has("bedwars_level")
                        ? achievements.get("bedwars_level").getAsInt()
                        : 0;
            }

            if (bedwars == null) {
                return false;
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

            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
        private int bwInt(JsonObject bedwars, String key) {
        return bedwars.has(key) ? bedwars.get(key).getAsInt() : 0;
    }

    private void fetchUrchinBatch(List<IntelPlayer> batch) {
        if (batch.isEmpty() || urchinApiKey.isEmpty()) {
            return;
        }

        try {
            for (IntelPlayer player : batch) {
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
            }
        } catch (Exception ignored) {
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
                coralintel.util.ChatUtil.sendFormatted(
                        "&d⚑ &f" + player.name + " &7flagged by &d" + source
                );
            }
        } catch (Exception ignored) {
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

            if (connection.getResponseCode() != 200) {
                return null;
            }

            return readStream(connection.getInputStream());
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

    private String detectTeam(NetworkPlayerInfo info) {
        try {
            String displayName = info.getDisplayName() != null
                    ? info.getDisplayName().getFormattedText()
                    : "";

            if (displayName.contains("§c")) return "red";
            if (displayName.contains("§9")) return "blue";
            if (displayName.contains("§a")) return "green";
            if (displayName.contains("§e")) return "yellow";
            if (displayName.contains("§b")) return "aqua";
            if (displayName.contains("§f")) return "white";
            if (displayName.contains("§d")) return "pink";
            if (displayName.contains("§8")) return "gray";
        } catch (Exception ignored) {
        }

        return null;
    }
}
