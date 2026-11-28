package app.morphe.extension.youtube.patches.returnyoutubeusername;

import android.view.View;

import app.morphe.extension.shared.patches.LithoRelayoutPatch;

/**
 * RelayoutSpan marker attached to Litho text while a channel handle fetch is in flight.
 * Kept in a separate class so older Morphe builds without LithoRelayoutPatch never trigger
 * NoClassDefFoundError when loading ReturnYouTubeUsernamePatch or ChannelResolver.
 */
public final class PendingNameSpan implements LithoRelayoutPatch.RelayoutSpan {

    private final String handle;

    public PendingNameSpan(String handle) {
        this.handle = handle;
    }

    @Override
    public boolean isOutdated() {
        return !ChannelResolver.isPending(handle);
    }

    @Override
    public boolean canRelayoutHost(View host) {
        return true;
    }

    /**
     * Triggers Morphe's LithoRelayoutPatch to relayout views whose PendingNameSpan is outdated.
     * Caller must check ChannelResolver.isLithoRelayoutAvailable() before invoking.
     */
    public static void triggerRelayout() {
        LithoRelayoutPatch.relayoutOutdatedTexts();
    }
}
