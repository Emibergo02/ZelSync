package dev.unnm3d.zelsync.api.data;

public enum DataKeys {
    SYNC_INV("zsync:inv"),
    SYNC_INV_UPDATE("zsync:inv_up"),
    PLAYER_LOCK("zsync:p_lock:"),
    COUNTER_GENERATOR("zsync:counter"),
    PLAYERLIST("zsync:playerlist")
    ;


    private final String key;

    DataKeys(String key) {
        this.key = key;
    }

    public byte[] getKeyBytes() {
        return key.getBytes();
    }

    public byte[] append(byte[] suffix) {
        byte[] keyBytes = getKeyBytes();
        byte[] combined = new byte[keyBytes.length + suffix.length];
        System.arraycopy(keyBytes, 0, combined, 0, keyBytes.length);
        System.arraycopy(suffix, 0, combined, keyBytes.length, suffix.length);
        return combined;
    }

    public static DataKeys fromBytes(byte[] bytes) {
        String key = new String(bytes);
        for (DataKeys dataKey : values()) {
            if (dataKey.key.equals(key)) {
                return dataKey;
            }
        }
        throw new IllegalArgumentException("Unknown data key: " + key);
    }

    @Override
    public String toString() {
        return key;
    }
}
