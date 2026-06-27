package com.enyu.autoledger;

import android.app.Notification;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class FinanceNotificationListener extends NotificationListenerService {
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) return;
        String packageName = sbn.getPackageName();

        // Version 3：不要讀取本 App 自己跳出的「已自動記帳」通知，避免重複記帳。
        if (AppSettings.getBool(getApplicationContext(), AppSettings.KEY_EXCLUDE_OWN, true)
                && getPackageName().equals(packageName)) {
            return;
        }

        Notification n = sbn.getNotification();
        Bundle extras = n.extras;
        if (extras == null) return;

        String appName = getAppName(packageName);
        String title = stringValue(extras.getCharSequence(Notification.EXTRA_TITLE));
        String text = stringValue(extras.getCharSequence(Notification.EXTRA_TEXT));
        String bigText = stringValue(extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        String linesText = linesValue(extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES));
        String combinedText = text;
        if (bigText.length() > combinedText.length()) combinedText = bigText;
        if (linesText.length() > combinedText.length()) combinedText = linesText;

        if (!AppSettings.shouldDetectSource(getApplicationContext(), packageName, appName, title, combinedText)) {
            return;
        }

        Transaction tx = FinanceParser.parse(sbn.getPostTime(), packageName, appName, title, combinedText);
        if (tx != null) {
            boolean added = TransactionStore.add(getApplicationContext(), tx);
            if (added) {
                DailyReportReceiver.showInstantSavedNotification(getApplicationContext(), tx);
            }
        } else if (FinanceParser.isAmountlessLinePayDebitCardNotice(packageName, appName, title, combinedText)) {
            DailyReportReceiver.showPendingLinePayCardNotification(getApplicationContext());
        }
    }

    private String getAppName(String packageName) {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            CharSequence label = pm.getApplicationLabel(ai);
            return label == null ? packageName : label.toString();
        } catch (Exception e) {
            return packageName;
        }
    }

    private String stringValue(CharSequence cs) {
        return cs == null ? "" : cs.toString();
    }

    private String linesValue(CharSequence[] lines) {
        if (lines == null) return "";
        StringBuilder b = new StringBuilder();
        for (CharSequence line : lines) {
            if (line != null) b.append(line).append(' ');
        }
        return b.toString().trim();
    }
}
