package dev.unnm3d.zelsync.core.contents;


import dev.unnm3d.zelsync.configs.Settings;

import java.util.*;

public class ContentRegistry {
    private static final Map<Class<? extends SnapshotContent>, ContentFactory<? extends SnapshotContent>> factories = new HashMap<>();
    private static final Map<Class<? extends SnapshotContent>, Byte> contentTypeIds = new LinkedHashMap<>();

    static {
        if (Settings.instance().synchronization.saveInventory())
            register(
              SnapshotContent.InventoryContent.class,
              new SnapshotContent.InventoryContent.Factory(),
              (byte) 1);
        if (Settings.instance().synchronization.savePersistentDataContainer())
            register(
              SnapshotContent.PersistentSnapshotContainerContent.class,
              new SnapshotContent.PersistentSnapshotContainerContent.Factory(),
              (byte) 2
            );
        if (Settings.instance().synchronization.saveHealth())
            register(
              SnapshotContent.HealthContent.class,
              new SnapshotContent.HealthContent.Factory(),
              (byte) 4
            );
        if (Settings.instance().synchronization.saveFood())
            register(
              SnapshotContent.FoodContent.class,
              new SnapshotContent.FoodContent.Factory(),
              (byte) 8
            );
        if (Settings.instance().synchronization.saveExperience())
            register(
              SnapshotContent.ExperienceContent.class,
              new SnapshotContent.ExperienceContent.Factory(),
              (byte) 16
            );
        if (Settings.instance().synchronization.savePotionEffects())
            register(
              SnapshotContent.EffectContent.class,
              new SnapshotContent.EffectContent.Factory(),
              (byte) 32
            );
        if (Settings.instance().synchronization.saveEnderChest())
            register(
              SnapshotContent.EnderChestContent.class,
              new SnapshotContent.EnderChestContent.Factory(),
              (byte) 64
            );
    }

    public static <T extends SnapshotContent> void register(Class<T> type, ContentFactory<T> factory, byte contentTypeId) {
        factories.put(type, factory);
        contentTypeIds.put(type, contentTypeId);
    }

    @SuppressWarnings("unchecked")
    public static <T extends SnapshotContent> ContentFactory<T> get(Class<T> type) {
        return (ContentFactory<T>) factories.getOrDefault(type, new SnapshotContent.EmptyContent.Factory());
    }

    public static byte getContentId(Class<? extends SnapshotContent> type) {
        Byte id = contentTypeIds.get(type);
        if (id == null) {
            throw new IllegalArgumentException("No content type ID registered for class: " + type.getName());
        }
        return id;
    }

    public static List<Class<? extends SnapshotContent>> getRegisteredContents() {
        return new ArrayList<>(contentTypeIds.keySet());
    }
}