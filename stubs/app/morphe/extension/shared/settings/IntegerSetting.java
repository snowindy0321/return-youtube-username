package app.morphe.extension.shared.settings;

public class IntegerSetting extends Setting<Integer> {
    public IntegerSetting(String key, Integer defaultValue) {}
    @Override
    public Integer get() {
        return 0;
    }
}
