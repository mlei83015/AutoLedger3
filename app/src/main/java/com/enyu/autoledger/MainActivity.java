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
import android.os.Looper;
import android.os.Handler;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.Editable;
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
import java.text.DecimalFormat;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    public static final String ACTION_QUICK_EXPENSE = "com.enyu.autoledger.action.QUICK_EXPENSE";
    public static final String ACTION_QUICK_INCOME = "com.enyu.autoledger.action.QUICK_INCOME";
    public static final String ACTION_QUICK_CALCULATOR = "com.enyu.autoledger.action.QUICK_CALCULATOR";
    public static final String ACTION_EDIT_WIDGET_PHOTO = "com.enyu.autoledger.action.EDIT_WIDGET_PHOTO";
    private static final int REQUEST_WIDGET_IMAGE = 1901;
    private static final int REQUEST_PROFILE_AVATAR = 1902;

    private LinearLayout root;
    private LinearLayout content;
    private LinearLayout nav;
    private int tab = 0;
    private String manualDirection = "expense";
    private String reportType = "expense";
    private String reportRange = "month";
    private long homeMonthMillis = System.currentTimeMillis();
    private long calendarMonthMillis = System.currentTimeMillis();
    private long calendarFocusedDayMillis = -1L;

    private int BG = 0xFFF6F7FB;
    private int CARD = 0xFFFFFFFF;
    private int TEXT = 0xFF1F2430;
    private int MUTED = 0xFF687282;
    private int BORDER = 0xFFE3E7ED;
    private int CHIP = 0xFFF0F3F7;
    private final int ORANGE = 0xFFFF7A3D;
    private final int CORAL = 0xFFFF5A5F;
    private final int TEAL = 0xFF12A7A0;
    private final int PURPLE = 0xFF6E61F2;
    private final int GREEN = 0xFF18A558;
    private final int EXPENSE_RED = 0xFFE94F64;
    private final int SOFT_BLUE = 0xFF4FADEB;

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
        } else if (requestCode == REQUEST_PROFILE_AVATAR && resultCode == RESULT_OK && data != null && data.getData() != null) {
            showProfileAvatarCropDialog(data.getData());
        }
    }

    private void handleLaunchIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (ACTION_QUICK_EXPENSE.equals(action)) {
            root.postDelayed(() -> showManual("expense"), 120);
        } else if (ACTION_QUICK_INCOME.equals(action)) {
            root.postDelayed(() -> showManual("income"), 120);
        } else if (ACTION_QUICK_CALCULATOR.equals(action)) {
            root.postDelayed(() -> showManual("expense"), 120);
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
        nav.setPadding(dp(10), dp(7), dp(10), dp(9));
        nav.setBackground(round(isDarkMode() ? 0xFF151F2A : 0xFFFFFFFF, dp(22), BORDER));
        elevate(nav, 3);
        root.addView(nav, marginLp(-1, -2, dp(10), 0, dp(10), dp(8)));
        setContentView(root);
    }

    private void refreshCurrent() {
        if (tab == 0) showHome();
        else if (tab == 1) showManual(manualDirection);
        else if (tab == 2) showStats();
        else if (tab == 4) {
            if (calendarFocusedDayMillis > 0) showCalendarDay(calendarFocusedDayMillis);
            else showCalendarMonth(calendarMonthMillis);
        }
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
        box.setPadding(dp(16), dp(14), dp(16), dp(22));
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
            BG = 0xFF0F151D;
            CARD = 0xFF17212C;
            TEXT = 0xFFF5F7FA;
            MUTED = 0xFFAAB3C2;
            BORDER = 0xFF2A3644;
            CHIP = 0xFF202B38;
        } else {
            BG = 0xFFF6F7FB;
            CARD = 0xFFFFFFFF;
            TEXT = 0xFF1F2430;
            MUTED = 0xFF687282;
            BORDER = 0xFFE3E7ED;
            CHIP = 0xFFF0F3F7;
        }
        if (root != null) {
            root.setBackgroundColor(BG);
            root.setPadding(0, safeTopPadding(), 0, 0);
        }
        if (nav != null) nav.setBackground(round(dark ? 0xFF151F2A : 0xFFFFFFFF, dp(22), BORDER));
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
        int spent = TransactionStore.effectiveMonthExpense(this);
        int remain = Math.max(0, budget - spent);
        texts.addView(text("本月可花預算", 16, TEXT, true));
        texts.addView(text("基本 " + TransactionStore.money(baseBudget) + (extra > 0 ? "＋加回 " + TransactionStore.money(extra) : "") + "，還能花 " + TransactionStore.money(remain), 13, MUTED, false));
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        Button edit = smallChip("修改", CHIP, ORANGE);
        edit.setOnClickListener(v -> showBudgetDialog());
        row.addView(edit, new LinearLayout.LayoutParams(dp(86), dp(44)));
        return row;
    }

    private void showRemainingBalanceDialog() {
        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        LinearLayout panel = dialogPanel(dp(30));
        panel.addView(text("設定目前剩餘餘額", 21, TEXT, true), marginLp(-1, -2, 0, 0, 0, dp(8)));
        panel.addView(text("這裡設定的是你現在實際還剩多少錢。\n\n例：本月預算是 $10,000，但你中途開始用 App，現在只剩 $5,000，就輸入 5000。App 會自動把本月圓形圖換算成已花費 $5,000、剩餘 $5,000，預算本身不會被改掉。", 14, TEXT, false), marginLp(-1, -2, 0, 0, 0, dp(12)));
        final EditText input = edit("例如：5000", true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(Math.max(0, TransactionStore.totalBalance(this))));
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
            if (amount < 0) {
                Toast.makeText(this, "餘額不能小於 0", Toast.LENGTH_SHORT).show();
                return;
            }
            AppSettings.setCurrentRemainingBalance(this, amount);
            Toast.makeText(this, "已設定目前剩餘餘額 " + TransactionStore.money(amount), Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            refreshCurrent();
        });
        showCustomDialog(dialog, panel);
    }

    private void showBudgetDialog() {
        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        LinearLayout panel = dialogPanel(dp(30));
        panel.addView(text("設定本月可花預算", 21, TEXT, true), marginLp(-1, -2, 0, 0, 0, dp(8)));
        panel.addView(text("每個月第一次使用時，剩餘餘額會從這個預算開始。中途要改現在剩多少，請點首頁的剩餘餘額；收入預設不加回，勾選後才會加回目前剩餘餘額。", 14, TEXT, false), marginLp(-1, -2, 0, 0, 0, dp(12)));
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
        homeMonthMillis = calendarMonthStart(homeMonthMillis);
        long homeMonthStart = homeMonthMillis;
        long homeMonthEnd = calendarMonthOffset(homeMonthStart, 1);
        boolean currentHomeMonth = isSameMonth(homeMonthStart, System.currentTimeMillis());
        List<Transaction> monthTxs = transactionsBetween(homeMonthStart, homeMonthEnd);
        int rawMonthExpense = totalFor(monthTxs, "expense");
        int monthIncome = totalFor(monthTxs, "income");
        int budgetForMonth = AppSettings.getMonthlyUsableBudget(this);
        int budget = Math.max(1, budgetForMonth);
        int remaining = currentHomeMonth ? Math.max(0, AppSettings.getCurrentRemainingBalance(this)) : Math.max(0, budget - rawMonthExpense);
        int monthExpense = currentHomeMonth ? Math.max(rawMonthExpense, budget - remaining) : rawMonthExpense;

        ScrollView scroll = pageBase();
        LinearLayout box = pageBox(scroll);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView menu = text("☰", 25, TEXT, true);
        menu.setGravity(Gravity.CENTER);
        menu.setBackground(round(CHIP, dp(18), BORDER));
        menu.setOnClickListener(v -> showSideMenu());
        titleRow.addView(menu, new LinearLayout.LayoutParams(dp(38), dp(38)));
        titleRow.addView(new View(this), new LinearLayout.LayoutParams(dp(46), dp(1)));
        TextView title = text(formatCalendarMonth(homeMonthMillis) + " ▾", 23, TEXT, true);
        title.setGravity(Gravity.CENTER);
        title.setOnClickListener(v -> showMonthPickerDialog(homeMonthMillis, picked -> {
            homeMonthMillis = picked;
            showHome();
        }));
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView bell = text("🔔", 22, TEXT, false);
        bell.setGravity(Gravity.CENTER);
        bell.setBackground(round(CHIP, dp(18), BORDER));
        bell.setOnClickListener(v -> showNotificationSettingsDialog());
        titleRow.addView(bell, new LinearLayout.LayoutParams(dp(38), dp(38)));
        TextView calendar = text("📅", 21, TEXT, false);
        calendar.setGravity(Gravity.CENTER);
        calendar.setBackground(round(CHIP, dp(18), BORDER));
        calendar.setOnClickListener(v -> showCalendarMonth(homeMonthMillis));
        titleRow.addView(calendar, marginLp(dp(38), dp(38), dp(8), 0, 0, 0));
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

        LinearLayout balanceCard = new LinearLayout(this);
        balanceCard.setOrientation(LinearLayout.VERTICAL);
        balanceCard.setPadding(dp(20), dp(16), dp(20), dp(16));
        balanceCard.setBackground(roundGradient(0xFF12A7A0, 0xFFFFA43D, dp(22)));
        elevate(balanceCard, 3);
        TextView b1 = text(currentHomeMonth ? "目前剩餘餘額" : "這個月估算剩餘", 14, 0xFFFFFFFF, true);
        TextView b2 = text(TransactionStore.money(remaining), 34, 0xFFFFFFFF, true);
        TextView b3 = text((currentHomeMonth ? "點此更新現在剩多少" : "依照這個月紀錄估算") + "｜月預算 " + TransactionStore.money(budgetForMonth), 12, 0xFFFFF7EC, false);
        balanceCard.addView(b1);
        balanceCard.addView(b2);
        balanceCard.addView(b3);
        balanceCard.setOnClickListener(v -> showRemainingBalanceDialog());
        LinearLayout.LayoutParams balanceLp = new LinearLayout.LayoutParams(-1, -2);
        balanceLp.setMargins(0, dp(16), 0, dp(12));
        box.addView(balanceCard, balanceLp);

        LinearLayout chartCard = card();
        chartCard.setOrientation(LinearLayout.HORIZONTAL);
        chartCard.setGravity(Gravity.CENTER_VERTICAL);
        DonutChartView donut = new DonutChartView(this);
        donut.setDarkMode(isDarkMode());
        donut.setCenterLabel("已使用");
        donut.setData(monthExpense, remaining, 0, AppSettings.getPalette(this));
        donut.setSegmentLabels("已花費", "剩餘預算", "");
        chartCard.addView(donut, new LinearLayout.LayoutParams(dp(156), dp(156)));
        LinearLayout legend = new LinearLayout(this);
        legend.setOrientation(LinearLayout.VERTICAL);
        legend.setPadding(dp(8), 0, 0, 0);

        int remainColor = donut.remainColor();
        int spentColor = donut.spentColor();
        TextView remainingTitle = text("剩餘預算", 13, remainColor, true);
        TextView remainingValue = text(TransactionStore.money(remaining), 25, remainColor, true);
        legend.addView(remainingTitle);
        legend.addView(remainingValue, marginLp(-1, -2, 0, dp(1), 0, dp(8)));

        legend.addView(text("本月財務狀況", 15, TEXT, true));
        legend.addView(legendRow("● 本月預算", budget, 0xFFFFA726));
        legend.addView(legendRow("● 已花費", monthExpense, spentColor));
        legend.addView(legendRow("● 剩餘預算", remaining, remainColor));
        chartCard.addView(legend, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(chartCard, marginLp(-1, -2, 0, 0, 0, dp(10)));

        int todayExpense = TransactionStore.expenseBetween(this, TransactionStore.startOfDay(0), TransactionStore.startOfDay(1));
        String focusLine = currentHomeMonth ? "今天總共花了  " + TransactionStore.money(todayExpense) : formatCalendarMonth(homeMonthMillis) + "支出  " + TransactionStore.money(rawMonthExpense);
        TextView todayLine = text(focusLine, 18, TEXT, true);
        todayLine.setGravity(Gravity.CENTER);
        todayLine.setTextColor((currentHomeMonth ? todayExpense : rawMonthExpense) > 0 ? EXPENSE_RED : MUTED);
        box.addView(todayLine, marginLp(-1, -2, 0, 0, 0, dp(10)));

        LinearLayout studentTip = card();
        studentTip.setPadding(dp(14), dp(10), dp(14), dp(10));
        studentTip.setBackground(round(isDarkMode() ? 0xFF1B2735 : 0xFFFFF7E8, dp(18), BORDER));
        int forecastHome = TransactionStore.forecastMonthExpense(this);
        int savingHome = TransactionStore.suggestedSaving(this);
        studentTip.addView(text(currentHomeMonth ? "✨ 本月小提醒" : "✨ 月份摘要", 15, TEXT, true));
        String tipHome = currentHomeMonth
                ? (forecastHome > budgetForMonth ? "照現在速度月底可能超過預算，今天可以先少喝一杯飲料。" : (savingHome > 0 ? "照現在速度月底可能有剩，可以先存 " + TransactionStore.money(savingHome) + "。" : "目前花費接近預算，先維持節奏。"))
                : ("這個月支出 " + TransactionStore.money(rawMonthExpense) + "，收入 " + TransactionStore.money(monthIncome) + "，共 " + monthTxs.size() + " 筆紀錄。");
        studentTip.addView(text(tipHome, 13, MUTED, false));
        box.addView(studentTip, marginLp(-1, -2, 0, 0, 0, dp(10)));

        LinearLayout monthCountCard = new LinearLayout(this);
        monthCountCard.setOrientation(LinearLayout.HORIZONTAL);
        monthCountCard.setGravity(Gravity.CENTER_VERTICAL);
        monthCountCard.setPadding(dp(14), dp(11), dp(14), dp(11));
        monthCountCard.setBackground(round(isDarkMode() ? 0xFF17212C : 0xFFFFFFFF, dp(18), BORDER));
        elevate(monthCountCard, 1);
        int monthTotalCount = TransactionStore.countBetween(this, homeMonthStart, homeMonthEnd);
        int monthAutoCount = TransactionStore.autoCountBetween(this, homeMonthStart, homeMonthEnd);
        int monthManualCount = TransactionStore.manualCountBetween(this, homeMonthStart, homeMonthEnd);
        monthCountCard.addView(text("本月記錄 " + monthTotalCount + " 筆", 14, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        monthCountCard.addView(text("自動 " + monthAutoCount + "｜手動 " + monthManualCount, 13, MUTED, false));
        box.addView(monthCountCard, marginLp(-1, -2, 0, 0, 0, dp(12)));

        LinearLayout quick = new LinearLayout(this);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        Button expense = bigAction("↓\n支出\n記錄花費", EXPENSE_RED, ORANGE);
        expense.setOnClickListener(v -> showManual("expense"));
        Button income = bigAction("↑\n收入\n記錄收入", GREEN, TEAL);
        income.setOnClickListener(v -> showManual("income"));
        Button calculator = bigAction("÷\n計算機\n分攤", SOFT_BLUE, PURPLE);
        calculator.setOnClickListener(v -> showManual("expense"));
        quick.addView(expense, new LinearLayout.LayoutParams(0, dp(82), 1));
        quick.addView(new View(this), new LinearLayout.LayoutParams(dp(8), 1));
        quick.addView(income, new LinearLayout.LayoutParams(0, dp(82), 1));
        quick.addView(new View(this), new LinearLayout.LayoutParams(dp(8), 1));
        quick.addView(calculator, new LinearLayout.LayoutParams(0, dp(82), 1));
        box.addView(quick, marginLp(-1, -2, 0, 0, 0, dp(12)));

        LinearLayout recordHeader = new LinearLayout(this);
        recordHeader.setGravity(Gravity.CENTER_VERTICAL);
        recordHeader.addView(text("記錄", 18, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView clear = text("清除資料", 13, MUTED, false);
        clear.setOnClickListener(v -> confirmClear());
        recordHeader.addView(clear);
        TextView hintEdit = text("  點一下看詳情｜長按修改", 12, MUTED, false);
        recordHeader.addView(hintEdit);
        box.addView(recordHeader, marginLp(-1, -2, 0, 0, 0, dp(6)));

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        if (monthTxs.isEmpty()) {
            list = card();
            TextView empty = text("還沒有紀錄。\n可以先按下方＋手動新增，或開啟通知讀取後測試 LINE Pay / 銀行通知。", 15, MUTED, false);
            empty.setPadding(dp(14), dp(18), dp(14), dp(18));
            list.addView(empty);
        } else {
            addDailyRecordGroups(list, monthTxs);
        }
        box.addView(list);

        setPage(scroll);
    }

    private void showCalendarMonth(long monthMillis) {
        tab = 4;
        calendarFocusedDayMillis = -1L;
        calendarMonthMillis = calendarMonthStart(monthMillis);
        applyModeColors();

        ScrollView scroll = pageBase();
        LinearLayout box = pageBox(scroll);
        box.setPadding(dp(14), dp(8), dp(14), dp(16));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("×", 28, TEXT, true);
        back.setGravity(Gravity.CENTER);
        back.setBackground(round(CHIP, dp(16), BORDER));
        back.setOnClickListener(v -> showHome());
        top.addView(back, new LinearLayout.LayoutParams(dp(38), dp(42)));

        LinearLayout monthControls = new LinearLayout(this);
        monthControls.setGravity(Gravity.CENTER_VERTICAL);
        TextView prev = text("‹", 28, TEXT, true);
        prev.setGravity(Gravity.CENTER);
        prev.setBackground(round(CHIP, dp(16), BORDER));
        prev.setOnClickListener(v -> showCalendarMonth(calendarMonthOffset(calendarMonthMillis, -1)));
        monthControls.addView(prev, marginLp(dp(40), dp(38), 0, 0, dp(8), 0));

        TextView title = text(formatCalendarMonth(calendarMonthMillis) + " ▾", 22, TEXT, true);
        title.setGravity(Gravity.CENTER);
        title.setOnClickListener(v -> showMonthPickerDialog(calendarMonthMillis, picked -> showCalendarMonth(picked)));
        monthControls.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        TextView next = text("›", 28, TEXT, true);
        next.setGravity(Gravity.CENTER);
        next.setBackground(round(CHIP, dp(16), BORDER));
        next.setOnClickListener(v -> showCalendarMonth(calendarMonthOffset(calendarMonthMillis, 1)));
        monthControls.addView(next, marginLp(dp(40), dp(38), dp(8), 0, 0, 0));
        top.addView(monthControls, new LinearLayout.LayoutParams(0, -2, 1));
        TextView spacer = text("", 1, TEXT, false);
        top.addView(spacer, new LinearLayout.LayoutParams(dp(38), dp(42)));
        box.addView(top, marginLp(-1, -2, 0, 0, 0, dp(10)));

        long monthStart = calendarMonthMillis;
        long monthEnd = calendarMonthOffset(monthStart, 1);
        List<Transaction> monthTxs = transactionsBetween(monthStart, monthEnd);
        int monthExpense = totalFor(monthTxs, "expense");
        int monthIncome = totalFor(monthTxs, "income");
        int budget = Math.max(1, AppSettings.getMonthlyUsableBudget(this));
        int remaining = isSameMonth(monthStart, System.currentTimeMillis())
                ? Math.max(0, AppSettings.getCurrentRemainingBalance(this))
                : Math.max(0, budget - monthExpense);
        int count = monthTxs.size();

        LinearLayout summary = card();
        summary.setPadding(dp(16), dp(14), dp(16), dp(14));
        summary.addView(text("月曆帳本", 18, TEXT, true), marginLp(-1, -2, 0, 0, 0, dp(8)));
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(calendarMetric("剩餘", TransactionStore.money(remaining), GREEN), marginLp(0, -2, 0, 0, dp(8), 0, 1));
        row1.addView(calendarMetric("支出", TransactionStore.money(monthExpense), EXPENSE_RED), marginLp(0, -2, dp(8), 0, 0, 0, 1));
        summary.addView(row1, marginLp(-1, -2, 0, 0, 0, dp(8)));
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(calendarMetric("收入", TransactionStore.money(monthIncome), SOFT_BLUE), marginLp(0, -2, 0, 0, dp(8), 0, 1));
        row2.addView(calendarMetric("預算", TransactionStore.money(budget), ORANGE), marginLp(0, -2, dp(8), 0, 0, 0, 1));
        summary.addView(row2);
        summary.addView(text("本月 " + count + " 筆紀錄", 13, MUTED, false), marginLp(-1, -2, 0, dp(10), 0, 0));
        box.addView(summary, marginLp(-1, -2, 0, 0, 0, dp(12)));

        addCalendarGrid(box, monthStart, monthTxs);
        setPage(scroll);
    }

    private LinearLayout calendarMetric(String label, String value, int color) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(dp(4), dp(2), dp(4), dp(2));
        block.addView(text(label, 12, MUTED, false));
        block.addView(text(value, 20, color, true));
        return block;
    }

    private void addCalendarGrid(LinearLayout box, long monthStart, List<Transaction> monthTxs) {
        LinearLayout grid = card();
        grid.setPadding(dp(8), dp(10), dp(8), dp(10));

        LinearLayout week = new LinearLayout(this);
        week.setOrientation(LinearLayout.HORIZONTAL);
        String[] names = new String[]{"一", "二", "三", "四", "五", "六", "日"};
        for (String name : names) {
            TextView w = text(name, 12, MUTED, true);
            w.setGravity(Gravity.CENTER);
            week.addView(w, new LinearLayout.LayoutParams(0, dp(26), 1));
        }
        grid.addView(week);

        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(monthStart);
        int maxDay = c.getActualMaximum(Calendar.DAY_OF_MONTH);
        int firstDayOfWeek = c.get(Calendar.DAY_OF_WEEK);
        int leading = (firstDayOfWeek + 5) % 7;
        int totalCells = ((leading + maxDay + 6) / 7) * 7;
        int[] dayExpense = new int[maxDay + 1];
        int[] dayIncome = new int[maxDay + 1];
        int[] dayCount = new int[maxDay + 1];
        if (monthTxs != null) {
            Calendar txCal = Calendar.getInstance();
            for (Transaction t : monthTxs) {
                if (t == null) continue;
                txCal.setTimeInMillis(t.timeMillis);
                int day = txCal.get(Calendar.DAY_OF_MONTH);
                if (day < 1 || day > maxDay) continue;
                dayCount[day]++;
                if ("expense".equals(t.direction)) dayExpense[day] += Math.max(0, t.amount);
                else if ("income".equals(t.direction)) dayIncome[day] += Math.max(0, t.amount);
            }
        }

        for (int cell = 0; cell < totalCells; cell += 7) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int x = 0; x < 7; x++) {
                int index = cell + x;
                int day = index - leading + 1;
                View dayCell;
                if (day < 1 || day > maxDay) {
                    dayCell = emptyCalendarCell();
                } else {
                    dayCell = calendarDayCell(calendarDayOfMonth(monthStart, day), dayExpense[day], dayIncome[day], dayCount[day]);
                }
                row.addView(dayCell, marginLp(0, dp(72), dp(2), dp(2), dp(2), dp(2), 1));
            }
            grid.addView(row);
        }

        box.addView(grid);
    }

    private View emptyCalendarCell() {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        return cell;
    }

    private View calendarDayCell(long dayStart, int expense, int income, int count) {
        boolean today = sameCalendarDay(dayStart, System.currentTimeMillis());

        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setPadding(dp(3), dp(5), dp(3), dp(3));
        int bg = today ? (isDarkMode() ? 0xFF304052 : 0xFFFFF4DA) : (isDarkMode() ? 0xFF111923 : 0xFFF7FAFC);
        int stroke = today ? ORANGE : BORDER;
        cell.setBackground(round(bg, dp(12), stroke));
        cell.setOnClickListener(v -> showCalendarDay(dayStart));

        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(dayStart);
        TextView day = text(String.valueOf(c.get(Calendar.DAY_OF_MONTH)), 14, TEXT, true);
        day.setGravity(Gravity.CENTER);
        cell.addView(day);

        TextView spent = text(expense > 0 ? "-" + moneyShort(expense) : "", 10, EXPENSE_RED, true);
        spent.setGravity(Gravity.CENTER);
        spent.setSingleLine(true);
        cell.addView(spent, new LinearLayout.LayoutParams(-1, dp(16)));

        TextView earned = text(income > 0 ? "+" + moneyShort(income) : (count > 0 ? count + "筆" : ""), 10, income > 0 ? GREEN : MUTED, false);
        earned.setGravity(Gravity.CENTER);
        earned.setSingleLine(true);
        cell.addView(earned, new LinearLayout.LayoutParams(-1, dp(16)));
        return cell;
    }

    private void showCalendarDay(long dayMillis) {
        tab = 4;
        calendarFocusedDayMillis = calendarDayStart(dayMillis);
        calendarMonthMillis = calendarMonthStart(dayMillis);
        applyModeColors();

        ScrollView scroll = pageBase();
        LinearLayout box = pageBox(scroll);
        box.setPadding(dp(14), dp(8), dp(14), dp(16));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 32, TEXT, false);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> showCalendarMonth(calendarMonthMillis));
        top.addView(back, new LinearLayout.LayoutParams(dp(38), dp(42)));
        TextView title = text(formatCalendarDate(calendarFocusedDayMillis), 20, TEXT, true);
        title.setGravity(Gravity.CENTER);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView home = text("⌂", 23, TEXT, true);
        home.setGravity(Gravity.CENTER);
        home.setBackground(round(CHIP, dp(16), BORDER));
        home.setOnClickListener(v -> showHome());
        top.addView(home, new LinearLayout.LayoutParams(dp(40), dp(38)));
        box.addView(top, marginLp(-1, -2, 0, 0, 0, dp(10)));

        long dayStart = calendarFocusedDayMillis;
        long dayEnd = calendarDayOffset(dayStart, 1);
        List<Transaction> txs = transactionsBetween(dayStart, dayEnd);
        int expense = totalFor(txs, "expense");
        int income = totalFor(txs, "income");
        int count = txs.size();

        LinearLayout summary = card();
        summary.setPadding(dp(16), dp(14), dp(16), dp(14));
        summary.addView(text("當日摘要", 18, TEXT, true), marginLp(-1, -2, 0, 0, 0, dp(8)));
        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.addView(calendarMetric("支出", TransactionStore.money(expense), EXPENSE_RED), marginLp(0, -2, 0, 0, dp(6), 0, 1));
        metrics.addView(calendarMetric("收入", TransactionStore.money(income), GREEN), marginLp(0, -2, dp(6), 0, dp(6), 0, 1));
        metrics.addView(calendarMetric("筆數", count + " 筆", SOFT_BLUE), marginLp(0, -2, dp(6), 0, 0, 0, 1));
        summary.addView(metrics);
        box.addView(summary, marginLp(-1, -2, 0, 0, 0, dp(12)));

        LinearLayout list = card();
        list.setPadding(dp(4), dp(6), dp(4), dp(6));
        if (txs.isEmpty()) {
            TextView empty = text("這一天還沒有紀錄。", 15, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(12), dp(20), dp(12), dp(20));
            list.addView(empty);
        } else {
            for (Transaction t : txs) {
                list.addView(transactionRow(t));
            }
        }
        box.addView(list);

        setPage(scroll);
    }

    private long calendarMonthStart(long time) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(time);
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private long calendarMonthOffset(long time, int months) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(calendarMonthStart(time));
        c.add(Calendar.MONTH, months);
        return c.getTimeInMillis();
    }

    private long calendarDayStart(long time) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(time);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private long calendarDayOffset(long time, int days) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(calendarDayStart(time));
        c.add(Calendar.DAY_OF_MONTH, days);
        return c.getTimeInMillis();
    }

    private long calendarDayOfMonth(long monthStart, int day) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(calendarMonthStart(monthStart));
        c.set(Calendar.DAY_OF_MONTH, day);
        return c.getTimeInMillis();
    }

    private boolean sameCalendarDay(long a, long b) {
        Calendar ca = Calendar.getInstance();
        ca.setTimeInMillis(a);
        Calendar cb = Calendar.getInstance();
        cb.setTimeInMillis(b);
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR);
    }

    private boolean isSameMonth(long a, long b) {
        Calendar ca = Calendar.getInstance();
        ca.setTimeInMillis(a);
        Calendar cb = Calendar.getInstance();
        cb.setTimeInMillis(b);
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.MONTH) == cb.get(Calendar.MONTH);
    }

    private String formatCalendarMonth(long time) {
        return new SimpleDateFormat("yyyy年M月", Locale.TAIWAN).format(new Date(time));
    }

    private void showMonthPickerDialog(long currentMonth, MonthPickCallback callback) {
        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(16), dp(18), dp(16));
        panel.setBackground(round(CARD, dp(24), BORDER));

        Calendar selected = Calendar.getInstance();
        selected.setTimeInMillis(calendarMonthStart(currentMonth));
        final int selectedYear = selected.get(Calendar.YEAR);
        final int selectedMonth = selected.get(Calendar.MONTH);
        final int[] shownYear = new int[]{selectedYear};

        LinearLayout yearRow = new LinearLayout(this);
        yearRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView prevYear = text("‹", 28, TEXT, true);
        prevYear.setGravity(Gravity.CENTER);
        prevYear.setBackground(round(CHIP, dp(16), BORDER));
        yearRow.addView(prevYear, new LinearLayout.LayoutParams(dp(42), dp(40)));
        TextView yearTitle = text(shownYear[0] + " 年", 20, TEXT, true);
        yearTitle.setGravity(Gravity.CENTER);
        yearRow.addView(yearTitle, new LinearLayout.LayoutParams(0, -2, 1));
        TextView nextYear = text("›", 28, TEXT, true);
        nextYear.setGravity(Gravity.CENTER);
        nextYear.setBackground(round(CHIP, dp(16), BORDER));
        yearRow.addView(nextYear, new LinearLayout.LayoutParams(dp(42), dp(40)));
        panel.addView(yearRow, marginLp(-1, -2, 0, 0, 0, dp(12)));

        LinearLayout monthsBox = new LinearLayout(this);
        monthsBox.setOrientation(LinearLayout.VERTICAL);
        panel.addView(monthsBox);

        final Runnable[] render = new Runnable[1];
        render[0] = () -> {
            yearTitle.setText(shownYear[0] + " 年");
            monthsBox.removeAllViews();
            for (int rowIndex = 0; rowIndex < 3; rowIndex++) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                for (int col = 0; col < 4; col++) {
                    final int month = rowIndex * 4 + col;
                    boolean picked = shownYear[0] == selectedYear && month == selectedMonth;
                    Button b = smallChip((month + 1) + " 月", picked ? 0xFFFFCC5C : CHIP, picked ? 0xFF1C1C1C : TEXT);
                    b.setTextSize(14);
                    b.setOnClickListener(v -> {
                        Calendar c = Calendar.getInstance();
                        c.set(Calendar.YEAR, shownYear[0]);
                        c.set(Calendar.MONTH, month);
                        c.set(Calendar.DAY_OF_MONTH, 1);
                        c.set(Calendar.HOUR_OF_DAY, 0);
                        c.set(Calendar.MINUTE, 0);
                        c.set(Calendar.SECOND, 0);
                        c.set(Calendar.MILLISECOND, 0);
                        dialog.dismiss();
                        if (callback != null) callback.onPick(c.getTimeInMillis());
                    });
                    row.addView(b, marginLp(0, dp(44), dp(4), dp(4), dp(4), dp(4), 1));
                }
                monthsBox.addView(row);
            }
        };
        prevYear.setOnClickListener(v -> { shownYear[0]--; render[0].run(); });
        nextYear.setOnClickListener(v -> { shownYear[0]++; render[0].run(); });
        render[0].run();
        showCustomDialog(dialog, panel);
    }

    private interface MonthPickCallback {
        void onPick(long monthMillis);
    }

    private String formatCalendarDate(long time) {
        return new SimpleDateFormat("yyyy/MM/dd EEEE", Locale.TAIWAN).format(new Date(time));
    }

    private String moneyShort(int amount) {
        if (amount >= 100000) return "$" + Math.round(amount / 1000f) + "k";
        if (amount >= 10000) return "$" + (amount / 1000) + "." + ((amount % 1000) / 100) + "k";
        return "$" + String.format(Locale.TAIWAN, "%,d", amount);
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
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setMinimumHeight(dp(72));

        boolean income = "income".equals(tx.direction);
        String rowIcon = !empty(tx.icon) ? tx.icon : (income ? "💰" : iconFor(tx.category));
        int iconBg = iconBgFor(tx);
        TextView ic = text(rowIcon, 22, TEXT, false);
        ic.setGravity(Gravity.CENTER);
        ic.setBackground(round(iconBg, dp(18)));
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
        row.addView(right, new LinearLayout.LayoutParams(dp(116), -2));

        row.setOnClickListener(v -> showTransactionDetail(tx));
        row.setOnLongClickListener(v -> { showEditTransactionDialog(tx); return true; });
        return row;
    }

    private void addDailyRecordGroups(LinearLayout parent, List<Transaction> txs) {
        if (parent == null || txs == null || txs.isEmpty()) return;
        ArrayList<Transaction> dayItems = new ArrayList<>();
        long currentDay = -1L;
        for (Transaction t : txs) {
            if (t == null) continue;
            long day = calendarDayStart(t.timeMillis);
            if (currentDay < 0) currentDay = day;
            if (day != currentDay) {
                parent.addView(dailyRecordGroup(currentDay, dayItems), marginLp(-1, -2, 0, 0, 0, dp(10)));
                dayItems = new ArrayList<>();
                currentDay = day;
            }
            dayItems.add(t);
        }
        if (!dayItems.isEmpty()) {
            parent.addView(dailyRecordGroup(currentDay, dayItems), marginLp(-1, -2, 0, 0, 0, dp(10)));
        }
    }

    private View dailyRecordGroup(long dayStart, List<Transaction> txs) {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setPadding(0, 0, 0, dp(4));
        group.setBackground(round(isDarkMode() ? 0xFF17212C : 0xFFFFFFFF, dp(18), BORDER));
        elevate(group, 1);

        int expense = totalFor(txs, "expense");
        int income = totalFor(txs, "income");
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(12), dp(14), dp(10));
        TextView date = text(formatRecordDayHeader(dayStart), 16, TEXT, true);
        date.setSingleLine(true);
        date.setEllipsize(TextUtils.TruncateAt.END);
        header.addView(date, new LinearLayout.LayoutParams(0, -2, 1));
        String totalText = expense > 0 ? "- " + TransactionStore.money(expense) : (income > 0 ? "+ " + TransactionStore.money(income) : TransactionStore.money(0));
        TextView total = text(totalText, 16, expense > 0 ? ORANGE : GREEN, true);
        total.setGravity(Gravity.RIGHT);
        header.addView(total, new LinearLayout.LayoutParams(dp(126), -2));
        group.addView(header);

        int rowIndex = 0;
        for (Transaction t : txs) {
            if (rowIndex++ > 0) {
                View line = new View(this);
                line.setBackgroundColor(BORDER);
                group.addView(line, marginLp(-1, dp(1), dp(56), 0, dp(14), 0));
            }
            group.addView(dailyRecordMiniRow(t));
        }
        return group;
    }

    private View dailyRecordMiniRow(Transaction tx) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(8), dp(14), dp(8));
        row.setMinimumHeight(dp(58));
        boolean income = "income".equals(tx.direction);

        TextView ic = text(!empty(tx.icon) ? tx.icon : (income ? "💰" : iconFor(tx.category)), 22, income ? GREEN : EXPENSE_RED, false);
        ic.setGravity(Gravity.CENTER);
        row.addView(ic, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setPadding(dp(10), 0, dp(8), 0);
        TextView title = text(recordTitle(tx), 15, TEXT, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        TextView sub = text(sourceDisplay(tx), 12, MUTED, false);
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        mid.addView(title);
        mid.addView(sub, marginLp(-1, -2, 0, dp(2), 0, 0));
        row.addView(mid, new LinearLayout.LayoutParams(0, -2, 1));

        TextView amount = text((income ? "+ " : "- ") + TransactionStore.money(tx.amount), 15, income ? GREEN : TEXT, true);
        amount.setGravity(Gravity.RIGHT);
        row.addView(amount, new LinearLayout.LayoutParams(dp(104), -2));
        row.setOnClickListener(v -> showTransactionDetail(tx));
        row.setOnLongClickListener(v -> { showEditTransactionDialog(tx); return true; });
        return row;
    }

    private String formatRecordDayHeader(long dayStart) {
        return new SimpleDateFormat("yyyy/MM/dd EEEE", Locale.TAIWAN).format(new Date(dayStart));
    }

    private String recordTitle(Transaction tx) {
        if (tx == null) return "記帳紀錄";
        String cat = cleanCategory(tx.category);
        if (!cat.isEmpty()) return trimUi(cat, 12);
        String merchant = tx.merchant == null ? "" : tx.merchant.trim();
        if (!merchant.isEmpty() && !looksLikeAutoSourceName(merchant)) return trimUi(merchant, 12);
        String inferred = inferredTitleFromRaw(tx);
        if (!inferred.isEmpty()) return inferred;
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
        String source = compactForUi(tx.source == null ? "" : tx.source);
        String merchant = compactForUi(tx.merchant == null ? "" : tx.merchant);
        String stored = source.equals(merchant) ? source : compactForUi(source + " " + merchant);
        if (stored.isEmpty()) {
            String hint = sourceHintFromRaw(tx);
            return hint.isEmpty() ? "自動通知" : hint;
        }
        return trimUi(stored, 10);
    }

    private String inferredTitleFromRaw(Transaction tx) {
        String raw = compactForUi(tx == null ? "" : tx.raw);
        if (raw.contains("轉帳") || raw.contains("匯款") || raw.contains("轉給")) return "轉帳";
        if (raw.contains("Google Pay") || raw.contains("Google 錢包") || raw.contains("Google錢包")) return "Google Pay";
        if (raw.contains("LINE Pay") || raw.contains("LINEPay") || raw.contains("LINE錢包")) return "LINE Pay";
        if (raw.contains("發票") || raw.contains("載具")) return "電子發票";
        if (raw.contains("中國信託") || raw.contains("中信") || raw.toLowerCase(Locale.ROOT).contains("ctbc")) return "銀行通知";
        return "";
    }

    private String sourceHintFromRaw(Transaction tx) {
        String raw = compactForUi(tx == null ? "" : tx.raw);
        String lower = raw.toLowerCase(Locale.ROOT);
        if (raw.contains("Google Pay") || raw.contains("Google 錢包") || raw.contains("Google錢包") || lower.contains("walletnfcrel")) return "Google 錢包";
        if (raw.contains("LINE Pay") || raw.contains("LINEPay") || raw.contains("LINE錢包")) return "LINE Pay";
        if (raw.contains("中國信託") || raw.contains("中信") || lower.contains("ctbc")) return "中國信託";
        if (raw.contains("發票") || raw.contains("載具")) return "電子發票";
        return "";
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
        addCalculatorPad(box, amountInput);

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
            TextView bonusText = text("加回目前剩餘餘額", 13, TEXT, true);
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
            // V31：快速常用項目只填分類；店家 / 項目如果沒有手動輸入就保持空白。
            Transaction tx = new Transaction(selectedTime[0], amount, income ? "income" : "expense", "手動新增", merchant, category, note, "manual-" + selectedTime[0] + "-" + System.currentTimeMillis());
            TransactionStore.add(this, tx);
            if (income && addIncomeToBudget.isChecked()) {
                AppSettings.addToCurrentRemainingBalance(this, amount);
                Toast.makeText(this, "已新增收入，並加回目前剩餘餘額", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "已新增" + (income ? "收入 " : "支出 ") + TransactionStore.money(amount), Toast.LENGTH_SHORT).show();
            }
            homeMonthMillis = selectedTime[0];
            showHome();
        });
        box.addView(save, marginLp(-1, dp(54), 0, dp(12), 0, 0));

        setPage(scroll);
    }

    private void addCalculatorPad(LinearLayout box, EditText amountInput) {
        LinearLayout calc = card();
        calc.setPadding(dp(12), dp(12), dp(12), dp(12));
        calc.setBackground(round(isDarkMode() ? 0xFF111923 : 0xFFF8FAFD, dp(18), BORDER));
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text("計算機", 14, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        head.addView(text("分攤 / 加減乘除", 12, MUTED, false));
        calc.addView(head, marginLp(-1, -2, 0, 0, 0, dp(8)));

        final String[] expr = new String[]{""};
        TextView display = text("輸入算式，例如 120÷3", 18, TEXT, true);
        display.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        display.setSingleLine(true);
        display.setEllipsize(TextUtils.TruncateAt.START);
        display.setPadding(dp(12), 0, dp(12), 0);
        display.setBackground(round(isDarkMode() ? 0xFF17212C : 0xFFFFFFFF, dp(14), BORDER));
        calc.addView(display, marginLp(-1, dp(46), 0, 0, 0, dp(8)));

        String[][] rows = new String[][]{
                {"7", "8", "9", "÷"},
                {"4", "5", "6", "×"},
                {"1", "2", "3", "-"},
                {"0", "00", "C", "+"},
                {"⌫", "=", "套用"}
        };
        for (String[] labels : rows) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (String label : labels) {
                int bg = ("=".equals(label) || "套用".equals(label)) ? 0xFFFFEEE6 : CHIP;
                int fg = ("=".equals(label) || "套用".equals(label)) ? ORANGE : TEXT;
                Button b = smallChip(label, bg, fg);
                b.setTextSize("套用".equals(label) ? 13 : 16);
                b.setOnClickListener(v -> handleCalcInput(label, expr, display, amountInput));
                row.addView(b, marginLp(0, dp(42), dp(3), dp(3), dp(3), dp(3), 1));
            }
            calc.addView(row);
        }
        box.addView(calc, marginLp(-1, -2, 0, 0, 0, dp(10)));
    }

    private void handleCalcInput(String label, String[] expr, TextView display, EditText amountInput) {
        if ("C".equals(label)) {
            expr[0] = "";
        } else if ("⌫".equals(label)) {
            if (expr[0].length() > 0) expr[0] = expr[0].substring(0, expr[0].length() - 1);
        } else if ("=".equals(label) || "套用".equals(label)) {
            int value = evaluateMoneyExpression(expr[0]);
            if (value > 0) {
                amountInput.setText(String.valueOf(value));
                expr[0] = String.valueOf(value);
            }
        } else {
            expr[0] += label;
        }
        display.setText(expr[0].isEmpty() ? "輸入算式，例如 120÷3" : expr[0]);
    }

    private int evaluateMoneyExpression(String raw) {
        if (raw == null) return 0;
        String s = raw.replace("×", "*").replace("÷", "/").replace(" ", "");
        if (s.isEmpty()) return 0;
        double total = 0d;
        double last = 0d;
        char op = '+';
        StringBuilder number = new StringBuilder();
        for (int i = 0; i <= s.length(); i++) {
            char ch = i < s.length() ? s.charAt(i) : '+';
            boolean operator = ch == '+' || ch == '-' || ch == '*' || ch == '/';
            if (!operator) {
                if ((ch >= '0' && ch <= '9') || ch == '.') number.append(ch);
                continue;
            }
            if (number.length() == 0) {
                op = ch;
                continue;
            }
            double value;
            try { value = Double.parseDouble(number.toString()); } catch (Exception e) { return 0; }
            if (op == '+') {
                total += last;
                last = value;
            } else if (op == '-') {
                total += last;
                last = -value;
            } else if (op == '*') {
                last *= value;
            } else if (op == '/') {
                if (value == 0d) return 0;
                last /= value;
            }
            op = ch;
            number.setLength(0);
        }
        double result = total + last;
        if (Double.isNaN(result) || Double.isInfinite(result) || result <= 0d) return 0;
        return Math.max(0, Math.round((float) result));
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
        List<String> presets = AppSettings.getSortedQuickItems(this, income);
        if (presets == null || presets.isEmpty()) {
            presets = new ArrayList<>();
            String[] defaults = income ? new String[]{"零用錢", "薪水", "打工", "退款", "紅包", "獎金"} : new String[]{"飲料", "點心", "早餐", "午餐", "晚餐", "交通", "停車", "全聯"};
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
                AppSettings.incrementQuickUsage(this, income, name);
                category.setText(name);
                merchant.setText("");
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(86), dp(38));
            lp.setMargins(0, 0, dp(8), 0);
            row.addView(chip, lp);
        }
        Button add = smallChip("＋新增", isDarkMode() ? 0xFF203344 : 0xFFE9F8FF, isDarkMode() ? 0xFF7EE8FF : 0xFF0B6B88);
        add.setTextSize(12);
        add.setMinHeight(dp(36));
        add.setPadding(dp(10), 0, dp(10), 0);
        add.setOnClickListener(v -> showAddQuickItemDialog(income));
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(dp(86), dp(38));
        addLp.setMargins(0, 0, dp(8), 0);
        row.addView(add, addLp);
        hsv.addView(row, new HorizontalScrollView.LayoutParams(-2, dp(40)));
        box.addView(hsv, marginLp(-1, dp(42), 0, 0, 0, dp(4)));
    }

    private void showAddQuickItemDialog(boolean income) {
        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        LinearLayout panel = dialogPanel(dp(24));
        panel.addView(text(income ? "新增收入常用項目" : "新增支出常用項目", 20, TEXT, true));
        panel.addView(text("只需要輸入名稱，不會固定金額。新增後會出現在快速常用項目的最後，使用次數多才會往前排。", 13, MUTED, false), marginLp(-1, -2, 0, dp(4), 0, dp(12)));
        final EditText input = edit(income ? "例如 薪水、零用錢" : "例如 早餐、飲料、交通", false);
        input.setSingleLine(true);
        panel.addView(input, marginLp(-1, dp(52), 0, 0, 0, dp(14)));
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button cancel = dialogBtn("取消");
        Button save = bigAction("✓ 儲存", 0xFFFF5A45, 0xFFFF8B2B);
        actions.addView(cancel, marginLp(0, dp(48), 0, 0, dp(8), 0, 1));
        actions.addView(save, marginLp(0, dp(48), dp(8), 0, 0, 0, 1));
        panel.addView(actions);
        cancel.setOnClickListener(v -> dialog.dismiss());
        save.setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "請輸入常用項目名稱", Toast.LENGTH_SHORT).show();
                return;
            }
            AppSettings.addQuickItem(this, income, name);
            Toast.makeText(this, "已新增常用項目：" + name, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            showManual(income ? "income" : "expense");
        });
        showCustomDialog(dialog, panel);
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
        List<String> presets = AppSettings.getSortedQuickItems(this, income);
        for (String line : presets) {
            String[] p = line.split("\\|");
            final String name = p.length > 0 ? p[0].trim() : line.trim();
            final String presetCategory = p.length > 2 ? p[2].trim() : (income ? guessIncomeCategory(name) : guessCategory(name));
            if (name.isEmpty()) continue;
            Button chip = presetCard(name, income ? 0xFFF2F0FF : 0xFFFFF3EA, income ? PURPLE : ORANGE);
            chip.setOnClickListener(v -> {
                AppSettings.incrementQuickUsage(this, income, name);
                merchant.setText("");
                category.setText(name);
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
                merchant.setText("");
                category.setText(income ? r : r);
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
        if (s == null) return "未分類";
        String clean = s.trim();
        return clean.isEmpty() ? "未分類" : clean;
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
        int rawExpense = TransactionStore.expenseBetween(this, start, end);
        int income = TransactionStore.incomeBetween(this, start, end);
        boolean currentMonthReport = "month".equals(reportRange);
        int budget = Math.max(1, AppSettings.getMonthlyUsableBudget(this) * reportMonthFactor());
        int remainingBudget = currentMonthReport ? Math.max(0, AppSettings.getCurrentRemainingBalance(this)) : Math.max(0, budget - rawExpense);
        int expense = currentMonthReport ? Math.max(0, budget - remainingBudget) : rawExpense;
        int balance = currentMonthReport ? remainingBudget : income - rawExpense;

        int mainValue;
        int remainValue;
        int mainColor;
        String centerLabel;
        String mainTitle;
        String segA;
        String segB;
        if ("income".equals(reportType)) {
            mainValue = income;
            remainValue = Math.max(0, Math.max(budget, income) - income);
            mainColor = GREEN;
            centerLabel = "收入";
            mainTitle = "總收入";
            segA = "收入";
            segB = "預算參考";
        } else if ("balance".equals(reportType)) {
            mainValue = Math.max(0, remainingBudget);
            remainValue = Math.max(0, expense);
            mainColor = GREEN;
            centerLabel = "結餘";
            mainTitle = "剩餘預算";
            segA = "剩餘預算";
            segB = "已花費";
        } else {
            mainValue = expense;
            remainValue = Math.max(0, remainingBudget);
            mainColor = EXPENSE_RED;
            centerLabel = "支出";
            mainTitle = "總支出";
            segA = "支出";
            segB = "剩餘預算";
        }

        LinearLayout chartCard = card();
        chartCard.setGravity(Gravity.CENTER);
        DonutChartView donut = new DonutChartView(this);
        donut.setDarkMode(isDarkMode());
        donut.setCenterLabel(centerLabel);
        donut.setData(mainValue, remainValue, 0, AppSettings.getPalette(this));
        donut.setSegmentLabels(segA, segB, "");
        chartCard.addView(donut, new LinearLayout.LayoutParams(dp(230), dp(230)));
        int displayAmount = "balance".equals(reportType) ? remainingBudget : mainValue;
        TextView centerSummary = text(mainTitle + "  " + TransactionStore.money(displayAmount), 18, mainColor, true);
        centerSummary.setGravity(Gravity.CENTER);
        chartCard.addView(centerSummary, marginLp(-1, -2, 0, dp(6), 0, 0));
        TextView budgetHint = text("已花費 " + TransactionStore.money(expense) + "｜剩餘 " + TransactionStore.money(remainingBudget) + "｜本月預算 " + TransactionStore.money(budget), 13, MUTED, false);
        budgetHint.setGravity(Gravity.CENTER);
        chartCard.addView(budgetHint, marginLp(-1, -2, 0, dp(4), 0, dp(6)));
        box.addView(chartCard, marginLp(-1, -2, 0, 0, 0, dp(12)));

        LinearLayout summary = card();
        summary.addView(text(reportRangeLabel() + "摘要", 18, TEXT, true));
        summary.addView(text("支出：" + TransactionStore.money(expense) + "　收入：" + TransactionStore.money(income), 15, TEXT, false));
        summary.addView(text("剩餘預算：" + TransactionStore.money(remainingBudget), 15, GREEN, true));
        if ("month".equals(reportRange)) {
            summary.addView(text("月底預估花費：" + TransactionStore.money(TransactionStore.forecastMonthExpense(this)), 14, MUTED, false));
        } else if ("custom".equals(reportRange)) {
            summary.addView(text("自訂目前先顯示最近 30 天，之後可再做日期區間選擇。", 13, MUTED, false));
        }
        box.addView(summary, marginLp(-1, -2, 0, 0, 0, dp(12)));

        addReportInsights(box, start, end, expense, income, budget, remainingBudget);

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
        LinearLayout toolRow2 = new LinearLayout(this);
        toolRow2.setOrientation(LinearLayout.HORIZONTAL);
        Button budgetBtn = smallChip("設定預算", CHIP, ORANGE);
        budgetBtn.setOnClickListener(v -> showBudgetDialog());
        Button simulator = smallChip("財務模擬", CHIP, GREEN);
        simulator.setOnClickListener(v -> showFinanceSimulator());
        toolRow2.addView(budgetBtn, marginLp(0, dp(50), 0, dp(8), dp(5), 0, 1));
        toolRow2.addView(simulator, marginLp(0, dp(50), dp(5), dp(8), 0, 0, 1));
        tools.addView(toolRow2);
        box.addView(tools);

        setPage(scroll);
    }

    private void addReportInsights(LinearLayout box, long start, long end, int expense, int income, int budget, int remainingBudget) {
        List<Transaction> txs = transactionsBetween(start, end);
        int count = txs.size();
        int autoCount = 0;
        int manualCount = 0;
        Transaction biggestExpense = null;
        for (Transaction t : txs) {
            if (t.hash != null && t.hash.startsWith("manual-")) manualCount++; else autoCount++;
            if ("expense".equals(t.direction) && (biggestExpense == null || t.amount > biggestExpense.amount)) biggestExpense = t;
        }

        int days = Math.max(1, reportElapsedDays(start, end));
        int avgDaily = Math.round(expense / (float) days);
        int daysLeft = Math.max(1, daysLeftInReport(end));
        int safeDaily = Math.max(0, Math.round(remainingBudget / (float) daysLeft));
        int forecast = "month".equals(reportRange) ? TransactionStore.forecastMonthExpense(this) : Math.round(avgDaily * reportMonthFactor() * 30f);

        LinearLayout rhythm = card();
        rhythm.addView(text("花費節奏", 18, TEXT, true));
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(metricBlock("平均每天", TransactionStore.money(avgDaily), EXPENSE_RED), marginLp(0, -2, 0, dp(10), dp(5), 0, 1));
        row1.addView(metricBlock("接下來每天可花", TransactionStore.money(safeDaily), GREEN), marginLp(0, -2, dp(5), dp(10), 0, 0, 1));
        rhythm.addView(row1);
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(metricBlock("本區間紀錄", count + " 筆", TEXT), marginLp(0, -2, 0, dp(8), dp(5), 0, 1));
        row2.addView(metricBlock("月底預估", TransactionStore.money(forecast), forecast > budget ? EXPENSE_RED : GREEN), marginLp(0, -2, dp(5), dp(8), 0, 0, 1));
        rhythm.addView(row2);
        rhythm.addView(text("自動 " + autoCount + " 筆｜手動 " + manualCount + " 筆｜收入 " + TransactionStore.money(income), 13, MUTED, false), marginLp(-1, -2, 0, dp(10), 0, 0));
        box.addView(rhythm, marginLp(-1, -2, 0, 0, 0, dp(12)));

        LinearLayout categories = card();
        categories.addView(text("支出分類排行", 18, TEXT, true));
        List<CategoryTotal> totals = categoryTotals(txs);
        if (totals.isEmpty()) {
            categories.addView(text("這個區間還沒有支出分類資料。", 14, MUTED, false), marginLp(-1, -2, 0, dp(10), 0, 0));
        } else {
            int max = Math.max(1, totals.get(0).amount);
            int limit = Math.min(5, totals.size());
            for (int i = 0; i < limit; i++) {
                categories.addView(categoryBar(totals.get(i), max, expense), marginLp(-1, -2, 0, dp(10), 0, 0));
            }
        }
        box.addView(categories, marginLp(-1, -2, 0, 0, 0, dp(12)));

        LinearLayout notable = card();
        notable.addView(text("重點提醒", 18, TEXT, true));
        if (biggestExpense != null) {
            notable.addView(text("最大筆支出：" + TransactionStore.money(biggestExpense.amount) + "｜" + recordTitle(biggestExpense), 14, TEXT, true), marginLp(-1, -2, 0, dp(8), 0, dp(2)));
            notable.addView(text(TransactionStore.formatTime(biggestExpense.timeMillis) + "｜" + recordSubtitle(biggestExpense), 12, MUTED, false));
        } else {
            notable.addView(text("這個區間還沒有支出。", 14, MUTED, false), marginLp(-1, -2, 0, dp(8), 0, 0));
        }
        int saving = Math.max(0, budget - forecast);
        String advice = forecast > budget
                ? "照目前速度可能超出 " + TransactionStore.money(forecast - budget) + "，可以先檢查排行前兩名。"
                : "照目前速度可能剩 " + TransactionStore.money(saving) + "，可以考慮先存一部分。";
        notable.addView(text(advice, 13, forecast > budget ? EXPENSE_RED : GREEN, true), marginLp(-1, -2, 0, dp(10), 0, 0));
        box.addView(notable, marginLp(-1, -2, 0, 0, 0, dp(12)));
    }

    private LinearLayout metricBlock(String label, String value, int color) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(dp(12), dp(10), dp(12), dp(10));
        block.setBackground(round(isDarkMode() ? 0xFF111923 : 0xFFF7FAFC, dp(16), BORDER));
        block.addView(text(label, 12, MUTED, false));
        block.addView(text(value, 19, color, true), marginLp(-1, -2, 0, dp(3), 0, 0));
        return block;
    }

    private View categoryBar(CategoryTotal total, int max, int allExpense) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(text(total.name, 14, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        int pct = Math.round(total.amount * 100f / Math.max(1, allExpense));
        top.addView(text(TransactionStore.money(total.amount) + "  " + pct + "%", 13, MUTED, false));
        box.addView(top);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackground(round(isDarkMode() ? 0xFF273341 : 0xFFE8EEF5, dp(6)));
        View filled = new View(this);
        filled.setBackground(round(EXPENSE_RED, dp(6)));
        int filledWeight = Math.max(1, Math.round(total.amount * 100f / Math.max(1, max)));
        bar.addView(filled, new LinearLayout.LayoutParams(0, dp(8), filledWeight));
        View rest = new View(this);
        bar.addView(rest, new LinearLayout.LayoutParams(0, dp(8), Math.max(1, 100 - filledWeight)));
        box.addView(bar, marginLp(-1, dp(8), 0, dp(6), 0, 0));
        return box;
    }

    private List<Transaction> transactionsBetween(long start, long end) {
        ArrayList<Transaction> out = new ArrayList<>();
        for (Transaction t : TransactionStore.getAll(this)) {
            if (t.timeMillis >= start && t.timeMillis < end) out.add(t);
        }
        return out;
    }

    private int totalFor(List<Transaction> txs, String direction) {
        int sum = 0;
        if (txs == null) return 0;
        for (Transaction t : txs) {
            if (t != null && direction.equals(t.direction)) sum += Math.max(0, t.amount);
        }
        return sum;
    }

    private int reportElapsedDays(long start, long end) {
        long until = Math.min(System.currentTimeMillis(), end);
        return Math.max(1, (int) Math.ceil((until - start) / (24f * 60f * 60f * 1000f)));
    }

    private int daysLeftInReport(long end) {
        long left = Math.max(0, end - System.currentTimeMillis());
        return Math.max(1, (int) Math.ceil(left / (24f * 60f * 60f * 1000f)));
    }

    private List<CategoryTotal> categoryTotals(List<Transaction> txs) {
        ArrayList<CategoryTotal> totals = new ArrayList<>();
        for (Transaction t : txs) {
            if (!"expense".equals(t.direction)) continue;
            String name = cleanCategory(t.category);
            if (name.isEmpty()) name = "未分類";
            CategoryTotal found = null;
            for (CategoryTotal c : totals) {
                if (c.name.equals(name)) { found = c; break; }
            }
            if (found == null) {
                found = new CategoryTotal(name);
                totals.add(found);
            }
            found.amount += Math.max(0, t.amount);
        }
        Collections.sort(totals, new Comparator<CategoryTotal>() {
            @Override public int compare(CategoryTotal a, CategoryTotal b) {
                return b.amount - a.amount;
            }
        });
        return totals;
    }

    private static class CategoryTotal {
        String name;
        int amount;
        CategoryTotal(String name) { this.name = name; }
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
        Calendar c = Calendar.getInstance();
        if ("year".equals(reportRange)) {
            c.add(Calendar.YEAR, 1);
            c.set(Calendar.MONTH, Calendar.JANUARY);
            c.set(Calendar.DAY_OF_MONTH, 1);
        } else if ("six".equals(reportRange)) {
            c.add(Calendar.MONTH, 1);
            c.set(Calendar.DAY_OF_MONTH, 1);
        } else if ("custom".equals(reportRange)) {
            return System.currentTimeMillis() + 60L * 1000L;
        } else {
            c.add(Calendar.MONTH, 1);
            c.set(Calendar.DAY_OF_MONTH, 1);
        }
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
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
        ImageView avatar = new ImageView(this);
        Bitmap avatarBitmap = loadProfileAvatarBitmap(78);
        avatar.setImageBitmap(avatarBitmap != null ? avatarBitmap : defaultAvatarBitmap(78));
        avatar.setBackground(round(isDarkMode() ? 0xFF2A333F : 0xFFF2F2F2, dp(42), BORDER));
        profile.addView(avatar, new LinearLayout.LayoutParams(dp(78), dp(78)));
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(14), 0, 0, 0);
        info.addView(text(profileName(), 18, TEXT, true));
        info.addView(text("點頭像或名稱可編輯", 12, MUTED, false), marginLp(-1, -2, 0, dp(2), 0, 0));
        Button vip = smallChip("升級 VIP", CHIP, TEXT);
        info.addView(vip, marginLp(dp(120), dp(38), 0, dp(8), 0, 0));
        profile.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        profile.setOnClickListener(v -> { closeDialogs(); showProfileDialog(); });
        panel.addView(profile, marginLp(-1, -2, 0, 0, 0, dp(14)));

        LinearLayout topAction = new LinearLayout(this);
        topAction.setOrientation(LinearLayout.HORIZONTAL);
        Button search = smallChip("⌕", CHIP, TEXT);
        search.setOnClickListener(v -> { closeDialogs(); showToolSearchDialog(); });
        Button streak = smallChip("🔥  連續記帳", 0xFFFF7076, 0xFFFFFFFF);
        streak.setTextSize(16);
        topAction.addView(search, new LinearLayout.LayoutParams(dp(76), dp(58)));
        topAction.addView(streak, new LinearLayout.LayoutParams(0, dp(58), 1));
        panel.addView(topAction, marginLp(-1, -2, 0, 0, 0, dp(16)));

        panel.addView(sideMenuButton("＋", "快速新增支出", v -> showManual("expense")));
        panel.addView(sideMenuButton("＋", "快速新增收入", v -> showManual("income")));
        panel.addView(sideMenuButton("◎", "設定目前剩餘", v -> showRemainingBalanceDialog()));
        panel.addView(sideMenuButton("💸", "欠款紀錄", v -> showDebtTracker()));
        panel.addView(sideMenuButton("💱", "匯率換算", v -> showExchangeConverter()));
        panel.addView(sideMenuButton("▦", "載具與桌面小工具", v -> showWidgetImageSettingsDialog()));
        panel.addView(sideMenuButton("🏷", "固定收支", v -> showFixedRecordsManager()));
        panel.addView(sideMenuButton("🌐", "財務模擬", v -> showFinanceSimulator()));
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

    private class MenuAction {
        String icon, title, subtitle, keywords;
        View.OnClickListener listener;
        MenuAction(String icon, String title, String subtitle, String keywords, View.OnClickListener listener) {
            this.icon = icon;
            this.title = title;
            this.subtitle = subtitle;
            this.keywords = keywords;
            this.listener = listener;
        }
    }

    private List<MenuAction> menuActions() {
        ArrayList<MenuAction> actions = new ArrayList<>();
        actions.add(new MenuAction("＋", "快速新增支出", "手動補一筆花費", "支出 花費 手動 新增 記帳", v -> showManual("expense")));
        actions.add(new MenuAction("＋", "快速新增收入", "薪水、零用錢、退款都可以補", "收入 薪水 零用錢 手動 新增", v -> showManual("income")));
        actions.add(new MenuAction("÷", "記帳計算機", "加減乘除後直接填入金額", "計算機 分攤 加減乘除 算錢 朋友", v -> showManual("expense")));
        actions.add(new MenuAction("◎", "設定目前剩餘", "更新現在實際還剩多少錢", "餘額 剩餘 預算 現在 剩多少", v -> showRemainingBalanceDialog()));
        actions.add(new MenuAction("◔", "財務報表", "支出、收入、結餘與分類分析", "統計 報表 分析 支出 收入 結餘", v -> showStats()));
        actions.add(new MenuAction("💸", "欠款紀錄", "朋友欠款、還款扣除", "欠款 借錢 朋友 還款 同學", v -> showDebtTracker()));
        actions.add(new MenuAction("💱", "匯率換算", "台幣與外幣快速換算", "匯率 外幣 換算 美金 日幣", v -> showExchangeConverter()));
        actions.add(new MenuAction("▦", "設定載具條碼", "小工具顯示手機載具條碼", "載具 條碼 發票 小工具", v -> showCarrierBarcodeDialog()));
        actions.add(new MenuAction("🖼", "圖片小工具設定", "選圖片、裁切桌面小工具照片", "圖片 小工具 照片 裁切", v -> showWidgetImageSettingsDialog()));
        actions.add(new MenuAction("🏷", "固定收支", "房租、訂閱、薪水一鍵新增", "固定 收支 房租 訂閱 薪水", v -> showFixedRecordsManager()));
        actions.add(new MenuAction("🌐", "財務模擬", "試算月底會剩多少", "模擬 預估 月底 預算", v -> showFinanceSimulator()));
        actions.add(new MenuAction("⇄", "掃描重複", "移除疑似同一筆的通知紀錄", "重複 掃描 除錯 防重複", v -> {
            int removed = TransactionStore.autoFixDuplicates(this);
            Toast.makeText(this, "已移除 " + removed + " 筆疑似重複資料", Toast.LENGTH_LONG).show();
            refreshCurrent();
        }));
        actions.add(new MenuAction("CSV", "匯出 CSV", "匯出到試算表", "csv 匯出 試算表 excel", v -> shareCsv()));
        actions.add(new MenuAction("⤓", "備份資料", "匯出 JSON 備份", "備份 json 匯出", v -> shareBackup()));
        actions.add(new MenuAction("⚙", "功能設定", "通知、分類、外觀與備份", "設定 通知 分類 外觀 備份", v -> showSettings()));
        return actions;
    }

    private void showToolSearchDialog() {
        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        LinearLayout panel = dialogPanel(dp(30));
        panel.addView(text("功能搜尋", 21, TEXT, true), marginLp(-1, -2, 0, 0, 0, dp(8)));
        final EditText input = edit("輸入：預算、載具、CSV、欠款...", false);
        input.setSingleLine(true);
        panel.addView(input, marginLp(-1, dp(54), 0, 0, 0, dp(12)));

        ScrollView resultsScroll = new ScrollView(this);
        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        resultsScroll.addView(results, new ScrollView.LayoutParams(-1, -2));
        panel.addView(resultsScroll, marginLp(-1, dp(360), 0, 0, 0, dp(12)));

        Button close = pill("關閉", CHIP, TEXT);
        panel.addView(close, marginLp(-1, dp(50), 0, 0, 0, 0));
        close.setOnClickListener(v -> dialog.dismiss());

        final Runnable[] refresh = new Runnable[1];
        refresh[0] = new Runnable() {
            @Override public void run() {
                results.removeAllViews();
                String q = input.getText().toString().trim().toLowerCase(Locale.ROOT);
                int shown = 0;
                for (MenuAction a : menuActions()) {
                    String hay = (a.title + " " + a.subtitle + " " + a.keywords).toLowerCase(Locale.ROOT);
                    if (!q.isEmpty() && !hay.contains(q)) continue;
                    results.addView(searchResultRow(a, dialog), marginLp(-1, -2, 0, 0, 0, dp(8)));
                    if (++shown >= 8) break;
                }
                if (shown == 0) {
                    TextView empty = text("沒有找到功能。可以試試：預算、載具、備份、欠款、CSV。", 14, MUTED, false);
                    empty.setGravity(Gravity.CENTER);
                    empty.setPadding(dp(10), dp(26), dp(10), dp(26));
                    results.addView(empty);
                }
            }
        };
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { refresh[0].run(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        refresh[0].run();
        showCustomDialog(dialog, panel);
    }

    private View searchResultRow(MenuAction action, AlertDialog dialog) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(round(CHIP, dp(16), BORDER));
        TextView icon = text(action.icon, 18, ORANGE, true);
        icon.setGravity(Gravity.CENTER);
        row.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(action.title, 16, TEXT, true));
        texts.addView(text(action.subtitle, 12, MUTED, false), marginLp(-1, -2, 0, dp(2), 0, 0));
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        row.setOnClickListener(v -> {
            dialog.dismiss();
            closeDialogs();
            if (action.listener != null) action.listener.onClick(v);
        });
        return row;
    }



    private interface CurrencyPickCallback {
        void onPicked(String code);
    }

    private void showExchangeConverter() {
        tab = 3;
        applyModeColors();
        ScrollView scroll = pageBase();
        LinearLayout box = pageBox(scroll);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 32, TEXT, false);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> showHome());
        top.addView(back, new LinearLayout.LayoutParams(dp(38), -2));
        TextView title = text("匯率換算", 22, TEXT, true);
        title.setGravity(Gravity.CENTER);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView refresh = text("↻", 25, TEXT, true);
        refresh.setGravity(Gravity.CENTER);
        top.addView(refresh, new LinearLayout.LayoutParams(dp(44), -2));
        box.addView(top, marginLp(-1, -2, 0, 0, 0, dp(12)));

        LinearLayout ratesCard = section("常用幣種參考");
        ratesCard.addView(text("以下是台幣 1 元可換多少外幣｜" + CurrencyRateStore.updatedText(this), 12, MUTED, false), marginLp(-1, -2, 0, 0, 0, dp(8)));
        String[] codes = CurrencyRateStore.commonCodes();
        Map<String, Double> rateMap = CurrencyRateStore.rates(this);
        DecimalFormat df = new DecimalFormat("#,##0.######");
        for (int i = 0; i < codes.length; i += 3) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int j = 0; j < 3 && i + j < codes.length; j++) {
                String code = codes[i + j];
                double rate = rateMap.containsKey(code) ? rateMap.get(code) : CurrencyRateStore.convert(this, 1, "TWD", code);
                String sub = "TWD".equals(code) ? "1 台幣" : df.format(rate) + " " + CurrencyRateStore.name(code);
                Button chip = smallChip(code + "\n" + sub, isDarkMode() ? 0xFF202A36 : 0xFFF6FAFF, TEXT);
                chip.setTextSize(12);
                chip.setSingleLine(false);
                chip.setMaxLines(2);
                row.addView(chip, marginLp(0, dp(58), dp(3), dp(4), dp(3), dp(4), 1));
            }
            ratesCard.addView(row);
        }
        box.addView(ratesCard, marginLp(-1, -2, 0, 0, 0, dp(12)));

        LinearLayout converter = section("雙向換算");
        converter.addView(text("上面或下面都可以輸入，另一格會自動換算。匯率是參考值，實際刷卡仍以銀行入帳為準。", 13, MUTED, false), marginLp(-1, -2, 0, 0, 0, dp(10)));

        final String[] from = new String[]{"TWD"};
        final String[] to = new String[]{"JPY"};
        final boolean[] updating = new boolean[]{false};
        final String[] active = new String[]{"from"};

        LinearLayout fromRow = new LinearLayout(this);
        fromRow.setOrientation(LinearLayout.HORIZONTAL);
        Button fromBtn = pill(currencyButtonText(from[0]), CHIP, TEXT);
        EditText fromAmount = edit("輸入金額", false);
        fromAmount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        fromAmount.setTextSize(18);
        fromRow.addView(fromBtn, marginLp(dp(112), dp(56), 0, 0, dp(8), 0));
        fromRow.addView(fromAmount, new LinearLayout.LayoutParams(0, dp(56), 1));
        converter.addView(fromRow, marginLp(-1, -2, 0, 0, 0, dp(10)));

        LinearLayout toRow = new LinearLayout(this);
        toRow.setOrientation(LinearLayout.HORIZONTAL);
        Button toBtn = pill(currencyButtonText(to[0]), CHIP, TEXT);
        EditText toAmount = edit("換算結果", false);
        toAmount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        toAmount.setTextSize(18);
        toRow.addView(toBtn, marginLp(dp(112), dp(56), 0, 0, dp(8), 0));
        toRow.addView(toAmount, new LinearLayout.LayoutParams(0, dp(56), 1));
        converter.addView(toRow, marginLp(-1, -2, 0, 0, 0, dp(8)));

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        Button swap = smallChip("⇅ 對調", CHIP, TEXT);
        Button update = bigAction("更新今日匯率", 0xFF42C7E8, 0xFF4D8DFF);
        actionRow.addView(swap, marginLp(0, dp(50), 0, 0, dp(6), 0, 1));
        actionRow.addView(update, marginLp(0, dp(50), dp(6), 0, 0, 0, 1));
        converter.addView(actionRow, marginLp(-1, -2, 0, dp(4), 0, dp(2)));
        box.addView(converter, marginLp(-1, -2, 0, 0, 0, dp(12)));

        TextWatcher fromWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable e) {
                if (updating[0] || !"from".equals(active[0])) return;
                updateCurrencyAmount(fromAmount, toAmount, from[0], to[0], updating);
            }
        };
        TextWatcher toWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable e) {
                if (updating[0] || !"to".equals(active[0])) return;
                updateCurrencyAmount(toAmount, fromAmount, to[0], from[0], updating);
            }
        };
        fromAmount.setOnFocusChangeListener((v, has) -> { if (has) active[0] = "from"; });
        toAmount.setOnFocusChangeListener((v, has) -> { if (has) active[0] = "to"; });
        fromAmount.addTextChangedListener(fromWatcher);
        toAmount.addTextChangedListener(toWatcher);
        fromAmount.setText("1000");
        updateCurrencyAmount(fromAmount, toAmount, from[0], to[0], updating);

        fromBtn.setOnClickListener(v -> showCurrencyPicker("選擇上方幣種", from[0], code -> {
            from[0] = code;
            fromBtn.setText(currencyButtonText(code));
            if ("from".equals(active[0])) updateCurrencyAmount(fromAmount, toAmount, from[0], to[0], updating);
            else updateCurrencyAmount(toAmount, fromAmount, to[0], from[0], updating);
        }));
        toBtn.setOnClickListener(v -> showCurrencyPicker("選擇下方幣種", to[0], code -> {
            to[0] = code;
            toBtn.setText(currencyButtonText(code));
            if ("from".equals(active[0])) updateCurrencyAmount(fromAmount, toAmount, from[0], to[0], updating);
            else updateCurrencyAmount(toAmount, fromAmount, to[0], from[0], updating);
        }));
        swap.setOnClickListener(v -> {
            String tmp = from[0]; from[0] = to[0]; to[0] = tmp;
            fromBtn.setText(currencyButtonText(from[0]));
            toBtn.setText(currencyButtonText(to[0]));
            active[0] = "from";
            updateCurrencyAmount(fromAmount, toAmount, from[0], to[0], updating);
        });
        update.setOnClickListener(v -> updateExchangeRateNow(true));
        refresh.setOnClickListener(v -> updateExchangeRateNow(true));

        setPage(scroll);
        updateExchangeRateNow(false);
    }

    private void updateExchangeRateNow(boolean showToast) {
        CurrencyRateStore.UpdateCallback callback = updated -> new Handler(Looper.getMainLooper()).post(() -> {
            if (showToast) {
                Toast.makeText(this, updated ? "已連網更新今日匯率" : CurrencyRateStore.updatedText(this), Toast.LENGTH_SHORT).show();
                showExchangeConverter();
            } else if (updated && tab == 3) {
                showExchangeConverter();
            }
        });
        if (showToast) CurrencyRateStore.updateNow(this, callback);
        else CurrencyRateStore.updateDailyIfNeeded(this, callback);
    }

    private String currencyButtonText(String code) {
        return code + " " + CurrencyRateStore.name(code);
    }

    private double parseCurrencyInput(EditText e) {
        try {
            String raw = e.getText().toString().replace(",", "").trim();
            if (raw.isEmpty()) return 0;
            return Double.parseDouble(raw);
        } catch (Exception ignored) { return 0; }
    }

    private String formatCurrency(double value) {
        DecimalFormat df = Math.abs(value) >= 1000 ? new DecimalFormat("#,##0.##") : new DecimalFormat("#,##0.###");
        return df.format(value);
    }

    private void updateCurrencyAmount(EditText source, EditText target, String from, String to, boolean[] updating) {
        double value = parseCurrencyInput(source);
        double result = CurrencyRateStore.convert(this, value, from, to);
        updating[0] = true;
        target.setText(formatCurrency(result));
        target.setSelection(target.getText().length());
        updating[0] = false;
    }

    private void showCurrencyPicker(String title, String current, CurrencyPickCallback callback) {
        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        LinearLayout panel = dialogPanel(dp(30));
        panel.addView(text(title, 21, TEXT, true), marginLp(-1, -2, 0, 0, 0, dp(4)));
        panel.addView(text("常用幣種在最上面，往下滑可以選更多國家。", 13, MUTED, false), marginLp(-1, -2, 0, 0, 0, dp(10)));

        ScrollView pickerScroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        pickerScroll.addView(list);

        String[] codes = CurrencyRateStore.allCodes();
        for (int i = 0; i < codes.length; i += 3) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int j = 0; j < 3 && i + j < codes.length; j++) {
                final String code = codes[i + j];
                boolean selected = code.equals(current);
                int bg = selected ? (isDarkMode() ? 0xFF2BB8B2 : 0xFFDAF8FF) : CHIP;
                int fg = selected ? (isDarkMode() ? 0xFFFFFFFF : 0xFF073B4C) : TEXT;
                Button b = smallChip((selected ? "✓ " : "") + code + "\n" + CurrencyRateStore.name(code), bg, fg);
                b.setTextSize(13);
                b.setSingleLine(false);
                b.setMaxLines(2);
                b.setOnClickListener(v -> { dialog.dismiss(); callback.onPicked(code); });
                row.addView(b, marginLp(0, dp(60), dp(3), dp(4), dp(3), dp(4), 1));
            }
            list.addView(row);
        }
        panel.addView(pickerScroll, marginLp(-1, dp(390), 0, 0, 0, dp(12)));

        Button close = pill("取消", CHIP, TEXT);
        close.setOnClickListener(v -> dialog.dismiss());
        panel.addView(close, marginLp(-1, dp(50), 0, 0, 0, 0));
        showCustomDialog(dialog, panel);
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

    private String saveProfileAvatar(Bitmap bitmap) {
        if (bitmap == null) return "";
        try {
            File f = new File(getFilesDir(), "autoledger_profile_avatar.png");
            FileOutputStream fos = new FileOutputStream(f);
            bitmap.compress(Bitmap.CompressFormat.PNG, 92, fos);
            fos.flush();
            fos.close();
            return f.getAbsolutePath();
        } catch (Exception e) {
            return "";
        }
    }

    private Bitmap loadProfileAvatarBitmap(int sizeDp) {
        String file = AppSettings.getString(this, AppSettings.KEY_PROFILE_AVATAR_FILE, "");
        if (file == null || file.trim().isEmpty()) return null;
        try {
            Bitmap src = BitmapFactory.decodeFile(file);
            if (src == null) return null;
            int size = dp(sizeDp);
            Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(out);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
            Path circle = new Path();
            circle.addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW);
            canvas.clipPath(circle);
            canvas.drawBitmap(src, null, new Rect(0, 0, size, size), p);
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private String profileName() {
        String name = AppSettings.getString(this, AppSettings.KEY_PROFILE_NAME, "自動記帳使用者");
        return name == null || name.trim().isEmpty() ? "自動記帳使用者" : name.trim();
    }

    private void showProfileDialog() {
        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        LinearLayout panel = dialogPanel(dp(30));
        panel.addView(text("個人資料", 21, TEXT, true), marginLp(-1, -2, 0, 0, 0, dp(8)));
        panel.addView(text("名稱會顯示在側邊選單；頭像只存在手機本機。", 13, MUTED, false), marginLp(-1, -2, 0, 0, 0, dp(12)));

        LinearLayout avatarRow = new LinearLayout(this);
        avatarRow.setGravity(Gravity.CENTER_VERTICAL);
        ImageView avatar = new ImageView(this);
        Bitmap avatarBitmap = loadProfileAvatarBitmap(72);
        if (avatarBitmap != null) avatar.setImageBitmap(avatarBitmap);
        else avatar.setImageBitmap(defaultAvatarBitmap(72));
        avatar.setBackground(round(CHIP, dp(36), BORDER));
        avatarRow.addView(avatar, new LinearLayout.LayoutParams(dp(72), dp(72)));
        LinearLayout avatarActions = new LinearLayout(this);
        avatarActions.setOrientation(LinearLayout.VERTICAL);
        avatarActions.setPadding(dp(12), 0, 0, 0);
        Button choose = pill("選擇頭像", CHIP, ORANGE);
        Button clear = pill("清除頭像", CHIP, EXPENSE_RED);
        avatarActions.addView(choose, new LinearLayout.LayoutParams(-1, dp(44)));
        avatarActions.addView(clear, marginLp(-1, dp(44), 0, dp(8), 0, 0));
        avatarRow.addView(avatarActions, new LinearLayout.LayoutParams(0, -2, 1));
        panel.addView(avatarRow, marginLp(-1, -2, 0, 0, 0, dp(14)));

        panel.addView(label("顯示名稱"));
        final EditText name = edit("例如：恩宇", false);
        name.setSingleLine(true);
        name.setText(profileName());
        panel.addView(name, marginLp(-1, dp(56), 0, 0, 0, dp(14)));

        LinearLayout actions = dialogActionsRow();
        Button cancel = pill("取消", CHIP, TEXT);
        Button save = bigSave("儲存");
        actions.addView(cancel, marginLp(0, dp(50), 0, 0, dp(6), 0, 1));
        actions.addView(save, marginLp(0, dp(50), dp(6), 0, 0, 0, 1));
        panel.addView(actions);

        choose.setOnClickListener(v -> {
            AppSettings.setString(this, AppSettings.KEY_PROFILE_NAME, name.getText().toString().trim());
            Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.setType("image/*");
            dialog.dismiss();
            closeDialogs();
            startActivityForResult(Intent.createChooser(intent, "選擇頭像照片"), REQUEST_PROFILE_AVATAR);
        });
        clear.setOnClickListener(v -> {
            AppSettings.setString(this, AppSettings.KEY_PROFILE_AVATAR_FILE, "");
            avatar.setImageBitmap(defaultAvatarBitmap(72));
            Toast.makeText(this, "已清除頭像", Toast.LENGTH_SHORT).show();
        });
        cancel.setOnClickListener(v -> dialog.dismiss());
        save.setOnClickListener(v -> {
            AppSettings.setString(this, AppSettings.KEY_PROFILE_NAME, name.getText().toString().trim());
            Toast.makeText(this, "已更新個人資料", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            showSideMenu();
        });
        showCustomDialog(dialog, panel);
    }

    private Bitmap defaultAvatarBitmap(int sizeDp) {
        int size = dp(sizeDp);
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(isDarkMode() ? 0xFF2A333F : 0xFFEAF4FF);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, p);
        p.setColor(isDarkMode() ? 0xFFF7FAFF : 0xFF4FADEB);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextSize(size * 0.42f);
        Paint.FontMetrics fm = p.getFontMetrics();
        String initial = profileName().isEmpty() ? "自" : profileName().substring(0, 1);
        canvas.drawText(initial, size / 2f, size / 2f - (fm.ascent + fm.descent) / 2f, p);
        return out;
    }

    private void showProfileAvatarCropDialog(Uri uri) {
        Bitmap src = decodeWidgetSource(uri);
        if (src == null) {
            Toast.makeText(this, "讀不到這張圖片，請改從相簿選一張圖片", Toast.LENGTH_LONG).show();
            showSideMenu();
            return;
        }
        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        LinearLayout panel = dialogPanel(dp(30));
        panel.addView(text("裁切頭像", 21, TEXT, true), marginLp(-1, -2, 0, 0, 0, dp(4)));
        panel.addView(text("拖曳照片調整位置，雙指縮放。白色圓圈內就是側邊選單會顯示的頭像。", 13, MUTED, false), marginLp(-1, -2, 0, 0, 0, dp(10)));
        PhotoCropView cropView = new PhotoCropView(this, src, 1f, true);
        cropView.setBackground(round(CHIP, dp(22), BORDER));
        panel.addView(cropView, marginLp(-1, dp(300), 0, 0, 0, dp(12)));
        LinearLayout actions = dialogActionsRow();
        Button cancel = pill("取消", CHIP, TEXT);
        Button save = bigSave("✓ 儲存頭像");
        actions.addView(cancel, marginLp(0, dp(52), 0, 0, dp(6), 0, 1));
        actions.addView(save, marginLp(0, dp(52), dp(6), 0, 0, 0, 1));
        panel.addView(actions);
        cancel.setOnClickListener(v -> { dialog.dismiss(); showSideMenu(); });
        save.setOnClickListener(v -> {
            Bitmap crop = cropView.getCroppedBitmap(512, 512);
            String path = saveProfileAvatar(crop);
            if (path.isEmpty()) {
                Toast.makeText(this, "儲存頭像失敗，請換一張圖試試", Toast.LENGTH_LONG).show();
                return;
            }
            AppSettings.setString(this, AppSettings.KEY_PROFILE_AVATAR_FILE, path);
            Toast.makeText(this, "已設定頭像", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            showSideMenu();
        });
        showCustomDialog(dialog, panel);
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
        private final float aspect;
        private final boolean ovalCrop;
        private float scale = 1f;
        private float minScale = 1f;
        private float tx = 0f;
        private float ty = 0f;
        private float lastX = 0f;
        private float lastY = 0f;
        private boolean initialized = false;

        PhotoCropView(Context context, Bitmap source) {
            this(context, source, 4f / 3f, false);
        }

        PhotoCropView(Context context, Bitmap source, float aspect, boolean ovalCrop) {
            super(context);
            src = source;
            this.aspect = aspect <= 0 ? 4f / 3f : aspect;
            this.ovalCrop = ovalCrop;
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
            float ch = cw / aspect;
            if (ch > maxH) {
                ch = maxH;
                cw = ch * aspect;
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
            if (ovalCrop) canvas.drawOval(cropRect, borderPaint);
            else canvas.drawRoundRect(cropRect, dp(20), dp(20), borderPaint);
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
        showRoundedInfoDialog("桌面小工具", "V28 有三種桌面小工具：\n\n1. 簡易記帳小工具：顯示餘額、今日花費，點「支出／收入」快速新增。\n\n2. 載具＋記帳小工具：顯示載具條碼、餘額、今日花費、輸入金額入口。\n\n3. 圖片＋載具＋記帳小工具：最上方顯示你裁切好的圖片，中間顯示載具條碼，下方顯示餘額與支出 / 收入。點小工具圖片可回到 App 修改圖片。", "知道了", null, "圖片設定", v -> showWidgetImageSettingsDialog());
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

        TextView version = text("AutoLedger V36", 12, MUTED, false);
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
        boolean selected = tab == index;
        if (selected) item.setBackground(round(isDarkMode() ? 0xFF253141 : 0xFFFFEFE6, dp(18)));
        TextView ic = text(icon, index == 1 ? 26 : 22, selected ? ORANGE : 0xFF707783, true);
        ic.setGravity(Gravity.CENTER);
        TextView lab = text(label, 12, selected ? ORANGE : 0xFF707783, selected);
        lab.setGravity(Gravity.CENTER);
        item.addView(ic);
        item.addView(lab);
        item.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(56), 1);
        lp.setMargins(dp(3), 0, dp(3), 0);
        nav.addView(item, lp);
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16), dp(16), dp(16), dp(16));
        l.setBackground(round(CARD, dp(20), BORDER));
        elevate(l, 1);
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
        e.setHintTextColor(isDarkMode() ? 0xFF6F7B8C : 0xFFA5ADB9);
        e.setBackground(round(isDarkMode() ? 0xFF111923 : 0xFFFFFFFF, dp(16), BORDER));
        return e;
    }

    private Button bigAction(String text, int c1, int c2) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(0xFFFFFFFF);
        b.setTextSize(16);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setAllCaps(false);
        b.setSingleLine(false);
        b.setMaxLines(3);
        b.setGravity(Gravity.CENTER);
        b.setBackground(roundGradient(c1, c2, dp(22)));
        elevate(b, 2);
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
        elevate(b, 2);
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
        b.setBackground(round(bg, dp(18), BORDER));
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
        b.setBackground(round(bg, dp(16), BORDER));
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

    private void elevate(View v, int dpValue) {
        if (v != null && Build.VERSION.SDK_INT >= 21) {
            v.setElevation(dp(dpValue));
        }
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
        showRoundedInfoDialog(
                "歡迎使用自動記帳 V36",
                "這版新增 / 優化：\n\n" +
                        "1. 首頁改成月份帳本，可切換年份月份，並用日期分組顯示紀錄。\n" +
                        "2. 新增記帳計算機，可從首頁、側邊搜尋與 Android 快速開關開啟。\n" +
                        "3. 修正中國信託 ATM 存款通知，會自動記到收入 / 存款。",
                "我知道了",
                v -> AppSettings.setBool(this, AppSettings.KEY_ONBOARDED, true),
                "通知用途",
                v -> showNotificationPurpose());
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
        panel.setBackground(round(CARD, dp(28), BORDER));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text("選擇圖標", 20, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView close = text("×", 25, TEXT, true);
        close.setGravity(Gravity.CENTER);
        close.setBackground(round(CHIP, dp(21), BORDER));
        close.setOnClickListener(v -> dialog.dismiss());
        head.addView(close, new LinearLayout.LayoutParams(dp(42), dp(42)));
        panel.addView(head, marginLp(-1, -2, 0, 0, 0, dp(8)));
        panel.addView(text("常用圖標放前面；也可以按「＋新增」加入自己的圖標。", 12, MUTED, false), marginLp(-1, -2, 0, 0, 0, dp(10)));

        ScrollView sc = new ScrollView(this);
        sc.setFillViewport(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        ArrayList<String[]> all = new ArrayList<>();
        String[][] defaults = income
                ? new String[][]{{"💰","收入"},{"💵","薪水"},{"🧧","紅包"},{"↩️","退款"},{"🎁","獎金"},{"👨‍👩‍👧","家人"},{"🏦","銀行"},{"⭐","其他"}}
                : new String[][]{{"🥤","茶飲"},{"🍴","餐飲"},{"🍱","便當"},{"🍜","早餐"},{"🍔","午餐"},{"🍲","晚餐"},{"🚌","交通"},{"🛵","機車"},{"🅿️","停車"},{"🏪","超商"},{"🛒","超市"},{"🛍️","購物"},{"▶️","訂閱"},{"🎮","遊戲"},{"🎬","娛樂"},{"🎁","禮物"},{"💊","醫療"},{"💄","保養"},{"👕","衣服"},{"📚","學習"},{"✈️","旅遊"},{"🏋️","運動"},{"🐾","寵物"},{"🏠","房租"},{"💡","水電"},{"📱","通訊"},{"🧾","發票"},{"📦","包裹"},{"🍀","其他"}};
        for (String[] it : defaults) all.add(it);
        for (String row : AppSettings.getCustomIcons(this)) {
            if (row == null || row.trim().isEmpty()) continue;
            String[] p = row.split("\\|", 2);
            String ic = p.length > 0 ? p[0].trim() : "";
            String name = p.length > 1 ? p[1].trim() : "自訂";
            if (!ic.isEmpty()) all.add(new String[]{ic, name.isEmpty() ? "自訂" : name});
        }

        for (int i = 0; i < all.size(); i += 3) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            for (int j = 0; j < 3; j++) {
                if (i + j < all.size()) {
                    String ic = all.get(i + j)[0];
                    String name = all.get(i + j)[1];
                    Button b = smallChip(ic + "  " + name, CHIP, TEXT);
                    b.setTextSize(13);
                    b.setAllCaps(false);
                    b.setSingleLine(true);
                    b.setEllipsize(TextUtils.TruncateAt.END);
                    b.setPadding(dp(8), 0, dp(8), 0);
                    b.setBackground(round(CHIP, dp(20), BORDER));
                    b.setOnClickListener(v -> {
                        selectedIcon[0] = ic;
                        preview.setText(ic);
                        if (category.getText().toString().trim().isEmpty() || cleanCategory(category.getText().toString()).isEmpty()) category.setText(name);
                        dialog.dismiss();
                    });
                    row.addView(b, marginLp(0, dp(46), dp(4), dp(4), dp(4), dp(4), 1));
                } else {
                    row.addView(new TextView(this), marginLp(0, dp(46), dp(4), dp(4), dp(4), dp(4), 1));
                }
            }
            list.addView(row);
        }
        sc.addView(list, new ScrollView.LayoutParams(-1, -2));
        panel.addView(sc, new LinearLayout.LayoutParams(-1, dp(300)));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button add = pill("＋新增圖標", CHIP, ORANGE);
        Button cancel = pill("取消", CHIP, TEXT);
        actions.addView(cancel, marginLp(0, dp(48), 0, dp(10), dp(6), 0, 1));
        actions.addView(add, marginLp(0, dp(48), dp(6), dp(10), 0, 0, 1));
        panel.addView(actions);
        cancel.setOnClickListener(v -> dialog.dismiss());
        add.setOnClickListener(v -> showAddCustomIconDialog(dialog, preview, category, selectedIcon));
        showCustomDialog(dialog, panel);
    }

    private void showAddCustomIconDialog(AlertDialog parent, TextView preview, EditText category, String[] selectedIcon) {
        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        LinearLayout panel = dialogPanel(dp(24));
        panel.setBackground(round(CARD, dp(28), BORDER));
        panel.addView(text("新增自己的圖標", 20, TEXT, true));
        panel.addView(text("第一格貼一個 emoji 圖標，第二格輸入名稱，例如：☕ 咖啡。", 13, MUTED, false), marginLp(-1, -2, 0, dp(4), 0, dp(12)));
        final EditText emoji = edit("圖標，例如 ☕", false);
        emoji.setSingleLine(true);
        emoji.setTextSize(22);
        panel.addView(label("圖標"));
        panel.addView(emoji, marginLp(-1, dp(52), 0, 0, 0, dp(10)));
        final EditText name = edit("名稱，例如 咖啡", false);
        name.setSingleLine(true);
        panel.addView(label("名稱"));
        panel.addView(name, marginLp(-1, dp(52), 0, 0, 0, dp(14)));
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button cancel = pill("取消", CHIP, TEXT);
        Button save = bigSave("✓ 加入");
        actions.addView(cancel, marginLp(0, dp(48), 0, 0, dp(6), 0, 1));
        actions.addView(save, marginLp(0, dp(48), dp(6), 0, 0, 0, 1));
        panel.addView(actions);
        cancel.setOnClickListener(v -> dialog.dismiss());
        save.setOnClickListener(v -> {
            String ic = emoji.getText().toString().trim();
            String label = name.getText().toString().trim();
            if (ic.isEmpty()) {
                Toast.makeText(this, "請先輸入圖標", Toast.LENGTH_SHORT).show();
                return;
            }
            AppSettings.addCustomIcon(this, ic, label.isEmpty() ? "自訂" : label);
            selectedIcon[0] = ic;
            preview.setText(ic);
            if (category.getText().toString().trim().isEmpty() && !label.isEmpty()) category.setText(label);
            Toast.makeText(this, "已新增圖標", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            if (parent != null) parent.dismiss();
        });
        showCustomDialog(dialog, panel);
    }

    private LinearLayout miniCategoryChips(EditText target, boolean income) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        addMiniSuggestionRows(box, target, income);
        return box;
    }

    private void addMiniSuggestionRows(LinearLayout box, EditText target, boolean income) {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hsv.setClipToPadding(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        List<String> items = AppSettings.getSortedQuickItems(this, income);
        if (items == null || items.isEmpty()) {
            items = new ArrayList<>();
            String[] defaults = income
                    ? new String[]{"薪水", "零用錢", "打工", "退款", "紅包", "獎金", "家人", "其他"}
                    : new String[]{"茶飲", "飲料", "早餐", "午餐", "晚餐", "交通", "超商", "訂閱"};
            for (String d : defaults) items.add(d);
        }
        for (String raw : items) {
            String value = raw == null ? "" : raw.split("\\|")[0].trim();
            if (value.isEmpty()) continue;
            Button b = smallChip(value, CHIP, income ? PURPLE : ORANGE);
            b.setTextSize(12);
            b.setMinHeight(dp(34));
            b.setPadding(dp(10), 0, dp(10), 0);
            b.setOnClickListener(v -> {
                AppSettings.incrementQuickUsage(this, income, value);
                target.setText(value);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(82), dp(36));
            lp.setMargins(0, dp(2), dp(8), dp(2));
            row.addView(b, lp);
        }
        Button add = smallChip("＋新增", isDarkMode() ? 0xFF203344 : 0xFFE9F8FF, isDarkMode() ? 0xFF7EE8FF : 0xFF0B6B88);
        add.setTextSize(12);
        add.setMinHeight(dp(34));
        add.setPadding(dp(10), 0, dp(10), 0);
        add.setOnClickListener(v -> showAddQuickItemDialogForEdit(income, box, target));
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(dp(82), dp(36));
        addLp.setMargins(0, dp(2), dp(8), dp(2));
        row.addView(add, addLp);
        hsv.addView(row, new HorizontalScrollView.LayoutParams(-2, dp(40)));
        box.addView(hsv, new LinearLayout.LayoutParams(-1, dp(42)));
    }

    private void showAddQuickItemDialogForEdit(boolean income, LinearLayout suggestionBox, EditText target) {
        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        LinearLayout panel = dialogPanel(dp(24));
        panel.addView(text(income ? "新增收入常用項目" : "新增支出常用項目", 20, TEXT, true));
        panel.addView(text("只輸入名稱，金額不會固定；新增後會出現在分類建議最後。", 13, MUTED, false), marginLp(-1, -2, 0, dp(4), 0, dp(12)));
        final EditText input = edit(income ? "例如 薪水、零用錢" : "例如 早餐、飲料、交通", false);
        input.setSingleLine(true);
        panel.addView(input, marginLp(-1, dp(52), 0, 0, 0, dp(14)));
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button cancel = dialogBtn("取消");
        Button save = bigAction("✓ 儲存", 0xFFFF5A45, 0xFFFF8B2B);
        actions.addView(cancel, marginLp(0, dp(48), 0, 0, dp(8), 0, 1));
        actions.addView(save, marginLp(0, dp(48), dp(8), 0, 0, 0, 1));
        panel.addView(actions);
        cancel.setOnClickListener(v -> dialog.dismiss());
        save.setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "請輸入常用項目名稱", Toast.LENGTH_SHORT).show();
                return;
            }
            AppSettings.addQuickItem(this, income, name);
            target.setText(name);
            if (suggestionBox != null) {
                suggestionBox.removeAllViews();
                addMiniSuggestionRows(suggestionBox, target, income);
                suggestionBox.setVisibility(View.VISIBLE);
            }
            Toast.makeText(this, "已新增常用項目：" + name, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        showCustomDialog(dialog, panel);
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


    private String[] parseFixedRecord(String line) {
        String[] out = new String[]{"支出", "固定項目", "0", "未分類", "每月"};
        if (line == null) return out;
        String[] p = line.split("\\|");
        for (int i = 0; i < p.length && i < out.length; i++) out[i] = p[i] == null ? out[i] : p[i].trim();
        if (!"收入".equals(out[0])) out[0] = "支出";
        if (empty(out[1])) out[1] = "固定項目";
        if (empty(out[2])) out[2] = "0";
        if (empty(out[3])) out[3] = "未分類";
        if (empty(out[4])) out[4] = "每月";
        return out;
    }

    private void showFixedRecordsManager() {
        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        LinearLayout panel = dialogPanel(dp(30));
        panel.addView(text("固定收支", 21, TEXT, true), marginLp(-1, -2, 0, 0, 0, dp(6)));
        panel.addView(text("把每月都會出現的房租、訂閱、薪水先存起來。之後可以一鍵新增一筆，或一次套用全部固定收支。", 13, MUTED, false), marginLp(-1, -2, 0, 0, 0, dp(12)));

        final LinearLayout listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(listBox, new ScrollView.LayoutParams(-1, -2));
        panel.addView(scroll, marginLp(-1, dp(360), 0, 0, 0, dp(12)));

        final Runnable[] rebuildRef = new Runnable[1];
        rebuildRef[0] = new Runnable() {
            @Override public void run() {
                listBox.removeAllViews();
                java.util.List<String> items = new ArrayList<>(AppSettings.getFixedRecords(MainActivity.this));
                if (items.isEmpty()) {
                    TextView emptyView = text("目前還沒有固定收支，先新增一筆吧。", 14, MUTED, false);
                    emptyView.setGravity(Gravity.CENTER);
                    emptyView.setPadding(0, dp(24), 0, dp(24));
                    listBox.addView(emptyView);
                    return;
                }
                for (String line : items) {
                    final String entry = line;
                    String[] f = parseFixedRecord(entry);
                    LinearLayout card = new LinearLayout(MainActivity.this);
                    card.setOrientation(LinearLayout.VERTICAL);
                    card.setPadding(dp(14), dp(12), dp(14), dp(12));
                    card.setBackground(round(isDarkMode() ? 0xFF1A2430 : 0xFFFFFFFF, dp(18), BORDER));

                    LinearLayout top = new LinearLayout(MainActivity.this);
                    top.setGravity(Gravity.CENTER_VERTICAL);
                    TextView chip = text(f[0], 12, "收入".equals(f[0]) ? GREEN : EXPENSE_RED, true);
                    chip.setPadding(dp(10), dp(6), dp(10), dp(6));
                    chip.setBackground(round(CHIP, dp(18), BORDER));
                    top.addView(chip);
                    int amountNum;
                    try { amountNum = Integer.parseInt(f[2].replace(",", "").trim()); } catch (Exception ex) { amountNum = 0; }
                    TextView amount = text(TransactionStore.money(Math.max(0, amountNum)), 18, TEXT, true);
                    top.addView(amount, marginLp(-2, -2, dp(10), 0, 0, 0));
                    card.addView(top);
                    card.addView(text(f[1], 17, TEXT, true), marginLp(-1, -2, 0, dp(8), 0, 0));
                    card.addView(text("分類：" + f[3] + "｜頻率：" + f[4], 13, MUTED, false), marginLp(-1, -2, 0, dp(2), 0, dp(10)));

                    LinearLayout actions = new LinearLayout(MainActivity.this);
                    actions.setOrientation(LinearLayout.HORIZONTAL);
                    Button editBtn = pill("編輯", CHIP, TEXT);
                    Button applyBtn = pill("新增一筆", 0xFFFFF3EA, ORANGE);
                    Button delBtn = pill("刪除", CHIP, EXPENSE_RED);
                    actions.addView(editBtn, new LinearLayout.LayoutParams(0, dp(42), 1));
                    actions.addView(applyBtn, marginLp(0, dp(42), dp(8), 0, dp(8), 0, 1));
                    actions.addView(delBtn, new LinearLayout.LayoutParams(0, dp(42), 1));
                    card.addView(actions);
                    listBox.addView(card, marginLp(-1, -2, 0, 0, 0, dp(10)));

                    editBtn.setOnClickListener(v -> {
                        dialog.dismiss();
                        showFixedRecordEditor(entry, () -> showFixedRecordsManager());
                    });
                    applyBtn.setOnClickListener(v -> {
                        String[] e = parseFixedRecord(entry);
                        int amountValue;
                        try { amountValue = Integer.parseInt(e[2].replace(",", "").trim()); } catch (Exception ex) { amountValue = 0; }
                        if (amountValue <= 0) {
                            Toast.makeText(MainActivity.this, "金額要大於 0", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        String dir = "收入".equals(e[0]) ? "income" : "expense";
                        String hash = "fixed-" + System.currentTimeMillis() + "-" + e[1];
                        Transaction tx = new Transaction(System.currentTimeMillis(), amountValue, dir, "固定收支", e[1], e[3], "固定收支｜" + e[4], hash, "收入".equals(e[0]) ? "💰" : "🏷");
                        boolean ok = TransactionStore.add(MainActivity.this, tx);
                        Toast.makeText(MainActivity.this, ok ? "已新增固定收支" : "新增失敗，可能重複了", Toast.LENGTH_SHORT).show();
                        refreshCurrent();
                    });
                    delBtn.setOnClickListener(v -> {
                        java.util.List<String> all = new ArrayList<>(AppSettings.getFixedRecords(MainActivity.this));
                        all.remove(entry);
                        AppSettings.setList(MainActivity.this, AppSettings.KEY_FIXED_RECORDS, all);
                        rebuildRef[0].run();
                    });
                }
            }
        };
        rebuildRef[0].run();

        LinearLayout actions = dialogActionsRow();
        Button close = pill("關閉", CHIP, TEXT);
        Button add = pill("＋新增", CHIP, ORANGE);
        Button applyAll = bigSave("套用全部固定收支");
        actions.addView(close, marginLp(0, dp(48), 0, 0, dp(6), 0, 1));
        actions.addView(add, marginLp(0, dp(48), dp(6), 0, dp(6), 0, 1));
        actions.addView(applyAll, marginLp(0, dp(48), dp(6), 0, 0, 0, 2));
        panel.addView(actions);
        close.setOnClickListener(v -> dialog.dismiss());
        add.setOnClickListener(v -> {
            dialog.dismiss();
            showFixedRecordEditor(null, () -> showFixedRecordsManager());
        });
        applyAll.setOnClickListener(v -> {
            int added = 0;
            for (String entry : AppSettings.getFixedRecords(MainActivity.this)) {
                String[] e = parseFixedRecord(entry);
                int amountValue;
                try { amountValue = Integer.parseInt(e[2].replace(",", "").trim()); } catch (Exception ex) { amountValue = 0; }
                if (amountValue <= 0) continue;
                String dir = "收入".equals(e[0]) ? "income" : "expense";
                String hash = "fixed-batch-" + System.currentTimeMillis() + "-" + added + "-" + e[1];
                Transaction tx = new Transaction(System.currentTimeMillis(), amountValue, dir, "固定收支", e[1], e[3], "固定收支｜" + e[4], hash, "收入".equals(e[0]) ? "💰" : "🏷");
                if (TransactionStore.add(MainActivity.this, tx)) added++;
            }
            Toast.makeText(MainActivity.this, added > 0 ? "已套用 " + added + " 筆固定收支" : "這次沒有新增任何固定收支", Toast.LENGTH_SHORT).show();
            refreshCurrent();
        });
        showCustomDialog(dialog, panel);
    }

    private void showFixedRecordEditor(String oldLine, Runnable afterSave) {
        String[] src = parseFixedRecord(oldLine);
        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        LinearLayout panel = dialogPanel(dp(30));
        panel.addView(text(oldLine == null ? "新增固定收支" : "修改固定收支", 21, TEXT, true), marginLp(-1, -2, 0, 0, 0, dp(8)));

        final boolean[] income = new boolean[]{"收入".equals(src[0])};
        LinearLayout typeRow = dialogActionsRow();
        Button expense = pill("支出", income[0] ? CHIP : 0xFFFFF0F0, income[0] ? MUTED : EXPENSE_RED);
        Button incomeBtn = pill("收入", income[0] ? 0xFFF0FFF6 : CHIP, income[0] ? GREEN : TEXT);
        typeRow.addView(expense, marginLp(0, dp(46), 0, 0, dp(6), 0, 1));
        typeRow.addView(incomeBtn, marginLp(0, dp(46), dp(6), 0, 0, 0, 1));
        panel.addView(typeRow, marginLp(-1, -2, 0, 0, 0, dp(10)));

        panel.addView(label("名稱"));
        final EditText name = edit("例如：房租、Netflix、薪水", false);
        name.setText(src[1]);
        panel.addView(name, marginLp(-1, dp(56), 0, 0, 0, dp(10)));

        panel.addView(label("金額"));
        final EditText amount = edit("例如：12000", true);
        amount.setInputType(InputType.TYPE_CLASS_NUMBER);
        amount.setText(src[2]);
        panel.addView(amount, marginLp(-1, dp(56), 0, 0, 0, dp(10)));

        panel.addView(label("分類"));
        final EditText category = edit("例如：租金、訂閱、薪水", false);
        category.setText(src[3]);
        panel.addView(category, marginLp(-1, dp(56), 0, 0, 0, dp(10)));

        panel.addView(label("頻率 / 備註"));
        final EditText schedule = edit("例如：每月 5 號、每月 10 號", false);
        schedule.setText(src[4]);
        panel.addView(schedule, marginLp(-1, dp(56), 0, 0, 0, dp(14)));

        Runnable refreshType = new Runnable() {
            @Override public void run() {
                expense.setTextColor(income[0] ? MUTED : EXPENSE_RED);
                expense.setBackground(round(income[0] ? CHIP : 0xFFFFF0F0, dp(24), BORDER));
                incomeBtn.setTextColor(income[0] ? GREEN : TEXT);
                incomeBtn.setBackground(round(income[0] ? 0xFFF0FFF6 : CHIP, dp(24), BORDER));
            }
        };
        refreshType.run();
        expense.setOnClickListener(v -> { income[0] = false; refreshType.run(); });
        incomeBtn.setOnClickListener(v -> { income[0] = true; refreshType.run(); });

        LinearLayout actions = dialogActionsRow();
        Button cancel = pill("取消", CHIP, TEXT);
        Button save = bigSave("儲存");
        actions.addView(cancel, marginLp(0, dp(50), 0, 0, dp(6), 0, 1));
        actions.addView(save, marginLp(0, dp(50), dp(6), 0, 0, 0, 1));
        panel.addView(actions);
        cancel.setOnClickListener(v -> {
            dialog.dismiss();
            if (afterSave != null) afterSave.run();
        });
        save.setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            int amt;
            try { amt = Integer.parseInt(amount.getText().toString().replace(",", "").trim()); } catch (Exception e) { amt = 0; }
            String c = category.getText().toString().trim();
            String s = schedule.getText().toString().trim();
            if (empty(n) || amt <= 0) {
                Toast.makeText(MainActivity.this, "請把名稱和金額填完整", Toast.LENGTH_SHORT).show();
                return;
            }
            if (empty(c)) c = income[0] ? "收入" : "未分類";
            if (empty(s)) s = "每月";
            String line = (income[0] ? "收入" : "支出") + "|" + n + "|" + amt + "|" + c + "|" + s;
            java.util.List<String> items = new ArrayList<>(AppSettings.getFixedRecords(MainActivity.this));
            if (oldLine != null) items.remove(oldLine);
            items.add(0, line);
            AppSettings.setList(MainActivity.this, AppSettings.KEY_FIXED_RECORDS, items);
            Toast.makeText(MainActivity.this, "已儲存固定收支", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            if (afterSave != null) afterSave.run();
        });
        showCustomDialog(dialog, panel);
    }

    private void showFinanceSimulator() {
        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        LinearLayout panel = dialogPanel(dp(30));
        panel.addView(text("財務模擬", 21, TEXT, true), marginLp(-1, -2, 0, 0, 0, dp(6)));
        panel.addView(text("先輸入本月預算、目前已花費，再試著加上額外收入或額外支出，快速看看月底可能還剩多少。", 13, MUTED, false), marginLp(-1, -2, 0, 0, 0, dp(12)));

        panel.addView(label("本月預算"));
        final EditText budgetInput = edit("例如：10000", true);
        budgetInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        budgetInput.setText(String.valueOf(AppSettings.getMonthlyBudget(this)));
        panel.addView(budgetInput, marginLp(-1, dp(56), 0, 0, 0, dp(10)));

        panel.addView(label("目前已花費"));
        final EditText spentInput = edit("例如：5000", true);
        spentInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        spentInput.setText(String.valueOf(TransactionStore.effectiveMonthExpense(this)));
        panel.addView(spentInput, marginLp(-1, dp(56), 0, 0, 0, dp(10)));

        LinearLayout adjustRow = new LinearLayout(this);
        adjustRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout left = new LinearLayout(this); left.setOrientation(LinearLayout.VERTICAL);
        left.addView(label("額外收入"));
        final EditText incomeInput = edit("0", true);
        incomeInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        incomeInput.setText("0");
        left.addView(incomeInput, new LinearLayout.LayoutParams(-1, dp(56)));
        LinearLayout right = new LinearLayout(this); right.setOrientation(LinearLayout.VERTICAL);
        right.addView(label("預計再花"));
        final EditText extraSpendInput = edit("0", true);
        extraSpendInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        extraSpendInput.setText("0");
        right.addView(extraSpendInput, new LinearLayout.LayoutParams(-1, dp(56)));
        adjustRow.addView(left, marginLp(0, -2, 0, 0, dp(6), dp(12), 1));
        adjustRow.addView(right, marginLp(0, -2, dp(6), 0, 0, dp(12), 1));
        panel.addView(adjustRow);

        LinearLayout preview = new LinearLayout(this);
        preview.setOrientation(LinearLayout.HORIZONTAL);
        preview.setGravity(Gravity.CENTER_VERTICAL);
        preview.setPadding(dp(14), dp(12), dp(14), dp(12));
        preview.setBackground(round(isDarkMode() ? 0xFF1A2430 : 0xFFFFFFFF, dp(18), BORDER));
        final DonutChartView donut = new DonutChartView(this);
        donut.setDarkMode(isDarkMode());
        preview.addView(donut, new LinearLayout.LayoutParams(dp(146), dp(146)));
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(8), 0, 0, 0);
        final TextView remainValue = text("$0", 24, 0xFF24A99B, true);
        final TextView line1 = text("模擬後剩餘", 13, 0xFF24A99B, true);
        final TextView line2 = text("使用率 0%", 14, TEXT, true);
        final TextView line3 = text("預算 $0", 13, MUTED, false);
        final TextView line4 = text("已花費 $0", 13, MUTED, false);
        info.addView(line1);
        info.addView(remainValue, marginLp(-1, -2, 0, dp(2), 0, dp(6)));
        info.addView(line2, marginLp(-1, -2, 0, 0, 0, dp(4)));
        info.addView(line3, marginLp(-1, -2, 0, 0, 0, dp(2)));
        info.addView(line4);
        preview.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        panel.addView(preview, marginLp(-1, -2, 0, 0, 0, dp(14)));

        Runnable update = new Runnable() {
            @Override public void run() {
                int budget = 0, spent = 0, income = 0, extraSpend = 0;
                try { budget = Integer.parseInt(budgetInput.getText().toString().replace(",", "").trim()); } catch (Exception ignored) { }
                try { spent = Integer.parseInt(spentInput.getText().toString().replace(",", "").trim()); } catch (Exception ignored) { }
                try { income = Integer.parseInt(incomeInput.getText().toString().replace(",", "").trim()); } catch (Exception ignored) { }
                try { extraSpend = Integer.parseInt(extraSpendInput.getText().toString().replace(",", "").trim()); } catch (Exception ignored) { }
                budget = Math.max(0, budget + income);
                spent = Math.max(0, spent + extraSpend);
                int remaining = Math.max(0, budget - spent);
                donut.setCenterLabel("已使用");
                donut.setData(spent, remaining, income, AppSettings.getPalette(MainActivity.this));
                remainValue.setText(TransactionStore.money(remaining));
                line2.setText("使用率 " + (budget <= 0 ? 0 : Math.min(999, Math.round(spent * 100f / Math.max(1, budget)))) + "%");
                line3.setText("模擬後總預算 " + TransactionStore.money(budget));
                line4.setText("模擬後總花費 " + TransactionStore.money(spent));
            }
        };
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { update.run(); }
            @Override public void afterTextChanged(Editable s) { update.run(); }
        };
        budgetInput.addTextChangedListener(watcher);
        spentInput.addTextChangedListener(watcher);
        incomeInput.addTextChangedListener(watcher);
        extraSpendInput.addTextChangedListener(watcher);
        update.run();

        LinearLayout actions = dialogActionsRow();
        Button close = pill("關閉", CHIP, TEXT);
        Button applyBudget = bigSave("套用本月預算");
        actions.addView(close, marginLp(0, dp(50), 0, 0, dp(6), 0, 1));
        actions.addView(applyBudget, marginLp(0, dp(50), dp(6), 0, 0, 0, 1));
        panel.addView(actions);
        close.setOnClickListener(v -> dialog.dismiss());
        applyBudget.setOnClickListener(v -> {
            int budget = 0;
            try { budget = Integer.parseInt(budgetInput.getText().toString().replace(",", "").trim()); } catch (Exception ignored) { }
            AppSettings.setMonthlyBudget(MainActivity.this, Math.max(0, budget));
            Toast.makeText(MainActivity.this, "已套用本月預算", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            showHome();
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
