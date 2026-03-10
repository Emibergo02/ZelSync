package dev.unnm3d.zelsync.core.contents;

import java.util.List;

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

    public List<Class<? extends SnapshotContent>> getContents() {
        return ContentRegistry.getRegisteredContents().stream()
          .filter(this::contains)
          .toList();
    }

    // Getters
    public int toInt() {
        return value;
    }

    public void with(Class<? extends SnapshotContent> flag) {
        this.value |= ContentRegistry.getContentId(flag);
    }

    public void without(Class<? extends SnapshotContent> flag) {
        this.value &= ~ContentRegistry.getContentId(flag);
    }

    public boolean contains(Class<? extends SnapshotContent> flag) {
        int flagMask = ContentRegistry.getContentId(flag);
        return (this.value & flagMask) == flagMask;
    }
}