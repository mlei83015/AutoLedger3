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
        AppSettings.ensureMonthlyRemainingBalance(context);
        SharedPreferences sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        Set<String> seen = new HashSet<>(sp.getStringSet(KEY_SEEN, new HashSet<String>()));
        if (tx.hash != null && !tx.hash.isEmpty() && seen.contains(tx.hash)) {
            log(context, "略過重複通知 hash｜" + summary(tx));
            return false;
        }
        List<Transaction> list = getAll(context);
        if (AppSettings.getBool(context, AppSettings.KEY_DEDUPE, true) && mergePointDiscountIfNeeded(context, list, tx)) {
            markSeen(sp, seen, tx);
            return true;
        }
        if (AppSettings.getBool(context, AppSettings.KEY_DEDUPE, true) && looksDuplicate(context, list, tx)) {
            return false;
        }
        list.add(tx);
        saveAll(context, list);
        applyBudgetImpactForNewTransaction(context, tx);
        markSeen(sp, seen, tx);
        log(context, "新增成功｜" + summary(tx));
        return true;
    }

    private static void applyBudgetImpactForNewTransaction(Context context, Transaction tx) {
        if (tx == null) return;
        AppSettings.ensureMonthlyRemainingBalance(context);
        if ("expense".equals(tx.direction)) {
            AppSettings.addToCurrentRemainingBalance(context, -Math.max(0, tx.amount));
        }
        // V32：收入預設不加回剩餘餘額；只有手動新增收入勾選「加回」時，畫面會另外呼叫 addToCurrentRemainingBalance。
    }

    private static void applyBudgetImpactForReplacement(Context context, Transaction oldTx, Transaction newTx) {
        if (oldTx == null || newTx == null) return;
        AppSettings.ensureMonthlyRemainingBalance(context);
        int oldImpact = "expense".equals(oldTx.direction) ? -Math.max(0, oldTx.amount) : 0;
        int newImpact = "expense".equals(newTx.direction) ? -Math.max(0, newTx.amount) : 0;
        int delta = newImpact - oldImpact;
        if (delta != 0) AppSettings.addToCurrentRemainingBalance(context, delta);
    }


    private static void markSeen(SharedPreferences sp, Set<String> seen, Transaction tx) {
        if (tx == null || tx.hash == null || tx.hash.isEmpty()) return;
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

    private static boolean mergePointDiscountIfNeeded(Context context, List<Transaction> list, Transaction tx) {
        if (tx == null || tx.hash == null || tx.hash.startsWith("manual-") || !"expense".equals(tx.direction)) return false;
        if (isTransferRecord(tx)) return false;
        String newType = sourceType(tx);
        if (!isPaymentSignal(newType)) return false;

        int checked = 0;
        for (int i = 0; i < list.size(); i++) {
            if (checked++ > 220) break;
            Transaction old = list.get(i);
            if (old == null || !"expense".equals(old.direction)) continue;
            if (isTransferRecord(old)) continue;
            if (old.hash != null && old.hash.startsWith("manual-")) continue;
            if (old.amount == tx.amount) continue;

            String oldType = sourceType(old);
            if (!isPaymentSignal(oldType)) continue;
            if (oldType.equals(newType)) continue;

            long diff = Math.abs(old.timeMillis - tx.timeMillis);
            boolean sameDate = sameDay(old.timeMillis, tx.timeMillis);
            boolean sameMerchant = merchantSimilar(old.merchant, tx.merchant)
                    || merchantSimilar(old.raw, tx.raw)
                    || merchantSimilar(old.merchant, tx.raw)
                    || merchantSimilar(old.raw, tx.merchant);
            boolean sameRaw = compact(old.raw).equals(compact(tx.raw));
            boolean discountEvidence = hasDiscountEvidence(old) || hasDiscountEvidence(tx);
            boolean knownPair = isLineBankWalletPair(oldType, newType) || isInvoiceWithWalletBank(oldType, newType);

            // V33 修正版：不同金額只在「真的像同一筆折抵」時才合併。
            // 不能再只因為 LINE Pay / 銀行 / Google 錢包通知時間很近就把 90 元與 20 元混成同一筆。
            boolean trustedDiscountMatch = sameMerchant || sameRaw || discountEvidence;

            // V31 修正版：如果其中一筆已經有明確「原價 / 點數折抵 / 實付」，
            // 只把另一筆金額等於「原價」或「實付」的通知視為同一筆；
            // 不再用最大值/最小值硬猜，避免 LINE Pay 使用 3 點被誤判成折抵 128 元。
            if (old.discountAmount > 0 || tx.discountAmount > 0 || old.originalAmount > 0 || tx.originalAmount > 0) {
                if (explicitDiscountRelated(old, tx) && knownPair && trustedDiscountMatch && diff <= 72L * 60L * 60L * 1000L && (sameDate || sameMerchant || diff <= 45L * 60L * 1000L)) {
                    Transaction merged = buildExplicitDiscountMergedTransaction(old, tx);
                    list.set(i, merged);
                    saveAll(context, list);
                    applyBudgetImpactForReplacement(context, old, merged);
                    log(context, "V31 明確點數/優惠通知合併｜原價 " + money(merged.originalAmount) + "｜實付 " + money(merged.amount) + "｜折抵 " + money(merged.discountAmount));
                    return true;
                }
                continue;
            }

            int high = Math.max(old.amount, tx.amount);
            int low = Math.min(old.amount, tx.amount);
            int discount = high - low;
            if (discount <= 0) continue;
            boolean reasonableDiscount = discount <= 500 || low >= Math.round(high * 0.55f);
            boolean strongTiming = diff <= 45L * 60L * 1000L;
            boolean delayedInvoice = isInvoiceWithWalletBank(oldType, newType) && diff <= 72L * 60L * 60L * 1000L && (sameDate || sameMerchant);
            boolean delayedLineBank = isLineBankWalletPair(oldType, newType) && diff <= 12L * 60L * 60L * 1000L && (sameDate && sameMerchant);

            if (knownPair && reasonableDiscount && trustedDiscountMatch && (strongTiming || delayedInvoice || delayedLineBank)) {
                Transaction merged = buildDiscountMergedTransaction(old, tx);
                list.set(i, merged);
                saveAll(context, list);
                applyBudgetImpactForReplacement(context, old, merged);
                log(context, "V23 點數/優惠折抵合併｜原價 " + money(merged.originalAmount) + "｜實付 " + money(merged.amount) + "｜折抵 " + money(merged.discountAmount));
                return true;
            }
        }
        return false;
    }

    private static boolean explicitDiscountRelated(Transaction a, Transaction b) {
        if (a == null || b == null) return false;
        if (a.amount == b.amount) return true;
        if (a.originalAmount > 0 && (b.amount == a.originalAmount || b.originalAmount == a.originalAmount)) return true;
        if (b.originalAmount > 0 && (a.amount == b.originalAmount || a.originalAmount == b.originalAmount)) return true;
        return false;
    }

    private static Transaction buildExplicitDiscountMergedTransaction(Transaction old, Transaction tx) {
        Transaction rich = (old.originalAmount > 0 || old.discountAmount > 0) ? old : tx;
        Transaction other = rich == old ? tx : old;
        int original = rich.originalAmount > 0 ? rich.originalAmount : Math.max(old.amount, tx.amount);
        int actual = rich.amount > 0 ? rich.amount : Math.min(old.amount, tx.amount);
        int discount = rich.discountAmount > 0 ? rich.discountAmount : Math.max(0, original - actual);
        String mergedRaw = safe(rich.raw);
        if (!safe(other.raw).isEmpty() && !compact(mergedRaw).contains(compact(other.raw))) {
            mergedRaw += (mergedRaw.isEmpty() ? "" : "\n\n") + "相關通知：" + other.raw;
        }
        mergedRaw += "\n\n系統辨識：明確點數 / 優惠折抵，原價 " + money(original) + "，折抵 " + money(discount) + "，實付 " + money(actual) + "。";
        return new Transaction(
                Math.min(old.timeMillis, tx.timeMillis),
                actual,
                original,
                discount,
                "expense",
                !safe(rich.source).isEmpty() ? rich.source : other.source,
                !safe(rich.merchant).isEmpty() ? rich.merchant : other.merchant,
                !safe(rich.category).isEmpty() ? rich.category : other.category,
                mergedRaw,
                !safe(rich.hash).isEmpty() ? rich.hash : other.hash,
                !safe(rich.icon).isEmpty() ? rich.icon : other.icon
        );
    }

    private static Transaction buildDiscountMergedTransaction(Transaction old, Transaction tx) {
        Transaction lower = old.amount <= tx.amount ? old : tx;
        Transaction higher = old.amount > tx.amount ? old : tx;
        int original = Math.max(Math.max(old.originalAmount, tx.originalAmount), higher.amount);
        int actual = Math.min(old.amount, tx.amount);
        int discount = Math.max(Math.max(old.discountAmount, tx.discountAmount), original - actual);
        String mergedRaw = safe(lower.raw);
        if (!safe(higher.raw).isEmpty() && !compact(lower.raw).contains(compact(higher.raw))) {
            mergedRaw += (mergedRaw.isEmpty() ? "" : "\n\n") + "原價通知：" + higher.raw;
        }
        mergedRaw += "\n\n系統辨識：可能使用點數 / 優惠折抵，原價 " + money(original) + "，折抵 " + money(discount) + "，實付 " + money(actual) + "。";
        return new Transaction(
                Math.min(old.timeMillis, tx.timeMillis),
                actual,
                original,
                discount,
                "expense",
                !safe(lower.source).isEmpty() ? lower.source : old.source,
                !safe(lower.merchant).isEmpty() ? lower.merchant : old.merchant,
                !safe(lower.category).isEmpty() ? lower.category : old.category,
                mergedRaw,
                !safe(lower.hash).isEmpty() ? lower.hash : old.hash,
                !safe(lower.icon).isEmpty() ? lower.icon : old.icon
        );
    }

    private static boolean isLineBankWalletPair(String a, String b) {
        boolean hasLine = "line_pay".equals(a) || "line_pay".equals(b);
        boolean hasActualPay = "bank_card".equals(a) || "bank_card".equals(b) || "google_wallet".equals(a) || "google_wallet".equals(b) || "wallet".equals(a) || "wallet".equals(b);
        return hasLine && hasActualPay;
    }

    private static boolean isInvoiceWithWalletBank(String a, String b) {
        boolean hasInvoice = "invoice".equals(a) || "invoice".equals(b);
        boolean hasActualPay = "bank_card".equals(a) || "bank_card".equals(b) || "google_wallet".equals(a) || "google_wallet".equals(b) || "line_pay".equals(a) || "line_pay".equals(b) || "wallet".equals(a) || "wallet".equals(b);
        return hasInvoice && hasActualPay;
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
            boolean transferPair = isTransferRecord(old) || isTransferRecord(tx);
            boolean sameSource = !safe(old.source).isEmpty() && safe(old.source).equalsIgnoreCase(safe(tx.source));
            boolean sameMerchant = merchantSimilar(old.merchant, tx.merchant) || merchantSimilar(old.raw, tx.raw) || merchantSimilar(old.merchant, tx.raw) || merchantSimilar(old.raw, tx.merchant);
            boolean sameRaw = compact(old.raw).equals(compact(tx.raw));
            boolean bothPaymentSignals = isPaymentSignal(oldType) && isPaymentSignal(newType);
            boolean crossSource = !oldType.equals(newType) || !safe(old.source).equalsIgnoreCase(safe(tx.source));

            if (diff <= veryShortWindow && (sameSource || sameMerchant || sameRaw || (!transferPair && bothPaymentSignals))) {
                logDuplicate(context, old, tx, "5 分鐘內同金額付款/同來源/同店家");
                return true;
            }

            if (!transferPair && diff <= shortWindow && bothPaymentSignals) {
                logDuplicate(context, old, tx, "30 分鐘內同金額付款通知：LINE Pay / Google 錢包 / 銀行 / 載具交叉比對");
                return true;
            }

            if (!AppSettings.getBool(context, AppSettings.KEY_CROSS_SOURCE_DEDUPE, true) || !"expense".equals(tx.direction)) {
                continue;
            }

            boolean invoicePair = !transferPair && (("invoice".equals(oldType) && isPaymentSignal(newType)) || ("invoice".equals(newType) && isPaymentSignal(oldType)));
            boolean walletBankPair = !transferPair && isWalletOrBank(oldType) && isWalletOrBank(newType) && crossSource;
            boolean strongKnownCrossSource = !transferPair && isPaymentSignal(oldType) && isPaymentSignal(newType) && crossSource && sameDay(old.timeMillis, tx.timeMillis);
            boolean trustedSameAmountMatch = sameMerchant || sameRaw || diff <= shortWindow;

            if (strongKnownCrossSource && trustedSameAmountMatch && diff <= 24L * 60L * 60L * 1000L) {
                // V33：同一天、同金額跨來源仍會去重，但需要時間近、同店家或原文相同，避免同日兩筆剛好同金額被吃掉。
                // 這是為了避免 LINE Pay 先跳、銀行或載具晚一點又跳，造成一筆消費記兩次。
                logDuplicate(context, old, tx, "V33 同日同金額跨來源付款通知，判定同一筆");
                return true;
            }

            if (invoicePair && diff <= invoiceWindow && (sameDay(old.timeMillis, tx.timeMillis) || sameMerchant || diff <= walletBankWindow)) {
                logDuplicate(context, old, tx, "載具與付款通知同金額，判定同一筆");
                return true;
            }

            if (walletBankPair && trustedSameAmountMatch && diff <= walletBankWindow && sameDay(old.timeMillis, tx.timeMillis)) {
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
        if (s.contains("銀行") || s.contains("中國信託") || s.contains("中信") || s.contains("ctbc") || s.contains("帳戶") || s.contains("轉帳") || s.contains("匯款") || s.contains("扣款") || s.contains("信用卡") || s.contains("金融卡") || s.contains("刷卡") || s.contains("bank") || s.contains("visa") || s.contains("mastercard")) return "bank_card";
        if (s.contains("錢包") || s.contains("街口") || s.contains("全支付") || s.contains("悠遊付") || s.contains("pi拍錢包") || s.contains("pay")) return "wallet";
        return "unknown";
    }

    private static boolean hasDiscountEvidence(Transaction t) {
        if (t == null) return false;
        if (t.originalAmount > 0 || t.discountAmount > 0) return true;
        String s = compact(t.raw).toLowerCase(Locale.ROOT);
        return s.contains("折抵")
                || s.contains("折扣")
                || s.contains("優惠")
                || s.contains("點數")
                || s.contains("抵用")
                || s.contains("實付")
                || s.contains("原價")
                || s.contains("point")
                || s.contains("coupon")
                || s.contains("discount");
    }

    private static boolean isTransferRecord(Transaction t) {
        if (t == null) return false;
        String s = compact(t.source + " " + t.merchant + " " + t.category + " " + t.raw);
        return s.contains("轉帳")
                || s.contains("匯款")
                || s.contains("轉出")
                || s.contains("轉給")
                || s.contains("Outwardtransfer")
                || s.contains("outwardtransfer");
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
        if (x.contains(y) || y.contains(x)) return true;
        String shorter = x.length() <= y.length() ? x : y;
        String longer = x.length() > y.length() ? x : y;
        for (int len = Math.min(6, shorter.length()); len >= 3; len--) {
            for (int i = 0; i + len <= shorter.length(); i++) {
                String token = shorter.substring(i, i + len);
                if (isMeaningfulToken(token) && longer.contains(token)) return true;
            }
        }
        return false;
    }

    private static boolean isMeaningfulToken(String token) {
        if (token == null || token.length() < 3) return false;
        if (token.matches("[0-9]+")) return false;
        String t = token.toLowerCase(Locale.ROOT);
        return !(t.contains("line") || t.contains("pay") || t.contains("nt") || t.contains("twd") || t.contains("通知") || t.contains("付款") || t.contains("消費") || t.contains("交易") || t.contains("金額"));
    }

    private static String merchantKey(String s) {
        String x = compact(s).toLowerCase(Locale.ROOT);
        x = x.replace("股份有限公司", "").replace("有限公司", "").replace("電子發票", "").replace("載具", "").replace("通知", "").replace("消費", "").replace("付款", "").replace("刷卡", "").replace("交易", "");
        x = x.replace("linepay", "").replace("line", "").replace("pay", "").replace("google", "").replace("wallet", "").replace("錢包", "").replace("銀行", "").replace("信用卡", "");
        x = x.replace("付款完成", "").replace("交易成功", "").replace("您有1張新發票進來囉", "").replace("總共花", "").replace("金額", "").replace("原價", "").replace("實付", "").replace("折抵", "");
        x = x.replaceAll("[0-9]+", "");
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
        try { BalanceWidgetProvider.updateAll(context); } catch (Exception ignored) { }
    }

    public static synchronized boolean update(Context context, String originalHash, long originalTime, Transaction edited) {
        if (edited == null || edited.amount <= 0) return false;
        List<Transaction> list = getAll(context);
        boolean updated = false;
        Transaction old = null;
        for (int i = 0; i < list.size(); i++) {
            Transaction t = list.get(i);
            if (sameIdentity(t, originalHash, originalTime)) {
                old = t;
                list.set(i, edited);
                updated = true;
                break;
            }
        }
        if (updated) {
            saveAll(context, list);
            applyBudgetImpactForReplacement(context, old, edited);
            log(context, "修改紀錄｜" + summary(edited));
        }
        return updated;
    }

    public static synchronized boolean delete(Context context, String hash, long timeMillis) {
        List<Transaction> list = getAll(context);
        boolean removed = false;
        Transaction removedTx = null;
        for (int i = list.size() - 1; i >= 0; i--) {
            Transaction t = list.get(i);
            if (sameIdentity(t, hash, timeMillis)) {
                log(context, "刪除紀錄｜" + summary(t));
                removedTx = t;
                list.remove(i);
                removed = true;
                break;
            }
        }
        if (removed) {
            saveAll(context, list);
            if (removedTx != null && "expense".equals(removedTx.direction)) {
                AppSettings.addToCurrentRemainingBalance(context, Math.max(0, removedTx.amount));
            }
        }
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
        try { AppSettings.clearCurrentMonthBalanceLink(context); } catch (Exception ignored) { }
        try { BalanceWidgetProvider.updateAll(context); } catch (Exception ignored) { }
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
        int removedCurrentMonthExpense = 0;
        long monthStart = startOfMonth(0);
        long monthEnd = startOfMonth(1);
        for (Transaction tx : chronological) {
            if (AppSettings.getBool(context, AppSettings.KEY_DEDUPE, true) && looksDuplicate(context, kept, tx)) {
                removed++;
                if (tx != null && "expense".equals(tx.direction) && tx.timeMillis >= monthStart && tx.timeMillis < monthEnd) {
                    removedCurrentMonthExpense += Math.max(0, tx.amount);
                }
            } else {
                kept.add(tx);
            }
        }
        if (removed > 0) {
            saveAll(context, kept);
            if (removedCurrentMonthExpense > 0) AppSettings.addToCurrentRemainingBalance(context, removedCurrentMonthExpense);
        }
        log(context, "自動除錯完成｜移除疑似重複 " + removed + " 筆");
        return removed;
    }

    public static int countBetween(Context context, long start, long end) {
        int count = 0;
        for (Transaction t : getAll(context)) {
            if (t.timeMillis >= start && t.timeMillis < end) count++;
        }
        return count;
    }

    public static int autoCountBetween(Context context, long start, long end) {
        int count = 0;
        for (Transaction t : getAll(context)) {
            if (t.timeMillis >= start && t.timeMillis < end && (t.hash == null || !t.hash.startsWith("manual-"))) count++;
        }
        return count;
    }

    public static int manualCountBetween(Context context, long start, long end) {
        int count = 0;
        for (Transaction t : getAll(context)) {
            if (t.timeMillis >= start && t.timeMillis < end && t.hash != null && t.hash.startsWith("manual-")) count++;
        }
        return count;
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
        return AppSettings.getCurrentRemainingBalance(context);
    }

    public static int monthExpense(Context context) {
        return expenseBetween(context, startOfMonth(0), startOfMonth(1));
    }

    public static int monthIncome(Context context) {
        return incomeBetween(context, startOfMonth(0), startOfMonth(1));
    }

    public static int effectiveMonthExpense(Context context) {
        return Math.max(0, AppSettings.getMonthlyUsableBudget(context) - AppSettings.getCurrentRemainingBalance(context));
    }

    public static int monthBudgetRemaining(Context context) {
        return Math.max(0, AppSettings.getCurrentRemainingBalance(context));
    }

    public static int forecastMonthExpense(Context context) {
        int spent = effectiveMonthExpense(context);
        Calendar now = Calendar.getInstance();
        int day = Math.max(1, now.get(Calendar.DAY_OF_MONTH));
        int maxDay = now.getActualMaximum(Calendar.DAY_OF_MONTH);
        return Math.round(spent * (maxDay / (float) day));
    }

    public static int suggestedSaving(Context context) {
        int budget = AppSettings.getMonthlyUsableBudget(context);
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
        b.append("時間,收入支出,實際金額,原價,折抵,來源,店家/項目,分類,備註,原始內容\n");
        for (Transaction t : getAll(context)) {
            b.append(csv(formatTime(t.timeMillis))).append(',')
                    .append(csv("income".equals(t.direction) ? "收入" : "支出")).append(',')
                    .append(t.amount).append(',')
                    .append(t.originalAmount).append(',')
                    .append(t.discountAmount).append(',')
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
