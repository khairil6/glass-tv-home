package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** Seamless left-scrolling news ticker drawn inside the home pill. */
public class NewsTickerView extends View {
    private static final float SPEED_PX_S = 48f;
    private static final float GAP = 36f;

    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tagPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<NewsFeed.Item> items = new ArrayList<>();
    private String placeholder = "";
    private float offset;
    private float cycleW;
    private long lastFrameNs;
    private boolean running;
    private LinearGradient fade;

    public NewsTickerView(Context context) {
        super(context);
        init();
    }

    public NewsTickerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public NewsTickerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        float sp = getResources().getDisplayMetrics().scaledDensity;
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextSize(14f * sp);
        textPaint.setFakeBoldText(false);
        tagPaint.setTextSize(12f * sp);
        tagPaint.setFakeBoldText(true);
        tagPaint.setLetterSpacing(0.06f);
        try {
            Typeface tf = getResources().getFont(R.font.outfit);
            if (Build.VERSION.SDK_INT >= 28) {
                textPaint.setTypeface(Typeface.create(tf, 500, false));
                tagPaint.setTypeface(Typeface.create(tf, 700, false));
            } else {
                textPaint.setTypeface(tf);
                tagPaint.setTypeface(Typeface.create(tf, Typeface.BOLD));
            }
        } catch (Exception e) {
            textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            tagPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        }
        fadePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
    }

    void setPlaceholder(String text) {
        placeholder = text == null ? "" : text;
        items.clear();
        offset = 0;
        cycleW = 0;
        invalidate();
    }

    void setItems(List<NewsFeed.Item> list) {
        items.clear();
        if (list != null) items.addAll(list);
        offset = 0;
        cycleW = measureCycle();
        lastFrameNs = 0;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            fade = new LinearGradient(0, 0, w, 0,
                    new int[]{0x00000000, 0xFF000000, 0xFF000000, 0x00000000},
                    new float[]{0f, 0.04f, 0.96f, 1f},
                    Shader.TileMode.CLAMP);
            fadePaint.setShader(fade);
        }
        cycleW = measureCycle();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true;
        lastFrameNs = 0;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        running = visibility == VISIBLE;
        if (running) {
            lastFrameNs = 0;
            postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        int save = canvas.saveLayer(0, 0, w, h, null);
        float baseline = (h / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f);

        if (items.isEmpty()) {
            if (!placeholder.isEmpty()) {
                canvas.drawText(placeholder, 0, baseline, textPaint);
            }
        } else {
            float cycle = cycleW > 0 ? cycleW : measureCycle();
            cycleW = cycle;
            if (cycle <= w + 8) {
                drawItems(canvas, 0, baseline);
            } else {
                float x = -offset;
                while (x < w) {
                    drawItems(canvas, x, baseline);
                    x += cycle;
                }
                long now = System.nanoTime();
                if (lastFrameNs > 0 && running) {
                    float dt = (now - lastFrameNs) / 1_000_000_000f;
                    if (dt > 0 && dt < 0.1f) {
                        offset += SPEED_PX_S * dt;
                        if (offset >= cycle) offset -= cycle;
                    }
                }
                lastFrameNs = now;
            }
        }

        if (fade != null) {
            canvas.drawRect(0, 0, w, h, fadePaint);
        }
        canvas.restoreToCount(save);

        if (running && cycleW > w + 8 && !items.isEmpty()) {
            postInvalidateOnAnimation();
        }
    }

    private void drawItems(Canvas canvas, float startX, float baseline) {
        float x = startX;
        for (NewsFeed.Item it : items) {
            tagPaint.setColor(it.tagColor());
            canvas.drawText(it.tag(), x, baseline, tagPaint);
            x += tagPaint.measureText(it.tag()) + 8f;
            canvas.drawText(it.title, x, baseline, textPaint);
            x += textPaint.measureText(it.title);
            textPaint.setColor(0x66FFFFFF);
            canvas.drawText("    ·    ", x, baseline, textPaint);
            x += textPaint.measureText("    ·    ");
            textPaint.setColor(0xFFFFFFFF);
        }
        if (startX == 0) {
            // trailing gap already included via last separator
        }
    }

    private float measureCycle() {
        if (items.isEmpty()) return 0;
        float w = 0;
        for (NewsFeed.Item it : items) {
            w += tagPaint.measureText(it.tag()) + 8f;
            w += textPaint.measureText(it.title);
            w += textPaint.measureText("    ·    ");
        }
        return w + GAP;
    }
}
