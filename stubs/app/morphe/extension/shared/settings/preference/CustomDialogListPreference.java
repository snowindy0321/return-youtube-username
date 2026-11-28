package app.morphe.extension.shared.settings.preference;

import android.content.Context;
import android.preference.ListPreference;
import android.util.AttributeSet;

@SuppressWarnings("deprecation")
public class CustomDialogListPreference extends ListPreference {

    public CustomDialogListPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public CustomDialogListPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public CustomDialogListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CustomDialogListPreference(Context context) {
        super(context);
    }
}
