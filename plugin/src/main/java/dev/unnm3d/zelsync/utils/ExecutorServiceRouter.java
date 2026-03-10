package dev.unnm3d.zelsync.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

public class ExecutorServiceRouter {

    private final List<ScheduledExecutorService> services;
    private final int size;

    public ExecutorServiceRouter(int size) {
        services = new ArrayList<>(size);
        this.size = size;
        for (int i = 0; i < size; i++) {
            services.add(Executors.newSingleThreadScheduledExecutor());
        }
    }

    public void route(Runnable r, int id) {
        services.get(Math.abs(id % size)).execute(r);
    }

    public ScheduledFuture<?> routeScheduleAtFixedRate(int id, Runnable r, long initialDelay, long period, java.util.concurrent.TimeUnit unit) {
        return services.get(Math.abs(id % size)).scheduleAtFixedRate(r, initialDelay, period, unit);
    }

    public void shutdown() {
        for (ExecutorService service : services) {
            service.shutdown();
        }
    }
}