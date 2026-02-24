package dev.unnm3d.zelsync.core;

import dev.unnm3d.zelsync.ZelSync;
import dev.unnm3d.zelsync.utils.Utils;
import lombok.NoArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.nio.ByteBuffer;


@NoArgsConstructor
public abstract class DataContent<T> {
    protected T content;

    public abstract T extract(Player player);

    public abstract void apply(Player player);

    public abstract byte[] serialize();

    public abstract void deserialize(byte[] bytes);

    @NoArgsConstructor
    public static class InventoryContent extends DataContent<ItemStack[]> {

        public InventoryContent(Player player) {
            content = extract(player);
        }

        public InventoryContent(ItemStack[] inventoryData) {
            content = inventoryData;
        }

        public InventoryContent(byte[] bytes) {
            deserialize(bytes);
        }

        @Override
        public ItemStack[] extract(Player player) {
            return player.getInventory().getContents();
        }

        @Override
        public void apply(Player player) {
            player.getInventory().setContents(content);
        }

        @Override
        public byte[] serialize() {
            return Utils.serialize(content);
        }

        @Override
        public void deserialize(byte[] bytes) {
            this.content = Utils.deserialize(bytes);
        }
    }

    public static class PersistentDataContainerContent extends DataContent<byte[]> {

        public PersistentDataContainerContent(Player player) {
            content = extract(player);
        }

        public PersistentDataContainerContent(byte[] data) {
            content = data;
        }

        @Override
        public byte[] extract(Player player) {
            try {
                return player.getPersistentDataContainer().serializeToBytes();
            } catch (IOException e) {
                ZelSync.getInstance().getLogger().log(java.util.logging.Level.SEVERE, "Failed to serialize PersistentDataContainer for player: " + player.getName(), e);
            }
            return new byte[0];
        }

        @Override
        public void apply(Player player) {
            try {
                player.getPersistentDataContainer().readFromBytes(content, true);
            } catch (IOException e) {
                ZelSync.getInstance().getLogger().log(java.util.logging.Level.SEVERE, "Failed to deserialize PersistentDataContainer for player: " + player.getName(), e);
            }
        }

        @Override
        public byte[] serialize() {
            return content; // Assuming content is already serialized
        }

        @Override
        public void deserialize(byte[] bytes) {
            this.content = bytes; // Assuming bytes are already deserialized into the correct format
        }
    }

    @NoArgsConstructor
    public static class HealthContent extends DataContent<double[]> {

        public HealthContent(Player player) {
            content = extract(player);
        }

        public HealthContent(double[] healthData) {
            content = healthData;
        }

        public HealthContent(byte[] bytes) {
            deserialize(bytes);
        }

        @Override
        public double[] extract(Player player) {
            double[] health = new double[2];
            health[0] = player.getHealth();
            health[1] = player.isHealthScaled() ? player.getHealthScale() : 0;
            return health;
        }

        @Override
        public void apply(Player player) {
            player.setHealth(content[0]);
            if (content[1] > 0) {
                player.setHealthScale(content[1]);
                player.setHealthScaled(true);
            } else {
                player.setHealthScaled(false);
            }
        }

        @Override
        public byte[] serialize() {
            ByteBuffer buffer = ByteBuffer.allocate(16);
            buffer.putDouble(content[0]);
            buffer.putDouble(content[1]);
            return buffer.array();
        }

        @Override
        public void deserialize(byte[] bytes) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            double[] health = new double[2];
            health[0] = buffer.getDouble();
            health[1] = buffer.getDouble();
            this.content = health;
        }
    }


    @NoArgsConstructor
    public static class FoodContent extends DataContent<Integer> {

        public FoodContent(Player player) {
            content = extract(player);
        }

        public FoodContent(int foodData) {
            content = foodData;
        }

        public FoodContent(byte[] bytes) {
            deserialize(bytes);
        }

        @Override
        public Integer extract(Player player) {
            return player.getFoodLevel();
        }

        @Override
        public void apply(Player player) {
            player.setFoodLevel(content);
        }

        @Override
        public byte[] serialize() {
            ByteBuffer buffer = ByteBuffer.allocate(4);
            buffer.putInt(content);
            return buffer.array();
        }

        @Override
        public void deserialize(byte[] bytes) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            this.content = buffer.getInt();
        }
    }

    @NoArgsConstructor
    public static class ExperienceContent extends DataContent<int[]> {
        private float expProgress;

        public ExperienceContent(Player player) {
            content = extract(player);
        }

        public ExperienceContent(byte[] bytes) {
            deserialize(bytes);
        }

        @Override
        public int[] extract(Player player) {
            int[] experience = new int[3];
            experience[0] = player.getTotalExperience();
            experience[1] = player.getLevel();
            experience[2] = player.getExpToLevel();
            expProgress = player.getExp();
            return experience;
        }

        @Override
        public void apply(Player player) {
            player.setTotalExperience(content[0]);
            player.setLevel(content[1]);
            player.setExp(expProgress);
        }

        @Override
        public byte[] serialize() {
            ByteBuffer buffer = ByteBuffer.allocate(12);
            buffer.putInt(content[0]);
            buffer.putInt(content[1]);
            buffer.putFloat(expProgress);
            return buffer.array();
        }

        @Override
        public void deserialize(byte[] bytes) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            this.content = new int[3];
            this.content[0] = buffer.getInt();
            this.content[1] = buffer.getInt();
            this.expProgress = buffer.getFloat();
        }
    }

    @NoArgsConstructor
    public static class EnderChestContent extends DataContent<ItemStack[]> {

        public EnderChestContent(Player player) {
            content = extract(player);
        }

        public EnderChestContent(ItemStack[] enderChestData) {
            content = enderChestData;
        }

        public EnderChestContent(byte[] bytes) {
            deserialize(bytes);
        }

        @Override
        public ItemStack[] extract(Player player) {
            return player.getEnderChest().getContents();
        }

        @Override
        public void apply(Player player) {
            player.getEnderChest().setContents(content);
        }

        @Override
        public byte[] serialize() {
            return Utils.serialize(content);
        }

        @Override
        public void deserialize(byte[] bytes) {
            this.content = Utils.deserialize(bytes);
        }
    }

}
