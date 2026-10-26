package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.Random;

/**
 * Background weather animation over the wallpaper: rain, storm, fog, wind, heat haze.
 * Cheap particles on a TV GPU — no bitmaps, no saveLayer.
 */
public class WeatherFxView extends View {
    static final int MODE_NONE = 0;
    static final int MODE_DRIZZLE = 1;
    static final int MODE_RAIN = 2;
    static final int MODE_HEAVY = 3;
    static final int MODE_STORM = 4;
    static final int MODE_FOG = 5;
    static final int MODE_CLOUD = 6;
    static final int MODE_WIND = 7;
    static final int MODE_HEAT = 8;

    private static final int MAX = 220;

    private final Random rng = new Random();
    private final Paint dropPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mistPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flashPaint = new Paint();
    private final Paint boltPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path bolt = new Path();
    private final float[] x = new float[MAX];
    private final float[] y = new float[MAX];
    private final float[] vx = new float[MAX];
    private final float[] vy = new float[MAX];
    private final float[] len = new float[MAX];
    private final float[] a = new float[MAX];
    private final float[] w = new float[MAX];

    private int mode = MODE_NONE;
    private int count;
    private int windKmh;
    private boolean running;
    private boolean paused;
    private long lastNs;
    private float flash;
    private float nextBoltS = 4f;
    private float boltTimer;
    private LinearGradient fogGrad;

    public WeatherFxView(Context context) {
        super(context);
        init();
    }

