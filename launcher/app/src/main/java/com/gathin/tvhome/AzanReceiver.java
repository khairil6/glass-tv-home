package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AzanReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || context == null) return;
        Context app = context.getApplicationContext();
        if (app == null) app = context;
        String action = intent.getAction();
        PendingResult pending = goAsync();
        try {
            if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                    || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                    || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)) {
                AzanPlayer.scheduleAlarms(app);
                AzanPlayer.checkAndPlay(app);
                return;
            }
            String prayer = intent.getStringExtra(AzanPlayer.EXTRA_PRAYER);
            AzanPlayer.play(app, prayer);
            AzanPlayer.scheduleAlarms(app);
        } finally {
            pending.finish();
        }
    }
}
