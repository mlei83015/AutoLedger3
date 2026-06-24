package com.enyu.autoledger;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class BarcodeUtil {
    private static final Map<Character, String> CODE39 = new HashMap<>();
    static {
        CODE39.put('0', "nnnwwnwnn"); CODE39.put('1', "wnnwnnnnw"); CODE39.put('2', "nnwwnnnnw"); CODE39.put('3', "wnwwnnnnn"); CODE39.put('4', "nnnwwnnnw");
        CODE39.put('5', "wnnwwnnnn"); CODE39.put('6', "nnwwwnnnn"); CODE39.put('7', "nnnwnnwnw"); CODE39.put('8', "wnnwnnwnn"); CODE39.put('9', "nnwwnnwnn");
        CODE39.put('A', "wnnnnwnnw"); CODE39.put('B', "nnwnnwnnw"); CODE39.put('C', "wnwnnwnnn"); CODE39.put('D', "nnnnwwnnw"); CODE39.put('E', "wnnnwwnnn");
        CODE39.put('F', "nnwnwwnnn"); CODE39.put('G', "nnnnnwwnw"); CODE39.put('H', "wnnnnwwnn"); CODE39.put('I', "nnwnnwwnn"); CODE39.put('J', "nnnnwwwnn");
        CODE39.put('K', "wnnnnnnww"); CODE39.put('L', "nnwnnnnww"); CODE39.put('M', "wnwnnnnwn"); CODE39.put('N', "nnnnwnnww"); CODE39.put('O', "wnnnwnnwn");
        CODE39.put('P', "nnwnwnnwn"); CODE39.put('Q', "nnnnnnwww"); CODE39.put('R', "wnnnnnwwn"); CODE39.put('S', "nnwnnnwwn"); CODE39.put('T', "nnnnwnwwn");
        CODE39.put('U', "wwnnnnnnw"); CODE39.put('V', "nwwnnnnnw"); CODE39.put('W', "wwwnnnnnn"); CODE39.put('X', "nwnnwnnnw"); CODE39.put('Y', "wwnnwnnnn");
        CODE39.put('Z', "nwwnwnnnn"); CODE39.put('-', "nwnnnnwnw"); CODE39.put('.', "wwnnnnwnn"); CODE39.put(' ', "nwwnnnwnn"); CODE39.put('$', "nwnwnwnnn");
        CODE39.put('/', "nwnwnnnwn"); CODE39.put('+', "nwnnnwnwn"); CODE39.put('%', "nnnwnwnwn"); CODE39.put('*', "nwnnwnwnn");
    }

    public static String normalizeCarrier(String raw) {
        if (raw == null) return "";
        String s = raw.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (CODE39.containsKey(c) && c != '*') out.append(c);
        }
        return out.toString();
    }

    public static Bitmap code39(String raw, int width, int height) {
        return code39Internal(raw, width, height, true);
    }

    public static Bitmap code39BarsOnly(String raw, int width, int height) {
        return code39Internal(raw, width, height, false);
    }

    private static Bitmap code39Internal(String raw, int width, int height, boolean showText) {
        String data = normalizeCarrier(raw);
        if (data.isEmpty()) data = "/ABC123";
        String encoded = "*" + data + "*";
        int narrow = Math.max(1, width / Math.max(120, encoded.length() * 18));
        int wide = narrow * 3;
        int quiet = Math.max(8, narrow * 8);
        int total = quiet * 2;
        for (int i = 0; i < encoded.length(); i++) {
            String pattern = CODE39.get(encoded.charAt(i));
            if (pattern == null) continue;
            for (int j = 0; j < pattern.length(); j++) total += (pattern.charAt(j) == 'w') ? wide : narrow;
            total += narrow;
        }
        float scale = (float) width / Math.max(1, total);
        Bitmap bm = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        c.drawColor(Color.WHITE);
        p.setColor(Color.BLACK);
        float x = quiet * scale;
        float top = Math.max(2, height * 0.08f);
        float bottom = showText ? height * 0.78f : height * 0.92f;
        for (int i = 0; i < encoded.length(); i++) {
            String pattern = CODE39.get(encoded.charAt(i));
            if (pattern == null) continue;
            for (int j = 0; j < pattern.length(); j++) {
                float w = ((pattern.charAt(j) == 'w') ? wide : narrow) * scale;
                if (j % 2 == 0) c.drawRect(x, top, x + w, bottom, p);
                x += w;
            }
            x += narrow * scale;
        }
        if (showText) {
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(Math.max(12, height * 0.16f));
            p.setColor(Color.BLACK);
            c.drawText(data, width / 2f, height * 0.95f, p);
        }
        return bm;
    }
}
