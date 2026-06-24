package com.enyu.autoledger;

import android.content.Context;
import android.content.SharedPreferences;

public class AppSettings {
    private static final String PREF = "auto_ledger_settings_v3";

    public static final String KEY_LINE_PAY = "detect_line_pay";
    public static final String KEY_INVOICE = "detect_invoice";
    public static final String KEY_BANK = "detect_bank";
    public static final String KEY_SMS = "detect_sms";
    public static final String KEY_OTHER = "detect_other";
    public static final String KEY_EXCLUDE_OWN = "exclude_own";
    public static final String KEY_DEDUPE = "dedupe";
    public static final String KEY_GOOGLE_BOUND = "google_bound";
    public static final String KEY_DARK_MODE = "dark_mode";
    public static final String KEY_CROSS_SOURCE_DEDUPE = "cross_source_dedupe";
    public static final String KEY_THEME = "theme";
    public static final String KEY_PALETTE = "palette";
    public static final String KEY_MONTHLY_BUDGET = "monthly_budget";

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static boolean getBool(Context c, String key, boolean def) {
        return sp(c).getBoolean(key, def);
    }

    public static void setBool(Context c, String key, boolean value) {
        sp(c).edit().putBoolean(key, value).apply();
    }

    public static String getTheme(Context c) {
        return sp(c).getString(KEY_THEME, "清新橙");
    }

    public static void setTheme(Context c, String theme) {
        sp(c).edit().putString(KEY_THEME, theme).apply();
    }

    public static int getPalette(Context c) {
        return sp(c).getInt(KEY_PALETTE, 0);
    }

    public static void setPalette(Context c, int palette) {
        sp(c).edit().putInt(KEY_PALETTE, palette).apply();
    }

    public static int getMonthlyBudget(Context c) {
        return sp(c).getInt(KEY_MONTHLY_BUDGET, 30000);
    }

    public static void setMonthlyBudget(Context c, int amount) {
        sp(c).edit().putInt(KEY_MONTHLY_BUDGET, Math.max(0, amount)).apply();
    }

    public static boolean shouldDetectSource(Context c, String packageName, String appName, String title, String text) {
        String all = ((packageName == null ? "" : packageName) + " " +
                (appName == null ? "" : appName) + " " +
                (title == null ? "" : title) + " " +
                (text == null ? "" : text)).toLowerCase();

        boolean isLinePay = all.contains("line pay") || all.contains("linepay") || all.contains("line錢包") || all.contains("line pay付款") || all.contains("line pay 付款");
        boolean isInvoice = all.contains("載具") || all.contains("發票") || all.contains("invoice") || all.contains("einvoice") || all.contains("財政部");
        boolean isBank = all.contains("銀行") || all.contains("帳戶") || all.contains("信用卡") || all.contains("金融卡") || all.contains("atm") || all.contains("bank");
        boolean isSms = all.contains("sms") || all.contains("mms") || all.contains("簡訊") || all.contains("訊息");

        if (isLinePay) return getBool(c, KEY_LINE_PAY, true);
        if (isInvoice) return getBool(c, KEY_INVOICE, true);
        if (isBank) return getBool(c, KEY_BANK, true);
        if (isSms) return getBool(c, KEY_SMS, true);
        return getBool(c, KEY_OTHER, true);
    }
}
