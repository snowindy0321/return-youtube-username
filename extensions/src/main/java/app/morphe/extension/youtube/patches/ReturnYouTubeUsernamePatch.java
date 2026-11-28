package app.morphe.extension.youtube.patches;

import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.patches.components.ContextInterface;
import app.morphe.extension.shared.settings.BooleanSetting;
import app.morphe.extension.shared.settings.IntegerSetting;
import app.morphe.extension.shared.settings.Setting;
import app.morphe.extension.shared.settings.StringSetting;
import app.morphe.extension.youtube.patches.returnyoutubeusername.ChannelResolver;
import app.morphe.extension.youtube.patches.returnyoutubeusername.PendingNameSpan;

/**
 * Replaces YouTube handles (@handle) with original channel names
 * across comments, Shorts uploaders, live chat messages, video cards/subtitles, and mentions.
 */
@SuppressWarnings("unused")
public class ReturnYouTubeUsernamePatch {

    public static final BooleanSetting ENABLED =
            new BooleanSetting("morphe_ryu_enabled", true);

    public static final IntegerSetting DISPLAY_FORMAT =
            new IntegerSetting("morphe_ryu_display_format", 0, Setting.parent(ENABLED));

    public static final StringSetting API_KEY =
            new StringSetting("morphe_ryu_api_key", "", Setting.parent(ENABLED));

    public static final BooleanSetting REPLACE_COMMENTS =
            new BooleanSetting("morphe_ryu_replace_comments", true, Setting.parent(ENABLED));

    public static final BooleanSetting REPLACE_SHORTS =
            new BooleanSetting("morphe_ryu_replace_shorts", true, Setting.parent(ENABLED));

    public static final BooleanSetting REPLACE_LIVE_CHAT =
            new BooleanSetting("morphe_ryu_replace_live_chat", true, Setting.parent(ENABLED));

    public static final BooleanSetting REPLACE_TITLE_DESCRIPTION =
            new BooleanSetting("morphe_ryu_replace_title_description", true, Setting.parent(ENABLED));

    private static final AtomicBoolean preFetchErrorLogged = new AtomicBoolean(false);
    private static final AtomicBoolean onLoadErrorLogged = new AtomicBoolean(false);

    /**
     * Entry point called on application / activity initialization.
     * Ensures this class is loaded and all Settings are registered into Morphe's Setting registry
     * before any preference fragments are inflated.
     */
    public static void setContext(android.content.Context context) {
        try {
            if (context != null && !app.morphe.extension.shared.Utils.isContextSet()) {
                app.morphe.extension.shared.Utils.setContext(context);
            }
        } catch (Throwable ignored) {
        }
    }

    // Standalone YouTube handle: supports Unicode letters/digits/marks, '_', '-', and '.',
    // excluding bullet separators ('·', '•', '・'), whitespace, punctuation, and replacement chars.
    private static final Pattern HANDLE_PATTERN = Pattern.compile(
            "^@[\\p{L}\\p{N}\\p{M}_-](?:[\\p{L}\\p{N}\\p{M}._-]{0,58}[\\p{L}\\p{N}\\p{M}_-])?$"
    );

    // YouTube handle within text:
    // - Left boundary allows CJK/non-ASCII before '@' while blocking emails (ASCII word/._%+-).
    // - Handle body excludes bullet separators ('·' U+00B7, '•' U+2022, '・' U+30FB).
    // - Right boundary preserves live-chat partial-prefix protection while allowing bullets & possessives (').
    private static final Pattern IN_TEXT_HANDLE_PATTERN = Pattern.compile(
            "(?:^|[^a-zA-Z0-9._%+-])"
                    + "(@[\\p{L}\\p{N}\\p{M}_-](?:[\\p{L}\\p{N}\\p{M}._-]{0,58}[\\p{L}\\p{N}\\p{M}_-])?)"
                    + "(?=$|[\\s\\u00A0\\u200B-\\u200F\\u202A-\\u202E\\u2066-\\u2069\\uFEFF\\uFFFC\\u00B7\\u2022\\u30FB':：,，。、;；)）\\]】}」』>》/|]|[.!！?？~～](?![\\p{L}\\p{N}\\p{M}_-]))"
    );

