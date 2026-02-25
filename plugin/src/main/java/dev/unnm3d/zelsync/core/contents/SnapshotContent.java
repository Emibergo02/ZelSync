package dev.unnm3d.zelsync.core.contents;

import dev.unnm3d.zelsync.ZelSync;
import dev.unnm3d.zelsync.utils.Utils;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

public abstract class SnapshotContent { // implementors handle their own exceptions
    public abstract void apply(Player player);

    public abstract byte[] serialize();

    public static class EmptyContent extends SnapshotContent {
        @Override
        public void apply(Player player) {
        }

        @Override
        public byte[] serialize() {
            return new byte[0];
        }

        public static class Factory implements ContentFactory<EmptyContent> {
            @Override
            public EmptyContent fromPlayer(Player player) {
                return new EmptyContent();
            }

            @Override
            public EmptyContent fromBytes(byte[] bytes) {
                return new EmptyContent();
            }
        }
    }

    @Getter
    @ToString
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class InventoryContent extends SnapshotContent {
        private ItemStack[] content;

        @Override
        public void apply(Player player) {
            player.getInventory().setContents(content);
        }

        @Override
        public byte[] serialize() {
            return Utils.serialize(content);
        }

        public static class Factory implements ContentFactory<InventoryContent> {
            @Override
            public InventoryContent fromPlayer(Player player) {
                return new InventoryContent(player.getInventory().getContents());
            }

            @Override
            public InventoryContent fromBytes(byte[] bytes) {
                return new InventoryContent(Utils.deserialize(bytes));
            }
        }
    }

    @Getter
    @ToString
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class PersistentSnapshotContainerContent extends SnapshotContent {
        private byte[] content;

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

        public static class Factory implements ContentFactory<PersistentSnapshotContainerContent> {
            @Override
            public PersistentSnapshotContainerContent fromPlayer(Player player) {
                try {
                    return new PersistentSnapshotContainerContent(player.getPersistentDataContainer().serializeToBytes());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public PersistentSnapshotContainerContent fromBytes(byte[] bytes) {
                return new PersistentSnapshotContainerContent(bytes);
            }
        }
    }

    @Getter
    @ToString
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class HealthContent extends SnapshotContent {
        private double health;
        private double healthScale;
        private double absorption;

        @Override
        public void apply(Player player) {
            player.setHealth(health);
            player.setHealthScale(healthScale);
            player.setAbsorptionAmount(absorption);
        }

        @Override
        public byte[] serialize() {
            ByteBuffer buffer = ByteBuffer.allocate(8 + 8 + 8);
            buffer.putDouble(health);
            buffer.putDouble(healthScale);
            buffer.putDouble(absorption);
            return buffer.array();
        }

        public static class Factory implements ContentFactory<HealthContent> {
            @Override
            public HealthContent fromPlayer(Player player) {
                return new HealthContent(player.getHealth(), player.getHealthScale(), player.getAbsorptionAmount());
            }

            @Override
            public HealthContent fromBytes(byte[] bytes) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                double health = buffer.getDouble();
                double healthScale = buffer.getDouble();
                double absorption = buffer.getDouble();
                return new HealthContent(health, healthScale, absorption);
            }
        }
    }

    @Getter
    @ToString
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class FoodContent extends SnapshotContent {
        private int foodLevel;
        private float saturation;
        private float exhaustion;


        @Override
        public void apply(Player player) {
            player.setFoodLevel(foodLevel);
            player.setSaturation(saturation);
            player.setExhaustion(exhaustion);
        }

        @Override
        public byte[] serialize() {
            ByteBuffer buffer = ByteBuffer.allocate(4 + 4 + 4);
            buffer.putInt(foodLevel);
            buffer.putFloat(saturation);
            buffer.putFloat(exhaustion);
            return buffer.array();
        }

        public static class Factory implements ContentFactory<FoodContent> {
            @Override
            public FoodContent fromPlayer(Player player) {
                return new FoodContent(player.getFoodLevel(), player.getSaturation(), player.getExhaustion());
            }

            @Override
            public FoodContent fromBytes(byte[] bytes) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                int foodLevel = buffer.getInt();
                float saturation = buffer.getFloat();
                float exhaustion = buffer.getFloat();
                return new FoodContent(foodLevel, saturation, exhaustion);
            }
        }
    }

