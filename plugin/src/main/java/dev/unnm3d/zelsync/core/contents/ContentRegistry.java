package dev.unnm3d.zelsync.core.contents;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ContentRegistry {
    private static final Map<Class<? extends SnapshotContent>, ContentFactory<? extends SnapshotContent>> factories = new HashMap<>();
    private static final Map<Class<? extends SnapshotContent>, Byte> contentTypeIds = new HashMap<>();

    static {
        register(
          SnapshotContent.InventoryContent.class,
          new SnapshotContent.InventoryContent.Factory(),
          (byte) 1);
        register(
          SnapshotContent.PersistentSnapshotContainerContent.class,
          new SnapshotContent.PersistentSnapshotContainerContent.Factory(),
          (byte) 2
        );
        register(
          SnapshotContent.HealthContent.class,
          new SnapshotContent.HealthContent.Factory(),
          (byte) 4
        );
        register(
          SnapshotContent.FoodContent.class,
          new SnapshotContent.FoodContent.Factory(),
          (byte) 8
        );
        register(
          SnapshotContent.ExperienceContent.class,
          new SnapshotContent.ExperienceContent.Factory(),
          (byte) 16
        );
        register(
          SnapshotContent.EffectContent.class,
          new SnapshotContent.EffectContent.Factory(),
          (byte) 32
        );
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
        return (ContentFactory<T>) factories.get(type);
    }

    public static byte getContentId(Class<? extends SnapshotContent> type) {
        Byte id = contentTypeIds.get(type);
        if (id == null) {
            throw new IllegalArgumentException("No content type ID registered for class: " + type.getName());
        }
        return id;
    }

    public static List<Class<? extends SnapshotContent>> getRegisteredContents() {
        return new ArrayList<>(factories.keySet());
    }
}