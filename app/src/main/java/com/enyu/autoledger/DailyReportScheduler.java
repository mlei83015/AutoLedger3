package com.enyu.autoledger;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;

public class DailyReportScheduler {
    public static void schedule(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent intent = new Intent(context, DailyReportReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                9001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag()
        );

        Calendar c = Calendar.getInstance();
        String hhmm = AppSettings.getString(context, AppSettings.KEY_DAILY_NOTIFY_TIME, "09:00");
        int hour = 9;
        int minute = 0;
        try {
            String[] parts = hhmm.split(":");
            hour = Math.max(0, Math.min(23, Integer.parseInt(parts[0])));
            minute = Math.max(0, Math.min(59, Integer.parseInt(parts[1])));
        } catch (Exception ignored) { }
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() <= System.currentTimeMillis()) {
            c.add(Calendar.DAY_OF_MONTH, 1);
        }
        am.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                c.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY,
                pi
        );
    }

    static int immutableFlag() {
        return Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
    }
}
