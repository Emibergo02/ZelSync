package dev.unnm3d.zelsync.configs;


import com.google.common.collect.Lists;
import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import de.exlll.configlib.YamlConfigurationProperties;
import de.exlll.configlib.YamlConfigurations;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

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

    public String licenseKey = "0000-0000-0000-0000";

    @Comment({"Storage type for the plugin",
      "MYSQL - MySQL or MariaDB storage",
      "POSTGRESQL - POSTGRESQL storage",
      "SQLITE - SQLite storage"})
    public StorageType storageType = StorageType.SQLITE;

    @Comment({"Cache type for the plugin",
      "REDIS - Redis cache",
      "PLUGIN_MESSAGE - Plugin message cache (not implemented yet)",
      "MEMORY - Memory cache (RAM) (does not enable cross-server features)"})
    public CacheType cacheType = CacheType.MEMORY;

    @Comment({"Drivers usually are:",
      "com.mysql.cj.jdbc.Driver for MySQL/MariaDB",
      "org.postgresql.Driver for PostgreSQL"})
    public SQLDatabase sqlDatabase = new SQLDatabase("localhost", 3306, "com.mysql.cj.jdbc.Driver",
      "zelsync", "root", "password",
      String.join("&",
        "?autoReconnect=true", "useSSL=false", "useUnicode=true", "characterEncoding=UTF-8"),
      10, 10, 1800000, 0, 5000);

    @Comment("Leave password or user empty if you don't have a password or user")
    public RedisSettings cache = new RedisSettings();


    public boolean debug = true;
    public boolean debugStrace = false;


    public record SQLDatabase(String databaseHost, int databasePort, String driverClass,
                              String databaseName, String databaseUsername, String databasePassword,
                              String parameters, int maximumPoolSize, int minimumIdle,
                              int maxLifetime, int keepAliveTime, int connectionTimeout) {
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
            public int retryBackoffMillis = 200;
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

    public enum CacheType {
        REDIS,
        PLUGIN_MESSAGE,
        MEMORY,
    }

    public enum StorageType {
        MYSQL,
        SQLITE,
        POSTGRESQL,
    }

}
