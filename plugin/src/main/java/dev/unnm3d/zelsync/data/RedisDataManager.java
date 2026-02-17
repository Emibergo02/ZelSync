package dev.unnm3d.zelsync.data;

import dev.unnm3d.zelsync.ZelSync;
import dev.unnm3d.zelsync.api.data.DataKeys;
import dev.unnm3d.zelsync.redistools.RedisAbstract;
import dev.unnm3d.zelsync.utils.Utils;
import org.bukkit.inventory.ItemStack;

import java.nio.ByteBuffer;
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
            ByteBuffer buffer = ByteBuffer.wrap(messageBytes);
            long mostSigBits = buffer.getLong();
            long leastSigBits = buffer.getLong();
            UUID playerUUID = new UUID(mostSigBits, leastSigBits);
            byte[] serializedInv = new byte[buffer.remaining()];
            buffer.get(serializedInv);
            ItemStack[] contents = Utils.deserialize(serializedInv);
        }
    }

    public void saveInv(UUID playerUUID, ItemStack[] contents) {
        byte[] serialized = Utils.serialize(contents);
        byte[] uuidBytes = ByteBuffer.allocate(8)
          .putLong(playerUUID.getMostSignificantBits())
          .putLong(playerUUID.getLeastSignificantBits()).array();
        byte[] allData = new byte[uuidBytes.length + serialized.length];
        System.arraycopy(uuidBytes, 0, allData, 0, uuidBytes.length);
        System.arraycopy(serialized, 0, allData, uuidBytes.length, serialized.length);
        executeTransaction(connection -> {
              connection.hset(DataKeys.SYNC_INV.getKeyBytes(), uuidBytes, serialized);
              connection.publish(DataKeys.SYNC_INV_UPDATE.getKeyBytes(), allData);
          }, (int) playerUUID.getLeastSignificantBits());
    }

    public CompletableFuture<ItemStack[]> getInv(UUID playerUUID) {
        return getThreadSafeConnectionAsync(connection -> {
            byte[] uuidBytes = ByteBuffer.allocate(16)
              .putLong(playerUUID.getMostSignificantBits())
              .putLong(playerUUID.getLeastSignificantBits()).array();
            byte[] serialized = connection.hget(DataKeys.SYNC_INV.getKeyBytes(), uuidBytes);
            if (serialized == null) {
                return new ItemStack[0];
            }
            return Utils.deserialize(serialized);
        }, (int) playerUUID.getLeastSignificantBits());
    }

}
