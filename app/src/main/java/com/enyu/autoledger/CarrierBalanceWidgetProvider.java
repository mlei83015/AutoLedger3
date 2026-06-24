package com.enyu.autoledger;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.widget.RemoteViews;

public class CarrierBalanceWidgetProvider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int id : appWidgetIds) updateWidget(context, appWidgetManager, id);
    }

    public static void updateAll(Context context) {
        if (context == null) return;
        AppWidgetManager manager = AppWidgetManager.getInstance(context.getApplicationContext());
        ComponentName name = new ComponentName(context.getApplicationContext(), CarrierBalanceWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(name);
        if (ids == null) return;
        for (int id : ids) updateWidget(context.getApplicationContext(), manager, id);
    }

    private static void updateWidget(Context context, AppWidgetManager manager, int widgetId) {
        int balance = TransactionStore.totalBalance(context);
        int today = TransactionStore.expenseBetween(context, TransactionStore.startOfDay(0), TransactionStore.startOfDay(1));
        String carrier = BarcodeUtil.normalizeCarrier(AppSettings.getString(context, AppSettings.KEY_CARRIER_BARCODE, ""));
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_carrier_balance);
        views.setTextViewText(R.id.widget_balance, TransactionStore.money(balance));
        views.setTextViewText(R.id.widget_today, "今日 " + TransactionStore.money(today));
        Bitmap bm = BarcodeUtil.code39(carrier.isEmpty() ? "/ABC123" : carrier, 900, 230);
        views.setImageViewBitmap(R.id.widget_carrier_barcode, bm);

        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        views.setOnClickPendingIntent(R.id.widget_root, pending(context, 4000 + widgetId, openIntent));

        Intent copyIntent = new Intent(context, CopyCarrierReceiver.class);
        copyIntent.setAction(CopyCarrierReceiver.ACTION_COPY_CARRIER);
        views.setOnClickPendingIntent(R.id.widget_carrier_barcode, pendingBroadcast(context, 4100 + widgetId, copyIntent));

        Intent quickInputIntent = new Intent(context, QuickAddActivity.class);
        quickInputIntent.setAction(MainActivity.ACTION_QUICK_EXPENSE);
        quickInputIntent.putExtra("direction", "expense");
        views.setOnClickPendingIntent(R.id.widget_quick_input, pending(context, 4500 + widgetId, quickInputIntent));

        Intent expenseIntent = new Intent(context, QuickAddActivity.class);
        expenseIntent.setAction(MainActivity.ACTION_QUICK_EXPENSE);
        expenseIntent.putExtra("direction", "expense");
        views.setOnClickPendingIntent(R.id.widget_expense, pending(context, 5000 + widgetId, expenseIntent));

        Intent incomeIntent = new Intent(context, QuickAddActivity.class);
        incomeIntent.setAction(MainActivity.ACTION_QUICK_INCOME);
        incomeIntent.putExtra("direction", "income");
        views.setOnClickPendingIntent(R.id.widget_income, pending(context, 6000 + widgetId, incomeIntent));

        manager.updateAppWidget(widgetId, views);
    }

    private static PendingIntent pending(Context context, int requestCode, Intent intent) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(context, requestCode, intent, flags);
    }

    private static PendingIntent pendingBroadcast(Context context, int requestCode, Intent intent) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, requestCode, intent, flags);
    }
}
