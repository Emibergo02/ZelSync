package dev.unnm3d.zelsync.migrators;

import dev.unnm3d.zelsync.core.snapshots.DataSnapshot;
import net.william278.husksync.api.HuskSyncAPI;
import net.william278.husksync.data.Data;
import net.william278.husksync.data.DataHolder;
import net.william278.husksync.data.Identifier;
import net.william278.husksync.user.User;

import java.util.List;
import java.util.Map;

public class HuskSyncMigrator {
    private HuskSyncAPI huskSyncAPI;

    public HuskSyncMigrator(HuskSyncAPI huskSyncAPI) {
        this.huskSyncAPI = HuskSyncAPI.getInstance();
        List<User> allUsers = huskSyncAPI.getPlugin().getDatabase().getAllUsers();
        for (User user : allUsers) {
            huskSyncAPI.getLatestSnapshot(user).thenAccept(data -> {

                data.flatMap(DataHolder::getInventory).ifPresent(inventory -> {

                });
            });
        }
    }

    public DataSnapshot translator(DataHolder dataHolder) {
        DataSnapshot snapshot = new DataSnapshot(DataSnapshot.SaveCause.LOGOUT);
        for (Map.Entry<Identifier, Data> idData : dataHolder.getData().entrySet()) {
            if(idData.getValue() instanceof Data.Items inventoryData){
                for (Data.Items.Stack stack : inventoryData.getStack()) {

                }
            }
        }
        dataHolder.getInventory().ifPresent(inventory -> {
            //Translate the inventory data to a format that ZelSync can understand and add it to the snapshot
        });
        return snapshot;
    }
}
