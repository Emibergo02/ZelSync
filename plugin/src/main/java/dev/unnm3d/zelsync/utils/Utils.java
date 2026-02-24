package dev.unnm3d.zelsync.utils;

import dev.unnm3d.zelsync.ZelSync;
import lombok.experimental.UtilityClass;
import org.bukkit.inventory.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.logging.Level;
import java.util.zip.Deflater;
import java.util.zip.DeflaterInputStream;
import java.util.zip.InflaterInputStream;

@UtilityClass
public class Utils {
    public enum CompressionLevel {
        SEND(1),
        STORAGE(9);

        private final int level;

        CompressionLevel(int level) {
            this.level = level;
        }

    }

    public String parseDoubleFormat(double value) {
        return new DecimalFormat("#.##").format(value);
    }

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

    public static byte[] compress(byte[] input, CompressionLevel level) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(input);
             DeflaterInputStream dis = new DeflaterInputStream(bis, new Deflater(level.level));
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[8192];
            int len;

            // Read compressed bytes from 'dis' and write them to 'bos'
            while ((len = dis.read(buffer)) > 0) {
                bos.write(buffer, 0, len);
            }

            return bos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return new byte[0];
        }
    }

    public static byte[] decompress(byte[] compressedInput) {
        return decompress(new ByteArrayInputStream(compressedInput));
    }

    public static byte[] decompress(InputStream compressedInput) {
        try (InflaterInputStream iis = new InflaterInputStream(compressedInput);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[8192];
            int len;
            while ((len = iis.read(buffer)) > 0) {
                bos.write(buffer, 0, len);
            }
            return bos.toByteArray();
        } catch (Exception e) {
            ZelSync.getInstance().getLogger().log(Level.SEVERE, "Error during bytes decompression, " +
              "you probably need to erase your archived trades table if you updated the plugin to a new version. Fix this issue with " +
              "/zeltrade wipearchives !!!!", e);
            return new byte[0];
        }
    }

    public String starsOf(double rating) {
        int fullStars = (int) rating;
        double halfStars = rating - fullStars;
        boolean halfStar = 0.25 < halfStars && halfStars < 0.75;
        if (rating < 0.25) return "N/A";
        return "\uD83D\uDFCA".repeat(fullStars) + (halfStar ? "⯨" : "");
    }
}
