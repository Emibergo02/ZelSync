package dev.unnm3d.zelsync.api;

public interface ZelSyncAPI {


    /**
     * Get ZelTrade API instance
     *
     * @return the ZelTrade API instance
     */
    static ZelSyncAPI get() {
        return ZelSyncProvider.getApi();
    }
}
