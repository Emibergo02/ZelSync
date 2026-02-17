package dev.unnm3d.zelsync.listeners;

import dev.unnm3d.zelsync.ZelSync;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class JoinListener implements Listener {
    ZelSync plugin;

    @EventHandler
    public void onLogin(PlayerLoginEvent event) {
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
    }

    @EventHandler
    public void onJoin(PlayerQuitEvent event) {
        RedisDataManager
    }

}
