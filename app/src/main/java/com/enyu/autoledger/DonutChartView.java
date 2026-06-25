package com.enyu.autoledger;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

public class DonutChartView extends View {
    private int primary = 0;
    private int secondary = 1;
    private int third = 0;
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int colorPrimary = 0xFFFF6B57;
    private int colorSecondary = 0xFF4AA8FF;
    private int colorThird = 0xFF24A99B;
    private boolean darkMode = false;
    private String centerLabel = "已使用";
    private String primaryLabel = "已花費";
    private String secondaryLabel = "剩餘";
    private String thirdLabel = "收入";

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
        if ("已使用".equals(this.centerLabel)) {
            this.primaryLabel = "已花費";
            this.secondaryLabel = "剩餘預算";
        } else if ("支出".equals(this.centerLabel)) {
            this.primaryLabel = "支出";
            this.secondaryLabel = "剩餘預算";
        } else if ("收入".equals(this.centerLabel)) {
            this.primaryLabel = "收入";
            this.secondaryLabel = "預算參考";
        } else if ("結餘".equals(this.centerLabel)) {
            this.primaryLabel = "結餘";
            this.secondaryLabel = "已花費";
        } else {
            this.primaryLabel = this.centerLabel;
            this.secondaryLabel = "剩餘";
        }
        invalidate();
    }

    public void setSegmentLabels(String primaryLabel, String secondaryLabel, String thirdLabel) {
        if (primaryLabel != null && !primaryLabel.trim().isEmpty()) this.primaryLabel = primaryLabel.trim();
        if (secondaryLabel != null && !secondaryLabel.trim().isEmpty()) this.secondaryLabel = secondaryLabel.trim();
        if (thirdLabel != null && !thirdLabel.trim().isEmpty()) this.thirdLabel = thirdLabel.trim();
        invalidate();
    }

    public void setData(int primary, int secondary, int third, int palette) {
        this.primary = Math.max(0, primary);
        this.secondary = Math.max(0, secondary);
        this.third = Math.max(0, third);
        int[][] palettes = new int[][]{
                {0xFFFF6B57, 0xFF4AA8FF, 0xFF24A99B},
                {0xFFFF7043, 0xFF7C6BFF, 0xFF4CAF50},
                {0xFF4E7BE6, 0xFFA184E8, 0xFFE7B060},
                {0xFF66BB6A, 0xFF26C6DA, 0xFFFF8A65},
                {0xFFAB47BC, 0xFFFFA65A, 0xFF5C7CFA}
        };
        int[] p = palettes[Math.max(0, Math.min(palette, palettes.length - 1))];
        colorPrimary = p[0];
        colorSecondary = p[1];
        colorThird = p[2];
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int ws = MeasureSpec.getSize(widthMeasureSpec);
        int hs = MeasureSpec.getSize(heightMeasureSpec);
        int size = Math.min(ws <= 0 ? dp(188) : ws, hs <= 0 ? dp(188) : hs);
        if (size <= 0) size = dp(188);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        int total = Math.max(1, primary + secondary + third);
        float stroke = Math.max(dp(18), w * 0.15f);
        float pad = stroke / 2f + dp(8);
        RectF ring = new RectF(pad, pad, w - pad, h - pad);

        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(stroke);
        ringPaint.setStrokeCap(Paint.Cap.BUTT);
        ringPaint.setColor(darkMode ? 0xFF273341 : 0xFFE7ECF4);
        canvas.drawArc(ring, -90, 360, false, ringPaint);

        float start = -90f;
        float gap = total > 1 ? 1.2f : 0f;
        start = drawSegment(canvas, ring, start, primary, total, colorPrimary, gap);
        start = drawSegment(canvas, ring, start, secondary, total, colorSecondary, gap);
        drawSegment(canvas, ring, start, third, total, colorThird, gap);

        drawCenterLabel(canvas, total);
    }

    private float drawSegment(Canvas canvas, RectF ring, float start, int value, int total, int color, float gap) {
        if (value <= 0) return start;
        float sweep = 360f * value / total;
        float drawSweep = Math.max(0f, sweep - gap);
        ringPaint.setColor(color);
        canvas.drawArc(ring, start + gap / 2f, drawSweep, false, ringPaint);
        return start + sweep;
    }

    private void drawCenterLabel(Canvas canvas, int total) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        int pct = Math.round(primary * 100f / Math.max(1, total));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        textPaint.setColor(darkMode ? 0xFFF7FAFF : 0xFF20242B);
        textPaint.setTextSize(getWidth() >= dp(205) ? dp(18) : dp(14));
        canvas.drawText(centerLabel, cx, cy - dp(4), textPaint);
        textPaint.setTypeface(android.graphics.Typeface.DEFAULT);
        textPaint.setColor(darkMode ? 0xFFAAB3C2 : 0xFF687282);
        textPaint.setTextSize(getWidth() >= dp(205) ? dp(13) : dp(10));
        canvas.drawText(pct + "%", cx, cy + dp(16), textPaint);
    }

    public int spentColor() { return colorPrimary; }
    public int remainColor() { return colorSecondary; }
    public int incomeColor() { return colorThird; }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
