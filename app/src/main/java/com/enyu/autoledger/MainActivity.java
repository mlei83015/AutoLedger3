package com.enyu.autoledger;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.Window;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

public class MainActivity extends Activity {
    public static final String ACTION_QUICK_EXPENSE = "com.enyu.autoledger.action.QUICK_EXPENSE";
    public static final String ACTION_QUICK_INCOME = "com.enyu.autoledger.action.QUICK_INCOME";
    public static final String ACTION_EDIT_WIDGET_PHOTO = "com.enyu.autoledger.action.EDIT_WIDGET_PHOTO";
    private static final int REQUEST_WIDGET_IMAGE = 1901;

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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_WIDGET_IMAGE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            AppSettings.setString(this, AppSettings.KEY_WIDGET_IMAGE_URI, uri.toString());
            showWidgetImageCropDialog(uri);
        }
    }

    private void handleLaunchIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (ACTION_QUICK_EXPENSE.equals(action)) {
            root.postDelayed(() -> showManual("expense"), 120);
        } else if (ACTION_QUICK_INCOME.equals(action)) {
            root.postDelayed(() -> showManual("income"), 120);
        } else if (ACTION_EDIT_WIDGET_PHOTO.equals(action)) {
            root.postDelayed(() -> showWidgetImageSettingsDialog(), 160);
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
        nav.setBackgroundColor(isDarkMode() ? 0xFF111923 : 0xFFFFFFFF);
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

    private boolean isDarkMode() {
        boolean systemDark = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        return AppSettings.getBool(this, AppSettings.KEY_DARK_MODE, systemDark);
    }

    private void applyModeColors() {
        boolean dark = isDarkMode();
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
            w.setNavigationBarColor(isDarkMode() ? 0xFF111923 : 0xFFFFFFFF);
            if (Build.VERSION.SDK_INT >= 23) {
                int flags = w.getDecorView().getSystemUiVisibility();
                if (isDarkMode()) {
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
        int baseBudget = AppSettings.getMonthlyBudget(this);
        int extra = AppSettings.getMonthlyExtra(this);
        int budget = AppSettings.getMonthlyUsableBudget(this);
        int spent = TransactionStore.monthExpense(this);
        int remain = Math.max(0, budget - spent);
        texts.addView(text("本月可花預算", 16, TEXT, true));
        texts.addView(text("基本 " + TransactionStore.money(baseBudget) + (extra > 0 ? "＋加回 " + TransactionStore.money(extra) : "") + "，還能花 " + TransactionStore.money(remain), 13, MUTED, false));
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        Button edit = smallChip("修改", CHIP, ORANGE);
        edit.setOnClickListener(v -> showBudgetDialog());
        row.addView(edit, new LinearLayout.LayoutParams(dp(86), dp(44)));
        return row;
    }

    private void showBudgetDialog() {
        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        LinearLayout panel = dialogPanel(dp(30));
        panel.addView(text("設定本月可花預算", 21, TEXT, true), marginLp(-1, -2, 0, 0, 0, dp(8)));
        panel.addView(text("每個月圓形圖會重新從這個預算開始計算；收入預設只加到上方全部餘額，若是獎金或中獎，可在新增收入時勾選加回本月可用預算。", 14, TEXT, false), marginLp(-1, -2, 0, 0, 0, dp(12)));
        final EditText input = edit("例如：10000", true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(AppSettings.getMonthlyBudget(this)));
        input.setSelectAllOnFocus(true);
        input.setTextSize(18);
        panel.addView(input, marginLp(-1, dp(56), 0, 0, 0, dp(14)));

        LinearLayout actions = dialogActionsRow();
        Button cancel = pill("取消", CHIP, TEXT);
        Button save = bigSave("儲存");
        actions.addView(cancel, marginLp(0, dp(50), 0, 0, dp(6), 0, 1));
        actions.addView(save, marginLp(0, dp(50), dp(6), 0, 0, 0, 1));
        panel.addView(actions);

        cancel.setOnClickListener(v -> dialog.dismiss());
        save.setOnClickListener(v -> {
            int amount = 0;
            try { amount = Integer.parseInt(input.getText().toString().replace(",", "").trim()); } catch (Exception ignored) { }
            if (amount <= 0) {
                Toast.makeText(this, "預算要大於 0", Toast.LENGTH_SHORT).show();
                return;
            }
            AppSettings.setMonthlyBudget(this, amount);
            Toast.makeText(this, "已設定本月預算 " + TransactionStore.money(amount), Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            refreshCurrent();
        });
        showCustomDialog(dialog, panel);
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

        int budgetForMonth = AppSettings.getMonthlyUsableBudget(this);
        int balance = TransactionStore.totalBalance(this);
        LinearLayout balanceCard = new LinearLayout(this);
        balanceCard.setOrientation(LinearLayout.VERTICAL);
        balanceCard.setPadding(dp(18), dp(12), dp(18), dp(12));
        balanceCard.setBackground(roundGradient(ORANGE, 0xFFFF9A2E, dp(16)));
        TextView b1 = text("剩餘餘額  ◉", 14, 0xFFFFFFFF, true);
        TextView b2 = text(TransactionStore.money(balance), 32, 0xFFFFFFFF, true);
        TextView b3 = text("全部餘額＝預算＋收入－支出｜本月可用 " + TransactionStore.money(budgetForMonth) + "，點此修改", 12, 0xFFFFF6EC, false);
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
        int budget = Math.max(1, AppSettings.getMonthlyUsableBudget(this));
        int remaining = Math.max(0, budget - monthExpense);
        DonutChartView donut = new DonutChartView(this);
        donut.setDarkMode(isDarkMode());
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
        studentTip.setBackground(round(isDarkMode() ? 0xFF1B2735 : 0xFFFFF4E8, dp(16), BORDER));
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
        monthCountCard.setBackground(round(isDarkMode() ? 0xFF151F2A : 0xFFFFFFFF, dp(16), BORDER));
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
        String rowIcon = !empty(tx.icon) ? tx.icon : (income ? "💰" : iconFor(tx.category));
        int iconBg = iconBgFor(tx);
        TextView ic = text(rowIcon, 22, TEXT, false);
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
        String cat = cleanCategory(tx.category);
        if (!cat.isEmpty()) return trimUi(cat, 12);
        String merchant = tx.merchant == null ? "" : tx.merchant.trim();
        if (!merchant.isEmpty() && !looksLikeAutoSourceName(merchant)) return trimUi(merchant, 12);
        return "未分類";
    }

    private String recordSubtitle(Transaction tx) {
        if (tx == null) return "";
        String type = cleanCategory(tx.category);
        if (type.isEmpty()) type = "未分類";
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
        if (tx == null) return "";
        if (tx.hash != null && tx.hash.startsWith("manual-")) return "手動新增";
        String stored = compactForUi((tx.source == null ? "" : tx.source) + " " + (tx.merchant == null ? "" : tx.merchant));
        if (stored.isEmpty()) return "自動通知";
        return trimUi(stored, 10);
    }

    private String cleanCategory(String c) {
        String x = c == null ? "" : c.trim();
        if (x.equals("未分類")) return "";
        return x;
    }

    private boolean looksLikeAutoSourceName(String s) {
        String x = compactForUi(s).toLowerCase(Locale.ROOT);
        return x.contains("line") || x.contains("載具") || x.contains("發票") || x.contains("google") || x.contains("銀行") || x.contains("wallet") || x.contains("通知");
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

    private int iconBgFor(Transaction tx) {
        boolean income = tx != null && "income".equals(tx.direction);
        String c = tx == null ? "" : tx.category;
        if (income) return isDarkMode() ? 0xFF183326 : 0xFFEAF8F0;
        if (c == null || c.trim().isEmpty() || c.equals("未分類")) return isDarkMode() ? 0xFF24303D : 0xFFEAF8FF;
        if (c.contains("餐") || c.contains("飲") || c.contains("茶") || c.contains("咖啡")) return isDarkMode() ? 0xFF332529 : 0xFFFFF0F1;
        if (c.contains("交通") || c.contains("捷運") || c.contains("停車") || c.contains("加油")) return isDarkMode() ? 0xFF1F3142 : 0xFFEAF4FF;
        if (c.contains("遊戲") || c.contains("娛樂")) return isDarkMode() ? 0xFF2D2843 : 0xFFF2EFFF;
        if (c.contains("禮物") || c.contains("紅包")) return isDarkMode() ? 0xFF3A2732 : 0xFFFFEEF7;
        if (c.contains("購") || c.contains("超商") || c.contains("用品")) return isDarkMode() ? 0xFF263326 : 0xFFEFF9EF;
        return isDarkMode() ? 0xFF263140 : 0xFFF1F7FF;
    }

    private String iconFor(String cat) {
        String c = cat == null ? "" : cat;
        if (c.contains("茶") || c.contains("飲料") || c.contains("咖啡")) return "🥤";
        if (c.contains("餐") || c.contains("早餐") || c.contains("午餐") || c.contains("晚餐")) return "🍴";
        if (c.contains("交通") || c.contains("捷運") || c.contains("停車") || c.contains("加油")) return "🚌";
        if (c.contains("超商")) return "🏪";
        if (c.contains("購") || c.contains("用品")) return "🛍";
        if (c.contains("訂閱")) return "▶";
        if (c.contains("遊戲") || c.contains("娛樂")) return "🎮";
        if (c.contains("禮物") || c.contains("紅包")) return "🎁";
        if (c.contains("醫") || c.contains("藥")) return "💊";
        if (c.contains("學") || c.contains("書")) return "📚";
        if (c.contains("旅")) return "✈️";
        if (c.contains("運動") || c.contains("健身")) return "🏋️";
        if (c.contains("薪") || c.contains("收入") || c.contains("打工") || c.contains("零用錢")) return "💰";
        return "🧾";
    }

    private void showManual(String direction) {
        tab = 1;
        applyModeColors();
        manualDirection = direction == null ? "expense" : direction;
        final boolean startIncome = "income".equals(manualDirection);

        ScrollView scroll = pageBase();
        LinearLayout box = pageBox(scroll);
        box.setPadding(dp(16), dp(8), dp(16), dp(10));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 30, TEXT, false);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> showHome());
        top.addView(back, new LinearLayout.LayoutParams(dp(38), dp(38)));
        TextView title = text("手動新增", 20, TEXT, true);
        title.setGravity(Gravity.CENTER);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView more = text("⋯", 24, MUTED, true);
        more.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        top.addView(more, new LinearLayout.LayoutParams(dp(38), dp(38)));
        box.addView(top, marginLp(-1, -2, 0, 0, 0, dp(8)));

        LinearLayout seg = new LinearLayout(this);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        Button expenseBtn = pill("↓  支出", !startIncome ? TEAL : CHIP, !startIncome ? 0xFFFFFFFF : TEXT);
        Button incomeBtn = pill("↑  收入", startIncome ? PURPLE : CHIP, startIncome ? 0xFFFFFFFF : TEXT);
        expenseBtn.setOnClickListener(v -> showManual("expense"));
        incomeBtn.setOnClickListener(v -> showManual("income"));
        seg.addView(expenseBtn, marginLp(0, dp(48), 0, 0, dp(8), 0, 1));
        seg.addView(incomeBtn, marginLp(0, dp(48), dp(8), 0, 0, 0, 1));
        box.addView(seg, marginLp(-1, -2, 0, 0, 0, dp(10)));

        final EditText amountInput = edit("金額，例如 120", true);
        amountInput.setTextSize(24);
        amountInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        amountInput.setTextColor(TEXT);
        box.addView(label("金額"));
        box.addView(amountInput, marginLp(-1, dp(50), 0, 0, 0, dp(8)));

        final EditText categoryInput = edit(startIncome ? "分類，例如 薪水、零用錢" : "分類，例如 餐飲、交通", false);
        box.addView(label("分類"));
        box.addView(categoryInput, marginLp(-1, dp(48), 0, 0, 0, dp(8)));

        final EditText merchantInput = edit(startIncome ? "來源，例如 打工、家人、朋友" : "店家 / 項目，例如 早餐、全家、捷運", false);
        box.addView(label(startIncome ? "來源" : "店家 / 項目"));
        box.addView(merchantInput, marginLp(-1, dp(48), 0, 0, 0, dp(8)));

        final String[] noteValue = new String[]{""};
        final TextView noteChip = smallChip("＋ 備註（可選）", CHIP, MUTED);
        noteChip.setGravity(Gravity.CENTER_VERTICAL);
        noteChip.setPadding(dp(14), 0, dp(14), 0);
        noteChip.setOnClickListener(v -> showNoteDialog(noteValue, noteChip));
        box.addView(noteChip, marginLp(-1, dp(42), 0, 0, 0, dp(8)));

        final long[] selectedTime = new long[]{System.currentTimeMillis()};
        LinearLayout dateCard = new LinearLayout(this);
        dateCard.setOrientation(LinearLayout.HORIZONTAL);
        dateCard.setGravity(Gravity.CENTER_VERTICAL);
        dateCard.setPadding(dp(12), 0, dp(12), 0);
        dateCard.setBackground(round(CARD, dp(18), BORDER));
        TextView calIcon = text("📅", 20, TEXT, false);
        calIcon.setGravity(Gravity.CENTER);
        dateCard.addView(calIcon, new LinearLayout.LayoutParams(dp(34), -1));
        TextView dateValue = text(formatFullDateMinute(selectedTime[0]), 18, TEXT, true);
        dateValue.setSingleLine(true);
        dateCard.addView(dateValue, new LinearLayout.LayoutParams(0, -2, 1));
        TextView dateArrow = text("›", 25, MUTED, true);
        dateArrow.setGravity(Gravity.CENTER);
        dateCard.addView(dateArrow, new LinearLayout.LayoutParams(dp(24), -1));
        dateCard.setOnClickListener(v -> showDateTimePicker(selectedTime, dateValue));
        box.addView(label("日期 / 時間"));
        box.addView(dateCard, marginLp(-1, dp(50), 0, 0, 0, dp(10)));

        TextView quickTitle = text("快速常用項目", 13, MUTED, true);
        quickTitle.setPadding(0, 0, 0, dp(4));
        box.addView(quickTitle);
        addPresetHorizontal(box, startIncome, categoryInput, merchantInput);

        final Switch addIncomeToBudget = new Switch(this);
        if (startIncome) {
            LinearLayout bonusRow = new LinearLayout(this);
            bonusRow.setOrientation(LinearLayout.HORIZONTAL);
            bonusRow.setGravity(Gravity.CENTER_VERTICAL);
            bonusRow.setPadding(dp(12), dp(6), dp(8), dp(6));
            bonusRow.setBackground(round(isDarkMode() ? 0xFF172538 : 0xFFEFFAFF, dp(18), BORDER));
            TextView bonusText = text("加回本月可用預算", 13, TEXT, true);
            bonusRow.addView(bonusText, new LinearLayout.LayoutParams(0, -2, 1));
            bonusRow.addView(addIncomeToBudget);
            box.addView(bonusRow, marginLp(-1, dp(44), 0, dp(8), 0, dp(8)));
        }

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
            String note = noteValue[0] == null ? "" : noteValue[0].trim();
            if (category.isEmpty()) category = income ? "收入" : "未分類";
            if (merchant.isEmpty()) merchant = category;
            Transaction tx = new Transaction(selectedTime[0], amount, income ? "income" : "expense", "手動新增", merchant, category, note, "manual-" + selectedTime[0] + "-" + System.currentTimeMillis());
            TransactionStore.add(this, tx);
            if (income && addIncomeToBudget.isChecked()) {
                AppSettings.addMonthlyExtra(this, amount);
                Toast.makeText(this, "已新增收入，並加回本月可用預算", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "已新增" + (income ? "收入 " : "支出 ") + TransactionStore.money(amount), Toast.LENGTH_SHORT).show();
            }
            showHome();
        });
        box.addView(save, marginLp(-1, dp(54), 0, dp(12), 0, 0));

        setPage(scroll);
    }

    private void showNoteDialog(final String[] noteValue, final TextView noteChip) {
        final EditText input = edit("備註 / 用在哪裡", false);
        input.setSingleLine(false);
        input.setMinLines(3);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setText(noteValue[0]);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(16), dp(20), dp(14));
        panel.setBackground(round(CARD, dp(24), BORDER));
        panel.addView(text("備註", 20, TEXT, true));
        panel.addView(text("可留空，之後點紀錄也能修改。", 13, MUTED, false), marginLp(-1, -2, 0, dp(2), 0, dp(10)));
        panel.addView(input, marginLp(-1, dp(112), 0, 0, 0, dp(12)));
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.RIGHT);
        Button cancel = dialogBtn("取消");
        Button save = dialogBtn("儲存");
        actions.addView(cancel, marginLp(dp(92), dp(44), 0, 0, dp(8), 0));
        actions.addView(save, marginLp(dp(92), dp(44), dp(8), 0, 0, 0));
        panel.addView(actions);
        AlertDialog dialog = new AlertDialog.Builder(this).create();
        dialog.setView(panel);
        cancel.setOnClickListener(v -> dialog.dismiss());
        save.setOnClickListener(v -> {
            noteValue[0] = input.getText().toString().trim();
            noteChip.setText(noteValue[0].isEmpty() ? "＋ 備註（可選）" : "備註：" + shortText(noteValue[0], 18));
            dialog.dismiss();
        });
        dialog.setOnShowListener(d -> {
            Window w = dialog.getWindow();
            if (w != null) w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        });
        dialog.show();
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

    private void addPresetHorizontal(LinearLayout box, boolean income, EditText category, EditText merchant) {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        List<String> presets = income ? AppSettings.getQuickIncome(this) : AppSettings.getQuickExpense(this);
        if (presets == null || presets.isEmpty()) {
            presets = new ArrayList<>();
            String[] defaults = income ? new String[]{"零用錢", "薪水", "打工", "退款", "紅包", "獎金"} : new String[]{"餐飲", "交通", "飲料", "停車", "全聯", "早餐", "午餐", "晚餐"};
            for (String d : defaults) presets.add(d);
        }
        for (String line : presets) {
            String[] p = line.split("\\|");
            final String name = p.length > 0 ? p[0].trim() : line.trim();
            if (name.isEmpty()) continue;
            final String presetCategory = p.length > 2 ? p[2].trim() : (income ? guessIncomeCategory(name) : guessCategory(name));
            Button chip = smallChip(name, income ? 0xFFF2F0FF : 0xFFFFF3EA, income ? PURPLE : ORANGE);
            chip.setTextSize(12);
            chip.setMinHeight(dp(36));
            chip.setPadding(dp(10), 0, dp(10), 0);
            chip.setOnClickListener(v -> {
                category.setText(presetCategory);
                merchant.setText(name);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(86), dp(38));
            lp.setMargins(0, 0, dp(8), 0);
            row.addView(chip, lp);
        }
        hsv.addView(row, new HorizontalScrollView.LayoutParams(-2, dp(40)));
        box.addView(hsv, marginLp(-1, dp(42), 0, 0, 0, dp(4)));
    }

    private String shortText(String value, int max) {
        if (value == null) return "";
        String v = value.trim().replace("\n", " ");
        return v.length() <= max ? v : v.substring(0, max) + "…";
    }

    private Button dialogBtn(String text) {
        Button b = smallChip(text, CARD, TEXT);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setMinHeight(dp(42));
        return b;
    }

    private void addPresetChips(LinearLayout box, boolean income, EditText amount, EditText category, EditText merchant, EditText note) {
        List<Button> chips = new ArrayList<>();
        List<String> presets = income ? AppSettings.getQuickIncome(this) : AppSettings.getQuickExpense(this);
        for (String line : presets) {
            String[] p = line.split("\\|");
            final String name = p.length > 0 ? p[0].trim() : line.trim();
            final String presetCategory = p.length > 2 ? p[2].trim() : (income ? guessIncomeCategory(name) : guessCategory(name));
            if (name.isEmpty()) continue;
            Button chip = presetCard(name, income ? 0xFFF2F0FF : 0xFFFFF3EA, income ? PURPLE : ORANGE);
            chip.setOnClickListener(v -> {
                merchant.setText(name);
                category.setText(presetCategory);
                if (note.getText().toString().trim().isEmpty()) note.setText(name);
            });
            chips.add(chip);
        }
        addChipGrid(box, chips, 3);
    }

    private String guessIncomeCategory(String s) {
        if (s == null) return "收入";
        if (s.contains("薪")) return "薪水";
        if (s.contains("零用")) return "零用錢";
        if (s.contains("打工")) return "打工";
        if (s.contains("退")) return "退款";
        if (s.contains("紅包")) return "紅包";
        return s.trim().isEmpty() ? "收入" : s.trim();
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
        b.setSingleLine(true);
        b.setMaxLines(1);
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
        typeTabs.setBackground(round(isDarkMode() ? 0xFF202933 : 0xFFF2F3F5, dp(18), BORDER));
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
        int budget = Math.max(1, AppSettings.getMonthlyUsableBudget(this) * reportMonthFactor());

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
        donut.setDarkMode(isDarkMode());
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
        Button b = smallChip(s, selected ? 0xFFFFCC55 : (isDarkMode() ? 0xFF1B2530 : 0xFFF7F7F8), selected ? 0xFF1F2329 : TEXT);
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
        panel.setBackground(round(isDarkMode() ? 0xFF1E242D : 0xFFFFFFFF, dp(20), BORDER));

        LinearLayout closeRow = new LinearLayout(this);
        closeRow.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        TextView close = text("✕", 22, MUTED, true);
        close.setGravity(Gravity.CENTER);
        close.setBackground(round(isDarkMode() ? 0xFF2A333F : 0xFFF2F2F2, dp(18), BORDER));
        close.setOnClickListener(v -> closeDialogs());
        closeRow.addView(close, new LinearLayout.LayoutParams(dp(42), dp(42)));
        panel.addView(closeRow, marginLp(-1, -2, 0, 0, 0, dp(4)));

        LinearLayout profile = new LinearLayout(this);
        profile.setGravity(Gravity.CENTER_VERTICAL);
        TextView avatar = text("●", 48, isDarkMode() ? 0xFFFFFFFF : 0xFF222222, true);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(round(isDarkMode() ? 0xFF2A333F : 0xFFF2F2F2, dp(42), BORDER));
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
        panel.addView(sideMenuButton("💸", "欠款紀錄", v -> { closeDialogs(); showDebtTracker(); }));
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

        ScrollView menuScroll = new ScrollView(this);
        menuScroll.setFillViewport(false);
        menuScroll.setVerticalScrollBarEnabled(false);
        if (Build.VERSION.SDK_INT >= 9) menuScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        menuScroll.addView(panel, new ScrollView.LayoutParams(-1, -2));
        AlertDialog dialog = new AlertDialog.Builder(this).setView(menuScroll).create();
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
        Button b = smallChip(icon + "   " + label, isDarkMode() ? 0xFF222A34 : 0xFFF7F7F8, TEXT);
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
        showRoundedInfoDialog(title, body + "\n\n這個會先放進功能入口，之後可以再做成正式功能。", "知道了", null, null, null);
    }


    private String carrierSubtitle() {
        String carrier = BarcodeUtil.normalizeCarrier(AppSettings.getString(this, AppSettings.KEY_CARRIER_BARCODE, ""));
        return carrier.isEmpty() ? "輸入手機條碼號碼，桌面小工具會自動產生條碼" : "目前載具：" + carrier + "，會顯示在載具小工具";
    }

    private void showCarrierBarcodeDialog() {
        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(18), dp(20), dp(14));
        panel.setBackground(round(CARD, dp(26), BORDER));
        panel.addView(text("設定載具條碼", 21, TEXT, true), marginLp(-1, -2, 0, 0, 0, dp(8)));
        panel.addView(text("不用綁定財政部帳號。只要輸入手機條碼號碼，App 會在桌面小工具產生條碼，方便結帳時掃描。", 14, TEXT, false));
        TextView warn = text("注意：這只是顯示條碼，不會自動登入載具或查發票。", 13, MUTED, false);
        warn.setPadding(0, dp(8), 0, dp(8));
        panel.addView(warn);
        final EditText input = edit("例如：/AB12CDE", false);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        input.setText(BarcodeUtil.normalizeCarrier(AppSettings.getString(this, AppSettings.KEY_CARRIER_BARCODE, "")));
        input.setSelectAllOnFocus(true);
        input.setTextSize(18);
        panel.addView(input, marginLp(-1, dp(52), 0, dp(4), 0, dp(14)));
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button clear = pill("清除", CHIP, GREEN);
        Button cancel = pill("取消", CHIP, TEXT);
        Button save = bigSave("儲存");
        actions.addView(clear, marginLp(0, dp(48), 0, 0, dp(4), 0, 1));
        actions.addView(cancel, marginLp(0, dp(48), dp(4), 0, dp(4), 0, 1));
        actions.addView(save, marginLp(0, dp(48), dp(4), 0, 0, 0, 1));
        panel.addView(actions);
        clear.setOnClickListener(v -> {
            AppSettings.setString(this, AppSettings.KEY_CARRIER_BARCODE, "");
            try { CarrierBalanceWidgetProvider.updateAll(this); } catch (Exception ignored) { }
            try { CarrierPhotoWidgetProvider.updateAll(this); } catch (Exception ignored) { }
            Toast.makeText(this, "已清除載具條碼", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            showSettings();
        });
        cancel.setOnClickListener(v -> dialog.dismiss());
        save.setOnClickListener(v -> {
            String code = BarcodeUtil.normalizeCarrier(input.getText().toString());
            if (code.length() < 5) {
                Toast.makeText(this, "載具號碼看起來太短，請確認後再儲存", Toast.LENGTH_LONG).show();
                return;
            }
            AppSettings.setString(this, AppSettings.KEY_CARRIER_BARCODE, code);
            try { CarrierBalanceWidgetProvider.updateAll(this); } catch (Exception ignored) { }
            try { CarrierPhotoWidgetProvider.updateAll(this); } catch (Exception ignored) { }
            Toast.makeText(this, "已儲存載具條碼，點桌面條碼可複製", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            showSettings();
        });
        showCustomDialog(dialog, panel);
    }


    private String widgetImageSubtitle() {
        String file = AppSettings.getString(this, AppSettings.KEY_WIDGET_IMAGE_FILE, "");
        return (file == null || file.trim().isEmpty() ? "尚未設定圖片" : "已設定並裁切圖片") + "｜點小工具圖片也可回來修改";
    }

    private void showWidgetImageSettingsDialog() {
        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        LinearLayout panel = dialogPanel(dp(30));
        panel.addView(text("圖片小工具設定", 21, TEXT, true), marginLp(-1, -2, 0, 0, 0, dp(8)));
        panel.addView(text("選一張手機相簿圖片後，可以預覽裁切成固定 4:3 區塊。桌面小工具會固定版面，不會因手機寬度不同把按鈕擠歪。", 14, TEXT, false), marginLp(-1, -2, 0, 0, 0, dp(12)));

        TextView state = text(widgetImageSubtitle(), 14, MUTED, false);
        state.setPadding(dp(14), dp(10), dp(14), dp(10));
        state.setBackground(round(CHIP, dp(18), BORDER));
        panel.addView(state, marginLp(-1, -2, 0, 0, 0, dp(12)));

        LinearLayout actions = dialogActionsRow();
        Button choose = bigAction("選手機相簿圖片", 0xFF42C7E8, 0xFF4D8DFF);
        Button clear = pill("清除圖片", CHIP, EXPENSE_RED);
        actions.addView(choose, marginLp(0, dp(52), 0, 0, dp(6), 0, 1));
        actions.addView(clear, marginLp(0, dp(52), dp(6), 0, 0, 0, 1));
        panel.addView(actions, marginLp(-1, -2, 0, 0, 0, dp(8)));

        LinearLayout bottom = dialogActionsRow();
        Button close = pill("關閉", CHIP, TEXT);
        Button carrier = bigSave("設定載具");
        bottom.addView(close, marginLp(0, dp(50), 0, 0, dp(6), 0, 1));
        bottom.addView(carrier, marginLp(0, dp(50), dp(6), 0, 0, 0, 1));
        panel.addView(bottom);

        choose.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.setType("image/*");
            Intent chooser = Intent.createChooser(intent, "選擇小工具圖片");
            dialog.dismiss();
            startActivityForResult(chooser, REQUEST_WIDGET_IMAGE);
        });
        clear.setOnClickListener(v -> {
            AppSettings.setString(this, AppSettings.KEY_WIDGET_IMAGE_URI, "");
            AppSettings.setString(this, AppSettings.KEY_WIDGET_IMAGE_FILE, "");
            try { CarrierPhotoWidgetProvider.updateAll(this); } catch (Exception ignored) { }
            Toast.makeText(this, "已清除小工具圖片", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            showSettings();
        });
        close.setOnClickListener(v -> dialog.dismiss());
        carrier.setOnClickListener(v -> { dialog.dismiss(); showCarrierBarcodeDialog(); });
        showCustomDialog(dialog, panel);
    }

    private Bitmap decodeWidgetSource(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) return null;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, opts);
            try { is.close(); } catch (Exception ignored) { }
            int sample = 1;
            int max = Math.max(opts.outWidth, opts.outHeight);
            while (max / sample > 1600) sample *= 2;
            BitmapFactory.Options opts2 = new BitmapFactory.Options();
            opts2.inSampleSize = Math.max(1, sample);
            opts2.inPreferredConfig = Bitmap.Config.ARGB_8888;
            InputStream is2 = getContentResolver().openInputStream(uri);
            Bitmap bm = BitmapFactory.decodeStream(is2, null, opts2);
            try { if (is2 != null) is2.close(); } catch (Exception ignored) { }
            return bm;
        } catch (Exception e) {
            return null;
        }
    }

    private Bitmap cropWidgetBitmap(Bitmap src, float cx, float cy, float zoom) {
        if (src == null) return null;
        int sw = src.getWidth();
        int sh = src.getHeight();
        float aspect = 4f / 3f;
        float baseW = Math.min(sw, sh * aspect);
        float baseH = baseW / aspect;
        zoom = Math.max(1f, Math.min(3f, zoom));
        float cw = baseW / zoom;
        float ch = baseH / zoom;
        float centerX = Math.max(0f, Math.min(1f, cx)) * sw;
        float centerY = Math.max(0f, Math.min(1f, cy)) * sh;
        float left = centerX - cw / 2f;
        float top = centerY - ch / 2f;
        if (left < 0) left = 0;
        if (top < 0) top = 0;
        if (left + cw > sw) left = sw - cw;
        if (top + ch > sh) top = sh - ch;
        Rect srcRect = new Rect(Math.round(left), Math.round(top), Math.round(left + cw), Math.round(top + ch));
        Bitmap out = Bitmap.createBitmap(1000, 750, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        canvas.drawColor(Color.TRANSPARENT);
        canvas.drawBitmap(src, srcRect, new Rect(0, 0, 1000, 750), null);
        return out;
    }

    private void refreshCropPreview(ImageView preview, Bitmap src, float[] cx, float[] cy, float[] zoom) {
        try {
            Bitmap crop = cropWidgetBitmap(src, cx[0], cy[0], zoom[0]);
            if (crop != null) preview.setImageBitmap(crop);
        } catch (Exception ignored) { }
    }

    private String saveWidgetPhoto(Bitmap bitmap) {
        if (bitmap == null) return "";
        try {
            File f = new File(getFilesDir(), "autoledger_widget_photo.png");
            FileOutputStream fos = new FileOutputStream(f);
            bitmap.compress(Bitmap.CompressFormat.PNG, 92, fos);
            fos.flush();
            fos.close();
            return f.getAbsolutePath();
        } catch (Exception e) {
            return "";
        }
    }

    private void showWidgetImageCropDialog(Uri uri) {
        Bitmap src = decodeWidgetSource(uri);
        if (src == null) {
            Toast.makeText(this, "讀不到這張圖片，請改從相簿選一張圖片", Toast.LENGTH_LONG).show();
            showSettings();
            return;
        }
        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        LinearLayout panel = dialogPanel(dp(30));
        panel.addView(text("裁切小工具圖片", 21, TEXT, true), marginLp(-1, -2, 0, 0, 0, dp(4)));
        panel.addView(text("用手指拖曳照片、雙指縮放。白色框看到的 4:3 區塊，就是桌面小工具會顯示的圖片。", 13, MUTED, false), marginLp(-1, -2, 0, 0, 0, dp(10)));

        PhotoCropView cropView = new PhotoCropView(this, src);
        cropView.setBackground(round(CHIP, dp(22), BORDER));
        panel.addView(cropView, marginLp(-1, dp(300), 0, 0, 0, dp(12)));

        TextView hint = text("提示：圖片不會被壓扁，只會依照你框選的位置裁切。", 12, MUTED, false);
        hint.setGravity(Gravity.CENTER);
        panel.addView(hint, marginLp(-1, -2, 0, 0, 0, dp(8)));

        LinearLayout actions = dialogActionsRow();
        Button cancel = pill("取消", CHIP, TEXT);
        Button save = bigSave("✓ 儲存圖片");
        actions.addView(cancel, marginLp(0, dp(52), 0, dp(8), dp(6), 0, 1));
        actions.addView(save, marginLp(0, dp(52), dp(6), dp(8), 0, 0, 1));
        panel.addView(actions);
        cancel.setOnClickListener(v -> { dialog.dismiss(); showSettings(); });
        save.setOnClickListener(v -> {
            Bitmap crop = cropView.getCroppedBitmap(1200, 900);
            String path = saveWidgetPhoto(crop);
            if (path.isEmpty()) {
                Toast.makeText(this, "儲存圖片失敗，請換一張圖試試", Toast.LENGTH_LONG).show();
                return;
            }
            AppSettings.setString(this, AppSettings.KEY_WIDGET_IMAGE_FILE, path);
            try { CarrierPhotoWidgetProvider.updateAll(this); } catch (Exception ignored) { }
            Toast.makeText(this, "已設定小工具圖片，點桌面圖片可再次修改", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            showSettings();
        });
        showCustomDialog(dialog, panel);
    }


    private class PhotoCropView extends View {
        private final Bitmap src;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        private final Paint shadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix matrix = new Matrix();
        private final RectF cropRect = new RectF();
        private final RectF imageRect = new RectF();
        private final ScaleGestureDetector scaleDetector;
        private float scale = 1f;
        private float minScale = 1f;
        private float tx = 0f;
        private float ty = 0f;
        private float lastX = 0f;
        private float lastY = 0f;
        private boolean initialized = false;

        PhotoCropView(Context context, Bitmap source) {
            super(context);
            src = source;
            setWillNotDraw(false);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            shadePaint.setColor(0x99000000);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(dp(2));
            borderPaint.setColor(0xFFFFFFFF);
            scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override public boolean onScale(ScaleGestureDetector detector) {
                    float old = scale;
                    scale = Math.max(minScale, Math.min(minScale * 4.5f, scale * detector.getScaleFactor()));
                    float factor = scale / old;
                    float fx = detector.getFocusX();
                    float fy = detector.getFocusY();
                    tx = fx - (fx - tx) * factor;
                    ty = fy - (fy - ty) * factor;
                    clampImage();
                    invalidate();
                    return true;
                }
            });
        }

        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            updateCropRect(w, h);
            initialized = false;
            initImageTransform();
        }

        private void updateCropRect(int w, int h) {
            float pad = dp(14);
            float maxW = Math.max(1, w - pad * 2);
            float maxH = Math.max(1, h - pad * 2);
            float cw = maxW;
            float ch = cw * 3f / 4f;
            if (ch > maxH) {
                ch = maxH;
                cw = ch * 4f / 3f;
            }
            float left = (w - cw) / 2f;
            float top = (h - ch) / 2f;
            cropRect.set(left, top, left + cw, top + ch);
        }

        private void initImageTransform() {
            if (src == null || src.getWidth() <= 0 || src.getHeight() <= 0 || cropRect.width() <= 0 || initialized) return;
            minScale = Math.max(cropRect.width() / src.getWidth(), cropRect.height() / src.getHeight());
            scale = minScale;
            tx = cropRect.centerX() - src.getWidth() * scale / 2f;
            ty = cropRect.centerY() - src.getHeight() * scale / 2f;
            clampImage();
            initialized = true;
        }

        private void updateMatrix() {
            matrix.reset();
            matrix.postScale(scale, scale);
            matrix.postTranslate(tx, ty);
            imageRect.set(0, 0, src == null ? 1 : src.getWidth(), src == null ? 1 : src.getHeight());
            matrix.mapRect(imageRect);
        }

        private void clampImage() {
            if (src == null) return;
            if (cropRect.width() <= 0 || cropRect.height() <= 0) return;
            minScale = Math.max(cropRect.width() / src.getWidth(), cropRect.height() / src.getHeight());
            if (scale < minScale) scale = minScale;
            float iw = src.getWidth() * scale;
            float ih = src.getHeight() * scale;
            if (iw <= cropRect.width()) tx = cropRect.centerX() - iw / 2f;
            else {
                float minTx = cropRect.right - iw;
                float maxTx = cropRect.left;
                if (tx < minTx) tx = minTx;
                if (tx > maxTx) tx = maxTx;
            }
            if (ih <= cropRect.height()) ty = cropRect.centerY() - ih / 2f;
            else {
                float minTy = cropRect.bottom - ih;
                float maxTy = cropRect.top;
                if (ty < minTy) ty = minTy;
                if (ty > maxTy) ty = maxTy;
            }
            updateMatrix();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (src == null) return;
            initImageTransform();
            updateMatrix();
            Path clip = new Path();
            RectF whole = new RectF(0, 0, getWidth(), getHeight());
            clip.addRoundRect(whole, dp(22), dp(22), Path.Direction.CW);
            int save = canvas.save();
            canvas.clipPath(clip);
            canvas.drawColor(isDarkMode() ? 0xFF111923 : 0xFFF7FCFF);
            canvas.drawBitmap(src, matrix, paint);
            canvas.drawRect(0, 0, getWidth(), cropRect.top, shadePaint);
            canvas.drawRect(0, cropRect.bottom, getWidth(), getHeight(), shadePaint);
            canvas.drawRect(0, cropRect.top, cropRect.left, cropRect.bottom, shadePaint);
            canvas.drawRect(cropRect.right, cropRect.top, getWidth(), cropRect.bottom, shadePaint);
            canvas.drawRoundRect(cropRect, dp(20), dp(20), borderPaint);
            canvas.restoreToCount(save);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            scaleDetector.onTouchEvent(event);
            if (event.getPointerCount() == 1 && !scaleDetector.isInProgress()) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    lastX = event.getX();
                    lastY = event.getY();
                    return true;
                } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                    float dx = event.getX() - lastX;
                    float dy = event.getY() - lastY;
                    tx += dx;
                    ty += dy;
                    lastX = event.getX();
                    lastY = event.getY();
                    clampImage();
                    invalidate();
                    return true;
                }
            }
            return true;
        }

        Bitmap getCroppedBitmap(int outW, int outH) {
            if (src == null) return null;
            clampImage();
            float left = (cropRect.left - tx) / scale;
            float top = (cropRect.top - ty) / scale;
            float right = (cropRect.right - tx) / scale;
            float bottom = (cropRect.bottom - ty) / scale;
            Rect srcRect = new Rect(
                    Math.max(0, Math.round(left)),
                    Math.max(0, Math.round(top)),
                    Math.min(src.getWidth(), Math.round(right)),
                    Math.min(src.getHeight(), Math.round(bottom))
            );
            if (srcRect.width() <= 0 || srcRect.height() <= 0) return null;
            Bitmap out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            c.drawColor(Color.TRANSPARENT);
            c.drawBitmap(src, srcRect, new Rect(0, 0, outW, outH), paint);
            return out;
        }
    }


    private void showWidgetInfoDialog() {
        showRoundedInfoDialog("桌面小工具", "V23 有三種桌面小工具：\n\n1. 簡易記帳小工具：顯示餘額、今日花費，點「支出／收入」快速新增。\n\n2. 載具＋記帳小工具：顯示載具條碼、餘額、今日花費、輸入金額入口。\n\n3. 圖片＋載具＋記帳小工具：最上方顯示你裁切好的圖片，中間顯示載具條碼，下方顯示餘額與支出 / 收入。點小工具圖片可回到 App 修改圖片。", "知道了", null, "圖片設定", v -> showWidgetImageSettingsDialog());
    }


    private View dialogSwitchRow(String title, String subtitle, String key, boolean def) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(6), dp(8), dp(2), dp(8));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(title, 16, TEXT, true));
        texts.addView(text(subtitle, 12, MUTED, false));
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        Switch sw = new Switch(this);
        sw.setChecked(AppSettings.getBool(this, key, def));
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> AppSettings.setBool(this, key, isChecked));
        row.addView(sw);
        return row;
    }

    private void showNotificationSettingsDialog() {
        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(18), dp(20), dp(14));
        panel.setBackground(round(CARD, dp(26), BORDER));
        panel.addView(text("鈴鐺通知設定", 21, TEXT, true), marginLp(-1, -2, 0, 0, 0, dp(8)));
        panel.addView(text("通知開關", 15, TEXT, true));
        panel.addView(dialogSwitchRow("每日昨日花費摘要", "每天固定時間提醒你昨天花多少", AppSettings.KEY_NOTIFY_DAILY, true));
        panel.addView(dialogSwitchRow("自動記帳完成通知", "每記到一筆就跳小通知", AppSettings.KEY_NOTIFY_AUTO_SAVED, true));
        panel.addView(dialogSwitchRow("預算提醒", "接近或超過本月預算時提醒", AppSettings.KEY_NOTIFY_BUDGET, true));
        panel.addView(dialogSwitchRow("重複資料提醒", "偵測到疑似同一筆時提醒你", AppSettings.KEY_NOTIFY_DUPLICATE, false));

        final EditText time = edit("09:00", false);
        time.setText(AppSettings.getString(this, AppSettings.KEY_DAILY_NOTIFY_TIME, "09:00"));
        time.setSingleLine(true);
        time.setTextSize(18);
        time.setInputType(InputType.TYPE_CLASS_TEXT);
        TextView timeLabel = text("每日摘要通知時間（24 小時制）", 14, TEXT, true);
        timeLabel.setPadding(0, dp(8), 0, dp(4));
        panel.addView(timeLabel);
        panel.addView(time, marginLp(-1, dp(52), 0, dp(2), 0, dp(8)));
        panel.addView(text("也可以到設定頁面調整 LINE Pay、載具、Google 錢包、銀行通知等偵測來源。", 13, MUTED, false));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button cancel = pill("取消", CHIP, TEXT);
        Button save = bigSave("儲存");
        actions.addView(cancel, marginLp(0, dp(50), 0, dp(12), dp(6), 0, 1));
        actions.addView(save, marginLp(0, dp(50), dp(6), dp(12), 0, 0, 1));
        panel.addView(actions);
        cancel.setOnClickListener(v -> dialog.dismiss());
        save.setOnClickListener(v -> {
            String raw = time.getText().toString().trim();
            if (!raw.matches("^([01]?\\d|2[0-3]):[0-5]\\d$")) {
                Toast.makeText(this, "時間格式請輸入 09:00 這種格式", Toast.LENGTH_LONG).show();
                return;
            }
            AppSettings.setString(this, AppSettings.KEY_DAILY_NOTIFY_TIME, raw);
            DailyReportScheduler.schedule(this);
            Toast.makeText(this, "已更新通知設定", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        showCustomDialog(dialog, panel);
    }


    private static class DebtEntry {
        String name;
        int amount;
        String note;
        long created;
    }

    private String safeDebtText(String s) {
        if (s == null) return "";
        return s.replace("|", " ").replace("\n", " ").trim();
    }

    private List<DebtEntry> loadDebts() {
        List<DebtEntry> list = new ArrayList<>();
        String raw = AppSettings.getString(this, AppSettings.KEY_DEBT_RECORDS, "");
        if (raw == null || raw.trim().isEmpty()) return list;
        for (String line : raw.split("\\n")) {
            String[] p = line.split("\\|", -1);
            if (p.length < 2) continue;
            DebtEntry e = new DebtEntry();
            e.name = p[0].trim().isEmpty() ? "未命名" : p[0].trim();
            try { e.amount = Math.max(0, Integer.parseInt(p[1].trim())); } catch (Exception ex) { e.amount = 0; }
            e.note = p.length >= 3 ? p[2].trim() : "";
            try { e.created = p.length >= 4 ? Long.parseLong(p[3].trim()) : System.currentTimeMillis(); } catch (Exception ex) { e.created = System.currentTimeMillis(); }
            if (e.amount > 0) list.add(e);
        }
        return list;
    }

    private void saveDebts(List<DebtEntry> list) {
        StringBuilder b = new StringBuilder();
        for (DebtEntry e : list) {
            if (e == null || e.amount <= 0) continue;
            if (b.length() > 0) b.append('\n');
            b.append(safeDebtText(e.name)).append('|')
                    .append(e.amount).append('|')
                    .append(safeDebtText(e.note)).append('|')
                    .append(e.created <= 0 ? System.currentTimeMillis() : e.created);
        }
        AppSettings.setString(this, AppSettings.KEY_DEBT_RECORDS, b.toString());
    }

    private int debtTotal(List<DebtEntry> list) {
        int total = 0;
        for (DebtEntry e : list) total += Math.max(0, e.amount);
        return total;
    }

    private void showDebtTracker() {
        applyModeColors();
        ScrollView scroll = pageBase();
        LinearLayout box = pageBox(scroll);
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 34, TEXT, true);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> showHome());
        titleRow.addView(back, new LinearLayout.LayoutParams(dp(52), dp(52)));
        TextView title = text("欠款紀錄", 23, TEXT, true);
        title.setGravity(Gravity.CENTER);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        SpaceFill(titleRow, dp(52));
        box.addView(titleRow, marginLp(-1, -2, 0, dp(4), 0, dp(10)));

        List<DebtEntry> debts = loadDebts();
        LinearLayout summary = section("目前別人欠你的錢");
        TextView total = text(TransactionStore.money(debtTotal(debts)), 34, GREEN, true);
        total.setPadding(0, dp(4), 0, dp(6));
        summary.addView(total);
        summary.addView(text("可以記錄同學、朋友或家人欠你的錢；對方還一部分時，按扣還款就會自動扣掉。", 14, MUTED, false));
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button add = bigSave("＋ 新增欠款");
        Button repay = pill("扣還款", CHIP, TEXT);
        actions.addView(add, marginLp(0, dp(54), 0, dp(14), dp(6), 0, 1));
        actions.addView(repay, marginLp(0, dp(54), dp(6), dp(14), 0, 0, 1));
        summary.addView(actions);
        box.addView(summary, marginLp(-1, -2, 0, 0, 0, dp(12)));

        LinearLayout listSec = section("欠款清單");
        if (debts.isEmpty()) {
            TextView empty = text("目前沒有欠款紀錄\n按「新增欠款」開始記錄。", 15, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(30), 0, dp(30));
            listSec.addView(empty);
        } else {
            for (int i = 0; i < debts.size(); i++) {
                final int index = i;
                DebtEntry e = debts.get(i);
                listSec.addView(debtRow(e, index), marginLp(-1, -2, 0, dp(4), 0, dp(4)));
            }
        }
        box.addView(listSec);
        add.setOnClickListener(v -> showDebtEditDialog(null, -1));
        repay.setOnClickListener(v -> showDebtRepayPicker());
        setPage(scroll);
    }

    private void SpaceFill(LinearLayout row, int width) {
        TextView spacer = text("", 1, TEXT, false);
        row.addView(spacer, new LinearLayout.LayoutParams(width, 1));
    }

    private View debtRow(DebtEntry e, int index) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setBackground(round(CHIP, dp(22), BORDER));
        TextView icon = text("💸", 25, TEXT, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(round(isDarkMode() ? 0xFF22303B : 0xFFF2FCFF, dp(18), BORDER));
        row.addView(icon, new LinearLayout.LayoutParams(dp(54), dp(54)));
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setPadding(dp(12), 0, 0, 0);
        mid.addView(text(e.name, 18, TEXT, true));
        String sub = e.note == null || e.note.isEmpty() ? "點一下可修改，長按快速扣還款" : e.note;
        mid.addView(text(sub, 13, MUTED, false));
        row.addView(mid, new LinearLayout.LayoutParams(0, -2, 1));
        TextView amount = text(TransactionStore.money(e.amount), 20, EXPENSE_RED, true);
        amount.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        row.addView(amount, new LinearLayout.LayoutParams(dp(120), -2));
        row.setOnClickListener(v -> showDebtEditDialog(e, index));
        row.setOnLongClickListener(v -> { showDebtRepayDialog(e, index); return true; });
        return row;
    }

    private void showDebtEditDialog(DebtEntry existing, int index) {
        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        LinearLayout panel = dialogPanel(dp(30));
        panel.addView(text(index >= 0 ? "修改欠款" : "新增欠款", 22, TEXT, true), marginLp(-1, -2, 0, 0, 0, dp(12)));
        final EditText name = edit("誰欠你錢，例如：阿明", false);
        final EditText amount = edit("欠多少，例如：1000", true);
        final EditText note = edit("備註，例如：午餐先墊", false);
        amount.setInputType(InputType.TYPE_CLASS_NUMBER);
        if (existing != null) {
            name.setText(existing.name);
            amount.setText(String.valueOf(existing.amount));
            note.setText(existing.note);
        }
        panel.addView(text("名字", 14, TEXT, true));
        panel.addView(name, marginLp(-1, dp(54), 0, dp(6), 0, dp(12)));
        panel.addView(text("金額", 14, TEXT, true));
        panel.addView(amount, marginLp(-1, dp(54), 0, dp(6), 0, dp(12)));
        panel.addView(text("備註", 14, TEXT, true));
        panel.addView(note, marginLp(-1, dp(54), 0, dp(6), 0, dp(16)));
        LinearLayout actions = dialogActionsRow();
        Button cancel = pill("取消", CHIP, TEXT);
        Button repay = pill("扣還款", CHIP, GREEN);
        Button save = bigSave("儲存");
        actions.addView(cancel, marginLp(0, dp(50), 0, 0, dp(6), 0, 1));
        if (index >= 0) actions.addView(repay, marginLp(0, dp(50), dp(6), 0, dp(6), 0, 1));
        actions.addView(save, marginLp(0, dp(50), dp(6), 0, 0, 0, 1));
        panel.addView(actions);
        cancel.setOnClickListener(v -> dialog.dismiss());
        repay.setOnClickListener(v -> { dialog.dismiss(); showDebtRepayDialog(existing, index); });
        save.setOnClickListener(v -> {
            int amt = 0;
            try { amt = Integer.parseInt(amount.getText().toString().replace(",", "").trim()); } catch (Exception ignored) { }
            if (name.getText().toString().trim().isEmpty() || amt <= 0) {
                Toast.makeText(this, "名字和金額都要填", Toast.LENGTH_SHORT).show();
                return;
            }
            List<DebtEntry> list = loadDebts();
            DebtEntry e = existing == null ? new DebtEntry() : existing;
            e.name = safeDebtText(name.getText().toString());
            e.amount = amt;
            e.note = safeDebtText(note.getText().toString());
            if (e.created <= 0) e.created = System.currentTimeMillis();
            if (index >= 0 && index < list.size()) list.set(index, e); else list.add(e);
            saveDebts(list);
            Toast.makeText(this, "已儲存欠款紀錄", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            showDebtTracker();
        });
        showCustomDialog(dialog, panel);
    }

    private void showDebtRepayPicker() {
        List<DebtEntry> list = loadDebts();
        if (list.isEmpty()) {
            Toast.makeText(this, "目前沒有欠款紀錄", Toast.LENGTH_SHORT).show();
            return;
        }
        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        LinearLayout panel = dialogPanel(dp(30));
        panel.addView(text("選擇要扣還款的人", 21, TEXT, true), marginLp(-1, -2, 0, 0, 0, dp(12)));
        for (int i = 0; i < list.size(); i++) {
            final int index = i;
            DebtEntry e = list.get(i);
            Button b = pill(e.name + "  " + TransactionStore.money(e.amount), CHIP, TEXT);
            b.setGravity(Gravity.CENTER_VERTICAL);
            b.setPadding(dp(16), 0, dp(16), 0);
            b.setOnClickListener(v -> { dialog.dismiss(); showDebtRepayDialog(e, index); });
            panel.addView(b, marginLp(-1, dp(50), 0, dp(4), 0, dp(6)));
        }
        Button close = pill("關閉", CHIP, TEXT);
        close.setOnClickListener(v -> dialog.dismiss());
        panel.addView(close, marginLp(-1, dp(50), 0, dp(10), 0, 0));
        showCustomDialog(dialog, panel);
    }

    private void showDebtRepayDialog(DebtEntry entry, int index) {
        if (entry == null || index < 0) return;
        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        LinearLayout panel = dialogPanel(dp(30));
        panel.addView(text("扣還款", 22, TEXT, true), marginLp(-1, -2, 0, 0, 0, dp(8)));
        panel.addView(text(entry.name + " 目前還欠你 " + TransactionStore.money(entry.amount), 15, MUTED, false), marginLp(-1, -2, 0, 0, 0, dp(14)));
        final EditText repay = edit("這次還多少，例如：100", true);
        repay.setInputType(InputType.TYPE_CLASS_NUMBER);
        panel.addView(repay, marginLp(-1, dp(56), 0, 0, 0, dp(16)));
        LinearLayout actions = dialogActionsRow();
        Button cancel = pill("取消", CHIP, TEXT);
        Button save = bigSave("扣除");
        actions.addView(cancel, marginLp(0, dp(50), 0, 0, dp(6), 0, 1));
        actions.addView(save, marginLp(0, dp(50), dp(6), 0, 0, 0, 1));
        panel.addView(actions);
        cancel.setOnClickListener(v -> dialog.dismiss());
        save.setOnClickListener(v -> {
            int paid = 0;
            try { paid = Integer.parseInt(repay.getText().toString().replace(",", "").trim()); } catch (Exception ignored) { }
            if (paid <= 0) {
                Toast.makeText(this, "還款金額要大於 0", Toast.LENGTH_SHORT).show();
                return;
            }
            List<DebtEntry> list = loadDebts();
            if (index < list.size()) {
                DebtEntry e = list.get(index);
                e.amount = Math.max(0, e.amount - paid);
                if (e.amount <= 0) {
                    list.remove(index);
                    Toast.makeText(this, "已還清，已移除這筆欠款", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "已扣除，剩下 " + TransactionStore.money(e.amount), Toast.LENGTH_SHORT).show();
                }
                saveDebts(list);
            }
            dialog.dismiss();
            showDebtTracker();
        });
        showCustomDialog(dialog, panel);
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
        widgetSec.addView(settingButton("圖片小工具設定", widgetImageSubtitle(), v -> showWidgetImageSettingsDialog()));
        widgetSec.addView(settingButton("桌面小工具說明", "有三種：簡易、載具記帳、圖片＋載具記帳", v -> showWidgetInfoDialog()));
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

        TextView version = text("AutoLedger V23", 12, MUTED, false);
        version.setGravity(Gravity.CENTER);
        version.setPadding(0, dp(16), 0, dp(10));
        box.addView(version);
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
        l.setBackground(round(CARD, dp(24), BORDER));
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
        e.setBackground(round(CARD, dp(18), BORDER));
        return e;
    }

    private Button bigAction(String text, int c1, int c2) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(0xFFFFFFFF);
        b.setTextSize(16);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setAllCaps(false);
        b.setSingleLine(true);
        b.setMaxLines(1);
        b.setBackground(roundGradient(c1, c2, dp(22)));
        return b;
    }

    private Button bigSave(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(0xFFFFFFFF);
        b.setTextSize(18);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setAllCaps(false);
        b.setSingleLine(true);
        b.setMaxLines(1);
        b.setBackground(roundGradient(0xFFFF624F, 0xFFFF7A45, dp(28)));
        return b;
    }

    private Button pill(String text, int bg, int fg) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(16);
        b.setTextColor(fg);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setAllCaps(false);
        b.setSingleLine(true);
        b.setMaxLines(1);
        b.setBackground(round(bg, dp(24), BORDER));
        return b;
    }

    private Button smallChip(String text, int bg, int fg) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(fg);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setSingleLine(true);
        b.setMaxLines(1);
        b.setBackground(round(bg, dp(20), BORDER));
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

    private LinearLayout dialogPanel(int radius) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(18), dp(20), dp(16));
        panel.setBackground(round(CARD, radius, BORDER));
        return panel;
    }

    private LinearLayout dialogActionsRow() {
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        return actions;
    }

    private void showCustomDialog(AlertDialog dialog, View panel) {
        try { dialog.setView(panel, 0, 0, 0, 0); } catch (Exception e) { dialog.setView(panel); }
        dialog.setOnShowListener(d -> {
            try {
                if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            } catch (Exception ignored) { }
        });
        dialog.show();
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
        showRoundedInfoDialog("歡迎使用自動記帳 V23", "這版新增 / 優化：\n\n1. LINE Pay 通知若有原價、點數折抵、實付金額，會優先用實付金額記帳。\n2. LINE Pay 原價通知與銀行 / Google 錢包實扣通知金額不同時，會自動合併成同一筆。\n3. 紀錄詳情會顯示原價、折抵與實際支出。\n4. 防重複、桌面小工具、CSV、備份、還原都保留。", "我知道了", v -> AppSettings.setBool(this, AppSettings.KEY_ONBOARDED, true), "通知用途", v -> showNotificationPurpose());
    }

    private void showNotificationPurpose() {
        showRoundedInfoDialog("通知讀取用途說明", "自動記帳需要通知讀取權限，是為了在你授權後讀取 LINE Pay、載具發票、Google 錢包、銀行刷卡與交易簡訊通知，從通知文字抓出金額、收入或支出。\n\n資料預設只存在你的手機本機。\n\nApp 會自動排除自己發出的通知，也會用金額、時間與原始通知內容判斷同一筆消費，避免重複記帳。", "了解", null, null, null);
    }


    private void showPrivacyPolicy() {
        showRoundedInfoDialog("隱私權政策", "自動記帳會在你授權後讀取通知內容，用於自動辨識付款、收入、發票與銀行交易。\n\n目前自用版資料預設儲存在手機本機，不會主動上傳到伺服器。\n\n你可以在設定中匯出 CSV、備份資料、還原資料或清除全部資料。\n\n若未來啟用 Google 同步，會在使用者同意後才將記帳資料同步到使用者自己的 Google 帳號。\n\n此 App 不提供投資建議；月底預估與儲蓄提醒只作為預算規劃參考。", "關閉", null, null, null);
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
        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        LinearLayout panel = dialogPanel(dp(30));
        panel.addView(text("還原資料", 21, TEXT, true), marginLp(-1, -2, 0, 0, 0, dp(8)));
        panel.addView(text("貼上之前備份的 JSON。還原會覆蓋目前記帳資料，建議先備份再還原。", 14, TEXT, false), marginLp(-1, -2, 0, 0, 0, dp(14)));
        final EditText input = edit("貼上備份 JSON 資料", false);
        input.setMinLines(7);
        input.setSingleLine(false);
        input.setGravity(Gravity.TOP | Gravity.LEFT);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setPadding(dp(16), dp(14), dp(16), dp(14));
        panel.addView(input, marginLp(-1, dp(180), 0, 0, 0, dp(14)));

        LinearLayout actions = dialogActionsRow();
        Button cancel = pill("取消", CHIP, TEXT);
        Button restore = bigSave("還原");
        actions.addView(cancel, marginLp(0, dp(50), 0, 0, dp(6), 0, 1));
        actions.addView(restore, marginLp(0, dp(50), dp(6), 0, 0, 0, 1));
        panel.addView(actions);

        cancel.setOnClickListener(v -> dialog.dismiss());
        restore.setOnClickListener(v -> {
            boolean ok = TransactionStore.importJson(this, input.getText().toString());
            Toast.makeText(this, ok ? "已還原資料" : "還原失敗，格式不正確", Toast.LENGTH_LONG).show();
            dialog.dismiss();
            refreshCurrent();
        });
        showCustomDialog(dialog, panel);
    }

    private void showManageListDialog(String title, String key, List<String> items, String hint) {
        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        LinearLayout panel = dialogPanel(dp(30));
        panel.addView(text(title, 21, TEXT, true), marginLp(-1, -2, 0, 0, 0, dp(8)));
        panel.addView(text("每一行一個項目。這裡只管理分類名稱，不需要輸入金額。", 14, TEXT, false), marginLp(-1, -2, 0, 0, 0, dp(14)));

        final EditText input = edit(hint, false);
        input.setMinLines(8);
        input.setSingleLine(false);
        input.setGravity(Gravity.TOP | Gravity.LEFT);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setTextSize(18);
        input.setPadding(dp(16), dp(14), dp(16), dp(14));
        StringBuilder raw = new StringBuilder();
        for (String item : items) {
            if (raw.length() > 0) raw.append('\n');
            raw.append(item);
        }
        input.setText(raw.toString());
        panel.addView(input, marginLp(-1, dp(220), 0, 0, 0, dp(16)));

        LinearLayout actions = dialogActionsRow();
        Button cancel = pill("取消", CHIP, TEXT);
        Button save = bigSave("儲存");
        actions.addView(cancel, marginLp(0, dp(50), 0, 0, dp(6), 0, 1));
        actions.addView(save, marginLp(0, dp(50), dp(6), 0, 0, 0, 1));
        panel.addView(actions);

        cancel.setOnClickListener(v -> dialog.dismiss());
        save.setOnClickListener(v -> {
            List<String> out = new ArrayList<>();
            for (String line : input.getText().toString().split("\\n")) {
                String clean = line.trim();
                if (!clean.isEmpty()) out.add(clean);
            }
            AppSettings.setList(this, key, out);
            Toast.makeText(this, "已儲存", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            showSettings();
        });
        showCustomDialog(dialog, panel);
    }

    private void showDebugDialog() {
        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        LinearLayout panel = dialogPanel(dp(30));
        panel.addView(text("錯誤回報 / 自動除錯紀錄", 21, TEXT, true), marginLp(-1, -2, 0, 0, 0, dp(8)));
        TextView logText = text(TransactionStore.getDebugLogs(this), 14, TEXT, false);
        logText.setLineSpacing(dp(2), 1.0f);
        logText.setPadding(dp(14), dp(12), dp(14), dp(12));
        logText.setBackground(round(CHIP, dp(20), BORDER));
        ScrollView sc = new ScrollView(this);
        sc.addView(logText, new ScrollView.LayoutParams(-1, -2));
        panel.addView(sc, marginLp(-1, dp(250), 0, 0, 0, dp(14)));

        LinearLayout actions = dialogActionsRow();
        Button clear = pill("清除紀錄", CHIP, GREEN);
        Button close = pill("關閉", CHIP, TEXT);
        Button scan = bigSave("掃描重複");
        actions.addView(clear, marginLp(0, dp(50), 0, 0, dp(6), 0, 1));
        actions.addView(close, marginLp(0, dp(50), dp(6), 0, dp(6), 0, 1));
        actions.addView(scan, marginLp(0, dp(50), dp(6), 0, 0, 0, 1));
        panel.addView(actions);

        clear.setOnClickListener(v -> {
            TransactionStore.clearDebugLogs(this);
            dialog.dismiss();
            showDebugDialog();
        });
        close.setOnClickListener(v -> dialog.dismiss());
        scan.setOnClickListener(v -> {
            int removed = TransactionStore.autoFixDuplicates(this);
            Toast.makeText(this, "已移除 " + removed + " 筆疑似重複資料", Toast.LENGTH_LONG).show();
            dialog.dismiss();
            refreshCurrent();
        });
        showCustomDialog(dialog, panel);
    }

    private View detailLine(String k, String v) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(3), 0, dp(3));
        row.addView(text(k, 13, MUTED, false), new LinearLayout.LayoutParams(dp(82), -2));
        row.addView(text(v == null || v.trim().isEmpty() ? "可留空" : v, 14, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }

    private void showRoundedInfoDialog(String title, String message, String positive, View.OnClickListener positiveAction, String neutral, View.OnClickListener neutralAction) {
        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(22), dp(20), dp(22), dp(16));
        panel.setBackground(round(CARD, dp(26), BORDER));
        panel.addView(text(title, 21, TEXT, true), marginLp(-1, -2, 0, 0, 0, dp(10)));
        TextView body = text(message, 15, TEXT, false);
        body.setLineSpacing(dp(2), 1.0f);
        ScrollView sc = new ScrollView(this);
        sc.addView(body, new ScrollView.LayoutParams(-1, -2));
        panel.addView(sc, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        if (neutral != null) {
            Button n = smallChip(neutral, CHIP, GREEN);
            n.setOnClickListener(v -> { dialog.dismiss(); if (neutralAction != null) neutralAction.onClick(v); });
            actions.addView(n, new LinearLayout.LayoutParams(dp(120), dp(44)));
        }
        Button p = smallChip(positive == null ? "知道了" : positive, CHIP, GREEN);
        p.setOnClickListener(v -> { dialog.dismiss(); if (positiveAction != null) positiveAction.onClick(v); });
        actions.addView(p, marginLp(dp(120), dp(44), dp(8), 0, 0, 0));
        panel.addView(actions, marginLp(-1, -2, 0, dp(12), 0, 0));
        showCustomDialog(dialog, panel);
    }

    private void showTransactionDetail(Transaction tx) {
        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(18), dp(20), dp(14));
        panel.setBackground(round(CARD, dp(26), BORDER));
        panel.addView(text("紀錄詳情", 21, TEXT, true));
        panel.addView(text("點修改可以調整分類、來源、備註與圖標。", 12, MUTED, false), marginLp(-1, -2, 0, dp(2), 0, dp(12)));
        panel.addView(detailLine("類型", "income".equals(tx.direction) ? "收入" : "支出"));
        panel.addView(detailLine("實際金額", TransactionStore.money(tx.amount)));
        if (tx.originalAmount > 0 && tx.originalAmount != tx.amount) {
            panel.addView(detailLine("原價", TransactionStore.money(tx.originalAmount)));
        }
        if (tx.discountAmount > 0) {
            panel.addView(detailLine("折抵 / 點數", "- " + TransactionStore.money(tx.discountAmount)));
        }
        panel.addView(detailLine("分類", cleanCategory(tx.category).isEmpty() ? "未分類" : cleanCategory(tx.category)));
        panel.addView(detailLine("來源 / 店家", empty(tx.merchant) ? "可留空" : tx.merchant));
        panel.addView(detailLine("通知來源", empty(tx.source) ? "自動通知" : tx.source));
        panel.addView(detailLine("時間", TransactionStore.formatTime(tx.timeMillis)));
        TextView rawTitle = label("備註 / 原始內容");
        panel.addView(rawTitle, marginLp(-1, -2, 0, dp(6), 0, 0));
        TextView raw = text(empty(tx.raw) ? "無" : tx.raw, 13, MUTED, false);
        raw.setPadding(dp(12), dp(10), dp(12), dp(10));
        raw.setBackground(round(CHIP, dp(18), BORDER));
        panel.addView(raw, marginLp(-1, -2, 0, dp(4), 0, dp(14)));
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button del = pill("刪除", CHIP, EXPENSE_RED);
        Button close = pill("關閉", CHIP, TEXT);
        Button edit = bigSave("修改");
        actions.addView(del, marginLp(0, dp(48), 0, 0, dp(5), 0, 1));
        actions.addView(close, marginLp(0, dp(48), dp(5), 0, dp(5), 0, 1));
        actions.addView(edit, marginLp(0, dp(48), dp(5), 0, 0, 0, 1));
        panel.addView(actions);
        del.setOnClickListener(v -> { dialog.dismiss(); showDeleteTxConfirm(tx); });
        close.setOnClickListener(v -> dialog.dismiss());
        edit.setOnClickListener(v -> { dialog.dismiss(); showEditTransactionDialog(tx); });
        showCustomDialog(dialog, panel);
    }


    private void showEditTransactionDialog(Transaction tx) {
        final AlertDialog dialog = new AlertDialog.Builder(this).create();

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(16), dp(18), dp(14));
        panel.setBackground(round(CARD, dp(24), BORDER));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout headTexts = new LinearLayout(this);
        headTexts.setOrientation(LinearLayout.VERTICAL);
        headTexts.addView(text("修改紀錄", 20, TEXT, true));
        headTexts.addView(text("修改後會重新計算首頁餘額、圓形圖與統計", 12, MUTED, false));
        head.addView(headTexts, new LinearLayout.LayoutParams(0, -2, 1));
        TextView close = text("×", 28, TEXT, true);
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> dialog.dismiss());
        head.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));
        panel.addView(head, marginLp(-1, -2, 0, 0, 0, dp(12)));

        final boolean[] editingIncome = new boolean[]{"income".equals(tx.direction)};
        final String[] selectedIcon = new String[]{!empty(tx.icon) ? tx.icon : (editingIncome[0] ? "💰" : iconFor(tx.category))};

        LinearLayout iconRow = new LinearLayout(this);
        iconRow.setOrientation(LinearLayout.HORIZONTAL);
        iconRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView iconPreview = text(selectedIcon[0], 24, TEXT, false);
        iconPreview.setGravity(Gravity.CENTER);
        iconPreview.setBackground(round(iconBgFor(tx), dp(20), BORDER));
        iconRow.addView(iconPreview, new LinearLayout.LayoutParams(dp(56), dp(56)));
        LinearLayout iconText = new LinearLayout(this);
        iconText.setOrientation(LinearLayout.VERTICAL);
        iconText.setPadding(dp(12), 0, 0, 0);
        iconText.addView(text("紀錄圖標", 14, TEXT, true));
        iconText.addView(text("點右邊可選茶飲、餐飲、禮物、遊戲等圖標", 12, MUTED, false));
        iconRow.addView(iconText, new LinearLayout.LayoutParams(0, -2, 1));
        Button pickIcon = smallChip("更換", CHIP, ORANGE);
        iconRow.addView(pickIcon, new LinearLayout.LayoutParams(dp(82), dp(44)));
        panel.addView(iconRow, marginLp(-1, -2, 0, 0, 0, dp(12)));

        LinearLayout typeRow = new LinearLayout(this);
        typeRow.setOrientation(LinearLayout.HORIZONTAL);
        Button expenseBtn = pill("支出", editingIncome[0] ? CHIP : 0xFFFFECEF, editingIncome[0] ? TEXT : EXPENSE_RED);
        Button incomeBtn = pill("收入", editingIncome[0] ? 0xFFE9F8F0 : CHIP, editingIncome[0] ? GREEN : TEXT);
        typeRow.addView(expenseBtn, marginLp(0, dp(46), 0, 0, dp(5), 0, 1));
        typeRow.addView(incomeBtn, marginLp(0, dp(46), dp(5), 0, 0, 0, 1));
        panel.addView(typeRow, marginLp(-1, -2, 0, 0, 0, dp(10)));

        final EditText amount = edit("金額", true);
        amount.setInputType(InputType.TYPE_CLASS_NUMBER);
        amount.setText(String.valueOf(tx.amount));
        amount.setTextSize(20);
        panel.addView(label("金額"));
        panel.addView(amount, marginLp(-1, dp(54), 0, 0, 0, dp(9)));

        final EditText category = edit("分類，可留空", false);
        category.setText(tx.category == null ? "" : tx.category);
        panel.addView(label("分類"));
        panel.addView(category, marginLp(-1, dp(54), 0, 0, 0, dp(6)));
        LinearLayout categorySuggestions = miniCategoryChips(category, editingIncome[0]);
        categorySuggestions.setVisibility(View.GONE);
        panel.addView(categorySuggestions, marginLp(-1, -2, 0, 0, 0, dp(9)));
        category.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) categorySuggestions.setVisibility(View.VISIBLE); });
        category.setOnClickListener(v -> categorySuggestions.setVisibility(View.VISIBLE));
        pickIcon.setOnClickListener(v -> showIconPickerDialog(iconPreview, category, selectedIcon, editingIncome[0]));

        final EditText merchant = edit("來源 / 店家，可留空", false);
        merchant.setText(tx.merchant == null ? "" : tx.merchant);
        panel.addView(label("來源 / 店家"));
        panel.addView(merchant, marginLp(-1, dp(54), 0, 0, 0, dp(9)));

        final EditText note = edit("備註 / 原始通知內容", false);
        note.setMinLines(2);
        note.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        note.setText(tx.raw == null ? "" : tx.raw);
        panel.addView(label("備註"));
        panel.addView(note, marginLp(-1, dp(78), 0, 0, 0, dp(12)));

        View.OnClickListener updateTypeUi = v -> {
            expenseBtn.setTextColor(editingIncome[0] ? TEXT : EXPENSE_RED);
            expenseBtn.setBackground(round(editingIncome[0] ? CHIP : 0xFFFFECEF, dp(14), editingIncome[0] ? BORDER : EXPENSE_RED));
            incomeBtn.setTextColor(editingIncome[0] ? GREEN : TEXT);
            incomeBtn.setBackground(round(editingIncome[0] ? 0xFFE9F8F0 : CHIP, dp(14), editingIncome[0] ? GREEN : BORDER));
            panel.removeView(categorySuggestions);
        };
        expenseBtn.setOnClickListener(v -> {
            editingIncome[0] = false;
            expenseBtn.setTextColor(EXPENSE_RED);
            expenseBtn.setBackground(round(0xFFFFECEF, dp(14), EXPENSE_RED));
            incomeBtn.setTextColor(TEXT);
            incomeBtn.setBackground(round(CHIP, dp(14), BORDER));
            if (empty(category.getText().toString())) { selectedIcon[0] = "🧾"; iconPreview.setText(selectedIcon[0]); }
            categorySuggestions.removeAllViews();
            addMiniSuggestionRows(categorySuggestions, category, false);
            categorySuggestions.setVisibility(View.VISIBLE);
        });
        incomeBtn.setOnClickListener(v -> {
            editingIncome[0] = true;
            expenseBtn.setTextColor(TEXT);
            expenseBtn.setBackground(round(CHIP, dp(14), BORDER));
            incomeBtn.setTextColor(GREEN);
            incomeBtn.setBackground(round(0xFFE9F8F0, dp(14), GREEN));
            if (empty(category.getText().toString())) { selectedIcon[0] = "💰"; iconPreview.setText(selectedIcon[0]); }
            categorySuggestions.removeAllViews();
            addMiniSuggestionRows(categorySuggestions, category, true);
            categorySuggestions.setVisibility(View.VISIBLE);
        });

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button cancel = pill("取消", CHIP, TEXT);
        Button save = bigSave("✓ 儲存");
        actions.addView(cancel, marginLp(0, dp(52), 0, 0, dp(6), 0, 1));
        actions.addView(save, marginLp(0, dp(52), dp(6), 0, 0, 0, 1));
        panel.addView(actions);
        cancel.setOnClickListener(v -> dialog.dismiss());
        save.setOnClickListener(v -> {
            int value = 0;
            try { value = Integer.parseInt(amount.getText().toString().replace(",", "").trim()); } catch (Exception ignored) { }
            if (value <= 0) {
                Toast.makeText(this, "金額要大於 0", Toast.LENGTH_SHORT).show();
                return;
            }
            String finalCategory = category.getText().toString().trim();
            String previousAutoIcon = "income".equals(tx.direction) ? "💰" : iconFor(tx.category);
            String finalIcon = selectedIcon[0];
            if (empty(tx.icon) && finalIcon.equals(previousAutoIcon) && !finalCategory.equals(tx.category == null ? "" : tx.category)) {
                finalIcon = editingIncome[0] ? "💰" : iconFor(finalCategory);
            }
            Transaction edited = new Transaction(
                    tx.timeMillis,
                    value,
                    editingIncome[0] ? "income" : "expense",
                    tx.source == null ? "" : tx.source,
                    merchant.getText().toString().trim(),
                    finalCategory,
                    note.getText().toString().trim(),
                    tx.hash == null || tx.hash.isEmpty() ? "edit-" + tx.timeMillis : tx.hash,
                    finalIcon
            );
            boolean ok = TransactionStore.update(this, tx.hash, tx.timeMillis, edited);
            Toast.makeText(this, ok ? "已修改紀錄" : "修改失敗", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            refreshCurrent();
        });

        showCustomDialog(dialog, panel);
    }

    private void showIconPickerDialog(TextView preview, EditText category, String[] selectedIcon, boolean income) {
        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(16), dp(18), dp(14));
        panel.setBackground(round(CARD, dp(26), BORDER));
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text("選擇圖標", 20, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView close = text("×", 25, TEXT, true);
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> dialog.dismiss());
        head.addView(close, new LinearLayout.LayoutParams(dp(42), dp(42)));
        panel.addView(head, marginLp(-1, -2, 0, 0, 0, dp(8)));
        String[][] items = income
                ? new String[][]{{"💰","收入"},{"💵","薪水"},{"🧧","紅包"},{"↩","退款"},{"👨‍👩‍👧","家人"},{"⭐","其他"}}
                : new String[][]{{"🥤","茶飲"},{"🍴","餐飲"},{"🚌","交通"},{"🏪","超商"},{"🛍","購物"},{"▶","訂閱"},{"🎮","遊戲"},{"🎁","禮物"},{"💊","醫療"},{"📚","學習"},{"✈️","旅遊"},{"🏋️","運動"},{"🐾","寵物"},{"🏠","房租"},{"🧾","其他"}};
        for (int i = 0; i < items.length; i += 3) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int j = 0; j < 3; j++) {
                if (i + j < items.length) {
                    String ic = items[i + j][0];
                    String name = items[i + j][1];
                    Button b = smallChip(ic + "  " + name, CHIP, TEXT);
                    b.setTextSize(13);
                    b.setOnClickListener(v -> {
                        selectedIcon[0] = ic;
                        preview.setText(ic);
                        if (category.getText().toString().trim().isEmpty() || cleanCategory(category.getText().toString()).isEmpty()) category.setText(name);
                        dialog.dismiss();
                    });
                    row.addView(b, marginLp(0, dp(42), dp(3), dp(3), dp(3), dp(3), 1));
                } else {
                    row.addView(new TextView(this), marginLp(0, dp(42), dp(3), dp(3), dp(3), dp(3), 1));
                }
            }
            panel.addView(row);
        }
        showCustomDialog(dialog, panel);
    }

    private LinearLayout miniCategoryChips(EditText target, boolean income) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        addMiniSuggestionRows(box, target, income);
        return box;
    }

    private void addMiniSuggestionRows(LinearLayout box, EditText target, boolean income) {
        String[] items = income
                ? new String[]{"薪水", "零用錢", "打工", "退款", "紅包", "獎金", "家人", "其他"}
                : new String[]{"茶飲", "飲料", "早餐", "午餐", "晚餐", "交通", "超商", "訂閱"};
        for (int i = 0; i < items.length; i += 4) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int j = 0; j < 4; j++) {
                if (i + j < items.length) {
                    String value = items[i + j];
                    Button b = smallChip(value, CHIP, income ? PURPLE : ORANGE);
                    b.setTextSize(12);
                    b.setMinHeight(dp(34));
                    b.setPadding(dp(2), 0, dp(2), 0);
                    b.setOnClickListener(v -> target.setText(value));
                    row.addView(b, marginLp(0, dp(36), dp(2), dp(2), dp(2), dp(2), 1));
                } else {
                    TextView blank = new TextView(this);
                    row.addView(blank, marginLp(0, dp(36), dp(2), dp(2), dp(2), dp(2), 1));
                }
            }
            box.addView(row, new LinearLayout.LayoutParams(-1, dp(40)));
        }
    }

    private void showDeleteTxConfirm(Transaction tx) {
        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        LinearLayout panel = dialogPanel(dp(30));
        panel.addView(text("刪除這筆紀錄？", 21, TEXT, true), marginLp(-1, -2, 0, 0, 0, dp(8)));
        panel.addView(text("刪除後無法再復原。\n\n" + ("income".equals(tx.direction) ? "收入 " : "支出 ") + TransactionStore.money(tx.amount) + "｜" + tx.merchant, 14, TEXT, false), marginLp(-1, -2, 0, 0, 0, dp(16)));
        LinearLayout actions = dialogActionsRow();
        Button cancel = pill("取消", CHIP, TEXT);
        Button del = bigAction("刪除", 0xFFFF6B6B, 0xFFFF5A45);
        actions.addView(cancel, marginLp(0, dp(50), 0, 0, dp(6), 0, 1));
        actions.addView(del, marginLp(0, dp(50), dp(6), 0, 0, 0, 1));
        panel.addView(actions);
        cancel.setOnClickListener(v -> dialog.dismiss());
        del.setOnClickListener(v -> {
            boolean ok = TransactionStore.delete(this, tx.hash, tx.timeMillis);
            Toast.makeText(this, ok ? "已刪除" : "刪除失敗", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            refreshCurrent();
        });
        showCustomDialog(dialog, panel);
    }

    private void copyToClipboard(String label, String text) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText(label, text));
            Toast.makeText(this, "已複製到剪貼簿", Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) { }
    }

    private void confirmClear() {
        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        LinearLayout panel = dialogPanel(dp(30));
        panel.addView(text("確認清除資料？", 21, TEXT, true), marginLp(-1, -2, 0, 0, 0, dp(8)));
        panel.addView(text("你要確認要刪除資料嗎？\n\n刪除後會清除所有收入、支出、通知 hash 與重複判斷紀錄。\n\n刪除資料無法再復原，建議先備份資料。", 14, TEXT, false), marginLp(-1, -2, 0, 0, 0, dp(16)));
        LinearLayout actions = dialogActionsRow();
        Button cancel = pill("取消", CHIP, TEXT);
        Button del = bigAction("確認刪除", 0xFFFF6B6B, 0xFFFF5A45);
        actions.addView(cancel, marginLp(0, dp(50), 0, 0, dp(6), 0, 1));
        actions.addView(del, marginLp(0, dp(50), dp(6), 0, 0, 0, 1));
        panel.addView(actions);
        cancel.setOnClickListener(v -> dialog.dismiss());
        del.setOnClickListener(v -> {
            TransactionStore.clear(this);
            Toast.makeText(this, "已清除全部資料", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            showHome();
        });
        showCustomDialog(dialog, panel);
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
