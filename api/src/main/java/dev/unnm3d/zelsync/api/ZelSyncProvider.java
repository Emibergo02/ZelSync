package dev.unnm3d.zelsync.api;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class ZelSyncProvider {
    private static ZelSyncAPI api;

    private ZelSyncProvider() {
        throw new UnsupportedOperationException("This class cannot be instantiated");
    }

    static @NotNull ZelSyncAPI getApi(){
        return Objects.requireNonNull(api, "API is not set! Please specify in plugin.yml ZelChat as dependency.");
    }

    public static void set(@NotNull ZelSyncAPI api) {
        ZelSyncProvider.api = api;
    }
}
