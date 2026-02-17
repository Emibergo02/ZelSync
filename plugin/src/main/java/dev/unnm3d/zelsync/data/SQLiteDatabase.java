package dev.unnm3d.zelsync.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.unnm3d.zelsync.ZelSync;
import dev.unnm3d.zeltrade.api.core.IArchivedTrade;
import dev.unnm3d.zeltrade.api.data.MeanRating;
import dev.unnm3d.zeltrade.api.data.TradeRating;
import dev.unnm3d.zeltrade.api.enums.Actor;
import dev.unnm3d.zeltrade.api.enums.Status;
import dev.unnm3d.zeltrade.api.enums.TradeViewType;
import dev.unnm3d.zelsync.core.ArchivedTrade;
import dev.unnm3d.zelsync.core.NewTrade;
import dev.unnm3d.zelsync.core.OrderInfo;
import dev.unnm3d.zelsync.core.TradeSide;
import dev.unnm3d.zelsync.integrity.ZelTradeStorageException;
import dev.unnm3d.zelsync.utils.Utils;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.invui.inventory.VirtualInventory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;

public class SQLiteDatabase implements Database {
    protected static String ARCHIVE_QUERY = """
      INSERT OR REPLACE INTO archived (trade_uuid,trade_timestamp,trader_uuid,trader_name,trader_rating,trader_price,
      customer_uuid,customer_name,customer_rating,customer_price,trader_items,customer_items)
      VALUES (?,?,?,?,?,?,?,?,?,?,?,?);""";
    protected static final String GET_ARCHIVED_TRADES_QUERY = """
      SELECT * FROM archived WHERE trader_uuid = ? OR customer_uuid = ? AND trade_timestamp BETWEEN ? AND ?
      ORDER BY trade_timestamp DESC;""";
    protected static final String GET_ARCHIVED_QUERY = """
      SELECT * FROM archived WHERE trade_uuid = ?""";
    protected static final String WIPE_ARCHIVED_QUERY = """
      DELETE FROM archived;""";
    protected static String BACKUP_TRADE_QUERY = """
      INSERT OR REPLACE INTO backup (trade_uuid,server_id,serialized)
      VALUES (?,?,?);""";
    protected static final String REMOVE_BACKUP_QUERY = """
      DELETE FROM backup WHERE trade_uuid = ?;""";
    protected static final String RESTORE_BACKUP_QUERY = """
      SELECT * FROM backup;""";
    protected static final String LOAD_NAMES_QUERY = """
      SELECT * FROM player_list;""";
    protected static String UPDATE_NAMES_QUERY = """
      INSERT OR REPLACE INTO player_list (player_name,player_uuid)
      VALUES (?,?);""";
    protected static String ADD_IGNORED_QUERY = """
      INSERT OR REPLACE INTO ignored_players (ignorer,ignored) VALUES (?,?);""";
    protected static String REMOVE_IGNORED_QUERY = """
      DELETE FROM ignored_players WHERE ignorer = ? AND ignored = ?;""";
    protected static final String GET_IGNORED_QUERY = """
      SELECT * FROM ignored_players WHERE ignorer = ?;""";
    protected static final String GET_RATING_QUERY = """
      SELECT trader_rating, customer_rating FROM archived
      WHERE trade_uuid = ?;""";
    protected static final String RATE_TRADE_QUERY = """
      UPDATE archived SET %s = ? WHERE trade_uuid = ?;""";
    protected static final String GET_MEAN_RATING_QUERY = """
      SELECT username,AVG(rating),COUNT(rating)
      FROM (SELECT trader_rating as rating,trader_name as username FROM archived WHERE archived.trader_uuid = ?
      UNION ALL
      SELECT customer_rating as rating,customer_name as username FROM archived WHERE archived.customer_uuid = ?)
      union_alias
      WHERE rating > 0
      GROUP BY username;""";


    protected final ZelSync plugin;
    @Getter
    protected boolean connected = false;
    protected HikariDataSource dataSource;
    protected Gson gson = new Gson();


