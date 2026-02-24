package dev.unnm3d.zelsync.data;

import dev.unnm3d.zelsync.ZelSync;
import dev.unnm3d.zelsync.api.data.DataKeys;
import dev.unnm3d.zelsync.core.DataSnapshot;
import dev.unnm3d.zelsync.core.QueryResult;
import dev.unnm3d.zelsync.core.StoredSnapshot;
import dev.unnm3d.zelsync.redistools.RedisAbstract;
import io.lettuce.core.ScriptOutputType;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class RedisDataManager extends RedisAbstract {

    private final ZelSync plugin;

    public RedisDataManager(ZelSync plugin) {
        this.plugin = plugin;
        registerChannels(this::receiveMessage,
          DataKeys.SYNC_INV_UPDATE.toString()
        );
    }

    public void receiveMessage(String channel, byte[] messageBytes) {
        if (channel.equals(DataKeys.SYNC_INV_UPDATE.toString())) {
            String messageStr = new String(messageBytes);
            plugin.getLogger().info("Received inventory update message: " + messageStr);


        } else if (channel.equals(DataKeys.PLAYERLIST.toString())) {
            String playerListStr = new String(messageBytes);
            List<String> playerList = Arrays.asList(playerListStr.split("§"));
            plugin.getPlayerListManager().updatePlayerList(playerList);
        }
    }

    public void publishPlayerList(List<String> playerList) {
        getConnectionAsync(connection ->
          connection.publish(
            DataKeys.PLAYERLIST.getKeyBytes(),
            String.join("§", playerList).getBytes()
          ))
          .exceptionally(exception -> {
              plugin.getLogger().warning("Error when publishing player list");
              return -1L;
          });
    }

    public CompletableFuture<StoredSnapshot> getInv(UUID playerUUID) {
        return getThreadSafeConnectionAsyncWithRetry(connection -> {
              byte[] uuidBytes = ByteBuffer.allocate(16)
                .putLong(playerUUID.getMostSignificantBits())
                .putLong(playerUUID.getLeastSignificantBits()).array();

              //0 is for first login, -1 is for locked inventory, any other value is a valid lock ID
              final List<byte[]> result = connection.eval("""
                  local latest=redis.call("HGET", KEYS[1], ARGV[1])
                  if not latest then return {"0",nil} end
                  if latest == "-1" then return {"-1",nil} end
                  local serialized=redis.call("GETDEL", KEYS[2] .. latest)
                  redis.call("HSET", KEYS[1], ARGV[1], "-1")
                  return {latest, serialized}
                  """, ScriptOutputType.MULTI,
                new byte[][]{
                  DataKeys.SYNC_INV.getKeyBytes(),
                  DataKeys.PLAYER_LOCK.getKeyBytes()
                },
                uuidBytes
              );

              Object lockIdObj = result.getFirst();
              plugin.getLogger().info("Raw lock ID object: " + lockIdObj + " of type " + (lockIdObj != null ? lockIdObj.getClass().getName() : "null"));
              long snapshotUniqueId = Long.parseLong(new String((byte[]) lockIdObj));
              QueryResult queryResult = QueryResult.fromResultLong(snapshotUniqueId);
              plugin.getLogger().info("Player " + playerUUID + " got inventory result: " + queryResult.toString());

              if (queryResult != QueryResult.SUCCESS) {
                  return new StoredSnapshot(snapshotUniqueId, null);
              }

              plugin.getLogger().info(Base64.getEncoder().encodeToString(result.get(1)));
              DataSnapshot snapshot = DataSnapshot.deserialize(result.get(1));

              return new StoredSnapshot(snapshotUniqueId, snapshot);
          }, (int) playerUUID.getLeastSignificantBits(),
          storedSnapshot -> storedSnapshot.getResult() != QueryResult.LOCKED); // Retry if the inventory is locked
    }

    /**
     * Saves the inventory using a Lua script to ensure atomicity and sets a lock with an expiration time to prevent race conditions.
     * The script increments a lock counter, saves the inventory, and publishes an update message. If the lock cannot be acquired, it returns null.
     *
     * @param playerUUID   the UUID of the player whose inventory is being saved
     * @param dataSnapshot the player data snapshot to be saved
     * @return a CompletableFuture that completes with the unique ID of the saved inventory snapshot, or null if the lock could not be acquired
     */
    public CompletableFuture<Long> saveSnapshotScript(UUID playerUUID, DataSnapshot dataSnapshot) {
        return getThreadSafeConnectionAsync(connection -> {
            byte[] uuidBytes = ByteBuffer.allocate(16)
              .putLong(playerUUID.getMostSignificantBits())
              .putLong(playerUUID.getLeastSignificantBits()).array();
            plugin.getLogger().info(Base64.getEncoder().encodeToString(dataSnapshot.serialize()));
            return connection.eval("""
                local id=redis.call("INCR", KEYS[1])
                redis.call("SET",KEYS[2] .. id,ARGV[2])
                redis.call("HSET", KEYS[3], ARGV[1], id)
                redis.call("PUBLISH", KEYS[4], id)
                return id
                """, ScriptOutputType.INTEGER,
              new byte[][]{
                DataKeys.COUNTER_GENERATOR.getKeyBytes(),//zsync:counter KEYS[1]
                DataKeys.PLAYER_LOCK.getKeyBytes(),//zsync:p_lock KEYS[2]
                DataKeys.SYNC_INV.getKeyBytes(),//zsync:inv KEYS[3]
                DataKeys.SYNC_INV_UPDATE.getKeyBytes(),//zsync:inv_up KEYS[4]
              },
              uuidBytes, // ARGV[1] = player UUID bytes for hash field
              dataSnapshot.serialize(), // ARGV[2] = serialized inventory data
              "30".getBytes());// ARGV[3] = lock expiration time in seconds
        }, (int) playerUUID.getLeastSignificantBits());
    }

}
