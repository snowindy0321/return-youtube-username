package app.morphe.extension.shared.settings;

public class BooleanSetting extends Setting<Boolean> {
    public BooleanSetting(String key, Boolean defaultValue) {}
    public BooleanSetting(String key, Boolean defaultValue, Availability availability) {}
    @Override
    public Boolean get() {
        return false;
    }
}
