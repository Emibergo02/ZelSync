package dev.unnm3d.zelsync.migrators;

import dev.unnm3d.zelsync.ZelSync;
import dev.unnm3d.zelsync.core.contents.ContentRegistry;
import dev.unnm3d.zelsync.core.contents.SnapshotContent;
import dev.unnm3d.zelsync.core.snapshots.DataSnapshot;
import dev.unnm3d.zelsync.utils.Utils;
import net.william278.husksync.api.HuskSyncAPI;
import net.william278.husksync.data.BukkitData;
import net.william278.husksync.data.Data;
import net.william278.husksync.data.DataHolder;
import net.william278.husksync.user.User;
import org.bukkit.GameMode;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

public class HuskSyncMigrator {

    private final HuskSyncAPI huskSyncAPI;
    private final ZelSync plugin;

    public HuskSyncMigrator(HuskSyncAPI huskSyncAPI, ZelSync plugin) {
        this.huskSyncAPI = huskSyncAPI;
        this.plugin = plugin;
    }

    /**
     * Migrates all HuskSync player data from the database into ZelSync's Redis store.
     * This is a blocking operation and should be run asynchronously.
     */
    public void migrate() {
        List<User> allUsers = huskSyncAPI.getPlugin().getDatabase().getAllUsers();
        int success = 0, failed = 0;

        plugin.getLogger().info("[HuskSyncMigrator] Starting migration of " + allUsers.size() + " users...");

        for (User user : allUsers) {
            try {
                Optional<net.william278.husksync.data.DataSnapshot.Packed> packed =
                        huskSyncAPI.getPlugin().getDatabase().getLatestSnapshot(user);

                if (packed.isEmpty()) {
                    plugin.getLogger().info("[HuskSyncMigrator] Skipping " + user.getName() + ": no snapshot found");
                    continue;
                }

                DataHolder dataHolder = packed.get().unpack(huskSyncAPI.getPlugin());
                DataSnapshot zelSnapshot = translate(dataHolder);
                plugin.getDataCache().saveSnapshot(user.getUuid(), zelSnapshot);

                success++;
                plugin.getLogger().info("[HuskSyncMigrator] Migrated " + user.getName()
                        + " (" + success + "/" + allUsers.size() + ")");
            } catch (Exception e) {
                failed++;
                plugin.getLogger().log(Level.WARNING,
                        "[HuskSyncMigrator] Failed to migrate " + user.getName(), e);
            }
        }

        plugin.getLogger().info("[HuskSyncMigrator] Migration complete: "
                + success + " succeeded, " + failed + " failed out of " + allUsers.size() + " users");
    }

