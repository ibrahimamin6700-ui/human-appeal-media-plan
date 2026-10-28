package com.humanappeal.mediaplan;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * SEKNA "No internet" screen in the Aurora look, the same as the web app: navy with
 * slow blue and gold light, the logo dimmed while a soft light sweeps across it, and
 * the gold bar of the "E" flickering like a weak bulb with small rings.
 */
public class SeknaOfflineView extends FrameLayout {
    private static final int NAVY = Color.rgb(12, 26, 70);
    private static final int GOLD = Color.rgb(226, 184, 116);
    private static final int GOLD_LIGHT = Color.rgb(243, 215, 159);
    private static final int GOLD_DEEP = Color.rgb(194, 141, 62);
    private static final int MIST = Color.rgb(201, 211, 245);
    private static final int ALERT = Color.rgb(255, 143, 154);

    // The gold bar inside the "E" of the 667×149 logo.
    private static final float BAR_L = 165f / 667f, BAR_T = 64f / 149f, BAR_W = 58f / 667f, BAR_H = 23f / 149f;

    private final Runnable onRetry;
    private final AuroraView aurora;
    private final LogoFx logoFx;
    private final TextView retryButton;
    private final TextView statusText;
    private final View statusDot;
    private final View[] entranceViews;
    private boolean retrying = false;
    private ValueAnimator dotsAnimator;
    private ObjectAnimator blink;

    public SeknaOfflineView(Context context, Runnable onRetry) {
        super(context);
        this.onRetry = onRetry;
        setBackgroundColor(NAVY);
        setClickable(true);

        aurora = new AuroraView(context);
        addView(aurora, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(26), dp(24), dp(26), dp(24));

        // The logo, dimmed, with the light sweep, rings and flickering gold bar drawn on top.
        int logoW = dp(250), logoH = Math.round(logoW * 149f / 667f);
        FrameLayout logoBox = new FrameLayout(context);
        ImageView logo = new ImageView(context);
        logo.setImageResource(R.drawable.sekna_logo_white);
        logo.setScaleType(ImageView.ScaleType.FIT_XY);
        logo.setAlpha(0.35f);
        logoBox.addView(logo, new LayoutParams(logoW, logoH));
        logoFx = new LogoFx(context);
        logoBox.addView(logoFx, new LayoutParams(logoW, logoH));
        logoBox.setClipChildren(false);
        content.setClipChildren(false);
        LinearLayout.LayoutParams boxParams = new LinearLayout.LayoutParams(logoW, logoH);
        boxParams.bottomMargin = dp(56);
        content.addView(logoBox, boxParams);

        TextView kicker = text(context, "NO INTERNET", 11, ALERT, true);
        kicker.setLetterSpacing(0.14f);
        content.addView(kicker);

        TextView title = text(context, "Connection lost", 26, Color.WHITE, true);
        LinearLayout.LayoutParams titleParams = wrap();
        titleParams.topMargin = dp(8);
        content.addView(title, titleParams);

        TextView message = text(context, "We'll keep trying in the background. HEMERA updates the moment you're back online.", 14, MIST, false);
        message.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(dp(290), LinearLayout.LayoutParams.WRAP_CONTENT);
        messageParams.topMargin = dp(10);
        content.addView(message, messageParams);

        // "Searching for a connection" chip with a blinking dot.
        LinearLayout status = new LinearLayout(context);
        status.setOrientation(LinearLayout.HORIZONTAL);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding(dp(14), dp(8), dp(14), dp(8));
        GradientDrawable statusBg = new GradientDrawable();
        statusBg.setCornerRadius(dp(999));
        statusBg.setColor(Color.argb(31, 255, 143, 154));
        statusBg.setStroke(dp(1), Color.argb(77, 255, 143, 154));
        status.setBackground(statusBg);
        statusDot = new View(context);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(ALERT);
        statusDot.setBackground(dot);
        status.addView(statusDot, new LinearLayout.LayoutParams(dp(8), dp(8)));
        statusText = text(context, "Searching for a connection", 12, Color.rgb(255, 179, 186), true);
        LinearLayout.LayoutParams stParams = wrap();
        stParams.leftMargin = dp(8);
        status.addView(statusText, stParams);
        LinearLayout.LayoutParams statusParams = wrap();
        statusParams.topMargin = dp(14);
        content.addView(status, statusParams);

        retryButton = buildRetryButton(context);
        LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        retryParams.topMargin = dp(20);
        content.addView(retryButton, retryParams);

        FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        contentParams.gravity = Gravity.CENTER;
        addView(content, contentParams);

        entranceViews = new View[]{logoBox, kicker, title, message, status, retryButton};
    }

