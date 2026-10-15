package com.humanappeal.mediaplan;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * SEKNA branded "No Internet" screen: soft network curves in the background,
 * an animated signal illustration, and a raised navy Retry button.
 */
public class SeknaOfflineView extends FrameLayout {
    private static final int NAVY = SeknaProgressBar.NAVY;
    private static final int GOLD = Color.rgb(194, 141, 62);
    private static final int BACKGROUND = Color.rgb(254, 254, 254);

    private final Runnable onRetry;
    private final BackgroundCurves curves;
    private final SignalView signal;
    private final TextView retryButton;
    private final View[] entranceViews;
    private boolean retrying = false;
    private ValueAnimator dotsAnimator;

    public SeknaOfflineView(Context context, Runnable onRetry) {
        super(context);
        this.onRetry = onRetry;
        setBackgroundColor(BACKGROUND);
        setClickable(true);

        curves = new BackgroundCurves(context);
        addView(curves, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(28), dp(24), dp(28), dp(24));

        ImageView logo = new ImageView(context);
        logo.setImageResource(R.drawable.sekna_logo);
        logo.setAdjustViewBounds(true);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(190), LinearLayout.LayoutParams.WRAP_CONTENT);
        logoParams.bottomMargin = dp(34);
        content.addView(logo, logoParams);

        signal = new SignalView(context);
        LinearLayout.LayoutParams signalParams = new LinearLayout.LayoutParams(dp(150), dp(150));
        signalParams.bottomMargin = dp(22);
        content.addView(signal, signalParams);

        TextView title = new TextView(context);
        title.setText("No Internet Connection");
        title.setTextSize(22);
        title.setTextColor(NAVY);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        title.setGravity(Gravity.CENTER);
        content.addView(title);

        View accent = new View(context);
        GradientDrawable accentBg = new GradientDrawable();
        accentBg.setColor(GOLD);
        accentBg.setCornerRadius(dp(2));
        accent.setBackground(accentBg);
        LinearLayout.LayoutParams accentParams = new LinearLayout.LayoutParams(dp(34), dp(4));
        accentParams.topMargin = dp(12);
        content.addView(accent, accentParams);

        TextView message = new TextView(context);
        message.setText("Check your connection and try again.\nتحقق من اتصال الإنترنت ثم حاول مرة أخرى.");
        message.setTextSize(14);
        message.setTextColor(Color.rgb(98, 102, 120));
        message.setGravity(Gravity.CENTER);
        message.setLineSpacing(dp(4), 1f);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        messageParams.topMargin = dp(14);
        messageParams.bottomMargin = dp(32);
        content.addView(message, messageParams);

        retryButton = buildRetryButton(context);
        LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(dp(210), dp(52));
        content.addView(retryButton, retryParams);

        FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        contentParams.gravity = Gravity.CENTER;
        addView(content, contentParams);

