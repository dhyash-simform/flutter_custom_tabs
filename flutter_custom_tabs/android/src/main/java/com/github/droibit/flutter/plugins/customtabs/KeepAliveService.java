package com.github.droibit.flutter.plugins.customtabs;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

public class KeepAliveService extends Service {
    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        KeepAliveService getService() {
            return KeepAliveService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
}