    private TextView text(Context context, String value, int sp, int color, boolean bold) {
        TextView t = new TextView(context);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return t;
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private TextView buildRetryButton(Context context) {
        TextView button = new TextView(context);
        button.setText("Try again");
        button.setTextSize(15);
        button.setTextColor(Color.rgb(15, 31, 82));
        button.setGravity(Gravity.CENTER);
        button.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        GradientDrawable body = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{GOLD, GOLD_LIGHT});
        body.setCornerRadius(dp(15));
        button.setBackground(body);
        button.setElevation(dp(6));
        button.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90).start();
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
            }
            return false;
        });
        button.setOnClickListener(v -> {
            if (retrying) return;
            setRetrying(true);
            if (onRetry != null) onRetry.run();
        });
        return button;
    }

    /** Called when the screen appears: staggered entrance and the looping animations. */
    public void show() {
        setRetrying(false);
        statusText.setText("Searching for a connection");
        for (int i = 0; i < entranceViews.length; i++) {
            View v = entranceViews[i];
            v.animate().cancel();
            v.setAlpha(0f);
            v.setTranslationY(dp(18));
            v.animate().alpha(1f).translationY(0f)
                    .setStartDelay(80L * i).setDuration(420)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
        aurora.start();
        logoFx.start();
        if (blink != null) blink.cancel();
        blink = ObjectAnimator.ofFloat(statusDot, "alpha", 1f, 0.25f);
        blink.setDuration(600);
        blink.setRepeatCount(ValueAnimator.INFINITE);
        blink.setRepeatMode(ValueAnimator.REVERSE);
        blink.start();
    }

    /** Stops all looping animations while the screen is hidden. */
    public void hide() {
        aurora.stop();
        logoFx.stop();
        if (blink != null) blink.cancel();
        stopDots();
    }

    /** A retry attempt failed: restore the button and give a short shake. */
    public void retryFailed() {
        if (!retrying) return;
        setRetrying(false);
        statusText.setText("Still offline · trying again");
        ObjectAnimator shake = ObjectAnimator.ofFloat(retryButton, "translationX",
                0, dp(10), -dp(10), dp(7), -dp(7), dp(3), 0);
        shake.setDuration(420);
        shake.start();
    }

    private void setRetrying(boolean value) {
        retrying = value;
        retryButton.setAlpha(value ? 0.85f : 1f);
        if (value) {
            startDots();
        } else {
            stopDots();
            retryButton.setText("Try again");
        }
    }

    private void startDots() {
        stopDots();
        dotsAnimator = ValueAnimator.ofInt(0, 3);
        dotsAnimator.setDuration(1200);
        dotsAnimator.setRepeatCount(ValueAnimator.INFINITE);
        dotsAnimator.setInterpolator(new LinearInterpolator());
        dotsAnimator.addUpdateListener(a -> {
            int dots = (int) a.getAnimatedValue();
            retryButton.setText("Connecting" + "...".substring(0, dots) + "   ".substring(dots));
        });
        dotsAnimator.start();
    }

    private void stopDots() {
        if (dotsAnimator != null) dotsAnimator.cancel();
        dotsAnimator = null;
    }

    @Override
    protected void onDetachedFromWindow() {
        hide();
        super.onDetachedFromWindow();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** Slow blue and gold light drifting over the navy, with a faint grid. */
    private static class AuroraView extends View {
        private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint grid = new Paint();
        private float t = 0f;
        private ValueAnimator animator;

        AuroraView(Context context) {
            super(context);
            grid.setColor(Color.argb(13, 255, 255, 255));
            grid.setStrokeWidth(1f);
        }

        void start() {
            if (animator != null && animator.isRunning()) return;
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(15000);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setRepeatMode(ValueAnimator.REVERSE);
            animator.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            animator.addUpdateListener(a -> {
                t = (float) a.getAnimatedValue();
                invalidate();
            });
            animator.start();
        }

        void stop() {
            if (animator != null) animator.cancel();
        }

        private void blob(Canvas c, float cx, float cy, float r, int color, int alpha) {
            glow.setShader(new RadialGradient(cx, cy, r, Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)),
                    Color.argb(0, Color.red(color), Color.green(color), Color.blue(color)), Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, r, glow);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), d = getResources().getDisplayMetrics().density;
            float step = 32 * d;
            for (float x = 0; x < w; x += step) c.drawLine(x, 0, x, h, grid);
            for (float y = 0; y < h; y += step) c.drawLine(0, y, w, y, grid);
            blob(c, w * (0.05f + 0.25f * t), h * (0.08f + 0.18f * t), w * 0.75f, Color.rgb(61, 107, 255), 170);
            blob(c, w * (0.95f - 0.3f * t), h * (0.88f - 0.22f * t), w * 0.6f, GOLD, 90);
            blob(c, w * (0.15f + 0.2f * t), h * (1.02f - 0.08f * t), w * 0.6f, Color.rgb(32, 60, 142), 200);
        }
    }

    /** On top of the dimmed logo: a soft light sweeping across, rings, and the flickering gold bar. */
    private static class LogoFx extends View {
        private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint sweep = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private long start = 0;
        private ValueAnimator animator;

        LogoFx(Context context) {
            super(context);
            ring.setStyle(Paint.Style.STROKE);
            setLayerType(LAYER_TYPE_HARDWARE, null);
        }

        void start() {
            start = System.currentTimeMillis();
            if (animator != null && animator.isRunning()) return;
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(1000);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.addUpdateListener(a -> invalidate());
            animator.start();
        }

        void stop() {
            if (animator != null) animator.cancel();
        }

        /** Same steps as the web: the bar is on for short moments, like a weak bulb. */
        private boolean bulbOn(float p) {
            return (p < 0.09f) || (p >= 0.20f && p < 0.27f) || (p >= 0.60f && p < 0.65f);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), d = getResources().getDisplayMetrics().density;
            long now = System.currentTimeMillis() - start;

            // Light sweeping left to right across the logo every 2.6 s.
            float sp = (now % 2600) / 2600f;
            float sw = w * 0.22f, sx = -w * 0.25f + sp * w * 1.3f;
            sweep.setShader(new LinearGradient(sx, 0, sx + sw, 0,
                    new int[]{Color.argb(0, 201, 211, 245), Color.argb(120, 201, 211, 245), Color.argb(0, 201, 211, 245)},
                    null, Shader.TileMode.CLAMP));
            c.drawRect(sx, -h * 0.1f, sx + sw, h * 1.1f, sweep);

            float cx = w * (BAR_L + BAR_W / 2f), cy = h * (BAR_T + BAR_H / 2f);
            // Two rings spreading from the bar every 2.2 s.
            ring.setStrokeWidth(2 * d);
            for (int i = 0; i < 2; i++) {
                float rp = ((now + i * 700) % 2200) / 2200f;
                ring.setColor(Color.argb(Math.round(153 * (1 - rp)), 226, 184, 116));
                c.drawCircle(cx, cy, 7 * d * (1 + 8 * rp), ring);
            }

            // The gold bar, flickering.
            boolean on = bulbOn((now % 2200) / 2200f);
            r.set(w * BAR_L, h * BAR_T, w * (BAR_L + BAR_W), h * (BAR_T + BAR_H));
            if (on) {
                halo.setShader(new RadialGradient(cx, cy, w * 0.09f, Color.argb(150, 226, 184, 116), Color.argb(0, 226, 184, 116), Shader.TileMode.CLAMP));
                c.drawCircle(cx, cy, w * 0.09f, halo);
            }
            bar.setShader(new LinearGradient(0, r.top, 0, r.bottom, GOLD_LIGHT, GOLD_DEEP, Shader.TileMode.CLAMP));
            bar.setAlpha(on ? 255 : 38);
            c.drawRoundRect(r, 3 * d, 3 * d, bar);
        }
    }
}
