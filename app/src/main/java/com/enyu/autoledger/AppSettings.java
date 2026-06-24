package com.enyu.autoledger;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class AppSettings {
    private static final String PREF = "auto_ledger_settings_v6";

    public static final String KEY_LINE_PAY = "detect_line_pay";
    public static final String KEY_INVOICE = "detect_invoice";
    public static final String KEY_BANK = "detect_bank";
    public static final String KEY_SMS = "detect_sms";
    public static final String KEY_OTHER = "detect_other";
    public static final String KEY_GOOGLE_WALLET = "detect_google_wallet";
    public static final String KEY_EXCLUDE_OWN = "exclude_own";
    public static final String KEY_DEDUPE = "dedupe";
    public static final String KEY_GOOGLE_BOUND = "google_bound";
    public static final String KEY_DARK_MODE = "dark_mode";
    public static final String KEY_CROSS_SOURCE_DEDUPE = "cross_source_dedupe";
    public static final String KEY_THEME = "theme";
    public static final String KEY_PALETTE = "palette";
    public static final String KEY_MONTHLY_BUDGET = "monthly_budget";
    public static final String KEY_ONBOARDED = "onboarded_v26";
    public static final String KEY_NOTIFY_DAILY = "notify_daily_report";
    public static final String KEY_NOTIFY_AUTO_SAVED = "notify_auto_saved";
    public static final String KEY_NOTIFY_BUDGET = "notify_budget";
    public static final String KEY_NOTIFY_DUPLICATE = "notify_duplicate";
    public static final String KEY_DAILY_NOTIFY_TIME = "daily_notify_time";
    public static final String KEY_CARRIER_BARCODE = "carrier_barcode";
    public static final String KEY_WIDGET_IMAGE_URI = "widget_image_uri";
    public static final String KEY_WIDGET_IMAGE_FILE = "widget_image_file";
    public static final String KEY_WIDGET_IMAGE_HEIGHT = "widget_image_height";
    public static final String KEY_MONTHLY_EXTRA_PREFIX = "monthly_extra_";
    public static final String KEY_DEBT_RECORDS = "debt_records_v1";

    public static final String KEY_EXPENSE_CATEGORIES = "expense_categories";
    public static final String KEY_INCOME_CATEGORIES = "income_categories";
    public static final String KEY_QUICK_EXPENSE = "quick_expense";
    public static final String KEY_QUICK_INCOME = "quick_income";
    public static final String KEY_QUICK_EXPENSE_USE_PREFIX = "quick_expense_use_";
    public static final String KEY_QUICK_INCOME_USE_PREFIX = "quick_income_use_";

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


    public static String currentMonthKey() {
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyyMM", java.util.Locale.TAIWAN);
        return f.format(new java.util.Date());
    }

    public static int getMonthlyExtra(Context c) {
        return sp(c).getInt(KEY_MONTHLY_EXTRA_PREFIX + currentMonthKey(), 0);
    }

    public static void addMonthlyExtra(Context c, int amount) {
        if (amount <= 0) return;
        String key = KEY_MONTHLY_EXTRA_PREFIX + currentMonthKey();
        sp(c).edit().putInt(key, Math.max(0, sp(c).getInt(key, 0) + amount)).apply();
    }

    public static int getMonthlyUsableBudget(Context c) {
        return getMonthlyBudget(c) + getMonthlyExtra(c);
    }

    public static String getString(Context c, String key, String def) {
        return sp(c).getString(key, def);
    }

    public static void setString(Context c, String key, String value) {
        sp(c).edit().putString(key, value == null ? "" : value).apply();
    }

    public static List<String> getExpenseCategories(Context c) {
        return getList(c, KEY_EXPENSE_CATEGORIES, "餐飲\n交通\n超商\n購物\n娛樂\n訂閱\n提款\n未分類");
    }

    public static List<String> getIncomeCategories(Context c) {
        return getList(c, KEY_INCOME_CATEGORIES, "收入\n薪水\n零用錢\n打工\n退款\n紅包");
    }

    public static List<String> getQuickExpense(Context c) {
        return getList(c, KEY_QUICK_EXPENSE, "餐飲\n交通\n飲料\n停車\n全聯\n早餐");
    }

    public static List<String> getQuickIncome(Context c) {
        return getList(c, KEY_QUICK_INCOME, "零用錢\n薪水\n打工\n紅包\n退款\n獎金");
    }

    private static String quickName(String line) {
        if (line == null) return "";
        String[] p = line.split("\\|");
        return p.length > 0 ? p[0].trim() : line.trim();
    }

    private static String quickUseKey(boolean income, String name) {
        String clean = name == null ? "" : name.trim();
        return (income ? KEY_QUICK_INCOME_USE_PREFIX : KEY_QUICK_EXPENSE_USE_PREFIX) + clean;
    }

    public static int getQuickUsage(Context c, boolean income, String name) {
        if (name == null || name.trim().isEmpty()) return 0;
        return sp(c).getInt(quickUseKey(income, name), 0);
    }

    public static void incrementQuickUsage(Context c, boolean income, String name) {
        if (name == null || name.trim().isEmpty()) return;
        String key = quickUseKey(income, name);
        sp(c).edit().putInt(key, sp(c).getInt(key, 0) + 1).apply();
    }

    public static void addQuickItem(Context c, boolean income, String name) {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty()) return;
        List<String> items = income ? getQuickIncome(c) : getQuickExpense(c);
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String item : items) {
            String n = quickName(item);
            if (!n.isEmpty()) set.add(n);
        }
        set.add(clean);
        setList(c, income ? KEY_QUICK_INCOME : KEY_QUICK_EXPENSE, new ArrayList<>(set));
    }

    public static List<String> getSortedQuickItems(Context c, boolean income) {
        List<String> src = income ? getQuickIncome(c) : getQuickExpense(c);
        final ArrayList<String> cleaned = new ArrayList<>();
        final LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String item : src) {
            String n = quickName(item);
            if (!n.isEmpty()) set.add(n);
        }
        cleaned.addAll(set);
        Collections.sort(cleaned, new Comparator<String>() {
            @Override public int compare(String a, String b) {
                int ca = getQuickUsage(c, income, a);
                int cb = getQuickUsage(c, income, b);
                if (ca != cb) return cb - ca;
                return 0;
            }
        });
        return cleaned;
    }

    public static int getWidgetImageHeight(Context c) {
        return sp(c).getInt(KEY_WIDGET_IMAGE_HEIGHT, 76);
    }

    public static void setWidgetImageHeight(Context c, int dp) {
        int safe = Math.max(48, Math.min(130, dp));
        sp(c).edit().putInt(KEY_WIDGET_IMAGE_HEIGHT, safe).apply();
    }

    public static void setList(Context c, String key, List<String> items) {
        StringBuilder b = new StringBuilder();
        if (items != null) {
            LinkedHashSet<String> set = new LinkedHashSet<>();
            for (String item : items) {
                String clean = item == null ? "" : item.trim();
                if (!clean.isEmpty()) set.add(clean);
            }
            for (String item : set) {
                if (b.length() > 0) b.append('\n');
                b.append(item);
            }
        }
        setString(c, key, b.toString());
    }

    public static List<String> getList(Context c, String key, String defaultText) {
        String raw = getString(c, key, defaultText);
        List<String> out = new ArrayList<>();
        Set<String> unique = new LinkedHashSet<>();
        if (raw != null) {
            for (String line : raw.split("\\n")) {
                String clean = line.trim();
                if (!clean.isEmpty()) unique.add(clean);
            }
        }
        out.addAll(unique);
        return out;
    }

    public static boolean shouldDetectSource(Context c, String packageName, String appName, String title, String text) {
        String all = ((packageName == null ? "" : packageName) + " " +
                (appName == null ? "" : appName) + " " +
                (title == null ? "" : title) + " " +
                (text == null ? "" : text)).toLowerCase();

        boolean isLinePay = all.contains("line pay") || all.contains("linepay") || all.contains("line錢包") || all.contains("line pay付款") || all.contains("line pay 付款");
        boolean isInvoice = all.contains("載具") || all.contains("發票") || all.contains("invoice") || all.contains("einvoice") || all.contains("財政部");
        boolean isGoogleWallet = all.contains("google wallet") || all.contains("google pay") || all.contains("gpay") || all.contains("google錢包") || all.contains("google 錢包") || all.contains("walletnfcrel");
        boolean isBank = all.contains("銀行") || all.contains("帳戶") || all.contains("信用卡") || all.contains("金融卡") || all.contains("刷卡") || all.contains("atm") || all.contains("bank");
        boolean isSms = all.contains("sms") || all.contains("mms") || all.contains("簡訊") || all.contains("訊息");

        if (isLinePay) return getBool(c, KEY_LINE_PAY, true);
        if (isInvoice) return getBool(c, KEY_INVOICE, true);
        if (isGoogleWallet) return getBool(c, KEY_GOOGLE_WALLET, true);
        if (isBank) return getBool(c, KEY_BANK, true);
        if (isSms) return getBool(c, KEY_SMS, true);
        return getBool(c, KEY_OTHER, true);
    }
}
