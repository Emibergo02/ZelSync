package dev.unnm3d.zelsync.core.managers;

import dev.unnm3d.zelsync.ZelSync;
import dev.unnm3d.zelsync.core.QueryResult;
import dev.unnm3d.zelsync.core.StoredSnapshot;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;


public class InventoryManager {
    private final ZelSync plugin;

    public InventoryManager(ZelSync plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<StoredSnapshot> loadInventory(Player player) {
        final CompletableFuture<StoredSnapshot> localCacheFuture = new CompletableFuture<>();
        plugin.getDataCache().getInv(player.getUniqueId()).thenAccept(storedSn -> {
            if (storedSn == null) {
                localCacheFuture.completeExceptionally(new IllegalStateException("Received null snapshot data from Redis for player " + player.getName()));
                return;
            }
            if (storedSn.getResult() == QueryResult.SUCCESS) {
                player.getScheduler().run(plugin, (task) -> {
                    if (player.isOnline()) {
                        storedSn.getDataSnapshot().getContentMap().values().forEach(content -> {
                            plugin.getLogger().info("Applying content " + content.getClass().getSimpleName() + " to player " + player.getName());
                            content.apply(player);
                        });
                        localCacheFuture.complete(storedSn);
                        return;
                    }
                    //Player is offline, send the data back to Redis to be saved in the local cache for when they come back online
                    storedSn.setResult(QueryResult.OFFLINE);
                    plugin.getDataCache().saveSnapshotScript(player.getUniqueId(), storedSn.getDataSnapshot());
                    localCacheFuture.complete(storedSn);
                }, () -> {
                    storedSn.setResult(QueryResult.OFFLINE);
                    plugin.getDataCache().saveSnapshotScript(player.getUniqueId(), storedSn.getDataSnapshot());
                    localCacheFuture.complete(storedSn);
                });
            }

            localCacheFuture.complete(storedSn);
        }).exceptionally(ex -> {
            localCacheFuture.completeExceptionally(ex);
            return null;
        });
        return localCacheFuture;
    }
}
