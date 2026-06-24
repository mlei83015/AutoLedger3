package com.enyu.autoledger;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;

public class CurrencyRateStore {
    private static final String PREF = "auto_ledger_exchange_v1";
    private static final String KEY_RATES_JSON = "rates_json";
    private static final String KEY_UPDATED_DAY = "updated_day";
    private static final String KEY_UPDATED_TEXT = "updated_text";

    public interface UpdateCallback {
        void onDone(boolean updated);
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static String todayKey() {
        return new SimpleDateFormat("yyyyMMdd", Locale.TAIWAN).format(new Date());
    }

    public static String updatedText(Context c) {
        String t = sp(c).getString(KEY_UPDATED_TEXT, "");
        return t == null || t.isEmpty() ? "使用內建匯率參考" : t;
    }

    public static LinkedHashMap<String, Double> defaultRates() {
        LinkedHashMap<String, Double> r = new LinkedHashMap<>();
        r.put("TWD", 1.0);
        r.put("USD", 0.0310);
        r.put("JPY", 4.82);
        r.put("KRW", 42.9);
        r.put("CNY", 0.225);
        r.put("MYR", 0.146);
        r.put("GBP", 0.0230);
        r.put("EUR", 0.0268);
        r.put("HKD", 0.243);
        return r;
    }

    public static String[] commonCodes() {
        return new String[]{"TWD", "USD", "JPY", "KRW", "CNY", "MYR", "GBP", "EUR", "HKD"};
    }

    public static String name(String code) {
        if ("TWD".equals(code)) return "台幣";
        if ("USD".equals(code)) return "美金";
        if ("JPY".equals(code)) return "日幣";
        if ("KRW".equals(code)) return "韓幣";
        if ("CNY".equals(code)) return "人民幣";
        if ("MYR".equals(code)) return "馬幣";
        if ("GBP".equals(code)) return "英鎊";
        if ("EUR".equals(code)) return "歐元";
        if ("HKD".equals(code)) return "港幣";
        return code;
    }

    public static String symbol(String code) {
        if ("TWD".equals(code)) return "NT$";
        if ("USD".equals(code)) return "$";
        if ("JPY".equals(code)) return "¥";
        if ("KRW".equals(code)) return "₩";
        if ("CNY".equals(code)) return "¥";
        if ("MYR".equals(code)) return "RM";
        if ("GBP".equals(code)) return "£";
        if ("EUR".equals(code)) return "€";
        if ("HKD".equals(code)) return "HK$";
        return "";
    }

    public static LinkedHashMap<String, Double> rates(Context c) {
        LinkedHashMap<String, Double> out = defaultRates();
        String json = sp(c).getString(KEY_RATES_JSON, "");
        if (json == null || json.isEmpty()) return out;
        try {
            JSONObject root = new JSONObject(json);
            for (String code : commonCodes()) {
                if (root.has(code)) out.put(code, root.getDouble(code));
            }
        } catch (Exception ignored) { }
        return out;
    }

    public static double convert(Context c, double amount, String from, String to) {
        Map<String, Double> r = rates(c);
        double rf = r.containsKey(from) ? r.get(from) : 1.0;
        double rt = r.containsKey(to) ? r.get(to) : 1.0;
        if (rf <= 0) rf = 1.0;
        return amount / rf * rt;
    }

    public static void updateDailyIfNeeded(Context c, UpdateCallback callback) {
        Context app = c.getApplicationContext();
        String today = todayKey();
        if (today.equals(sp(app).getString(KEY_UPDATED_DAY, ""))) {
            if (callback != null) callback.onDone(false);
            return;
        }
        new Thread(() -> {
            boolean ok = false;
            try {
                URL url = new URL("https://open.er-api.com/v6/latest/TWD");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                conn.setRequestMethod("GET");
                InputStreamCompat.mark();
                Scanner scanner = new Scanner(conn.getInputStream(), "UTF-8").useDelimiter("\\A");
                String body = scanner.hasNext() ? scanner.next() : "";
                scanner.close();
                JSONObject root = new JSONObject(body);
                JSONObject rates = root.getJSONObject("rates");
                JSONObject keep = new JSONObject();
                for (String code : commonCodes()) {
                    if (rates.has(code)) keep.put(code, rates.getDouble(code));
                }
                keep.put("TWD", 1.0);
                sp(app).edit()
                        .putString(KEY_RATES_JSON, keep.toString())
                        .putString(KEY_UPDATED_DAY, today)
                        .putString(KEY_UPDATED_TEXT, "今日已更新匯率")
                        .apply();
                ok = true;
            } catch (Exception e) {
                sp(app).edit()
                        .putString(KEY_UPDATED_DAY, today)
                        .putString(KEY_UPDATED_TEXT, "使用內建匯率參考")
                        .apply();
            }
            if (callback != null) callback.onDone(ok);
        }).start();
    }

    // 避免部分舊編譯器移除 java.net 匯入的空類別。
    private static class InputStreamCompat { static void mark() {} }
}
