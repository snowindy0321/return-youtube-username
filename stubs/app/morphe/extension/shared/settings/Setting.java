package app.morphe.extension.shared.settings;

import java.util.List;

public abstract class Setting<T> {
    public abstract T get();

    public interface Availability {
        boolean isAvailable();
        List<Setting<?>> getParentSettings();
    }

    public static Availability parent(BooleanSetting parent) {
        return null;
    }
}
