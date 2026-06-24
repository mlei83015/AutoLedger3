package com.enyu.autoledger;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class QuickAddActivity extends Activity {
    private final int BG = 0xFF0B1219;
    private final int CARD = 0xFF17202A;
    private final int CARD_SOFT = 0xFF1C2835;
    private final int TEXT = 0xFFF5F7FA;
    private final int MUTED = 0xFFAAB3C2;
    private final int BORDER = 0xFF344252;
    private final int ORANGE = 0xFFFF7043;
    private final int RED = 0xFFE85D5D;
    private final int PURPLE = 0xFF7B61FF;
    private final int GREEN = 0xFF28B778;
    private String direction = "expense";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        direction = getIntent().getStringExtra("direction");
        if (direction == null) direction = "expense";
        prepareDialogWindow();
        build();
    }

    private void prepareDialogWindow() {
        try {
            Window w = getWindow();
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setDimAmount(0.56f);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setStatusBarColor(Color.TRANSPARENT);
            w.setNavigationBarColor(Color.TRANSPARENT);
        } catch (Exception ignored) { }
    }

    private void build() {
        boolean income = "income".equals(direction);
        int accent = income ? PURPLE : RED;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(16));
        root.setBackground(round(BG, dp(28), 0xFF273443));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = t(income ? "快速新增收入" : "快速新增支出", 22, TEXT, true);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView close = t("✕", 24, TEXT, true);
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> finish());
        top.addView(close, new LinearLayout.LayoutParams(dp(42), dp(42)));
        root.addView(top, lp(-1, -2, 0, 0, 0, dp(8)));

        EditText amount = input("金額，例如 120", true, accent, "＄");
        amount.setTextSize(23);
        root.addView(label("金額"));
        root.addView(amount, lp(-1, dp(54), 0, dp(4), 0, dp(10)));

        EditText category = input(income ? "分類，例如 薪水、零用錢" : "分類，例如 餐飲、交通", false, accent, "🏷");
        root.addView(label("分類"));
        root.addView(category, lp(-1, dp(50), 0, dp(4), 0, dp(10)));

        root.addView(label("快速常用項目"));
        String[] presets = income
                ? new String[]{"零用錢", "薪水", "打工", "退款", "紅包", "獎金"}
                : new String[]{"餐飲", "交通", "飲料", "停車", "全聯", "早餐", "午餐", "晚餐"};
        root.addView(presetStrip(presets, category, accent), lp(-1, dp(42), 0, dp(4), 0, dp(12)));

        Button save = button("✓ 儲存", ORANGE, Color.WHITE, 18, dp(24));
        save.setOnClickListener(v -> {
            int value = 0;
            try { value = Integer.parseInt(amount.getText().toString().replace(",", "").trim()); } catch (Exception ignored) { }
            if (value <= 0) {
                Toast.makeText(this, "請輸入金額", Toast.LENGTH_SHORT).show();
                return;
            }
            String cat = category.getText().toString().trim();
            if (cat.isEmpty()) cat = income ? "收入" : "未分類";
            Transaction tx = new Transaction(
                    System.currentTimeMillis(),
                    value,
                    income ? "income" : "expense",
                    "桌面小工具",
                    cat,
                    cat,
                    "",
                    "widget-manual-" + System.currentTimeMillis()
            );
            boolean ok = TransactionStore.add(this, tx);
            Toast.makeText(this, ok ? "已新增 " + TransactionStore.money(value) : "疑似重複，已略過", Toast.LENGTH_SHORT).show();
            finish();
        });
        root.addView(save, lp(-1, dp(52), 0, 0, 0, 0));

        setContentView(root);
        try {
            Window w = getWindow();
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            w.setLayout((int) (screenWidth * 0.84f), WindowManager.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.CENTER);
        } catch (Exception ignored) { }
    }

    private HorizontalScrollView presetStrip(String[] presets, EditText category, int accent) {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setOverScrollMode(android.view.View.OVER_SCROLL_NEVER);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (String name : presets) {
            Button b = button(name, CARD_SOFT, accent, 12, dp(18));
            b.setSingleLine(true);
            b.setOnClickListener(v -> category.setText(name));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(82), dp(38));
            lp.setMargins(0, 0, dp(8), 0);
            row.addView(b, lp);
        }
        hsv.addView(row, new HorizontalScrollView.LayoutParams(-2, dp(40)));
        return hsv;
    }

    private LinearLayout presetGrid(String[] presets, EditText category, int accent) {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < presets.length; i += 3) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            for (int j = 0; j < 3; j++) {
                if (i + j >= presets.length) {
                    TextView spacer = new TextView(this);
                    row.addView(spacer, weighted(0, dp(42), 1, dp(4), 0, dp(4), 0));
                    continue;
                }
                String name = presets[i + j];
                Button b = button(name, CARD_SOFT, accent, 13, dp(15));
                b.setSingleLine(true);
                b.setOnClickListener(v -> category.setText(name));
                row.addView(b, weighted(0, dp(42), 1, dp(4), 0, dp(4), 0));
            }
            grid.addView(row, lp(-1, -2, 0, dp(2), 0, dp(6)));
        }
        return grid;
    }

    private TextView label(String s) {
        TextView v = t(s, 14, TEXT, true);
        v.setPadding(dp(2), dp(3), 0, dp(2));
        return v;
    }

    private TextView t(String s, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    private EditText input(String hint, boolean number, int accent, String prefix) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(MUTED);
        e.setTextColor(TEXT);
        e.setSingleLine(true);
        e.setInputType(number ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT);
        e.setPadding(dp(18), 0, dp(16), 0);
        e.setBackground(round(CARD, dp(16), withAlpha(accent, 0xAA)));
        return e;
    }

    private Button button(String text, int bg, int fg, int sp, int radius) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(fg);
        b.setTextSize(sp);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setPadding(dp(4), 0, dp(4), 0);
        b.setBackground(round(bg, radius, BORDER));
        return b;
    }

    private GradientDrawable round(int color, int radius, int stroke) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(radius);
        if (stroke != 0) gd.setStroke(dp(1), stroke);
        return gd;
    }

    private int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(l, t, r, b);
        return p;
    }

    private LinearLayout.LayoutParams weighted(int w, int h, float weight, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h, weight);
        p.setMargins(l, t, r, b);
        return p;
    }

    private int dp(int v) { return Math.round(getResources().getDisplayMetrics().density * v); }
}
