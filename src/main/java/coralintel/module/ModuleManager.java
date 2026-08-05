package coralintel.module;

import coralintel.CoralIntel;
import coralintel.event.EventTarget;
import coralintel.events.KeyEvent;
import coralintel.util.ChatUtil;

import java.util.LinkedHashMap;

/**
 * Trimmed from the original client's ModuleManager.
 * Generic registry + key-toggle dispatch, without the sound/HUD-alert coupling
 * (CoralIntel doesn't ship the HUD module from the original client).
 */
public class ModuleManager {
    public final LinkedHashMap<Class<?>, Module> modules = new LinkedHashMap<>();

    public Module getModule(String name) {
        return this.modules.values().stream()
                .filter(m -> m.getName().equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }

    public Module getModule(Class<?> clazz) {
        return this.modules.get(clazz);
    }

    @EventTarget
    public void onKey(KeyEvent event) {
        for (Module module : this.modules.values()) {
            if (module.getKey() != event.getKey() || module.getKey() == 0) {
                continue;
            }
            boolean toggled = module.toggle();
            if (toggled) {
                String status = module.isEnabled() ? "&a&lON" : "&c&lOFF";
                ChatUtil.sendFormatted(String.format("%s%s: %s&r", CoralIntel.clientName, module.getName(), status));
            }
        }
    }
}
