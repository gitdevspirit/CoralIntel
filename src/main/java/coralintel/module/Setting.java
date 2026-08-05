package coralintel.module;

import java.util.function.BooleanSupplier;

public abstract class Setting {
    protected final String name;
    private final BooleanSupplier visibility;

    public Setting(String name) {
        this(name, null);
    }

    protected Setting(String name, BooleanSupplier visibility) {
        this.name = name;
        this.visibility = visibility;
    }

    public String getName() {
        return name;
    }

    public boolean isVisible() {
        return visibility == null || visibility.getAsBoolean();
    }
}
