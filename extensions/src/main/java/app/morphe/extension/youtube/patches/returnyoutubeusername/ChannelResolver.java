package app.morphe.extension.youtube.patches.returnyoutubeusername;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

public final class ChannelResolver {

    private static final String PREF_NAME = "morphe_ryu_channel_cache";
    private static final int NETWORK_TIMEOUT_MS = 3500;
    private static final int BLOCKING_TIMEOUT_MS = 600;

    private static final Pattern OG_TITLE_PATTERN =
            Pattern.compile("<meta\\s+property=\"og:title\"\\s+content=\"([^\"]+)\"");

    private static final Pattern CHANNEL_TITLE_XML_PATTERN =
            Pattern.compile("<title>([^<]+)</title>");

    // In-memory LRU / concurrent cache: handle (lowercase) -> real channel name
    private static final Map<String, String> cache = new ConcurrentHashMap<>();

    // Map of active in-flight requests to avoid duplicate fetches
    private static final Map<String, Future<String>> inFlight = new ConcurrentHashMap<>();

    private static volatile SharedPreferences preferences;

    private static SharedPreferences getPreferences() {
        if (preferences == null && Utils.isContextSet()) {
            try {
                Context context = Utils.getContext();
                if (context != null) {
                    preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                }
            } catch (Exception ex) {
                Logger.printException(() -> "Failed to get SharedPreferences for ChannelResolver", ex);
            }
        }
        return preferences;
    }

    /**
     * Resolves a handle into the formatted display name.
     * If cached, returns immediately.
     * If not cached, initiates async resolution and waits briefly (up to BLOCKING_TIMEOUT_MS).
     * If timed out or unresolved, returns the original handle.
     */
    public static String getOrResolve(String handle, String apiKey, int format) {
        if (handle == null || handle.isEmpty()) return handle;

        String cleanHandle = handle.trim();
        String normalizedKey = cleanHandle.toLowerCase(Locale.ENGLISH);

        // 1. Check in-memory cache
        String cachedName = cache.get(normalizedKey);
        if (cachedName != null) {
            return formatName(cleanHandle, cachedName, format);
        }

        // 2. Check disk / SharedPreferences cache
        SharedPreferences sp = getPreferences();
        if (sp != null) {
            String diskCached = sp.getString(normalizedKey, null);
            if (diskCached != null && !diskCached.isEmpty()) {
                cache.put(normalizedKey, diskCached);
                return formatName(cleanHandle, diskCached, format);
            }
        }

        // 3. Initiate resolution
        Future<String> future = resolveAsync(cleanHandle, apiKey);
        try {
            // Wait with a small timeout on the background Litho layout thread
            String resolved = future.get(BLOCKING_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (resolved != null && !resolved.isEmpty()) {
                return formatName(cleanHandle, resolved, format);
            }
        } catch (Exception ignored) {
            // Timed out or in progress; will be picked up on next layout/scroll
        }

        return cleanHandle;
    }

    /**
     * Pre-fetches channel name for a handle in background without blocking.
     */
    public static void prefetch(String handle, String apiKey) {
        if (handle == null || handle.isEmpty()) return;
        String normalizedKey = handle.trim().toLowerCase(Locale.ENGLISH);
        if (!cache.containsKey(normalizedKey)) {
            resolveAsync(handle.trim(), apiKey);
        }
    }

    public static Future<String> resolveAsync(final String handle, final String apiKey) {
        final String normalizedKey = handle.toLowerCase(Locale.ENGLISH);

        return inFlight.computeIfAbsent(normalizedKey, key ->
            Utils.submitOnBackgroundThread(() -> {
                try {
                    String resolved = doFetch(handle, apiKey);
                    if (resolved != null && !resolved.trim().isEmpty()) {
                        resolved = unescapeHtml(resolved.trim());
                        cache.put(normalizedKey, resolved);

                        SharedPreferences sp = getPreferences();
                        if (sp != null) {
                            sp.edit().putString(normalizedKey, resolved).apply();
                        }
                        return resolved;
                    }
                } catch (Exception ex) {
                    Logger.printDebug(() -> "ChannelResolver fetch error for " + handle + ": " + ex.getMessage());
                } finally {
                    inFlight.remove(normalizedKey);
                }
                return null;
            })
        );
    }

    private static String doFetch(String handle, String apiKey) {
        // Strategy 1: If YouTube Data API v3 key is provided, use official API
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            try {
                String apiResult = fetchViaDataApi(handle, apiKey.trim());
                if (apiResult != null && !apiResult.isEmpty()) {
                    return apiResult;
                }
            } catch (Exception e) {
                Logger.printDebug(() -> "Data API v3 failed for " + handle + ", falling back to public page");
            }
        }

        // Strategy 2: Zero-config public page meta resolver
        return fetchViaPublicPage(handle);
    }

    private static String fetchViaDataApi(String handle, String apiKey) throws Exception {
        String clean = handle.startsWith("@") ? handle.substring(1) : handle;
        String urlStr = "https://www.googleapis.com/youtube/v3/channels?part=snippet,brandingSettings&forHandle="
                + clean + "&key=" + apiKey;

        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(NETWORK_TIMEOUT_MS);
        conn.setReadTimeout(NETWORK_TIMEOUT_MS);

        if (conn.getResponseCode() == 200) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();

            JSONObject json = new JSONObject(sb.toString());
            JSONArray items = json.optJSONArray("items");
            if (items != null && items.length() > 0) {
                JSONObject first = items.getJSONObject(0);
                JSONObject snippet = first.optJSONObject("snippet");
                if (snippet != null && snippet.has("title")) {
                    return snippet.getString("title");
                }
                JSONObject branding = first.optJSONObject("brandingSettings");
                if (branding != null) {
                    JSONObject channel = branding.optJSONObject("channel");
                    if (channel != null && channel.has("title")) {
                        return channel.getString("title");
                    }
                }
            }
        }
        return null;
    }

    private static String fetchViaPublicPage(String handle) {
        String target = handle.startsWith("@") ? handle : "@" + handle;
        try {
            URL url = new URL("https://www.youtube.com/" + target);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept-Language", "zh-TW,zh;q=0.9,en-US;q=0.8,en;q=0.7");
            conn.setConnectTimeout(NETWORK_TIMEOUT_MS);
            conn.setReadTimeout(NETWORK_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);

            int code = conn.getResponseCode();
            if (code == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    Matcher m = OG_TITLE_PATTERN.matcher(line);
                    if (m.find()) {
                        String name = m.group(1);
                        reader.close();
                        return name;
                    }
                }
                reader.close();
            }
        } catch (Exception e) {
            Logger.printDebug(() -> "fetchViaPublicPage failed for " + handle + ": " + e.getMessage());
        }
        return null;
    }

    private static String formatName(String handle, String channelName, int format) {
        if (channelName == null || channelName.isEmpty()) return handle;
        switch (format) {
            case DisplayFormat.USERNAME_HANDLE:
                return channelName + " (" + handle + ")";
            case DisplayFormat.HANDLE_USERNAME:
                return handle + " (" + channelName + ")";
            case DisplayFormat.USERNAME_ONLY:
            default:
                return channelName;
        }
    }

    private static String unescapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&amp;", "&")
                   .replace("&quot;", "\"")
                   .replace("&#39;", "'")
                   .replace("&apos;", "'")
                   .replace("&lt;", "<")
                   .replace("&gt;", ">");
    }
}
