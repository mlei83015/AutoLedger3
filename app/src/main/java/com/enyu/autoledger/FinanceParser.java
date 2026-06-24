package com.enyu.autoledger;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FinanceParser {
    private static final Pattern[] AMOUNT_PATTERNS = new Pattern[]{
            Pattern.compile("(?:NT\\$|NTD|TWD|\\$|新臺幣|台幣|臺幣)\\s*([0-9,]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:金額|扣款|付款|支付|刷卡|消費|交易金額|消費金額|合計|共計|實付|實際付款|實際支付|折抵後|應付)[:：]?\\s*(?:NT\\$|NTD|TWD|\\$)?\\s*([0-9,]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("([0-9,]+)\\s*元")
    };

    private static final String ACTUAL_KEYS = "實付|實際付款|實際支付|實際扣款|折抵後|扣款金額|刷卡金額|已扣款|已刷卡|實收|支付金額|實際金額";
    private static final String ORIGINAL_KEYS = "原價|原始金額|原金額|付款金額|消費金額|訂單金額|交易金額|商品金額|應付|總金額|小計|共計|合計";
    private static final String DISCOUNT_KEYS = "點數折抵|LINE POINTS|Line Points|LINEPoints|點數|折抵|折扣|優惠券|優惠|折讓|抵用";

    public static Transaction parse(long timeMillis, String packageName, String appName, String title, String text) {
        String raw = join(appName, title, text).replace('，', ',');
        String normalized = raw.replace("　", " ").trim();

        AmountInfo info = analyzeAmounts(normalized);
        int amount = info.actualAmount > 0 ? info.actualAmount : extractAmount(normalized);
        if (amount <= 0) return null;

        String direction = detectDirection(normalized, packageName, appName);
        if (direction == null) return null;

        // V16 起：自動通知先不自動填分類與來源，使用者可點紀錄進去修改。
        // V23 起：如果通知本身有「原價 / 點數折抵 / 實付」，會把首頁與預算改用實付金額。
        String source = "";
        String merchant = "";
        String category = "";

        String enrichedRaw = normalized;
        if ("expense".equals(direction) && info.hasDiscountInfo()) {
            enrichedRaw += "\n\n系統辨識：";
            if (info.originalAmount > 0) enrichedRaw += "原價 $" + info.originalAmount + " ";
            if (info.discountAmount > 0) enrichedRaw += "折抵 $" + info.discountAmount + " ";
            enrichedRaw += "實付 $" + amount;
        }

        String hash = sha256(packageName + "|" + title + "|" + text + "|" + amount + "|" + (timeMillis / 60000));
        return new Transaction(timeMillis, amount, info.originalAmount, info.discountAmount, direction, source, merchant, category, enrichedRaw, hash, "");
    }

    private static String join(String appName, String title, String text) {
        StringBuilder b = new StringBuilder();
        if (appName != null) b.append(appName).append(' ');
        if (title != null) b.append(title).append(' ');
        if (text != null) b.append(text);
        return b.toString();
    }

    public static int extractAmount(String s) {
        if (s == null) return 0;
        for (Pattern p : AMOUNT_PATTERNS) {
            Matcher m = p.matcher(s);
            if (m.find()) {
                try {
                    return Integer.parseInt(m.group(1).replace(",", ""));
                } catch (Exception ignored) {
                }
            }
        }
        return 0;
    }

    private static AmountInfo analyzeAmounts(String s) {
        AmountInfo info = new AmountInfo();
        if (s == null) return info;

        int actual = amountAfterKeys(s, ACTUAL_KEYS);
        int original = amountAfterKeys(s, ORIGINAL_KEYS);
        int discount = amountAfterKeys(s, DISCOUNT_KEYS);
        List<Integer> all = allAmounts(s);

        // 有些 LINE Pay 會寫「付款 30，點數折抵 5，實付 25」。這種直接抓實付。
        if (actual > 0) info.actualAmount = actual;
        if (original > 0) info.originalAmount = original;
        if (discount > 0) info.discountAmount = discount;

        // 如果有原價與折抵，但沒有明確實付，就自己算實付。
        if (info.actualAmount <= 0 && info.originalAmount > 0 && info.discountAmount > 0 && info.originalAmount > info.discountAmount) {
            info.actualAmount = info.originalAmount - info.discountAmount;
        }

        // 如果通知出現多個金額，而且有折抵字眼，通常最大值是原價，最小值是實付。
        if (containsDiscountWord(s) && all.size() >= 2) {
            int min = Integer.MAX_VALUE;
            int max = 0;
            for (int v : all) {
                if (v <= 0) continue;
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
            if (max > 0 && min < Integer.MAX_VALUE && max > min) {
                if (info.originalAmount <= 0) info.originalAmount = max;
                if (info.actualAmount <= 0) info.actualAmount = min;
                if (info.discountAmount <= 0) info.discountAmount = max - min;
            }
        }

        if (info.originalAmount > 0 && info.actualAmount > 0 && info.originalAmount > info.actualAmount && info.discountAmount <= 0) {
            info.discountAmount = info.originalAmount - info.actualAmount;
        }
        return info;
    }

    private static boolean containsDiscountWord(String s) {
        if (s == null) return false;
        String upper = s.toUpperCase(Locale.ROOT);
        return upper.contains("折抵") || upper.contains("折扣") || upper.contains("優惠") || upper.contains("POINT") || upper.contains("點數") || upper.contains("抵用");
    }

    private static int amountAfterKeys(String s, String keys) {
        if (s == null) return 0;
        Pattern p = Pattern.compile("(?:" + keys + ")[^0-9]{0,16}(?:NT\\$|NTD|TWD|\\$)?\\s*([0-9,]+)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(s);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1).replace(",", "")); } catch (Exception ignored) { }
        }
        // 也支援「5 點折抵」這種金額在前面的寫法。
        Pattern p2 = Pattern.compile("([0-9,]+)\\s*(?:元|點)?[^0-9]{0,10}(?:" + keys + ")", Pattern.CASE_INSENSITIVE);
        Matcher m2 = p2.matcher(s);
        if (m2.find()) {
            try { return Integer.parseInt(m2.group(1).replace(",", "")); } catch (Exception ignored) { }
        }
        return 0;
    }

    private static List<Integer> allAmounts(String s) {
        ArrayList<Integer> out = new ArrayList<>();
        if (s == null) return out;
        Pattern p = Pattern.compile("(?:(?:NT\\$|NTD|TWD|\\$)\\s*([0-9,]{1,7})|([0-9,]{1,7})\\s*(?:元|點))", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(s);
        while (m.find()) {
            try {
                String g = m.group(1) != null ? m.group(1) : m.group(2);
                int v = Integer.parseInt(g.replace(",", ""));
                // 排除時間、日期、載具號碼中太短或太怪的數字。1~999999 都保留，因為點數可能很小。
                if (v > 0 && v < 1000000) out.add(v);
            } catch (Exception ignored) { }
        }
        return out;
    }

    private static String detectDirection(String s, String packageName, String appName) {
        String lower = (s + " " + packageName + " " + appName).toLowerCase(Locale.ROOT);
        boolean isLinePay = lower.contains("line pay") || lower.contains("linepay") || lower.contains("line錢包");
        boolean isGoogleWallet = lower.contains("google wallet") || lower.contains("google pay") || lower.contains("google錢包") || lower.contains("google 錢包") || lower.contains("walletnfcrel");

        if (containsAny(s, "入帳", "轉入", "匯入", "收到匯款", "薪資", "薪水", "存入", "退款", "退貨", "退刷", "還款入帳")) {
            return "income";
        }
        if (containsAny(s, "付款成功", "交易成功", "付款", "消費", "扣款", "刷卡", "提款", "轉出", "支出", "扣帳", "支付", "發票", "載具", "消費明細", "交易明細") || isLinePay || isGoogleWallet) {
            return "expense";
        }
        return null;
    }

    private static String detectMerchant(String s, String title, String appName) {
        if (s == null) return appName == null ? "未知來源" : appName;
        String[] markers = new String[]{"於", "在", "商店", "店家", "特店", "商戶", "地點", "Merchant", "merchant"};
        for (String marker : markers) {
            int idx = s.indexOf(marker);
            if (idx >= 0 && idx + marker.length() < s.length()) {
                String tail = s.substring(idx + marker.length()).trim();
                tail = tail.replaceAll("(消費|付款|扣款|支付|金額|共計|合計|NT\\$|元|交易).*$", "").trim();
                tail = tail.replaceAll("[,:：，。].*$", "").trim();
                if (tail.length() >= 2 && tail.length() <= 24) return tail;
            }
        }
        if (title != null && title.length() > 0 && title.length() <= 24) return title;
        return appName == null || appName.isEmpty() ? "未知來源" : appName;
    }

    private static String detectCategory(String s, String merchant, String direction) {
        if ("income".equals(direction)) return "收入";
        String all = (s + " " + merchant).toUpperCase(Locale.ROOT);
        if (containsAny(all, "7-ELEVEN", "711", "全家", "萊爾富", "OK超商", "統一超商", "便利商店", "超商")) return "超商";
        if (containsAny(all, "飲料", "咖啡", "早餐", "午餐", "晚餐", "餐", "麥當勞", "KFC", "星巴克", "迷客夏", "清心", "可不可")) return "餐飲";
        if (containsAny(all, "公車", "台鐵", "高鐵", "捷運", "UBER", "TAXI", "停車", "加油", "悠遊卡")) return "交通";
        if (containsAny(all, "提款", "ATM")) return "提款";
        if (containsAny(all, "NETFLIX", "SPOTIFY", "YOUTUBE", "APPLE", "GOOGLE", "訂閱")) return "訂閱";
        return "未分類";
    }

    private static boolean containsAny(String s, String... keys) {
        if (s == null) return false;
        for (String k : keys) {
            if (s.contains(k)) return true;
        }
        return false;
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] b = md.digest(input.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }

    private static class AmountInfo {
        int actualAmount;
        int originalAmount;
        int discountAmount;
        boolean hasDiscountInfo() {
            return originalAmount > 0 || discountAmount > 0;
        }
    }
}