    @Getter
    @ToString
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ExperienceContent extends SnapshotContent {
        private int totalExp;
        private int level;
        private float progress;

        @Override
        public void apply(Player player) {
            player.setTotalExperience(totalExp);
            player.setLevel(level);
            player.setExp(progress);
        }

        @Override
        public byte[] serialize() {
            ByteBuffer buffer = ByteBuffer.allocate(4 + 4 + 4);
            buffer.putInt(totalExp);
            buffer.putInt(level);
            buffer.putFloat(progress);
            return buffer.array();
        }

        public static class Factory implements ContentFactory<ExperienceContent> {
            @Override
            public ExperienceContent fromPlayer(Player player) {
                return new ExperienceContent(player.getTotalExperience(), player.getLevel(), player.getExp());
            }

            @Override
            public ExperienceContent fromBytes(byte[] bytes) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                int totalExp = buffer.getInt();
                int level = buffer.getInt();
                float progress = buffer.getFloat();
                return new ExperienceContent(totalExp, level, progress);
            }
        }
    }

    @Getter
    @ToString
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class EnderChestContent extends SnapshotContent {
        private ItemStack[] content;

        @Override
        public void apply(Player player) {
            player.getEnderChest().setContents(content);
        }

        @Override
        public byte[] serialize() {
            return Utils.serialize(content);
        }

        public static class Factory implements ContentFactory<EnderChestContent> {
            @Override
            public EnderChestContent fromPlayer(Player player) {
                return new EnderChestContent(player.getEnderChest().getContents());
            }

            @Override
            public EnderChestContent fromBytes(byte[] bytes) {
                return new EnderChestContent(Utils.deserialize(bytes));
            }
        }
    }

    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class EffectContent extends SnapshotContent {
        private List<PotionEffect> effects;

        @Override
        public void apply(Player player) {
            for (PotionEffect effect : effects) {
                player.addPotionEffect(effect);
            }
        }

        @Override
        public byte[] serialize() {
            ByteBuffer buffer = ByteBuffer.allocate(4 + effects.size() * (32 + 4 + 4)); // Rough estimate, adjust as needed
            buffer.putInt(effects.size());
            for (PotionEffect effect : effects) {
                byte[] typeBytes = new byte[32];
                System.arraycopy(
                  effect.getType().getKey().toString().getBytes(), 0,
                  typeBytes, 0,
                  Math.min(effect.getType().getKey().toString().getBytes().length, 32));
                buffer.put(typeBytes);
                buffer.putInt(effect.getAmplifier());
                buffer.putInt(effect.getDuration());
                buffer.put(effect.hasParticles() ? (byte) 1 : (byte) 0);
                buffer.put(effect.isAmbient() ? (byte) 1 : (byte) 0);
                buffer.put(effect.hasIcon() ? (byte) 1 : (byte) 0);
            }
            return buffer.array();
        }

        public static class Factory implements ContentFactory<EffectContent> {
            @Override
            public EffectContent fromPlayer(Player player) {
                return new EffectContent(player.getActivePotionEffects().stream().toList());
            }

            @Override
            public EffectContent fromBytes(byte[] bytes) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                int size = buffer.getInt();
                List<PotionEffect> effects = new java.util.ArrayList<>();
                for (int i = 0; i < size; i++) {
                    byte[] typeBytes = new byte[32];
                    buffer.get(typeBytes);
                    NamespacedKey typeName = NamespacedKey.fromString(new String(typeBytes).trim());
                    int amplifier = buffer.getInt();
                    int duration = buffer.getInt();
                    boolean hasParticles = buffer.get() == (byte) 1;
                    boolean isAmbient = buffer.get() == (byte) 1;
                    boolean hasIcon = buffer.get() == (byte) 1;
                    if (typeName == null) continue;
                    PotionEffectType type = Registry.POTION_EFFECT_TYPE.get(typeName);
                    if (type != null) {
                        effects.add(new PotionEffect(type, duration, amplifier, isAmbient, hasParticles, hasIcon));
                    }
                }
                return new EffectContent(effects);
            }
        }
    }
}