    // Stats suffixes that YouTube may glue directly onto @handle in video subtitles without whitespace/bullets
    // (e.g. "@Google觀看次數：1.2萬次", "@Google1.2萬次觀看", "@Google・100回視聴").
    private static final Pattern VIDEO_SUBTITLE_GLUED_STATS_PATTERN = Pattern.compile(
            "(?:觀看次數|观看次数|播放次數|播放次数|回視聴|조회수|正在觀看|正在观看|直播中|正在直播|首播|プレミア公開|位訂閱者|位订阅者|訂閱者|订阅者"
                    + "|\\d+(?:\\.\\d+)?[萬万億亿千KkMmBb]?(?:次觀看|次观看|回視聴|회|天前|小時前|小时前|分鐘前|分钟前|秒前|週前|周前|個月前|个月前|年前|日前|時間前|分前|か月前))"
    );

    private static final class MatchRange {
        final String handle;
        final int start;
        final int end;

        MatchRange(String handle, int start, int end) {
            this.handle = handle;
            this.start = start;
            this.end = end;
        }
    }

    /**
     * Pre-fetch hook called when SpannableString is being built in the background.
     */
    public static void preFetchLithoText(ContextInterface contextInterface,
                                         CharSequence original) {
        try {
            if (!ENABLED.get() || original == null) {
                return;
            }

            String text = original.toString();
            if (text.isEmpty() || text.indexOf('@') < 0) return;

            String path = getPath(contextInterface);
            String identifier = getIdentifier(contextInterface);
            if (isChannelProfileHeader(path, identifier)) {
                return;
            }

            boolean isVideoSub = isVideoCardOrSubtitleContext(path, identifier);
            String stripped = stripWhitespace(text);
            boolean isStandaloneHandle = isStandaloneHandleForContext(original, text, stripped, isVideoSub);

            // 1. Standalone handle (comments / Shorts / video cards / subtitles)
            if (isStandaloneHandle && isEligibleHandle(contextInterface, stripped)) {
                ChannelResolver.prefetch(stripped, API_KEY.get());
                return;
            }

            // 2. In-text handles (Live chat, title/description, video subtitle/feed card)
            boolean isLiveChat = REPLACE_LIVE_CHAT.get() && isLiveChatContext(contextInterface, text);
            boolean isTitleDesc = REPLACE_TITLE_DESCRIPTION.get() && isTitleOrDescriptionContext(contextInterface, text);
            boolean shouldFetch = isLiveChat || isTitleDesc || isVideoSub;
            if (shouldFetch) {
                String apiKey = API_KEY.get();
                List<MatchRange> matches = extractInTextMatches(original, text, isVideoSub);
                for (MatchRange m : matches) {
                    ChannelResolver.prefetch(m.handle, apiKey);
                }
            }
        } catch (Throwable ex) {
            if (preFetchErrorLogged.compareAndSet(false, true)) {
                Logger.printException(() -> "ReturnYouTubeUsername preFetchLithoText failed", ex);
            } else {
                Logger.printDebug(() -> "ReturnYouTubeUsername preFetchLithoText failed: " + ex);
            }
        }
    }

