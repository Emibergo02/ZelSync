package dev.unnm3d.zelsync.core.managers;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InventoryManager {

    private final ConcurrentHashMap<UUID, ItemStack[]> playerInventories;

    public InventoryManager() {
        this.playerInventories = new ConcurrentHashMap<>();
    }

    public void updateInventory(UUID playerUUID, ItemStack[] contents) {
        playerInventories.put(playerUUID, contents);
    }

}
