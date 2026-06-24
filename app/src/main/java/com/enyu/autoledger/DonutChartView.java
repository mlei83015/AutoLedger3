package com.enyu.autoledger;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

public class DonutChartView extends View {
    private int spent = 0;
    private int remaining = 1;
    private int income = 0;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int colorSpent = 0xFFFF5A45;
    private int colorRemain = 0xFFFFA726;
    private int colorIncome = 0xFF16A085;

    public DonutChartView(Context context) {
        super(context);
        textPaint.setColor(0xFF20242B);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setData(int spent, int remaining, int income, int palette) {
        this.spent = Math.max(0, spent);
        this.remaining = Math.max(0, remaining);
        this.income = Math.max(0, income);
        int[][] palettes = new int[][]{
                {0xFFFF5A45, 0xFFFFA726, 0xFF24A99B},
                {0xFFFF7043, 0xFFFFCA28, 0xFF4CAF50},
                {0xFF40A9FF, 0xFF7C6BFF, 0xFFFF8A65},
                {0xFF66BB6A, 0xFF26C6DA, 0xFFFF7043},
                {0xFFAB47BC, 0xFF7E57C2, 0xFFFF8A65}
        };
        int[] p = palettes[Math.max(0, Math.min(palette, palettes.length - 1))];
        colorSpent = p[0];
        colorRemain = p[1];
        colorIncome = p[2];
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = Math.min(MeasureSpec.getSize(widthMeasureSpec), dp(180));
        if (size <= 0) size = dp(160);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        int pad = dp(12);
        RectF r = new RectF(pad, pad, w - pad, h - pad);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(dp(22));
        paint.setColor(0xFFEFEFF3);
        canvas.drawArc(r, -90, 360, false, paint);

        int total = Math.max(1, spent + remaining);
        float spentSweep = 360f * spent / total;
        float remainSweep = 360f - spentSweep;
        paint.setColor(colorRemain);
        canvas.drawArc(r, -90 + spentSweep, Math.max(0, remainSweep), false, paint);
        if (spent > 0) {
            paint.setColor(colorSpent);
            canvas.drawArc(r, -90, Math.max(8, spentSweep), false, paint);
        }

        textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        textPaint.setTextSize(dp(23));
        int percent = (int) Math.ceil(spent * 100f / total);
        if (spent == 0) percent = 0;
        if (spent > 0 && percent < 1) percent = 1;
        canvas.drawText(percent + "%", w / 2f, h / 2f - dp(4), textPaint);
        textPaint.setTypeface(android.graphics.Typeface.DEFAULT);
        textPaint.setTextSize(dp(13));
        textPaint.setColor(0xFF5F6672);
        canvas.drawText("已使用", w / 2f, h / 2f + dp(18), textPaint);
        textPaint.setColor(0xFF20242B);
    }

    public int spentColor() { return colorSpent; }
    public int remainColor() { return colorRemain; }
    public int incomeColor() { return colorIncome; }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
