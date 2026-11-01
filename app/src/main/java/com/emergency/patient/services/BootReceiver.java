package com.emergency.patient.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * BootReceiver — Restarts services after device reboot.
 * Persistent Emergency SOS notification has been disabled.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Medical ID & Emergency SOS persistent notification disabled
    }
}
