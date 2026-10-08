package coralintel.ui.intel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Disk cache of fully-loaded Bedwars stats (./config/CoralIntel/stat-cache.json).
 *
 * Re-queuing into players seen a few minutes ago reuses their stats instead of
 * calling the APIs again, which also eases rate-limit pressure on the API key.
 * Only COMPLETE results are stored, never nicks. Entries expire after the TTL
 * (set in the LobbyIntel settings, 0 = off) and anything older than MAX_AGE_MS is
 * dropped from the file regardless.
 */
public final class StatCache {
    private static final StatCache INSTANCE = new StatCache();

    /** Matches the top of the TTL slider; older entries are never useful. */
    private static final long MAX_AGE_MS = 30L * 60L * 1000L;
    private static final long SAVE_THROTTLE_MS = 3000L;

    private final Gson gson = new GsonBuilder().create();
    private final File file = new File("./config/CoralIntel/stat-cache.json");
    private final Map<String, Entry> entries = new HashMap<>();
    private boolean loaded = false;
    private boolean dirty = false;
    private long lastSave = 0L;

    public static StatCache getInstance() {
        return INSTANCE;
    }

    private static class Entry {
        long at;
        int star, level, winstreak, finalKills, finalDeaths, bedsBroken, bedsLost, kills, deaths, wins, losses;
        double fkdr, wlr;
    }

    private StatCache() {
    }

    private void loadIfNeeded() {
        if (loaded) return;
        loaded = true;

        try {
            if (!file.exists()) return;

            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            JsonElement parsed = new JsonParser().parse(text);
            if (!parsed.isJsonObject()) return;

            long now = System.currentTimeMillis();
            for (Map.Entry<String, JsonElement> e : parsed.getAsJsonObject().entrySet()) {
                try {
                    Entry entry = gson.fromJson(e.getValue(), Entry.class);
                    if (entry != null && now - entry.at < MAX_AGE_MS) {
                        entries.put(e.getKey(), entry);
                    }
                } catch (Exception ignored) {
                    // one bad entry must not lose the rest
                }
            }
        } catch (Exception exception) {
            IntelManager.dbg("[Intel] stat cache load failed: " + exception);
        }
    }

    /** Copies fresh cached stats onto the player. @return true on a hit. */
    public synchronized boolean apply(IntelPlayer player, long ttlMs) {
        if (ttlMs <= 0) return false;
        loadIfNeeded();

        Entry entry = entries.get(player.name.toLowerCase(Locale.ROOT));
        if (entry == null || System.currentTimeMillis() - entry.at >= ttlMs) return false;

        player.star = entry.star;
        player.level = entry.level;
        player.winstreak = entry.winstreak;
        player.finalKills = entry.finalKills;
        player.finalDeaths = entry.finalDeaths;
        player.bedsBroken = entry.bedsBroken;
        player.bedsLost = entry.bedsLost;
        player.kills = entry.kills;
        player.deaths = entry.deaths;
        player.wins = entry.wins;
        player.losses = entry.losses;
        player.fkdr = entry.fkdr;
        player.wlr = entry.wlr;
        return true;
    }

    /** Stores a fully-loaded player (call only when statsComplete and not nicked). */
    public synchronized void put(IntelPlayer player) {
        loadIfNeeded();

        Entry entry = new Entry();
        entry.at = System.currentTimeMillis();
        entry.star = player.star;
        entry.level = player.level;
        entry.winstreak = player.winstreak;
        entry.finalKills = player.finalKills;
        entry.finalDeaths = player.finalDeaths;
        entry.bedsBroken = player.bedsBroken;
        entry.bedsLost = player.bedsLost;
        entry.kills = player.kills;
        entry.deaths = player.deaths;
        entry.wins = player.wins;
        entry.losses = player.losses;
        entry.fkdr = player.fkdr;
        entry.wlr = player.wlr;

        entries.put(player.name.toLowerCase(Locale.ROOT), entry);
        dirty = true;
        saveIfDue(false);
    }

    /** Writes the file now if anything changed (used on world changes / shutdown). */
    public synchronized void flush() {
        saveIfDue(true);
    }

    private void saveIfDue(boolean force) {
        long now = System.currentTimeMillis();
        if (!dirty || (!force && now - lastSave < SAVE_THROTTLE_MS)) return;

        entries.values().removeIf(e -> now - e.at >= MAX_AGE_MS);

        try {
            file.getParentFile().mkdirs();
            JsonObject root = new JsonObject();
            for (Map.Entry<String, Entry> e : entries.entrySet()) {
                root.add(e.getKey(), gson.toJsonTree(e.getValue()));
            }
            Files.write(file.toPath(), gson.toJson(root).getBytes(StandardCharsets.UTF_8));
            dirty = false;
            lastSave = now;
        } catch (IOException exception) {
            IntelManager.dbg("[Intel] stat cache save failed: " + exception);
        }
    }
}
