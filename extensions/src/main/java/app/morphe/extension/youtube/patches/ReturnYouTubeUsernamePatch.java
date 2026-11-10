package app.morphe.extension.youtube.patches;

import android.text.SpannableString;
import android.text.Spanned;

import java.util.Locale;
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
 * across comments, Shorts uploaders, and handles throughout the app.
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

    public static final BooleanSetting REPLACE_ALL_HANDLES =
            new BooleanSetting("morphe_return_youtube_username_replace_all_handles", true);

    // YouTube handle regex: starts with @ followed by Unicode letters/numbers/marks and punctuation allowed in handles
    private static final Pattern HANDLE_PATTERN =
            Pattern.compile("^@[^\\s/@#%&?+:;!\"'()\\[\\]{}<>=]+$");

    /**
     * Pre-fetch hook called when SpannableString is being built in the background.
     */
    public static void preFetchLithoText(ContextInterface contextInterface,
                                         CharSequence original) {
        try {
            if (!ENABLED.get() || original == null) {
                return;
            }

            String text = stripWhitespace(original.toString());
            if (isEligibleHandle(contextInterface, text)) {
                ChannelResolver.prefetch(text, API_KEY.get());
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

            String text = stripWhitespace(original.toString());
            if (!isEligibleHandle(contextInterface, text)) {
                return original;
            }

            int format = DISPLAY_FORMAT.get();
            String resolved = ChannelResolver.getOrResolve(text, API_KEY.get(), format);

            if (resolved != null && !resolved.isEmpty() && !resolved.equals(text)) {
                return newSpanUsingStylingOfAnotherSpan(original, resolved);
            }
        } catch (Exception ex) {
            Logger.printException(() -> "ReturnYouTubeUsername onLithoTextLoaded failed", ex);
        }
        return original;
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
            String path = "";
            try {
                StringBuilder pb = contextInterface.patch_getPathBuilder();
                if (pb != null) path = pb.toString().toLowerCase(Locale.ENGLISH);
            } catch (Exception ignored) {}

            String identifier = "";
            try {
                String id = contextInterface.patch_getIdentifier();
                if (id != null) identifier = id.toLowerCase(Locale.ENGLISH);
            } catch (Exception ignored) {}

            // Priority 1: Channel profile header / bottom sheet (Image 2 vs Image 3)
            // Under a channel's header/profile, the handle item (e.g. @iamvaca8787 under the channel name)
            // is specifically meant to show the channel's @handle and must NOT be replaced with the channel name again.
            if (isChannelProfileHeader(path, identifier)) {
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
        }

        return REPLACE_ALL_HANDLES.get();
    }

    private static boolean isChannelProfileHeader(String path, String identifier) {
        if (path == null) path = "";
        if (identifier == null) identifier = "";

        return path.contains("page_header")
                || path.contains("content_metadata")
                || path.contains("profile_card")
                || path.contains("profilecard")
                || path.contains("channel_profile")
                || path.contains("channel_header")
                || path.contains("channel_page")
                || path.contains("channel_metadata")
                || path.contains("channel_about")
                || path.contains("about_channel")
                || path.contains("profile_sheet")
                || path.contains("bottom_sheet_header")
                || path.contains("cairo_channel_header")
                || identifier.contains("page_header")
                || identifier.contains("content_metadata")
                || identifier.contains("profile_card")
                || identifier.contains("profilecard")
                || identifier.contains("channel_profile")
                || identifier.contains("channel_header")
                || identifier.contains("channel_page")
                || identifier.contains("channel_metadata")
                || identifier.contains("channel_about")
                || identifier.contains("about_channel")
                || identifier.contains("profile_sheet")
                || identifier.contains("bottom_sheet_header")
                || identifier.contains("cairo_channel_header");
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
