package coralintel.ui.intel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Direct Coral (Urchin) API calls for the .view and .tadd commands.
 *
 * LOOKUP is the same Cubelify endpoint the lobby scan already uses, so what
 * .view prints matches what the HUD/tab show.
 *
 * ADD is the "Add player tag (Admin only)" endpoint from the Coral API
 * reference. The request format below (JSON body with uuid/type/reason, key
 * sent as X-API-Key header and ?key=) is a best guess from that page's tag
 * schema — if Coral expects something different, only ADD_TAG_URL and
 * addTag() need to change. A key without admin access gets 401/403.
 */
public final class CoralApi {

    private static final String LOOKUP_URL = "https://api.urchin.gg/v3/cubelify";
    private static final String ADD_TAG_URL = "https://urchin.ws/admin/add-tag";
    private static final String USER_AGENT = "Spirit-Client/1.0";
    private static final int TIMEOUT_MS = 8000;

    private CoralApi() {
    }

    public static class Tag {
        public final String icon;
        public final String text;
        public final String reason;

        Tag(String icon, String text, String reason) {
            this.icon = icon;
            this.text = text;
            this.reason = reason;
        }
    }

    public static class LookupResult {
        public final List<Tag> tags;
        public final String error;

        LookupResult(List<Tag> tags, String error) {
            this.tags = tags;
            this.error = error;
        }

        public boolean isSuccess() {
            return error == null;
        }
    }

    public static class AddResult {
        public final boolean ok;
        public final int code;
        public final String message;

        AddResult(boolean ok, int code, String message) {
            this.ok = ok;
            this.code = code;
            this.message = message;
        }
    }

    /** Fetches every Coral tag on a player (blocking — call off the main thread). */
    public static LookupResult lookup(String uuid, String name, String key) {
        try {
            String url = LOOKUP_URL
                    + "?uuid=" + URLEncoder.encode(uuid, "UTF-8")
                    + "&name=" + URLEncoder.encode(name, "UTF-8")
                    + "&sources=MANUAL"
                    + "&key=" + URLEncoder.encode(key, "UTF-8");

            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept", "application/json");

            int code = connection.getResponseCode();

            if (code == 401 || code == 403) {
                return new LookupResult(null, "Coral rejected your API key (HTTP " + code + ").");
            }

            if (code == 429) {
                return new LookupResult(null, "Coral rate limited the request — try again in a moment.");
            }

            if (code != 200) {
                return new LookupResult(null, "Coral returned HTTP " + code + ".");
            }

            JsonObject root = new JsonParser().parse(read(connection.getInputStream())).getAsJsonObject();
            List<Tag> tags = new ArrayList<>();

            if (root.has("tags") && root.get("tags").isJsonArray()) {
                JsonArray array = root.getAsJsonArray("tags");

                for (JsonElement element : array) {
                    if (!element.isJsonObject()) {
                        continue;
                    }

                    JsonObject tag = element.getAsJsonObject();
                    String icon = str(tag, "icon");
                    String reason = str(tag, "tooltip");
                    String text = str(tag, "text");

                    if (text.isEmpty()) {
                        text = reason.isEmpty() ? icon : reason;
                    }

                    tags.add(new Tag(icon, text, reason));
                }
            }

            return new LookupResult(tags, null);
        } catch (Exception exception) {
            return new LookupResult(null, "Could not reach Coral: " + exception.getMessage());
        }
    }

    /** Adds a tag to a player (blocking — call off the main thread). Admin-only on Coral's side. */
    public static AddResult addTag(String uuid, String type, String reason, String key) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("uuid", uuid.replace("-", ""));
            body.addProperty("type", type);
            body.addProperty("reason", reason);

            HttpURLConnection connection = (HttpURLConnection) new URL(
                    ADD_TAG_URL + "?key=" + URLEncoder.encode(key, "UTF-8")
            ).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("X-API-Key", key);

            OutputStream out = connection.getOutputStream();
            try {
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            } finally {
                out.close();
            }

            int code = connection.getResponseCode();
            InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String response = stream == null ? "" : read(stream);

            if (code >= 200 && code < 300) {
                return new AddResult(true, code, "");
            }

            if (code == 401 || code == 403) {
                return new AddResult(false, code,
                        "Coral rejected the request (HTTP " + code + "). Adding tags is admin-only — "
                                + "your key may not have that access.");
            }

            String detail = response.length() > 120 ? response.substring(0, 120) : response;
            return new AddResult(false, code, "Coral returned HTTP " + code
                    + (detail.isEmpty() ? "." : ": " + detail));
        } catch (Exception exception) {
            return new AddResult(false, -1, "Could not reach Coral: " + exception.getMessage());
        }
    }

    private static String str(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && !element.isJsonNull() ? element.getAsString() : "";
    }

    private static String read(InputStream stream) throws java.io.IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();

        try {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        } finally {
            reader.close();
        }

        return sb.toString();
    }
}
