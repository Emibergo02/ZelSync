package dev.unnm3d.zelsync.redistools;

import dev.unnm3d.zelsync.ZelSync;
import dev.unnm3d.zelsync.configs.Settings;
import dev.unnm3d.zelsync.utils.ExecutorServiceRouter;
import io.lettuce.core.*;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.pubsub.RedisPubSubListener;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.support.ConnectionPoolSupport;
import lombok.Getter;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;


public abstract class RedisAbstract {
    private final RedisURI redisURI;
    private final ConcurrentHashMap<String[], StatefulRedisPubSubConnection<byte[], byte[]>> pubSubConnections;
    protected final GenericObjectPool<StatefulRedisConnection<byte[], byte[]>> lettucePool;
    @Getter
    private final RedisClient lettuceClient;
    protected ExecutorServiceRouter executorServiceRouter;
    protected ScheduledExecutorService scheduler;

    public RedisAbstract() {
        final Settings.RedisSettings redisSettings = Settings.instance().cache;

        // 1. BUILD REDIS URI WITH AUTHENTICATION
        RedisURI.Builder redisUriBuilder = RedisURI.Builder.redis(
            redisSettings.credentials.host,
            redisSettings.credentials.port
          )
          .withAuthentication(RedisCredentialsProvider.from(() -> RedisCredentials.just(
            redisSettings.credentials.user,
            redisSettings.credentials.password
          )))
          .withDatabase(redisSettings.credentials.database)
          .withSsl(redisSettings.credentials.useSsl);

        // 2. CONFIGURE REDIS SENTINEL (if applicable)
        // Set the master ID if Sentinel is configured
        if (!redisSettings.sentinel.master.isEmpty()) {
            redisUriBuilder.withSentinelMasterId(redisSettings.sentinel.master);
        }

        // Add each Sentinel node with optional password authentication
        for (String node : redisSettings.sentinel.nodes) {
            final String[] parts = node.split(":");

            if (redisSettings.sentinel.password.isEmpty()) {
                // Add Sentinel node without password
                redisUriBuilder.withSentinel(parts[0], Integer.parseInt(parts[1]));
            } else {
                // Add Sentinel node with password
                redisUriBuilder.withSentinel(parts[0], Integer.parseInt(parts[1]), redisSettings.sentinel.password);
            }
        }

        // Build the final Redis URI
        this.redisURI = redisUriBuilder.build();

        // 3. CREATE AND CONFIGURE REDIS CLIENT
        this.lettuceClient = RedisClient.create();

        // Configure client options with connection and socket timeouts
        this.lettuceClient.setOptions(ClientOptions.builder()
          .timeoutOptions(TimeoutOptions.enabled(Duration.ofMillis(redisSettings.credentials.commandTimeout)))
          .socketOptions(SocketOptions.builder()
            .connectTimeout(Duration.ofMillis(redisSettings.credentials.connectionTimeout))
            .build())
          .build());

        // 4. CREATE CONNECTION POOL
        this.lettucePool = ConnectionPoolSupport.createGenericObjectPool(
          () -> this.lettuceClient.connect(ByteArrayCodec.INSTANCE, redisURI),
          buildPoolConfig(redisSettings.credentials));

        this.pubSubConnections = new ConcurrentHashMap<>();

        this.executorServiceRouter = new ExecutorServiceRouter(redisSettings.credentials.threadPoolSize);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());

