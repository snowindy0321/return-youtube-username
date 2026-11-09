package app.morphe.extension.youtube.patches;

import android.text.SpannableString;
import android.text.Spanned;

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

    // Standard YouTube handle regex: starts with @ followed by alphanumeric, dot, underscore, hyphen
    private static final Pattern HANDLE_PATTERN = Pattern.compile("^@[a-zA-Z0-9._-]+$");

    /**
     * Pre-fetch hook called when SpannableString is being built in the background.
     */
    public static void preFetchLithoText(ContextInterface contextInterface,
                                         CharSequence original) {
        try {
            if (!ENABLED.get() || original == null) {
                return;
            }

            String text = original.toString().trim();
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

            String text = original.toString().trim();
            if (!isEligibleHandle(contextInterface, text)) {
                return original;
            }

            int format = DISPLAY_FORMAT.get();
            String resolved = ChannelResolver.getOrResolve(text, API_KEY.get(), format);

            if (resolved != null && !resolved.equals(text)) {
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
                if (pb != null) path = pb.toString();
            } catch (Exception ignored) {}

            String identifier = "";
            try {
                String id = contextInterface.patch_getIdentifier();
                if (id != null) identifier = id;
            } catch (Exception ignored) {}

            boolean isComments = path.contains("author_badge") || path.contains("comment") || identifier.contains("comment");
            if (isComments) {
                return REPLACE_COMMENTS.get();
            }

            boolean isShorts = path.contains("reel_channel_bar") || path.contains("reel_player_overlay") || path.contains("reel") || identifier.contains("reel");
            if (isShorts) {
                return REPLACE_SHORTS.get();
            }
        }

        return REPLACE_ALL_HANDLES.get();
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
