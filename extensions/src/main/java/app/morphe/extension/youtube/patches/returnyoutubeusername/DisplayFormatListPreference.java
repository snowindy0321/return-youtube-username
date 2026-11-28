package app.morphe.extension.youtube.patches.returnyoutubeusername;

import android.content.Context;
import android.util.AttributeSet;

import java.util.Locale;

import app.morphe.extension.shared.settings.preference.CustomDialogListPreference;

/**
 * Display format selector backed by Morphe's CustomDialogListPreference.
 * Supplies localized entries and numeric entryValues ("0", "1", "2") programmatically
 * to avoid requiring custom @array XML resource IDs in third-party .mpp bundles.
 */
@SuppressWarnings({"deprecation", "unused"})
public class DisplayFormatListPreference extends CustomDialogListPreference {

    private static final CharSequence[] ENTRY_VALUES = new CharSequence[]{"0", "1", "2"};

    public DisplayFormatListPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public DisplayFormatListPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public DisplayFormatListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DisplayFormatListPreference(Context context) {
        super(context);
        init();
    }

    private void init() {
        setEntries(getLocalizedEntries());
        setEntryValues(ENTRY_VALUES);
    }

    public static CharSequence[] getLocalizedEntries() {
        Locale loc = Locale.getDefault();
        if (loc != null && "zh".equalsIgnoreCase(loc.getLanguage())) {
            String country = loc.getCountry();
            String script = loc.getScript();
            boolean isZhTW = "TW".equalsIgnoreCase(country)
                    || "HK".equalsIgnoreCase(country)
                    || "MO".equalsIgnoreCase(country)
                    || "Hant".equalsIgnoreCase(script);
            if (isZhTW) {
                return new CharSequence[]{
                        "僅頻道名稱",
                        "頻道名稱 (@代號)",
                        "@代號 (頻道名稱)"
                };
            }
            return new CharSequence[]{
                    "仅频道名称",
                    "频道名称 (@用户名)",
                    "@用户名 (频道名称)"
            };
        }
        return new CharSequence[]{
                "Channel name",
                "Channel name (@handle)",
                "@handle (Channel name)"
        };
    }
}
