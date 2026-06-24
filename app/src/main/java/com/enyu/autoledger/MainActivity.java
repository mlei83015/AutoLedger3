package com.enyu.autoledger;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private LinearLayout root;
    private LinearLayout content;
    private LinearLayout nav;
    private int tab = 0;
    private String manualDirection = "expense";

    private int BG = 0xFFFFFCF7;
    private int CARD = 0xFFFFFFFF;
    private int TEXT = 0xFF23262D;
    private int MUTED = 0xFF727985;
    private int BORDER = 0xFFEFEFF2;
    private int CHIP = 0xFFF7F7F8;
    private final int ORANGE = 0xFFFF7043;
    private final int CORAL = 0xFFFF5A45;
    private final int TEAL = 0xFF16A7B7;
    private final int PURPLE = 0xFF6D5DF6;
    private final int GREEN = 0xFF19A75F;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DailyReportReceiver.createChannels(this);
        DailyReportScheduler.schedule(this);
        requestPostNotificationPermissionIfNeeded();
        buildShell();
        showHome();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshCurrent();
    }

    private void buildShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        applyModeColors();
        root.setBackgroundColor(BG);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

        nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(8), dp(6), dp(8), dp(8));
        nav.setBackgroundColor(AppSettings.getBool(this, AppSettings.KEY_DARK_MODE, false) ? 0xFF111923 : 0xFFFFFFFF);
        root.addView(nav, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
    }

    private void refreshCurrent() {
        if (tab == 0) showHome();
        else if (tab == 1) showManual(manualDirection);
        else if (tab == 2) showStats();
        else showSettings();
    }

    private void setPage(View v) {
        content.removeAllViews();
        content.addView(v, new LinearLayout.LayoutParams(-1, -1));
        rebuildNav();
    }

    private ScrollView pageBase() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(18), dp(16), dp(18));
        scroll.addView(box, new ScrollView.LayoutParams(-1, -2));
        return scroll;
    }

    private LinearLayout pageBox(ScrollView scroll) {
        return (LinearLayout) scroll.getChildAt(0);
    }

    private void applyModeColors() {
        boolean dark = AppSettings.getBool(this, AppSettings.KEY_DARK_MODE, false);
        if (dark) {
            BG = 0xFF0E141B;
            CARD = 0xFF17202A;
            TEXT = 0xFFF5F7FA;
            MUTED = 0xFFAAB3C2;
            BORDER = 0xFF2B3745;
            CHIP = 0xFF202A36;
        } else {
            BG = 0xFFFFFCF7;
            CARD = 0xFFFFFFFF;
            TEXT = 0xFF23262D;
            MUTED = 0xFF727985;
            BORDER = 0xFFEFEFF2;
            CHIP = 0xFFF7F7F8;
        }
        if (root != null) root.setBackgroundColor(BG);
        if (nav != null) nav.setBackgroundColor(dark ? 0xFF111923 : 0xFFFFFFFF);
    }

    private View budgetRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        int budget = AppSettings.getMonthlyBudget(this);
        int spent = TransactionStore.monthExpense(this);
        int remain = Math.max(0, budget - spent);
        texts.addView(text("這個月可以花多少", 16, TEXT, true));
        texts.addView(text("目前預算 " + TransactionStore.money(budget) + "，剩下 " + TransactionStore.money(remain), 13, MUTED, false));
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        Button edit = smallChip("修改", CHIP, ORANGE);
        edit.setOnClickListener(v -> showBudgetDialog());
        row.addView(edit, new LinearLayout.LayoutParams(dp(86), dp(44)));
        return row;
    }

    private void showBudgetDialog() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("例如：10000");
        input.setText(String.valueOf(AppSettings.getMonthlyBudget(this)));
        input.setSelectAllOnFocus(true);
        input.setPadding(dp(16), dp(8), dp(16), dp(8));
        new AlertDialog.Builder(this)
                .setTitle("設定本月可花預算")
                .setMessage("這會影響首頁剩餘餘額和圓形圖百分比。")
                .setView(input)
                .setPositiveButton("儲存", (dialog, which) -> {
                    int amount = 0;
                    try { amount = Integer.parseInt(input.getText().toString().replace(",", "").trim()); } catch (Exception ignored) { }
                    if (amount <= 0) {
                        Toast.makeText(this, "預算要大於 0", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    AppSettings.setMonthlyBudget(this, amount);
                    Toast.makeText(this, "已設定本月預算 " + TransactionStore.money(amount), Toast.LENGTH_SHORT).show();
                    refreshCurrent();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showHome() {
        tab = 0;
        applyModeColors();
        ScrollView scroll = pageBase();
        LinearLayout box = pageBox(scroll);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView menu = text("☰", 25, TEXT, true);
        titleRow.addView(menu, new LinearLayout.LayoutParams(dp(36), -2));
        TextView title = text("自動記帳", 22, TEXT, true);
        title.setGravity(Gravity.CENTER);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView bell = text("🔔", 22, TEXT, false);
        bell.setGravity(Gravity.RIGHT);
        titleRow.addView(bell, new LinearLayout.LayoutParams(dp(40), -2));
        box.addView(titleRow);

        if (!isNotificationListenerEnabled()) {
            LinearLayout perm = card();
            perm.addView(text("狀態：尚未開啟通知讀取", 15, 0xFFC62828, true));
            perm.addView(text("要自動記 LINE Pay / 銀行 / 載具通知，請先開啟通知讀取權限。", 13, MUTED, false));
            Button open = pill("① 開啟通知讀取權限", 0xFFFFF0EA, ORANGE);
            open.setOnClickListener(v -> openNotificationListenerSettings());
            perm.addView(open, marginLp(-1, dp(46), 0, dp(10), 0, 0));
            Button appNotify = pill("② 開啟本 App 通知權限", CHIP, TEXT);
            appNotify.setOnClickListener(v -> openAppNotificationSettings());
            perm.addView(appNotify, marginLp(-1, dp(46), 0, dp(8), 0, 0));
            box.addView(perm, marginLp(-1, -2, 0, dp(12), 0, 0));
        }

        int budgetForMonth = AppSettings.getMonthlyBudget(this);
        int balance = Math.max(0, budgetForMonth - TransactionStore.monthExpense(this));
        LinearLayout balanceCard = new LinearLayout(this);
        balanceCard.setOrientation(LinearLayout.VERTICAL);
        balanceCard.setPadding(dp(18), dp(12), dp(18), dp(12));
        balanceCard.setBackground(roundGradient(ORANGE, 0xFFFF9A2E, dp(16)));
        TextView b1 = text("剩餘餘額  ◉", 14, 0xFFFFFFFF, true);
        TextView b2 = text(TransactionStore.money(balance), 32, 0xFFFFFFFF, true);
        TextView b3 = text("本月預算 " + TransactionStore.money(budgetForMonth) + "，點此修改", 12, 0xFFFFF6EC, false);
        balanceCard.addView(b1);
        balanceCard.addView(b2);
        balanceCard.addView(b3);
        balanceCard.setOnClickListener(v -> showBudgetDialog());
        LinearLayout.LayoutParams balanceLp = new LinearLayout.LayoutParams(-1, -2);
        balanceLp.setMargins(0, dp(14), 0, dp(12));
        box.addView(balanceCard, balanceLp);

        LinearLayout chartCard = card();
        chartCard.setOrientation(LinearLayout.HORIZONTAL);
        chartCard.setGravity(Gravity.CENTER_VERTICAL);
        int monthExpense = TransactionStore.monthExpense(this);
        int monthIncome = TransactionStore.monthIncome(this);
        int budget = Math.max(1, AppSettings.getMonthlyBudget(this));
        int remaining = Math.max(0, budget - monthExpense);
        DonutChartView donut = new DonutChartView(this);
        donut.setData(monthExpense, remaining, monthIncome, AppSettings.getPalette(this));
        chartCard.addView(donut, new LinearLayout.LayoutParams(dp(150), dp(150)));
        LinearLayout legend = new LinearLayout(this);
        legend.setOrientation(LinearLayout.VERTICAL);
        legend.setPadding(dp(8), 0, 0, 0);
        legend.addView(text("本月財務狀況", 15, TEXT, true));
        legend.addView(legendRow("● 本月預算", budget, 0xFFFFA726));
        legend.addView(legendRow("● 剩餘預算", remaining, 0xFF24A99B));
        legend.addView(legendRow("● 已花費", monthExpense, CORAL));
        if (monthIncome > 0) legend.addView(legendRow("● 本月收入", monthIncome, GREEN));
        chartCard.addView(legend, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(chartCard, marginLp(-1, -2, 0, 0, 0, dp(10)));

        int todayExpense = TransactionStore.expenseBetween(this, TransactionStore.startOfDay(0), TransactionStore.startOfDay(1));
        TextView todayLine = text("今天總共花了  " + TransactionStore.money(todayExpense), 18, TEXT, true);
        todayLine.setGravity(Gravity.CENTER);
        todayLine.setTextColor(todayExpense > 0 ? CORAL : MUTED);
        box.addView(todayLine, marginLp(-1, -2, 0, 0, 0, dp(12)));

        LinearLayout quick = new LinearLayout(this);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        Button expense = bigAction("↓\n支出\n記錄花費", TEAL, 0xFF20B4C7);
        expense.setOnClickListener(v -> showManual("expense"));
        Button income = bigAction("↑\n收入\n記錄收入", PURPLE, 0xFF7869FF);
        income.setOnClickListener(v -> showManual("income"));
        quick.addView(expense, new LinearLayout.LayoutParams(0, dp(76), 1));
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(dp(12), 1);
        quick.addView(new View(this), gap);
        quick.addView(income, new LinearLayout.LayoutParams(0, dp(76), 1));
        box.addView(quick, marginLp(-1, -2, 0, 0, 0, dp(12)));

        LinearLayout recordHeader = new LinearLayout(this);
        recordHeader.setGravity(Gravity.CENTER_VERTICAL);
        recordHeader.addView(text("最近記錄", 18, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView clear = text("清除測試資料", 13, MUTED, false);
        clear.setOnClickListener(v -> confirmClear());
        recordHeader.addView(clear);
        box.addView(recordHeader, marginLp(-1, -2, 0, 0, 0, dp(6)));

        LinearLayout list = card();
        list.setPadding(0, dp(4), 0, dp(4));
        List<Transaction> all = TransactionStore.getAll(this);
        if (all.isEmpty()) {
            TextView empty = text("還沒有紀錄。\n可以先按下方＋手動新增，或開啟通知讀取後測試 LINE Pay / 銀行通知。", 15, MUTED, false);
            empty.setPadding(dp(14), dp(18), dp(14), dp(18));
            list.addView(empty);
        } else {
            int count = 0;
            for (Transaction t : all) {
                if (count++ >= 8) break;
                list.addView(transactionRow(t));
            }
        }
        box.addView(list);

        setPage(scroll);
    }

    private TextView legendRow(String name, int amount, int color) {
        TextView t = text(name + "   " + TransactionStore.money(amount), 13, color, false);
        t.setPadding(0, dp(4), 0, dp(2));
        return t;
    }

    private View transactionRow(Transaction tx) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(10), dp(14), dp(10));
        String icon = "income".equals(tx.direction) ? "💰" : iconFor(tx.category);
        TextView ic = text(icon, 22, TEXT, false);
        ic.setGravity(Gravity.CENTER);
        ic.setBackground(round(0xFFF4F6FA, dp(16)));
        row.addView(ic, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setPadding(dp(10), 0, dp(8), 0);
        String main = empty(tx.merchant) ? tx.category : tx.merchant;
        String note = empty(tx.raw) ? tx.category : tx.raw;
        if (note.length() > 24) note = note.substring(0, 24) + "…";
        mid.addView(text(main, 15, TEXT, true));
        mid.addView(text(tx.category + "・" + note, 12, MUTED, false));
        row.addView(mid, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.RIGHT);
        boolean income = "income".equals(tx.direction);
        right.addView(text((income ? "+ " : "- ") + TransactionStore.money(tx.amount), 15, income ? GREEN : TEXT, true));
        right.addView(text(TransactionStore.formatTime(tx.timeMillis), 11, MUTED, false));
        row.addView(right, new LinearLayout.LayoutParams(-2, -2));
        return row;
    }

    private String iconFor(String cat) {
        String c = cat == null ? "" : cat;
        if (c.contains("餐") || c.contains("飲") || c.contains("超商")) return "🍴";
        if (c.contains("交通") || c.contains("捷運") || c.contains("停車")) return "🚌";
        if (c.contains("購") || c.contains("用品")) return "🛍";
        if (c.contains("訂閱") || c.contains("娛樂")) return "▶";
        return "🧾";
    }

    private void showManual(String direction) {
        tab = 1;
        applyModeColors();
        manualDirection = direction == null ? "expense" : direction;
        final boolean startIncome = "income".equals(manualDirection);
        ScrollView scroll = pageBase();
        LinearLayout box = pageBox(scroll);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 32, TEXT, false);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> showHome());
        top.addView(back, new LinearLayout.LayoutParams(dp(40), -2));
        TextView title = text("手動新增", 20, TEXT, true);
        title.setGravity(Gravity.CENTER);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView more = text("⋯", 26, MUTED, true);
        more.setGravity(Gravity.RIGHT);
        top.addView(more, new LinearLayout.LayoutParams(dp(40), -2));
        box.addView(top);

        LinearLayout seg = new LinearLayout(this);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        Button expenseBtn = pill("↓  支出", !startIncome ? TEAL : CHIP, !startIncome ? 0xFFFFFFFF : TEXT);
        Button incomeBtn = pill("↑  收入", startIncome ? PURPLE : CHIP, startIncome ? 0xFFFFFFFF : TEXT);
        expenseBtn.setOnClickListener(v -> showManual("expense"));
        incomeBtn.setOnClickListener(v -> showManual("income"));
        seg.addView(expenseBtn, new LinearLayout.LayoutParams(0, dp(54), 1));
        seg.addView(incomeBtn, new LinearLayout.LayoutParams(0, dp(54), 1));
        box.addView(seg, marginLp(-1, -2, 0, dp(14), 0, dp(14)));

        final EditText amountInput = edit("金額，例如 120", true);
        amountInput.setTextSize(27);
        amountInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        amountInput.setTextColor(TEXT);
        box.addView(label("金額"));
        box.addView(amountInput, marginLp(-1, dp(60), 0, 0, 0, dp(12)));

        final EditText categoryInput = edit(startIncome ? "分類，例如 薪水、零用錢" : "分類，例如 餐飲、交通", false);
        box.addView(label("分類"));
        box.addView(categoryInput, marginLp(-1, dp(54), 0, 0, 0, dp(12)));

        final EditText merchantInput = edit(startIncome ? "來源，例如 打工、家人、朋友" : "店家 / 項目，例如 午餐、全家、捷運", false);
        box.addView(label(startIncome ? "來源" : "店家 / 項目"));
        box.addView(merchantInput, marginLp(-1, dp(54), 0, 0, 0, dp(12)));

        final EditText noteInput = edit("備註 / 用在哪裡，例如：午餐便當、朋友還錢", false);
        noteInput.setMinLines(2);
        noteInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        box.addView(label("備註 / 用在哪裡"));
        box.addView(noteInput, marginLp(-1, dp(76), 0, 0, 0, dp(12)));

        TextView date = text("📅  " + new SimpleDateFormat("yyyy/MM/dd  HH:mm", Locale.TAIWAN).format(new Date()), 15, TEXT, false);
        date.setPadding(dp(14), 0, dp(14), 0);
        date.setBackground(round(CARD, dp(12), BORDER));
        box.addView(label("日期 / 時間"));
        box.addView(date, marginLp(-1, dp(50), 0, 0, 0, dp(16)));

        TextView quickTitle = text("快速常用項目（先輸入金額後點選套用）", 14, MUTED, true);
        box.addView(quickTitle);
        addPresetChips(box, startIncome, amountInput, categoryInput, merchantInput, noteInput);

        TextView recentTitle = text("最近使用（會記得你之前打過的）", 14, MUTED, true);
        recentTitle.setPadding(0, dp(10), 0, dp(6));
        box.addView(recentTitle);
        addRecentChips(box, startIncome, categoryInput, merchantInput);

        Button save = bigSave("✓  確認新增");
        save.setOnClickListener(v -> {
            String amountText = amountInput.getText().toString().trim().replace(",", "");
            int amount;
            try { amount = Integer.parseInt(amountText); } catch (Exception e) { amount = 0; }
            if (amount <= 0) {
                Toast.makeText(this, "請輸入正確金額", Toast.LENGTH_SHORT).show();
                return;
            }
            boolean income = "income".equals(manualDirection);
            String category = categoryInput.getText().toString().trim();
            String merchant = merchantInput.getText().toString().trim();
            String note = noteInput.getText().toString().trim();
            if (category.isEmpty()) category = income ? "收入" : "未分類";
            if (merchant.isEmpty()) merchant = income ? category : category;
            Transaction tx = new Transaction(System.currentTimeMillis(), amount, income ? "income" : "expense", "手動新增", merchant, category, note, "manual-" + System.currentTimeMillis());
            TransactionStore.add(this, tx);
            Toast.makeText(this, "已新增" + (income ? "收入 " : "支出 ") + TransactionStore.money(amount), Toast.LENGTH_SHORT).show();
            showHome();
        });
        box.addView(save, marginLp(-1, dp(58), 0, dp(18), 0, dp(24)));

        setPage(scroll);
    }

    private void addPresetChips(LinearLayout box, boolean income, EditText amount, EditText category, EditText merchant, EditText note) {
        LinearLayout row = wrapRow();
        String[][] presets = income
                ? new String[][]{{"零用錢", "1000", "收入"}, {"薪水", "0", "收入"}, {"紅包", "0", "收入"}, {"退款", "0", "退款"}}
                : new String[][]{{"午餐", "120", "餐飲"}, {"飲料", "65", "餐飲"}, {"交通", "40", "交通"}, {"停車", "60", "交通"}, {"全聯", "0", "購物"}};
        for (String[] p : presets) {
            Button chip = smallChip(p[0] + (p[1].equals("0") ? "" : "\n$" + p[1]), 0xFFFFFFFF, ORANGE);
            chip.setOnClickListener(v -> {
                if (amount.getText().toString().trim().isEmpty() && !p[1].equals("0")) amount.setText(p[1]);
                merchant.setText(p[0]);
                category.setText(p[2]);
                if (note.getText().toString().trim().isEmpty()) note.setText(p[0]);
            });
            row.addView(chip, chipLp());
        }
        box.addView(row);
    }

    private void addRecentChips(LinearLayout box, boolean income, EditText category, EditText merchant) {
        LinearLayout row = wrapRow();
        List<String> recents = TransactionStore.recentChips(this, income ? "income" : "expense", 8);
        if (recents.isEmpty()) {
            String[] defaults = income ? new String[]{"零用錢", "薪水"} : new String[]{"餐飲", "交通", "購物", "娛樂"};
            for (String s : defaults) recents.add(s);
        }
        for (String r : recents) {
            Button chip = smallChip(r, CHIP, TEXT);
            chip.setOnClickListener(v -> {
                merchant.setText(r);
                category.setText(income ? "收入" : guessCategory(r));
            });
            row.addView(chip, chipLp());
        }
        box.addView(row);
    }

    private String guessCategory(String s) {
        if (s.contains("捷運") || s.contains("交通") || s.contains("停車") || s.contains("加油")) return "交通";
        if (s.contains("午餐") || s.contains("飲料") || s.contains("餐") || s.contains("咖啡")) return "餐飲";
        if (s.contains("全聯") || s.contains("購")) return "購物";
        return s;
    }

    private void showStats() {
        tab = 2;
        applyModeColors();
        ScrollView scroll = pageBase();
        LinearLayout box = pageBox(scroll);
        box.addView(centerTitle("統計"));
        int today = TransactionStore.expenseBetween(this, TransactionStore.startOfDay(0), TransactionStore.startOfDay(1));
        int yesterday = TransactionStore.expenseBetween(this, TransactionStore.startOfDay(-1), TransactionStore.startOfDay(0));
        int month = TransactionStore.monthExpense(this);
        LinearLayout c = card();
        c.addView(text("今天支出：" + TransactionStore.money(today), 22, CORAL, true));
        c.addView(text("昨天支出：" + TransactionStore.money(yesterday), 18, TEXT, true));
        c.addView(text("本月支出：" + TransactionStore.money(month), 18, TEXT, true));
        c.addView(text("本月預算：" + TransactionStore.money(AppSettings.getMonthlyBudget(this)), 16, MUTED, false));
        box.addView(c, marginLp(-1, -2, 0, dp(16), 0, dp(12)));
        LinearLayout features = card();
        features.addView(text("智慧摘要", 18, TEXT, true));
        String tip = month > AppSettings.getMonthlyBudget(this) ? "本月已超過預算，建議先檢查餐飲、交通、訂閱支出。" : "目前還在預算內，可以繼續保持。";
        features.addView(text(tip, 15, MUTED, false));
        box.addView(features);
        setPage(scroll);
    }

    private void showSettings() {
        tab = 3;
        applyModeColors();
        ScrollView scroll = pageBase();
        LinearLayout box = pageBox(scroll);
        box.addView(centerTitle("設定"));

        LinearLayout sources = section("通知偵測來源（自動抓取）");
        sources.addView(switchRow("LINE Pay", "擷取付款與交易通知", AppSettings.KEY_LINE_PAY, true));
        sources.addView(switchRow("載具發票", "擷取電子發票與消費資訊", AppSettings.KEY_INVOICE, true));
        sources.addView(switchRow("銀行通知", "擷取帳戶交易、提款、入帳通知", AppSettings.KEY_BANK, true));
        sources.addView(switchRow("簡訊通知", "擷取交易簡訊內容", AppSettings.KEY_SMS, true));
        sources.addView(switchRow("其他 App", "其他付款 App 通知", AppSettings.KEY_OTHER, true));
        box.addView(sources, marginLp(-1, -2, 0, dp(8), 0, dp(10)));

        LinearLayout budgetSec = section("本月預算");
        budgetSec.addView(budgetRow());
        box.addView(budgetSec, marginLp(-1, -2, 0, 0, 0, dp(10)));

        LinearLayout style = section("外觀設定");
        style.addView(switchRow("深色模式", "開啟黑底護眼介面，首頁和設定會變深色", AppSettings.KEY_DARK_MODE, false));
        style.addView(text("主題模式", 15, TEXT, true));
        LinearLayout themes = wrapRow();
        String[] themeNames = new String[]{"清新橙", "珊瑚粉", "薄荷綠", "夢幻紫"};
        for (String th : themeNames) {
            Button b = smallChip((AppSettings.getTheme(this).equals(th) ? "✓ " : "") + th, AppSettings.getTheme(this).equals(th) ? 0xFFFFF0EA : CHIP, ORANGE);
            b.setOnClickListener(v -> { AppSettings.setTheme(this, th); Toast.makeText(this, "已套用 " + th, Toast.LENGTH_SHORT).show(); showSettings(); });
            themes.addView(b, chipLp());
        }
        style.addView(themes);
        style.addView(text("圖表配色", 15, TEXT, true));
        LinearLayout palettes = wrapRow();
        int[] cs = new int[]{0xFFFF5A45, 0xFFFFA726, 0xFF24A99B, 0xFF40A9FF, 0xFF7C6BFF};
        for (int i = 0; i < cs.length; i++) {
            final int idx = i;
            Button dot = new Button(this);
            dot.setText(AppSettings.getPalette(this) == idx ? "✓" : "");
            dot.setTextColor(0xFFFFFFFF);
            dot.setBackground(round(cs[i], dp(20)));
            dot.setOnClickListener(v -> { AppSettings.setPalette(this, idx); showSettings(); });
            palettes.addView(dot, new LinearLayout.LayoutParams(dp(42), dp(42)));
        }
        style.addView(palettes);
        box.addView(style, marginLp(-1, -2, 0, 0, 0, dp(10)));

        LinearLayout account = section("帳號與同步");
        TextView google = text("G  綁定 Google 帳號", 17, TEXT, true);
        google.setPadding(dp(12), dp(12), dp(12), dp(4));
        google.setOnClickListener(v -> {
            AppSettings.setBool(this, AppSettings.KEY_GOOGLE_BOUND, true);
            Toast.makeText(this, "自用版先記錄為已綁定；正式版可再接 Google 登入與雲端同步。", Toast.LENGTH_LONG).show();
            showSettings();
        });
        account.addView(google);
        account.addView(text(AppSettings.getBool(this, AppSettings.KEY_GOOGLE_BOUND, false) ? "已啟用同步備份準備項目" : "點一下先開啟同步備份設定", 13, MUTED, false));
        box.addView(account, marginLp(-1, -2, 0, 0, 0, dp(10)));

        LinearLayout advanced = section("進階設定");
        advanced.addView(switchRow("排除自己通知", "不記錄本 App 自己跳出的通知，避免重複記帳", AppSettings.KEY_EXCLUDE_OWN, true));
        advanced.addView(switchRow("防重複記帳", "同一筆通知短時間內自動合併", AppSettings.KEY_DEDUPE, true));
        advanced.addView(switchRow("自動除錯：LINE Pay / 載具同筆偵測", "LINE Pay 先記、載具較晚進來時，會用金額與日期自動合併", AppSettings.KEY_CROSS_SOURCE_DEDUPE, true));
        advanced.addView(featureRow("智慧分類", "自動判斷餐飲、交通、收入、訂閱等分類"));
        advanced.addView(featureRow("昨日花費摘要", "每天早上自動提醒昨天總共花多少"));
        advanced.addView(featureRow("預算提醒", "超過每月預算時提醒你注意"));
        box.addView(advanced);
        setPage(scroll);
    }

    private View switchRow(String title, String subtitle, String key, boolean def) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(8), dp(8));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(title, 16, TEXT, true));
        texts.addView(text(subtitle, 12, MUTED, false));
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        Switch sw = new Switch(this);
        sw.setChecked(AppSettings.getBool(this, key, def));
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppSettings.setBool(this, key, isChecked);
            if (AppSettings.KEY_DARK_MODE.equals(key)) showSettings();
        });
        row.addView(sw);
        return row;
    }

    private TextView featureRow(String title, String sub) {
        TextView t = text("✦ " + title + "\n   " + sub, 14, TEXT, false);
        t.setPadding(dp(12), dp(8), dp(12), dp(8));
        return t;
    }

    private void rebuildNav() {
        nav.removeAllViews();
        addNav("首頁", "⌂", 0, v -> showHome());
        addNav("記錄", "＋", 1, v -> showManual(manualDirection));
        addNav("統計", "▥", 2, v -> showStats());
        addNav("設定", "⚙", 3, v -> showSettings());
    }

    private void addNav(String label, String icon, int index, View.OnClickListener l) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(0, dp(4), 0, dp(4));
        TextView ic = text(icon, index == 1 ? 26 : 22, tab == index ? ORANGE : 0xFF707783, true);
        ic.setGravity(Gravity.CENTER);
        TextView lab = text(label, 12, tab == index ? ORANGE : 0xFF707783, tab == index);
        lab.setGravity(Gravity.CENTER);
        item.addView(ic);
        item.addView(lab);
        item.setOnClickListener(l);
        nav.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(14), dp(14), dp(14), dp(14));
        l.setBackground(round(CARD, dp(16), BORDER));
        return l;
    }

    private LinearLayout section(String title) {
        LinearLayout l = card();
        TextView t = text(title, 16, TEXT, true);
        t.setPadding(0, 0, 0, dp(6));
        l.addView(t);
        return l;
    }

    private TextView centerTitle(String s) {
        TextView t = text(s, 22, TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, 0, 0, dp(14));
        return t;
    }

    private TextView label(String s) {
        TextView t = text(s, 14, TEXT, true);
        t.setPadding(0, 0, 0, dp(4));
        return t;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private EditText edit(String hint, boolean prefixMoney) {
        EditText e = new EditText(this);
        e.setHint(prefixMoney ? "$  " + hint : hint);
        e.setSingleLine(!hint.contains("備註"));
        e.setPadding(dp(14), 0, dp(14), 0);
        e.setTextColor(TEXT);
        e.setHintTextColor(0xFFB2B6BE);
        e.setBackground(round(CARD, dp(12), BORDER));
        return e;
    }

    private Button bigAction(String text, int c1, int c2) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(0xFFFFFFFF);
        b.setTextSize(16);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setAllCaps(false);
        b.setBackground(roundGradient(c1, c2, dp(14)));
        return b;
    }

    private Button bigSave(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(0xFFFFFFFF);
        b.setTextSize(18);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setAllCaps(false);
        b.setBackground(roundGradient(0xFFFF624F, 0xFFFF7A45, dp(24)));
        return b;
    }

    private Button pill(String text, int bg, int fg) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(16);
        b.setTextColor(fg);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setAllCaps(false);
        b.setBackground(round(bg, dp(14), 0xFFE8E8E8));
        return b;
    }

    private Button smallChip(String text, int bg, int fg) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(fg);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setBackground(round(bg, dp(12), 0xFFE7E7EA));
        b.setMinHeight(dp(44));
        return b;
    }

    private LinearLayout wrapRow() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.LEFT);
        return r;
    }

    private LinearLayout.LayoutParams chipLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(52), 1);
        lp.setMargins(dp(3), dp(4), dp(3), dp(4));
        return lp;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(radius);
        return gd;
    }

    private GradientDrawable round(int color, int radius, int stroke) {
        GradientDrawable gd = round(color, radius);
        gd.setStroke(dp(1), stroke);
        return gd;
    }

    private GradientDrawable roundGradient(int c1, int c2, int radius) {
        GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{c1, c2});
        gd.setCornerRadius(radius);
        return gd;
    }

    private LinearLayout.LayoutParams marginLp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.setMargins(l, t, r, b);
        return lp;
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("清除紀錄")
                .setMessage("確定要清除目前所有記帳資料嗎？")
                .setNegativeButton("取消", null)
                .setPositiveButton("清除", (d, w) -> { TransactionStore.clear(this); showHome(); })
                .show();
    }

    private boolean empty(String s) { return s == null || s.trim().isEmpty(); }

    private boolean isNotificationListenerEnabled() {
        String enabledListeners = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (TextUtils.isEmpty(enabledListeners)) return false;
        ComponentName me = new ComponentName(this, FinanceNotificationListener.class);
        return enabledListeners.toLowerCase().contains(me.flattenToString().toLowerCase());
    }

    private void requestPostNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2001);
        }
    }

    public void openNotificationListenerSettings() {
        startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
    }

    public void openAppNotificationSettings() {
        Intent intent = new Intent();
        if (Build.VERSION.SDK_INT >= 26) {
            intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        } else {
            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
        }
        startActivity(intent);
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