    /**
     * Translates a HuskSync {@link DataHolder} into a ZelSync {@link DataSnapshot}.
     * Only content types registered in ZelSync's {@link ContentRegistry} are included.
     *
     * @param dataHolder the HuskSync data holder to translate
     * @return the translated ZelSync snapshot
     */
    public DataSnapshot translate(DataHolder dataHolder) {
        DataSnapshot snapshot = new DataSnapshot(DataSnapshot.SaveCause.LOGOUT);

        // Inventory
        dataHolder.getInventory().ifPresent(inventory -> {
            if (inventory instanceof BukkitData.Items items) {
                byte[] bytes = Utils.serialize(items.getContents());
                addContent(snapshot, SnapshotContent.InventoryContent.class, bytes);
            }
        });

        // Ender chest
        dataHolder.getEnderChest().ifPresent(enderChest -> {
            if (enderChest instanceof BukkitData.Items items) {
                byte[] bytes = Utils.serialize(items.getContents());
                addContent(snapshot, SnapshotContent.EnderChestContent.class, bytes);
            }
        });

        // Health (health, healthScale, absorption=0 — HuskSync does not track absorption)
        dataHolder.getHealth().ifPresent(health -> {
            ByteBuffer buffer = ByteBuffer.allocate(8 + 8 + 8);
            buffer.putDouble(health.getHealth());
            buffer.putDouble(health.getHealthScale());
            buffer.putDouble(0.0);
            addContent(snapshot, SnapshotContent.HealthContent.class, buffer.array());
        });

        // Food / Hunger
        dataHolder.getHunger().ifPresent(hunger -> {
            ByteBuffer buffer = ByteBuffer.allocate(4 + 4 + 4);
            buffer.putInt(hunger.getFoodLevel());
            buffer.putFloat(hunger.getSaturation());
            buffer.putFloat(hunger.getExhaustion());
            addContent(snapshot, SnapshotContent.FoodContent.class, buffer.array());
        });

        // Experience
        dataHolder.getExperience().ifPresent(experience -> {
            ByteBuffer buffer = ByteBuffer.allocate(4 + 4 + 4);
            buffer.putInt(experience.getTotalExperience());
            buffer.putInt(experience.getExpLevel());
            buffer.putFloat(experience.getExpProgress());
            addContent(snapshot, SnapshotContent.ExperienceContent.class, buffer.array());
        });

        // Potion effects
        dataHolder.getPotionEffects().ifPresent(potionEffects -> {
            List<Data.PotionEffects.Effect> effects = potionEffects.getActiveEffects();
            // Per-effect layout (matches EffectContent.fromBytes): 32-byte type key + int amplifier
            // + int duration + byte hasParticles + byte isAmbient + byte hasIcon = 43 bytes
            ByteBuffer buffer = ByteBuffer.allocate(4 + effects.size() * 43);
            buffer.putInt(effects.size());
            for (Data.PotionEffects.Effect effect : effects) {
                byte[] typeBytes = new byte[32];
                byte[] typeNameBytes = effect.type().getBytes();
                System.arraycopy(typeNameBytes, 0, typeBytes, 0, Math.min(typeNameBytes.length, 32));
                buffer.put(typeBytes);
                buffer.putInt(effect.amplifier());
                buffer.putInt(effect.duration());
                buffer.put(effect.showParticles() ? (byte) 1 : (byte) 0);
                buffer.put(effect.isAmbient() ? (byte) 1 : (byte) 0);
                buffer.put(effect.hasIcon() ? (byte) 1 : (byte) 0);
            }
            addContent(snapshot, SnapshotContent.EffectContent.class, buffer.array());
        });

        // Game mode
        dataHolder.getGameMode().ifPresent(gameMode -> {
            try {
                GameMode mode = GameMode.valueOf(gameMode.getGameMode().toUpperCase());
                ByteBuffer buffer = ByteBuffer.allocate(4);
                buffer.putInt(mode.getValue());
                addContent(snapshot, SnapshotContent.GamemodeContent.class, buffer.array());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[HuskSyncMigrator] Unknown game mode: " + gameMode.getGameMode());
            }
        });

        // Flight status (HuskSync does not track fly speed — default 0.2f)
        dataHolder.getFlightStatus().ifPresent(flightStatus -> {
            ByteBuffer buffer = ByteBuffer.allocate(1 + 1 + 4);
            buffer.put((byte) (flightStatus.isAllowFlight() ? 1 : 0));
            buffer.put((byte) (flightStatus.isFlying() ? 1 : 0));
            buffer.putFloat(0.2f);
            addContent(snapshot, SnapshotContent.FlightContent.class, buffer.array());
        });

        return snapshot;
    }

    /**
     * Deserializes {@code bytes} into a {@link SnapshotContent} via the registered factory and adds
     * it to the snapshot. Silently skips content types that are not registered (disabled in config).
     */
    private <T extends SnapshotContent> void addContent(DataSnapshot snapshot, Class<T> type, byte[] bytes) {
        try {
            T content = ContentRegistry.get(type).fromBytes(bytes);
            snapshot.addContent(content);
        } catch (IllegalStateException e) {
            // Content type not registered (disabled in config) — skip silently
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "[HuskSyncMigrator] Failed to translate " + type.getSimpleName(), e);
        }
    }
}
