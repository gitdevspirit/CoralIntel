package coralintel.ui.intel;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Local history of players' Bedwars totals, used by .daily / .monthly.
 *
 * Hypixel's API only returns lifetime totals, so "stats over the last 24
 * hours" is worked out as (current totals) - (totals from a snapshot taken
 * about 24 hours ago). A snapshot is saved every time a player's stats are
 * fully loaded (lobby scan, .bw, .daily, .monthly), at most once per
 * MIN_GAP_MS per player. Saved to ./config/CoralIntel/snapshots.json.
 */
public class StatSnapshotManager {
    private static StatSnapshotManager instance;
    private static final Gson gson = new Gson();
    private static final File FILE = new File("./config/CoralIntel/snapshots.json");

    private static final long MINUTE = 60L * 1000L;
    private static final long HOUR = 60L * MINUTE;
    private static final long DAY = 24L * HOUR;

    /** At most one snapshot per player per this long. */
    private static final long MIN_GAP_MS = 20L * MINUTE;
    /** Snapshots older than this are dropped (monthly window is 30 days). */
    private static final long KEEP_MS = 35L * DAY;
    /** A baseline younger than this is just "the stats we fetched a moment ago". */
    private static final long MIN_BASELINE_MS = 10L * MINUTE;
    private static final long SAVE_THROTTLE_MS = 15_000L;

    public static class Snapshot {
        public long t;
        public int fk, fd, k, d, w, l, bb, bl;
    }

    /** Change in totals between a baseline snapshot and the current stats. */
    public static class Delta {
        public long baselineAgeMs;
        public boolean partial; // no snapshot at the full window mark; used the oldest one we have
        public int finalKills, finalDeaths, kills, deaths, wins, losses, bedsBroken, bedsLost;

        public boolean isEmpty() {
            return finalKills == 0 && finalDeaths == 0 && kills == 0 && deaths == 0
                    && wins == 0 && losses == 0 && bedsBroken == 0 && bedsLost == 0;
        }
    }

    private final Map<String, List<Snapshot>> data = new HashMap<>();
    private boolean dirty = false;
    private long lastSave = 0;

    private StatSnapshotManager() {
        load();
    }

    public static synchronized StatSnapshotManager getInstance() {
        if (instance == null) {
            instance = new StatSnapshotManager();
        }
        return instance;
    }

    /** Saves a snapshot of this player's totals if they are fully loaded (and one isn't too recent). */
    public synchronized void record(IntelPlayer p) {
        if (p == null || p.name == null || !p.statsComplete || p.isNicked) {
            return;
        }

        String key = p.name.toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();

        List<Snapshot> list = data.get(key);
        if (list == null) {
            list = new ArrayList<>();
            data.put(key, list);
        }

        if (!list.isEmpty() && now - list.get(list.size() - 1).t < MIN_GAP_MS) {
            return;
        }

        Snapshot s = new Snapshot();
        s.t = now;
        s.fk = p.finalKills;
        s.fd = p.finalDeaths;
        s.k = p.kills;
        s.d = p.deaths;
        s.w = p.wins;
        s.l = p.losses;
        s.bb = p.bedsBroken;
        s.bl = p.bedsLost;
        list.add(s);

        long cutoff = now - KEEP_MS;
        while (list.size() > 1 && list.get(0).t < cutoff) {
            list.remove(0);
        }
        if (list.size() > 60) {
            thin(list, now);
        }

        dirty = true;
        if (now - lastSave > SAVE_THROTTLE_MS) {
            save();
        }
    }

