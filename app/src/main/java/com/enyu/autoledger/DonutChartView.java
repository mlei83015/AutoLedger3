package com.enyu.autoledger;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

public class DonutChartView extends View {
    private int spent = 0;
    private int remaining = 1;
    private int income = 0;
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int colorSpent = 0xFFFF6B57;
    private int colorRemain = 0xFF4AA8FF;
    private int colorIncome = 0xFF24A99B;
    private boolean darkMode = false;
    private String centerLabel = "已使用";

    public DonutChartView(Context context) {
        super(context);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setDarkMode(boolean darkMode) {
        this.darkMode = darkMode;
        invalidate();
    }

    public void setCenterLabel(String label) {
        this.centerLabel = label == null || label.trim().isEmpty() ? "已使用" : label;
        invalidate();
    }

    public void setData(int spent, int remaining, int income, int palette) {
        this.spent = Math.max(0, spent);
        this.remaining = Math.max(0, remaining);
        this.income = Math.max(0, income);
        int[][] palettes = new int[][]{
                {0xFFFF6B57, 0xFF4AA8FF, 0xFF24A99B},
                {0xFFFF7043, 0xFF7C6BFF, 0xFF4CAF50},
                {0xFF4E7BE6, 0xFFA184E8, 0xFFE7B060},
                {0xFF66BB6A, 0xFF26C6DA, 0xFFFF8A65},
                {0xFFAB47BC, 0xFFFFA65A, 0xFF5C7CFA}
        };
        int[] p = palettes[Math.max(0, Math.min(palette, palettes.length - 1))];
        colorSpent = p[0];
        colorRemain = p[1];
        colorIncome = p[2];
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = Math.min(MeasureSpec.getSize(widthMeasureSpec), dp(188));
        if (size <= 0) size = dp(168);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        float stroke = dp(18);
        float pad = stroke / 2f + dp(10);
        RectF ring = new RectF(pad, pad, w - pad, h - pad);
        int total = Math.max(1, spent + remaining);
        float spentSweep = 360f * spent / total;
        float remainSweep = 360f - spentSweep;
        float gap = (spent > 0 && remaining > 0) ? 3.5f : 0f;

        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(stroke);
        ringPaint.setStrokeCap(Paint.Cap.ROUND);
        ringPaint.setColor(darkMode ? 0xFF273341 : 0xFFE7ECF4);
        canvas.drawArc(ring, -90, 360, false, ringPaint);

        float start = -90f;
        if (spent > 0) {
            ringPaint.setColor(colorSpent);
            float drawSweep = Math.max(6f, spentSweep - gap / 2f);
            canvas.drawArc(ring, start, drawSweep, false, ringPaint);
            start += spentSweep + gap;
        }
        if (remaining > 0) {
            ringPaint.setColor(colorRemain);
            float drawSweep = Math.max(6f, remainSweep - gap / 2f);
            canvas.drawArc(ring, start, drawSweep, false, ringPaint);
        }

        int percent = (int) Math.round(spent * 100f / total);
        if (spent == 0) percent = 0;
        if (spent > 0 && percent < 1) percent = 1;

        textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        textPaint.setTextSize(dp(22));
        textPaint.setColor(darkMode ? 0xFFF7FAFF : 0xFF20242B);
        canvas.drawText(percent + "%", w / 2f, h / 2f - dp(2), textPaint);

        textPaint.setTypeface(android.graphics.Typeface.DEFAULT);
        textPaint.setTextSize(dp(13));
        textPaint.setColor(darkMode ? 0xFFAAB5C3 : 0xFF647083);
        canvas.drawText(centerLabel, w / 2f, h / 2f + dp(20), textPaint);
    }

    public int spentColor() { return colorSpent; }
    public int remainColor() { return colorRemain; }
    public int incomeColor() { return colorIncome; }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
