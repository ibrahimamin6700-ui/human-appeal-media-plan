package com.humanappeal.mediaplan;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * The web's page background: the loading screen's blue (lit from the top, deep navy below)
 * with a soft blue light in the top-left corner and a gold light at the bottom right.
 */
public class HemeraBg extends Drawable {
    private final Paint base = new Paint(), top = new Paint(Paint.ANTI_ALIAS_FLAG), blue = new Paint(Paint.ANTI_ALIAS_FLAG), gold = new Paint(Paint.ANTI_ALIAS_FLAG);

    @Override protected void onBoundsChange(Rect b) {
        float w = b.width(), h = b.height();
        if (w <= 0 || h <= 0) return;
        base.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{Color.rgb(11, 42, 120), Color.rgb(10, 29, 98), Color.rgb(8, 22, 80), Color.rgb(6, 15, 56)},
                new float[]{0f, .35f, .62f, 1f}, Shader.TileMode.CLAMP));
        top.setShader(ellipse(w * .5f, 0, w * .70f, h * .34f, Color.argb(204, 28, 76, 175)));
        blue.setShader(ellipse(w * .12f, h * .10f, w * .62f, h * .42f, Color.argb(191, 52, 99, 230)));
        gold.setShader(ellipse(w * .96f, h * .82f, w * .55f, h * .38f, Color.argb(77, 226, 184, 116)));
    }

    private static Shader ellipse(float cx, float cy, float rx, float ry, int color) {
        RadialGradient g = new RadialGradient(0, 0, 1f, new int[]{color, color & 0x00FFFFFF}, new float[]{0f, .75f}, Shader.TileMode.CLAMP);
        Matrix m = new Matrix();
        m.setScale(rx, ry);
        m.postTranslate(cx, cy);
        g.setLocalMatrix(m);
        return g;
    }

    @Override public void draw(Canvas c) {
        Rect b = getBounds();
        c.drawRect(b, base);
        c.drawRect(b, top);
        c.drawRect(b, blue);
        c.drawRect(b, gold);
    }

    @Override public void setAlpha(int a) {}
    @Override public void setColorFilter(ColorFilter f) {}
    @Override public int getOpacity() { return PixelFormat.OPAQUE; }
}
