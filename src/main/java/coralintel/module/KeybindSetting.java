package coralintel.module;

import coralintel.util.KeyBindUtil;

public class KeybindSetting extends Setting {
    private int keyCode;
    private boolean listening; // true while waiting for a key press

    public KeybindSetting(String name, int defaultKey) {
        super(name);
        this.keyCode   = defaultKey;
        this.listening = false;
    }

    public int getKeyCode()      { return keyCode; }
    public void setKeyCode(int k){ keyCode = k; listening = false; }

    public boolean isListening()       { return listening; }
    public void startListening()       { listening = true; }
    public void cancelListening()      { listening = false; }

    public String getDisplayName() {
        if (listening) return "...";
        return keyCode == 0 ? "NONE" : KeyBindUtil.getKeyName(keyCode);
    }
}
