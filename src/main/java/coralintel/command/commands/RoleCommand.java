package coralintel.command.commands;

import coralintel.command.Command;
import coralintel.ui.intel.PlayerRole;
import coralintel.ui.intel.RoleManager;
import coralintel.util.ChatUtil;
import net.minecraft.client.Minecraft;

public class RoleCommand extends Command {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public RoleCommand() {
        super("role");
    }

    @Override
    public void execute(String[] args) {
        RoleManager rm = RoleManager.getInstance();
        String myName = mc.getSession().getUsername();
        
        if (args.length == 0) {
            ChatUtil.sendFormatted("&7[Role] Usage:");
            ChatUtil.sendFormatted("  &e.role list &7- Show all players with roles");
            ChatUtil.sendFormatted("  &e.role set <player> <role> &7- Assign role (OWNER only)");
            ChatUtil.sendFormatted("  &e.role remove <player> &7- Remove role (OWNER only)");
            ChatUtil.sendFormatted("  &e.role check <player> &7- Check player's role");
            ChatUtil.sendFormatted("&7Roles: &cOWNER &bBETA &aFRIEND &7USER");
            return;
        }
        
        String subCmd = args[0].toLowerCase();
        
        switch (subCmd) {
            case "reload":
            case "refresh":
                rm.load();
                ChatUtil.sendFormatted("&a[Role] Reloaded roles from file");
                ChatUtil.sendFormatted("&7Total roles: " + rm.getAllRoles().size());
                // Show if you're an owner
                if (rm.isOwner(myName)) {
                    ChatUtil.sendFormatted("&a[Role] You are an &cOWNER");
                } else {
                    ChatUtil.sendFormatted("&7[Role] You are not an owner");
                }
                break;
            
            case "list":
                ChatUtil.sendFormatted("&7[Role] Players with roles:");
                java.util.Map<String, PlayerRole> roles = rm.getAllRoles();
                if (roles.isEmpty()) {
                    ChatUtil.sendFormatted("  &7No roles assigned yet");
                } else {
                    for (java.util.Map.Entry<String, PlayerRole> entry : roles.entrySet()) {
                        PlayerRole role = entry.getValue();
                        String colorCode = getColorCode(role);
                        ChatUtil.sendFormatted("  " + colorCode + entry.getKey() + " &7- " + colorCode + role.getName());
                    }
                }
                break;
                
            case "set":
                if (args.length < 3) {
                    ChatUtil.sendFormatted("&c[Role] Usage: .role set <player> <role>");
                    ChatUtil.sendFormatted("&7Available roles: OWNER, BETA, FRIEND, USER");
                    return;
                }
                
                String targetPlayer = args[1];
                String roleStr = args[2].toUpperCase();
                
                // Check if user is owner
                if (!rm.isOwner(myName)) {
                    ChatUtil.sendFormatted("&c[Role] Only owners can assign roles!");
                    return;
                }
                
                // Parse role
                PlayerRole role = PlayerRole.fromString(roleStr);
                if (role == null) {
                    ChatUtil.sendFormatted("&c[Role] Invalid role: " + roleStr);
                    ChatUtil.sendFormatted("&7Available roles: OWNER, BETA, FRIEND, USER");
                    return;
                }
                
                // Special handling for OWNER role
                if (role == PlayerRole.OWNER) {
                    rm.addOwner(targetPlayer);
                    ChatUtil.sendFormatted("&a[Role] Added &c" + targetPlayer + " &aas &cOWNER");
                } else {
                    boolean success = rm.assignRole(myName, targetPlayer, role);
                    if (success) {
                        String colorCode = getColorCode(role);
                        ChatUtil.sendFormatted("&a[Role] Set " + colorCode + targetPlayer + " &ato " + colorCode + role.getName());
                    } else {
                        ChatUtil.sendFormatted("&c[Role] Failed to assign role (not authorized)");
                    }
                }
                break;
                
            case "remove":
                if (args.length < 2) {
                    ChatUtil.sendFormatted("&c[Role] Usage: .role remove <player>");
                    return;
                }
                
                if (!rm.isOwner(myName)) {
                    ChatUtil.sendFormatted("&c[Role] Only owners can remove roles!");
                    return;
                }
                
                String playerToRemove = args[1];
                boolean removed = rm.assignRole(myName, playerToRemove, null);
                if (removed) {
                    ChatUtil.sendFormatted("&a[Role] Removed role from &7" + playerToRemove);
                } else {
                    ChatUtil.sendFormatted("&c[Role] Failed to remove role (not authorized)");
                }
                break;
                
            case "check":
                if (args.length < 2) {
                    ChatUtil.sendFormatted("&c[Role] Usage: .role check <player>");
                    return;
                }
                
                String checkPlayer = args[1];
                PlayerRole checkRole = rm.getRole(checkPlayer);
                if (checkRole != null) {
                    String colorCode = getColorCode(checkRole);
                    ChatUtil.sendFormatted("&7[Role] " + colorCode + checkPlayer + " &7has role: " + colorCode + checkRole.getName());
                } else {
                    ChatUtil.sendFormatted("&7[Role] " + checkPlayer + " has no role");
                }
                break;
                
            default:
                ChatUtil.sendFormatted("&c[Role] Unknown subcommand: " + subCmd);
                ChatUtil.sendFormatted("&7Use &e.role &7for help");
                break;
        }
    }
    
    private String getColorCode(PlayerRole role) {
        switch (role) {
            case OWNER: return "&c";
            case BETA: return "&b";
            case FRIEND: return "&a";
            case USER: return "&7";
            default: return "&7";
        }
    }
}
