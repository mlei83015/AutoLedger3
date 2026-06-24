package com.enyu.autoledger;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.RemoteViews;

public class BalanceWidgetProvider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int id : appWidgetIds) updateWidget(context, appWidgetManager, id);
    }

    public static void updateAll(Context context) {
        if (context == null) return;
        AppWidgetManager manager = AppWidgetManager.getInstance(context.getApplicationContext());
        ComponentName name = new ComponentName(context.getApplicationContext(), BalanceWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(name);
        if (ids != null) for (int id : ids) updateWidget(context.getApplicationContext(), manager, id);
        try { CarrierBalanceWidgetProvider.updateAll(context); } catch (Exception ignored) { }
        try { CarrierPhotoWidgetProvider.updateAll(context); } catch (Exception ignored) { }
    }

    private static void updateWidget(Context context, AppWidgetManager manager, int widgetId) {
        int balance = TransactionStore.totalBalance(context);
        int spent = TransactionStore.monthExpense(context);
        int budget = Math.max(1, AppSettings.getMonthlyBudget(context));
        int today = TransactionStore.expenseBetween(context, TransactionStore.startOfDay(0), TransactionStore.startOfDay(1));

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_balance);
        views.setTextViewText(R.id.widget_balance, TransactionStore.money(balance));
        views.setTextViewText(R.id.widget_subtitle, "今日花費 " + TransactionStore.money(today) + "｜本月 " + spent + "/" + budget);

        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        views.setOnClickPendingIntent(R.id.widget_root, pending(context, 1000 + widgetId, openIntent));

        Intent quickInputIntent = new Intent(context, QuickAddActivity.class);
        quickInputIntent.setAction(MainActivity.ACTION_QUICK_EXPENSE);
        quickInputIntent.putExtra("direction", "expense");
        views.setOnClickPendingIntent(R.id.widget_quick_input, pending(context, 1500 + widgetId, quickInputIntent));

        Intent expenseIntent = new Intent(context, QuickAddActivity.class);
        expenseIntent.setAction(MainActivity.ACTION_QUICK_EXPENSE);
        expenseIntent.putExtra("direction", "expense");
        views.setOnClickPendingIntent(R.id.widget_expense, pending(context, 2000 + widgetId, expenseIntent));

        Intent incomeIntent = new Intent(context, QuickAddActivity.class);
        incomeIntent.setAction(MainActivity.ACTION_QUICK_INCOME);
        incomeIntent.putExtra("direction", "income");
        views.setOnClickPendingIntent(R.id.widget_income, pending(context, 3000 + widgetId, incomeIntent));

        manager.updateAppWidget(widgetId, views);
    }

    private static PendingIntent pending(Context context, int requestCode, Intent intent) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(context, requestCode, intent, flags);
    }
}
