package coralintel.module;

import java.util.ArrayList;
import java.util.List;
import coralintel.util.KeyBindUtil;

/**
 * Base class for a toggleable module with settings.
 * Trimmed from the original: no toggle-sound / notification-toast coupling,
 * since CoralIntel doesn't ship the HUD/Notifications modules from the original client.
 */
public abstract class Module {
    protected final String name;
    protected final boolean defaultEnabled;
    protected final int defaultKey;
    protected final boolean defaultHidden;
    protected boolean enabled;
    protected int key;
    protected boolean hidden;
    protected final List<Setting> settings;

    public Module(String name, boolean enabled) {
        this(name, enabled, false);
    }

    public Module(String name, boolean enabled, boolean hidden) {
        this.settings = new ArrayList<>();
        this.name = name;
        this.enabled = this.defaultEnabled = enabled;
        this.key = this.defaultKey = 0;
        this.hidden = this.defaultHidden = hidden;
    }

    protected <T extends Setting> T register(T setting) {
        this.settings.add(setting);
        return setting;
    }

    public List<Setting> getSettings() {
        return this.settings;
    }

    public String getName() {
        return this.name;
    }

    public String formatModule() {
        return String.format("%s%s &r(%s&r)",
                this.key == 0 ? "" : String.format("&l[%s] &r", KeyBindUtil.getKeyName(this.key)),
                this.name, this.enabled ? "&a&lON" : "&c&lOFF");
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;
            if (enabled) {
                this.onEnabled();
            } else {
                this.onDisabled();
            }
        }
    }

    public boolean toggle() {
        boolean target = !this.enabled;
        this.setEnabled(target);
        return this.enabled == target;
    }

    public int getKey() {
        return this.key;
    }

    public void setKey(int key) {
        this.key = key;
    }

    public boolean isHidden() {
        return this.hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public void onEnabled() {
    }

    public void onDisabled() {
    }

    public void verifyValue(String string) {
    }
}
