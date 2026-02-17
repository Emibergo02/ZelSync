package dev.unnm3d.zelsync.data;

import com.zaxxer.hikari.HikariDataSource;
import dev.unnm3d.zelsync.ZelSync;
import dev.unnm3d.zeltrade.api.core.IArchivedTrade;
import dev.unnm3d.zelsync.configs.Settings;
import dev.unnm3d.zelsync.core.ArchivedTrade;
import dev.unnm3d.zelsync.core.NewTrade;
import dev.unnm3d.zelsync.integrity.ZelTradeStorageException;
import dev.unnm3d.zelsync.utils.Utils;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.sql.*;
import java.util.Date;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

@Setter
@Getter
public class PostgresqlDatabase extends MySQLDatabase {

    public PostgresqlDatabase(ZelSync plugin, Settings.SQLDatabase settings) {
        super(plugin, settings);
        ARCHIVE_QUERY = """
          INSERT INTO archived (trade_uuid,trade_timestamp,trader_uuid,trader_name,trader_rating,trader_price,
          customer_uuid,customer_name,customer_rating,customer_price,trader_items,customer_items)
          VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
          ON CONFLICT (trade_uuid)
          DO UPDATE SET trade_timestamp=EXCLUDED.trade_timestamp,
          trader_uuid=EXCLUDED.trader_uuid, trader_name=EXCLUDED.trader_name, trader_rating=EXCLUDED.trader_rating,
          trader_price=EXCLUDED.trader_price, customer_uuid=EXCLUDED.customer_uuid, customer_name=EXCLUDED.customer_name,
          customer_rating=EXCLUDED.customer_rating, customer_price=EXCLUDED.customer_price,
          trader_items=EXCLUDED.trader_items, customer_items=EXCLUDED.customer_items;""";
        BACKUP_TRADE_QUERY = """
          INSERT INTO backup (trade_uuid, server_id, serialized)
          VALUES (?,?,?)
          ON CONFLICT (trade_uuid)
          DO UPDATE SET trade_uuid = EXCLUDED.trade_uuid,server_id = EXCLUDED.server_id, serialized = EXCLUDED.serialized;""";
        UPDATE_NAMES_QUERY = """
          INSERT INTO player_list (player_name,player_uuid)
          VALUES (?,?)
          ON CONFLICT (player_name)
          DO UPDATE SET player_name = EXCLUDED.player_name, player_uuid = EXCLUDED.player_uuid;""";
    }

    @Override
    public void connect() {
        dataSource = new HikariDataSource();
        try {
            Class.forName(settings.driverClass());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        dataSource.setJdbcUrl(String.format("jdbc:postgresql://%s:%s/%s%s",
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
            final String[] databaseSchema = getSchemaStatements("postgresql_schema.sql");
            try (Statement statement = connection.createStatement()) {
                for (String tableCreationStatement : databaseSchema) {
                    //Translate data types to postgres equivalents
                    statement.execute(tableCreationStatement
                      .replace("tinyint", "smallint")
                      .replace("mediumblob", "bytea")
                      .replace("longblob", "bytea"));
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
    public CompletableFuture<IArchivedTrade<NewTrade>> archiveTrade(@NotNull NewTrade trade) {
        long timestamp = System.currentTimeMillis();
        return CompletableFuture.supplyAsync(() -> {
            Connection connection = null;
            try {
                connection = getConnection();
                connection.setAutoCommit(false); // Start transaction

                try (PreparedStatement statement = connection.prepareStatement(ARCHIVE_QUERY)) {
                    statement.setString(1, trade.getUuid().toString());
                    statement.setTimestamp(2, new Timestamp(timestamp));
                    //Trader side
                    statement.setString(3, trade.getTraderSide().getTraderUUID().toString());
                    statement.setString(4, trade.getTraderSide().getTraderName());
                    statement.setInt(5, trade.getTraderSide().getOrder().getRating());
                    statement.setString(6, gson.toJson(trade.getTraderSide().getOrder().getPrices()));
                    //Customer side
                    statement.setString(7, trade.getCustomerSide().getTraderUUID().toString());
                    statement.setString(8, trade.getCustomerSide().getTraderName());
                    statement.setInt(9, trade.getCustomerSide().getOrder().getRating());
                    statement.setString(10, gson.toJson(trade.getCustomerSide().getOrder().getPrices()));

                    statement.setBytes(11,
                      Utils.compress(
                        trade.getTraderSide().getOrder().getVirtualInventory().serialize(),
                        Utils.CompressionLevel.STORAGE)
                    );
                    statement.setBytes(12,
                      Utils.compress(
                        trade.getCustomerSide().getOrder().getVirtualInventory().serialize(),
                        Utils.CompressionLevel.STORAGE)
                    );

                    boolean success = statement.executeUpdate() != 0;

                    if (success) {
                        connection.commit(); // Commit transaction
                        return true;
                    } else {
                        connection.rollback(); // Rollback if no rows affected
                        return false;
                    }
                }
            } catch (SQLException e) {
                if (connection != null) {
                    try {
                        connection.rollback(); // Rollback on error
                    } catch (SQLException rollbackEx) {
                        // Log rollback failure if needed
                        rollbackEx.addSuppressed(e);
                    }
                }
                plugin.getIntegritySystem().handleStorageException(
                  new ZelTradeStorageException(e, ZelTradeStorageException.ExceptionSource.ARCHIVE_TRADE, trade.getUuid())
                );
                return false;
            } finally {
                if (connection != null) {
                    try {
                        connection.setAutoCommit(true); // Restore auto-commit
                        connection.close();
                    } catch (SQLException e) {
                        // Log close failure if needed
                    }
                }
            }
        }).thenApply(success -> {
            if (success) {
                return new ArchivedTrade(new Date(timestamp), trade);
            } else {
                return null;
            }
        });
    }

}