        // 5. VERIFY CONNECTION
        try (var lettuceConnection = this.lettucePool.borrowObject()) {
            lettuceConnection.sync().ping();
            ZelSync.getInstance().getLogger().info("Successfully connected to Redis server");
        } catch (Exception e) {
            throw new IllegalStateException(
              "Failed to establish connection with Redis. " +
                "Please check the supplied credentials in the config file", e);
        }
    }

    public void registerChannels(BiConsumer<String, byte[]> messageConsumer, String... channels) {
        StatefulRedisPubSubConnection<byte[], byte[]> connection = lettuceClient.connectPubSub(ByteArrayCodec.INSTANCE, redisURI);

        connection.addListener(new RedisPubSubListener<>() {
            @Override
            public void message(byte[] channel, byte[] message) {
                messageConsumer.accept(new String(channel), message);
            }

            @Override
            public void message(byte[] pattern, byte[] channel, byte[] message) {
                messageConsumer.accept(new String(channel), message);
            }

            @Override
            public void subscribed(byte[] channel, long count) {
            }

            @Override
            public void psubscribed(byte[] pattern, long count) {
            }

            @Override
            public void unsubscribed(byte[] channel, long count) {

            }

            @Override
            public void punsubscribed(byte[] pattern, long count) {

            }
        });
        connection.async().subscribe(Arrays.stream(channels)
          .map(String::getBytes).toArray(byte[][]::new));

        pubSubConnections.put(channels, connection);
    }

    private static @NotNull GenericObjectPoolConfig<StatefulRedisConnection<byte[], byte[]>> buildPoolConfig(Settings.RedisSettings.RedisCredentials credentials) {
        GenericObjectPoolConfig<StatefulRedisConnection<byte[], byte[]>> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxIdle(credentials.maxConnections);
        poolConfig.setMaxTotal(credentials.maxConnections);
        poolConfig.setMinIdle(credentials.minIdleConnections);
        poolConfig.setTestOnBorrow(credentials.testOnBorrow);
        poolConfig.setTestOnReturn(credentials.testOnReturn);
        poolConfig.setTestWhileIdle(credentials.testWhileIdle);
        poolConfig.setBlockWhenExhausted(true);
        poolConfig.setFairness(true);
        poolConfig.setMinEvictableIdleTimeMillis(credentials.minEvictableIdleTimeMillis);
        poolConfig.setTimeBetweenEvictionRunsMillis(credentials.timeBetweenEvictionRunsMillis);
        return poolConfig;
    }


    public <T> CompletableFuture<T> getConnectionAsync(Function<RedisCommands<byte[], byte[]>, T> redisCallBack) {
        return getThreadSafeConnectionAsync(redisCallBack, new Random().nextInt());
    }

    public CompletableFuture<Optional<List<Object>>> executeTransaction(Consumer<RedisCommands<byte[], byte[]>> redisCommandsConsumer, int id) {
        final CompletableFuture<Optional<List<Object>>> future = new CompletableFuture<>();
        this.executorServiceRouter.route(() -> {
            try (var connection = lettucePool.borrowObject()) {
                final var syncCommands = connection.sync();
                try {
                    syncCommands.multi();
                    redisCommandsConsumer.accept(syncCommands);
                    future.complete(
                      Optional.of(syncCommands.exec())
                        .filter(result -> !result.wasDiscarded())
                        .map(result -> result.stream().collect(Collectors.toList()))
                    );
                } catch (Exception e) {
                    syncCommands.discard();
                    throw e;
                }
            } catch (Exception e) {
                ZelSync.getInstance().getLogger().log(Level.SEVERE, "Error executing Redis transaction", e);
                future.completeExceptionally(e);
            }
        }, id);
        return future;
    }

    public <T> CompletableFuture<T> getThreadSafeConnectionAsync(Function<RedisCommands<byte[], byte[]>, T> redisCallBack, int id) {
        final CompletableFuture<T> future = new CompletableFuture<>();
        this.executorServiceRouter.route(() -> {
            try (var connection = lettucePool.borrowObject()) {
                future.complete(redisCallBack.apply(connection.sync()));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        }, id);
        return future;
    }

    public <T> CompletableFuture<T> getThreadSafeConnectionAsyncWithRetry(BiFunction<RedisCommands<byte[], byte[]>, Integer, T> redisCallBack,
                                                                          int id,
                                                                          Function<T, Boolean> validator) {
        final CompletableFuture<T> future = new CompletableFuture<>();
        tryAgainConnectionAsync(
          redisCallBack,
          future,
          id,
          Settings.instance().cache.credentials.maxRetries,
          Settings.instance().cache.credentials.retryBackoffMillis,
          validator);
        return future;
    }

    private <T> void tryAgainConnectionAsync(BiFunction<RedisCommands<byte[], byte[]>, Integer, T> redisCallBack,
                                             CompletableFuture<T> toComplete,
                                             int id,
                                             int remainingRetries,
                                             long retryDelayMillis,
                                             Function<T, Boolean> validator) {

        this.executorServiceRouter.route(() -> {
            try (var connection = lettucePool.borrowObject()) {
                T result = redisCallBack.apply(connection.sync(), remainingRetries);

                if (validator.apply(result)) {
                    toComplete.complete(result);
                    return; // Success! Exit the runnable.
                } else {
                    throw new IllegalStateException("Validation failed for Redis command resultInt");
                }
            } catch (Exception e) {
                if (remainingRetries > 0) {
                    ZelSync.getInstance().getLogger().log(Level.WARNING,
                      "Redis command failed, retrying... (" + remainingRetries + " attempts left)", e);

                    scheduler.schedule(() ->
                        tryAgainConnectionAsync(
                          redisCallBack,
                          toComplete,
                          id,
                          remainingRetries - 1,
                          retryDelayMillis * 2,
                          validator
                        ),
                      retryDelayMillis,
                      TimeUnit.MILLISECONDS
                    );

                } else {
                    ZelSync.getInstance().getLogger().log(Level.SEVERE, "Redis command failed after all attempts", e);
                    toComplete.completeExceptionally(e);
                }
            }
        }, id);
    }

    public void close() {
        pubSubConnections.values().forEach(StatefulRedisPubSubConnection::close);
        ZelSync.getInstance().getLogger().info("Closing Lettuce PubSub connections");
        executorServiceRouter.shutdown();
        lettucePool.close();
        lettuceClient.shutdown();
        ZelSync.getInstance().getLogger().info("Lettuce shutdown connection");
    }
}
