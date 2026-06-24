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
    private static final String KEY_DEBUG_LOGS = "debug_logs";

    public static synchronized boolean add(Context context, Transaction tx) {
        if (tx == null || tx.amount <= 0) return false;
        SharedPreferences sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        Set<String> seen = new HashSet<>(sp.getStringSet(KEY_SEEN, new HashSet<String>()));
        if (tx.hash != null && !tx.hash.isEmpty() && seen.contains(tx.hash)) {
            log(context, "略過重複通知 hash｜" + summary(tx));
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
        log(context, "新增成功｜" + summary(tx));
        return true;
    }

    private static boolean looksDuplicate(Context context, List<Transaction> list, Transaction tx) {
        if (tx.hash != null && tx.hash.startsWith("manual-")) return false;

        long veryShortWindow = 5L * 60L * 1000L;
        long shortWindow = 30L * 60L * 1000L;
        long walletBankWindow = 12L * 60L * 60L * 1000L;
        long invoiceWindow = 72L * 60L * 60L * 1000L;

        int checked = 0;
        for (Transaction old : list) {
            if (checked++ > 180) break;
            if (old == null) continue;
            if (!safe(old.direction).equals(safe(tx.direction))) continue;
            if (old.amount != tx.amount) continue;
            if (old.hash != null && old.hash.startsWith("manual-") && tx.hash != null && !tx.hash.startsWith("manual-")) {
                // 手動補登不直接擋自動通知，避免使用者故意先手動記帳後自動通知被吃掉。
                continue;
            }

            long diff = Math.abs(old.timeMillis - tx.timeMillis);
            String oldType = sourceType(old);
            String newType = sourceType(tx);
            boolean sameSource = safe(old.source).equalsIgnoreCase(safe(tx.source));
            boolean sameMerchant = merchantSimilar(old.merchant, tx.merchant) || merchantSimilar(old.raw, tx.raw) || merchantSimilar(old.merchant, tx.raw) || merchantSimilar(old.raw, tx.merchant);
            boolean sameRaw = compact(old.raw).equals(compact(tx.raw));
            boolean bothPaymentSignals = isPaymentSignal(oldType) && isPaymentSignal(newType);
            boolean crossSource = !oldType.equals(newType) || !safe(old.source).equalsIgnoreCase(safe(tx.source));

            if (diff <= veryShortWindow && (sameSource || sameMerchant || sameRaw || bothPaymentSignals)) {
                logDuplicate(context, old, tx, "5 分鐘內同金額付款/同來源/同店家");
                return true;
            }

            if (diff <= shortWindow && bothPaymentSignals) {
                logDuplicate(context, old, tx, "30 分鐘內同金額付款通知：LINE Pay / Google 錢包 / 銀行 / 載具交叉比對");
                return true;
            }

            if (!AppSettings.getBool(context, AppSettings.KEY_CROSS_SOURCE_DEDUPE, true) || !"expense".equals(tx.direction)) {
                continue;
            }

            boolean invoicePair = ("invoice".equals(oldType) && isPaymentSignal(newType)) || ("invoice".equals(newType) && isPaymentSignal(oldType));
            boolean walletBankPair = isWalletOrBank(oldType) && isWalletOrBank(newType) && crossSource;

            if (invoicePair && diff <= invoiceWindow && (sameDay(old.timeMillis, tx.timeMillis) || sameMerchant || diff <= walletBankWindow)) {
                logDuplicate(context, old, tx, "載具與付款通知同金額，判定同一筆");
                return true;
            }

            if (walletBankPair && diff <= walletBankWindow && sameDay(old.timeMillis, tx.timeMillis)) {
                logDuplicate(context, old, tx, "LINE Pay / Google 錢包 / 銀行刷卡通知同金額，判定同一筆");
                return true;
            }

            if (sameMerchant && diff <= 6L * 60L * 60L * 1000L) {
                logDuplicate(context, old, tx, "同金額且店家相似");
                return true;
            }
        }
        return false;
    }

    private static String sourceType(Transaction t) {
        String s = compact(t.source + " " + t.merchant + " " + t.category + " " + t.raw).toLowerCase(Locale.ROOT);
        if (s.contains("載具") || s.contains("發票") || s.contains("invoice") || s.contains("einvoice") || s.contains("財政部")) return "invoice";
        if (s.contains("linepay") || s.contains("linepay") || s.contains("line") && s.contains("pay") || s.contains("line錢包")) return "line_pay";
        if (s.contains("googlewallet") || s.contains("googlepay") || s.contains("google錢包") || s.contains("google 錢包") || s.contains("walletnfcrel")) return "google_wallet";
        if (s.contains("銀行") || s.contains("信用卡") || s.contains("金融卡") || s.contains("刷卡") || s.contains("bank") || s.contains("visa") || s.contains("mastercard")) return "bank_card";
        if (s.contains("錢包") || s.contains("街口") || s.contains("全支付") || s.contains("悠遊付") || s.contains("pi拍錢包") || s.contains("pay")) return "wallet";
        return "unknown";
    }

    private static boolean isPaymentSignal(String type) {
        return "line_pay".equals(type) || "invoice".equals(type) || "google_wallet".equals(type) || "bank_card".equals(type) || "wallet".equals(type);
    }

    private static boolean isWalletOrBank(String type) {
        return "line_pay".equals(type) || "google_wallet".equals(type) || "bank_card".equals(type) || "wallet".equals(type);
    }

    private static boolean merchantSimilar(String a, String b) {
        String x = merchantKey(a);
        String y = merchantKey(b);
        if (x.length() < 2 || y.length() < 2) return false;
        return x.contains(y) || y.contains(x);
    }

    private static String merchantKey(String s) {
        String x = compact(s).toLowerCase(Locale.ROOT);
        x = x.replace("股份有限公司", "").replace("有限公司", "").replace("電子發票", "").replace("載具", "").replace("通知", "").replace("消費", "").replace("付款", "").replace("刷卡", "").replace("交易", "");
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
        int limit = Math.min(list.size(), 3000);
        for (int i = 0; i < limit; i++) {
            try { arr.put(list.get(i).toJson()); } catch (JSONException ignored) { }
        }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_TRANSACTIONS, arr.toString())
                .apply();
    }

    public static synchronized boolean update(Context context, String originalHash, long originalTime, Transaction edited) {
        if (edited == null || edited.amount <= 0) return false;
        List<Transaction> list = getAll(context);
        boolean updated = false;
        for (int i = 0; i < list.size(); i++) {
            Transaction t = list.get(i);
            if (sameIdentity(t, originalHash, originalTime)) {
                list.set(i, edited);
                updated = true;
                break;
            }
        }
        if (updated) {
            saveAll(context, list);
            log(context, "修改紀錄｜" + summary(edited));
        }
        return updated;
    }

    public static synchronized boolean delete(Context context, String hash, long timeMillis) {
        List<Transaction> list = getAll(context);
        boolean removed = false;
        for (int i = list.size() - 1; i >= 0; i--) {
            Transaction t = list.get(i);
            if (sameIdentity(t, hash, timeMillis)) {
                log(context, "刪除紀錄｜" + summary(t));
                list.remove(i);
                removed = true;
                break;
            }
        }
        if (removed) saveAll(context, list);
        return removed;
    }

    private static boolean sameIdentity(Transaction t, String hash, long timeMillis) {
        if (t == null) return false;
        if (!safe(hash).isEmpty() && !safe(t.hash).isEmpty() && safe(t.hash).equals(hash)) return true;
        return t.timeMillis == timeMillis;
    }

    public static synchronized void clear(Context context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_TRANSACTIONS)
                .remove(KEY_SEEN)
                .apply();
        log(context, "已清除全部記帳資料");
    }

    public static synchronized int autoFixDuplicates(Context context) {
        List<Transaction> original = getAll(context);
        List<Transaction> chronological = new ArrayList<>(original);
        Collections.sort(chronological, new Comparator<Transaction>() {
            @Override
            public int compare(Transaction a, Transaction b) {
                return Long.compare(a.timeMillis, b.timeMillis);
            }
        });
        List<Transaction> kept = new ArrayList<>();
        int removed = 0;
        for (Transaction tx : chronological) {
            if (AppSettings.getBool(context, AppSettings.KEY_DEDUPE, true) && looksDuplicate(context, kept, tx)) {
                removed++;
            } else {
                kept.add(tx);
            }
        }
        if (removed > 0) saveAll(context, kept);
        log(context, "自動除錯完成｜移除疑似重複 " + removed + " 筆");
        return removed;
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

    public static int totalBalance(Context context) {
        return AppSettings.getMonthlyBudget(context) + totalIncome(context) - totalExpense(context);
    }

    public static int monthExpense(Context context) {
        return expenseBetween(context, startOfMonth(0), startOfMonth(1));
    }

    public static int monthIncome(Context context) {
        return incomeBetween(context, startOfMonth(0), startOfMonth(1));
    }

    public static int forecastMonthExpense(Context context) {
        int spent = monthExpense(context);
        Calendar now = Calendar.getInstance();
        int day = Math.max(1, now.get(Calendar.DAY_OF_MONTH));
        int maxDay = now.getActualMaximum(Calendar.DAY_OF_MONTH);
        return Math.round(spent * (maxDay / (float) day));
    }

    public static int suggestedSaving(Context context) {
        int budget = AppSettings.getMonthlyBudget(context);
        int forecast = forecastMonthExpense(context);
        int possible = budget - forecast;
        if (possible <= 0) return 0;
        // 建議先抓可剩餘金額的 30%，避免建議太激進。
        return Math.max(0, Math.round(possible * 0.3f / 100f) * 100);
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

    public static String exportCsv(Context context) {
        StringBuilder b = new StringBuilder();
        b.append("時間,收入支出,金額,來源,店家/項目,分類,備註,原始內容\n");
        for (Transaction t : getAll(context)) {
            b.append(csv(formatTime(t.timeMillis))).append(',')
                    .append(csv("income".equals(t.direction) ? "收入" : "支出")).append(',')
                    .append(t.amount).append(',')
                    .append(csv(t.source)).append(',')
                    .append(csv(t.merchant)).append(',')
                    .append(csv(t.category)).append(',')
                    .append(csv(t.raw)).append(',')
                    .append(csv(t.hash)).append('\n');
        }
        return b.toString();
    }

    public static synchronized String exportJson(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        return sp.getString(KEY_TRANSACTIONS, "[]");
    }

    public static synchronized boolean importJson(Context context, String json) {
        try {
            JSONArray arr = new JSONArray(json == null ? "" : json.trim());
            List<Transaction> list = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                list.add(Transaction.fromJson(arr.getJSONObject(i)));
            }
            saveAll(context, list);
            log(context, "已還原備份｜共 " + list.size() + " 筆");
            return true;
        } catch (Exception e) {
            log(context, "還原失敗｜資料格式不正確");
            return false;
        }
    }

    private static String csv(String s) {
        String x = safe(s).replace("\"", "\"\"");
        return "\"" + x + "\"";
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

    private static void logDuplicate(Context context, Transaction old, Transaction tx, String reason) {
        log(context, "略過重複｜" + reason + "｜新 " + summary(tx) + "｜舊 " + summary(old));
    }

    public static synchronized void log(Context context, String message) {
        if (context == null || message == null) return;
        SharedPreferences sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String old = sp.getString(KEY_DEBUG_LOGS, "");
        String line = new SimpleDateFormat("MM/dd HH:mm:ss", Locale.TAIWAN).format(Calendar.getInstance().getTime()) + "  " + message;
        String next = line + (old.isEmpty() ? "" : "\n" + old);
        String[] lines = next.split("\\n");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Math.min(lines.length, 80); i++) {
            if (i > 0) b.append('\n');
            b.append(lines[i]);
        }
        sp.edit().putString(KEY_DEBUG_LOGS, b.toString()).apply();
    }

    public static String getDebugLogs(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_DEBUG_LOGS, "目前沒有除錯紀錄。");
    }

    public static void clearDebugLogs(Context context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(KEY_DEBUG_LOGS).apply();
    }

    private static String summary(Transaction t) {
        if (t == null) return "空資料";
        return ("income".equals(t.direction) ? "收入 " : "支出 ") + money(t.amount) + "｜" + safe(t.source) + "｜" + safe(t.merchant) + "｜" + formatTime(t.timeMillis);
    }
}
