package app.morphe.extension.youtube.patches;

import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.patches.components.ContextInterface;
import app.morphe.extension.shared.settings.BooleanSetting;
import app.morphe.extension.shared.settings.IntegerSetting;
import app.morphe.extension.shared.settings.StringSetting;
import app.morphe.extension.youtube.patches.returnyoutubeusername.ChannelResolver;
import app.morphe.extension.youtube.patches.returnyoutubeusername.DisplayFormat;

/**
 * Replaces YouTube handles (@handle) with original channel names
 * across comments, Shorts uploaders, live chat messages, and handles throughout the app.
 */
@SuppressWarnings("unused")
public class ReturnYouTubeUsernamePatch {

    public static final BooleanSetting ENABLED =
            new BooleanSetting("morphe_return_youtube_username_enabled", true);

    public static final IntegerSetting DISPLAY_FORMAT =
            new IntegerSetting("morphe_return_youtube_username_display_format", 0);

    public static final StringSetting API_KEY =
            new StringSetting("morphe_return_youtube_username_api_key", "");

    public static final BooleanSetting REPLACE_COMMENTS =
            new BooleanSetting("morphe_return_youtube_username_replace_comments", true);

    public static final BooleanSetting REPLACE_SHORTS =
            new BooleanSetting("morphe_return_youtube_username_replace_shorts", true);

    public static final BooleanSetting REPLACE_LIVE_CHAT =
            new BooleanSetting("morphe_return_youtube_username_replace_live_chat", true);

    public static final BooleanSetting REPLACE_TITLE_DESCRIPTION =
            new BooleanSetting("morphe_return_youtube_username_replace_title_description", true);

    /**
     * Entry point called on application / activity initialization.
     * Ensures this class is loaded and all Settings are registered into Morphe's Setting registry
     * before any preference fragments are inflated.
     */
    public static void setContext(android.content.Context context) {
        try {
            app.morphe.extension.shared.Utils.setContext(context);
        } catch (Throwable ignored) {
        }
    }

    // YouTube handle regex: starts with @ followed by characters allowed in handles
    private static final Pattern HANDLE_PATTERN =
            Pattern.compile("^@[^\\s/@#%&?+:;!\"'()\\[\\]{}<>=]+$");

    // YouTube handle within text: starts at line begin or preceded by non-handle character
    private static final Pattern IN_TEXT_HANDLE_PATTERN =
            Pattern.compile("(?:^|[^a-zA-Z0-9._%+-])(@[a-zA-Z0-9_-](?:[a-zA-Z0-9._-]{0,28}[a-zA-Z0-9_-])?)");

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
            if (text.isEmpty()) return;

            // 1. Standalone handle (comments / Shorts)
            String stripped = stripWhitespace(text);
            if (isEligibleHandle(contextInterface, stripped)) {
                ChannelResolver.prefetch(stripped, API_KEY.get());
                return;
            }

