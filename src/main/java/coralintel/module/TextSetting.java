package coralintel.module;

/** A free-text setting (used by script modules; shown in the ClickGUI as a click-to-edit box). */
public class TextSetting extends Setting {
    private final int maxLength;
    private String value;

    public TextSetting(String name, String defaultValue, int maxLength) {
        super(name);
        this.maxLength = maxLength;
        this.value = clip(defaultValue);
    }

    public String getValue() {
        return value;
    }

    public int getMaxLength() {
        return maxLength;
    }

    public void setValue(String v) {
        value = clip(v);
    }

    private String clip(String s) {
        if (s == null) return "";
        return s.length() > maxLength ? s.substring(0, maxLength) : s;
    }
}
