package dev.unnm3d.zelsync.core.contents;


import org.bukkit.entity.Player;

public interface ContentFactory<T extends SnapshotContent> {
    T fromPlayer(Player player);

    T fromBytes(byte[] bytes);
}
