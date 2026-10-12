package coralintel.script;

import coralintel.command.Command;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything the scripting system needs from the game, behind one interface. The scripting core
 * never touches Minecraft directly, so it can be exercised without the game; MinecraftHost is
 * the real implementation. All methods are called on the game's main thread.
 */
public interface ScriptHost {
    /** Shows a client-side chat line ("&" colour codes). Never sends anything to the server. */
    void chat(String formatted);

    /** False until the player exists (chat lines would be dropped). */
    boolean canChat();

    void sound(String name);

    /** Names of the players the lobby tracker knows about. */
    List<String> lobbyPlayers();

    /** Plain copy of one lobby player's data, or null when unknown. */
    Map<String, Object> lobbyPlayer(String name);

    /** Suspicion score, tier and reasons for one lobby player, or null when unknown. */
    Map<String, Object> suspicion(String name);

    /** Your session numbers (see SessionStats.scriptSnapshot). */
    Map<String, Object> session();

    /** Your own in-game name. */
    String ownName();

    /** Real (non-NPC) names currently in the tab list. */
    Set<String> tabNames();

    void drawText(String text, int x, int y, int color);

    boolean commandExists(String name);

    void registerCommand(Command command);

    void unregisterCommand(Command command);

    /** True when a built-in module or ClickGUI panel already uses this name. */
    boolean moduleNameTaken(String name);

    /** Best effort: open the folder in the system file manager. */
    void openFolder(File dir);
}
