package coralintel.script;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import coralintel.module.BooleanSetting;
import coralintel.module.Module;
import coralintel.module.Setting;
import coralintel.module.SliderSetting;
import coralintel.module.TextSetting;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

/**
 * A module registered by a script. It is a normal Module (so the ClickGUI treats it like any
 * built-in one) built from the same setting classes. The setting subclasses below report every
 * change back so it can be saved and handed to the script.
 */
public final class ScriptModule extends Module {
    public static final int TEXT_MAX_LENGTH = 100;

    final ScriptInstance owner;
    private final String description;
    LuaValue onEnable;
    LuaValue onDisable;
    LuaValue onChange;
    private boolean silent;

    ScriptModule(ScriptInstance owner, String name, String description, boolean enabled) {
        super(name, enabled);
        this.owner = owner;
        this.description = description == null ? "" : description;
    }

    public String getDescription() {
        return description;
    }

    // -- Building ---------------------------------------------------------

    void addToggle(String name, boolean def) {
        register(new ToggleS(name, def));
    }

    void addSlider(String name, double def, double min, double max, double step) {
        register(new SliderS(name, Math.max(min, Math.min(max, def)), min, max, step));
    }

    void addText(String name, String def) {
        register(new TextS(name, def));
    }

    public Setting find(String name) {
        for (Setting s : getSettings()) {
            if (s.getName().equals(name)) return s;
        }
        return null;
    }

    // -- Script-facing access ---------------------------------------------

    LuaValue valueOf(Setting s) {
        if (s instanceof BooleanSetting) return LuaValue.valueOf(((BooleanSetting) s).getValue());
        if (s instanceof SliderSetting) return LuaValue.valueOf(((SliderSetting) s).getValue());
        if (s instanceof TextSetting) return LuaValue.valueOf(((TextSetting) s).getValue());
        return LuaValue.NIL;
    }

    /** m.set(): the script changed it itself, so it is not echoed back through onChange. */
    void setFromScript(String name, LuaValue v) {
        Setting s = find(name);
        if (s == null) throw new LuaError("unknown setting '" + name + "'");
        silent = true;
        try {
            if (s instanceof BooleanSetting) {
                ((BooleanSetting) s).setValue(v.checkboolean());
            } else if (s instanceof SliderSetting) {
                ((SliderSetting) s).setValue(v.checkdouble());
            } else if (s instanceof TextSetting) {
                ((TextSetting) s).setValue(v.checkjstring());
            }
        } finally {
            silent = false;
        }
    }

    /** Restores a saved value without notifying anyone. */
    void loadValue(Setting s, JsonElement saved) {
        silent = true;
        try {
            if (!saved.isJsonPrimitive()) return;
            JsonPrimitive p = saved.getAsJsonPrimitive();
            if (s instanceof BooleanSetting && p.isBoolean()) {
                ((BooleanSetting) s).setValue(p.getAsBoolean());
            } else if (s instanceof SliderSetting && p.isNumber()) {
                ((SliderSetting) s).setValue(p.getAsDouble());
            } else if (s instanceof TextSetting && p.isString()) {
                ((TextSetting) s).setValue(p.getAsString());
            }
        } catch (RuntimeException ignored) {
            // bad saved value: keep the default
        } finally {
            silent = false;
        }
    }

    /** Sets the on/off flag from saved state, without firing onEnable/onDisable. */
    void applyEnabled(boolean on) {
        this.enabled = on;
    }

    // -- Change reporting -------------------------------------------------

    @Override
    public void setEnabled(boolean on) {
        boolean was = isEnabled();
        super.setEnabled(on);
        if (was != isEnabled()) owner.mgr.moduleToggled(this);
    }

    private void changed(Setting s) {
        if (silent) owner.mgr.markStateDirty();
        else owner.mgr.settingChanged(this, s);
    }

    private final class ToggleS extends BooleanSetting {
        ToggleS(String name, boolean def) {
            super(name, def);
        }

        @Override
        public void setValue(boolean v) {
            boolean old = getValue();
            super.setValue(v);
            if (old != getValue()) changed(this);
        }

        @Override
        public void toggle() {
            setValue(!getValue());
        }
    }

    private final class SliderS extends SliderSetting {
        SliderS(String name, double def, double min, double max, double step) {
            super(name, def, min, max, step);
        }

        @Override
        public void setValue(double v) {
            double old = getValue();
            super.setValue(v);
            if (old != getValue()) changed(this);
        }
    }

    private final class TextS extends TextSetting {
        TextS(String name, String def) {
            super(name, def, TEXT_MAX_LENGTH);
        }

        @Override
        public void setValue(String v) {
            String old = getValue();
            super.setValue(v);
            if (!old.equals(getValue())) changed(this);
        }
    }
}
