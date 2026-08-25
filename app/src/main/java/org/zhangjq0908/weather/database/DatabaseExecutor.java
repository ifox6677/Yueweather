package org.zhangjq0908.weather.database;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DatabaseExecutor {

    private static final ExecutorService EXECUTOR =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "db-executor"));
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private DatabaseExecutor() {
    }

    public static void execute(Runnable backgroundTask) {
        EXECUTOR.execute(backgroundTask);
    }

    public static void runOnMainThread(Runnable task) {
        MAIN_HANDLER.post(task);
    }
}
