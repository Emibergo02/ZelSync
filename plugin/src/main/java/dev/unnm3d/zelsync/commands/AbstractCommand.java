package dev.unnm3d.zelsync.commands;

import dev.unnm3d.zelsync.ZelSync;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public class AbstractCommand {
    protected final ZelSync plugin;
    private final ConcurrentHashMap<UUID, Long> cmdCooldown = new ConcurrentHashMap<>();

    public AbstractCommand() {
        this.plugin = ZelSync.getInstance();
    }

    protected boolean cooldown(UUID playerUUID) {
        if (cmdCooldown.containsKey(playerUUID)) {
            if (cmdCooldown.get(playerUUID) > System.currentTimeMillis()) {
                return true;
            }
            cmdCooldown.values().removeIf(aLong -> aLong < System.currentTimeMillis());
        }
        //cmdCooldown.put(playerUUID, System.currentTimeMillis() + Settings.instance().commandCooldown);
        return false;
    }
}
