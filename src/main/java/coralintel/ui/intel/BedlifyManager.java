package coralintel.ui.intel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import coralintel.CoralIntel;
import coralintel.module.modules.LobbyIntel;
import coralintel.util.ChatUtil;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Denicking through the Bedlify API (https://api.bedlify.xyz/docs): GET /nick?nick=<nick> returns
 * the games a nick was seen in with the real player behind it, newest first.
 *
 * The API key is personal (Bedlify's rules: never share or publish it), so it is NEVER in the
 * source or the jar. Each user saves their own with ".bedlify key <key>"; it is kept in
 * ./config/CoralIntel/bedlify.json on their machine, sent in the X-API-Key header (never in the
 * URL), and never printed in full or logged.
 *
 * A nick can be reused by different players over time, so the newest match is a best guess, not a
 * certainty. Results keep how many games each name was seen in and when, so you can judge.
 */
public class BedlifyManager {
    private static BedlifyManager instance;

    private static final String BASE_URL = "https://api.bedlify.xyz";
    private static final long CACHE_MS = 10L * 60L * 1000L;          // a found name
    private static final long NEGATIVE_CACHE_MS = 2L * 60L * 1000L;  // "no known real name"
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static final String ERR_AUTH = "auth";
    public static final String ERR_RATE = "rate";
    public static final String ERR_NET = "net";
    public static final String ERR_OTHER = "other";

    /** One possible real player behind a nick. */
    public static class Candidate {
        public final String name;
        public final String uuid;
        public final long lastSeen; // unix seconds
        public int games;

        Candidate(String name, String uuid, long lastSeen) {
            this.name = name;
            this.uuid = uuid;
            this.lastSeen = lastSeen;
            this.games = 1;
        }
    }

    public static class Result {
        public final List<Candidate> candidates; // newest first, one entry per real name
        public final String error;               // null when the lookup worked
        public final String errorKind;

        Result(List<Candidate> candidates, String error, String errorKind) {
            this.candidates = candidates;
            this.error = error;
            this.errorKind = errorKind;
        }

        static Result ok(List<Candidate> c) {
            return new Result(c, null, null);
        }

        static Result fail(String kind, String message) {
            return new Result(new ArrayList<>(), message, kind);
        }
    }

    private static class Stored {
        String key = "";
    }

    private static class Cached {
        final Result result;
        final long at;

        Cached(Result result, long at) {
            this.result = result;
            this.at = at;
        }
    }

    private final File file = new File("./config/CoralIntel/bedlify.json");
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "CoralIntel-Bedlify");
        t.setDaemon(true);
        return t;
    });

    private volatile String key = "";
    private volatile long blockedUntil = 0L;
    private volatile int rateRemaining = -1;
    private final Map<String, Boolean> reported = new ConcurrentHashMap<>();

    private BedlifyManager() {
        load();
    }

    public static synchronized BedlifyManager getInstance() {
        if (instance == null) {
            instance = new BedlifyManager();
        }
        return instance;
    }

    // ── key handling ─────────────────────────────────────────────────────

    public boolean hasKey() {
        return !key.isEmpty();
    }

    /** First 8 and last 3 characters only, so the key is never shown in full. */
    public String maskedKey() {
        String k = key;
        if (k.length() <= 12) return k.isEmpty() ? "" : "****";
        return k.substring(0, 8) + "..." + k.substring(k.length() - 3);
    }

    public static boolean looksLikeKey(String s) {
        return s != null && s.matches("[A-Za-z0-9_\\-]{10,128}");
    }

    public void setKey(String newKey) {
        key = newKey.trim();
        cache.clear();
        reported.clear();
        blockedUntil = 0L;
        save();
    }

    public void clearKey() {
        key = "";
        cache.clear();
        save();
    }

    /** Requests left in the current window as of the last response, or -1 if unknown. */
    public int rateRemaining() {
        return rateRemaining;
    }

    private void load() {
        if (!file.exists()) return;
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            Stored stored = gson.fromJson(reader, Stored.class);
            if (stored != null && stored.key != null && looksLikeKey(stored.key.trim())) {
                key = stored.key.trim();
            }
        } catch (Exception e) {
            System.err.println("[Bedlify] Failed to read bedlify.json");
        }
    }

    private void save() {
        try {
            file.getParentFile().mkdirs();
            Stored stored = new Stored();
            stored.key = key;
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                gson.toJson(stored, writer);
            }
            // Best effort: only the owner should read it (no effect on Windows).
            file.setReadable(false, false);
            file.setReadable(true, true);
            file.setWritable(false, false);
            file.setWritable(true, true);
        } catch (Exception e) {
            System.err.println("[Bedlify] Failed to save bedlify.json");
        }
    }

    // ── lookups ──────────────────────────────────────────────────────────

    /** Blocking lookup; call it off the game thread. Results are cached for a few minutes. */
    public Result lookup(String nick) {
        if (!hasKey()) {
            return Result.fail(ERR_AUTH, "No Bedlify key set. Use .bedlify key <key>.");
        }

        String cacheKey = nick.toLowerCase(Locale.ROOT);
        Cached cached = cache.get(cacheKey);
        if (cached != null) {
            long ttl = cached.result.candidates.isEmpty() ? NEGATIVE_CACHE_MS : CACHE_MS;
            if (System.currentTimeMillis() - cached.at < ttl) return cached.result;
        }

        long wait = blockedUntil - System.currentTimeMillis();
        if (wait > 0) {
            return Result.fail(ERR_RATE, "Bedlify rate limit reached, try again in " + ((wait + 999) / 1000) + "s.");
        }

        Result result = fetch(nick);
        if (result.error == null) {
            cache.put(cacheKey, new Cached(result, System.currentTimeMillis()));
        }
        return result;
    }

    /** Runs a lookup in the background and hands the result to {@code callback} on the game thread. */
    public void lookupAsync(final String nick, final java.util.function.Consumer<Result> callback) {
        pool.submit(() -> {
            final Result result = lookup(nick);
            Minecraft.getMinecraft().addScheduledTask(() -> callback.accept(result));
        });
    }

    private Result fetch(String nick) {
        HttpURLConnection c = null;
        try {
            URL url = new URL(BASE_URL + "/nick?nick=" + URLEncoder.encode(nick, "UTF-8") + "&limit=10");
            c = (HttpURLConnection) url.openConnection();
            c.setRequestMethod("GET");
            c.setConnectTimeout(5000);
            c.setReadTimeout(15000);
            c.setRequestProperty("User-Agent", "CoralIntel/1.0");
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("X-API-Key", key);

            int code = c.getResponseCode();

            String remaining = c.getHeaderField("X-RateLimit-Remaining");
            if (remaining != null) {
                try {
                    rateRemaining = Integer.parseInt(remaining.trim());
                } catch (NumberFormatException ignored) {
                }
            }

            if (code == 401) {
                return Result.fail(ERR_AUTH, "Bedlify rejected your API key. Set a new one with .bedlify key <key>.");
            }
            if (code == 429) {
                long retry = 30L;
                try {
                    String header = c.getHeaderField("Retry-After");
                    if (header != null) retry = Math.max(1L, Long.parseLong(header.trim()));
                } catch (NumberFormatException ignored) {
                }
                blockedUntil = System.currentTimeMillis() + retry * 1000L;
                return Result.fail(ERR_RATE, "Bedlify rate limit reached, try again in " + retry + "s.");
            }
            if (code == 400) {
                return Result.fail(ERR_OTHER, "Bedlify didn't accept that nick.");
            }
            if (code != 200) {
                return Result.fail(ERR_OTHER, "Bedlify returned HTTP " + code + ".");
            }

            try (Reader reader = new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8)) {
                return Result.ok(parse(new JsonParser().parse(reader)));
            }
        } catch (java.io.IOException e) {
            return Result.fail(ERR_NET, "Couldn't reach Bedlify.");
        } catch (RuntimeException e) {
            return Result.fail(ERR_OTHER, "Bedlify sent a response CoralIntel couldn't read.");
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** Collapses the newest-first event list into one candidate per real name. */
    private static List<Candidate> parse(JsonElement root) {
        Map<String, Candidate> byName = new LinkedHashMap<>();

        if (root != null && root.isJsonObject()) {
            JsonObject obj = root.getAsJsonObject();
            JsonElement results = obj.get("results");

            if (results != null && results.isJsonArray()) {
                JsonArray array = results.getAsJsonArray();
                for (JsonElement el : array) {
                    if (!el.isJsonObject()) continue;
                    JsonObject o = el.getAsJsonObject();

                    String name = string(o, "name");
                    if (name == null) continue; // unresolved nick: no real name yet

                    long ts = 0L;
                    JsonElement t = o.get("timestamp");
                    if (t != null && !t.isJsonNull()) {
                        try {
                            ts = t.getAsLong();
                        } catch (RuntimeException ignored) {
                        }
                    }

                    String lower = name.toLowerCase(Locale.ROOT);
                    Candidate existing = byName.get(lower);
                    if (existing == null) {
                        byName.put(lower, new Candidate(name, string(o, "uuid"), ts));
                    } else {
                        existing.games++;
                    }
                }
            }
        }

        return new ArrayList<>(byName.values());
    }

    private static String string(JsonObject o, String field) {
        JsonElement e = o.get(field);
        if (e == null || e.isJsonNull()) return null;
        String s = e.getAsString();
        return s == null || s.isEmpty() ? null : s;
    }

    // ── lobby integration ────────────────────────────────────────────────

    /**
     * Looks up the real name behind a nicked lobby player in the background, stores it on the
     * player and (if enabled) tells you in chat. Safe to call from any thread.
     */
    public void denickPlayer(final IntelPlayer p, final Runnable refresh) {
        if (p == null || p.name == null || p.denickRequested) return;

        if (!hasKey()) {
            reportOnce("nokey", "&5[NICK] &7Set your Bedlify key to see who's behind nicks: &f.bedlify key <key>");
            return;
        }

        p.denickRequested = true;
        final String nick = p.name;

        pool.submit(() -> {
            final Result result = lookup(nick);

            Minecraft.getMinecraft().addScheduledTask(() -> {
                if (result.error != null) {
                    p.denickRequested = false; // allow a retry (.denick) once the problem is fixed
                    reportOnce(result.errorKind, "&c[Bedlify] &7" + result.error);
                    return;
                }

                if (!result.candidates.isEmpty()) {
                    Candidate best = result.candidates.get(0);
                    p.realName = best.name;
                    p.realNameSeen = best.lastSeen;
                }

                if (refresh != null) refresh.run();
                if (denickChatEnabled()) announce(nick, result);
            });
        });
    }

    private static boolean denickChatEnabled() {
        if (CoralIntel.moduleManager == null) return true;
        Object m = CoralIntel.moduleManager.getModule(LobbyIntel.class);
        return !(m instanceof LobbyIntel) || ((LobbyIntel) m).denickChat.getValue();
    }

    /** Prints the result of a lookup (used by the lobby alert and by .denick). */
    public static void announce(String nick, Result result) {
        if (result.candidates.isEmpty()) {
            ChatUtil.sendFormatted("&5[NICK] &f" + nick + " &7\u2014 no known real name yet.");
            return;
        }

        Candidate best = result.candidates.get(0);
        ChatUtil.sendFormatted("&5[NICK] &f" + nick + " &7is likely &e" + best.name
                + " &8(seen " + ago(best.lastSeen) + ", " + best.games + (best.games == 1 ? " game" : " games") + ")");

        if (result.candidates.size() > 1) {
            StringBuilder others = new StringBuilder();
            for (int i = 1; i < result.candidates.size() && i < 4; i++) {
                Candidate c = result.candidates.get(i);
                if (others.length() > 0) others.append("&8, ");
                others.append("&f").append(c.name).append(" &8(").append(ago(c.lastSeen)).append(")");
            }
            ChatUtil.sendFormatted("&7  Also used by: " + others);
        }

        ChatUtil.sendFormatted("&7  Check them with &f.bw " + best.name);
    }

    private void reportOnce(String kind, String message) {
        if (reported.putIfAbsent(kind, Boolean.TRUE) == null) {
            ChatUtil.sendFormatted(message);
        }
    }

    public static String ago(long unixSeconds) {
        if (unixSeconds <= 0) return "a while ago";
        long s = Math.max(0L, System.currentTimeMillis() / 1000L - unixSeconds);
        if (s < 60) return "just now";
        long m = s / 60;
        if (m < 60) return m + "m ago";
        long h = m / 60;
        if (h < 24) return h + "h ago";
        return (h / 24) + "d ago";
    }
}
