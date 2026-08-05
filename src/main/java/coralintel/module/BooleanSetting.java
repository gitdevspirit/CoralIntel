package coralintel.module;

import java.util.function.BooleanSupplier;

public class BooleanSetting extends Setting {
    private boolean value;

    public BooleanSetting(String name, boolean defaultValue) {
        super(name);
        this.value = defaultValue;
    }

    public BooleanSetting(String name, boolean defaultValue, BooleanSupplier visibility) {
        super(name, visibility);
        this.value = defaultValue;
    }

    public boolean getValue() { return value; }
    public void setValue(boolean v) { value = v; }
    public void toggle() { value = !value; }
}
