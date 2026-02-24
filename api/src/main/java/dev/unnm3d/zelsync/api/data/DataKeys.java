package dev.unnm3d.zelsync.api.data;

public enum DataKeys {

    /**
     * zsync:inv (HASHSET) UUID-SNAPSHOTID registers latest snapshot ID for a uuid
     * zsync:p_lock<snapshot_id> registers the serialized snapshot
     *
     * zsync:inv<playerUUID> (HASHSET) SNAPSHOTID-SERIALIZED
     * zsync:p_lock<playerUUID> LATEST SNAPSHOTID 0 missing, SNAPSHOTID, if negative is locked.
     *
     * How to set a shapshot as missing (0) or locked (-1)
     */
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
