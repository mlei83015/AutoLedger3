package com.enyu.autoledger;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Typeface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.widget.Toast;

public class QuickAddActivity extends Activity {
    private final int BG = 0xFF0E141B;
    private final int CARD = 0xFF17202A;
    private final int TEXT = 0xFFF5F7FA;
    private final int MUTED = 0xFFAAB3C2;
    private final int BORDER = 0xFF2B3745;
    private final int ORANGE = 0xFFFF7043;
    private final int TEAL = 0xFF16A7B7;
    private final int PURPLE = 0xFF6D5DF6;
    private String direction = "expense";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            Window w = getWindow();
            w.setStatusBarColor(BG);
            w.setNavigationBarColor(BG);
        } catch (Exception ignored) {}
        direction = getIntent().getStringExtra("direction");
        if (direction == null) direction = "expense";
        build();
    }

    private void build() {
        boolean income = "income".equals(direction);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(36), dp(18), dp(18));
        root.setBackgroundColor(BG);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = t((income ? "快速新增收入" : "快速新增支出"), 22, TEXT, true);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView close = t("✕", 22, MUTED, true);
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> finish());
        top.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));
        root.addView(top);

        EditText amount = input("金額，例如 120", true);
        amount.setTextSize(28);
        root.addView(label("金額"));
        root.addView(amount, lp(-1, dp(64), 0, dp(16), 0, dp(12)));

        EditText category = input(income ? "分類，例如 薪水、零用錢" : "分類，例如 餐飲、交通", false);
        root.addView(label("分類"));
        root.addView(category, lp(-1, dp(54), 0, 0, 0, dp(12)));

        EditText merchant = input(income ? "來源，例如 打工、家人" : "項目，例如 午餐、全家", false);
        root.addView(label(income ? "來源" : "店家 / 項目"));
        root.addView(merchant, lp(-1, dp(54), 0, 0, 0, dp(12)));

        String[] presets = income ? new String[]{"零用錢|1000|收入", "薪水|0|薪水", "打工|0|打工", "退款|0|退款"} : new String[]{"午餐|120|餐飲", "飲料|65|餐飲", "交通|40|交通", "全聯|0|購物"};
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < presets.length; i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int j = 0; j < 2; j++) {
                if (i+j >= presets.length) break;
                String[] p = presets[i+j].split("\\|");
                String name = p[0];
                String amt = p[1];
                String cat = p[2];
                Button b = button(name + ("0".equals(amt) ? "" : "\n$" + amt), income ? 0xFFF2F0FF : 0xFFFFF3EA, income ? PURPLE : ORANGE);
                b.setOnClickListener(v -> {
                    if (amount.getText().toString().trim().isEmpty() && !"0".equals(amt)) amount.setText(amt);
                    merchant.setText(name);
                    category.setText(cat);
                });
                row.addView(b, new LinearLayout.LayoutParams(0, dp(62), 1));
            }
            grid.addView(row, lp(-1, -2, 0, dp(2), 0, dp(8)));
        }
        root.addView(label("快速常用項目"));
        root.addView(grid);

        Button save = button("✓ 儲存", ORANGE, Color.WHITE);
        save.setTextSize(18);
        save.setOnClickListener(v -> {
            int value = 0;
            try { value = Integer.parseInt(amount.getText().toString().replace(",", "").trim()); } catch (Exception ignored) {}
            if (value <= 0) {
                Toast.makeText(this, "請輸入金額", Toast.LENGTH_SHORT).show();
                return;
            }
            String cat = category.getText().toString().trim();
            String mer = merchant.getText().toString().trim();
            if (cat.isEmpty()) cat = income ? "收入" : "未分類";
            if (mer.isEmpty()) mer = cat;
            Transaction tx = new Transaction(System.currentTimeMillis(), value, income ? "income" : "expense", "桌面小工具", mer, cat, "桌面快速新增", "widget-" + System.currentTimeMillis());
            TransactionStore.add(this, tx);
            Toast.makeText(this, "已新增 " + TransactionStore.money(value), Toast.LENGTH_SHORT).show();
            finish();
        });
        root.addView(save, lp(-1, dp(58), 0, dp(18), 0, 0));
        setContentView(root);
    }

    private TextView label(String s) { TextView v = t(s, 14, TEXT, true); v.setPadding(0, dp(6),0,dp(6)); return v; }
    private TextView t(String s, int sp, int color, boolean bold) { TextView v = new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(color); if (bold) v.setTypeface(Typeface.DEFAULT_BOLD); return v; }
    private EditText input(String hint, boolean number) { EditText e = new EditText(this); e.setHint(hint); e.setHintTextColor(MUTED); e.setTextColor(TEXT); e.setSingleLine(true); e.setInputType(number ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT); e.setPadding(dp(16),0,dp(16),0); e.setBackground(round(CARD, dp(14), BORDER)); return e; }
    private Button button(String text, int bg, int fg) { Button b = new Button(this); b.setText(text); b.setTextColor(fg); b.setAllCaps(false); b.setTypeface(Typeface.DEFAULT_BOLD); b.setBackground(round(bg, dp(18), BORDER)); return b; }
    private GradientDrawable round(int color, int radius, int stroke) { GradientDrawable gd = new GradientDrawable(); gd.setColor(color); gd.setCornerRadius(radius); gd.setStroke(dp(1), stroke); return gd; }
    private LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w,h); p.setMargins(l,t,r,b); return p; }
    private int dp(int v) { return Math.round(getResources().getDisplayMetrics().density * v); }
}
