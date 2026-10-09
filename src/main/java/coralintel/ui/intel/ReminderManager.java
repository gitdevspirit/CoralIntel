package coralintel.ui.intel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Personal reminders: quick notes about players who might be cheating, so you
 * can look them up and tag them in Coral later. Saved to
 * ./config/CoralIntel/reminders.json (same idea as the blacklist). Keyed by
 * lowercase name; every entry keeps its notes, how many separate times you've
 * run into the player, and when you last did.
 */
public class ReminderManager {
    private static ReminderManager instance;
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    /** Meeting the same player again within this long counts as the same encounter. */
    private static final long ENCOUNTER_GAP_MS = 5L * 60L * 1000L;
    public static final int MAX_NOTE_LENGTH = 200;
    private static final int MAX_NOTES = 20;

    public static class Note {
        public String text;
        public long at;

        public Note() {
        }

        Note(String text, long at) {
            this.text = text;
            this.at = at;
        }
    }

    public static class Reminder {
        public String name;
        public List<Note> notes = new ArrayList<>();
        public long created;
        public long lastSeen;
        public int seen;

        public String latestNote() {
            return notes == null || notes.isEmpty() ? "" : notes.get(notes.size() - 1).text;
        }
    }

    private static class Store {
        Map<String, Reminder> entries = new LinkedHashMap<>();
    }

    private final File file = new File("./config/CoralIntel/reminders.json");
    private final Map<String, Reminder> entries = new LinkedHashMap<>();
    // Reminded players you've been in a game with (met in the lobby, or noted during it).
    // Not saved: it only feeds the end-of-game "tag them" alert (see ReminderAlerts).
    private final Set<String> inThisGame = new LinkedHashSet<>();

    private ReminderManager() {
        load();
    }

    public static synchronized ReminderManager getInstance() {
        if (instance == null) {
            instance = new ReminderManager();
        }
        return instance;
    }

    public synchronized boolean has(String name) {
        return entries.containsKey(name.toLowerCase(Locale.ROOT));
    }

    public synchronized Reminder get(String name) {
        return entries.get(name.toLowerCase(Locale.ROOT));
    }

    /** Adds a note (note may be empty). Creates the reminder on first use. */
    public synchronized Reminder add(String name, String note) {
        String key = name.toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();

        Reminder r = entries.get(key);
        if (r == null) {
            r = new Reminder();
            r.name = name;
            r.created = now;
            r.seen = 1;
            r.lastSeen = now;
            entries.put(key, r);
        } else {
            touch(r, now);
        }

        if (note != null && !note.trim().isEmpty()) {
            String text = note.trim();
            if (text.length() > MAX_NOTE_LENGTH) text = text.substring(0, MAX_NOTE_LENGTH);
            r.notes.add(new Note(text, now));
            while (r.notes.size() > MAX_NOTES) r.notes.remove(0);
        }

        inThisGame.add(key);

        save();
        return r;
    }

    /**
     * Called when a player shows up in your lobby. Returns their reminder when it
     * is a new encounter (so you get alerted once, not on every roster refresh),
     * or null when they aren't on the list or you only just saw them.
     */
    public synchronized Reminder recordEncounter(String name) {
        Reminder r = entries.get(name.toLowerCase(Locale.ROOT));
        if (r == null) return null;

        long now = System.currentTimeMillis();
        if (now - r.lastSeen < ENCOUNTER_GAP_MS) return null;

        touch(r, now);
        save();
        return r;
    }

    /** A reminded player is in your current game. No-op for anyone not on the list. */
    public synchronized void markInGame(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        if (entries.containsKey(key)) inThisGame.add(key);
    }

    /** The reminded players from the game that just ended (and forgets them). */
    public synchronized List<Reminder> takeInGame() {
        List<Reminder> out = new ArrayList<>();
        for (String key : inThisGame) {
            Reminder r = entries.get(key);
            if (r != null) out.add(r);
        }
        inThisGame.clear();
        return out;
    }

    public synchronized void clearInGame() {
        inThisGame.clear();
    }

    private static void touch(Reminder r, long now) {
        if (now - r.lastSeen >= ENCOUNTER_GAP_MS) r.seen++;
        r.lastSeen = now;
    }

    public synchronized boolean remove(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        inThisGame.remove(key);
        boolean removed = entries.remove(key) != null;
        if (removed) save();
        return removed;
    }

    public synchronized int clear() {
        int n = entries.size();
        entries.clear();
        inThisGame.clear();
        save();
        return n;
    }

    /** Most recently seen first. */
    public synchronized List<Reminder> getAll() {
        List<Reminder> all = new ArrayList<>(entries.values());
        all.sort((a, b) -> Long.compare(b.lastSeen, a.lastSeen));
        return all;
    }

    public synchronized void load() {
        if (!file.exists()) return;

        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            Store store = gson.fromJson(reader, Store.class);
            entries.clear();
            if (store != null && store.entries != null) {
                for (Map.Entry<String, Reminder> e : store.entries.entrySet()) {
                    Reminder r = e.getValue();
                    if (r == null || r.name == null) continue;
                    if (r.notes == null) r.notes = new ArrayList<>();
                    entries.put(e.getKey().toLowerCase(Locale.ROOT), r);
                }
            }
        } catch (Exception e) {
            System.err.println("[ReminderManager] Failed to load reminders: " + e.getMessage());
        }
    }

    public synchronized void save() {
        try {
            file.getParentFile().mkdirs();

            Store store = new Store();
            store.entries = entries;

            try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                gson.toJson(store, writer);
            }
        } catch (Exception e) {
            System.err.println("[ReminderManager] Failed to save reminders: " + e.getMessage());
        }
    }
}