        entranceViews = new View[]{logo, signal, title, accent, message, retryButton};
    }

    private TextView buildRetryButton(Context context) {
        TextView button = new TextView(context);
        button.setText("↻  Retry");
        button.setTextSize(16);
        button.setTextColor(Color.WHITE);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));

        GradientDrawable body = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(74, 104, 196), NAVY, Color.rgb(20, 38, 96)});
        body.setCornerRadius(dp(26));
        GradientDrawable gloss = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(95, 255, 255, 255), Color.argb(0, 255, 255, 255)});
        gloss.setCornerRadius(dp(26));
        LayerDrawable layers = new LayerDrawable(new android.graphics.drawable.Drawable[]{body, gloss});
        layers.setLayerInset(1, dp(3), dp(2), dp(3), dp(26));
        button.setBackground(layers);
        button.setElevation(dp(8));

        button.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.95f).scaleY(0.95f).translationZ(-dp(4)).setDuration(90).start();
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f).translationZ(0).setDuration(160).start();
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

    /** Called when the screen appears: staggered entrance and looping animations. */
    public void show() {
        setRetrying(false);
        for (int i = 0; i < entranceViews.length; i++) {
            View v = entranceViews[i];
            v.animate().cancel();
            v.setAlpha(0f);
            v.setTranslationY(dp(18));
            v.animate().alpha(1f).translationY(0f)
                    .setStartDelay(80L * i).setDuration(420)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
        curves.start();
        signal.start();
    }

    /** Stops all looping animations while the screen is hidden. */
    public void hide() {
        curves.stop();
        signal.stop();
        stopDots();
    }

    /** A retry attempt failed: restore the button and give a short shake. */
    public void retryFailed() {
        if (!retrying) return;
        setRetrying(false);
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
            retryButton.setText("↻  Retry");
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

    /** Faint network curves with breathing nodes, echoing the SEKNA splash. */
    private static class BackgroundCurves extends View {
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint node = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path top = new Path();
        private final Path bottom = new Path();
        private float phase = 0f;
        private ValueAnimator animator;

        BackgroundCurves(Context context) {
            super(context);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeCap(Paint.Cap.ROUND);
            line.setColor(Color.rgb(241, 242, 249));
            halo.setColor(Color.rgb(238, 239, 247));
            node.setColor(Color.rgb(214, 217, 236));
        }

        void start() {
            if (animator != null && animator.isRunning()) return;
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(2600);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setRepeatMode(ValueAnimator.REVERSE);
            animator.addUpdateListener(a -> {
                phase = (float) a.getAnimatedValue();
                invalidate();
            });
            animator.start();
        }

        void stop() {
            if (animator != null) animator.cancel();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            line.setStrokeWidth(w * 0.018f);
            top.reset();
            top.moveTo(-w * 0.05f, h * 0.13f);
            top.cubicTo(w * 0.22f, h * 0.19f, w * 0.30f, h * 0.30f, w * 0.48f, h * 0.38f);
            bottom.reset();
            bottom.moveTo(w * 1.05f, h * 0.55f);
            bottom.cubicTo(w * 0.75f, h * 0.68f, w * 0.62f, h * 0.84f, -w * 0.05f, h * 0.86f);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            canvas.drawPath(top, line);
            canvas.drawPath(bottom, line);
            float pulse = 1f + 0.18f * phase;
            drawNode(canvas, w * 0.17f, h * 0.175f, w * 0.03f, pulse);
            drawNode(canvas, w * 0.29f, h * 0.265f, w * 0.018f, 1.18f - 0.18f * phase);
            drawNode(canvas, w * 0.80f, h * 0.635f, w * 0.03f, 1.18f - 0.18f * phase);
            drawNode(canvas, w * 0.68f, h * 0.73f, w * 0.018f, pulse);
        }

        private void drawNode(Canvas canvas, float x, float y, float r, float scale) {
            canvas.drawCircle(x, y, r * 2f * scale, halo);
            canvas.drawCircle(x, y, r, node);
        }
    }

    /** Wi-Fi glyph with a gold "off" slash, ripple rings and a gentle float. */
    private static class SignalView extends View {
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint disc = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint discEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint slash = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint slashGap = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();
        private float phase = 0f;
        private ValueAnimator animator;

        SignalView(Context context) {
            super(context);
            ring.setStyle(Paint.Style.STROKE);
            discEdge.setStyle(Paint.Style.STROKE);
            discEdge.setColor(Color.argb(40, 32, 60, 142));
            arc.setStyle(Paint.Style.STROKE);
            arc.setStrokeCap(Paint.Cap.ROUND);
            arc.setColor(NAVY);
            dot.setColor(NAVY);
            slash.setStyle(Paint.Style.STROKE);
            slash.setStrokeCap(Paint.Cap.ROUND);
            slash.setColor(GOLD);
            slashGap.setStyle(Paint.Style.STROKE);
            slashGap.setStrokeCap(Paint.Cap.ROUND);
            setElevation(0);
        }

        void start() {
            if (animator != null && animator.isRunning()) return;
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(2400);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(a -> {
                phase = (float) a.getAnimatedValue();
                invalidate();
            });
            animator.start();
            setScaleX(0.6f);
            setScaleY(0.6f);
            animate().scaleX(1f).scaleY(1f).setDuration(620).setStartDelay(120)
                    .setInterpolator(new OvershootInterpolator(1.6f)).start();
        }

        void stop() {
            if (animator != null) animator.cancel();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            float size = Math.min(w, h);
            disc.setShader(new LinearGradient(0, h / 2f - size * 0.3f, 0, h / 2f + size * 0.3f,
                    Color.WHITE, Color.rgb(232, 237, 249), Shader.TileMode.CLAMP));
            discEdge.setStrokeWidth(size * 0.012f);
            ring.setStrokeWidth(size * 0.012f);
            arc.setStrokeWidth(size * 0.045f);
            slash.setStrokeWidth(size * 0.045f);
            slashGap.setStrokeWidth(size * 0.09f);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float size = Math.min(w, h);
            float cx = w / 2f;
            float cy = h / 2f;
            float discR = size * 0.30f;

            // Ripple rings moving outward.
            for (int i = 0; i < 3; i++) {
                float p = (phase + i / 3f) % 1f;
                float r = discR + (size * 0.5f - discR) * p;
                ring.setColor(Color.argb((int) (90 * (1f - p)), 32, 60, 142));
                canvas.drawCircle(cx, cy, r, ring);
            }

            canvas.drawCircle(cx, cy, discR, disc);
            canvas.drawCircle(cx, cy, discR, discEdge);

            // Gentle float of the glyph.
            float bob = (float) Math.sin(phase * Math.PI * 2) * size * 0.012f;
            float gx = cx;
            float gy = cy + size * 0.085f + bob;
            float[] radii = {size * 0.17f, size * 0.115f, size * 0.06f};
            for (float r : radii) {
                oval.set(gx - r, gy - r, gx + r, gy + r);
                canvas.drawArc(oval, 225, 90, false, arc);
            }
            canvas.drawCircle(gx, gy, size * 0.022f, dot);

            float sx = size * 0.15f;
            slashGap.setColor(Color.rgb(242, 244, 251));
            canvas.drawLine(cx - sx, cy - sx + bob, cx + sx, cy + sx + bob, slashGap);
            canvas.drawLine(cx - sx, cy - sx + bob, cx + sx, cy + sx + bob, slash);
        }
    }
}
