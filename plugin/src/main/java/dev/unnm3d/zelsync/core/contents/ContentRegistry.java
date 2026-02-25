package dev.unnm3d.zelsync.core.contents;


import dev.unnm3d.zelsync.configs.Settings;

import java.util.*;

public class ContentRegistry {
    private static final Map<Class<? extends SnapshotContent>, ContentFactory<? extends SnapshotContent>> factories = new HashMap<>();
    private static final Map<Class<? extends SnapshotContent>, Integer> contentTypeIds = new LinkedHashMap<>();

    static {
        if (Settings.instance().synchronization.saveInventory())
            locallyRegister(
              SnapshotContent.InventoryContent.class,
              new SnapshotContent.InventoryContent.Factory(),
              1);
        if (Settings.instance().synchronization.savePersistentDataContainer())
            locallyRegister(
              SnapshotContent.PersistentSnapshotContainerContent.class,
              new SnapshotContent.PersistentSnapshotContainerContent.Factory(),
              2
            );
        if (Settings.instance().synchronization.saveHealth())
            locallyRegister(
              SnapshotContent.HealthContent.class,
              new SnapshotContent.HealthContent.Factory(),
              4
            );
        if (Settings.instance().synchronization.saveFood())
            locallyRegister(
              SnapshotContent.FoodContent.class,
              new SnapshotContent.FoodContent.Factory(),
              8
            );
        if (Settings.instance().synchronization.saveExperience())
            locallyRegister(
              SnapshotContent.ExperienceContent.class,
              new SnapshotContent.ExperienceContent.Factory(),
              16
            );
        if (Settings.instance().synchronization.savePotionEffects())
            locallyRegister(
              SnapshotContent.EffectContent.class,
              new SnapshotContent.EffectContent.Factory(),
              32
            );
        if (Settings.instance().synchronization.saveEnderChest())
            locallyRegister(
              SnapshotContent.EnderChestContent.class,
              new SnapshotContent.EnderChestContent.Factory(),
              64
            );
        if (Settings.instance().synchronization.saveGamemode())
            locallyRegister(
              SnapshotContent.GamemodeContent.class,
              new SnapshotContent.GamemodeContent.Factory(),
              128
            );
        if (Settings.instance().synchronization.saveFlight())
            locallyRegister(
              SnapshotContent.FlightContent.class,
              new SnapshotContent.FlightContent.Factory(),
              256
            );
        if (Settings.instance().synchronization.saveLocation())
            locallyRegister(
              SnapshotContent.LocationContent.class,
              new SnapshotContent.LocationContent.Factory(),
              512
            );
    }

    private static <T extends SnapshotContent> void locallyRegister(Class<T> type, ContentFactory<T> factory, int contentFlagId) {
        factories.put(type, factory);
        contentTypeIds.put(type, contentFlagId);
    }

    public static <T extends SnapshotContent> void register(Class<T> type, ContentFactory<T> factory, int contentFlagId) {
        if (factories.containsKey(type)) {
            throw new IllegalArgumentException("Content type already registered: " + type.getName());
        }
        if (contentFlagId <= 37768) {
            throw new IllegalArgumentException("Content type ID must be greater than 37768 to avoid conflicts with built-in content types: " + contentFlagId);
        }
        locallyRegister(type, factory, contentFlagId);
    }

    @SuppressWarnings("unchecked")
    public static <T extends SnapshotContent> ContentFactory<T> get(Class<T> type) {
        return (ContentFactory<T>) factories.getOrDefault(type, new SnapshotContent.EmptyContent.Factory());
    }

    public static int getContentId(Class<? extends SnapshotContent> type) {
        Integer id = contentTypeIds.get(type);
        if (id == null) {
            throw new IllegalArgumentException("No content type ID registered for class: " + type.getName());
        }
        return id;
    }

    public static List<Class<? extends SnapshotContent>> getRegisteredContents() {
        return new ArrayList<>(contentTypeIds.keySet());
    }
}