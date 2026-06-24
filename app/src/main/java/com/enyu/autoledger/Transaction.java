package com.enyu.autoledger;

import org.json.JSONException;
import org.json.JSONObject;

public class Transaction {
    public long timeMillis;
    public int amount;
    public String direction; // expense / income
    public String source;
    public String merchant;
    public String category;
    public String raw;
    public String hash;

    public Transaction(long timeMillis, int amount, String direction, String source, String merchant, String category, String raw, String hash) {
        this.timeMillis = timeMillis;
        this.amount = amount;
        this.direction = direction;
        this.source = source == null ? "" : source;
        this.merchant = merchant == null ? "" : merchant;
        this.category = category == null ? "未分類" : category;
        this.raw = raw == null ? "" : raw;
        this.hash = hash == null ? "" : hash;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("timeMillis", timeMillis);
        o.put("amount", amount);
        o.put("direction", direction);
        o.put("source", source);
        o.put("merchant", merchant);
        o.put("category", category);
        o.put("raw", raw);
        o.put("hash", hash);
        return o;
    }

    public static Transaction fromJson(JSONObject o) {
        return new Transaction(
                o.optLong("timeMillis"),
                o.optInt("amount"),
                o.optString("direction", "expense"),
                o.optString("source", ""),
                o.optString("merchant", ""),
                o.optString("category", "未分類"),
                o.optString("raw", ""),
                o.optString("hash", "")
        );
    }
}
