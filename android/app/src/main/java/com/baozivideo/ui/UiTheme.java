package com.baozivideo.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.baozivideo.utils.StorageManager;
import com.baozivideo.utils.TvHelper;

public final class UiTheme {
    private UiTheme() {}

    public static boolean isDark(Context ctx) {
        int mode = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    public static int bg(Context ctx) {
        return isDark(ctx) ? 0xFF0B0B0F : 0xFFF5F5F8;
    }

    public static int barBg(Context ctx) {
        boolean glass = StorageManager.getInstance() != null && StorageManager.getInstance().getGlassEnabled();
        if (isDark(ctx)) {
            return glass ? 0xE614141B : 0xFF14141B;
        } else {
            return glass ? 0xE6FFFFFF : 0xFFFFFFFF;
        }
    }

    public static int cardBg(Context ctx) {
        boolean glass = StorageManager.getInstance() != null && StorageManager.getInstance().getGlassEnabled();
        if (isDark(ctx)) {
            return glass ? 0x1AFFFFFF : 0xFF181820;
        } else {
            return glass ? 0xCCFFFFFF : 0xFFFFFFFF;
        }
    }

    public static int cardBorder(Context ctx) {
        return isDark(ctx) ? 0x22FFFFFF : 0x16000000;
    }

    public static int text(Context ctx) {
        return isDark(ctx) ? 0xFFF2F2F7 : 0xFF141418;
    }

    public static int textDim(Context ctx) {
        return isDark(ctx) ? 0xFF9E9EA8 : 0xFF666672;
    }

    public static int accent(Context ctx) {
        return isDark(ctx) ? 0xFFFFB454 : 0xFFD97516;
    }

    public static int chipInactiveBg(Context ctx) {
        return isDark(ctx) ? 0xFF20202A : 0xFFE8E8EE;
    }

    public static void applySystemBarInsets(View view, boolean applyBottom) {
        final int origLeft = view.getPaddingLeft();
        final int origTop = view.getPaddingTop();
        final int origRight = view.getPaddingRight();
        final int origBottom = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                origLeft + bars.left,
                origTop + bars.top,
                origRight + bars.right,
                applyBottom ? origBottom + bars.bottom : origBottom
            );
            return insets;
        });
        ViewCompat.requestApplyInsets(view);
    }

    public static TextView makePillButton(Context ctx, String label, boolean primary) {
        float dp = ctx.getResources().getDisplayMetrics().density;
        TextView tv = new TextView(ctx);
        tv.setText(label);
        tv.setTextSize(13);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding((int) (14 * dp), (int) (8 * dp), (int) (14 * dp), (int) (8 * dp));
        stylePill(tv, primary);
        TvHelper.applyChipFocus(tv);
        return tv;
    }

    public static void stylePill(TextView tv, boolean active) {
        Context ctx = tv.getContext();
        float dp = ctx.getResources().getDisplayMetrics().density;
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(18 * dp);
        if (active) {
            bg.setColor(accent(ctx));
            tv.setTextColor(0xFF241503);
        } else {
            bg.setColor(chipInactiveBg(ctx));
            tv.setTextColor(text(ctx));
        }
        tv.setBackground(bg);
    }

    public static TextView makeOverlayButton(Context ctx, String label) {
        float dp = ctx.getResources().getDisplayMetrics().density;
        TextView tv = new TextView(ctx);
        tv.setText(label);
        tv.setTextSize(13);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(Color.WHITE);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding((int) (12 * dp), (int) (6 * dp), (int) (12 * dp), (int) (6 * dp));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(16 * dp);
        bg.setColor(0x88000000);
        bg.setStroke((int) (1 * dp), 0x33FFFFFF);
        tv.setBackground(bg);
        TvHelper.applyChipFocus(tv);
        return tv;
    }

    public static LinearLayout makeCard(Context ctx) {
        float dp = ctx.getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding((int) (14 * dp), (int) (12 * dp), (int) (14 * dp), (int) (12 * dp));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(16 * dp);
        bg.setColor(cardBg(ctx));
        bg.setStroke((int) (1 * dp), cardBorder(ctx));
        box.setBackground(bg);
        return box;
    }
}
