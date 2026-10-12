package coralintel.duels;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Disk cache of fully loaded Duels stats (./config/CoralIntel/duels-cache.json), separate from the
 * Bedwars stat cache. Re-queuing into players seen a few minutes ago reuses their stats instead of
 * calling the API again.
 */
public final class DuelsStatCache {
    private static final DuelsStatCache INSTANCE = new DuelsStatCache();
    private static final long MAX_AGE_MS = 30L * 60L * 1000L;
    private static final long SAVE_THROTTLE_MS = 3000L;

    private final Gson gson = new GsonBuilder().create();
    private final File file = new File("./config/CoralIntel/duels-cache.json");
    private final Map<String, Entry> entries = new HashMap<>();
    private boolean loaded = false;
    private boolean dirty = false;
    private long lastSave = 0L;

    public static DuelsStatCache getInstance() {
        return INSTANCE;
    }

    /** Each array is {wins, losses, kills, deaths, winstreak, bestWinstreak}. */
    private static class Entry {
        long at;
        int[] overall, classic, bridge, bedwars;
    }

    private DuelsStatCache() {
    }

    private static int[] pack(DuelsPlayer.Mode m) {
        return new int[]{m.wins, m.losses, m.kills, m.deaths, m.winstreak, m.bestWinstreak};
    }

    private static void unpack(int[] a, DuelsPlayer.Mode m) {
        if (a == null || a.length < 6) return;
        m.wins = a[0];
        m.losses = a[1];
        m.kills = a[2];
        m.deaths = a[3];
        m.winstreak = a[4];
        m.bestWinstreak = a[5];
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
        } catch (Exception ignored) {
        }
    }

    /** Copies fresh cached stats onto the player. @return true on a hit. */
    public synchronized boolean apply(DuelsPlayer player, long ttlMs) {
        if (ttlMs <= 0) return false;
        loadIfNeeded();

        Entry entry = entries.get(player.name.toLowerCase(Locale.ROOT));
        if (entry == null || System.currentTimeMillis() - entry.at >= ttlMs) return false;

        unpack(entry.overall, player.overall);
        unpack(entry.classic, player.classic);
        unpack(entry.bridge, player.bridge);
        unpack(entry.bedwars, player.bedwars);
        player.duelsLoaded = true;
        player.statsFromCache = true;
        return true;
    }

    /** Stores a fully loaded player (never nicks). */
    public synchronized void store(DuelsPlayer player) {
        if (player.isNicked || !player.duelsLoaded) return;
        loadIfNeeded();

        Entry entry = new Entry();
        entry.at = System.currentTimeMillis();
        entry.overall = pack(player.overall);
        entry.classic = pack(player.classic);
        entry.bridge = pack(player.bridge);
        entry.bedwars = pack(player.bedwars);
        entries.put(player.name.toLowerCase(Locale.ROOT), entry);
        dirty = true;
        saveThrottled();
    }

    private void saveThrottled() {
        long now = System.currentTimeMillis();
        if (!dirty || now - lastSave < SAVE_THROTTLE_MS) return;
        lastSave = now;
        dirty = false;

        try {
            long cutoff = now - MAX_AGE_MS;
            entries.values().removeIf(e -> e.at < cutoff);
            file.getParentFile().mkdirs();
            Files.write(file.toPath(), gson.toJson(entries).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }
}
