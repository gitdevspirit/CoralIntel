package coralintel.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Every session's summary, kept on disk in ./config/CoralIntel/session-history.json.
 *
 * A session is one run from launch (or the last .reset) to the next .reset / game
 * close. Each session has an id (the time it started); saving the same id again
 * overwrites its entry, so a session is updated after every game and never listed
 * twice. Only the newest MAX_SESSIONS are kept.
 */
public final class SessionHistory {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<ArrayList<Record>>() { }.getType();
    private static final File FILE = new File("./config/CoralIntel/session-history.json");
    public static final int MAX_SESSIONS = 500;

    private SessionHistory() {
    }

    public static class Record {
        public long id;          // when the session started (ms)
        public long endedAt;     // last time it was saved (ms)
        public long durationMs;  // total session time
        public long activeMs;    // time spent inside games
        public long avgGameMs;   // average finished game length, -1 if none
        public int games, wins, losses;
        public int kills, deaths, finalKills, finalDeaths, bedsBroken, bedsLost;
        public double stars, fkdr, bblr, wlr;
    }

    /** Adds the record, or replaces the saved one with the same id. */
    public static synchronized void upsert(Record record) {
        List<Record> all = load();
        boolean replaced = false;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id == record.id) {
                all.set(i, record);
                replaced = true;
                break;
            }
        }
        if (!replaced) all.add(record);

        while (all.size() > MAX_SESSIONS) all.remove(0);
        write(all);
    }

    /** All saved sessions, oldest first. Never null. */
    public static synchronized List<Record> load() {
        if (!FILE.exists()) return new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(FILE.toPath(), StandardCharsets.UTF_8)) {
            List<Record> all = GSON.fromJson(reader, LIST_TYPE);
            return all == null ? new ArrayList<>() : all;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static synchronized void clear() {
        write(new ArrayList<>());
    }

    private static void write(List<Record> all) {
        try {
            File dir = FILE.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            Files.write(FILE.toPath(), GSON.toJson(all, LIST_TYPE).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // History is best-effort; never break the game over it.
        }
    }
}
