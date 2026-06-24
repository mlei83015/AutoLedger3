package com.enyu.autoledger;

import java.security.MessageDigest;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FinanceParser {
    private static final Pattern[] AMOUNT_PATTERNS = new Pattern[]{
            Pattern.compile("(?:NT\\$|NTD|TWD|\\$|新臺幣|台幣|臺幣)\\s*([0-9,]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:金額|扣款|付款|支付|刷卡|消費|交易金額|消費金額|合計|共計)[:：]?\\s*(?:NT\\$|NTD|TWD|\\$)?\\s*([0-9,]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("([0-9,]+)\\s*元")
    };

    public static Transaction parse(long timeMillis, String packageName, String appName, String title, String text) {
        String raw = join(appName, title, text).replace('，', ',');
        String normalized = raw.replace("　", " ").trim();
        int amount = extractAmount(normalized);
        if (amount <= 0) return null;

        String direction = detectDirection(normalized, packageName, appName);
        if (direction == null) return null;

        String merchant = detectMerchant(normalized, title, appName);
        String category = detectCategory(normalized, merchant, direction);
        String source = appName == null || appName.isEmpty() ? packageName : appName;
        String hash = sha256(packageName + "|" + title + "|" + text + "|" + amount + "|" + (timeMillis / 60000));
        return new Transaction(timeMillis, amount, direction, source, merchant, category, normalized, hash);
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
}
