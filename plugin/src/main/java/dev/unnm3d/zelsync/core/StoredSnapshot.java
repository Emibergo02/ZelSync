package dev.unnm3d.zelsync.core;

import lombok.Getter;
import lombok.Setter;

@Getter
public class StoredSnapshot {
    private final long id;
    @Setter
    private QueryResult result;
    private final DataSnapshot dataSnapshot;

    public StoredSnapshot(long id, DataSnapshot dataSnapshot) {
        this.id = id;
        this.dataSnapshot = dataSnapshot;
        this.result = QueryResult.fromResultLong(id);
    }
}
