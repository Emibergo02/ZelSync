package dev.unnm3d.zelsync.core.managers;

import dev.unnm3d.zelsync.ZelSync;
import dev.unnm3d.zelsync.core.snapshots.DataSnapshot;
import dev.unnm3d.zelsync.core.snapshots.SnapshotDiff;
import dev.unnm3d.zelsync.core.snapshots.StoredSnapshot;
import dev.unnm3d.zelsync.listeners.LockListener;
import dev.unnm3d.zelsync.utils.ExecutorServiceRouter;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerKickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;


public class NewPlayerSnapshotManager {
    private final ZelSync plugin;
    @Getter
    private final Player player;
    @Getter
    private final LockListener lockListener;

    /**
     * The diff between the last snapshot and the current state of the player, recalculated every 10 seconds and on logout
     */
    @Getter
    private CompletableFuture<SnapshotDiff> snapshotDiff;

    /**
     * The executor that keeps the actions thread-safe by routing them to the same thread based on the player's UUID
     */
    private final ExecutorServiceRouter executorRouter;

    /**
     * The scheduled task that recalculates the snapshot diff every 10 seconds
     */
    private ScheduledFuture<?> diffTimer;

    public CompletableFuture<StoredSnapshot> getBaseSnapshot() {
        return snapshotDiff.thenApply(SnapshotDiff::getBaseSnapshot);
    }

    public NewPlayerSnapshotManager(Player player, ExecutorServiceRouter executorRouter, ZelSync plugin) {
        this.player = player;
        this.executorRouter = executorRouter;
        this.plugin = plugin;
        this.lockListener = new LockListener(player);
        this.lockListener.register(plugin);
        this.snapshotDiff = plugin.getDataCache().getInv(player.getUniqueId())
          .thenCompose(storedSn -> applyOrCreate(player, storedSn))
          .thenApply(storedSn -> {
              this.lockListener.unregister();
              startDiffTimer();
              return new SnapshotDiff(storedSn);
          }).exceptionally(ex -> {
              errAndKick(new IllegalStateException("Error loading inventory for player " + player.getName(), ex));
              return null;
          });
    }

    private CompletableFuture<StoredSnapshot> applyOrCreate(Player player, final StoredSnapshot storedSn) {
        CompletableFuture<StoredSnapshot> future = new CompletableFuture<>();
        if (storedSn == null) {
            future.completeExceptionally(new IllegalStateException("Received null snapshot data from Redis for player " + player.getName()));
        } else if (storedSn.isValid()) {
            player.getScheduler().run(plugin, (task) ->
              storedSn.getSnapshot().getContentMap().values().forEach(content -> {
                  ZelSync.debug("Applying content " + content.getClass().getSimpleName() + " to player " + player.getName());
                  content.apply(player);
                  player.sendActionBar(Component.text("Inventory loaded").color(net.kyori.adventure.text.format.NamedTextColor.GREEN));
                  future.complete(storedSn);
              }), () -> {
                plugin.getDataCache().saveSnapshotScript(player.getUniqueId(), storedSn.getSnapshot());
                future.completeExceptionally(new IllegalStateException("Player " + player.getName() + " went offline during inventory load"));
            });
        } else if (storedSn.isFirstLogin()) {
            DataSnapshot snapshot = new DataSnapshot(player, DataSnapshot.SaveCause.LOGOUT);
            plugin.getDataCache().saveSnapshotScript(player.getUniqueId(), snapshot).thenAccept((id) -> {
                if (id == null || id <= 0) {
                    future.completeExceptionally(new IllegalStateException(
                      "Failed to save snapshot for player " + player.getName() +
                        " during first login: Received invalid snapshot ID " + id));
                    return;
                }
                ZelSync.debug("The saved inventory for first login player " + player.getName() + " has ID " + id);
                future.complete(new StoredSnapshot(id, snapshot));
            });
        } else {
            future.completeExceptionally(new IllegalStateException("Received invalid snapshot for player " + player.getName() + " with id " + storedSn.getId()));
        }
        return future;
    }

    /**
     * Recalculates the snapshot diff one last time, saves the new snapshot to the database.
     * If the players log out before the snapshot is loaded, it returns null and doesn't save anything
     *
     * @return A future that completes with the new stored snapshot, or null if the player logged out before the login snapshot was loaded
     */
    public CompletableFuture<@Nullable StoredSnapshot> logoutAndGetSnapshot() {
        final CompletableFuture<StoredSnapshot> future = new CompletableFuture<>();
        if (diffTimer != null) this.diffTimer.cancel(true);
        if (!snapshotDiff.isDone()) {
            lockListener.unregister();
            future.complete(null);
            return future;
        }
        final DataSnapshot snapshot = new DataSnapshot(player, DataSnapshot.SaveCause.LOGOUT);
        this.executorRouter.route(() -> {
            recalculateAndSendDiff();//The last time. Timer has been canceled
            long newId = ZelSync.getInstance().getDataCache().saveSnapshot(player.getUniqueId(), snapshot);
            if(newId <= 0) {
                future.completeExceptionally(new IllegalStateException("Failed to save snapshot for player " + player.getName() + " on logout: Received invalid snapshot ID " + newId));
                return;
            }
            ZelSync.debug("The saved inventory for logging out player " + player.getName() + " has ID " + newId);
            future.complete(new StoredSnapshot(newId, snapshot));
        }, (int) player.getUniqueId().getLeastSignificantBits());
        return future;
    }

    private void startDiffTimer() {
        this.diffTimer = executorRouter.routeScheduleAtFixedRate(
          (int) player.getUniqueId().getLeastSignificantBits(),
          this::recalculateAndSendDiff,
          10,
          10,
          java.util.concurrent.TimeUnit.SECONDS);
    }

    private void recalculateAndSendDiff() {
        try {
            long startTime = System.currentTimeMillis();
            this.snapshotDiff.get().recalculateDiff(new DataSnapshot(player, DataSnapshot.SaveCause.LOGOUT));
            plugin.getLogger().info("Recalculated snapshot length: "+ this.snapshotDiff.get().serializeToBytes().length + " bytes for player " + player.getName() + " in " + (System.currentTimeMillis() - startTime) + " ms");
            ZelSync.getInstance().getDataCache().publishSnapshotDiff(this.snapshotDiff.get());
        } catch (Exception e) {
            ZelSync.getInstance().getLogger().log(java.util.logging.Level.SEVERE, "Error calculating snapshot diff for player " + player.getName(), e);
        }
    }

    private void errAndKick(Exception ex) {
        plugin.getLogger().log(java.util.logging.Level.SEVERE, "Error loading inventory for player " + player.getName(), ex);
        player.kick(
          Component.text("Failed to load your inventory. Please try again later."),
          PlayerKickEvent.Cause.PLUGIN
        );
    }
}
