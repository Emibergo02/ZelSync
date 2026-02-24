package dev.unnm3d.zelsync.listeners;

import dev.unnm3d.zelsync.ZelSync;
import dev.unnm3d.zelsync.core.DataSnapshot;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;


public class JoinListener implements Listener {
    private final ZelSync plugin;
    private final ConcurrentHashMap<UUID, CompletableFuture<Void>> currentLoads;
    private final ConcurrentHashMap<UUID, LockListener> lockListeners = new ConcurrentHashMap<>();

    public JoinListener(ZelSync plugin) {
        this.plugin = plugin;
        this.currentLoads = new ConcurrentHashMap<>();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        LockListener lockListener = new LockListener(plugin, event.getPlayer());
        lockListeners.put(event.getPlayer().getUniqueId(), lockListener);
        currentLoads.put(event.getPlayer().getUniqueId(), plugin.getInventoryManager().loadInventory(event.getPlayer())
          .exceptionally(ex -> {
              plugin.getLogger().severe("Failed to load inventory for player " + event.getPlayer().getName() + ": " + ex.getMessage());
              event.getPlayer().kick(
                Component.text("Failed to load your inventory. Please try again later."),
                PlayerKickEvent.Cause.PLUGIN
              );
              return null;
          }).thenAccept((lr) -> {
              if (lr == null) return;
              lockListener.unregister();
              currentLoads.remove(event.getPlayer().getUniqueId());
          }));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (currentLoads.containsKey(event.getPlayer().getUniqueId())) {
            currentLoads.remove(event.getPlayer().getUniqueId());
            lockListeners.remove(event.getPlayer().getUniqueId()).unregister();
            return;
        }
        final DataSnapshot snapshot = new DataSnapshot(event.getPlayer(), DataSnapshot.SaveCause.LOGOUT);
        plugin.getDataCache().saveSnapshotScript(event.getPlayer().getUniqueId(), snapshot)
          .thenAccept(result -> {
              plugin.getLogger().info("The saved inventory for player " + event.getPlayer().getName() + " has ID " + result);
          }).exceptionally(ex -> {
              plugin.getLogger().log(Level.SEVERE, "Failed to save inventory for player " + event.getPlayer().getName(), ex);
              return null;
          });
    }

}
