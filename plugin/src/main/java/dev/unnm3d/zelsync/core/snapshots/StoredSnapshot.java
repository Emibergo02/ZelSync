package dev.unnm3d.zelsync.core.snapshots;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
public class StoredSnapshot {
    public static final long LOCKED_ID = -1L;
    public static final long FIRST_LOGIN_ID = 0L;

    @Setter
    private long id;
    private final DataSnapshot snapshot;

    public boolean isLocked() {
        return id == LOCKED_ID;
    }

    public boolean isFirstLogin() {
        return id == FIRST_LOGIN_ID;
    }

    public boolean isValid() {
        return id > FIRST_LOGIN_ID;
    }
}
