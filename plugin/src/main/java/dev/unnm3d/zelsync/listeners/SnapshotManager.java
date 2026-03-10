package dev.unnm3d.zelsync.listeners;

import dev.unnm3d.zelsync.ZelSync;
import dev.unnm3d.zelsync.core.managers.NewPlayerSnapshotManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public class SnapshotManager implements Listener {
    private final ZelSync plugin;
    private final ConcurrentHashMap<UUID, NewPlayerSnapshotManager> snapshotManagers = new ConcurrentHashMap<>();

    public SnapshotManager(ZelSync plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        NewPlayerSnapshotManager manager = new NewPlayerSnapshotManager(event.getPlayer(), plugin.getExecutorServiceRouter(), plugin);
        snapshotManagers.put(event.getPlayer().getUniqueId(), manager);//Set to null to mark the player as loading, so if they log out during the load we don't save an empty snapshot
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        snapshotManagers.remove(event.getPlayer().getUniqueId())
          .logoutAndGetSnapshot().exceptionally(ex -> {
              plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to save snapshot for player " + event.getPlayer().getName() + " on quit", ex);
              return null;
          });
    }

}
