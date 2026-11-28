package app.morphe.extension.youtube.patches.returnyoutubeusername;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.BidiFormatter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

public final class ChannelResolver {

    private static final String PREF_NAME = "morphe_ryu_channel_cache";
    private static final int NETWORK_TIMEOUT_MS = 3500;
    private static final int BLOCKING_TIMEOUT_MS = 1200;
    private static final int LIVE_CHAT_TIMEOUT_MS = 600;
    private static final int FETCH_THREAD_COUNT = 6;
    private static final int STREAM_READ_BUFFER_SIZE = 8192;
    private static final int STREAM_WINDOW_OVERLAP = 2048;
    private static final int MAX_HTML_SCAN_CHARS = 500000;

    private static final int MEMORY_CACHE_MAX_SIZE = 500;
    private static final int DISK_CACHE_MAX_SIZE = 2000;
    private static final long CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000;
    private static final long FAILED_RETRY_COOLDOWN_MS = 30_000L;

    private static final String INNERTUBE_RESOLVE_URL =
            "https://youtubei.googleapis.com/youtubei/v1/navigation/resolve_url?prettyPrint=false&fields=endpoint.browseEndpoint.browseId";
    private static final String INNERTUBE_BROWSE_URL =
            "https://youtubei.googleapis.com/youtubei/v1/browse?prettyPrint=false&fields=metadata.channelMetadataRenderer.title";
    private static final String INNERTUBE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
    private static final String INNERTUBE_CLIENT_NAME = "MWEB";
    private static final String INNERTUBE_CLIENT_VERSION = "2.20250101.00.00";

    private static final Pattern OG_TITLE_PATTERN_1 =
            Pattern.compile("<meta\\s+[^>]*property=[\"']og:title[\"'][^>]*content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    private static final Pattern OG_TITLE_PATTERN_2 =
            Pattern.compile("<meta\\s+[^>]*content=[\"']([^\"']+)[\"'][^>]*property=[\"']og:title[\"']", Pattern.CASE_INSENSITIVE);

    private static final Pattern JSON_TITLE_PATTERN =
            Pattern.compile("\"channelMetadataRenderer\"\\s*:\\s*\\{[^}]{0,400}?\"title\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\]){1,200})\"");

    // Matches m.youtube.com hex-escaped ytInitialData: \x22channelMetadataRenderer\x22:\x7b\x22title\x22:\x22...\x22
    private static final Pattern ESCAPED_JSON_TITLE_PATTERN =
            Pattern.compile("\\\\x22channelMetadataRenderer\\\\x22\\s*:\\s*(?:\\{|\\\\x7b)(?:(?!\\\\x7d|\\}).){0,400}?\\\\x22title\\\\x22\\s*:\\s*\\\\x22((?:\\\\\\\\\\\\x22|(?!\\\\x22).){1,200})\\\\x22");

    private static final Pattern CHANNEL_TITLE_XML_PATTERN =
            Pattern.compile("<title>([^<]+)</title>", Pattern.CASE_INSENSITIVE);

    private static final class CacheEntry {
        final String name;
        final long timestampMs;

        CacheEntry(String name, long timestampMs) {
            this.name = name;
            this.timestampMs = timestampMs;
        }
    }

    private static final class DiskKeyTimestamp {
        final String key;
        final long timestampMs;

        DiskKeyTimestamp(String key, long timestampMs) {
            this.key = key;
            this.timestampMs = timestampMs;
        }
    }

    // Dedicated small daemon executor so concurrent channel fetches never starve Morphe's shared background pool
    private static final AtomicInteger threadCounter = new AtomicInteger(1);
    private static final ExecutorService FETCH_EXECUTOR = Executors.newFixedThreadPool(
            FETCH_THREAD_COUNT,
            r -> {
                Thread t = new Thread(r, "morphe-ryu-fetch-" + threadCounter.getAndIncrement());
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY - 1);
                return t;
            }
    );

