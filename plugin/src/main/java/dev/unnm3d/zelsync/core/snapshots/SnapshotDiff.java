package dev.unnm3d.zelsync.core.snapshots;

import dev.unnm3d.zelsync.utils.Utils;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;


public class SnapshotDiff {
    @Getter
    private final StoredSnapshot baseSnapshot;
    private final byte[] baseSnapshotBytes;
    @Setter
    private byte[] diffBytes;

    public SnapshotDiff(@NotNull StoredSnapshot baseSnapshot) {
        this.baseSnapshot = baseSnapshot;
        this.baseSnapshotBytes = baseSnapshot.getSnapshot().serialize();
        this.diffBytes = null;
    }

    public void recalculateDiff(@NotNull DataSnapshot newSnapshot) {
        try {
            this.diffBytes = Utils.computeDiff(baseSnapshotBytes, newSnapshot.serialize());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] serializeToBytes() {
        return ByteBuffer.allocate(8 + this.diffBytes.length)
          .putLong(baseSnapshot.getId())
          .put(this.diffBytes)
          .array();
    }

    public StoredSnapshot calculateSnapshot() {
        if (diffBytes == null) {
            return baseSnapshot;
        }
        try {
            return new StoredSnapshot(baseSnapshot.getId(),
              new DataSnapshot(
                Utils.applyDiff(baseSnapshotBytes, diffBytes)
              ));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