            // 2. In-text handles (Title, description, live chat, video subtitle)
            if (text.indexOf('@') >= 0) {
                boolean shouldFetch = (REPLACE_LIVE_CHAT.get() && isLiveChatContext(contextInterface, text))
                        || (REPLACE_TITLE_DESCRIPTION.get() && isTitleOrDescriptionContext(contextInterface, text))
                        || isVideoSubtitleContext(contextInterface);
                if (shouldFetch) {
                    Matcher matcher = IN_TEXT_HANDLE_PATTERN.matcher(text);
                    while (matcher.find()) {
                        String handle = matcher.group(1);
                        ChannelResolver.prefetch(handle, API_KEY.get());
                    }
                }
            }
        } catch (Exception ex) {
            Logger.printException(() -> "ReturnYouTubeUsername preFetchLithoText failed", ex);
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
            if (text.isEmpty()) return original;

            // Check channel profile header / bottom sheet exclusion first
            if (contextInterface != null) {
                String path = getPath(contextInterface);
                String identifier = getIdentifier(contextInterface);
                if (isChannelProfileHeader(path, identifier)) {
                    return original;
                }
            }

            String stripped = stripWhitespace(text);
            boolean isStandaloneHandle = (stripped != null && HANDLE_PATTERN.matcher(stripped).matches());

            // 1. In-text handle replacement (Live Chat, Title, Description, or Video Subtitle)
            // Standalone handles are skipped here unless it is live chat, so they don't get accidentally replaced as titles
            if (text.indexOf('@') >= 0 && (!isStandaloneHandle || isLiveChatContext(contextInterface, text))) {
                boolean isLiveChat = REPLACE_LIVE_CHAT.get() && isLiveChatContext(contextInterface, text);
                boolean isTitleDesc = REPLACE_TITLE_DESCRIPTION.get() && isTitleOrDescriptionContext(contextInterface, text);
                boolean isVideoSub = isVideoSubtitleContext(contextInterface);

                if (isLiveChat || isTitleDesc || isVideoSub) {
                    return replaceInTextHandles(contextInterface, original);
                }
            }

            // 2. Standalone handle replacement (Comments, Shorts uploaders, video metadata, title/description)
            if (isEligibleHandle(contextInterface, stripped)) {
                int format = DISPLAY_FORMAT.get();
                String resolved = ChannelResolver.getOrResolve(stripped, API_KEY.get(), format);
                if (resolved != null && !resolved.isEmpty() && !resolved.equals(stripped)) {
                    if (isTitleOrDescriptionContext(contextInterface, text)) {
                        if (!resolved.startsWith("@")) {
                            resolved = "@" + resolved;
                        }
                    } else if (isVideoSubtitleContext(contextInterface)) {
                        if (!resolved.endsWith(" ")) {
                            resolved = resolved + " "; // ONE SPACE
                        }
                    }
                    return newSpanUsingStylingOfAnotherSpan(original, resolved);
                }
            }
        } catch (Exception ex) {
            Logger.printException(() -> "ReturnYouTubeUsername onLithoTextLoaded failed", ex);
        }
        return original;
    }

    /**
     * Replaces in-text handles (e.g. in live chat rows, video titles, or description lines)
     * while preserving all spans, badges, and formatting, and ensuring appropriate spacing.
     */
    private static CharSequence replaceInTextHandles(ContextInterface contextInterface,
                                                     CharSequence original) {
        String text = original.toString();
        Matcher matcher = IN_TEXT_HANDLE_PATTERN.matcher(text);

        List<MatchRange> matches = new ArrayList<>();
        while (matcher.find()) {
            String handle = matcher.group(1);
            int start = matcher.start(1);
            int end = matcher.end(1);
            matches.add(new MatchRange(handle, start, end));
        }

        if (matches.isEmpty()) {
            return original;
        }

        SpannableStringBuilder ssb = new SpannableStringBuilder(original);
        boolean modified = false;

        boolean isVideoSub = isVideoSubtitleContext(contextInterface);
        boolean isTitleOrDesc = isTitleOrDescriptionContext(contextInterface, text);

        // Replace from end to start so earlier indices remain valid
        for (int i = matches.size() - 1; i >= 0; i--) {
            MatchRange m = matches.get(i);
            String resolved = isTitleOrDesc
                    ? ChannelResolver.getOrResolve(m.handle, API_KEY.get(), DISPLAY_FORMAT.get())
                    : ChannelResolver.getOrResolveLiveChat(m.handle, API_KEY.get(), DISPLAY_FORMAT.get());

            if (resolved != null && !resolved.isEmpty() && !resolved.equals(m.handle)) {
                if (isTitleOrDesc) {
                    // For video title and description mentions: keep @ in front: @頻道名稱
                    if (!resolved.startsWith("@")) {
                        resolved = "@" + resolved;
                    }
                } else if (isVideoSub) {
                    // For video subtitle under player: add a single space separator
                    if (!resolved.endsWith(" ")) {
                        resolved = resolved + " ";
                    }
                } else {
                    // For live chat or general in-text handles: separate if followed by non-whitespace
                    if (m.end < ssb.length()) {
                        char nextChar = ssb.charAt(m.end);
                        if (!Character.isWhitespace(nextChar) && nextChar != ':' && nextChar != '：'
                                && nextChar != ',' && nextChar != '，' && nextChar != ')' && nextChar != '）'
                                && nextChar != ']' && nextChar != '】') {
                            resolved = resolved + " ";
                        }
                    }
                }

                ssb.replace(m.start, m.end, resolved);
                modified = true;
            }
        }

        return modified ? ssb : original;
    }

    /**
     * Checks if this text component is within a Live Chat / Super Chat context.
     */
    private static boolean isLiveChatContext(ContextInterface contextInterface, String text) {
        if (contextInterface != null) {
            String path = getPath(contextInterface);
            String identifier = getIdentifier(contextInterface);
            if (path.contains("live_chat") || path.contains("super_chat")
                    || identifier.contains("live_chat") || identifier.contains("super_chat")) {
                return true;
            }
        }
        // Heuristic: compound string containing replacement character (\uFFFC for badges) and @
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
        } catch (Exception ignored) {}
        return "";
    }

    private static String getIdentifier(ContextInterface contextInterface) {
        if (contextInterface == null) return "";
        try {
            String id = contextInterface.patch_getIdentifier();
            if (id != null) return id.toLowerCase(Locale.ENGLISH);
        } catch (Exception ignored) {}
        return "";
    }

    /**
     * Checks if this text component is within video metadata / video subtitle
     * (the uploader and stats line right below the video player).
     */
    private static boolean isVideoSubtitleContext(ContextInterface contextInterface) {
        if (contextInterface == null) return false;
        String path = getPath(contextInterface);
        String identifier = getIdentifier(contextInterface);
        return path.contains("video_subtitle")
                || path.contains("video_metadata")
                || path.contains("slim_video")
                || path.contains("video_info")
                || path.contains("video_details")
                || path.contains("subtitle")
                || identifier.contains("video_subtitle")
                || identifier.contains("video_metadata")
                || identifier.contains("slim_video")
                || identifier.contains("video_info")
                || identifier.contains("video_details")
                || identifier.contains("subtitle");
    }

    /**
     * Checks if this text component is within a video title, video description,
     * or general content mentions.
     */
    private static boolean isTitleOrDescriptionContext(ContextInterface contextInterface, String text) {
        if (contextInterface == null) return false;

        String path = getPath(contextInterface);
        String identifier = getIdentifier(contextInterface);

        // Channel profile headers, preview cards, and profile sheets are NEVER title/description!
        if (isChannelProfileHeader(path, identifier)) {
            return false;
        }

        // If it's a standalone handle (e.g. "@Karsa0214"), it is NEVER a title or description sentence
        // unless explicitly indicated by video_title or headline.
        String stripped = stripWhitespace(text);
        boolean isStandaloneHandle = (stripped != null && HANDLE_PATTERN.matcher(stripped).matches());
        if (isStandaloneHandle) {
            if (!path.contains("video_title") && !identifier.contains("video_title")
                    && !path.contains("headline") && !identifier.contains("headline")) {
                return false;
            }
        }

        if (path.contains("video_title") || identifier.contains("video_title")
                || path.contains("title") || identifier.contains("title")
                || path.contains("headline") || identifier.contains("headline")
                || path.contains("description") || identifier.contains("description")
                || path.contains("macro_markers") || identifier.contains("macro_markers")
                || path.contains("structured_description") || identifier.contains("structured_description")
                || path.contains("expandable") || identifier.contains("expandable")
                || path.contains("cell_description") || identifier.contains("cell_description")) {
            return true;
        }

        // Outside of chat, comments, shorts, and channel pages: compound text with @ is title/description
        if (!isStandaloneHandle) {
            if (!path.contains("live_chat") && !path.contains("super_chat")
                    && !path.contains("comment") && !path.contains("reply")
                    && !path.contains("reel") && !path.contains("shorts")
                    && !path.contains("channel") && !identifier.contains("channel")
                    && !path.contains("profile") && !identifier.contains("profile")
                    && !identifier.contains("live_chat") && !identifier.contains("comment")
                    && !identifier.contains("reel") && !identifier.contains("shorts")) {
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

            // Priority 1: Channel profile header / preview card / bottom sheet (Image 2 vs Image 3, Image 10)
            if (isChannelProfileHeader(path, identifier)) {
                return false;
            }

            // Channel elements (pages, previews, cards) are never comments or shorts
            if ((path.contains("channel") || identifier.contains("channel")
                    || path.contains("profile") || identifier.contains("profile"))
                    && !path.contains("comment") && !identifier.contains("comment")
                    && !path.contains("reply") && !identifier.contains("reply")
                    && !path.contains("reel") && !identifier.contains("reel")
                    && !path.contains("shorts") && !identifier.contains("shorts")) {
                return false;
            }

            // Priority 2: Comments: comment text, author badge, comment thread, reply, etc.
            boolean isComments = path.contains("comment")
                    || path.contains("author")
                    || path.contains("reply")
                    || identifier.contains("comment")
                    || identifier.contains("author")
                    || identifier.contains("reply");
            if (isComments) {
                return REPLACE_COMMENTS.get();
            }

            // Priority 3: Shorts: reel channel bar, reel player overlay, reel viewer, etc.
            boolean isShorts = path.contains("reel") || identifier.contains("reel")
                    || path.contains("shorts") || identifier.contains("shorts");
            if (isShorts) {
                return REPLACE_SHORTS.get();
            }

            // Priority 4: Title / Description
            if (isTitleOrDescriptionContext(contextInterface, text)) {
                return REPLACE_TITLE_DESCRIPTION.get();
            }

            // Priority 5: Video subtitle
            if (isVideoSubtitleContext(contextInterface)) {
                return true;
            }
        }

        // Default to REPLACE_COMMENTS for comments, replies, community posts, and other content
        return REPLACE_COMMENTS.get();
    }

    private static boolean isChannelProfileHeader(String path, String identifier) {
        if (path == null) path = "";
        if (identifier == null) identifier = "";

        // Descriptions and video titles are never channel profile headers
        if (path.contains("description") || identifier.contains("description")
                || path.contains("video_title") || identifier.contains("video_title")
                || path.contains("headline") || identifier.contains("headline")) {
            return false;
        }

        return path.contains("page_header")
                || path.contains("content_metadata")
                || path.contains("lockup_metadata")
                || path.contains("metadata_content")
                || path.contains("profile_card")
                || path.contains("profilecard")
                || path.contains("channel_profile")
                || path.contains("channel_header")
                || path.contains("channel_page")
                || path.contains("channel_metadata")
                || path.contains("channel_about")
                || path.contains("about_channel")
                || path.contains("profile_sheet")
                || path.contains("channel_sheet")
                || path.contains("cairo_channel_header")
                || path.contains("preview")
                || path.contains("channel_preview")
                || path.contains("channelpreview")
                || path.contains("user_card")
                || path.contains("user_preview")
                || path.contains("channel_summary")
                || path.contains("channel_info")
                || path.contains("channel_details")
                || identifier.contains("page_header")
                || identifier.contains("content_metadata")
                || identifier.contains("lockup_metadata")
                || identifier.contains("metadata_content")
                || identifier.contains("profile_card")
                || identifier.contains("profilecard")
                || identifier.contains("channel_profile")
                || identifier.contains("channel_header")
                || identifier.contains("channel_page")
                || identifier.contains("channel_metadata")
                || identifier.contains("channel_about")
                || identifier.contains("about_channel")
                || identifier.contains("profile_sheet")
                || identifier.contains("channel_sheet")
                || identifier.contains("cairo_channel_header")
                || identifier.contains("preview")
                || identifier.contains("channel_preview")
                || identifier.contains("channelpreview")
                || identifier.contains("user_card")
                || identifier.contains("user_preview")
                || identifier.contains("channel_summary")
                || identifier.contains("channel_info")
                || identifier.contains("channel_details");
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
        SpannableString result = new SpannableString(target);
        Spanned spanned = (Spanned) source;
        Object[] spans = spanned.getSpans(0, spanned.length(), Object.class);
        int targetLen = target.length();
        for (Object span : spans) {
            try {
                result.setSpan(span, 0, targetLen, spanned.getSpanFlags(span));
            } catch (Exception ignored) {
            }
        }
        return result;
    }
}
