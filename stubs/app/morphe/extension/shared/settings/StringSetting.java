package app.morphe.extension.shared.settings;

public class StringSetting extends Setting<String> {
    public StringSetting(String key, String defaultValue) {}
    public StringSetting(String key, String defaultValue, Availability availability) {}
    @Override
    public String get() {
        return "";
    }
}
