package app.morphe.extension.shared.patches;

import android.view.View;

public final class LithoRelayoutPatch {
    public interface RelayoutSpan {
        boolean isOutdated();
        default boolean canRelayoutHost(View host) {
            return true;
        }
    }

    public static void relayoutOutdatedTexts() {}
}
