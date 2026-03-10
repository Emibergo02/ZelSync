package dev.unnm3d.zelsync.configs;


import com.google.common.collect.Lists;
import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import de.exlll.configlib.YamlConfigurationProperties;
import de.exlll.configlib.YamlConfigurations;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

@Configuration
public class Settings {
    private static Settings SETTINGS;


    public static Settings instance() {
        return SETTINGS;
    }

    public static Settings initSettings(Path configFile) {
        SETTINGS = YamlConfigurations.update(
          configFile,
          Settings.class,
          YamlConfigurationProperties.newBuilder()
            .header("zelsync config")
            .footer("Authors: Unnm3d")
            .charset(StandardCharsets.UTF_8)
            .build()
        );
        return SETTINGS;
    }

    @Comment("Leave password or user empty if you don't have a password or user")
    public RedisSettings cache = new RedisSettings();


    public SynchronizationSettings synchronization = new SynchronizationSettings(
      true, // saveInventory
      true, // savePersistentDataContainer
      true, // saveEnderChest
      true, // saveHealth
      true, // saveFood
      true, // saveExperience
      true, // savePotionEffects
      true, // saveGamemode
      true, // saveFlight
      false // saveLocation
    );

    @Comment("Time in seconds after a snapshot is deleted from Redis, only the latest snapshot has no expiration time")
    public int snapshotExpirationSeconds = 604800;

    public boolean debug = false;
    public boolean debugToConsole = false;
    public boolean debugStrace = false;

    public record SynchronizationSettings(boolean saveInventory,boolean savePersistentDataContainer, boolean saveEnderChest,
                                          boolean saveHealth, boolean saveFood, boolean saveExperience,
                                          boolean savePotionEffects, boolean saveGamemode, boolean saveFlight,
                                          boolean saveLocation) {
    }

    @Configuration
    public static class RedisSettings {

        @Comment({"Specify the credentials of your Redis server here.",
          "Set \"user\" to '' if you don't have one or would like to use the default user.",
          "Set \"password\" to '' if you don't have one."})
        public RedisCredentials credentials = new RedisCredentials();

        @Configuration
        public static class RedisCredentials {
            public String host = "localhost";
            public int port = 6379;
            @Comment("Only change the database if you know what you are doing. The default is 0.")
            public int database = 0;
            public String user = "";
            public String password = "";

            @Comment("Use SSL/TLS for encrypted connections.")
            public boolean useSsl = false;

            @Comment("Connection timeout in milliseconds.")
            public int connectionTimeout = 2000;

            @Comment("Timeout (read/write) timeout in milliseconds.")
            public int commandTimeout = 2000;

            @Comment("Max number of connections in the pool.")
            public int maxConnections = 20;

            @Comment("Min number of idle connections in the pool.")
            public int minIdleConnections = 10;

            @Comment("Enable health checks when borrowing connections from the pool.")
            public boolean testOnBorrow = false;

            @Comment("Enable health checks when returning connections to the pool.")
            public boolean testOnReturn = false;

            @Comment("Enable periodic idle connection health checks.")
            public boolean testWhileIdle = true;

            @Comment("Min evictable idle time (ms) before a connection is eligible for eviction.")
            public long minEvictableIdleTimeMillis = 30000;

            @Comment("Time (ms) between eviction runs.")
            public long timeBetweenEvictionRunsMillis = 15000;

            @Comment("Threads for handling Redis commands")
            public int threadPoolSize = 5;

            @Comment("Number of retries for commands when connection fails. (not configurable yet)")
            public int maxRetries = 3;

            @Comment("Base backoff time in ms for retries (exponential backoff multiplier). (not implemented yet)")
            public int retryBackoffMillis = 350;
        }

        @Comment("Options for if you're using Redis sentinel. Don't modify this unless you know what you're doing!")
        public RedisSentinel sentinel = new RedisSentinel();

        @Configuration
        public static class RedisSentinel {
            @Comment("The master set name for the Redis sentinel.")
            public String master = "";
            @Comment("List of host:port pairs")
            public List<String> nodes = Lists.newArrayList();
            public String password = "";
        }

    }

}
