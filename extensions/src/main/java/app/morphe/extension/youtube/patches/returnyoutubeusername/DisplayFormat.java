package app.morphe.extension.youtube.patches.returnyoutubeusername;

public final class DisplayFormat {
    /**
     * Shows only the original channel name (e.g. MrBeast).
     */
    public static final int USERNAME_ONLY = 0;

    /**
     * Shows username first followed by handle in parentheses (e.g. MrBeast (@mrbeast)).
     */
    public static final int USERNAME_HANDLE = 1;

    /**
     * Shows handle first followed by username in parentheses (e.g. @mrbeast (MrBeast)).
     */
    public static final int HANDLE_USERNAME = 2;

    private DisplayFormat() {}
}
