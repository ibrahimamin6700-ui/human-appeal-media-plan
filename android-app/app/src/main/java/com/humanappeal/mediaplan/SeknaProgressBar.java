package com.humanappeal.mediaplan;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

/**
 * Glossy 3D loading bar in the SEKNA navy. The fill grows left to right with the
 * real page progress while a light sweep keeps moving left to right across it.
 */
public class SeknaProgressBar extends View {
    static final int NAVY = Color.rgb(32, 60, 142);
    private static final int NAVY_LIGHT = Color.rgb(74, 104, 196);
    private static final int NAVY_DARK = Color.rgb(20, 38, 96);

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF fillRect = new RectF();
    private final Path fillClip = new Path();

    private float shownProgress = 0f;
    private float shinePhase = 0f;
    private ValueAnimator progressAnimator;
    private ValueAnimator shineAnimator;

    public SeknaProgressBar(Context context) {
        super(context);
        trackStroke.setStyle(Paint.Style.STROKE);
        trackStroke.setColor(Color.argb(70, 32, 60, 142));
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), view.getHeight() / 2f);
            }
        });
    }

    /** Animates the fill towards the given value (0-100). */
    public void setProgress(int progress) {
        float target = Math.max(0f, Math.min(100f, progress)) / 100f;
        if (target <= shownProgress) return;
        if (progressAnimator != null) progressAnimator.cancel();
        progressAnimator = ValueAnimator.ofFloat(shownProgress, target);
        progressAnimator.setDuration(450);
        progressAnimator.setInterpolator(new DecelerateInterpolator());
        progressAnimator.addUpdateListener(a -> {
            shownProgress = (float) a.getAnimatedValue();
            invalidate();
        });
        progressAnimator.start();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startShine();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnimations();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE && isAttachedToWindow()) startShine();
        else if (visibility == GONE) stopAnimations();
    }

    private void startShine() {
        if (shineAnimator != null && shineAnimator.isRunning()) return;
        shineAnimator = ValueAnimator.ofFloat(0f, 1f);
        shineAnimator.setDuration(1300);
        shineAnimator.setRepeatCount(ValueAnimator.INFINITE);
        shineAnimator.setInterpolator(new LinearInterpolator());
        shineAnimator.addUpdateListener(a -> {
            shinePhase = (float) a.getAnimatedValue();
            invalidate();
        });
        shineAnimator.start();
    }

    private void stopAnimations() {
        if (shineAnimator != null) shineAnimator.cancel();
        if (progressAnimator != null) progressAnimator.cancel();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float stroke = Math.max(1f, h / 14f);
        trackStroke.setStrokeWidth(stroke);
        // Inset track: darker at the top, lighter at the bottom.
        trackPaint.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{Color.rgb(214, 220, 238), Color.rgb(236, 239, 248), Color.WHITE},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        // Raised fill: light top edge, brand navy body, deep bottom edge.
        fillPaint.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{NAVY_LIGHT, NAVY, NAVY_DARK},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        glossPaint.setShader(new LinearGradient(0, 0, 0, h * 0.55f,
                Color.argb(150, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;
        float radius = h / 2f;

        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, radius, radius, trackPaint);

        float inset = Math.max(1.5f, h / 9f);
        float fillWidth = (w - inset * 2) * shownProgress;
        if (fillWidth > 0.5f) {
            float fillHeight = h - inset * 2;
            float r = fillHeight / 2f;
            fillRect.set(inset, inset, inset + Math.max(fillWidth, fillHeight), h - inset);
            canvas.drawRoundRect(fillRect, r, r, fillPaint);

            int save = canvas.save();
            fillClip.reset();
            fillClip.addRoundRect(fillRect, r, r, Path.Direction.CW);
            canvas.clipPath(fillClip);

            // Moving light sweep, left to right.
            float band = Math.max(h * 3f, w * 0.22f);
            float x = fillRect.left - band + (fillRect.width() + band * 2) * shinePhase;
            shinePaint.setShader(new LinearGradient(x, 0, x + band, 0,
                    new int[]{Color.argb(0, 255, 255, 255), Color.argb(120, 255, 255, 255), Color.argb(0, 255, 255, 255)},
                    null, Shader.TileMode.CLAMP));
            canvas.drawRect(fillRect, shinePaint);

            // Top gloss for the 3D look.
            canvas.drawRoundRect(fillRect.left + r * 0.5f, fillRect.top + 1f,
                    fillRect.right - r * 0.5f, fillRect.top + fillHeight * 0.5f, r, r, glossPaint);
            canvas.restoreToCount(save);
        }

        float half = trackStroke.getStrokeWidth() / 2f;
        rect.set(half, half, w - half, h - half);
        canvas.drawRoundRect(rect, radius - half, radius - half, trackStroke);
    }
}
