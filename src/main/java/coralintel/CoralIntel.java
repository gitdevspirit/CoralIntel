package coralintel;

import coralintel.command.CommandManager;
import coralintel.command.commands.AddIntelPlayerCommand;
import coralintel.command.commands.BedwarsStatsCommand;
import coralintel.command.commands.BindCommand;
import coralintel.command.commands.IntelDebugCommand;
import coralintel.command.commands.IntelKeyCommand;
import coralintel.command.commands.IntelPathCommand;
import coralintel.command.commands.RemoveIntelPlayerCommand;
import coralintel.command.commands.RoleCommand;
import coralintel.command.commands.UrchinKeyCommand;
import coralintel.config.Config;
import coralintel.event.EventManager;
import coralintel.module.Module;
import coralintel.module.ModuleManager;
import coralintel.module.modules.LobbyIntel;
import coralintel.module.modules.BedwarsTag;
import coralintel.render.RenderEventBridge;
import net.minecraftforge.common.MinecraftForge;
import coralintel.property.Property;
import coralintel.property.PropertyManager;

import java.lang.reflect.Field;
import java.util.ArrayList;

/**
 * CoralIntel — a standalone extraction of the LobbyIntel / tab-overlay / HUD-overlay /
 * .coralkey / .intelkey / .bw system from the original "Spirit" client.
 *
 * Everything combat-related (KillAura, ESP, Scaffold, anti-cheat evasion, etc.) from the
 * original has been deliberately left out — this is just the lobby-scouting stats overlay.
 *
 * This class replaces the original's Myau.java hub. It's injected the same way, via
 * MixinMinecraft's postStartGame hook, right after Minecraft.startGame() returns.
 */
public class CoralIntel {
    public static final String clientName = "&7[&bCoralIntel&7]&r ";
    public static String version;

    public static PropertyManager propertyManager;
    public static ModuleManager moduleManager;
    public static CommandManager commandManager;

    public CoralIntel() {
        this.init();
    }

    public void init() {
        propertyManager = new PropertyManager();
        moduleManager = new ModuleManager();

        commandManager = new CommandManager();
        commandManager.register(new BindCommand());
        commandManager.register(new UrchinKeyCommand());
        commandManager.register(new IntelKeyCommand());
        commandManager.register(new AddIntelPlayerCommand());
        commandManager.register(new RemoveIntelPlayerCommand());
        commandManager.register(new IntelDebugCommand());
        commandManager.register(new IntelPathCommand());
        commandManager.register(new RoleCommand());
        commandManager.register(new BedwarsStatsCommand());
        EventManager.register(commandManager);
        EventManager.register(moduleManager);

        // Only two modules in this standalone build.
        moduleManager.modules.put(LobbyIntel.class, new LobbyIntel());
        moduleManager.modules.put(BedwarsTag.class, new BedwarsTag());
        MinecraftForge.EVENT_BUS.register(new RenderEventBridge());

        // Reflection scan: pick up every Property<?> field declared on each module
        // and register it with the PropertyManager, same as the original client did.
        for (Module module : moduleManager.modules.values()) {
            ArrayList<Property<?>> properties = new ArrayList<>();

            for (Field field : module.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                final Object value;
                try {
                    value = field.get(module);
                } catch (IllegalAccessException exception) {
                    throw new RuntimeException(exception);
                }

                if (value instanceof Property<?>) {
                    ((Property<?>) value).setOwner(module);
                    properties.add((Property<?>) value);
                }
            }

            propertyManager.properties.put(module.getClass(), properties);
            EventManager.register(module);
        }

        Config config = new Config("default", true);
        if (config.file.exists()) {
            config.load();
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                LobbyIntel lobbyIntel = (LobbyIntel) moduleManager.getModule("LobbyIntel");
                if (lobbyIntel != null) {
                    lobbyIntel.saveHudSettings();
                }
            } catch (Exception ignored) {
            }
            config.save();
        }));

        version = "1.0";
    }
}
