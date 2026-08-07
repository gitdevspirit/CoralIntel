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
 * Persisted personal safelist — .safelist / .unsafelist. Saved to
 * ./config/CoralIntel/safelist.json so it survives game restarts, same
 * pattern as BlacklistManager. Safelisted players are excluded from Coral's
 * cheater tag/threat elevation and cheater-flag notifications — you've
 * personally vouched for them. Blacklist still overrides this if a player
 * somehow ends up on both.
 */
public class SafelistManager {
    private static SafelistManager instance;
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final File safelistFile;
    private final Map<String, String> entries; // lowercase name -> reason

    private SafelistManager() {
        this.safelistFile = new File("./config/CoralIntel/safelist.json");
        this.entries = new LinkedHashMap<>();
        load();
    }

    public static SafelistManager getInstance() {
        if (instance == null) {
            instance = new SafelistManager();
        }
        return instance;
    }

    public boolean isSafelisted(String name) {
        return entries.containsKey(name.toLowerCase());
    }

    /** Null if the player isn't safelisted. */
    public String getReason(String name) {
        return entries.get(name.toLowerCase());
    }

    public void safelist(String name, String reason) {
        entries.put(name.toLowerCase(), reason == null || reason.isEmpty() ? "No reason given" : reason);
        save();
    }

    public boolean unsafelist(String name) {
        boolean removed = entries.remove(name.toLowerCase()) != null;
        if (removed) save();
        return removed;
    }

    public Map<String, String> getAll() {
        return new LinkedHashMap<>(entries);
    }

    @SuppressWarnings("unchecked")
    public void load() {
        if (!safelistFile.exists()) {
            return;
        }

        try (Reader reader = new InputStreamReader(new FileInputStream(safelistFile), StandardCharsets.UTF_8)) {
            Map<String, Object> data = gson.fromJson(reader, new TypeToken<Map<String, Object>>(){}.getType());
            if (data == null || !data.containsKey("entries")) {
                return;
            }

            Map<String, String> loaded = (Map<String, String>) data.get("entries");
            entries.clear();
            entries.putAll(loaded);
        } catch (Exception e) {
            System.err.println("[SafelistManager] Failed to load safelist: " + e.getMessage());
        }
    }

    public void save() {
        try {
            safelistFile.getParentFile().mkdirs();

            Map<String, Object> data = new HashMap<>();
            data.put("entries", entries);

            try (Writer writer = new OutputStreamWriter(new FileOutputStream(safelistFile), StandardCharsets.UTF_8)) {
                gson.toJson(data, writer);
            }
        } catch (Exception e) {
            System.err.println("[SafelistManager] Failed to save safelist: " + e.getMessage());
        }
    }
}
