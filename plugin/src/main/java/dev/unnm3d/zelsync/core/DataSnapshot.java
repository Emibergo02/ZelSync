package dev.unnm3d.zelsync.core;

import dev.unnm3d.zelsync.ZelSync;
import dev.unnm3d.zelsync.core.contents.ContentFlag;
import dev.unnm3d.zelsync.core.contents.ContentRegistry;
import dev.unnm3d.zelsync.core.contents.SnapshotContent;
import dev.unnm3d.zelsync.utils.Utils;
import lombok.Getter;
import org.bukkit.entity.Player;

import java.io.*;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;


@Getter
public class DataSnapshot {
    private int serverId;
    private SaveCause saveCause;
    private long timestamp;
    private ContentFlag contentFlag;
    private Map<Class<? extends SnapshotContent>, SnapshotContent> contentMap;

    public DataSnapshot(Player player, SaveCause saveCause) {
        this.serverId = ZelSync.getServerId();
        this.saveCause = saveCause;
        this.timestamp = System.currentTimeMillis();
        this.contentFlag = ContentFlag.empty();
        this.contentMap = new HashMap<>();
        for (Class<? extends SnapshotContent> contentClass : ContentRegistry.getRegisteredContents()) {
            this.contentMap.put(contentClass, ContentRegistry.get(contentClass).fromPlayer(player));
            this.contentFlag.with(contentClass);
        }
    }

    public DataSnapshot(byte[] data) {
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data))) {
            this.serverId = dis.readInt();
            this.saveCause = SaveCause.fromId(dis.readShort());
            this.timestamp = dis.readLong();
            this.contentFlag = ContentFlag.fromInt(dis.readInt());
            this.contentMap = new HashMap<>();
            ZelSync.getInstance().getLogger()
              .info("ServerID: " + serverId + ", SaveCause: " + saveCause + ", Timestamp: " + timestamp + ", ContentFlag: " + contentFlag.toInt());

            for (Class<? extends SnapshotContent> contentClass : contentFlag.getContents()) {
                ZelSync.getInstance().getLogger().info("Attempting to read content: " + contentClass.getSimpleName() + " | Bytes left in stream: " + dis.available());

                int length = dis.readInt();
                byte[] contentBytes = new byte[length];
                dis.readFully(contentBytes);
                ZelSync.getInstance().getLogger()
                  .info(contentClass.getSimpleName() + " content "+length+" bytes: " + Base64.getEncoder().encodeToString(contentBytes));

                this.contentMap.put(contentClass, ContentRegistry.get(contentClass).fromBytes(contentBytes));
            }
        } catch (IOException e) {
            ZelSync.getInstance().getLogger().log(Level.SEVERE, "Failed to deserialize DataSnapshot", e);
        }
    }


    public static DataSnapshot deserialize(byte[] data) {
        return new DataSnapshot(Utils.decompress(data));
    }

    public byte[] serialize() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        try {
            dos.writeInt(serverId);
            dos.writeShort(saveCause.getId());
            dos.writeLong(timestamp);
            dos.writeInt(contentFlag.toInt());
            ZelSync.getInstance().getLogger()
              .info("ServerID: " + serverId + ", SaveCause: " + saveCause + ", Timestamp: " + timestamp + ", ContentFlag: " + contentFlag.toInt());

            for (Class<? extends SnapshotContent> contentClass : contentFlag.getContents()) {
                byte[] bytes = contentMap.get(contentClass).serialize();
                dos.writeInt(bytes.length);
                dos.write(bytes);
                ZelSync.getInstance().getLogger()
                  .info(contentClass.getSimpleName() + " content bytes length: " + bytes.length);
            }
        } catch (IOException e) {
            ZelSync.getInstance().getLogger().log(Level.SEVERE, "Failed to serialize DataSnapshot", e);
        }
        return Utils.compress(baos.toByteArray());
    }

    @Getter
    public enum SaveCause {
        LOGOUT((short) 1),
        PERIODIC((short) 2);

        private final short id;

        SaveCause(short id) {
            this.id = id;
        }

        public static SaveCause fromId(short id) {
            for (SaveCause cause : values()) {
                if (cause.id == id) {
                    return cause;
                }
            }
            throw new IllegalArgumentException("Invalid SaveCause id: " + id);
        }
    }
}