    /**
     * Current totals minus the baseline snapshot for the window. Returns null
     * when there is no usable baseline (never seen before, only seen a moment
     * ago, or the nearest snapshot is far older than the window).
     */
    public synchronized Delta compute(IntelPlayer cur, long windowMs) {
        if (cur == null || cur.name == null) {
            return null;
        }

        List<Snapshot> list = data.get(cur.name.toLowerCase(Locale.ROOT));
        if (list == null || list.isEmpty()) {
            return null;
        }

        long now = System.currentTimeMillis();
        long cutoff = now - windowMs;

        // Newest snapshot at or before the window start = a full-window baseline.
        Snapshot base = null;
        for (Snapshot s : list) {
            if (s.t <= cutoff) {
                base = s;
            } else {
                break;
            }
        }

        boolean partial = false;
        if (base == null) {
            base = list.get(0); // oldest we have; covers only part of the window
            partial = true;
        }

        long age = now - base.t;

        if (partial && age < MIN_BASELINE_MS) {
            return null;
        }
        if (age > windowMs + windowMs / 2) {
            return null; // nearest snapshot is much older than the window; would mislabel the period
        }

        Delta delta = new Delta();
        delta.baselineAgeMs = age;
        delta.partial = partial;
        delta.finalKills = Math.max(0, cur.finalKills - base.fk);
        delta.finalDeaths = Math.max(0, cur.finalDeaths - base.fd);
        delta.kills = Math.max(0, cur.kills - base.k);
        delta.deaths = Math.max(0, cur.deaths - base.d);
        delta.wins = Math.max(0, cur.wins - base.w);
        delta.losses = Math.max(0, cur.losses - base.l);
        delta.bedsBroken = Math.max(0, cur.bedsBroken - base.bb);
        delta.bedsLost = Math.max(0, cur.bedsLost - base.bl);
        return delta;
    }

    /** Keeps every snapshot from the last 48h, but only one per 6h before that. */
    private static void thin(List<Snapshot> list, long now) {
        long recentCutoff = now - 48L * HOUR;
        List<Snapshot> out = new ArrayList<>();
        Snapshot lastKept = null;

        for (Snapshot s : list) {
            if (s.t >= recentCutoff || lastKept == null || s.t - lastKept.t >= 6L * HOUR) {
                out.add(s);
                lastKept = s;
            }
        }

        list.clear();
        list.addAll(out);
    }

    public synchronized void load() {
        if (!FILE.exists()) {
            return;
        }

        try (Reader reader = new InputStreamReader(new FileInputStream(FILE), StandardCharsets.UTF_8)) {
            Map<String, List<Snapshot>> loaded = gson.fromJson(reader,
                    new TypeToken<Map<String, List<Snapshot>>>() { }.getType());

            if (loaded == null) {
                return;
            }

            data.clear();
            long cutoff = System.currentTimeMillis() - KEEP_MS;

            for (Map.Entry<String, List<Snapshot>> entry : loaded.entrySet()) {
                List<Snapshot> list = entry.getValue();
                if (list == null || list.isEmpty()) {
                    continue;
                }
                if (list.get(list.size() - 1).t < cutoff) {
                    continue; // not seen in over 35 days
                }
                data.put(entry.getKey(), new ArrayList<>(list));
            }
        } catch (Exception e) {
            System.err.println("[StatSnapshotManager] Failed to load snapshots: " + e.getMessage());
        }
    }

    public synchronized void save() {
        try {
            FILE.getParentFile().mkdirs();

            // Drop players with no recent snapshots before writing.
            long cutoff = System.currentTimeMillis() - KEEP_MS;
            Iterator<Map.Entry<String, List<Snapshot>>> it = data.entrySet().iterator();
            while (it.hasNext()) {
                List<Snapshot> list = it.next().getValue();
                if (list.isEmpty() || list.get(list.size() - 1).t < cutoff) {
                    it.remove();
                }
            }

            try (Writer writer = new OutputStreamWriter(new FileOutputStream(FILE), StandardCharsets.UTF_8)) {
                gson.toJson(data, writer);
            }

            dirty = false;
            lastSave = System.currentTimeMillis();
        } catch (Exception e) {
            System.err.println("[StatSnapshotManager] Failed to save snapshots: " + e.getMessage());
        }
    }

    /** Saves only if something changed; used by the shutdown hook. */
    public synchronized void saveIfDirty() {
        if (dirty) {
            save();
        }
    }
}