    /**
     * Main hook called by hookLithoSpannableString when Litho creates or reuses text.
     */
    public static CharSequence onLithoTextLoaded(ContextInterface contextInterface,
                                                 CharSequence original) {
        try {
            if (!ENABLED.get() || original == null) {
                return original;
            }

            String text = original.toString();
            if (text.isEmpty() || text.indexOf('@') < 0) return original;

            // Check channel profile header / bottom sheet exclusion first
            String path = getPath(contextInterface);
            String identifier = getIdentifier(contextInterface);
            if (isChannelProfileHeader(path, identifier)) {
                return original;
            }

            boolean isVideoSub = isVideoCardOrSubtitleContext(path, identifier);
            String stripped = stripWhitespace(text);
            boolean isStandaloneHandle = isStandaloneHandleForContext(original, text, stripped, isVideoSub);

            // 1. In-text handle replacement (Live Chat, Title, Description, or Video Subtitle / Feed Card)
            // Standalone handles are skipped here unless it is live chat, so they don't get accidentally replaced as titles
            boolean liveChatMatch = isLiveChatContext(contextInterface, text);
            if (!isStandaloneHandle || liveChatMatch) {
                boolean isLiveChat = REPLACE_LIVE_CHAT.get() && liveChatMatch;
                boolean isTitleDesc = REPLACE_TITLE_DESCRIPTION.get() && isTitleOrDescriptionContext(contextInterface, text);

                // Comments entry point teaser respects REPLACE_COMMENTS
                boolean effectiveVideoSub = isVideoSub
                        && (!containsAny(path, identifier, "comments_entry_point_teaser") || REPLACE_COMMENTS.get());

                if (isLiveChat || isTitleDesc || effectiveVideoSub) {
                    return replaceInTextHandles(original, text, isLiveChat, isTitleDesc, effectiveVideoSub);
                }
            }

            // 2. Standalone handle replacement (Comments, Shorts uploaders, video cards/subtitles, title/description)
            if (isStandaloneHandle && isEligibleHandle(contextInterface, stripped)) {
                int format = DISPLAY_FORMAT.get();
                String apiKey = API_KEY.get();
                boolean useRelayout = ChannelResolver.isLithoRelayoutAvailable();
                String resolved = useRelayout
                        ? ChannelResolver.getCachedOrStartFetch(stripped, apiKey, format)
                        : ChannelResolver.getOrResolve(stripped, apiKey, format);

                if (resolved != null && !resolved.isEmpty() && !resolved.equals(stripped)) {
                    if (isTitleOrDescriptionContext(contextInterface, text)) {
                        if (!resolved.startsWith("@")) {
                            resolved = "@" + resolved;
                        }
                    } else if (text.endsWith(" ") && !resolved.endsWith(" ")) {
                        resolved = resolved + " ";
                    }
                    return newSpanUsingStylingOfAnotherSpan(original, resolved);
                }

                if (useRelayout && resolved == null && ChannelResolver.isPending(stripped)) {
                    return withPendingSpan(original, stripped);
                }
            }
        } catch (Throwable ex) {
            if (onLoadErrorLogged.compareAndSet(false, true)) {
                Logger.printException(() -> "ReturnYouTubeUsername onLithoTextLoaded failed", ex);
            } else {
                Logger.printDebug(() -> "ReturnYouTubeUsername onLithoTextLoaded failed: " + ex);
            }
        }
        return original;
    }

    private static CharSequence withPendingSpan(CharSequence original, String handle) {
        if (!ChannelResolver.isLithoRelayoutAvailable()) {
            return original;
        }
        try {
            SpannableStringBuilder pending = new SpannableStringBuilder(original);
            attachPendingSpan(pending, handle);
            return pending;
        } catch (Throwable ignored) {
            return original;
        }
    }

