package dev.unnm3d.zelsync.core;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;


public class ContentFlag {
    // 'value' is a clearer name than 'flag' for the aggregate integer
    private int value;

    // Private constructor to force use of static factory methods
    private ContentFlag(int value) {
        this.value = value;
    }

    // Static factories for clear instantiation
    public static ContentFlag empty() {
        return new ContentFlag(0);
    }

    public static ContentFlag fromInt(int value) {
        return new ContentFlag(value);
    }

    public static ContentFlag of(ContentType... flags) {
        int combined = 0;
        for (ContentType flag : flags) {
            combined |= flag.getFlag();
        }
        return new ContentFlag(combined);
    }

    public List<ContentType> getContents() {
        return Arrays.stream(ContentType.values())
          .filter(this::contains)
          .toList();
    }

    // Getters
    public int toInt() {
        return value;
    }

    public ContentFlag with(ContentType flag) {
        this.value |= flag.getFlag();
        return this;
    }

    public boolean contains(ContentType flag) {
        int flagMask = flag.getFlag();
        return (this.value & flagMask) == flagMask;
    }

    @Getter
    @AllArgsConstructor
    public enum ContentType {
        INV(1, DataContent.InventoryContent::new, DataContent.InventoryContent::new),
        PDC(2, DataContent.PersistentDataContainerContent::new, DataContent.PersistentDataContainerContent::new),
        HEALTH(4, DataContent.HealthContent::new, DataContent.HealthContent::new),
        FOOD(8, DataContent.FoodContent::new, DataContent.FoodContent::new),
        EXPERIENCE(16, DataContent.ExperienceContent::new, DataContent.ExperienceContent::new),
        ATTRIBUTES(64, null, null),
        EFFECTS(128, null, null),
        LOCATION(256, null, null),
        ENDERCHEST(512, DataContent.EnderChestContent::new, DataContent.EnderChestContent::new),
        GAMEMODE(1024, null, null),
        FLIGHT(2048, null, null),
        CUSTOM(4086, null, null);

        private final int flag;
        private final Function<Player, DataContent<?>> extractor;
        private final Function<byte[], DataContent<?>> deserializer;

    }
}
