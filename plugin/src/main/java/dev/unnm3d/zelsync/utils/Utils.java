package dev.unnm3d.zelsync.utils;

import com.davidehrmann.vcdiff.VCDiffDecoderBuilder;
import com.davidehrmann.vcdiff.VCDiffEncoderBuilder;
import com.davidehrmann.vcdiff.VCDiffStreamingEncoder;
import dev.unnm3d.zelsync.ZelSync;
import lombok.experimental.UtilityClass;
import org.bukkit.inventory.ItemStack;
import org.xerial.snappy.Snappy;

import java.io.*;
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
            return Snappy.compress(input);
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

    public static byte[] computeDiff(byte[] original, byte[] changed) throws IOException {
        // Zstd wrapping the output — delta bytes get compressed as they're written
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            VCDiffStreamingEncoder<OutputStream> encoder = VCDiffEncoderBuilder.builder()
              .withDictionary(original)
              .buildStreaming();
            encoder.startEncoding(baos);

            // Feed the target in chunks
            int offset = 0;
            while (offset < changed.length) {
                int len = Math.min(8192, changed.length - offset);
                encoder.encodeChunk(changed, offset, len, baos);
                offset += len;
            }

            encoder.finishEncoding(baos);
            return baos.toByteArray();
        }

    }

    public static byte[] applyDiff(byte[] original, byte[] diff) throws IOException {
        ByteArrayOutputStream restoredOutput = new ByteArrayOutputStream();

        try (InputStream deltaStream = VCDiffDecoderBuilder.builder()
          .buildInputStream(
            new ByteArrayInputStream(diff),
            original
          )) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = deltaStream.read(buffer)) != -1) {
                restoredOutput.write(buffer, 0, bytesRead);
            }
        }
        return restoredOutput.toByteArray();
    }
}