    // Bounded in-memory LRU cache (max 500 entries): handle (lowercase) -> timestamped channel name ("" for 404)
    private static final Map<String, CacheEntry> memoryCache =
            Collections.synchronizedMap(new LinkedHashMap<String, CacheEntry>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                    return size() > MEMORY_CACHE_MAX_SIZE;
                }
            });

    // Map of active in-flight requests to avoid duplicate fetches
    private static final Map<String, Future<String>> inFlight = new ConcurrentHashMap<>();

    // Failed-request retry cooldown map: handle (lowercase) -> failure epoch millis
    private static final Map<String, Long> failedCooldowns = new ConcurrentHashMap<>();

    private static final AtomicBoolean prefErrorLogged = new AtomicBoolean(false);
    private static final AtomicBoolean diskPruneScheduled = new AtomicBoolean(false);
    private static volatile SharedPreferences preferences;

    // One-time availability check for Morphe v2.4+ LithoRelayoutPatch & RelayoutSpan
    private static final boolean LITHO_RELAYOUT_AVAILABLE = checkLithoRelayoutAvailable();

    private static boolean checkLithoRelayoutAvailable() {
        try {
            Class<?> patchClass = Class.forName("app.morphe.extension.shared.patches.LithoRelayoutPatch");
            Class.forName("app.morphe.extension.shared.patches.LithoRelayoutPatch$RelayoutSpan");
            patchClass.getMethod("relayoutOutdatedTexts");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isLithoRelayoutAvailable() {
        return LITHO_RELAYOUT_AVAILABLE;
    }

    public static boolean isPending(String handle) {
        try {
            if (handle == null || handle.isEmpty()) return false;
            return inFlight.containsKey(handle.trim().toLowerCase(Locale.ROOT));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static SharedPreferences getPreferences() {
        if (preferences == null) {
            try {
                if (Utils.isContextSet()) {
                    Context context = Utils.getContext();
                    if (context != null) {
                        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                        preferences = sp;
                        scheduleDiskPruneOnce(sp);
                    }
                }
            } catch (Throwable ex) {
                if (prefErrorLogged.compareAndSet(false, true)) {
                    Logger.printException(() -> "Failed to get SharedPreferences for ChannelResolver", ex);
                } else {
                    Logger.printDebug(() -> "Failed to get SharedPreferences for ChannelResolver: " + ex);
                }
            }
        }
        return preferences;
    }

    private static void scheduleDiskPruneOnce(final SharedPreferences sp) {
        if (sp != null && diskPruneScheduled.compareAndSet(false, true)) {
            try {
                FETCH_EXECUTOR.submit(() -> pruneDiskCache(sp));
            } catch (Throwable ignored) {
            }
        }
    }

    private static void pruneDiskCache(SharedPreferences sp) {
        try {
            Map<String, ?> all = sp.getAll();
            if (all == null || all.isEmpty()) return;

            long now = System.currentTimeMillis();
            List<String> keysToRemove = new ArrayList<>();
            List<DiskKeyTimestamp> validEntries = new ArrayList<>(all.size());

            for (Map.Entry<String, ?> entry : all.entrySet()) {
                String key = entry.getKey();
                Object val = entry.getValue();
                if (!(val instanceof String)) {
                    keysToRemove.add(key);
                    continue;
                }
                String raw = (String) val;
                long ts = parseDiskTimestamp(raw);
                if (ts > 0) {
                    if (now - ts > CACHE_TTL_MS) {
                        keysToRemove.add(key);
                    } else {
                        validEntries.add(new DiskKeyTimestamp(key, ts));
                    }
                } else {
                    // Legacy plain-name entry: keep valid, treat as older than timestamped entries if cap exceeded
                    validEntries.add(new DiskKeyTimestamp(key, 0L));
                }
            }

            if (validEntries.size() > DISK_CACHE_MAX_SIZE) {
                Collections.sort(validEntries, (a, b) -> Long.compare(a.timestampMs, b.timestampMs));
                int excess = validEntries.size() - DISK_CACHE_MAX_SIZE;
                for (int i = 0; i < excess; i++) {
                    keysToRemove.add(validEntries.get(i).key);
                }
            }

            if (!keysToRemove.isEmpty()) {
                SharedPreferences.Editor editor = sp.edit();
                for (String key : keysToRemove) {
                    editor.remove(key);
                }
                editor.apply();
            }
        } catch (Throwable ignored) {
        }
    }

    private static long parseDiskTimestamp(String raw) {
        int sep = raw.indexOf('|');
        if (sep <= 0) return -1L;
        try {
            return Long.parseLong(raw.substring(0, sep));
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }

    private static CacheEntry getMemoryEntry(String normalizedKey) {
        CacheEntry entry = memoryCache.get(normalizedKey);
        if (entry == null) return null;
        if (System.currentTimeMillis() - entry.timestampMs > CACHE_TTL_MS) {
            memoryCache.remove(normalizedKey);
            return null;
        }
        return entry;
    }

    private static CacheEntry readDiskEntry(SharedPreferences sp, String normalizedKey) {
        if (sp == null) return null;
        String raw = sp.getString(normalizedKey, null);
        if (raw == null) return null;

        long now = System.currentTimeMillis();
        int sep = raw.indexOf('|');
        if (sep > 0) {
            try {
                long ts = Long.parseLong(raw.substring(0, sep));
                if (now - ts > CACHE_TTL_MS) {
                    sp.edit().remove(normalizedKey).apply();
                    return null;
                }
                return new CacheEntry(raw.substring(sep + 1), ts);
            } catch (NumberFormatException ignored) {
                // Legacy plain channel name containing '|'
            }
        }
        // Backward-compatible legacy plain-name entry without timestamp prefix
        return new CacheEntry(raw, now);
    }

    private static boolean isInFailedCooldown(String normalizedKey) {
        Long failedAt = failedCooldowns.get(normalizedKey);
        if (failedAt == null) return false;
        if (System.currentTimeMillis() - failedAt < FAILED_RETRY_COOLDOWN_MS) {
            return true;
        }
        failedCooldowns.remove(normalizedKey);
        return false;
    }

    private static void recordFailureCooldown(String normalizedKey) {
        long now = System.currentTimeMillis();
        if (failedCooldowns.size() > 1000) {
            for (Map.Entry<String, Long> e : failedCooldowns.entrySet()) {
                if (now - e.getValue() >= FAILED_RETRY_COOLDOWN_MS) {
                    failedCooldowns.remove(e.getKey());
                }
            }
        }
        failedCooldowns.put(normalizedKey, now);
    }

    /**
     * Non-blocking (0ms) lookup for LithoRelayoutPatch hosts.
     * Checks bounded memory LRU cache (7-day TTL), then SharedPreferences disk cache (7-day TTL).
     * On cache miss, starts an async fetch (unless in 30s retry cooldown) and immediately returns null.
     * On cached definitive 404 (""), returns the original clean handle.
     */
    public static String getCachedOrStartFetch(String handle, String apiKey, int format) {
        try {
            if (handle == null || handle.isEmpty()) return handle;

            String cleanHandle = handle.trim();
            String normalizedKey = cleanHandle.toLowerCase(Locale.ROOT);

            // 1. Check in-memory LRU cache (respecting TTL)
            CacheEntry memEntry = getMemoryEntry(normalizedKey);
            if (memEntry != null) {
                if (memEntry.name.isEmpty()) return cleanHandle;
                return formatName(cleanHandle, memEntry.name, format);
            }

            // 2. Check disk / SharedPreferences cache (respecting TTL)
            SharedPreferences sp = getPreferences();
            CacheEntry diskEntry = readDiskEntry(sp, normalizedKey);
            if (diskEntry != null) {
                memoryCache.put(normalizedKey, diskEntry);
                if (diskEntry.name.isEmpty()) return cleanHandle;
                return formatName(cleanHandle, diskEntry.name, format);
            }

            // 3. Cache miss: trigger background fetch and return null immediately (0ms)
            resolveAsync(cleanHandle, apiKey);
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Resolves a handle into the formatted display name.
     * If cached, returns immediately.
     * If not cached, initiates async resolution and waits briefly (up to BLOCKING_TIMEOUT_MS).
     * If timed out or unresolved, returns the original handle.
     */
    public static String getOrResolve(String handle, String apiKey, int format) {
        try {
            if (handle == null || handle.isEmpty()) return handle;

            String cleanHandle = handle.trim();
            String normalizedKey = cleanHandle.toLowerCase(Locale.ROOT);

            // 1. Check in-memory LRU cache
            CacheEntry memEntry = getMemoryEntry(normalizedKey);
            if (memEntry != null) {
                if (memEntry.name.isEmpty()) return cleanHandle;
                return formatName(cleanHandle, memEntry.name, format);
            }

            // 2. Check disk / SharedPreferences cache
            SharedPreferences sp = getPreferences();
            CacheEntry diskEntry = readDiskEntry(sp, normalizedKey);
            if (diskEntry != null) {
                memoryCache.put(normalizedKey, diskEntry);
                if (diskEntry.name.isEmpty()) return cleanHandle;
                return formatName(cleanHandle, diskEntry.name, format);
            }

            // 3. Initiate resolution
            Future<String> future = resolveAsync(cleanHandle, apiKey);
            if (future != null) {
                try {
                    String resolved = future.get(BLOCKING_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    if (resolved != null && !resolved.isEmpty()) {
                        return formatName(cleanHandle, resolved, format);
                    }
                } catch (Throwable ignored) {
                    // Timed out or in progress; will be picked up on relayout/scroll
                }
            }

            return cleanHandle;
        } catch (Throwable ignored) {
            return handle;
        }
    }

    /**
     * Resolves a handle specifically for live chat.
     * If cached, returns the real channel name immediately.
     * If not yet cached, initiates resolution and waits up to LIVE_CHAT_TIMEOUT_MS (600ms)
     * so that the real channel name can be displayed on the initial render pass.
     * If timed out, falls back safely to the handle without '@'.
     */
    public static String getOrResolveLiveChat(String handle, String apiKey, int format) {
        try {
            if (handle == null || handle.isEmpty()) return handle;

            String cleanHandle = handle.trim();
            String normalizedKey = cleanHandle.toLowerCase(Locale.ROOT);

            // 1. Check in-memory LRU cache
            CacheEntry memEntry = getMemoryEntry(normalizedKey);
            if (memEntry != null) {
                if (memEntry.name.isEmpty()) {
                    return stripLeadingAt(cleanHandle);
                }
                return formatName(cleanHandle, memEntry.name, format);
            }

            // 2. Check disk / SharedPreferences cache
            SharedPreferences sp = getPreferences();
            CacheEntry diskEntry = readDiskEntry(sp, normalizedKey);
            if (diskEntry != null) {
                memoryCache.put(normalizedKey, diskEntry);
                if (diskEntry.name.isEmpty()) {
                    return stripLeadingAt(cleanHandle);
                }
                return formatName(cleanHandle, diskEntry.name, format);
            }

            // 3. Initiate resolution and wait with a bounded 600ms timeout
            Future<String> future = resolveAsync(cleanHandle, apiKey);
            if (future != null) {
                try {
                    String resolved = future.get(LIVE_CHAT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    if (resolved != null && !resolved.isEmpty()) {
                        return formatName(cleanHandle, resolved, format);
                    }
                } catch (Throwable ignored) {
                    // Timed out or fetch error: fallback to handle without '@'
                }
            }

            // Fallback: return handle without '@'
            return stripLeadingAt(cleanHandle);
        } catch (Throwable ignored) {
            return handle;
        }
    }

    private static String stripLeadingAt(String handle) {
        if (handle != null && handle.startsWith("@") && handle.length() > 1) {
            return handle.substring(1);
        }
        return handle;
    }

    /**
     * Pre-fetches channel name for a handle in background without blocking.
     * Checks both in-memory LRU cache and SharedPreferences disk cache (with 7-day TTL)
     * before submitting network requests.
     */
    public static void prefetch(String handle, String apiKey) {
        try {
            if (handle == null || handle.isEmpty()) return;
            String cleanHandle = handle.trim();
            String normalizedKey = cleanHandle.toLowerCase(Locale.ROOT);

            if (getMemoryEntry(normalizedKey) != null) {
                return;
            }

            SharedPreferences sp = getPreferences();
            CacheEntry diskEntry = readDiskEntry(sp, normalizedKey);
            if (diskEntry != null) {
                memoryCache.put(normalizedKey, diskEntry);
                return;
            }

            resolveAsync(cleanHandle, apiKey);
        } catch (Throwable ignored) {
        }
    }

    public static Future<String> resolveAsync(final String handle, final String apiKey) {
        try {
            final String normalizedKey = handle.toLowerCase(Locale.ROOT);

            // Respect 30s retry backoff on recently failed requests
            if (isInFailedCooldown(normalizedKey)) {
                return null;
            }

            return inFlight.computeIfAbsent(normalizedKey, key ->
                    FETCH_EXECUTOR.submit(() -> {
                        try {
                            String resolved = doFetch(handle, apiKey);
                            if (resolved != null) {
                                resolved = unescapeHtml(resolved.trim());
                                long now = System.currentTimeMillis();
                                failedCooldowns.remove(normalizedKey);
                                memoryCache.put(normalizedKey, new CacheEntry(resolved, now));

                                if (!resolved.isEmpty()) {
                                    SharedPreferences sp = getPreferences();
                                    if (sp != null) {
                                        sp.edit().putString(normalizedKey, now + "|" + resolved).apply();
                                    }
                                }
                                return resolved;
                            } else {
                                recordFailureCooldown(normalizedKey);
                            }
                        } catch (Throwable ex) {
                            recordFailureCooldown(normalizedKey);
                            Logger.printDebug(() -> "ChannelResolver fetch error for " + handle + ": " + ex.getMessage());
                        } finally {
                            // Remove from inFlight FIRST so PendingNameSpan.isOutdated() immediately sees !isPending()
                            inFlight.remove(normalizedKey);
                            triggerLithoRelayout();
                        }
                        return null;
                    })
            );
        } catch (Throwable ex) {
            Logger.printDebug(() -> "ChannelResolver resolveAsync error for " + handle + ": " + ex.getMessage());
            return null;
        }
    }

    /**
     * Triggers Morphe v2.4+ LithoRelayoutPatch.relayoutOutdatedTexts() via isolated PendingNameSpan
     * so views waiting on this handle relayout immediately once the fetch completes.
     */
    private static void triggerLithoRelayout() {
        if (!LITHO_RELAYOUT_AVAILABLE) {
            return;
        }
        try {
            PendingNameSpan.triggerRelayout();
        } catch (Throwable ignored) {
        }
    }

    private static String doFetch(String handle, String apiKey) {
        // Strategy 1: If YouTube Data API v3 key is provided, use official API
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            try {
                String apiResult = fetchViaDataApi(handle, apiKey.trim());
                if (apiResult != null && !apiResult.isEmpty()) {
                    return apiResult;
                }
            } catch (Throwable e) {
                Logger.printDebug(() -> "Data API v3 failed for " + handle + ", falling back to InnerTube");
            }
        }

        // Strategy 2: Primary zero-config backend — unauthenticated InnerTube MWEB API
        String innerTubeResult = fetchViaInnerTube(handle);
        if (innerTubeResult != null) {
            // Non-empty resolved title OR "" definitive 404 not-found
            return innerTubeResult;
        }

        // Strategy 3: Fallback on transient InnerTube failure — m.youtube.com public page scanner
        return fetchViaPublicPage(handle);
    }

    private static final Pattern INNERTUBE_BROWSE_ID_PATTERN =
            Pattern.compile("\"browseId\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])+)\"");

    private static final Pattern INNERTUBE_TITLE_PATTERN =
            Pattern.compile("\"title\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])+)\"");

    private static String fetchViaInnerTube(String handle) {
        try {
            String clean = handle.startsWith("@") ? handle.substring(1) : handle;
            String encodedClean = URLEncoder.encode(clean, "UTF-8").replace("+", "%20");
            String lang = Locale.getDefault().getLanguage();
            if (lang == null || lang.isEmpty()) {
                lang = "en";
            }

            // Step 1: resolve_url (@handle -> browseId)
            String resolveBody = buildInnerTubeRequestJson(lang, "url", "https://www.youtube.com/@" + encodedClean);

            InnerTubeResponse resolveResp = postInnerTubeJson(INNERTUBE_RESOLVE_URL, resolveBody);
            if (resolveResp == null) {
                return null;
            }
            if (resolveResp.statusCode == 404) {
                return "";
            }
            if (resolveResp.statusCode != 200 || resolveResp.body == null || resolveResp.body.isEmpty()) {
                return null;
            }

            String browseId = extractInnerTubeBrowseId(resolveResp.body);
            if (browseId == null || browseId.isEmpty()) {
                return null;
            }

            // Step 2: browse (browseId -> metadata.channelMetadataRenderer.title)
            String browseBody = buildInnerTubeRequestJson(lang, "browseId", browseId);

            InnerTubeResponse browseResp = postInnerTubeJson(INNERTUBE_BROWSE_URL, browseBody);
            if (browseResp == null) {
                return null;
            }
            if (browseResp.statusCode == 404) {
                return "";
            }
            if (browseResp.statusCode != 200 || browseResp.body == null || browseResp.body.isEmpty()) {
                return null;
            }

            String title = extractInnerTubeTitle(browseResp.body);
            return (title == null || title.isEmpty()) ? null : title;
        } catch (Throwable e) {
            Logger.printDebug(() -> "fetchViaInnerTube failed for " + handle + ": " + e.getMessage());
            return null;
        }
    }

    private static String buildInnerTubeRequestJson(String lang, String fieldName, String fieldValue) {
        return "{\"context\":{\"client\":{"
                + "\"clientName\":\"" + INNERTUBE_CLIENT_NAME + "\","
                + "\"clientVersion\":\"" + INNERTUBE_CLIENT_VERSION + "\","
                + "\"hl\":\"" + escapeJsonString(lang) + "\","
                + "\"gl\":\"US\"}},"
                + "\"" + fieldName + "\":\"" + escapeJsonString(fieldValue) + "\"}";
    }

    private static String escapeJsonString(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format(Locale.US, "\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    private static String extractInnerTubeBrowseId(String body) {
        try {
            JSONObject resolveJson = new JSONObject(body);
            JSONObject endpoint = resolveJson.optJSONObject("endpoint");
            JSONObject browseEndpoint = endpoint != null ? endpoint.optJSONObject("browseEndpoint") : null;
            return browseEndpoint != null ? browseEndpoint.optString("browseId", "") : "";
        } catch (Throwable ignored) {
            // Fallback when org.json is stubbed in pure-JVM unit tests
            Matcher m = INNERTUBE_BROWSE_ID_PATTERN.matcher(body);
            return m.find() ? unescapeJsString(m.group(1)).trim() : "";
        }
    }

    private static String extractInnerTubeTitle(String body) {
        try {
            JSONObject browseJson = new JSONObject(body);
            JSONObject metadata = browseJson.optJSONObject("metadata");
            JSONObject renderer = metadata != null ? metadata.optJSONObject("channelMetadataRenderer") : null;
            return renderer != null ? renderer.optString("title", "").trim() : "";
        } catch (Throwable ignored) {
            // Fallback when org.json is stubbed in pure-JVM unit tests
            Matcher m = INNERTUBE_TITLE_PATTERN.matcher(body);
            return m.find() ? unescapeJsString(m.group(1)).trim() : "";
        }
    }

    private static final class InnerTubeResponse {
        final int statusCode;
        final String body;

        InnerTubeResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }
    }

    private static InnerTubeResponse postInnerTubeJson(String urlStr, String jsonBody) {
        HttpURLConnection conn = null;
        OutputStream os = null;
        BufferedReader reader = null;
        try {
            conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", INNERTUBE_USER_AGENT);
            conn.setConnectTimeout(NETWORK_TIMEOUT_MS);
            conn.setReadTimeout(NETWORK_TIMEOUT_MS);
            conn.setDoOutput(true);

            byte[] payload = jsonBody.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(payload.length);
            os = conn.getOutputStream();
            os.write(payload);
            os.flush();
            os.close();
            os = null;

            int code = conn.getResponseCode();
            if (code == 200) {
                reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                return new InnerTubeResponse(code, sb.toString());
            }
            return new InnerTubeResponse(code, null);
        } catch (Throwable e) {
            Logger.printDebug(() -> "InnerTube POST failed: " + e.getMessage());
            return null;
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (Throwable ignored) {
                }
            }
            if (reader != null) {
                try {
                    reader.close();
                } catch (Throwable ignored) {
                }
            }
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static String fetchViaDataApi(String handle, String apiKey) throws Exception {
        String clean = handle.startsWith("@") ? handle.substring(1) : handle;
        String encodedClean = URLEncoder.encode(clean, "UTF-8");
        String urlStr = "https://www.googleapis.com/youtube/v3/channels?part=snippet,brandingSettings&forHandle="
                + encodedClean + "&key=" + apiKey;

        HttpURLConnection conn = null;
        BufferedReader reader = null;
        try {
            conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(NETWORK_TIMEOUT_MS);
            conn.setReadTimeout(NETWORK_TIMEOUT_MS);

            if (conn.getResponseCode() == 200) {
                reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }

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
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Throwable ignored) {
                }
            }
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static String fetchViaPublicPage(String handle) {
        String target = handle.startsWith("@") ? handle.substring(1) : handle;
        boolean targetIsYouTube = target.equalsIgnoreCase("youtube");
        HttpURLConnection conn = null;
        BufferedReader reader = null;
        try {
            String encodedHandle = URLEncoder.encode(target, "UTF-8");
            // Fetch m.youtube.com directly to eliminate the www -> m 301 redirect RTT
            URL url = new URL("https://m.youtube.com/@" + encodedHandle);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36");
            conn.setRequestProperty("Accept-Language", "zh-TW,zh;q=0.9,en-US;q=0.8,en;q=0.7");
            conn.setConnectTimeout(NETWORK_TIMEOUT_MS);
            conn.setReadTimeout(NETWORK_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);

            int code = conn.getResponseCode();
            if (code == 200) {
                reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                char[] buf = new char[STREAM_READ_BUFFER_SIZE];
                String tail = "";
                int charsRead;
                int totalChars = 0;

                // Sliding tail-window scan: O(N) time and O(1) memory, checking only new chunk + 2KB overlap
                while ((charsRead = reader.read(buf, 0, buf.length)) != -1) {
                    totalChars += charsRead;
                    String window = tail + new String(buf, 0, charsRead);

                    String found = extractTitleFromWindow(window, targetIsYouTube);
                    if (found != null) {
                        return found;
                    }

                    if (totalChars >= MAX_HTML_SCAN_CHARS) {
                        break;
                    }

                    if (window.length() > STREAM_WINDOW_OVERLAP) {
                        tail = window.substring(window.length() - STREAM_WINDOW_OVERLAP);
                    } else {
                        tail = window;
                    }
                }
            } else if (code == 404) {
                return "";
            }
        } catch (Throwable e) {
            Logger.printDebug(() -> "fetchViaPublicPage failed for " + handle + ": " + e.getMessage());
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Throwable ignored) {
                }
            }
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private static String extractTitleFromWindow(String window, boolean targetIsYouTube) {
        if (window.contains("og:title")) {
            Matcher m = OG_TITLE_PATTERN_1.matcher(window);
            if (m.find()) {
                String name = m.group(1).trim();
                if (!name.isEmpty() && (targetIsYouTube || !name.equalsIgnoreCase("YouTube"))) {
                    return name;
                }
            }
            m = OG_TITLE_PATTERN_2.matcher(window);
            if (m.find()) {
                String name = m.group(1).trim();
                if (!name.isEmpty() && (targetIsYouTube || !name.equalsIgnoreCase("YouTube"))) {
                    return name;
                }
            }
        }

        if (window.contains("channelMetadataRenderer")) {
            Matcher m = JSON_TITLE_PATTERN.matcher(window);
            if (m.find()) {
                String name = unescapeJsString(m.group(1)).trim();
                if (!name.isEmpty()) {
                    return name;
                }
            }
            m = ESCAPED_JSON_TITLE_PATTERN.matcher(window);
            if (m.find()) {
                String name = unescapeJsString(m.group(1)).trim();
                if (!name.isEmpty()) {
                    return name;
                }
            }
        }

        if (window.contains("<title>") || window.contains("<TITLE>")) {
            Matcher m = CHANNEL_TITLE_XML_PATTERN.matcher(window);
            while (m.find()) {
                String raw = m.group(1).trim();
                boolean hadSuffix = raw.endsWith(" - YouTube");
                String name = hadSuffix ? raw.substring(0, raw.length() - 10).trim() : raw;
                if (!name.isEmpty() && (!name.equalsIgnoreCase("YouTube") || (targetIsYouTube && hadSuffix))) {
                    return name;
                }
            }
        }

        return null;
    }

    private static String formatName(String handle, String channelName, int format) {
        if (channelName == null || channelName.isEmpty()) return handle;
        String safeName = bidiWrap(channelName);
        switch (format) {
            case DisplayFormat.USERNAME_HANDLE:
                return safeName + " (" + handle + ")";
            case DisplayFormat.HANDLE_USERNAME:
                return handle + " (" + safeName + ")";
            case DisplayFormat.USERNAME_ONLY:
            default:
                return safeName;
        }
    }

    /**
     * Wraps resolved channel names with Android's BidiFormatter so RTL channel names
     * never corrupt surrounding LTR text. Guarded for pure-JVM unit tests where
     * Android BidiFormatter is stubbed.
     */
    public static String bidiWrap(String text) {
        if (text == null || text.isEmpty()) return text;
        try {
            BidiFormatter formatter = BidiFormatter.getInstance();
            if (formatter != null) {
                String wrapped = formatter.unicodeWrap(text);
                if (wrapped != null) {
                    return wrapped;
                }
            }
        } catch (Throwable ignored) {
        }
        return text;
    }

    /**
     * Unescapes JS hex (xNN), unicode (uNNNN), and standard JSON escape sequences
     * used in m.youtube.com ytInitialData payloads.
     */
    private static String unescapeJsString(String text) {
        if (text == null || text.indexOf('\\') < 0) return text == null ? "" : text;
        StringBuilder out = new StringBuilder(text.length());
        int len = text.length();
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < len) {
                char next = text.charAt(i + 1);
                if (next == 'x' && i + 3 < len) {
                    int val = parseHex(text, i + 2, 2);
                    if (val >= 0) {
                        out.append((char) val);
                        i += 3;
                        continue;
                    }
                } else if (next == 'u' && i + 5 < len) {
                    int val = parseHex(text, i + 2, 4);
                    if (val >= 0) {
                        out.append((char) val);
                        i += 5;
                        continue;
                    }
                } else if (next == '"' || next == '\\' || next == '/' || next == '\'') {
                    out.append(next);
                    i++;
                    continue;
                } else if (next == 'n') {
                    out.append('\n');
                    i++;
                    continue;
                } else if (next == 'r') {
                    out.append('\r');
                    i++;
                    continue;
                } else if (next == 't') {
                    out.append('\t');
                    i++;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    private static int parseHex(String s, int start, int count) {
        int val = 0;
        for (int i = 0; i < count; i++) {
            int digit = Character.digit(s.charAt(start + i), 16);
            if (digit < 0) return -1;
            val = (val << 4) | digit;
        }
        return val;
    }

    private static String unescapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&amp;", "&")
                   .replace("&quot;", "\"")
                   .replace("&#39;", "'")
                   .replace("&#x27;", "'")
                   .replace("&#34;", "\"")
                   .replace("&apos;", "'")
                   .replace("&lt;", "<")
                   .replace("&gt;", ">")
                   .replace("\\\"", "\"");
    }
}
