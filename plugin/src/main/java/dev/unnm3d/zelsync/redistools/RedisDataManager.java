package dev.unnm3d.zelsync.redistools;

import dev.unnm3d.zelsync.ZelSync;
import dev.unnm3d.zelsync.api.data.DataKeys;
import dev.unnm3d.zelsync.configs.Settings;
import dev.unnm3d.zelsync.core.snapshots.DataSnapshot;
import dev.unnm3d.zelsync.core.snapshots.SnapshotDiff;
import dev.unnm3d.zelsync.core.snapshots.StoredSnapshot;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.sync.RedisCommands;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class RedisDataManager extends RedisAbstract {

    private final ZelSync plugin;

    public RedisDataManager(ZelSync plugin) {
        this.plugin = plugin;
        registerChannels(this::receiveMessage,
          DataKeys.PLAYERLIST.toString(),
          DataKeys.SYNC_INV_UPDATE.toString()
        );
    }

    public void receiveMessage(String channel, byte[] messageBytes) {
        if (channel.equals(DataKeys.PLAYERLIST.toString())) {
            String playerListStr = new String(messageBytes);
            List<String> playerList = Arrays.asList(playerListStr.split("§"));
            plugin.getPlayerListManager().updatePlayerList(playerList);
        } else if (channel.equals(DataKeys.SYNC_INV_UPDATE.toString())) {
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

    public void publishSnapshotDiff(SnapshotDiff snapshotDiff) {
        getConnectionSync(connection ->
          connection.publish(
            DataKeys.SYNC_INV_UPDATE.getKeyBytes(),
            snapshotDiff.serializeToBytes()
          ));
    }

    public CompletableFuture<StoredSnapshot> getInv(UUID playerUUID) {
        return getThreadSafeConnectionAsyncWithRetry((connection, remainingTries) -> {
              byte[] uuidBytes = ByteBuffer.allocate(16)
                .putLong(playerUUID.getMostSignificantBits())
                .putLong(playerUUID.getLeastSignificantBits()).array();
              //zsync:inv<playerUUID> (HASHSET) SNAPSHOTID-SERIALIZED
              //zsync:p_lock<playerUUID> LATEST SNAPSHOTID 0 missing, SNAPSHOTID, if negative is locked.
              String latestHandling = remainingTries == 0 ?
                "latest=math.abs(latest)" : //If last try, "force unlock" the inv and return that
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

              long snapshotUniqueId = (Long) result.getFirst();

              if (snapshotUniqueId <= 0) { // < 0 means not valid snapshot data
                  return new StoredSnapshot(snapshotUniqueId, null);
              }
              DataSnapshot snapshot = DataSnapshot.deserialize((byte[]) result.get(1));

              return new StoredSnapshot(snapshotUniqueId, snapshot);
          }, (int) playerUUID.getLeastSignificantBits(),
          storedSnapshot -> !storedSnapshot.isLocked()); // Retry if the inventory is locked
    }

    public StoredSnapshot getInvSync(UUID playerUUID) {
        byte[] uuidBytes = ByteBuffer.allocate(16)
          .putLong(playerUUID.getMostSignificantBits())
          .putLong(playerUUID.getLeastSignificantBits()).array();
        final List<Object> result = getConnectionSync(connection ->
          connection.eval("""
              local latest=tonumber(redis.call("GET", KEYS[1]))
              if not latest then return {0} end
              if latest < 0 then return {-1} end
              local serialized=redis.call("HGET", KEYS[2],tostring(latest))
              redis.call("SET", KEYS[1], -latest)
              return {latest, serialized}
              """,
            ScriptOutputType.MULTI,
            new byte[][]{
              DataKeys.PLAYER_LOCK.append(uuidBytes),//zsync:p_lock<playerUUID> KEYS[1]
              DataKeys.SYNC_INV.append(uuidBytes), //zsync:inv<playerUUID> KEYS[2]
            }
          ));

        long snapshotUniqueId = (Long) result.getFirst();

        if (snapshotUniqueId <= 0) { // < 0 means not valid snapshot data
            return new StoredSnapshot(snapshotUniqueId, null);
        }
        DataSnapshot snapshot = DataSnapshot.deserialize((byte[]) result.get(1));

        return new StoredSnapshot(snapshotUniqueId, snapshot);
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
        return getThreadSafeConnectionAsync(
          createSaveSnapshotFunction(playerUUID, dataSnapshot),
          (int) playerUUID.getLeastSignificantBits());
    }

    private Function<RedisCommands<byte[], byte[]>, Long> createSaveSnapshotFunction(UUID playerUUID, DataSnapshot dataSnapshot) {
        return connection -> {
            byte[] uuidBytes = ByteBuffer.allocate(16)
              .putLong(playerUUID.getMostSignificantBits())
              .putLong(playerUUID.getLeastSignificantBits()).array();
            return connection.eval("""
                local previous=redis.call("GET", KEYS[3])
                if previous then
                redis.call("HEXPIRE",KEYS[2],ARGV[3],"FIELDS","1",math.abs(tonumber(previous)))
                end
                local newID=redis.call("INCR", KEYS[1])
                redis.call("HSET",KEYS[2],newID,ARGV[2])
                redis.call("SET", KEYS[3], newID)
                return newID
                """, ScriptOutputType.INTEGER,
              new byte[][]{
                DataKeys.COUNTER_GENERATOR.getKeyBytes(),//zsync:counter KEYS[1]
                DataKeys.SYNC_INV.append(uuidBytes),//zsync:inv KEYS[2]
                DataKeys.PLAYER_LOCK.append(uuidBytes),//zsync:p_lock:<playerUUID> KEYS[3]
              },
              uuidBytes, // ARGV[1] = player UUID bytes for hash field
              dataSnapshot.serialize(), // ARGV[2] = serialized inventory data
              String.valueOf(Settings.instance().snapshotExpirationSeconds).getBytes());// ARGV[3] = lock expiration time in seconds
        };
    }

    public long saveSnapshot(UUID playerUUID, DataSnapshot dataSnapshot) {
        return getConnectionSync(createSaveSnapshotFunction(playerUUID, dataSnapshot));
    }

}
