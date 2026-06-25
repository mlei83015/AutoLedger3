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
    private static final String PREF = "auto_ledger_exchange_v3";
    private static final String KEY_RATES_JSON = "rates_json";
    private static final String KEY_UPDATED_DAY = "updated_day";
    private static final String KEY_UPDATED_TEXT = "updated_text";
    private static final String KEY_UPDATED_AT = "updated_at";
    private static final String SOURCE = "open.er-api.com";

    public interface UpdateCallback {
        void onDone(boolean updated);
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static String todayKey() {
        return new SimpleDateFormat("yyyyMMdd", Locale.TAIWAN).format(new Date());
    }

    private static String nowText() {
        return new SimpleDateFormat("MM/dd HH:mm", Locale.TAIWAN).format(new Date());
    }

    private static boolean hasCachedRates(Context c) {
        String json = sp(c).getString(KEY_RATES_JSON, "");
        return json != null && !json.trim().isEmpty();
    }

    public static String updatedText(Context c) {
        String t = sp(c).getString(KEY_UPDATED_TEXT, "");
        if (t != null && !t.isEmpty()) return t;
        if (hasCachedRates(c)) {
            String at = sp(c).getString(KEY_UPDATED_AT, "");
            return "使用快取匯率" + (at == null || at.isEmpty() ? "" : "｜最後更新 " + at) + "｜來源：" + SOURCE;
        }
        return "使用內建參考匯率｜來源：App 內建";
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
        r.put("SGD", 0.0405);
        r.put("THB", 1.13);
        r.put("VND", 815.0);
        r.put("PHP", 1.78);
        r.put("IDR", 505.0);
        r.put("AUD", 0.047);
        r.put("CAD", 0.042);
        r.put("NZD", 0.052);
        r.put("CHF", 0.025);
        r.put("SEK", 0.292);
        r.put("NOK", 0.315);
        r.put("DKK", 0.200);
        r.put("INR", 2.66);
        r.put("AED", 0.114);
        r.put("SAR", 0.116);
        r.put("MOP", 0.249);
        r.put("TRY", 1.02);
        r.put("BRL", 0.170);
        r.put("MXN", 0.575);
        r.put("ZAR", 0.555);
        r.put("PLN", 0.115);
        r.put("CZK", 0.655);
        r.put("HUF", 9.75);
        r.put("ISK", 4.25);
        r.put("ILS", 0.112);
        r.put("QAR", 0.113);
        r.put("KWD", 0.0095);
        r.put("BHD", 0.0117);
        r.put("OMR", 0.0119);
        r.put("EGP", 1.55);
        r.put("ARS", 37.0);
        r.put("CLP", 29.0);
        r.put("COP", 130.0);
        r.put("PEN", 0.115);
        r.put("RUB", 2.45);
        r.put("JOD", 0.022);
        return r;
    }

    public static String[] commonCodes() {
        return new String[]{"TWD", "USD", "JPY", "KRW", "CNY", "MYR", "GBP", "EUR", "HKD"};
    }

    public static String[] allCodes() {
        return new String[]{
                "TWD", "USD", "JPY", "KRW", "CNY", "MYR", "GBP", "EUR", "HKD",
                "SGD", "THB", "VND", "PHP", "IDR", "AUD", "CAD", "NZD", "CHF",
                "SEK", "NOK", "DKK", "INR", "AED", "SAR", "MOP", "TRY", "BRL",
                "MXN", "ZAR", "PLN", "CZK", "HUF", "ISK", "ILS", "QAR", "KWD",
                "BHD", "OMR", "EGP", "ARS", "CLP", "COP", "PEN", "RUB", "JOD"
        };
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
        if ("SGD".equals(code)) return "新加坡幣";
        if ("THB".equals(code)) return "泰銖";
        if ("VND".equals(code)) return "越南盾";
        if ("PHP".equals(code)) return "菲律賓披索";
        if ("IDR".equals(code)) return "印尼盾";
        if ("AUD".equals(code)) return "澳幣";
        if ("CAD".equals(code)) return "加幣";
        if ("NZD".equals(code)) return "紐幣";
        if ("CHF".equals(code)) return "瑞士法郎";
        if ("SEK".equals(code)) return "瑞典克朗";
        if ("NOK".equals(code)) return "挪威克朗";
        if ("DKK".equals(code)) return "丹麥克朗";
        if ("INR".equals(code)) return "印度盧比";
        if ("AED".equals(code)) return "阿聯酋迪拉姆";
        if ("SAR".equals(code)) return "沙烏地里亞爾";
        if ("MOP".equals(code)) return "澳門幣";
        if ("TRY".equals(code)) return "土耳其里拉";
        if ("BRL".equals(code)) return "巴西雷亞爾";
        if ("MXN".equals(code)) return "墨西哥披索";
        if ("ZAR".equals(code)) return "南非蘭特";
        if ("PLN".equals(code)) return "波蘭茲羅提";
        if ("CZK".equals(code)) return "捷克克朗";
        if ("HUF".equals(code)) return "匈牙利福林";
        if ("ISK".equals(code)) return "冰島克朗";
        if ("ILS".equals(code)) return "以色列謝克爾";
        if ("QAR".equals(code)) return "卡達里亞爾";
        if ("KWD".equals(code)) return "科威特第納爾";
        if ("BHD".equals(code)) return "巴林第納爾";
        if ("OMR".equals(code)) return "阿曼里亞爾";
        if ("EGP".equals(code)) return "埃及鎊";
        if ("ARS".equals(code)) return "阿根廷披索";
        if ("CLP".equals(code)) return "智利披索";
        if ("COP".equals(code)) return "哥倫比亞披索";
        if ("PEN".equals(code)) return "秘魯索爾";
        if ("RUB".equals(code)) return "俄羅斯盧布";
        if ("JOD".equals(code)) return "約旦第納爾";
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
        return code + " ";
    }

    public static LinkedHashMap<String, Double> rates(Context c) {
        LinkedHashMap<String, Double> out = defaultRates();
        String json = sp(c).getString(KEY_RATES_JSON, "");
        if (json == null || json.isEmpty()) return out;
        try {
            JSONObject root = new JSONObject(json);
            for (String code : allCodes()) {
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
        updateRates(c, false, callback);
    }

    public static void updateNow(Context c, UpdateCallback callback) {
        updateRates(c, true, callback);
    }

    private static void updateRates(Context c, boolean force, UpdateCallback callback) {
        Context app = c.getApplicationContext();
        String today = todayKey();
        if (!force && today.equals(sp(app).getString(KEY_UPDATED_DAY, "")) && hasCachedRates(app)) {
            if (callback != null) callback.onDone(false);
            return;
        }
        new Thread(() -> {
            boolean ok = false;
            try {
                URL url = new URL("https://open.er-api.com/v6/latest/TWD");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestMethod("GET");
                Scanner scanner = new Scanner(conn.getInputStream(), "UTF-8").useDelimiter("\\A");
                String body = scanner.hasNext() ? scanner.next() : "";
                scanner.close();
                JSONObject root = new JSONObject(body);
                String result = root.optString("result", "success");
                if (!"success".equalsIgnoreCase(result)) throw new RuntimeException("rate api failed");
                JSONObject rates = root.getJSONObject("rates");
                JSONObject keep = new JSONObject();
                for (String code : allCodes()) {
                    if (rates.has(code)) keep.put(code, rates.getDouble(code));
                }
                keep.put("TWD", 1.0);
                String at = nowText();
                sp(app).edit()
                        .putString(KEY_RATES_JSON, keep.toString())
                        .putString(KEY_UPDATED_DAY, today)
                        .putString(KEY_UPDATED_AT, at)
                        .putString(KEY_UPDATED_TEXT, "線上匯率｜最後更新 " + at + "｜來源：" + SOURCE)
                        .apply();
                ok = true;
            } catch (Exception e) {
                String at = sp(app).getString(KEY_UPDATED_AT, "");
                if (hasCachedRates(app)) {
                    sp(app).edit()
                            .putString(KEY_UPDATED_TEXT, "使用快取匯率" + (at == null || at.isEmpty() ? "" : "｜最後更新 " + at) + "｜來源：" + SOURCE)
                            .apply();
                } else {
                    sp(app).edit()
                            .putString(KEY_UPDATED_TEXT, "使用內建參考匯率｜來源：App 內建")
                            .apply();
                }
            }
            if (callback != null) callback.onDone(ok);
        }).start();
    }
}
