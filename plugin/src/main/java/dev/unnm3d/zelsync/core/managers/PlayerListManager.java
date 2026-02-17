package dev.unnm3d.zelsync.core.managers;

import dev.unnm3d.zelsync.ZelSync;
import dev.unnm3d.zeltrade.api.core.managers.IPlayerListManager;
import dev.unnm3d.zelsync.utils.License;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.HumanEntity;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class PlayerListManager {
    private final ScheduledTask task;
    private final ConcurrentHashMap<String, Long> onlinePlayerList;
    private final ConcurrentHashMap<String, UUID> nameUUIDs;


    public PlayerListManager(ZelSync plugin) {
        this.onlinePlayerList = new ConcurrentHashMap<>();
        this.nameUUIDs = new ConcurrentHashMap<>();
        this.task = plugin.getServer().getAsyncScheduler().runAtFixedRate(plugin, (task) -> {
            onlinePlayerList.entrySet().removeIf(stringLongEntry ->
              System.currentTimeMillis() - stringLongEntry.getValue() > 1000 * 4);

            final List<String> tempList = plugin.getServer().getOnlinePlayers().stream()
              .map(HumanEntity::getName)
              .filter(n -> !n.isEmpty())
              .toList();

            if (!tempList.isEmpty())
                plugin.getDataCache().publishPlayerList(tempList);
            tempList.forEach(s -> onlinePlayerList.put(s, System.currentTimeMillis()));
        }, 0L, 3L, TimeUnit.SECONDS);//Every 3 secs

        License.getLicenseById().validCheck();

        plugin.getDataStorage().loadNameUUIDs()
          .thenAccept(map -> {
              //Allow the plugin to load the players that are already online
              plugin.getServer().getOnlinePlayers().forEach(player ->
                map.put(player.getName(), player.getUniqueId())
              );
              nameUUIDs.clear();
              nameUUIDs.putAll(map);

              plugin.getLogger().info("Loaded " + map.size() + " nameUUIDs from database");
          });

    }

    public void updatePlayerList(List<String> inPlayerList) {
        long currentTimeMillis = System.currentTimeMillis();
        inPlayerList.forEach(s -> {
            if (s != null && !s.isEmpty())
                onlinePlayerList.put(s, currentTimeMillis);
        });
    }

    public Set<String> getPlayerList(@Nullable CommandSender sender) {
        final Set<String> keySet = new HashSet<>(onlinePlayerList.keySet());
        //Vanish integration
        return keySet;
    }

    public Optional<UUID> getPlayerUUID(String name) {
        return Optional.ofNullable(nameUUIDs.get(name));
    }

    public void setPlayerNameUUID(String name, UUID uuid) {
        nameUUIDs.put(name, uuid);
    }

    public Optional<String> getPlayerName(UUID uuid) {
        for (Map.Entry<String, UUID> stringUUIDEntry : nameUUIDs.entrySet()) {
            if (stringUUIDEntry.getValue().equals(uuid)) {
                return Optional.of(stringUUIDEntry.getKey());
            }
        }
        return Optional.empty();
    }

    public void stop() {
        task.cancel();
    }

    public record Target(String playerName) {
        public boolean all() {
            return "*".equals(playerName) || "-ALL-".equalsIgnoreCase(playerName);
        }
    }
}
