package com.humanappeal.mediaplan;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * The opening screen's background, the same as the web's "Live pulse": plain HEMERA blue lit from
 * above with darker corners, and a green heartbeat line across the middle that fades away behind
 * the logo (the logo, Live and the sync line are separate views on top).
 */
public class SeknaPulseSplash extends View {
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint vignette = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float density;
    private float pathLen = 1f, phase = 0f;
    private ValueAnimator anim;
    /** Half the width of the gap left for the logo, in pixels. */
    private final float gap;

    public SeknaPulseSplash(Context c, float logoHalfWidthPx) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        gap = logoHalfWidthPx + 6 * density;
        for (Paint p : new Paint[]{dim, line}) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2.2f * density);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeCap(Paint.Cap.ROUND);
        }
        line.setShadowLayer(5 * density, 0, 0, Color.argb(150, 57, 226, 154));
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (w == 0 || h == 0) return;
        // Light from above: an ellipse of light centred just above the top edge.
        RadialGradient top = new RadialGradient(0, 0, 1f,
                new int[]{Color.rgb(42, 79, 184), Color.rgb(20, 49, 127), Color.rgb(10, 30, 90), Color.rgb(6, 18, 60)},
                new float[]{0f, .32f, .62f, 1f}, Shader.TileMode.CLAMP);
        Matrix m = new Matrix();
        m.setScale(w * 1.2f, h * 0.7f);
        m.postTranslate(w / 2f, -0.08f * h);
        top.setLocalMatrix(m);
        bg.setShader(top);
        RadialGradient v = new RadialGradient(0, 0, 1f, new int[]{Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(115, 0, 0, 0)},
                new float[]{0f, .55f, 1f}, Shader.TileMode.CLAMP);
        Matrix vm = new Matrix();
        vm.setScale(w * 1.3f * 0.75f, h * 0.9f * 0.75f);
        vm.postTranslate(w / 2f, h / 2f);
        v.setLocalMatrix(vm);
        vignette.setShader(v);

        // The heartbeat: flat, with one beat on each side of the logo (two more on wide screens).
        float cy = h / 2f, cx = w / 2f, d = density, o = Math.min(186 * d, 118 * d + (cx - 118 * d) * .42f);
        path.reset();
        path.moveTo(0, cy);
        if (w > 700 * d) beat(cx - o - 260 * d, cy);
        beat(cx - o, cy);
        beat(cx + o, cy);
        if (w > 700 * d) beat(cx + o + 230 * d, cy);
        path.lineTo(w, cy);
        android.graphics.PathMeasure pm = new android.graphics.PathMeasure(path, false);
        pathLen = Math.max(1f, pm.getLength());
        // Fade the line where the logo sits.
        float a = (cx - gap - 16 * d) / w, b = (cx - gap) / w, c2 = (cx + gap) / w, e = (cx + gap + 16 * d) / w;
        float[] pos = {0f, Math.max(0f, a), Math.max(0f, b), Math.min(1f, c2), Math.min(1f, e), 1f};
        dim.setShader(new LinearGradient(0, 0, w, 0, fade(Color.argb(41, 57, 226, 154)), pos, Shader.TileMode.CLAMP));
        line.setShader(new LinearGradient(0, 0, w, 0, fade(Color.rgb(57, 226, 154)), pos, Shader.TileMode.CLAMP));
        start();
    }

    private static int[] fade(int c) { int t = c & 0x00FFFFFF; return new int[]{c, c, t, t, c, c}; }

    private void beat(float x, float cy) {
        float d = density, k = 22 * d / 42f; // the web's 84px-tall line, at this screen's size
        path.lineTo(x - 14 * d, cy);
        path.lineTo(x - 6 * d, cy - 22 * k);
        path.lineTo(x + 2 * d, cy + 28 * k);
        path.lineTo(x + 10 * d, cy - 36 * k);
        path.lineTo(x + 18 * d, cy);
    }

    private void start() {
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(2000);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(x -> { phase = (float) x.getAnimatedValue(); invalidate(); });
        anim.start();
    }

    @Override protected void onDetachedFromWindow() { if (anim != null) anim.cancel(); super.onDetachedFromWindow(); }

    @Override protected void onVisibilityChanged(View changed, int vis) {
        super.onVisibilityChanged(changed, vis);
        if (vis != VISIBLE && anim != null) anim.cancel();
    }

    @Override protected void onDraw(Canvas c) {
        c.drawRect(0, 0, getWidth(), getHeight(), bg);
        c.drawRect(0, 0, getWidth(), getHeight(), vignette);
        c.drawPath(path, dim);
        // A bright stretch 38% of the line long runs left to right, like the web's dash.
        float on = pathLen * .38f, off = pathLen * .62f;
        line.setPathEffect(new DashPathEffect(new float[]{on, off}, pathLen * (1f - phase)));
        c.drawPath(path, line);
    }
}
