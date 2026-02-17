package dev.unnm3d.zelsync.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExecutorServiceRouter {

    private final List<ExecutorService> services;
    private final int size;

    public ExecutorServiceRouter(int size) {
        services = new ArrayList<>(size);
        this.size = size;
        for (int i = 0; i < size; i++) {
            services.add(Executors.newSingleThreadExecutor());
        }
    }

    public void route(Runnable r, int id) {
        services.get(Math.abs(id % size)).execute(r);
    }

    public void shutdown() {
        for (ExecutorService service : services) {
            service.shutdown();
        }
    }
}