package org.zhangjq0908.weather.http;

import android.content.Context;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

public final class VolleySingleton {

    private static volatile RequestQueue sQueue;

    private VolleySingleton() {
    }

    public static RequestQueue get(Context context) {
        if (sQueue == null) {
            synchronized (VolleySingleton.class) {
                if (sQueue == null) {
                    sQueue = Volley.newRequestQueue(context.getApplicationContext());
                }
            }
        }
        return sQueue;
    }
}
