package coralintel.ui.intel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages player roles for the Spirit client
 * Only owners can assign roles
 */
public class RoleManager {
    private static RoleManager instance;
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    private final File rolesFile;
    private final Map<String, PlayerRole> playerRoles; // UUID or name -> role
    private final Map<String, String> ownerList; // List of owner UUIDs
    
    private RoleManager() {
        this.rolesFile = new File("./config/CoralIntel/roles.json");
        this.playerRoles = new HashMap<>();
        this.ownerList = new HashMap<>();
        load();
    }
    
    public static RoleManager getInstance() {
        if (instance == null) {
            instance = new RoleManager();
        }
        return instance;
    }
    
    /**
     * Check if a player is an owner
     */
    public boolean isOwner(String playerNameOrUuid) {
        return ownerList.containsKey(playerNameOrUuid.toLowerCase());
    }
    
    /**
     * Get a player's role
     */
    public PlayerRole getRole(String playerNameOrUuid) {
        String key = playerNameOrUuid.toLowerCase();
        PlayerRole role = playerRoles.get(key);
        System.out.println("[RoleManager] getRole(" + playerNameOrUuid + ") -> key: " + key + " -> role: " + role);
        return role;
    }
    
    /**
     * Assign a role to a player (only owners can do this)
     */
    public boolean assignRole(String assignerUuid, String targetPlayer, PlayerRole role) {
        if (!isOwner(assignerUuid)) {
            return false; // Not authorized
        }
        
        if (role == null) {
            playerRoles.remove(targetPlayer.toLowerCase());
        } else {
            playerRoles.put(targetPlayer.toLowerCase(), role);
        }
        
        save();
        return true;
    }
    
    /**
     * Add an owner (can only be done programmatically or via config edit)
     */
    public void addOwner(String playerNameOrUuid) {
        String key = playerNameOrUuid.toLowerCase();
        ownerList.put(key, playerNameOrUuid);
        playerRoles.put(key, PlayerRole.OWNER);
        System.out.println("[RoleManager] Added owner: " + playerNameOrUuid + " (key: " + key + ")");
        save();
    }
    
    /**
     * Load roles from file
     */
    public void load() {
        if (!rolesFile.exists()) {
            // Create default - add 999Spirit as owner
            addOwner("999Spirit");
            // Save immediately to create the file
            save();
            return;
        }
        
        try (Reader reader = new InputStreamReader(new FileInputStream(rolesFile), StandardCharsets.UTF_8)) {
            Map<String, Object> data = gson.fromJson(reader, new TypeToken<Map<String, Object>>(){}.getType());
            
            if (data == null) {
                addOwner("999Spirit");
                save();
                return;
            }
            
            // Load owners
            if (data.containsKey("owners")) {
                Map<String, String> owners = (Map<String, String>) data.get("owners");
                ownerList.putAll(owners);
                // Owners automatically get OWNER role
                for (String owner : owners.keySet()) {
                    playerRoles.put(owner.toLowerCase(), PlayerRole.OWNER);
                }
            } else {
                // No owners in file, add 999Spirit
                addOwner("999Spirit");
            }
            
            // Load roles
            if (data.containsKey("roles")) {
                Map<String, String> roles = (Map<String, String>) data.get("roles");
                for (Map.Entry<String, String> entry : roles.entrySet()) {
                    PlayerRole role = PlayerRole.fromString(entry.getValue());
                    if (role != null) {
                        playerRoles.put(entry.getKey().toLowerCase(), role);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load roles: " + e.getMessage());
            addOwner("999Spirit");
            save();
        }
    }
    
    /**
     * Save roles to file
     */
    public void save() {
        try {
            rolesFile.getParentFile().mkdirs();
            
            Map<String, Object> data = new HashMap<>();
            data.put("owners", ownerList);
            
            // Save non-owner roles
            Map<String, String> rolesToSave = new HashMap<>();
            for (Map.Entry<String, PlayerRole> entry : playerRoles.entrySet()) {
                if (entry.getValue() != PlayerRole.OWNER) { // Owners are in owners list
                    rolesToSave.put(entry.getKey(), entry.getValue().getName());
                }
            }
            data.put("roles", rolesToSave);
            
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(rolesFile), StandardCharsets.UTF_8)) {
                gson.toJson(data, writer);
            }
        } catch (Exception e) {
            System.err.println("Failed to save roles: " + e.getMessage());
        }
    }
    
    /**
     * Get all players with roles (for display in GUI)
     */
    public Map<String, PlayerRole> getAllRoles() {
        return new HashMap<>(playerRoles);
    }
}