    private static void attachPendingSpan(SpannableStringBuilder ssb, String handle) {
        if (!ChannelResolver.isLithoRelayoutAvailable()) {
            return;
        }
        try {
            Object span = new PendingNameSpan(handle);
            ssb.setSpan(span, 0, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } catch (Throwable ignored) {
        }
    }

    private static boolean isStandaloneHandleForContext(CharSequence original,
                                                        String text,
                                                        String stripped,
                                                        boolean isVideoSub) {
        if (stripped == null || !HANDLE_PATTERN.matcher(stripped).matches()) {
            return false;
        }
        if (isVideoSub) {
            int atIdx = text.indexOf('@');
            if (atIdx >= 0) {
                int rawEnd = atIdx + stripped.length();
                int trimmedEnd = trimVideoSubtitleHandleEnd(original, text, atIdx, rawEnd);
                if (trimmedEnd > atIdx + 1 && trimmedEnd < rawEnd) {
                    return false;
                }
            }
        }
        return true;
    }

    private static List<MatchRange> extractInTextMatches(CharSequence original,
                                                         String text,
                                                         boolean isVideoSub) {
        Matcher matcher = IN_TEXT_HANDLE_PATTERN.matcher(text);
        List<MatchRange> matches = new ArrayList<>();
        while (matcher.find()) {
            int start = matcher.start(1);
            int end = matcher.end(1);
            if (isVideoSub) {
                end = trimVideoSubtitleHandleEnd(original, text, start, end);
            }
            if (end > start + 1) {
                String handle = text.substring(start, end);
                matches.add(new MatchRange(handle, start, end));
            }
        }
        return matches;
    }

    /**
     * In video subtitles/cards, handles may be immediately followed by view counts or CJK stats
     * without a space or bullet (e.g. "@Google觀看次數：1.2萬次"). Uses Spanned transitions and
     * known subtitle stats suffixes to trim only the handle portion without breaking Unicode handles.
     */
    private static int trimVideoSubtitleHandleEnd(CharSequence original,
                                                  String text,
                                                  int start,
                                                  int end) {
        int resultEnd = end;

        // 1. Check Spanned span transitions inside the matched range
        if (original instanceof Spanned) {
            try {
                Spanned spanned = (Spanned) original;
                int pos = start;
                while (pos < resultEnd) {
                    int next = spanned.nextSpanTransition(pos, resultEnd, Object.class);
                    if (next <= pos) break;
                    if (next > start + 1 && next < resultEnd) {
                        char boundaryChar = text.charAt(next);
                        if (!(boundaryChar >= 'a' && boundaryChar <= 'z')
                                && !(boundaryChar >= 'A' && boundaryChar <= 'Z')
                                && boundaryChar != '_' && boundaryChar != '-') {
                            resultEnd = next;
                            break;
                        }
                    }
                    pos = next;
                }
            } catch (Throwable ignored) {
            }
        }

        // 2. Trim known subtitle stats suffixes glued directly onto the handle
        if (resultEnd > start + 2) {
            String segment = text.substring(start, resultEnd);
            Matcher statsMatcher = VIDEO_SUBTITLE_GLUED_STATS_PATTERN.matcher(segment);
            if (statsMatcher.find() && statsMatcher.start() > 1) {
                resultEnd = start + statsMatcher.start();
            }
        }

        // Trim any trailing '.' left exposed by trimming
        while (resultEnd > start + 2 && text.charAt(resultEnd - 1) == '.') {
            resultEnd--;
        }
        return resultEnd;
    }

    /**
     * Replaces in-text handles (e.g. in live chat rows, video subtitles/cards, titles, or descriptions)
     * while preserving all spans, badges, and formatting, and ensuring appropriate spacing.
     */
    private static CharSequence replaceInTextHandles(CharSequence original,
                                                     String text,
                                                     boolean isLiveChat,
                                                     boolean isTitleOrDesc,
                                                     boolean isVideoSub) {
        List<MatchRange> matches = extractInTextMatches(original, text, isVideoSub);
        if (matches.isEmpty()) {
            return original;
        }

        SpannableStringBuilder ssb = new SpannableStringBuilder(original);
        boolean modified = false;
        List<String> pendingHandles = null;
        String apiKey = API_KEY.get();
        int format = DISPLAY_FORMAT.get();
        boolean useRelayout = ChannelResolver.isLithoRelayoutAvailable();

        // Replace from end to start so earlier indices remain valid
        for (int i = matches.size() - 1; i >= 0; i--) {
            MatchRange m = matches.get(i);
            String resolved;
            if (isLiveChat) {
                // Live chat waits up to 600ms so fast-scrolling messages resolve on first render when possible
                resolved = ChannelResolver.getOrResolveLiveChat(m.handle, apiKey, format);
            } else if (useRelayout) {
                // Non-blocking 0ms lookup when Morphe v2.4+ LithoRelayoutPatch is available
                resolved = ChannelResolver.getCachedOrStartFetch(m.handle, apiKey, format);
            } else {
                // Older Morphe fallback: bounded wait (1200ms); never strips '@' on timeout
                resolved = ChannelResolver.getOrResolve(m.handle, apiKey, format);
            }

            if (resolved != null && !resolved.isEmpty() && !resolved.equals(m.handle)) {
                if (isTitleOrDesc) {
                    // For video title and description mentions: keep @ in front: @頻道名稱
                    if (!resolved.startsWith("@")) {
                        resolved = "@" + resolved;
                    }
                } else {
                    // Only append a single space when followed by non-whitespace and not a bullet/punctuation separator
                    if (m.end < ssb.length() && !resolved.endsWith(" ")) {
                        char nextChar = ssb.charAt(m.end);
                        if (needsTrailingSpace(nextChar)) {
                            resolved = resolved + " ";
                        }
                    }
                }

                ssb.replace(m.start, m.end, resolved);
                modified = true;
            }

            if (useRelayout && ChannelResolver.isPending(m.handle)) {
                if (pendingHandles == null) {
                    pendingHandles = new ArrayList<>(matches.size());
                }
                pendingHandles.add(m.handle);
            }
        }

        boolean attachedPending = false;
        if (pendingHandles != null) {
            for (String handle : pendingHandles) {
                attachPendingSpan(ssb, handle);
            }
            attachedPending = true;
        }

        return (modified || attachedPending) ? ssb : original;
    }

    private static boolean needsTrailingSpace(char nextChar) {
        return !Character.isWhitespace(nextChar)
                && nextChar != '\u00A0'
                && nextChar != '\u00B7' // ·
                && nextChar != '\u2022' // •
                && nextChar != '\u30FB' // ・
                && nextChar != '\uFFFC' // object replacement (badge)
                && nextChar != ':' && nextChar != '：'
                && nextChar != ',' && nextChar != '，'
                && nextChar != '.' && nextChar != '。'
                && nextChar != '、' && nextChar != ';' && nextChar != '；'
                && nextChar != ')' && nextChar != '）'
                && nextChar != ']' && nextChar != '】'
                && nextChar != '\'' && nextChar != '"'
                && nextChar != '!' && nextChar != '！'
                && nextChar != '?' && nextChar != '？';
    }

    /**
     * Checks if this text component is within a Live Chat / Super Chat context.
     */
    private static boolean isLiveChatContext(ContextInterface contextInterface, String text) {
        if (contextInterface != null) {
            String path = getPath(contextInterface);
            String identifier = getIdentifier(contextInterface);
            if (containsAny(path, identifier, "live_chat", "super_chat")) {
                return true;
            }
            // Do not misclassify verified-badge (U+FFFC) video cards, subtitles, comments, shorts, or descriptions as live chat
            if (isVideoCardOrSubtitleContext(path, identifier)
                    || containsAny(path, identifier,
                    "comment", "reply", "reel", "shorts",
                    "video_title", "headline", "description", "structured_description", "cell_description")) {
                return false;
            }
        }
        // Heuristic: compound string containing replacement character (U+FFFC for badges) and @
        if (text != null && text.length() > 2) {
            if (text.indexOf('\uFFFC') >= 0 && text.indexOf('@') >= 0) {
                return true;
            }
        }
        return false;
    }

    private static String getPath(ContextInterface contextInterface) {
        if (contextInterface == null) return "";
        try {
            StringBuilder pb = contextInterface.patch_getPathBuilder();
            if (pb != null) return pb.toString().toLowerCase(Locale.ENGLISH);
        } catch (Throwable ignored) {}
        return "";
    }

    private static String getIdentifier(ContextInterface contextInterface) {
        if (contextInterface == null) return "";
        try {
            String id = contextInterface.patch_getIdentifier();
            if (id != null) return id.toLowerCase(Locale.ENGLISH);
        } catch (Throwable ignored) {}
        return "";
    }

    /**
     * Checks if this text component is within a modern YouTube under-video subtitle,
     * feed video card, watch-page uploader/channel bar, or comments teaser.
     */
    private static boolean isVideoCardOrSubtitleContext(ContextInterface contextInterface) {
        if (contextInterface == null) return false;
        return isVideoCardOrSubtitleContext(getPath(contextInterface), getIdentifier(contextInterface));
    }

    private static boolean isVideoCardOrSubtitleContext(String path, String identifier) {
        if (isChannelProfileHeader(path, identifier)) {
            return false;
        }
        // Video titles and descriptions inside a video card belong to Title/Description context, not subtitle
        if (containsAny(path, identifier,
                "video_title", "headline", "description", "structured_description",
                "cell_description", "macro_markers")) {
            return false;
        }
        // Shorts reel_channel_bar is governed by REPLACE_SHORTS, not video subtitle
        if (containsAny(path, identifier, "reel", "shorts")) {
            return false;
        }
        return containsAny(path, identifier,
                "video_subtitle",
                "video_metadata",
                "slim_video",
                "video_info",
                "video_details",
                "subtitle",
                "content_metadata",
                "lockup_metadata",
                "video_lockup",
                "video_with_context",
                "compact_video",
                "video_owner",
                "slim_owner",
                "channel_bar",
                "byline",
                "comments_entry_point_teaser");
    }

    /**
     * Checks if this text component is within video metadata / video subtitle / feed video cards.
     */
    private static boolean isVideoSubtitleContext(ContextInterface contextInterface) {
        return isVideoCardOrSubtitleContext(contextInterface);
    }

    /**
     * Checks if this text component is within a video title, video description,
     * or general content mentions.
     */
    private static boolean isTitleOrDescriptionContext(ContextInterface contextInterface, String text) {
        if (contextInterface == null) return false;

        String path = getPath(contextInterface);
        String identifier = getIdentifier(contextInterface);

        // Channel profile headers, preview cards, and profile sheets are NEVER title/description
        if (isChannelProfileHeader(path, identifier)) {
            return false;
        }

        // Video subtitles and feed card metadata rows are NEVER title/description
        // (note: "subtitle" contains "title", so this check prevents false matches!)
        if (isVideoCardOrSubtitleContext(path, identifier)) {
            return false;
        }

        // If it's a standalone handle (e.g. "@channel_handle"), it is NEVER a title or description sentence
        // unless explicitly indicated by video_title or headline.
        String stripped = stripWhitespace(text);
        boolean isStandaloneHandle = (stripped != null && HANDLE_PATTERN.matcher(stripped).matches());
        if (isStandaloneHandle) {
            if (!containsAny(path, identifier, "video_title", "headline")) {
                return false;
            }
        }

        if (containsAny(path, identifier,
                "video_title",
                "headline",
                "description",
                "macro_markers",
                "structured_description",
                "expandable",
                "cell_description")) {
            return true;
        }

        if ((path.contains("title") && !path.contains("subtitle"))
                || (identifier.contains("title") && !identifier.contains("subtitle"))) {
            return true;
        }

        // Outside of chat, comments, shorts, video cards/subtitles, and channel pages: compound text with @ is title/description
        if (!isStandaloneHandle) {
            if (!containsAny(path, identifier,
                    "live_chat", "super_chat", "comment", "reply",
                    "reel", "shorts", "channel", "profile")) {
                if (text != null && text.length() > 5 && text.indexOf('@') >= 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Determines whether the given text is a YouTube handle in a supported context.
     */
    private static boolean isEligibleHandle(ContextInterface contextInterface, String text) {
        if (text == null || text.length() < 2 || text.length() > 60 || text.charAt(0) != '@') {
            return false;
        }

        if (!HANDLE_PATTERN.matcher(text).matches()) {
            return false;
        }

        // Check context if available
        if (contextInterface != null) {
            String path = getPath(contextInterface);
            String identifier = getIdentifier(contextInterface);

            // Priority 1: Strict channel profile header / preview bottom sheet exclusion
            if (isChannelProfileHeader(path, identifier)) {
                return false;
            }

            // Priority 2: Comments (comment text, author badge, comment thread, reply, teaser)
            if (containsAny(path, identifier, "comment", "author", "reply", "comments_entry_point_teaser")) {
                return REPLACE_COMMENTS.get();
            }

            // Priority 3: Shorts (reel channel bar, reel player overlay, reel viewer)
            if (containsAny(path, identifier, "reel", "shorts")) {
                return REPLACE_SHORTS.get();
            }

            // Priority 4: Video subtitle / feed video cards / watch-page uploader & channel bar
            if (isVideoCardOrSubtitleContext(path, identifier)) {
                return true;
            }

            // Priority 5: Title / Description
            if (isTitleOrDescriptionContext(contextInterface, text)) {
                return REPLACE_TITLE_DESCRIPTION.get();
            }

            // Priority 6: Generic channel/profile rejection (never reject video/owner/channel_bar/byline/lockup/subtitle paths)
            if (containsAny(path, identifier, "channel", "profile")
                    && !containsAny(path, identifier, "video", "owner", "channel_bar", "byline", "lockup", "subtitle")) {
                return false;
            }
        }

        // Default to REPLACE_COMMENTS for comments, replies, community posts, and other content
        return REPLACE_COMMENTS.get();
    }

    private static boolean isChannelProfileHeader(String path, String identifier) {
        if (path == null) path = "";
        if (identifier == null) identifier = "";

        // Unambiguous video, comment, shorts, and title/description contexts are never channel profile headers
        if (containsAny(path, identifier,
                "video_lockup",
                "lockup_metadata",
                "video_subtitle",
                "video_metadata",
                "video_with_context",
                "compact_video",
                "video_title",
                "description",
                "headline",
                "comment",
                "reply",
                "reel",
                "shorts",
                "comments_entry_point_teaser")) {
            return false;
        }

        // Strict exclusions ONLY for actual channel profile headers and channel preview bottom sheets
        // (note: standalone content_metadata without page_header/profile_card is a feed video card and returns false)
        return containsAny(path, identifier,
                "page_header",
                "channel_profile",
                "channel_header",
                "channel_page",
                "channel_about",
                "about_channel",
                "profile_card",
                "profilecard",
                "profile_sheet",
                "channel_sheet",
                "cairo_channel_header",
                "channel_preview",
                "channelpreview",
                "user_card",
                "user_preview");
    }

    private static boolean containsAny(String path, String identifier, String... needles) {
        for (String needle : needles) {
            if ((path != null && path.contains(needle))
                    || (identifier != null && identifier.contains(needle))) {
                return true;
            }
        }
        return false;
    }

    private static String stripWhitespace(String text) {
        if (text == null) return null;
        int start = 0;
        int end = text.length();
        while (start < end && (Character.isWhitespace(text.charAt(start)) || text.charAt(start) == '\u200B' || text.charAt(start) == '\uFEFF')) {
            start++;
        }
        while (end > start && (Character.isWhitespace(text.charAt(end - 1)) || text.charAt(end - 1) == '\u200B' || text.charAt(end - 1) == '\uFEFF')) {
            end--;
        }
        return text.substring(start, end);
    }

    /**
     * Clones all spans and styling from source to target SpannableString.
     */
    public static CharSequence newSpanUsingStylingOfAnotherSpan(CharSequence source,
                                                                CharSequence target) {
        if (!(source instanceof Spanned)) {
            return target;
        }
        try {
            SpannableString result = new SpannableString(target);
            Spanned spanned = (Spanned) source;
            Object[] spans = spanned.getSpans(0, spanned.length(), Object.class);
            int targetLen = target.length();
            if (spans != null) {
                for (Object span : spans) {
                    try {
                        result.setSpan(span, 0, targetLen, spanned.getSpanFlags(span));
                    } catch (Throwable ignored) {
                    }
                }
            }
            return result;
        } catch (Throwable ignored) {
            return target;
        }
    }
}
