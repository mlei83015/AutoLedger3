package com.enyu.autoledger;

import org.json.JSONException;
import org.json.JSONObject;

public class Transaction {
    public long timeMillis;
    public int amount;          // 實際會影響餘額 / 預算的金額
    public int originalAmount;  // 通知上的原價 / 原始金額，沒有就 0
    public int discountAmount;  // 點數 / 優惠 / 折抵金額，沒有就 0
    public String direction; // expense / income
    public String source;
    public String merchant;
    public String category;
    public String raw;
    public String hash;
    public String icon;

    public Transaction(long timeMillis, int amount, String direction, String source, String merchant, String category, String raw, String hash) {
        this(timeMillis, amount, direction, source, merchant, category, raw, hash, "");
    }

    public Transaction(long timeMillis, int amount, String direction, String source, String merchant, String category, String raw, String hash, String icon) {
        this(timeMillis, amount, 0, 0, direction, source, merchant, category, raw, hash, icon);
    }

    public Transaction(long timeMillis, int amount, int originalAmount, int discountAmount, String direction, String source, String merchant, String category, String raw, String hash, String icon) {
        this.timeMillis = timeMillis;
        this.amount = amount;
        this.originalAmount = Math.max(0, originalAmount);
        this.discountAmount = Math.max(0, discountAmount);
        this.direction = direction;
        this.source = source == null ? "" : source;
        this.merchant = merchant == null ? "" : merchant;
        this.category = category == null ? "" : category;
        this.raw = raw == null ? "" : raw;
        this.hash = hash == null ? "" : hash;
        this.icon = icon == null ? "" : icon;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("timeMillis", timeMillis);
        o.put("amount", amount);
        o.put("originalAmount", originalAmount);
        o.put("discountAmount", discountAmount);
        o.put("direction", direction);
        o.put("source", source);
        o.put("merchant", merchant);
        o.put("category", category);
        o.put("raw", raw);
        o.put("hash", hash);
        o.put("icon", icon);
        return o;
    }

    public static Transaction fromJson(JSONObject o) {
        return new Transaction(
                o.optLong("timeMillis"),
                o.optInt("amount"),
                o.optInt("originalAmount", 0),
                o.optInt("discountAmount", 0),
                o.optString("direction", "expense"),
                o.optString("source", ""),
                o.optString("merchant", ""),
                o.optString("category", ""),
                o.optString("raw", ""),
                o.optString("hash", ""),
                o.optString("icon", "")
        );
    }
}
