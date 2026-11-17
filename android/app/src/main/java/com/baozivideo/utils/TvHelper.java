package com.baozivideo.utils;

import android.app.UiModeManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;

public final class TvHelper {
    public static final int GOLD = 0xFFFFB454;

    private TvHelper() {}

    public static boolean isTv(Context context) {
        if (context == null) return false;
        UiModeManager umm = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
        if (umm != null && umm.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
            return true;
        }
        PackageManager pm = context.getPackageManager();
        return pm != null && pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }

    /** 设置或清除视图前景上的金色焦点边框 */
    public static void setGoldBorder(View target, boolean hasFocus, float cornerRadiusDp, float strokeDp, int fillColor) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || target == null) return;
        if (hasFocus) {
            float dp = target.getResources().getDisplayMetrics().density;
            GradientDrawable ring = new GradientDrawable();
            ring.setColor(fillColor);
            ring.setCornerRadius(cornerRadiusDp * dp);
            ring.setStroke(Math.max(1, (int) (strokeDp * dp)), GOLD);
            target.setForeground(ring);
        } else {
            target.setForeground(null);
        }
    }

    public static void applyCardFocus(View cardView, View borderTarget) {
        cardView.setFocusable(true);
        cardView.setOnFocusChangeListener((v, hasFocus) -> {
            v.animate()
                .scaleX(hasFocus ? 1.06f : 1.0f)
                .scaleY(hasFocus ? 1.06f : 1.0f)
                .setDuration(120)
                .start();
            setGoldBorder(borderTarget, hasFocus, 12f, 3f, Color.TRANSPARENT);
        });
    }

    public static void applyChipFocus(View chip) {
        chip.setFocusable(true);
        chip.setClickable(true);
        chip.setOnFocusChangeListener((v, hasFocus) -> {
            v.animate()
                .scaleX(hasFocus ? 1.06f : 1.0f)
                .scaleY(hasFocus ? 1.06f : 1.0f)
                .setDuration(100)
                .start();
            setGoldBorder(v, hasFocus, 16f, 2.5f, 0x18FFB454);
        });
    }

    public static void applyListRowFocus(View row) {
        row.setFocusable(true);
        row.setClickable(true);
        row.setOnFocusChangeListener((v, hasFocus) -> {
            v.animate()
                .scaleX(hasFocus ? 1.02f : 1.0f)
                .scaleY(hasFocus ? 1.02f : 1.0f)
                .setDuration(100)
                .start();
            setGoldBorder(v, hasFocus, 12f, 2.5f, 0x1AFFB454);
        });
    }

    public static void applySeekBarFocus(View seekBar) {
        seekBar.setFocusable(true);
        seekBar.setOnFocusChangeListener((v, hasFocus) ->
                setGoldBorder(v, hasFocus, 8f, 2.5f, 0x22FFB454));
    }

    public static void applyFocusRing(View view, float cornerRadiusDp) {
        view.setFocusable(true);
        view.setOnFocusChangeListener((v, hasFocus) ->
                setGoldBorder(v, hasFocus, cornerRadiusDp, 2.5f, 0x18FFB454));
    }
}
