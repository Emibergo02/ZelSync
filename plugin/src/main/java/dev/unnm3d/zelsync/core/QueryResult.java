package dev.unnm3d.zelsync.core;

public enum QueryResult {
    FIRST_LOGIN,
    LOCKED,
    OFFLINE,
    SUCCESS;

    public long toLong() {
        return switch (this) {
            case FIRST_LOGIN -> 0L;
            case LOCKED -> -1L;
            case OFFLINE -> -2L;
            case SUCCESS -> throw new IllegalStateException("Cannot convert SUCCESS to long, as it represents a positive integer result.");
        };
    }

    public static QueryResult fromResultLong(long resultInt) {
        if (resultInt == 0) {
            return FIRST_LOGIN;
        }
        if (resultInt == -1) {
            return LOCKED;
        }
        if (resultInt == -2) {
            return OFFLINE;
        }
        if (resultInt > 0) {
            return SUCCESS;
        }
        throw new IllegalArgumentException("Invalid result type: " + resultInt);
    }
}
