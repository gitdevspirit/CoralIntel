package coralintel.ui.intel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persisted personal blacklist — .blacklist / .unblacklist. Saved to
 * ./config/CoralIntel/blacklist.json so it survives game restarts, same
 * pattern as RoleManager. Keyed by lowercase player name -> reason text.
 */
public class BlacklistManager {
    private static BlacklistManager instance;
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final File blacklistFile;
    private final Map<String, String> entries; // lowercase name -> reason

    private BlacklistManager() {
        this.blacklistFile = new File("./config/CoralIntel/blacklist.json");
        this.entries = new LinkedHashMap<>();
        load();
    }

    public static BlacklistManager getInstance() {
        if (instance == null) {
            instance = new BlacklistManager();
        }
        return instance;
    }

    public boolean isBlacklisted(String name) {
        return entries.containsKey(name.toLowerCase());
    }

    /** Null if the player isn't blacklisted. */
    public String getReason(String name) {
        return entries.get(name.toLowerCase());
    }

    public void blacklist(String name, String reason) {
        entries.put(name.toLowerCase(), reason == null || reason.isEmpty() ? "No reason given" : reason);
        save();
    }

    public boolean unblacklist(String name) {
        boolean removed = entries.remove(name.toLowerCase()) != null;
        if (removed) save();
        return removed;
    }

    public Map<String, String> getAll() {
        return new LinkedHashMap<>(entries);
    }

    @SuppressWarnings("unchecked")
    public void load() {
        if (!blacklistFile.exists()) {
            return;
        }

        try (Reader reader = new InputStreamReader(new FileInputStream(blacklistFile), StandardCharsets.UTF_8)) {
            Map<String, Object> data = gson.fromJson(reader, new TypeToken<Map<String, Object>>(){}.getType());
            if (data == null || !data.containsKey("entries")) {
                return;
            }

            Map<String, String> loaded = (Map<String, String>) data.get("entries");
            entries.clear();
            entries.putAll(loaded);
        } catch (Exception e) {
            System.err.println("[BlacklistManager] Failed to load blacklist: " + e.getMessage());
        }
    }

    public void save() {
        try {
            blacklistFile.getParentFile().mkdirs();

            Map<String, Object> data = new HashMap<>();
            data.put("entries", entries);

            try (Writer writer = new OutputStreamWriter(new FileOutputStream(blacklistFile), StandardCharsets.UTF_8)) {
                gson.toJson(data, writer);
            }
        } catch (Exception e) {
            System.err.println("[BlacklistManager] Failed to save blacklist: " + e.getMessage());
        }
    }
}
