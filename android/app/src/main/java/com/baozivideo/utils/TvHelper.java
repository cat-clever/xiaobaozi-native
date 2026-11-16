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

    public static void applyCardFocus(View cardView, View borderTarget) {
        cardView.setFocusable(true);
        cardView.setOnFocusChangeListener((v, hasFocus) -> {
            v.animate()
                .scaleX(hasFocus ? 1.06f : 1.0f)
                .scaleY(hasFocus ? 1.06f : 1.0f)
                .setDuration(120)
                .start();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && borderTarget != null) {
                if (hasFocus) {
                    float dp = v.getResources().getDisplayMetrics().density;
                    GradientDrawable ring = new GradientDrawable();
                    ring.setColor(Color.TRANSPARENT);
                    ring.setCornerRadius(12 * dp);
                    ring.setStroke((int) (3 * dp), 0xFFFFB454);
                    borderTarget.setForeground(ring);
                } else {
                    borderTarget.setForeground(null);
                }
            }
        });
    }

    public static void applyChipFocus(View chip) {
        chip.setFocusable(true);
        chip.setOnFocusChangeListener((v, hasFocus) -> {
            v.animate()
                .scaleX(hasFocus ? 1.08f : 1.0f)
                .scaleY(hasFocus ? 1.08f : 1.0f)
                .setDuration(100)
                .start();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (hasFocus) {
                    float dp = v.getResources().getDisplayMetrics().density;
                    GradientDrawable ring = new GradientDrawable();
                    ring.setColor(Color.TRANSPARENT);
                    ring.setCornerRadius(14 * dp);
                    ring.setStroke((int) (2 * dp), 0xFFFFFFFF);
                    v.setForeground(ring);
                } else {
                    v.setForeground(null);
                }
            }
        });
    }

    public static void applyListRowFocus(View row) {
        row.setFocusable(true);
        row.setOnFocusChangeListener((v, hasFocus) -> {
            v.animate()
                .scaleX(hasFocus ? 1.02f : 1.0f)
                .scaleY(hasFocus ? 1.02f : 1.0f)
                .setDuration(100)
                .start();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (hasFocus) {
                    float dp = v.getResources().getDisplayMetrics().density;
                    GradientDrawable ring = new GradientDrawable();
                    ring.setColor(0x1AFFB454);
                    ring.setCornerRadius(12 * dp);
                    ring.setStroke((int) (2.5f * dp), 0xFFFFB454);
                    v.setForeground(ring);
                } else {
                    v.setForeground(null);
                }
            }
        });
    }

    public static void applySeekBarFocus(View seekBar) {
        seekBar.setFocusable(true);
        seekBar.setOnFocusChangeListener((v, hasFocus) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (hasFocus) {
                    float dp = v.getResources().getDisplayMetrics().density;
                    GradientDrawable ring = new GradientDrawable();
                    ring.setColor(0x22FFB454);
                    ring.setCornerRadius(8 * dp);
                    ring.setStroke((int) (2 * dp), 0xFFFFB454);
                    v.setForeground(ring);
                } else {
                    v.setForeground(null);
                }
            }
        });
    }
}
