package com.enyu.autoledger;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class TransactionStore {
    private static final String PREF = "auto_ledger_store";
    private static final String KEY_TRANSACTIONS = "transactions_json";
    private static final String KEY_SEEN = "seen_hashes";

    public static synchronized boolean add(Context context, Transaction tx) {
        if (tx == null || tx.amount <= 0) return false;
        SharedPreferences sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        Set<String> seen = new HashSet<>(sp.getStringSet(KEY_SEEN, new HashSet<String>()));
        if (tx.hash != null && !tx.hash.isEmpty() && seen.contains(tx.hash)) {
            return false;
        }
        List<Transaction> list = getAll(context);
        if (AppSettings.getBool(context, AppSettings.KEY_DEDUPE, true) && looksDuplicate(context, list, tx)) {
            return false;
        }
        list.add(tx);
        saveAll(context, list);
        if (tx.hash != null && !tx.hash.isEmpty()) {
            seen.add(tx.hash);
            if (seen.size() > 500) {
                Set<String> smaller = new HashSet<>();
                int i = 0;
                for (String h : seen) {
                    smaller.add(h);
                    if (++i >= 300) break;
                }
                seen = smaller;
            }
            sp.edit().putStringSet(KEY_SEEN, seen).apply();
        }
        return true;
    }

    private static boolean looksDuplicate(Context context, List<Transaction> list, Transaction tx) {
        if (tx.hash != null && tx.hash.startsWith("manual-")) return false;
        long window = 3L * 60L * 1000L;
        int checked = 0;
        for (Transaction old : list) {
            if (checked++ > 50) break;
            long diff = Math.abs(old.timeMillis - tx.timeMillis);
            if (diff > window) continue;
            if (!safe(old.direction).equals(safe(tx.direction))) continue;
            if (old.amount != tx.amount) continue;
            boolean sameSource = safe(old.source).equalsIgnoreCase(safe(tx.source));
            boolean sameMerchant = !safe(old.merchant).isEmpty() && safe(old.merchant).equalsIgnoreCase(safe(tx.merchant));
            boolean sameRaw = compact(old.raw).equals(compact(tx.raw));
            if (sameSource || sameMerchant || sameRaw) return true;
        }

        if (AppSettings.getBool(context, AppSettings.KEY_CROSS_SOURCE_DEDUPE, true) && "expense".equals(tx.direction)) {
            long crossWindow = 48L * 60L * 60L * 1000L;
            checked = 0;
            for (Transaction old : list) {
                if (checked++ > 120) break;
                if (old == null) continue;
                if (!"expense".equals(old.direction)) continue;
                if (old.amount != tx.amount) continue;
                if (old.hash != null && old.hash.startsWith("manual-")) continue;
                long diff = Math.abs(old.timeMillis - tx.timeMillis);
                if (diff > crossWindow) continue;

                boolean invoicePair = (isInvoice(old) && isPayment(tx)) || (isInvoice(tx) && isPayment(old));
                boolean similarMerchant = merchantSimilar(old.merchant, tx.merchant) || merchantSimilar(old.raw, tx.raw);
                boolean veryClose = diff <= 10L * 60L * 1000L;
                if (invoicePair && (sameDay(old.timeMillis, tx.timeMillis) || veryClose || similarMerchant)) {
                    return true;
                }
                if (similarMerchant && diff <= 6L * 60L * 60L * 1000L) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isInvoice(Transaction t) {
        String s = compact(t.source + " " + t.merchant + " " + t.category + " " + t.raw).toLowerCase(Locale.ROOT);
        return s.contains("載具") || s.contains("發票") || s.contains("invoice") || s.contains("einvoice") || s.contains("財政部");
    }

    private static boolean isPayment(Transaction t) {
        String s = compact(t.source + " " + t.merchant + " " + t.category + " " + t.raw).toLowerCase(Locale.ROOT);
        return s.contains("linepay") || s.contains("line") || s.contains("錢包") || s.contains("pay") || s.contains("銀行") || s.contains("信用卡") || s.contains("金融卡") || s.contains("街口") || s.contains("全支付") || s.contains("悠遊付") || s.contains("pi拍錢包");
    }

    private static boolean merchantSimilar(String a, String b) {
        String x = merchantKey(a);
        String y = merchantKey(b);
        if (x.length() < 2 || y.length() < 2) return false;
        return x.contains(y) || y.contains(x);
    }

    private static String merchantKey(String s) {
        String x = compact(s).toLowerCase(Locale.ROOT);
        x = x.replace("股份有限公司", "").replace("有限公司", "").replace("電子發票", "").replace("載具", "").replace("通知", "").replace("消費", "").replace("付款", "");
        return x;
    }

    private static boolean sameDay(long a, long b) {
        Calendar ca = Calendar.getInstance();
        ca.setTimeInMillis(a);
        Calendar cb = Calendar.getInstance();
        cb.setTimeInMillis(b);
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR);
    }

    private static String compact(String s) {
        return safe(s).replaceAll("\\s+", "").replaceAll("[，,。.:：]", "");
    }

    private static String safe(String s) { return s == null ? "" : s; }

    public static synchronized List<Transaction> getAll(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String raw = sp.getString(KEY_TRANSACTIONS, "[]");
        List<Transaction> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                list.add(Transaction.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException ignored) {
        }
        Collections.sort(list, new Comparator<Transaction>() {
            @Override
            public int compare(Transaction a, Transaction b) {
                return Long.compare(b.timeMillis, a.timeMillis);
            }
        });
        return list;
    }

    public static synchronized void saveAll(Context context, List<Transaction> list) {
        JSONArray arr = new JSONArray();
        if (list == null) list = new ArrayList<>();
        Collections.sort(list, new Comparator<Transaction>() {
            @Override
            public int compare(Transaction a, Transaction b) {
                return Long.compare(b.timeMillis, a.timeMillis);
            }
        });
        int limit = Math.min(list.size(), 2000);
        for (int i = 0; i < limit; i++) {
            try { arr.put(list.get(i).toJson()); } catch (JSONException ignored) { }
        }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_TRANSACTIONS, arr.toString())
                .apply();
    }

    public static synchronized void clear(Context context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_TRANSACTIONS)
                .remove(KEY_SEEN)
                .apply();
    }

    public static int expenseBetween(Context context, long start, long end) {
        int sum = 0;
        for (Transaction t : getAll(context)) {
            if ("expense".equals(t.direction) && t.timeMillis >= start && t.timeMillis < end) sum += t.amount;
        }
        return sum;
    }

    public static int incomeBetween(Context context, long start, long end) {
        int sum = 0;
        for (Transaction t : getAll(context)) {
            if ("income".equals(t.direction) && t.timeMillis >= start && t.timeMillis < end) sum += t.amount;
        }
        return sum;
    }

    public static int totalIncome(Context context) {
        int sum = 0;
        for (Transaction t : getAll(context)) if ("income".equals(t.direction)) sum += t.amount;
        return sum;
    }

    public static int totalExpense(Context context) {
        int sum = 0;
        for (Transaction t : getAll(context)) if ("expense".equals(t.direction)) sum += t.amount;
        return sum;
    }

    public static int monthExpense(Context context) {
        return expenseBetween(context, startOfMonth(0), startOfMonth(1));
    }

    public static int monthIncome(Context context) {
        return incomeBetween(context, startOfMonth(0), startOfMonth(1));
    }

    public static List<String> recentChips(Context context, String direction, int limit) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (Transaction t : getAll(context)) {
            if (!safe(direction).equals(t.direction)) continue;
            if (!safe(t.merchant).isEmpty()) set.add(t.merchant);
            if (!safe(t.category).isEmpty()) set.add(t.category);
            if (set.size() >= limit) break;
        }
        return new ArrayList<>(set);
    }

    public static long startOfDay(int offsetDays) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, offsetDays);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    public static long startOfMonth(int offsetMonths) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.MONTH, offsetMonths);
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    public static String formatTime(long millis) {
        return new SimpleDateFormat("MM/dd HH:mm", Locale.TAIWAN).format(millis);
    }

    public static String money(int amount) {
        return "$" + String.format(Locale.TAIWAN, "%,d", amount);
    }
}
