package com.enyu.autoledger;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ComponentName;
import android.content.ClipData;
import android.content.ClipboardManager;
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
import android.view.Window;
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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

public class MainActivity extends Activity {
    public static final String ACTION_QUICK_EXPENSE = "com.enyu.autoledger.action.QUICK_EXPENSE";
    public static final String ACTION_QUICK_INCOME = "com.enyu.autoledger.action.QUICK_INCOME";

    private LinearLayout root;
    private LinearLayout content;
    private LinearLayout nav;
    private int tab = 0;
    private String manualDirection = "expense";
    private String reportType = "expense";
    private String reportRange = "month";

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
    private final int EXPENSE_RED = 0xFFFF4F5E;
    private final int SOFT_BLUE = 0xFF66C7E8;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DailyReportReceiver.createChannels(this);
        DailyReportScheduler.schedule(this);
        requestPostNotificationPermissionIfNeeded();
        buildShell();
        showHome();
        handleLaunchIntent(getIntent());
        if (!AppSettings.getBool(this, AppSettings.KEY_ONBOARDED, false)) {
            root.postDelayed(() -> showOnboarding(), 500);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleLaunchIntent(intent);
    }

    private void handleLaunchIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (ACTION_QUICK_EXPENSE.equals(action)) {
            root.postDelayed(() -> showManual("expense"), 120);
        } else if (ACTION_QUICK_INCOME.equals(action)) {
            root.postDelayed(() -> showManual("income"), 120);
        }
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
        applySystemBars();
        root.setPadding(0, safeTopPadding(), 0, 0);

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
        box.setPadding(dp(16), dp(12), dp(16), dp(18));
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
        if (root != null) {
            root.setBackgroundColor(BG);
            root.setPadding(0, safeTopPadding(), 0, 0);
        }
        if (nav != null) nav.setBackgroundColor(dark ? 0xFF111923 : 0xFFFFFFFF);
        applySystemBars();
    }

    private void applySystemBars() {
        try {
            Window w = getWindow();
            w.setStatusBarColor(BG);
            w.setNavigationBarColor(AppSettings.getBool(this, AppSettings.KEY_DARK_MODE, false) ? 0xFF111923 : 0xFFFFFFFF);
            if (Build.VERSION.SDK_INT >= 23) {
                int flags = w.getDecorView().getSystemUiVisibility();
                if (AppSettings.getBool(this, AppSettings.KEY_DARK_MODE, false)) {
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                } else {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                }
                w.getDecorView().setSystemUiVisibility(flags);
            }
        } catch (Exception ignored) { }
    }

    private int safeTopPadding() {
        int result = dp(12);
        int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resId > 0) result += getResources().getDimensionPixelSize(resId);
        else result += dp(28);
        return result;
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
        texts.addView(text("本月可花預算", 16, TEXT, true));
        texts.addView(text("圓形圖只看支出：預算 " + TransactionStore.money(budget) + "，還能花 " + TransactionStore.money(remain), 13, MUTED, false));
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
                .setMessage("這只會影響圓形圖的本月可花額度；收入會加到最上面的全部餘額，不會把圓形圖預算變大。")
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
        menu.setGravity(Gravity.CENTER);
        menu.setOnClickListener(v -> showSideMenu());
        titleRow.addView(menu, new LinearLayout.LayoutParams(dp(36), -2));
        TextView title = text("自動記帳", 22, TEXT, true);
        title.setGravity(Gravity.CENTER);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView bell = text("🔔", 22, TEXT, false);
        bell.setGravity(Gravity.RIGHT);
        bell.setOnClickListener(v -> showNotificationSettingsDialog());
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
        int balance = TransactionStore.totalBalance(this);
        LinearLayout balanceCard = new LinearLayout(this);
        balanceCard.setOrientation(LinearLayout.VERTICAL);
        balanceCard.setPadding(dp(18), dp(12), dp(18), dp(12));
        balanceCard.setBackground(roundGradient(ORANGE, 0xFFFF9A2E, dp(16)));
        TextView b1 = text("剩餘餘額  ◉", 14, 0xFFFFFFFF, true);
        TextView b2 = text(TransactionStore.money(balance), 32, 0xFFFFFFFF, true);
        TextView b3 = text("全部餘額＝預算＋收入－支出｜本月預算 " + TransactionStore.money(budgetForMonth) + "，點此修改", 12, 0xFFFFF6EC, false);
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
        donut.setDarkMode(AppSettings.getBool(this, AppSettings.KEY_DARK_MODE, false));
        donut.setData(monthExpense, remaining, 0, AppSettings.getPalette(this));
        chartCard.addView(donut, new LinearLayout.LayoutParams(dp(150), dp(150)));
        LinearLayout legend = new LinearLayout(this);
        legend.setOrientation(LinearLayout.VERTICAL);
        legend.setPadding(dp(8), 0, 0, 0);

        TextView remainingTitle = text("剩餘預算", 13, 0xFF24A99B, true);
        TextView remainingValue = text(TransactionStore.money(remaining), 25, 0xFF24A99B, true);
        legend.addView(remainingTitle);
        legend.addView(remainingValue, marginLp(-1, -2, 0, dp(1), 0, dp(8)));

        legend.addView(text("本月財務狀況", 15, TEXT, true));
        legend.addView(legendRow("● 本月預算", budget, 0xFFFFA726));
        legend.addView(legendRow("● 已花費", monthExpense, CORAL));
        chartCard.addView(legend, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(chartCard, marginLp(-1, -2, 0, 0, 0, dp(10)));

        int todayExpense = TransactionStore.expenseBetween(this, TransactionStore.startOfDay(0), TransactionStore.startOfDay(1));
        TextView todayLine = text("今天總共花了  " + TransactionStore.money(todayExpense), 18, TEXT, true);
        todayLine.setGravity(Gravity.CENTER);
        todayLine.setTextColor(todayExpense > 0 ? EXPENSE_RED : MUTED);
        box.addView(todayLine, marginLp(-1, -2, 0, 0, 0, dp(10)));

        LinearLayout studentTip = card();
        studentTip.setPadding(dp(14), dp(10), dp(14), dp(10));
        studentTip.setBackground(round(AppSettings.getBool(this, AppSettings.KEY_DARK_MODE, false) ? 0xFF1B2735 : 0xFFFFF4E8, dp(16), BORDER));
        int forecastHome = TransactionStore.forecastMonthExpense(this);
        int savingHome = TransactionStore.suggestedSaving(this);
        studentTip.addView(text("✨ 本月小提醒", 15, TEXT, true));
        String tipHome = forecastHome > budgetForMonth ? "照現在速度月底可能超過預算，今天可以先少喝一杯飲料。" : (savingHome > 0 ? "照現在速度月底可能有剩，可以先存 " + TransactionStore.money(savingHome) + "。" : "目前花費接近預算，先維持節奏。") ;
        studentTip.addView(text(tipHome, 13, MUTED, false));
        box.addView(studentTip, marginLp(-1, -2, 0, 0, 0, dp(10)));

        LinearLayout monthCountCard = new LinearLayout(this);
        monthCountCard.setOrientation(LinearLayout.HORIZONTAL);
        monthCountCard.setGravity(Gravity.CENTER_VERTICAL);
        monthCountCard.setPadding(dp(14), dp(10), dp(14), dp(10));
        monthCountCard.setBackground(round(AppSettings.getBool(this, AppSettings.KEY_DARK_MODE, false) ? 0xFF151F2A : 0xFFFFFFFF, dp(16), BORDER));
        int monthTotalCount = TransactionStore.countBetween(this, TransactionStore.startOfMonth(0), TransactionStore.startOfMonth(1));
        int monthAutoCount = TransactionStore.autoCountBetween(this, TransactionStore.startOfMonth(0), TransactionStore.startOfMonth(1));
        int monthManualCount = TransactionStore.manualCountBetween(this, TransactionStore.startOfMonth(0), TransactionStore.startOfMonth(1));
        monthCountCard.addView(text("本月記錄 " + monthTotalCount + " 筆", 14, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        monthCountCard.addView(text("自動 " + monthAutoCount + "｜手動 " + monthManualCount, 13, MUTED, false));
        box.addView(monthCountCard, marginLp(-1, -2, 0, 0, 0, dp(12)));

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
        TextView clear = text("清除資料", 13, MUTED, false);
        clear.setOnClickListener(v -> confirmClear());
        recordHeader.addView(clear);
        TextView hintEdit = text("  點一下看詳情｜長按修改", 12, MUTED, false);
        recordHeader.addView(hintEdit);
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
        row.setPadding(dp(14), dp(11), dp(14), dp(11));
        row.setMinimumHeight(dp(72));

        boolean income = "income".equals(tx.direction);
        int iconBg = income ? 0xFFEAF8F0 : (isInvoiceRecord(tx) ? 0xFFEAF8FF : 0xFFFFF0F1);
        TextView ic = text(income ? "💰" : iconFor(tx.category), 22, TEXT, false);
        ic.setGravity(Gravity.CENTER);
        ic.setBackground(round(iconBg, dp(16)));
        row.addView(ic, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setPadding(dp(12), 0, dp(8), 0);
        TextView title = text(recordTitle(tx), 16, TEXT, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        TextView sub = text(recordSubtitle(tx), 12, MUTED, false);
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        mid.addView(title);
        mid.addView(sub, marginLp(-1, -2, 0, dp(3), 0, 0));
        row.addView(mid, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        TextView amt = text((income ? "+ " : "- ") + TransactionStore.money(tx.amount), 17, income ? GREEN : EXPENSE_RED, true);
        amt.setGravity(Gravity.RIGHT);
        TextView tm = text(TransactionStore.formatTime(tx.timeMillis), 12, MUTED, false);
        tm.setGravity(Gravity.RIGHT);
        right.addView(amt);
        right.addView(tm, marginLp(-1, -2, 0, dp(2), 0, 0));
        row.addView(right, new LinearLayout.LayoutParams(dp(104), -2));

        row.setOnClickListener(v -> showTransactionDetail(tx));
        row.setOnLongClickListener(v -> { showEditTransactionDialog(tx); return true; });
        return row;
    }

    private String recordTitle(Transaction tx) {
        if (tx == null) return "記帳紀錄";
        String source = compactForUi(tx.source + " " + tx.merchant + " " + tx.raw);
        String merchant = tx.merchant == null ? "" : tx.merchant.trim();
        if (source.contains("發票") || source.contains("載具")) return "發票載具";
        if (source.toLowerCase(Locale.ROOT).contains("line") || source.contains("LINE錢包")) return "LINE錢包";
        if (source.toLowerCase(Locale.ROOT).contains("google") || source.contains("Google錢包")) return "Google 錢包";
        if (source.contains("銀行") || source.contains("信用卡") || source.contains("刷卡")) return "銀行刷卡";
        if (!merchant.isEmpty()) return trimUi(merchant, 12);
        if (tx.category != null && !tx.category.trim().isEmpty()) return trimUi(tx.category, 12);
        return "記帳紀錄";
    }

    private String recordSubtitle(Transaction tx) {
        if (tx == null) return "";
        String type = (tx.category == null || tx.category.trim().isEmpty()) ? "未分類" : tx.category.trim();
        String source = sourceDisplay(tx);
        String note = compactForUi(tx.raw);
        if (note.length() > 0) {
            note = note.replace(recordTitle(tx), "").replace(source, "").trim();
        }
        note = trimUi(note, 18);
        if (note.isEmpty()) return type + "・" + source;
        return type + "・" + source + "・" + note;
    }

    private String sourceDisplay(Transaction tx) {
        String all = compactForUi(tx.source + " " + tx.merchant + " " + tx.raw).toLowerCase(Locale.ROOT);
        if (all.contains("發票") || all.contains("載具") || all.contains("invoice")) return "載具自動記帳";
        if (all.contains("line") && (all.contains("pay") || all.contains("錢包"))) return "LINE Pay";
        if (all.contains("google") || all.contains("wallet")) return "Google 錢包";
        if (all.contains("銀行") || all.contains("刷卡") || all.contains("信用卡")) return "銀行通知";
        if (tx.hash != null && tx.hash.startsWith("manual-")) return "手動新增";
        if (tx.source != null && !tx.source.trim().isEmpty()) return trimUi(tx.source.trim(), 10);
        return "自動記帳";
    }

    private boolean isInvoiceRecord(Transaction tx) {
        String s = compactForUi(tx == null ? "" : (tx.source + " " + tx.merchant + " " + tx.raw));
        return s.contains("發票") || s.contains("載具");
    }

    private String compactForUi(String s) {
        if (s == null) return "";
        return s.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
    }

    private String trimUi(String s, int max) {
        if (s == null) return "";
        String x = compactForUi(s);
        if (x.length() <= max) return x;
        return x.substring(0, Math.max(0, max)) + "…";
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
        seg.addView(expenseBtn, marginLp(0, dp(54), 0, 0, dp(6), 0, 1));
        seg.addView(incomeBtn, marginLp(0, dp(54), dp(6), 0, 0, 0, 1));
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

        final long[] selectedTime = new long[]{System.currentTimeMillis()};
        LinearLayout dateCard = new LinearLayout(this);
        dateCard.setOrientation(LinearLayout.HORIZONTAL);
        dateCard.setGravity(Gravity.CENTER_VERTICAL);
        dateCard.setPadding(dp(16), 0, dp(16), 0);
        dateCard.setBackground(round(CARD, dp(14), BORDER));
        TextView calIcon = text("📅", 24, TEXT, false);
        calIcon.setGravity(Gravity.CENTER);
        dateCard.addView(calIcon, new LinearLayout.LayoutParams(dp(42), -1));
        LinearLayout dateTexts = new LinearLayout(this);
        dateTexts.setOrientation(LinearLayout.VERTICAL);
        TextView dateHint = text("點一下可調整日期與時間", 12, MUTED, false);
        TextView dateValue = text(formatFullDateMinute(selectedTime[0]), 20, TEXT, true);
        dateTexts.addView(dateHint);
        dateTexts.addView(dateValue);
        dateCard.addView(dateTexts, new LinearLayout.LayoutParams(0, -2, 1));
        TextView dateArrow = text("›", 28, MUTED, true);
        dateArrow.setGravity(Gravity.CENTER);
        dateCard.addView(dateArrow, new LinearLayout.LayoutParams(dp(30), -1));
        dateCard.setOnClickListener(v -> showDateTimePicker(selectedTime, dateValue));
        box.addView(label("日期 / 時間"));
        box.addView(dateCard, marginLp(-1, dp(76), 0, 0, 0, dp(16)));

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
            Transaction tx = new Transaction(selectedTime[0], amount, income ? "income" : "expense", "手動新增", merchant, category, note, "manual-" + selectedTime[0] + "-" + System.currentTimeMillis());
            TransactionStore.add(this, tx);
            Toast.makeText(this, "已新增" + (income ? "收入 " : "支出 ") + TransactionStore.money(amount), Toast.LENGTH_SHORT).show();
            showHome();
        });
        box.addView(save, marginLp(-1, dp(58), 0, dp(18), 0, dp(24)));

        setPage(scroll);
    }

    private String formatFullDateMinute(long time) {
        return new SimpleDateFormat("yyyy/MM/dd  HH:mm", Locale.TAIWAN).format(new Date(time));
    }

    private void showDateTimePicker(final long[] selectedTime, final TextView target) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(selectedTime[0]);
        DatePickerDialog dpd = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            Calendar picked = Calendar.getInstance();
            picked.setTimeInMillis(selectedTime[0]);
            picked.set(Calendar.YEAR, year);
            picked.set(Calendar.MONTH, month);
            picked.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            TimePickerDialog tpd = new TimePickerDialog(this, (timeView, hourOfDay, minute) -> {
                picked.set(Calendar.HOUR_OF_DAY, hourOfDay);
                picked.set(Calendar.MINUTE, minute);
                picked.set(Calendar.SECOND, 0);
                picked.set(Calendar.MILLISECOND, 0);
                selectedTime[0] = picked.getTimeInMillis();
                target.setText(formatFullDateMinute(selectedTime[0]));
            }, picked.get(Calendar.HOUR_OF_DAY), picked.get(Calendar.MINUTE), true);
            tpd.setTitle("選擇時間");
            tpd.show();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        dpd.setTitle("選擇日期");
        dpd.show();
    }

    private void addPresetChips(LinearLayout box, boolean income, EditText amount, EditText category, EditText merchant, EditText note) {
        List<Button> chips = new ArrayList<>();
        List<String> presets = income ? AppSettings.getQuickIncome(this) : AppSettings.getQuickExpense(this);
        for (String line : presets) {
            String[] p = line.split("\\|");
            final String name = p.length > 0 ? p[0].trim() : line.trim();
            final String presetAmount = p.length > 1 ? p[1].trim() : "0";
            final String presetCategory = p.length > 2 ? p[2].trim() : (income ? "收入" : guessCategory(name));
            if (name.isEmpty()) continue;
            Button chip = presetCard(name + ((presetAmount.equals("0") || presetAmount.isEmpty()) ? "" : "\n$" + presetAmount), income ? 0xFFF2F0FF : 0xFFFFF3EA, income ? PURPLE : ORANGE);
            chip.setOnClickListener(v -> {
                if (amount.getText().toString().trim().isEmpty() && !presetAmount.equals("0") && !presetAmount.isEmpty()) amount.setText(presetAmount);
                merchant.setText(name);
                category.setText(presetCategory);
                if (note.getText().toString().trim().isEmpty()) note.setText(name);
            });
            chips.add(chip);
        }
        addChipGrid(box, chips, 3);
    }

    private void addRecentChips(LinearLayout box, boolean income, EditText category, EditText merchant) {
        List<Button> chips = new java.util.ArrayList<>();
        List<String> recents = TransactionStore.recentChips(this, income ? "income" : "expense", 8);
        if (recents.isEmpty()) {
            String[] defaults = income ? new String[]{"零用錢", "薪水", "打工", "退款"} : new String[]{"餐飲", "交通", "購物", "娛樂"};
            for (String s : defaults) recents.add(s);
        }
        for (String r : recents) {
            Button chip = presetCard(r, CHIP, TEXT);
            chip.setOnClickListener(v -> {
                merchant.setText(r);
                category.setText(income ? "收入" : guessCategory(r));
            });
            chips.add(chip);
        }
        addChipGrid(box, chips, 4);
    }

    private void addChipGrid(LinearLayout box, List<Button> chips, int perRow) {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < chips.size(); i += perRow) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            for (int j = 0; j < perRow; j++) {
                if (i + j < chips.size()) {
                    row.addView(chips.get(i + j), evenChipLp());
                } else {
                    TextView blank = new TextView(this);
                    row.addView(blank, evenChipLp());
                }
            }
            grid.addView(row, new LinearLayout.LayoutParams(-1, dp(58)));
        }
        box.addView(grid);
    }

    private LinearLayout.LayoutParams evenChipLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(50), 1);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        return lp;
    }

    private Button presetCard(String text, int bg, int fg) {
        Button b = smallChip(text, bg, fg);
        b.setTextSize(13);
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(false);
        b.setMinHeight(dp(50));
        b.setPadding(dp(2), 0, dp(2), 0);
        return b;
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

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 32, TEXT, false);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> showHome());
        top.addView(back, new LinearLayout.LayoutParams(dp(38), -2));
        TextView title = text("財務報表", 22, TEXT, true);
        title.setGravity(Gravity.CENTER);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView chartIcon = text("▥", 25, TEXT, true);
        chartIcon.setGravity(Gravity.RIGHT);
        top.addView(chartIcon, new LinearLayout.LayoutParams(dp(44), -2));
        box.addView(top, marginLp(-1, -2, 0, 0, 0, dp(12)));

        LinearLayout typeTabs = new LinearLayout(this);
        typeTabs.setOrientation(LinearLayout.HORIZONTAL);
        typeTabs.setBackground(round(AppSettings.getBool(this, AppSettings.KEY_DARK_MODE, false) ? 0xFF202933 : 0xFFF2F3F5, dp(18), BORDER));
        typeTabs.addView(reportTab("支出", "expense".equals(reportType), () -> { reportType = "expense"; showStats(); }), marginLp(0, dp(54), 0, 0, dp(5), 0, 1));
        typeTabs.addView(reportTab("收入", "income".equals(reportType), () -> { reportType = "income"; showStats(); }), marginLp(0, dp(54), dp(5), 0, dp(5), 0, 1));
        typeTabs.addView(reportTab("結餘", "balance".equals(reportType), () -> { reportType = "balance"; showStats(); }), marginLp(0, dp(54), dp(5), 0, 0, 0, 1));
        box.addView(typeTabs, marginLp(-1, -2, 0, 0, 0, dp(12)));

        LinearLayout rangeTabs = new LinearLayout(this);
        rangeTabs.setOrientation(LinearLayout.HORIZONTAL);
        rangeTabs.addView(rangeChip("月", "month".equals(reportRange), () -> { reportRange = "month"; showStats(); }), marginLp(0, dp(48), 0, 0, dp(4), 0, 1));
        rangeTabs.addView(rangeChip("近六個月", "six".equals(reportRange), () -> { reportRange = "six"; showStats(); }), marginLp(0, dp(48), dp(4), 0, dp(4), 0, 1));
        rangeTabs.addView(rangeChip("年", "year".equals(reportRange), () -> { reportRange = "year"; showStats(); }), marginLp(0, dp(48), dp(4), 0, dp(4), 0, 1));
        rangeTabs.addView(rangeChip("自訂", "custom".equals(reportRange), () -> { reportRange = "custom"; showStats(); }), marginLp(0, dp(48), dp(4), 0, 0, 0, 1));
        box.addView(rangeTabs, marginLp(-1, -2, 0, 0, 0, dp(12)));

        LinearLayout monthRow = new LinearLayout(this);
        monthRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView range = text(reportRangeLabel(), 17, TEXT, true);
        monthRow.addView(range, new LinearLayout.LayoutParams(0, -2, 1));
        TextView currency = text("全幣別 (TWD) ▾", 15, TEXT, true);
        currency.setGravity(Gravity.RIGHT);
        monthRow.addView(currency, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(monthRow, marginLp(-1, -2, 0, 0, 0, dp(12)));

        long start = reportStartMillis();
        long end = reportEndMillis();
        int expense = TransactionStore.expenseBetween(this, start, end);
        int income = TransactionStore.incomeBetween(this, start, end);
        int balance = income - expense;
        int budget = Math.max(1, AppSettings.getMonthlyBudget(this) * reportMonthFactor());

        int mainValue;
        int remainValue;
        int mainColor;
        String centerLabel;
        String mainTitle;
        if ("income".equals(reportType)) {
            mainValue = income;
            remainValue = Math.max(0, Math.max(budget, income) - income);
            mainColor = GREEN;
            centerLabel = "收入";
            mainTitle = "總收入";
        } else if ("balance".equals(reportType)) {
            mainValue = Math.max(0, balance);
            int reference = Math.max(1, Math.max(income, expense));
            remainValue = Math.max(0, reference - mainValue);
            mainColor = balance >= 0 ? GREEN : EXPENSE_RED;
            centerLabel = balance >= 0 ? "結餘" : "赤字";
            mainTitle = "總結餘";
        } else {
            mainValue = expense;
            remainValue = Math.max(0, budget - expense);
            mainColor = EXPENSE_RED;
            centerLabel = "支出";
            mainTitle = "總支出";
        }

        LinearLayout chartCard = card();
        chartCard.setGravity(Gravity.CENTER);
        DonutChartView donut = new DonutChartView(this);
        donut.setDarkMode(AppSettings.getBool(this, AppSettings.KEY_DARK_MODE, false));
        donut.setCenterLabel(centerLabel);
        donut.setData(mainValue, remainValue, 0, AppSettings.getPalette(this));
        chartCard.addView(donut, new LinearLayout.LayoutParams(dp(230), dp(230)));
        int displayAmount = "balance".equals(reportType) ? balance : mainValue;
        TextView centerSummary = text(mainTitle + "  " + TransactionStore.money(displayAmount), 18, mainColor, true);
        centerSummary.setGravity(Gravity.CENTER);
        chartCard.addView(centerSummary, marginLp(-1, -2, 0, dp(6), 0, 0));
        TextView budgetHint = text("支出 " + TransactionStore.money(expense) + "｜收入 " + TransactionStore.money(income) + "｜預算參考 " + TransactionStore.money(budget), 13, MUTED, false);
        budgetHint.setGravity(Gravity.CENTER);
        chartCard.addView(budgetHint, marginLp(-1, -2, 0, dp(4), 0, dp(6)));
        box.addView(chartCard, marginLp(-1, -2, 0, 0, 0, dp(12)));

        LinearLayout summary = card();
        summary.addView(text(reportRangeLabel() + "摘要", 18, TEXT, true));
        summary.addView(text("支出：" + TransactionStore.money(expense) + "　收入：" + TransactionStore.money(income), 15, TEXT, false));
        summary.addView(text("結餘：" + TransactionStore.money(balance), 15, balance >= 0 ? GREEN : EXPENSE_RED, true));
        if ("month".equals(reportRange)) {
            summary.addView(text("月底預估花費：" + TransactionStore.money(TransactionStore.forecastMonthExpense(this)), 14, MUTED, false));
        } else if ("custom".equals(reportRange)) {
            summary.addView(text("自訂目前先顯示最近 30 天，之後可再做日期區間選擇。", 13, MUTED, false));
        }
        box.addView(summary, marginLp(-1, -2, 0, 0, 0, dp(12)));

        LinearLayout tools = card();
        tools.addView(text("報表工具", 18, TEXT, true));
        LinearLayout toolRow = new LinearLayout(this);
        toolRow.setOrientation(LinearLayout.HORIZONTAL);
        Button csv = smallChip("匯出 CSV", 0xFFFFECEC, EXPENSE_RED);
        csv.setOnClickListener(v -> shareCsv());
        Button scan = smallChip("掃描重複", CHIP, TEXT);
        scan.setOnClickListener(v -> {
            int removed = TransactionStore.autoFixDuplicates(this);
            Toast.makeText(this, "已移除 " + removed + " 筆疑似重複資料", Toast.LENGTH_LONG).show();
            showStats();
        });
        toolRow.addView(csv, new LinearLayout.LayoutParams(0, dp(50), 1));
        toolRow.addView(scan, new LinearLayout.LayoutParams(0, dp(50), 1));
        tools.addView(toolRow);
        box.addView(tools);

        setPage(scroll);
    }

    private long reportStartMillis() {
        Calendar c = Calendar.getInstance();
        if ("six".equals(reportRange)) {
            c.add(Calendar.MONTH, -5);
            c.set(Calendar.DAY_OF_MONTH, 1);
        } else if ("year".equals(reportRange)) {
            c.set(Calendar.MONTH, Calendar.JANUARY);
            c.set(Calendar.DAY_OF_MONTH, 1);
        } else if ("custom".equals(reportRange)) {
            c.add(Calendar.DAY_OF_MONTH, -29);
        } else {
            c.set(Calendar.DAY_OF_MONTH, 1);
        }
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private long reportEndMillis() {
        return System.currentTimeMillis() + 60L * 1000L;
    }

    private int reportMonthFactor() {
        if ("six".equals(reportRange)) return 6;
        if ("year".equals(reportRange)) return 12;
        return 1;
    }

    private String reportRangeLabel() {
        if ("six".equals(reportRange)) return "近六個月";
        if ("year".equals(reportRange)) return new SimpleDateFormat("yyyy年", Locale.TAIWAN).format(new Date());
        if ("custom".equals(reportRange)) return "自訂：最近 30 天";
        return new SimpleDateFormat("yyyy年M月", Locale.TAIWAN).format(new Date());
    }

    private Button reportTab(String s, boolean selected, final Runnable click) {
        Button b = smallChip(s, selected ? 0xFFFFCC55 : CHIP, selected ? 0xFF1F2329 : TEXT);
        b.setTextSize(16);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setOnClickListener(v -> { if (click != null) click.run(); });
        return b;
    }

    private Button rangeChip(String s, boolean selected, final Runnable click) {
        Button b = smallChip(s, selected ? 0xFFFFCC55 : (AppSettings.getBool(this, AppSettings.KEY_DARK_MODE, false) ? 0xFF1B2530 : 0xFFF7F7F8), selected ? 0xFF1F2329 : TEXT);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setOnClickListener(v -> { if (click != null) click.run(); });
        return b;
    }

    private void showSideMenu() {
        applyModeColors();
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(18), dp(20), dp(18));
        panel.setBackground(round(AppSettings.getBool(this, AppSettings.KEY_DARK_MODE, false) ? 0xFF1E242D : 0xFFFFFFFF, dp(20), BORDER));

        LinearLayout closeRow = new LinearLayout(this);
        closeRow.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        TextView close = text("✕", 22, MUTED, true);
        close.setGravity(Gravity.CENTER);
        close.setBackground(round(AppSettings.getBool(this, AppSettings.KEY_DARK_MODE, false) ? 0xFF2A333F : 0xFFF2F2F2, dp(18), BORDER));
        close.setOnClickListener(v -> closeDialogs());
        closeRow.addView(close, new LinearLayout.LayoutParams(dp(42), dp(42)));
        panel.addView(closeRow, marginLp(-1, -2, 0, 0, 0, dp(4)));

        LinearLayout profile = new LinearLayout(this);
        profile.setGravity(Gravity.CENTER_VERTICAL);
        TextView avatar = text("●", 48, AppSettings.getBool(this, AppSettings.KEY_DARK_MODE, false) ? 0xFFFFFFFF : 0xFF222222, true);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(round(AppSettings.getBool(this, AppSettings.KEY_DARK_MODE, false) ? 0xFF2A333F : 0xFFF2F2F2, dp(42), BORDER));
        profile.addView(avatar, new LinearLayout.LayoutParams(dp(78), dp(78)));
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(14), 0, 0, 0);
        info.addView(text("自動記帳使用者  ☁", 18, TEXT, true));
        Button vip = smallChip("升級 VIP", CHIP, TEXT);
        info.addView(vip, marginLp(dp(120), dp(42), 0, dp(8), 0, 0));
        profile.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        panel.addView(profile, marginLp(-1, -2, 0, 0, 0, dp(14)));

        LinearLayout topAction = new LinearLayout(this);
        topAction.setOrientation(LinearLayout.HORIZONTAL);
        Button search = smallChip("⌕", CHIP, TEXT);
        Button streak = smallChip("🔥  連續記帳", 0xFFFF7076, 0xFFFFFFFF);
        streak.setTextSize(16);
        topAction.addView(search, new LinearLayout.LayoutParams(dp(76), dp(58)));
        topAction.addView(streak, new LinearLayout.LayoutParams(0, dp(58), 1));
        panel.addView(topAction, marginLp(-1, -2, 0, 0, 0, dp(16)));

        panel.addView(sideMenuButton("◔", "帳務報表", v -> { closeDialogs(); showStats(); }));
        panel.addView(sideMenuButton("▤", "發票記帳  HOT", v -> showFeatureComing("發票記帳", "之後會接載具發票匯入與中獎提醒。")));
        panel.addView(sideMenuButton("▦", "記帳小工具", v -> showWidgetInfoDialog()));
        panel.addView(sideMenuButton("👥", "共享帳本", v -> showFeatureComing("共享帳本", "未來可做室友、情侶、社團共同帳本。")));
        panel.addView(sideMenuButton("▦", "分類管理", v -> showSettings()));
        panel.addView(sideMenuButton("🏷", "固定收支", v -> showFeatureComing("固定收支", "之後可新增每月房租、訂閱、薪水自動產生紀錄。")));
        panel.addView(sideMenuButton("🌐", "財務模擬", v -> showStats()));
        panel.addView(sideMenuButton("⚙", "功能設定", v -> showSettings()));
        TextView encourage = text("👍  給予鼓勵", 15, 0xFF34B7E6, true);
        encourage.setGravity(Gravity.CENTER);
        encourage.setPadding(0, dp(14), 0, 0);
        panel.addView(encourage);

        AlertDialog dialog = new AlertDialog.Builder(this).setView(panel).create();
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        });
        sideDialog = dialog;
        dialog.show();
    }

    private AlertDialog sideDialog;

    private void closeDialogs() {
        if (sideDialog != null && sideDialog.isShowing()) sideDialog.dismiss();
    }

    private View sideMenuButton(String icon, String label, View.OnClickListener listener) {
        Button b = smallChip(icon + "   " + label, AppSettings.getBool(this, AppSettings.KEY_DARK_MODE, false) ? 0xFF222A34 : 0xFFF7F7F8, TEXT);
        b.setTextSize(16);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(dp(18), 0, dp(18), 0);
        b.setOnClickListener(v -> { closeDialogs(); listener.onClick(v); });
        LinearLayout.LayoutParams lp = marginLp(-1, dp(56), 0, dp(6), 0, dp(6));
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(b, lp);
        return wrap;
    }

    private void showFeatureComing(String title, String body) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(body + "\n\n這個會先放進功能入口，之後可以再做成正式功能。")
                .setPositiveButton("知道了", null)
                .show();
    }


    private String carrierSubtitle() {
        String carrier = BarcodeUtil.normalizeCarrier(AppSettings.getString(this, AppSettings.KEY_CARRIER_BARCODE, ""));
        return carrier.isEmpty() ? "輸入手機條碼號碼，桌面小工具會自動產生條碼" : "目前載具：" + carrier + "，會顯示在載具小工具";
    }

    private void showCarrierBarcodeDialog() {
        final EditText input = new EditText(this);
        input.setHint("例如：/AB12CDE");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        input.setText(BarcodeUtil.normalizeCarrier(AppSettings.getString(this, AppSettings.KEY_CARRIER_BARCODE, "")));
        input.setSelectAllOnFocus(true);
        input.setTextSize(18);
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setTextColor(0xFF1F2933);
        input.setHintTextColor(0xFF8A96A3);
        input.setBackground(round(0xFFF8FBFF, dp(12), 0xFFD6EAF5));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(4), dp(2), dp(4), 0);
        panel.addView(text("不用綁定財政部帳號。只要輸入手機條碼號碼，App 會在桌面小工具產生條碼，方便結帳時掃描。", 14, 0xFF27323A, false));
        TextView warn = text("注意：這只是顯示條碼，不會自動登入載具或查發票。", 13, 0xFF6A7680, false);
        warn.setPadding(0, dp(8), 0, dp(8));
        panel.addView(warn);
        panel.addView(input, marginLp(-1, dp(48), 0, dp(4), 0, 0));

        new AlertDialog.Builder(this)
                .setTitle("設定載具條碼")
                .setView(panel)
                .setNegativeButton("取消", null)
                .setNeutralButton("清除", (d, w) -> {
                    AppSettings.setString(this, AppSettings.KEY_CARRIER_BARCODE, "");
                    try { CarrierBalanceWidgetProvider.updateAll(this); } catch (Exception ignored) { }
                    Toast.makeText(this, "已清除載具條碼", Toast.LENGTH_SHORT).show();
                    showSettings();
                })
                .setPositiveButton("儲存", (d, w) -> {
                    String code = BarcodeUtil.normalizeCarrier(input.getText().toString());
                    if (code.length() < 5) {
                        Toast.makeText(this, "載具號碼看起來太短，請確認後再儲存", Toast.LENGTH_LONG).show();
                        return;
                    }
                    AppSettings.setString(this, AppSettings.KEY_CARRIER_BARCODE, code);
                    try { CarrierBalanceWidgetProvider.updateAll(this); } catch (Exception ignored) { }
                    Toast.makeText(this, "已儲存載具條碼，點桌面條碼可複製", Toast.LENGTH_SHORT).show();
                    showSettings();
                })
                .show();
    }

    private void showWidgetInfoDialog() {
        new AlertDialog.Builder(this)
                .setTitle("桌面小工具")
                .setMessage("V12 有兩種桌面小工具：\n\n1. 簡易記帳小工具：顯示餘額、今日花費，點「支出／收入」快速新增。\n\n2. 載具＋記帳小工具：上方只顯示一個載具條碼，點條碼可以複製載具號碼，下方顯示餘額與快速新增。\n\nAndroid 桌面小工具不能直接放真正的打字輸入框，所以小工具上的「點我輸入金額」會打開一個很小的快速新增視窗，不會進到完整 App。")
                .setPositiveButton("知道了", null)
                .setNeutralButton("設定載具", (d, w) -> showCarrierBarcodeDialog())
                .show();
    }

    private View dialogSwitchRow(String title, String subtitle, String key, boolean def) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(6), dp(8), dp(2), dp(8));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(title, 16, 0xFF1F2933, true));
        texts.addView(text(subtitle, 12, 0xFF66727E, false));
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        Switch sw = new Switch(this);
        sw.setChecked(AppSettings.getBool(this, key, def));
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> AppSettings.setBool(this, key, isChecked));
        row.addView(sw);
        return row;
    }

    private void showNotificationSettingsDialog() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(6), dp(2), dp(6), dp(4));
        panel.setBackgroundColor(0xFFFFFFFF);

        panel.addView(text("通知開關", 17, 0xFF1F2933, true));
        panel.addView(dialogSwitchRow("每日昨日花費摘要", "每天固定時間提醒你昨天花多少", AppSettings.KEY_NOTIFY_DAILY, true));
        panel.addView(dialogSwitchRow("自動記帳完成通知", "每記到一筆就跳小通知", AppSettings.KEY_NOTIFY_AUTO_SAVED, true));
        panel.addView(dialogSwitchRow("預算提醒", "接近或超過本月預算時提醒", AppSettings.KEY_NOTIFY_BUDGET, true));
        panel.addView(dialogSwitchRow("重複資料提醒", "偵測到疑似同一筆時提醒你", AppSettings.KEY_NOTIFY_DUPLICATE, false));

        final EditText time = new EditText(this);
        time.setHint("09:00");
        time.setText(AppSettings.getString(this, AppSettings.KEY_DAILY_NOTIFY_TIME, "09:00"));
        time.setSingleLine(true);
        time.setTextSize(18);
        time.setInputType(InputType.TYPE_CLASS_TEXT);
        time.setTextColor(0xFF1F2933);
        time.setHintTextColor(0xFF8A96A3);
        time.setPadding(dp(12), 0, dp(12), 0);
        time.setBackground(round(0xFFF8FBFF, dp(12), 0xFFD6EAF5));
        TextView timeLabel = text("每日摘要通知時間（24 小時制）", 14, 0xFF1F2933, true);
        timeLabel.setPadding(0, dp(8), 0, dp(4));
        panel.addView(timeLabel);
        panel.addView(time, marginLp(-1, dp(50), 0, dp(2), 0, dp(8)));

        panel.addView(text("也可以到設定頁面調整 LINE Pay、載具、Google 錢包、銀行通知等偵測來源。", 13, 0xFF66727E, false));

        new AlertDialog.Builder(this)
                .setTitle("鈴鐺通知設定")
                .setView(panel)
                .setNegativeButton("取消", null)
                .setPositiveButton("儲存", (d, w) -> {
                    String raw = time.getText().toString().trim();
                    if (!raw.matches("^([01]?\\d|2[0-3]):[0-5]\\d$")) {
                        Toast.makeText(this, "時間格式請輸入 09:00 這種格式", Toast.LENGTH_LONG).show();
                        return;
                    }
                    AppSettings.setString(this, AppSettings.KEY_DAILY_NOTIFY_TIME, raw);
                    DailyReportScheduler.schedule(this);
                    Toast.makeText(this, "已更新通知設定", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void showSettings() {
        tab = 3;
        applyModeColors();
        ScrollView scroll = pageBase();
        LinearLayout box = pageBox(scroll);
        box.addView(centerTitle("設定"));

        LinearLayout startSec = section("上架前必備 / 使用說明");
        startSec.addView(settingButton("第一次開啟的新手說明", "重新看一次 App 使用流程", v -> showOnboarding()));
        startSec.addView(settingButton("通知讀取用途說明", "說明為什麼需要通知讀取權限", v -> showNotificationPurpose()));
        startSec.addView(settingButton("鈴鐺通知設定", "設定每日摘要、記帳完成通知與提醒時間", v -> showNotificationSettingsDialog()));
        startSec.addView(settingButton("隱私權政策頁面", "本機保存、通知讀取、資料清除與備份說明", v -> showPrivacyPolicy()));
        box.addView(startSec, marginLp(-1, -2, 0, dp(8), 0, dp(10)));

        LinearLayout widgetSec = section("桌面小工具與載具");
        widgetSec.addView(settingButton("設定載具條碼", carrierSubtitle(), v -> showCarrierBarcodeDialog()));
        widgetSec.addView(settingButton("桌面小工具說明", "有兩種：簡易記帳小工具、載具條碼＋餘額小工具", v -> showWidgetInfoDialog()));
        box.addView(widgetSec, marginLp(-1, -2, 0, 0, 0, dp(10)));

        LinearLayout sources = section("通知偵測來源（自動抓取）");
        sources.addView(switchRow("LINE Pay", "擷取付款與交易通知", AppSettings.KEY_LINE_PAY, true));
        sources.addView(switchRow("載具發票", "擷取電子發票與消費資訊", AppSettings.KEY_INVOICE, true));
        sources.addView(switchRow("Google 錢包 / Google Pay", "擷取 Google Wallet 刷卡交易", AppSettings.KEY_GOOGLE_WALLET, true));
        sources.addView(switchRow("銀行通知", "擷取帳戶交易、信用卡刷卡、提款、入帳通知", AppSettings.KEY_BANK, true));
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

        LinearLayout manage = section("分類與常用項目");
        manage.addView(settingButton("支出分類管理", "自訂餐飲、交通、購物、娛樂等分類", v -> showManageListDialog("支出分類管理", AppSettings.KEY_EXPENSE_CATEGORIES, AppSettings.getExpenseCategories(this), "每行一個分類，例如：餐飲")));
        manage.addView(settingButton("收入分類管理", "自訂薪水、零用錢、退款等分類", v -> showManageListDialog("收入分類管理", AppSettings.KEY_INCOME_CATEGORIES, AppSettings.getIncomeCategories(this), "每行一個分類，例如：薪水")));
        manage.addView(settingButton("自訂支出常用項目", "格式：名稱|預設金額|分類，例如 午餐|120|餐飲", v -> showManageListDialog("自訂支出常用項目", AppSettings.KEY_QUICK_EXPENSE, AppSettings.getQuickExpense(this), "格式：名稱|金額|分類")));
        manage.addView(settingButton("自訂收入常用項目", "格式：名稱|預設金額|分類，例如 零用錢|1000|收入", v -> showManageListDialog("自訂收入常用項目", AppSettings.KEY_QUICK_INCOME, AppSettings.getQuickIncome(this), "格式：名稱|金額|分類")));
        box.addView(manage, marginLp(-1, -2, 0, 0, 0, dp(10)));

        LinearLayout account = section("帳號、備份與資料");
        account.addView(settingButton("G  綁定 Google 帳號", AppSettings.getBool(this, AppSettings.KEY_GOOGLE_BOUND, false) ? "已啟用同步備份準備項目" : "自用版先建立入口，正式版再接 Google 登入", v -> {
            AppSettings.setBool(this, AppSettings.KEY_GOOGLE_BOUND, true);
            Toast.makeText(this, "自用版先記錄為已綁定；正式版可再接 Google 登入與雲端同步。", Toast.LENGTH_LONG).show();
            showSettings();
        }));
        account.addView(settingButton("匯出 CSV", "把所有記帳資料匯出成 CSV 文字，可貼到試算表", v -> shareCsv()));
        account.addView(settingButton("備份資料", "匯出 JSON 備份，可之後貼回 App 還原", v -> shareBackup()));
        account.addView(settingButton("還原資料", "貼上之前備份的 JSON 資料", v -> showRestoreDialog()));
        account.addView(settingButton("清除資料", "清除全部收入、支出、通知 hash 與重複判斷紀錄", v -> confirmClear()));
        box.addView(account, marginLp(-1, -2, 0, 0, 0, dp(10)));

        LinearLayout advanced = section("自動除錯與防重複");
        advanced.addView(switchRow("排除自己通知", "不記錄本 App 自己跳出的通知，避免重複記帳", AppSettings.KEY_EXCLUDE_OWN, true));
        advanced.addView(switchRow("防重複記帳", "同一筆通知短時間內自動合併", AppSettings.KEY_DEDUPE, true));
        advanced.addView(switchRow("LINE Pay / 載具 / Google 錢包 / 銀行同筆偵測", "同金額短時間或同日交叉比對，避免一筆消費記兩次", AppSettings.KEY_CROSS_SOURCE_DEDUPE, true));
        advanced.addView(settingButton("立即掃描重複資料", "掃描既有紀錄，移除疑似同一筆的重複資料", v -> {
            int removed = TransactionStore.autoFixDuplicates(this);
            Toast.makeText(this, "已掃描完成，移除 " + removed + " 筆疑似重複資料", Toast.LENGTH_LONG).show();
            refreshCurrent();
        }));
        advanced.addView(settingButton("錯誤回報 / 自動除錯紀錄", "查看哪些通知被新增、哪些被判斷為重複", v -> showDebugDialog()));
        advanced.addView(featureRow("智慧分類", "自動判斷餐飲、交通、收入、訂閱等分類"));
        advanced.addView(featureRow("昨日花費摘要", "每天早上自動提醒昨天總共花多少"));
        advanced.addView(featureRow("預算提醒", "超過每月預算時提醒你注意"));
        advanced.addView(featureRow("月底預估花費", "依照目前花費速度推估月底可能花多少"));
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

    private LinearLayout.LayoutParams marginLp(int w, int h, int l, int t, int r, int b, float weight) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h, weight);
        lp.setMargins(l, t, r, b);
        return lp;
    }

    private LinearLayout.LayoutParams marginLp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.setMargins(l, t, r, b);
        return lp;
    }

    private View settingButton(String title, String subtitle, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(title, 16, TEXT, true));
        texts.addView(text(subtitle, 12, MUTED, false));
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        TextView arrow = text("›", 24, MUTED, true);
        arrow.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(28), -1));
        row.setOnClickListener(listener);
        return row;
    }

    private void showOnboarding() {
        new AlertDialog.Builder(this)
                .setTitle("歡迎使用自動記帳 V10")
                .setMessage("這版新增：\n\n1. 通知自動記帳：LINE Pay、載具、Google 錢包、銀行刷卡通知。\n2. 手動補登：收入支出都能加備註。\n3. 點紀錄先查看，長按才快速修改，避免誤觸。\n4. 防重複：同金額短時間交叉比對，避免一筆記兩次。\n5. 可匯出 CSV、備份、還原、清除資料。\n\n建議先去「設定」看通知讀取用途，再開啟通知讀取權限。")
                .setPositiveButton("我知道了", (d, w) -> AppSettings.setBool(this, AppSettings.KEY_ONBOARDED, true))
                .setNeutralButton("通知用途", (d, w) -> showNotificationPurpose())
                .show();
    }

    private void showNotificationPurpose() {
        new AlertDialog.Builder(this)
                .setTitle("通知讀取用途說明")
                .setMessage("自動記帳需要通知讀取權限，是為了在你授權後讀取 LINE Pay、載具發票、Google 錢包、銀行刷卡與交易簡訊通知，從通知文字抓出金額、店家、收入或支出。\n\n資料預設只存在你的手機本機。\n\nApp 會自動排除自己發出的通知，也會用金額、時間、來源與店家判斷同一筆消費，避免重複記帳。")
                .setPositiveButton("了解", null)
                .show();
    }

    private void showPrivacyPolicy() {
        new AlertDialog.Builder(this)
                .setTitle("隱私權政策")
                .setMessage("自動記帳會在你授權後讀取通知內容，用於自動辨識付款、收入、發票與銀行交易。\n\n目前自用版資料預設儲存在手機本機，不會主動上傳到伺服器。\n\n你可以在設定中匯出 CSV、備份資料、還原資料或清除全部資料。\n\n若未來啟用 Google 同步，會在使用者同意後才將記帳資料同步到使用者自己的 Google 帳號。\n\n此 App 不提供投資建議；月底預估與儲蓄提醒只作為預算規劃參考。")
                .setPositiveButton("關閉", null)
                .show();
    }

    private void shareCsv() {
        String csv = TransactionStore.exportCsv(this);
        copyToClipboard("AutoLedger CSV", csv);
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/csv");
        share.putExtra(Intent.EXTRA_SUBJECT, "自動記帳 CSV 匯出");
        share.putExtra(Intent.EXTRA_TEXT, csv);
        startActivity(Intent.createChooser(share, "匯出 CSV"));
    }

    private void shareBackup() {
        String json = TransactionStore.exportJson(this);
        copyToClipboard("AutoLedger Backup", json);
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("application/json");
        share.putExtra(Intent.EXTRA_SUBJECT, "自動記帳資料備份");
        share.putExtra(Intent.EXTRA_TEXT, json);
        startActivity(Intent.createChooser(share, "備份資料"));
    }

    private void showRestoreDialog() {
        final EditText input = new EditText(this);
        input.setHint("貼上備份 JSON 資料");
        input.setMinLines(6);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        new AlertDialog.Builder(this)
                .setTitle("還原資料")
                .setMessage("貼上之前備份的 JSON。還原會覆蓋目前記帳資料，建議先備份再還原。")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("還原", (d, w) -> {
                    boolean ok = TransactionStore.importJson(this, input.getText().toString());
                    Toast.makeText(this, ok ? "已還原資料" : "還原失敗，格式不正確", Toast.LENGTH_LONG).show();
                    refreshCurrent();
                })
                .show();
    }

    private void showManageListDialog(String title, String key, List<String> items, String hint) {
        final EditText input = new EditText(this);
        input.setHint(hint);
        input.setMinLines(7);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        StringBuilder raw = new StringBuilder();
        for (String item : items) {
            if (raw.length() > 0) raw.append('\n');
            raw.append(item);
        }
        input.setText(raw.toString());
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage("每一行一個項目。常用項目可用：名稱|金額|分類。金額填 0 代表不預設金額。")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("儲存", (d, w) -> {
                    List<String> out = new ArrayList<>();
                    for (String line : input.getText().toString().split("\\n")) {
                        String clean = line.trim();
                        if (!clean.isEmpty()) out.add(clean);
                    }
                    AppSettings.setList(this, key, out);
                    Toast.makeText(this, "已儲存", Toast.LENGTH_SHORT).show();
                    showSettings();
                })
                .show();
    }

    private void showDebugDialog() {
        new AlertDialog.Builder(this)
                .setTitle("錯誤回報 / 自動除錯紀錄")
                .setMessage(TransactionStore.getDebugLogs(this))
                .setNegativeButton("關閉", null)
                .setNeutralButton("清除紀錄", (d, w) -> TransactionStore.clearDebugLogs(this))
                .setPositiveButton("掃描重複", (d, w) -> {
                    int removed = TransactionStore.autoFixDuplicates(this);
                    Toast.makeText(this, "已移除 " + removed + " 筆疑似重複資料", Toast.LENGTH_LONG).show();
                    refreshCurrent();
                })
                .show();
    }

    private void showTransactionDetail(Transaction tx) {
        String message = "類型：" + ("income".equals(tx.direction) ? "收入" : "支出") +
                "\n金額：" + TransactionStore.money(tx.amount) +
                "\n分類：" + tx.category +
                "\n店家 / 來源：" + tx.merchant +
                "\n通知來源：" + tx.source +
                "\n時間：" + TransactionStore.formatTime(tx.timeMillis) +
                "\n備註 / 原始內容：\n" + tx.raw +
                "\n\n為了避免誤觸，點紀錄只會先查看；要修改請按「修改」，或在列表長按該筆紀錄。";
        new AlertDialog.Builder(this)
                .setTitle("紀錄詳情")
                .setMessage(message)
                .setNegativeButton("關閉", null)
                .setNeutralButton("刪除", (d, w) -> showDeleteTxConfirm(tx))
                .setPositiveButton("修改", (d, w) -> showEditTransactionDialog(tx))
                .show();
    }

    private void showEditTransactionDialog(Transaction tx) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(8), dp(4), dp(8), dp(4));

        Switch incomeSwitch = new Switch(this);
        incomeSwitch.setText("收入（關閉就是支出）");
        incomeSwitch.setTextColor(TEXT);
        incomeSwitch.setChecked("income".equals(tx.direction));
        form.addView(incomeSwitch);

        final EditText amount = edit("金額", true);
        amount.setInputType(InputType.TYPE_CLASS_NUMBER);
        amount.setText(String.valueOf(tx.amount));
        form.addView(label("金額"));
        form.addView(amount, marginLp(-1, dp(52), 0, 0, 0, dp(8)));

        final EditText category = edit("分類", false);
        category.setText(tx.category);
        form.addView(label("分類"));
        form.addView(category, marginLp(-1, dp(52), 0, 0, 0, dp(8)));

        final EditText merchant = edit("店家 / 來源", false);
        merchant.setText(tx.merchant);
        form.addView(label("店家 / 來源"));
        form.addView(merchant, marginLp(-1, dp(52), 0, 0, 0, dp(8)));

        final EditText note = edit("備註 / 用在哪裡", false);
        note.setMinLines(3);
        note.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        note.setText(tx.raw);
        form.addView(label("備註 / 用在哪裡"));
        form.addView(note, marginLp(-1, dp(90), 0, 0, 0, dp(8)));

        new AlertDialog.Builder(this)
                .setTitle("修改紀錄")
                .setMessage("自動記帳與手動記帳都可以改。修改後會重新計算首頁餘額、圓形圖與統計。")
                .setView(form)
                .setNegativeButton("取消", null)
                .setPositiveButton("儲存", (d, w) -> {
                    int value = 0;
                    try { value = Integer.parseInt(amount.getText().toString().replace(",", "").trim()); } catch (Exception ignored) { }
                    if (value <= 0) {
                        Toast.makeText(this, "金額要大於 0", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Transaction edited = new Transaction(
                            tx.timeMillis,
                            value,
                            incomeSwitch.isChecked() ? "income" : "expense",
                            tx.source,
                            merchant.getText().toString().trim(),
                            category.getText().toString().trim().isEmpty() ? (incomeSwitch.isChecked() ? "收入" : "未分類") : category.getText().toString().trim(),
                            note.getText().toString().trim(),
                            tx.hash == null || tx.hash.isEmpty() ? "edit-" + tx.timeMillis : tx.hash
                    );
                    boolean ok = TransactionStore.update(this, tx.hash, tx.timeMillis, edited);
                    Toast.makeText(this, ok ? "已修改紀錄" : "修改失敗", Toast.LENGTH_SHORT).show();
                    refreshCurrent();
                })
                .show();
    }

    private void showDeleteTxConfirm(Transaction tx) {
        new AlertDialog.Builder(this)
                .setTitle("刪除這筆紀錄？")
                .setMessage("刪除後無法再復原。\n\n" + ("income".equals(tx.direction) ? "收入 " : "支出 ") + TransactionStore.money(tx.amount) + "｜" + tx.merchant)
                .setNegativeButton("取消", null)
                .setPositiveButton("刪除", (d, w) -> {
                    boolean ok = TransactionStore.delete(this, tx.hash, tx.timeMillis);
                    Toast.makeText(this, ok ? "已刪除" : "刪除失敗", Toast.LENGTH_SHORT).show();
                    refreshCurrent();
                })
                .show();
    }

    private void copyToClipboard(String label, String text) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText(label, text));
            Toast.makeText(this, "已複製到剪貼簿", Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) { }
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("確認清除資料？")
                .setMessage("你要確認要刪除資料嗎？\n\n刪除後會清除所有收入、支出、通知 hash 與重複判斷紀錄。\n\n刪除資料無法再復原，建議先備份資料。")
                .setNegativeButton("取消", null)
                .setPositiveButton("確認刪除", (d, w) -> { TransactionStore.clear(this); Toast.makeText(this, "已清除全部資料", Toast.LENGTH_SHORT).show(); showHome(); })
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
