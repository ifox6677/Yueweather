package org.zhangjq0908.weather.widget;

import android.content.Context;
import android.graphics.Bitmap;

import java.lang.ref.SoftReference;

/**
 * Single-entry in-process cache for the latest radar tile. The entry is
 * published atomically (bitmap + timestamp + zoom always change together) and
 * held via a SoftReference so the memory pressure collector may reclaim it at
 * any time - widgets then simply keep showing their last rendered RemoteViews
 * until the next worker run refreshes the tile. No disk I/O involved.
 */
public final class RadarStore {
    private static volatile SoftReference<LoadedRadar> sEntry;

    private RadarStore() {
    }

    public static void save(Context context, Bitmap bitmap, long timeGMT, int zoom) {
        sEntry = new SoftReference<>(new LoadedRadar(bitmap, timeGMT, zoom));
    }

    /**
     * Returns the cached tile, or null if nothing was cached yet or the entry
     * was reclaimed under memory pressure.
     */
    public static LoadedRadar load(Context context) {
        SoftReference<LoadedRadar> ref = sEntry;
        return ref != null ? ref.get() : null;
    }

    public static void clear(Context context) {
        sEntry = null;
    }

    public static final class LoadedRadar {
        public final Bitmap bitmap;
        public final long timeGMT;
        public final int zoom;

        LoadedRadar(Bitmap bitmap, long timeGMT, int zoom) {
            this.bitmap = bitmap;
            this.timeGMT = timeGMT;
            this.zoom = zoom;
        }
    }
}
