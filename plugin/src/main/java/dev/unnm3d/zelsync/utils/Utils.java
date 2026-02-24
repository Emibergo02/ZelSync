package dev.unnm3d.zelsync.utils;

import dev.unnm3d.zelsync.ZelSync;
import lombok.experimental.UtilityClass;
import org.bukkit.inventory.ItemStack;
import org.xerial.snappy.Snappy;

import java.io.IOException;
import java.util.logging.Level;

@UtilityClass
public class Utils {

    public byte[] serialize(ItemStack... items) {
        try {
            return ItemStack.serializeItemsAsBytes(items);
        } catch (Exception exception) {
            exception.printStackTrace();
            return new byte[0];
        }
    }

    public ItemStack[] deserialize(byte[] source) {
        try {
            return ItemStack.deserializeItemsFromBytes(source);
        } catch (Exception exception) {
            ZelSync.getInstance().getLogger().log(Level.SEVERE, "Error during item array decompression: " +
              "you probably need to erase your archived trades table if you updated the plugin to a new version. Fix this issue with " +
              "/zeltrade wipearchives !!!!", exception);
            return new ItemStack[0];
        }
    }

    public static byte[] compress(byte[] input) {
        try {
            byte[] compressed = Snappy.compress(input);
            ZelSync.getInstance().getLogger().info("Compressed data from " + input.length + " bytes to " + compressed.length);
            return compressed;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] decompress(byte[] compressedInput) {
        try {
            return Snappy.uncompress(compressedInput);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
