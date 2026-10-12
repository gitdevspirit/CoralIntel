package coralintel.duels;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import coralintel.CoralIntel;
import coralintel.module.modules.DuelsIntel;
import coralintel.ui.intel.BedlifyManager;
import coralintel.ui.intel.IntelManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Roster + stats loading for the Duels lobby intel. Separate from the Bedwars IntelManager: it keeps its own
 * player list, cache and fetch queue, and only borrows the shared pieces (Hypixel API key, UUID lookup).
 */
public final class DuelsManager {
    private static final DuelsManager INSTANCE = new DuelsManager();

    public static DuelsManager getInstance() {
        return INSTANCE;
    }

    /** After this many fetch rounds with no usable stats a player's stats are just set to 0. */
    public static final int MAX_FETCH_ATTEMPTS = 3;

    private static final long HYPIXEL_INTERVAL_MS = 600L;
    private static final String BORDIC_URL = "https://api.bordic.xyz/v3/cache/hypixel?uuid=";

    private final Minecraft mc = Minecraft.getMinecraft();
    private final List<DuelsPlayer> players = new CopyOnWriteArrayList<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "CoralIntel-Duels-Fetch");
        t.setDaemon(true);
        return t;
    });
    private final Object rateLock = new Object();
    private final AtomicLong lastRequest = new AtomicLong(0);

    private DuelsManager() {
    }

    // ------------------------------------------------------------------ roster

    public List<DuelsPlayer> getPlayers() {
        return new ArrayList<>(players);
    }

    public DuelsPlayer getPlayer(String name) {
        if (name == null) return null;
        for (DuelsPlayer p : players) {
            if (p.name.equalsIgnoreCase(name)) return p;
        }
        return null;
    }

    public void clearAll() {
        players.clear();
    }

    public void forgetPlayer(String name) {
        DuelsPlayer p = getPlayer(name);
        if (p != null) players.remove(p);
    }

    /** Adds a player by name (manual add / chat) and starts loading their stats. No-op if already on the roster. */
    public boolean addPlayer(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_]{1,16}")) return false;
        if (getPlayer(name) != null) return true;

        NetworkPlayerInfo info = mc.getNetHandler() != null ? mc.getNetHandler().getPlayerInfo(name) : null;
        if (info != null && IntelManager.isNpc(info)) return false;

        DuelsPlayer p = new DuelsPlayer(name);
        if (info != null) {
            p.rankPrefix = extractRankPrefix(info, name);
            if (isNickUuid(info.getGameProfile().getId())) {
                markNicked(p);
                players.add(p);
                return true;
            }
        }

        players.add(p);
        startFetch(p);
        return true;
    }

    /** A chatter whose account does not exist (or whose tab UUID is a nick's): shown as a nick, no stats. */
    public void addNickedChatter(String name) {
        DuelsPlayer p = getPlayer(name);
        if (p == null) {
            p = new DuelsPlayer(name);
            players.add(p);
        }
        markNicked(p);
    }

    /** Adds everyone in the tab list who is not already on the roster. Cheap; safe to call every few seconds. */
    public void scanLobby() {
        if (mc.getNetHandler() == null) return;
        Collection<NetworkPlayerInfo> infos = mc.getNetHandler().getPlayerInfoMap();
        if (infos == null) return;

        String self = mc.thePlayer != null ? mc.thePlayer.getName() : "";
        for (NetworkPlayerInfo info : new ArrayList<>(infos)) {
            if (info == null || info.getGameProfile() == null) continue;
            String name = info.getGameProfile().getName();
            if (name == null || IntelManager.isNpc(info)) continue;
            if (!name.matches("[A-Za-z0-9_]{1,16}")) continue; // obfuscated pregame names never match

            DuelsPlayer existing = getPlayer(name);
            if (existing != null) {
                if (existing.rankPrefix.isEmpty()) existing.rankPrefix = extractRankPrefix(info, name);
                continue;
            }
            if (skipSelf() && name.equalsIgnoreCase(self)) continue;

            DuelsPlayer p = new DuelsPlayer(name);
            p.rankPrefix = extractRankPrefix(info, name);
            players.add(p);
            if (isNickUuid(info.getGameProfile().getId())) {
                markNicked(p);
            } else {
                startFetch(p);
            }
        }
    }

    private static boolean isNickUuid(java.util.UUID uuid) {
        return uuid != null && uuid.version() == 1;
    }

    private static String extractRankPrefix(NetworkPlayerInfo info, String rawName) {
        try {
            if (info.getDisplayName() == null) return "";
            String formatted = info.getDisplayName().getFormattedText();
            if (formatted == null || rawName == null) return "";
            int idx = formatted.indexOf(rawName);
            return idx <= 0 ? "" : formatted.substring(0, idx).trim();
        } catch (Exception e) {
            return "";
        }
    }

    // ------------------------------------------------------------------ settings access

    private DuelsIntel module() {
        try {
            return (DuelsIntel) CoralIntel.moduleManager.getModule(DuelsIntel.class);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean skipSelf() {
        DuelsIntel m = module();
        return m != null && m.skipSelfStats.getValue();
    }

    private long cacheTtlMs() {
        DuelsIntel m = module();
        return m == null ? 0L : (long) (m.statCacheMinutes.getValue() * 60_000L);
    }

    private boolean bordicPrimary() {
        DuelsIntel m = module();
        return m != null && m.bordicPrimary.getValue();
    }

    private boolean bordicFallback() {
        DuelsIntel m = module();
        return m != null && m.bordicFallback.getValue();
    }

    // ------------------------------------------------------------------ nicks

    private void markNicked(DuelsPlayer p) {
        p.isNicked = true;
        p.loading = false;
        p.statsFinal = true;
        p.statsFetchFailed = false;
        requestDenick(p);
    }

    private void requestDenick(DuelsPlayer p) {
        DuelsIntel m = module();
        if (m == null || !m.denick.getValue() || p.denickRequested) return;
        BedlifyManager bedlify = BedlifyManager.getInstance();
        if (!bedlify.hasKey()) return;

        p.denickRequested = true;
        bedlify.lookupAsync(p.name, result -> {
            if (result != null && result.error == null && !result.candidates.isEmpty()) {
                p.realName = result.candidates.get(0).name;
            }
        });
    }

    // ------------------------------------------------------------------ fetching

    public void startFetch(final DuelsPlayer p) {
        if (p.fetchInFlight || p.statsFinal) return;
        p.fetchInFlight = true;
        pool.submit(() -> runFetch(p));
    }

    /** Re-tries players that are not fully loaded yet. Called every ~10 seconds from the module's tick. */
    public void retryUnloaded() {
        for (DuelsPlayer p : players) {
            if (!p.isNicked && !p.statsFinal && !p.fetchInFlight) startFetch(p);
        }
    }

    private void runFetch(DuelsPlayer p) {
        try {
            boolean ok = false;
            try {
                ok = load(p);
                p.statsFetchFailed = false;
            } catch (Exception e) {
                p.statsFetchFailed = true;
                IntelManager.dbg("[Duels] stats fetch failed for " + p.name + ": " + e);
            }

            if (ok) {
                p.loading = false;
                p.statsFinal = true;
                if (!p.statsFromCache) DuelsStatCache.getInstance().store(p);
                return;
            }

            p.fetchAttempts++;
            if (p.fetchAttempts >= MAX_FETCH_ATTEMPTS) {
                p.zero(); // nothing usable after repeated tries: show zeros instead of loading forever
                p.loading = false;
                p.statsFetchFailed = false;
                p.statsFinal = true;
                IntelManager.dbg("[Duels] " + p.name + " returned no stats " + p.fetchAttempts + " times, set to 0.");
            } else {
                p.loading = true;
            }
        } finally {
            p.fetchInFlight = false;
        }
    }

    /** One load attempt: cache, then Hypixel (or Bordic first when chosen), then the other as fallback. */
    private boolean load(DuelsPlayer p) {
        if (DuelsStatCache.getInstance().apply(p, cacheTtlMs())) return true;

        boolean hasKey = !IntelManager.hypixelApiKey.isEmpty();
        boolean tryBordicFirst = bordicPrimary() || !hasKey;

        if (tryBordicFirst) {
            if (fetchBordic(p)) return true;
            return hasKey && fetchHypixel(p);
        }

        if (fetchHypixel(p)) return true;
        return bordicFallback() && fetchBordic(p);
    }

    private boolean fetchHypixel(DuelsPlayer p) {
        try {
            throttle();
            String uuid = IntelManager.getInstance().resolveUuid(p.name);
            if (uuid == null) return false;

            String json = httpGet("https://api.hypixel.net/v2/player?uuid=" + uuid,
                    "API-Key", IntelManager.hypixelApiKey);
            return json != null && applyResponse(json, p);
        } catch (RuntimeException e) {
            if (e instanceof RateLimited) {
                lastRequest.set(System.currentTimeMillis() + 4000); // back off before the next request
                throw e;
            }
            return false;
        }
    }

    private boolean fetchBordic(DuelsPlayer p) {
        try {
            String uuid = IntelManager.getInstance().resolveUuid(p.name);
            if (uuid == null) return false;
            String json = httpGet(BORDIC_URL + uuid, null, null);
            return json != null && applyResponse(json, p);
        } catch (RuntimeException e) {
            if (e instanceof RateLimited) throw e;
            return false;
        }
    }

    private void throttle() {
        synchronized (rateLock) {
            long wait = HYPIXEL_INTERVAL_MS - (System.currentTimeMillis() - lastRequest.get());
            if (wait > 0) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            lastRequest.set(System.currentTimeMillis());
        }
    }

    private boolean applyResponse(String json, DuelsPlayer p) {
        try {
            JsonObject root = new JsonParser().parse(json).getAsJsonObject();
            if (!root.has("success") || !root.get("success").getAsBoolean()) return false;
            if (!root.has("player") || !root.get("player").isJsonObject()) return false;
            return applyProfile(root.getAsJsonObject("player"), p);
        } catch (Exception e) {
            return false;
        }
    }

    /** Maps a Hypixel-format player object onto the DuelsPlayer (shared by Hypixel and Bordic). */
    private boolean applyProfile(JsonObject profile, DuelsPlayer p) {
        JsonObject stats = profile.has("stats") && profile.get("stats").isJsonObject()
                ? profile.getAsJsonObject("stats") : null;
        if (stats == null) {
            p.statsHidden = true; // stats hidden entirely via API settings
            return false;
        }

        JsonObject duels = stats.has("Duels") && stats.get("Duels").isJsonObject()
                ? stats.getAsJsonObject("Duels") : null;
        p.statsHidden = false;

        if (duels == null) {
            p.duelsLoaded = true; // account has never played Duels: real zeros
            return true;
        }

        DuelsPlayer.Mode o = p.overall;
        o.wins = num(duels, "wins");
        o.losses = num(duels, "losses");
        o.kills = num(duels, "kills");
        o.deaths = num(duels, "deaths");
        o.winstreak = num(duels, "current_winstreak");
        o.bestWinstreak = Math.max(num(duels, "best_overall_winstreak"), o.winstreak);

        readMode(duels, p.classic, "classic_duel",
                new String[]{"classic_duel_kills"}, new String[]{"classic_duel_deaths"});
        readMode(duels, p.bridge, "bridge_duel",
                new String[]{"bridge_duel_bridge_kills", "bridge_duel_kills"},
                new String[]{"bridge_duel_bridge_deaths", "bridge_duel_deaths"});
        readMode(duels, p.bedwars, "bedwars_two_one_duels",
                new String[]{"bedwars_two_one_duels_kills"}, new String[]{"bedwars_two_one_duels_deaths"});

        p.duelsLoaded = true;
        return true;
    }

    private void readMode(JsonObject duels, DuelsPlayer.Mode out, String mode, String[] killKeys, String[] deathKeys) {
        out.wins = num(duels, mode + "_wins");
        out.losses = num(duels, mode + "_losses");
        out.kills = first(duels, killKeys);
        out.deaths = first(duels, deathKeys);
        out.winstreak = num(duels, "current_winstreak_mode_" + mode);
        out.bestWinstreak = Math.max(num(duels, "best_winstreak_mode_" + mode), out.winstreak);
    }

    private static int first(JsonObject obj, String... keys) {
        for (String key : keys) {
            if (obj.has(key) && !obj.get(key).isJsonNull()) return num(obj, key);
        }
        return 0;
    }

    private static int num(JsonObject obj, String key) {
        try {
            JsonElement e = obj.get(key);
            return e == null || e.isJsonNull() ? 0 : e.getAsInt();
        } catch (Exception ignored) {
            return 0;
        }
    }

    // ------------------------------------------------------------------ standalone lookup (.duels)

    /** Blocking one-off lookup for the .duels command; always fetches fresh. Run off the main thread. */
    public DuelsPlayer fetchStandalone(String name) {
        DuelsPlayer p = new DuelsPlayer(name);
        for (int attempt = 0; attempt < MAX_FETCH_ATTEMPTS; attempt++) {
            try {
                if (load(p)) {
                    p.loading = false;
                    p.statsFinal = true;
                    return p;
                }
            } catch (Exception ignored) {
            }
        }
        p.loading = false;
        return p;
    }

    // ------------------------------------------------------------------ http

    private static class RateLimited extends RuntimeException {
        RateLimited(String url) {
            super("rate limited: " + url);
        }
    }

    private String httpGet(String url, String headerKey, String headerValue) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("User-Agent", "Spirit-Client/1.0");
            if (headerKey != null) connection.setRequestProperty(headerKey, headerValue);

            int code = connection.getResponseCode();
            if (code == 429) throw new RateLimited(url);
            if (code != 200) return null;
            return IntelManager.readStreamStatic(connection.getInputStream());
        } catch (RateLimited e) {
            throw e;
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Lower-case helper kept for callers that compare names. */
    public static String key(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }
}