    public WeatherFxView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public WeatherFxView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        dropPaint.setStrokeCap(Paint.Cap.ROUND);
        boltPaint.setStyle(Paint.Style.STROKE);
        boltPaint.setStrokeCap(Paint.Cap.ROUND);
        boltPaint.setStrokeJoin(Paint.Join.ROUND);
        boltPaint.setColor(0xE6FFFFFF);
        flashPaint.setStyle(Paint.Style.FILL);
        setClickable(false);
        setFocusable(false);
    }

    void setFromWeather(int code, int temp, int windKmh) {
        this.windKmh = Math.max(0, windKmh);
        int next = modeOf(code, temp, windKmh);
        if (next != mode || count == 0) {
            mode = next;
            spawnAll();
        }
        if (mode == MODE_NONE) {
            running = false;
            invalidate();
        } else {
            tickRunning();
        }
    }

    void setPaused(boolean paused) {
        this.paused = paused;
        if (paused) running = false;
        else tickRunning();
        invalidate();
    }

    static int modeOf(int code, int temp, int wind) {
        if (code == 95 || code == 96 || code == 99) return MODE_STORM;
        if (code == 80 || code == 81 || code == 82) return MODE_HEAVY;
        if (code == 61 || code == 63 || code == 65) return MODE_RAIN;
        if (code == 51 || code == 53 || code == 55) return MODE_DRIZZLE;
        if (code == 45 || code == 48) return MODE_FOG;
        if (code == 3) return MODE_CLOUD;
        if (wind >= 22 && code <= 2) return MODE_WIND;
        if (temp >= 33 && code <= 2) return MODE_HEAT;
        if (code == 1 || code == 2) return MODE_CLOUD;
        return MODE_NONE;
    }

    private void spawnAll() {
        int wpx = Math.max(getWidth(), 1920);
        int hpx = Math.max(getHeight(), 1080);
        switch (mode) {
            case MODE_DRIZZLE:
                count = 120;
                break;
            case MODE_RAIN:
                count = 160;
                break;
            case MODE_HEAVY:
            case MODE_STORM:
                count = 200;
                break;
            case MODE_FOG:
                count = 7;
                break;
            case MODE_CLOUD:
                count = 6;
                break;
            case MODE_WIND:
                count = 55;
                break;
            case MODE_HEAT:
                count = 42;
                break;
            default:
                count = 0;
                return;
        }
        for (int i = 0; i < count; i++) spawn(i, wpx, hpx, true);
        lastNs = 0;
        flash = 0;
        boltTimer = 0;
        nextBoltS = 3f + rng.nextFloat() * 5f;
    }

    private void spawn(int i, int W, int H, boolean anywhere) {
        float wind = windKmh;
        switch (mode) {
            case MODE_DRIZZLE:
                x[i] = rng.nextFloat() * (W + 80) - 40;
                y[i] = anywhere ? rng.nextFloat() * H : -20 - rng.nextFloat() * 80;
                vx[i] = wind * 1.2f + rng.nextFloat() * 20f;
                vy[i] = 420 + rng.nextFloat() * 320;
                len[i] = 11 + rng.nextFloat() * 12;
                a[i] = 0.32f + rng.nextFloat() * 0.28f;
                w[i] = 1.8f;
                break;
            case MODE_RAIN:
            case MODE_HEAVY:
            case MODE_STORM:
                x[i] = rng.nextFloat() * (W + 120) - 60;
                y[i] = anywhere ? rng.nextFloat() * H : -30 - rng.nextFloat() * 120;
                vx[i] = wind * 1.8f + rng.nextFloat() * 30f;
                float base = mode == MODE_RAIN ? 720 : 980;
                vy[i] = base + rng.nextFloat() * 520;
                len[i] = (mode == MODE_RAIN ? 14 : 18) + rng.nextFloat() * 18;
                a[i] = 0.38f + rng.nextFloat() * 0.38f;
                w[i] = mode == MODE_STORM ? 2.4f : 2.0f;
                break;
            case MODE_FOG:
                x[i] = rng.nextFloat() * W;
                y[i] = rng.nextFloat() * H;
                vx[i] = 8 + rng.nextFloat() * 14;
                vy[i] = (rng.nextFloat() - 0.5f) * 6f;
                len[i] = W * (0.35f + rng.nextFloat() * 0.4f);
                w[i] = 90 + rng.nextFloat() * 140;
                a[i] = 0.06f + rng.nextFloat() * 0.08f;
                break;
            case MODE_CLOUD:
                x[i] = anywhere ? rng.nextFloat() * W : -400;
                y[i] = rng.nextFloat() * (H * 0.45f);
                vx[i] = 6 + rng.nextFloat() * 12 + wind * 0.15f;
                vy[i] = 0;
                len[i] = 220 + rng.nextFloat() * 340;
                w[i] = 50 + rng.nextFloat() * 80;
                a[i] = 0.05f + rng.nextFloat() * 0.07f;
                break;
            case MODE_WIND:
                x[i] = anywhere ? rng.nextFloat() * W : -20;
                y[i] = rng.nextFloat() * H;
                vx[i] = 280 + rng.nextFloat() * 420 + wind * 8f;
                vy[i] = (rng.nextFloat() - 0.45f) * 80f;
                len[i] = 18 + rng.nextFloat() * 36;
                a[i] = 0.12f + rng.nextFloat() * 0.2f;
                w[i] = 1.2f + rng.nextFloat();
                break;
            case MODE_HEAT:
                x[i] = rng.nextFloat() * W;
                y[i] = anywhere ? rng.nextFloat() * H : H + 20;
                vx[i] = (rng.nextFloat() - 0.5f) * 18f;
                vy[i] = -(30 + rng.nextFloat() * 50);
                len[i] = 16 + rng.nextFloat() * 28;
                w[i] = 8 + rng.nextFloat() * 14;
                a[i] = 0.08f + rng.nextFloat() * 0.1f;
                break;
            default:
                break;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0) {
            fogGrad = new LinearGradient(0, 0, 0, h,
                    new int[]{0x33D6E4F0, 0x14D6E4F0, 0x00000000},
                    new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
            if (mode != MODE_NONE) spawnAll();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        tickRunning();
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) tickRunning();
        else running = false;
    }

    private void tickRunning() {
        boolean on = !paused && mode != MODE_NONE && getWindowVisibility() == VISIBLE
                && isAttachedToWindow();
        if (on && !running) {
            running = true;
            lastNs = 0;
            postInvalidateOnAnimation();
        }
        if (!on) running = false;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int W = getWidth();
        int H = getHeight();
        if (W <= 0 || H <= 0 || mode == MODE_NONE) return;

        long now = System.nanoTime();
        float dt = 0.016f;
        if (lastNs > 0) {
            dt = (now - lastNs) / 1_000_000_000f;
            if (dt < 0.004f) dt = 0.004f;
            if (dt > 0.05f) dt = 0.05f;
        }
        lastNs = now;

        switch (mode) {
            case MODE_DRIZZLE:
            case MODE_RAIN:
            case MODE_HEAVY:
            case MODE_STORM:
                drawRain(canvas, W, H, dt);
                break;
            case MODE_FOG:
                drawFog(canvas, W, H, dt);
                break;
            case MODE_CLOUD:
                drawClouds(canvas, W, H, dt);
                break;
            case MODE_WIND:
                drawWind(canvas, W, H, dt);
                break;
            case MODE_HEAT:
                drawHeat(canvas, W, H, dt);
                break;
            default:
                break;
        }

        if (running) postInvalidateOnAnimation();
    }

    private void drawRain(Canvas canvas, int W, int H, float dt) {
        boolean storm = mode == MODE_STORM;
        if (storm) {
            boltTimer += dt;
            if (flash > 0) {
                flash -= dt * 2.8f;
                if (flash < 0) flash = 0;
            }
            if (boltTimer >= nextBoltS) {
                boltTimer = 0;
                nextBoltS = 3.5f + rng.nextFloat() * 6f;
                flash = 0.55f + rng.nextFloat() * 0.35f;
                buildBolt(W, H);
            }
            if (flash > 0.02f) {
                flashPaint.setColor(((int) (90 * flash) << 24) | 0x00D6E8FF);
                canvas.drawRect(0, 0, W, H, flashPaint);
                boltPaint.setStrokeWidth(2.4f);
                boltPaint.setAlpha((int) (220 * Math.min(1f, flash * 1.6f)));
                canvas.drawPath(bolt, boltPaint);
            }
        }

        dropPaint.setColor(storm ? 0xFFC5DDF0 : 0xFFEAF4FC);
        for (int i = 0; i < count; i++) {
            x[i] += vx[i] * dt;
            y[i] += vy[i] * dt;
            if (y[i] > H + 20 || x[i] > W + 40) spawn(i, W, H, false);
            dropPaint.setStrokeWidth(w[i]);
            dropPaint.setAlpha((int) (a[i] * 255));
            float dx = vx[i] * (len[i] / Math.max(40f, vy[i]));
            canvas.drawLine(x[i], y[i], x[i] + dx, y[i] + len[i], dropPaint);
        }
    }

    private void buildBolt(int W, int H) {
        bolt.reset();
        float px = W * (0.2f + rng.nextFloat() * 0.6f);
        float py = 0;
        bolt.moveTo(px, py);
        int segs = 7 + rng.nextInt(5);
        for (int i = 1; i <= segs; i++) {
            px += (rng.nextFloat() - 0.5f) * 70f;
            py = H * (i / (float) segs) * (0.45f + rng.nextFloat() * 0.2f);
            bolt.lineTo(px, py);
            if (rng.nextFloat() < 0.35f) {
                bolt.moveTo(px, py);
                bolt.lineTo(px + (rng.nextFloat() - 0.5f) * 90f, py + 30 + rng.nextFloat() * 50);
                bolt.moveTo(px, py);
            }
        }
    }

    private void drawFog(Canvas canvas, int W, int H, float dt) {
        if (fogGrad != null) {
            mistPaint.setShader(fogGrad);
            mistPaint.setAlpha(255);
            canvas.drawRect(0, 0, W, H, mistPaint);
            mistPaint.setShader(null);
        }
        mistPaint.setColor(0xFFFFFFFF);
        for (int i = 0; i < count; i++) {
            x[i] += vx[i] * dt;
            y[i] += vy[i] * dt;
            if (x[i] - len[i] > W) x[i] = -len[i];
            if (y[i] < -w[i]) y[i] = H + w[i];
            if (y[i] > H + w[i]) y[i] = -w[i];
            mistPaint.setAlpha((int) (a[i] * 255));
            canvas.drawOval(x[i] - len[i] / 2f, y[i] - w[i] / 2f,
                    x[i] + len[i] / 2f, y[i] + w[i] / 2f, mistPaint);
        }
    }

    private void drawClouds(Canvas canvas, int W, int H, float dt) {
        mistPaint.setColor(0xFFFFFFFF);
        for (int i = 0; i < count; i++) {
            x[i] += vx[i] * dt;
            if (x[i] - len[i] > W) {
                x[i] = -len[i];
                y[i] = rng.nextFloat() * (H * 0.45f);
            }
            mistPaint.setAlpha((int) (a[i] * 255));
            float l = len[i], hh = w[i];
            canvas.drawOval(x[i], y[i], x[i] + l, y[i] + hh, mistPaint);
            canvas.drawOval(x[i] + l * 0.25f, y[i] - hh * 0.35f, x[i] + l * 0.8f, y[i] + hh * 0.7f, mistPaint);
        }
    }

    private void drawWind(Canvas canvas, int W, int H, float dt) {
        dropPaint.setColor(0xFFE8F0E4);
        for (int i = 0; i < count; i++) {
            x[i] += vx[i] * dt;
            y[i] += vy[i] * dt;
            if (x[i] > W + 40) spawn(i, W, H, false);
            dropPaint.setStrokeWidth(w[i]);
            dropPaint.setAlpha((int) (a[i] * 255));
            canvas.drawLine(x[i], y[i], x[i] + len[i], y[i] + vy[i] * 0.04f, dropPaint);
        }
    }

    private void drawHeat(Canvas canvas, int W, int H, float dt) {
        mistPaint.setColor(0xFFFFC48A);
        for (int i = 0; i < count; i++) {
            x[i] += vx[i] * dt + (float) Math.sin(y[i] * 0.04f + i) * 12f * dt;
            y[i] += vy[i] * dt;
            if (y[i] + len[i] < -10) spawn(i, W, H, false);
            mistPaint.setAlpha((int) (a[i] * 255));
            canvas.drawOval(x[i], y[i], x[i] + w[i], y[i] + len[i], mistPaint);
        }
        flashPaint.setColor(0x14FFB070);
        canvas.drawRect(0, 0, W, H, flashPaint);
    }
}
