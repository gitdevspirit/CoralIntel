package coralintel.ui.intel;

/**
 * Player roles for the Spirit client community
 */
public enum PlayerRole {
    OWNER("OWNER", 0xFFFF5555, "⚜"),      // Red with crown
    BETA("BETA", 0xFF55BBFF, "β"),        // Light blue with beta symbol
    FRIEND("FRIEND", 0xFF55FF55, "♥"),    // Green with heart
    USER("USER", 0xFFAAAAAA, "✓");        // Gray with checkmark
    
    private final String name;
    private final int color;
    private final String icon;
    
    PlayerRole(String name, int color, String icon) {
        this.name = name;
        this.color = color;
        this.icon = icon;
    }
    
    public String getName() {
        return name;
    }
    
    public int getColor() {
        return color;
    }
    
    public String getIcon() {
        return icon;
    }
    
    public static PlayerRole fromString(String str) {
        if (str == null) return null;
        for (PlayerRole role : values()) {
            if (role.name.equalsIgnoreCase(str)) {
                return role;
            }
        }
        return null;
    }
}
