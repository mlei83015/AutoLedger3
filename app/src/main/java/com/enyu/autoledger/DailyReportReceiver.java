package com.enyu.autoledger;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class DailyReportReceiver extends BroadcastReceiver {
    public static final String CHANNEL_REPORT = "daily_report";
    public static final String CHANNEL_SAVED = "saved_transaction";
    public static final String CHANNEL_PENDING = "pending_amount";

    @Override
    public void onReceive(Context context, Intent intent) {
        createChannels(context);
        if (!AppSettings.getBool(context, AppSettings.KEY_NOTIFY_DAILY, true)) {
            DailyReportScheduler.schedule(context);
            return;
        }
        long yStart = TransactionStore.startOfDay(-1);
        long tStart = TransactionStore.startOfDay(0);
        int expense = TransactionStore.expenseBetween(context, yStart, tStart);
        int income = TransactionStore.incomeBetween(context, yStart, tStart);
        String title = "昨日記帳報告";
        String body = "昨天支出 $" + expense + "，收入 $" + income + "。";
        if (expense == 0 && income == 0) {
            body = "昨天沒有自動記到支出或收入。";
        }
        showNotification(context, 1001, CHANNEL_REPORT, title, body, true);
        DailyReportScheduler.schedule(context);
    }

    public static void showInstantSavedNotification(Context context, Transaction tx) {
        if (!AppSettings.getBool(context, AppSettings.KEY_NOTIFY_AUTO_SAVED, true)) return;
        createChannels(context);
        String dir = "income".equals(tx.direction) ? "收入" : "支出";
        String title = "已自動記帳：" + dir + " $" + tx.amount;
        String body = tx.category + "｜" + tx.merchant;
        if (tx.originalAmount > 0 && tx.originalAmount != tx.amount) {
            body = "實付 $" + tx.amount + "｜原價 $" + tx.originalAmount + "｜折抵 $" + tx.discountAmount;
        }
        showNotification(context, (int) (System.currentTimeMillis() % 100000), CHANNEL_SAVED, title, body, false);
    }

    public static void showPendingLinePayCardNotification(Context context) {
        createChannels(context);
        Intent open = new Intent(context, MainActivity.class);
        open.setAction(MainActivity.ACTION_PENDING_LINEPAY_CARD);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        showNotification(
                context,
                2039,
                CHANNEL_PENDING,
                "LINE Pay 簽帳卡待補金額",
                "中國信託通知沒有金額。點一下補上蝦皮 / 網購金額，避免漏記。",
                true,
                open
        );
    }

    public static void createChannels(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel report = new NotificationChannel(CHANNEL_REPORT, "每日記帳報告", NotificationManager.IMPORTANCE_DEFAULT);
        NotificationChannel saved = new NotificationChannel(CHANNEL_SAVED, "自動記帳完成", NotificationManager.IMPORTANCE_LOW);
        NotificationChannel pending = new NotificationChannel(CHANNEL_PENDING, "待補金額提醒", NotificationManager.IMPORTANCE_DEFAULT);
        nm.createNotificationChannel(report);
        nm.createNotificationChannel(saved);
        nm.createNotificationChannel(pending);
    }

    private static void showNotification(Context context, int id, String channel, String title, String body, boolean autoCancel) {
        Intent open = new Intent(context, MainActivity.class);
        showNotification(context, id, channel, title, body, autoCancel, open);
    }

    private static void showNotification(Context context, int id, String channel, String title, String body, boolean autoCancel, Intent open) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        PendingIntent pi = PendingIntent.getActivity(
                context,
                id,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | DailyReportScheduler.immutableFlag()
        );
        android.app.Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new android.app.Notification.Builder(context, channel)
                : new android.app.Notification.Builder(context);
        b.setSmallIcon(android.R.drawable.ic_menu_agenda)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new android.app.Notification.BigTextStyle().bigText(body))
                .setContentIntent(pi)
                .setAutoCancel(autoCancel);
        nm.notify(id, b.build());
    }
}
