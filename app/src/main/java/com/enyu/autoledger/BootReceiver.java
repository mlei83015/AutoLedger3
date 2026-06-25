package com.enyu.autoledger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        DailyReportScheduler.schedule(context);
        try { BalanceWidgetProvider.updateAll(context); } catch (Exception ignored) { }
        try { CarrierBalanceWidgetProvider.updateAll(context); } catch (Exception ignored) { }
        try { CarrierPhotoWidgetProvider.updateAll(context); } catch (Exception ignored) { }
    }
}
