package dev.unnm3d.zelsync.api.data;

public enum DataKeys {

    SYNC_INV("zsync:inv"),
    SYNC_INV_UPDATE("zsync:inv_up"),
    PLAYER_LOCK("zsync:p_lock"),
    FULL_TRADE("rtrade:f_trade_up"),
    TRADES("rtrade:trades"),
    TRADE_QUERY("rtrade:query"),
    NAME_UUIDS("rtrade:name_uuids"),
    TRADE_ARCHIVE("rtrade:trades"),
    TRADE_ARCHIVE_PLAYER_PREFIX("rtrade:p_trades_"),
    PLAYER_TRADES("rtrade:p_trades"),
    IGNORE_PLAYER_PREFIX("rtrade:ignore_"),
    IGNORE_PLAYER_UPDATE("rtrade:ignore_up"),
    INVITE_UPDATE("rtrade:invute_up"),
    FIELD_UPDATE_TRADE("rtrade:f_update"),
    PLAYERLIST("rtrade:playerlist"),
    OPEN_WINDOW("rtrade:open_window"),
    ;


    private final String key;

    DataKeys(String key) {
        this.key = key;
    }

    public byte[] getKeyBytes() {
        return key.getBytes();
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
