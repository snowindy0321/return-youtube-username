package app.morphe.extension.shared.settings;

public class IntegerSetting extends Setting<Integer> {
    public IntegerSetting(String key, Integer defaultValue) {}
    public IntegerSetting(String key, Integer defaultValue, Availability availability) {}
    @Override
    public Integer get() {
        return 0;
    }
}
