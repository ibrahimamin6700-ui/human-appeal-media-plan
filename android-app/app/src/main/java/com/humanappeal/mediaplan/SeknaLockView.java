package com.humanappeal.mediaplan;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Full-screen cover shown while SEKNA is locked with the phone's fingerprint, face or PIN. */
public class SeknaLockView extends FrameLayout {
    public SeknaLockView(Context context, Runnable onUnlock) {
        super(context);
        setBackground(new HemeraBg());
        setClickable(true);
        setFocusable(true);

        float density = getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = Math.round(28 * density);
        box.setPadding(pad, pad, pad, pad);

        ImageView logo = new ImageView(context);
        logo.setImageResource(R.drawable.sekna_logo_white);
        logo.setAdjustViewBounds(true);
        box.addView(logo, new LinearLayout.LayoutParams(Math.round(150 * density), LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(context);
        title.setText("HEMERA is locked");
        title.setTextColor(Color.WHITE);
        title.setTextSize(19);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = Math.round(28 * density);
        box.addView(title, titleParams);

        TextView sub = new TextView(context);
        sub.setText("Unlock with your fingerprint, face or phone PIN.");
        sub.setTextColor(Color.rgb(201, 211, 245));
        sub.setTextSize(13);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subParams.topMargin = Math.round(8 * density);
        box.addView(sub, subParams);

        TextView unlock = new TextView(context);
        unlock.setText("Unlock");
        unlock.setTextColor(Color.rgb(15, 31, 82));
        unlock.setTextSize(15);
        unlock.setTypeface(Typeface.DEFAULT_BOLD);
        unlock.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(226, 184, 116), Color.rgb(243, 215, 159)});
        bg.setCornerRadius(14 * density);
        unlock.setBackground(bg);
        unlock.setOnClickListener(v -> onUnlock.run());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                Math.round(220 * density), Math.round(50 * density));
        buttonParams.topMargin = Math.round(26 * density);
        box.addView(unlock, buttonParams);

        addView(box, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));
    }
}
