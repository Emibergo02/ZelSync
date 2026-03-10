package dev.unnm3d.zelsync;

import com.jonahseguin.drink.CommandService;
import com.jonahseguin.drink.Drink;
import de.exlll.configlib.ConfigLib;
import de.exlll.configlib.ConfigurationException;
import de.exlll.configlib.YamlConfigurations;
import dev.unnm3d.zelsync.api.ZelSyncAPI;
import dev.unnm3d.zelsync.api.ZelSyncProvider;
import dev.unnm3d.zelsync.commands.providers.TargetProvider;
import dev.unnm3d.zelsync.configs.GuiSettings;
import dev.unnm3d.zelsync.configs.Messages;
import dev.unnm3d.zelsync.configs.Settings;
import dev.unnm3d.zelsync.core.managers.PlayerListManager;
import dev.unnm3d.zelsync.listeners.SnapshotManager;
import dev.unnm3d.zelsync.redistools.RedisDataManager;
import dev.unnm3d.zelsync.utils.ExecutorServiceRouter;
import dev.unnm3d.zelsync.utils.Metrics;
import io.lettuce.core.RedisConnectionException;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Random;

public class ZelSync extends JavaPlugin implements ZelSyncAPI {
    @Getter
    private static final int serverId = new Random().nextInt();
    private static File debugFile;
    @Getter
    private static ZelSync instance;
    @Getter
    private RedisDataManager dataCache;
    @Getter
    private PlayerListManager playerListManager;
    @Getter
    private ExecutorServiceRouter executorServiceRouter;
    private Metrics metrics;

    public static void debug(String string) {
        if (Settings.instance().debug) {
            if (Settings.instance().debugToConsole) {
                getInstance().getLogger().info(string);
            }

            try {
                final FileWriter writer = new FileWriter(debugFile.getAbsoluteFile(), true);
                writer.append("[")
                  .append(String.valueOf(LocalDateTime.now()))
                  .append("] ")
                  .append(string);
                if (Settings.instance().debugStrace && Thread.currentThread().getStackTrace().length > 1) {
                    for (int i = 2; i < Math.min(Thread.currentThread().getStackTrace().length, 7); i++) {
                        writer.append("\n\t").append(Thread.currentThread().getStackTrace()[i].toString());
                    }
                }

                writer.append("\r\n");
                writer.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void onLoad() {
        instance = this;
        ZelSyncProvider.set(instance);

        loadDebugFile();
        loadYML();
    }

    @Override
    public void onEnable() {
        try {
            dataCache = new RedisDataManager(this);
        } catch (RedisConnectionException e) {
            getLogger().severe("Cannot connect to Redis server");
            getLogger().severe("Check your configuration and try again");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        //dataStorage = switch (settings.storageType) {
        //    case MYSQL -> new MySQLDatabase(this, this.settings.sqlDatabase);
        //    case POSTGRESQL -> new PostgresqlDatabase(this, this.settings.sqlDatabase);
        //    case SQLITE -> new SQLiteDatabase(this);
        //};
        //((Database) dataStorage).connect();
        this.playerListManager = new PlayerListManager(this);

        this.executorServiceRouter = new ExecutorServiceRouter(10);


        getServer().getPluginManager().registerEvents(new SnapshotManager(this), this);
        try {
            loadCommands();
        } catch (Exception e) {
            getLogger().severe("Error loading commands");
            getLogger().severe("Check your configuration and try again");
        }


        //bStats
        this.metrics = new Metrics(this, 28750);
        metrics.addCustomChart(new Metrics.SimplePie("player_count", () -> {
            int count = getServer().getOnlinePlayers().size();
            return count > 100 ? "100+" : count > 50 ? "50-100" : count > 20 ? "20-50" : count > 10 ? "10-20" : count > 5 ? "5-10" : "less than 5";
        }));
    }

    @Override
    public void onDisable() {
        executorServiceRouter.shutdown();
        if (metrics != null)
            metrics.shutdown();
        Drink.unregister(this);
        if (dataCache != null) {
            dataCache.close();
        }
    }

    private void loadCommands() {
        CommandService drink = Drink.get(this);
        drink.bind(PlayerListManager.Target.class).toProvider(new TargetProvider(playerListManager));
        drink.registerCommands();
    }

    //private void applyAliasesAndRegister(CommandService drink, Object commandInstance, String commandName) {
    //    final List<String> aliases = settings.commandAliases.getOrDefault(commandName, List.of(commandName));
    //    if (aliases.isEmpty()) {
    //        getLogger().severe("No command or aliases found for " + commandName);
    //        getLogger().severe("The command will not be registered");
    //        return;
    //    }
    //    drink.locallyRegister(commandInstance, aliases.getFirst(), aliases.subList(1, aliases.size()).toArray(new String[0]));
    //}

    public void loadYML() throws ConfigurationException {
        Path configFile = new File(getDataFolder(), "config.yml").toPath();
        Settings.initSettings(configFile);
        Path messagesFile = new File(getDataFolder(), "messages.yml").toPath();
        Messages.loadMessages(messagesFile);
        Path guisFile = new File(getDataFolder(), "guis.yml").toPath();
        GuiSettings.loadGuiSettings(guisFile);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void loadDebugFile() {
        final File pluginDir = new File(getServer().getPluginsFolder(), "ZelSync");
        if (!pluginDir.exists()) {
            pluginDir.mkdir();
        }
        final File parentDir = new File(pluginDir, "logs");
        if (!parentDir.exists()) {
            parentDir.mkdir();
        }
        debugFile = new File(parentDir, "debug" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".log");
        if (!debugFile.exists()) {
            try {
                debugFile.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void saveYML() {
        Path configFile = new File(getDataFolder(), "config.yml").toPath();
        YamlConfigurations.save(configFile, Settings.class, Settings.instance(),
          ConfigLib.BUKKIT_DEFAULT_PROPERTIES.toBuilder()
            .header("zelsync config")
            .footer("Authors: Unnm3d")
            .charset(StandardCharsets.UTF_8)
            .build()
        );
        Path guisFile = new File(getDataFolder(), "guis.yml").toPath();
        GuiSettings.saveGuiSettings(guisFile);
    }
}
