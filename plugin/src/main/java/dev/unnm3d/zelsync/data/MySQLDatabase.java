package dev.unnm3d.zelsync.data;

import com.zaxxer.hikari.HikariDataSource;
import dev.unnm3d.zelsync.ZelSync;
import dev.unnm3d.zelsync.configs.Settings;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Properties;

@Setter
@Getter
public class MySQLDatabase extends SQLiteDatabase {
    protected final Settings.SQLDatabase settings;

    public MySQLDatabase(ZelSync plugin, Settings.SQLDatabase settings) {
        super(plugin);
        this.settings = settings;
        ARCHIVE_QUERY = """
          INSERT INTO archived (trade_uuid,trade_timestamp,trader_uuid,trader_name,trader_rating,trader_price,
          customer_uuid,customer_name,customer_rating,customer_price,trader_items,customer_items)
          VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
          ON DUPLICATE KEY UPDATE trade_timestamp=VALUES(trade_timestamp),
          trader_uuid=VALUES(trader_uuid), trader_name=VALUES(trader_name), trader_rating=VALUES(trader_rating),
          trader_price=VALUES(trader_price), customer_uuid=VALUES(customer_uuid), customer_name=VALUES(customer_name),
          customer_rating=VALUES(customer_rating), customer_price=VALUES(customer_price),
          trader_items=VALUES(trader_items), customer_items=VALUES(customer_items);""";
        BACKUP_TRADE_QUERY = """
          INSERT INTO backup (trade_uuid, server_id, serialized)
          VALUES (?,?,?)
          ON DUPLICATE KEY UPDATE trade_uuid = VALUES(trade_uuid),server_id = VALUES(server_id), serialized = VALUES(serialized);""";
        UPDATE_NAMES_QUERY = """
          INSERT INTO player_list (player_name,player_uuid)
          VALUES (?,?)
          ON DUPLICATE KEY UPDATE player_name = VALUES(player_name), player_uuid = VALUES(player_uuid);""";
        ADD_IGNORED_QUERY = """
          INSERT INTO ignored_players (ignorer,ignored) VALUES (?,?)
          ON DUPLICATE KEY UPDATE ignored = VALUES(ignored);""";
        REMOVE_IGNORED_QUERY = """
          DELETE FROM ignored_players WHERE ignorer = ? AND ignored = ?;""";
    }

    @Override
    public void connect() {
        dataSource = new HikariDataSource();
        try {
            Class.forName(settings.driverClass());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        final String databaseType = settings.driverClass().contains("mariadb") ? "mariadb" : "mysql";
        dataSource.setJdbcUrl(String.format("jdbc:%s://%s:%s/%s%s",
          databaseType,
          settings.databaseHost(),
          settings.databasePort(),
          settings.databaseName(),
          settings.parameters()
        ));
        dataSource.setUsername(settings.databaseUsername());
        dataSource.setPassword(settings.databasePassword());

        dataSource.setMaximumPoolSize(settings.maximumPoolSize());
        dataSource.setMinimumIdle(settings.minimumIdle());
        dataSource.setMaxLifetime(settings.maxLifetime());
        dataSource.setKeepaliveTime(settings.keepAliveTime());
        dataSource.setConnectionTimeout(settings.connectionTimeout());
        dataSource.setPoolName("ZelTradeHikariPool");

        final Properties properties = new Properties();
        properties.putAll(
          Map.of("cachePrepStmts", "true",
            "prepStmtCacheSize", "250",
            "prepStmtCacheSqlLimit", "2048",
            "useServerPrepStmts", "true",
            "useLocalSessionState", "true",
            "useLocalTransactionState", "true"
          ));
        properties.putAll(
          Map.of(
            "rewriteBatchedStatements", "true",
            "cacheResultSetMetadata", "true",
            "cacheServerConfiguration", "true",
            "elideSetAutoCommits", "true",
            "maintainTimeStats", "false")
        );
        dataSource.setDataSourceProperties(properties);

        try (Connection connection = dataSource.getConnection()) {
            final String[] databaseSchema = getSchemaStatements(databaseType + "_schema.sql");
            try (Statement statement = connection.createStatement()) {
                for (String tableCreationStatement : databaseSchema) {
                    statement.execute(tableCreationStatement);
                }
                connected = true;
            } catch (SQLException e) {
                close();
                throw new IllegalStateException("Failed to create database tables. Please ensure you are running MySQL v8.0+ or MariaDB " +
                  "and that your connecting user account has privileges to create tables.", e);
            }
        } catch (SQLException | IOException e) {
            close();
            throw new IllegalStateException("Failed to establish a connection to the MySQL/MariaDB database. " +
              "Please check the supplied database credentials in the config file", e);
        }
    }

    @Override
    public void close() {
        if (dataSource == null) return;
        if (dataSource.isClosed()) return;
        dataSource.close();
        connected = false;
    }
}
