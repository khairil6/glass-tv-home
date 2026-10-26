package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

/** Red live-dot that expands and fades in a loop. */
public class NewsLiveDot extends View {
    private static final int COLOR = 0xFFEF4444;
    private final Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ValueAnimator anim;
    private float pulse;

    public NewsLiveDot(Context context) {
        super(context);
        init();
    }

    public NewsLiveDot(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public NewsLiveDot(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        core.setColor(COLOR);
        core.setStyle(Paint.Style.FILL);
        ring.setColor(COLOR);
        ring.setStyle(Paint.Style.FILL);
        setWillNotDraw(false);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        start();
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) start();
        else stop();
    }

    private void start() {
        if (anim != null && anim.isStarted()) return;
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(1100);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setRepeatMode(ValueAnimator.RESTART);
        anim.setInterpolator(new AccelerateDecelerateInterpolator());
        anim.addUpdateListener(a -> {
            pulse = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    private void stop() {
        if (anim != null) {
            anim.cancel();
            anim = null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float coreR = Math.min(cx, cy) * 0.38f;
        float ringR = coreR + (Math.min(cx, cy) - coreR) * pulse;
        ring.setAlpha(Math.max(0, (int) (180 * (1f - pulse))));
        canvas.drawCircle(cx, cy, ringR, ring);
        float coreScale = 0.88f + 0.12f * (float) Math.sin(pulse * Math.PI);
        core.setAlpha(255);
        canvas.drawCircle(cx, cy, coreR * coreScale, core);
    }
}