    public SQLiteDatabase(ZelSync plugin) {
        this.plugin = plugin;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void connect() {
        final File databaseFile = new File(ZelSync.getInstance().getDataFolder(), "redistrade.db");
        try {
            if (databaseFile.createNewFile()) {
                plugin.getLogger().info("Created the SQLite database file");
            }

            Class.forName("org.sqlite.JDBC");

            HikariConfig config = new HikariConfig();
            config.setPoolName("ZelTradeHikariPool");
            config.setDriverClassName("org.sqlite.JDBC");
            config.setJdbcUrl("jdbc:sqlite:" + databaseFile.getAbsolutePath());
            config.setConnectionTestQuery("SELECT 1");
            config.setMaxLifetime(60000);
            config.setIdleTimeout(45000);
            config.setMaximumPoolSize(50);
            dataSource = new HikariDataSource(config);

        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "An exception occurred creating the database file", e);
        } catch (ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load the necessary SQLite driver", e);
        }

        //Backup the database file
        if (!databaseFile.exists()) {
            return;
        }
        final File backup = new File(databaseFile.getParent(), String.format("%s.bak", databaseFile.getName()));
        try {
            if (!backup.exists() || backup.delete()) {
                Files.copy(databaseFile.toPath(), backup.toPath());
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to backup flat file database", e);
        }

        //Initialize the database
        try {
            final String[] databaseSchema = getSchemaStatements("sqlite_schema.sql");
            for (String tableCreationStatement : databaseSchema) {
                try (Statement statement = getConnection().createStatement()) {
                    statement.execute(tableCreationStatement);
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to execute database schema statement: " + tableCreationStatement, e);
                }
            }
        } catch (IOException e) {
            close();
            throw new IllegalStateException("Failed to create database tables.", e);
        }
        connected = true;
    }


    @Override
    public void close() {
        try {
            if (getConnection() != null) {
                if (!getConnection().isClosed()) {
                    getConnection().close();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        connected = false;
    }

    @Override
    public CompletableFuture<IArchivedTrade<NewTrade>> archiveTrade(@NotNull NewTrade trade) {
        long timestamp = System.currentTimeMillis();

        return CompletableFuture.supplyAsync(() -> {
            // 1. Use try-with-resources to automatically close connection and statement
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(ARCHIVE_QUERY)) {

                statement.setString(1, trade.getUuid().toString());
                statement.setTimestamp(2, new Timestamp(timestamp));

                // 2. Extract sides to local variables to avoid massive method chaining
                var trader = trade.getTraderSide();
                statement.setString(3, trader.getTraderUUID().toString());
                statement.setString(4, trader.getTraderName());
                statement.setInt(5, trader.getOrder().getRating());
                statement.setString(6, gson.toJson(trader.getOrder().getPrices()));

                var customer = trade.getCustomerSide();
                statement.setString(7, customer.getTraderUUID().toString());
                statement.setString(8, customer.getTraderName());
                statement.setInt(9, customer.getOrder().getRating());
                statement.setString(10, gson.toJson(customer.getOrder().getPrices()));

                // 3. Use setBytes directly for SQLite blobs (much cleaner than Streams/SerialBlob)
                statement.setBytes(11, Utils.compress(trader.getOrder().getVirtualInventory().serialize(), Utils.CompressionLevel.STORAGE));
                statement.setBytes(12, Utils.compress(customer.getOrder().getVirtualInventory().serialize(), Utils.CompressionLevel.STORAGE));

                return statement.executeUpdate() > 0;

            } catch (SQLException e) {
                plugin.getIntegritySystem().handleStorageException(
                  new ZelTradeStorageException(e, ZelTradeStorageException.ExceptionSource.ARCHIVE_TRADE, trade.getUuid())
                );
                return false;
            }
        }).thenApply(success -> success ? new ArchivedTrade(new Date(timestamp), trade) : null);
    }

    @Override
    public CompletableFuture<List<IArchivedTrade<NewTrade>>> getArchivedTrades(@NotNull UUID playerUUID, @NotNull LocalDateTime startTimestamp, @NotNull LocalDateTime endTimestamp) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(GET_ARCHIVED_TRADES_QUERY)) {
                statement.setString(1, playerUUID.toString());
                statement.setString(2, playerUUID.toString());
                statement.setTimestamp(3, Timestamp.valueOf(startTimestamp));
                statement.setTimestamp(4, Timestamp.valueOf(endTimestamp));

                try (ResultSet result = statement.executeQuery()) {
                    final List<IArchivedTrade<NewTrade>> trades = new ArrayList<>();
                    while (result.next()) {
                        try {
                            trades.add(tradeFromResultSet(result));
                        } catch (Exception e) {
                            plugin.getIntegritySystem().handleStorageException(new ZelTradeStorageException(e, ZelTradeStorageException.ExceptionSource.SERIALIZATION));
                        }
                    }
                    return trades;
                }
            } catch (SQLException e) {
                plugin.getIntegritySystem().handleStorageException(new ZelTradeStorageException(e, ZelTradeStorageException.ExceptionSource.UNARCHIVE_TRADE));
                return Collections.emptyList();
            }
        });
    }

    @Override
    public CompletableFuture<Optional<IArchivedTrade<NewTrade>>> getArchivedTrade(@NotNull UUID tradeUUID) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(GET_ARCHIVED_QUERY)) {
                statement.setString(1, tradeUUID.toString());

                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        return Optional.of(tradeFromResultSet(result));
                    }
                    return Optional.empty();
                }
            } catch (SQLException e) {
                plugin.getIntegritySystem().handleStorageException(new ZelTradeStorageException(e, ZelTradeStorageException.ExceptionSource.UNARCHIVE_TRADE));
                return Optional.empty();
            }
        });
    }

    @Override
    public void wipeArchivedTrades() {
        CompletableFuture.runAsync(() -> {
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(WIPE_ARCHIVED_QUERY)) {
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getIntegritySystem().handleStorageException(new ZelTradeStorageException(e, ZelTradeStorageException.ExceptionSource.UNARCHIVE_TRADE));
            }
        });
    }

    @Override
    public void backupTrade(@NotNull NewTrade trade) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(BACKUP_TRADE_QUERY)) {
            statement.setString(1, trade.getUuid().toString());
            statement.setInt(2, ZelSync.getServerId());
            statement.setBytes(3, trade.serialize());
            if (statement.executeUpdate() != 0) {
                ZelSync.debug("Trade " + trade.getUuid() + " backed up");
            }
        } catch (Exception e) {
            plugin.getIntegritySystem().handleStorageException(new ZelTradeStorageException(e,
              ZelTradeStorageException.ExceptionSource.BACKUP_TRADE, trade.getUuid()));
        }
    }

    @Override
    public void removeTradeBackup(@NotNull UUID tradeUUID) {
        CompletableFuture.runAsync(() -> {
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(REMOVE_BACKUP_QUERY)) {
                statement.setString(1,
                  tradeUUID.toString());
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getIntegritySystem().handleStorageException(new ZelTradeStorageException(e, ZelTradeStorageException.
                  ExceptionSource.BACKUP_TRADE, tradeUUID));
            }
        });
    }

    @Override
    public void updateStoragePlayerList(@NotNull String playerName, @NotNull UUID playerUUID) {
        CompletableFuture.runAsync(() -> {
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(UPDATE_NAMES_QUERY)) {
                statement.setString(1, playerName);
                statement.setString(2, playerUUID.toString());
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getIntegritySystem().handleStorageException(new ZelTradeStorageException(e, ZelTradeStorageException.ExceptionSource.PLAYERLIST));
            }
        });
    }

    @Override
    public void ignorePlayer(@NotNull String ignorer, @NotNull String ignored, boolean ignore) {
        CompletableFuture.runAsync(() -> {
            final String query = ignore ? ADD_IGNORED_QUERY : REMOVE_IGNORED_QUERY;
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, ignorer);
                statement.setString(2, ignored);
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getIntegritySystem().handleStorageException(new ZelTradeStorageException(e, ZelTradeStorageException.ExceptionSource.IGNORED_PLAYER));
            }
        });
    }

    @Override
    public void rateTrade(@NotNull UUID archivedTradeUUID, @NotNull Actor actor, int rating) {
        CompletableFuture.runAsync(() -> {
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(RATE_TRADE_QUERY
                   .formatted(actor == Actor.CUSTOMER ? "customer_rating" : "trader_rating"))) {
                statement.setInt(1, rating);
                statement.setString(2, archivedTradeUUID.toString());
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getIntegritySystem().handleStorageException(new ZelTradeStorageException(e, ZelTradeStorageException.ExceptionSource.IGNORED_PLAYER));
            }
        });
    }

    @Override
    public CompletionStage<Map<Integer, NewTrade>> restoreTrades() {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(RESTORE_BACKUP_QUERY)) {
                try (ResultSet result = statement.executeQuery()) {
                    final HashMap<Integer, NewTrade> trades = new HashMap<>();
                    while (result.next()) {
                        try {
                            byte[] serializedTradeStream = result.getBytes("serialized");
                            trades.put(result.getInt("server_id"),
                              NewTrade.deserialize(serializedTradeStream));
                        } catch (Exception e) {
                            plugin.getIntegritySystem().handleStorageException(new ZelTradeStorageException(e, ZelTradeStorageException.ExceptionSource.SERIALIZATION));
                        }
                    }
                    return trades;
                }
            } catch (SQLException e) {
                plugin.getIntegritySystem().handleStorageException(new ZelTradeStorageException(e, ZelTradeStorageException.ExceptionSource.RESTORE_TRADE));
                return Collections.emptyMap();
            }
        });
    }

    @Override
    public CompletionStage<Map<String, UUID>> loadNameUUIDs() {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(LOAD_NAMES_QUERY)) {
                try (ResultSet result = statement.executeQuery()) {
                    final Map<String, UUID> nameUUIDs = new HashMap<>();
                    while (result.next()) {
                        nameUUIDs.put(result.getString("player_name"), UUID.fromString(result.getString("player_uuid")));
                    }
                    return nameUUIDs;
                }
            } catch (SQLException e) {
                plugin.getIntegritySystem().handleStorageException(new ZelTradeStorageException(e, ZelTradeStorageException.ExceptionSource.PLAYERLIST));
                return new HashMap<>();
            }
        });
    }

    @Override
    public CompletionStage<Set<String>> getIgnoredPlayers(@NotNull String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(GET_IGNORED_QUERY)) {
                statement.setString(1, playerName);
                try (ResultSet result = statement.executeQuery()) {
                    final Set<String> ignoredPlayers = new HashSet<>();
                    while (result.next()) {
                        ignoredPlayers.add(result.getString("ignored"));
                    }
                    return ignoredPlayers;
                }
            } catch (SQLException e) {
                plugin.getIntegritySystem().handleStorageException(new ZelTradeStorageException(e, ZelTradeStorageException.ExceptionSource.IGNORED_PLAYER));
                return Collections.emptySet();
            }
        });
    }

    @Override
    public CompletionStage<TradeRating> getTradeRating(@NotNull UUID tradeUUID) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(GET_RATING_QUERY)) {
                statement.setString(1, tradeUUID.toString());
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        return new TradeRating(result.getInt(1), result.getInt(2));
                    }
                    return new TradeRating(0, 0);
                }
            } catch (SQLException e) {
                plugin.getIntegritySystem().handleStorageException(new ZelTradeStorageException(e, ZelTradeStorageException.ExceptionSource.IGNORED_PLAYER));
                return new TradeRating(0, 0);
            }
        });
    }

    @Override
    public CompletableFuture<MeanRating> getMeanRating(@NotNull UUID playerUUID) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(GET_MEAN_RATING_QUERY)) {
                statement.setString(1, playerUUID.toString());
                statement.setString(2, playerUUID.toString());
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        return new MeanRating(result.getString(1), result.getDouble(2), result.getInt(3));
                    }
                    return null;
                }
            } catch (SQLException e) {
                plugin.getIntegritySystem().handleStorageException(new ZelTradeStorageException(e, ZelTradeStorageException.ExceptionSource.IGNORED_PLAYER));
                return null;
            }
        });
    }

    protected IArchivedTrade<NewTrade> tradeFromResultSet(ResultSet result) throws SQLException {
        Type typeOfPriceHashMap = new TypeToken<Map<String, Double>>() {
        }.getType();

        HashMap<String, Double> traderPrice = new HashMap<>(gson.fromJson(result.getString("trader_price"), typeOfPriceHashMap));

        byte[] traderItemsStream = result.getBytes("trader_items");
        OrderInfo traderOrder = new OrderInfo(VirtualInventory.deserialize(
          Utils.decompress(traderItemsStream)
        ), Status.COMPLETED, result.getInt("trader_rating"), traderPrice);

        TradeSide traderSide = new TradeSide(UUID.fromString(result.getString("trader_uuid")),
          result.getString("trader_name"), false, TradeViewType.NOT_VIEWING, traderOrder);

        HashMap<String, Double> customerPrice = new HashMap<>(gson.fromJson(result.getString("customer_price"), typeOfPriceHashMap));

        byte[] customerItemsStream = result.getBytes("customer_items");
        OrderInfo customerOrder = new OrderInfo(VirtualInventory.deserialize(
          Utils.decompress(customerItemsStream)
        ), Status.COMPLETED, result.getInt("customer_rating"), customerPrice);

        TradeSide customerSide = new TradeSide(UUID.fromString(result.getString("customer_uuid")),
          result.getString("customer_name"), false, TradeViewType.NOT_VIEWING, customerOrder);
        Date tradeDate = result.getTimestamp("trade_timestamp");
        return new ArchivedTrade(tradeDate,
          new NewTrade(UUID.fromString(result.getString("trade_uuid")), traderSide, customerSide));
    }
}
