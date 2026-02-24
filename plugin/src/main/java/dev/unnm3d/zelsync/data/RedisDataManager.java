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
        return getThreadSafeConnectionAsyncWithRetry((connection, remainingTries) -> {
              byte[] uuidBytes = ByteBuffer.allocate(16)
                .putLong(playerUUID.getMostSignificantBits())
                .putLong(playerUUID.getLeastSignificantBits()).array();
              long currentTime = System.currentTimeMillis();
              //zsync:inv<playerUUID> (HASHSET) SNAPSHOTID-SERIALIZED
              //zsync:p_lock<playerUUID> LATEST SNAPSHOTID 0 missing, SNAPSHOTID, if negative is locked.
              String latestHandling = remainingTries == 0 ?
                "if latest <= 0 then latest=-latest end" : //If last try, "force unlock" the inv and return that
                "if latest < 0 then return {-1} end";
              final List<Object> result = connection.eval("""
                  local latest=tonumber(redis.call("GET", KEYS[1]))
                  if not latest then return {0} end
                  %s
                  local serialized=redis.call("HGET", KEYS[2],tostring(latest))
                  redis.call("SET", KEYS[1], -latest)
                  return {latest, serialized}
                  """.formatted(latestHandling),
                ScriptOutputType.MULTI,
                new byte[][]{
                  DataKeys.PLAYER_LOCK.append(uuidBytes),//zsync:p_lock<playerUUID> KEYS[1]
                  DataKeys.SYNC_INV.append(uuidBytes), //zsync:inv<playerUUID> KEYS[2]
                }
              );
              plugin.getLogger().info("Elapsed time for inventory retrieval script: " + (System.currentTimeMillis() - currentTime) + "ms");

              long snapshotUniqueId = (Long) result.getFirst();
              plugin.getLogger().info("Raw lock ID object: " + snapshotUniqueId);
              QueryResult queryResult = QueryResult.fromResultLong(snapshotUniqueId);
              plugin.getLogger().info("Player " + playerUUID + " got inventory result: " + queryResult.toString());

              if (queryResult != QueryResult.SUCCESS) {
                  return new StoredSnapshot(snapshotUniqueId, null);
              }

              plugin.getLogger().info(Base64.getEncoder().encodeToString((byte[]) result.get(1)));
              DataSnapshot snapshot = DataSnapshot.deserialize((byte[]) result.get(1));

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
                redis.call("HSET",KEYS[2],id,ARGV[2])
                redis.call("SET", KEYS[3], id)
                redis.call("PUBLISH", KEYS[4], id)
                return id
                """, ScriptOutputType.INTEGER,
              new byte[][]{
                DataKeys.COUNTER_GENERATOR.getKeyBytes(),//zsync:counter KEYS[1]
                DataKeys.SYNC_INV.append(uuidBytes),//zsync:inv KEYS[2]
                DataKeys.PLAYER_LOCK.append(uuidBytes),//zsync:p_lock:<playerUUID> KEYS[3]
                DataKeys.SYNC_INV_UPDATE.getKeyBytes(),//zsync:inv_up KEYS[4]
              },
              uuidBytes, // ARGV[1] = player UUID bytes for hash field
              dataSnapshot.serialize(), // ARGV[2] = serialized inventory data
              "30".getBytes());// ARGV[3] = lock expiration time in seconds
        }, (int) playerUUID.getLeastSignificantBits());
    }

}
