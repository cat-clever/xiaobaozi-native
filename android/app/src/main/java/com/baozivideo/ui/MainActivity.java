package com.baozivideo.ui;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import com.baozivideo.utils.TvHelper;

public class MainActivity extends AppCompatActivity {
    private static final int ID_CONTAINER = 10001;
    private static final int TAB_HOME = 0;
    private static final int TAB_CATEGORY = 1;
    private static final int TAB_SEARCH = 2;

    private int activeTab = TAB_HOME;
    private LinearLayout navBar;
    private final LinearLayout[] tabViews = new LinearLayout[3];
    private final ImageView[] tabIcons = new ImageView[3];
    private final TextView[] tabLabels = new TextView[3];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        float dp = getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(UiTheme.bg(this));

        FrameLayout container = new FrameLayout(this);
        container.setId(ID_CONTAINER);
        UiTheme.applySystemBarInsets(container, false);
        root.addView(container, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        navBar = new LinearLayout(this);
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        navBar.setBackgroundColor(UiTheme.barBg(this));
        navBar.setClipChildren(false);
        navBar.setClipToPadding(false);
        final int basePadH = (int) (8 * dp);
        final int basePadTop = (int) (6 * dp);
        final int basePadBot = (int) (6 * dp);
        navBar.setPadding(basePadH, basePadTop, basePadH, basePadBot);
        ViewCompat.setOnApplyWindowInsetsListener(navBar, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(basePadH + bars.left, basePadTop, basePadH + bars.right, basePadBot + bars.bottom);
            return insets;
        });

        int[] icons = {
                android.R.drawable.ic_menu_compass,
                android.R.drawable.ic_menu_sort_by_size,
                android.R.drawable.ic_menu_search
        };
        String[] titles = { "首页", "分类", "搜索" };

        for (int i = 0; i < 3; i++) {
            final int idx = i;
            LinearLayout tab = new LinearLayout(this);
            tab.setId(View.generateViewId());
            tab.setOrientation(LinearLayout.VERTICAL);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding((int) (6 * dp), (int) (6 * dp), (int) (6 * dp), (int) (6 * dp));
            tab.setClickable(true);
            TvHelper.applyFocusRing(tab, 12f);

            ImageView iv = new ImageView(this);
            iv.setImageResource(icons[i]);
            LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams((int) (22 * dp), (int) (22 * dp));
            tab.addView(iv, ivLp);

            TextView tv = new TextView(this);
            tv.setText(titles[i]);
            tv.setTextSize(11);
            tv.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tvLp.topMargin = (int) (2 * dp);
            tab.addView(tv, tvLp);

            tab.setOnClickListener(v -> selectTab(idx));

            tabViews[i] = tab;
            tabIcons[i] = iv;
            tabLabels[i] = tv;
            navBar.addView(tab, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }

        // 显式绑定底部栏左右焦点链，确保任意时候都能左右顺畅切换
        tabViews[0].setNextFocusLeftId(tabViews[0].getId());
        tabViews[0].setNextFocusRightId(tabViews[1].getId());
        tabViews[1].setNextFocusLeftId(tabViews[0].getId());
        tabViews[1].setNextFocusRightId(tabViews[2].getId());
        tabViews[2].setNextFocusLeftId(tabViews[1].getId());
        tabViews[2].setNextFocusRightId(tabViews[2].getId());

        root.addView(navBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);
        ViewCompat.requestApplyInsets(navBar);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // 若当前焦点在内容区，按返回键先将焦点移回当前导航按钮，方便左右切 Tab
                if (navBar != null && !navBar.hasFocus() && tabViews[activeTab] != null) {
                    tabViews[activeTab].requestFocus();
                    return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        });

        updateTabStyles();
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction().replace(ID_CONTAINER, new HomeFragment()).commit();
        }
    }

    private void selectTab(int idx) {
        if (activeTab == idx) return;
        activeTab = idx;
        updateTabStyles();
        Fragment f;
        if (idx == TAB_CATEGORY) f = new CategoryFragment();
        else if (idx == TAB_SEARCH) f = new SearchFragment();
        else f = new HomeFragment();
        getSupportFragmentManager().beginTransaction().replace(ID_CONTAINER, f).commit();
    }

    private void updateTabStyles() {
        int acc = UiTheme.accent(this);
        int dim = UiTheme.textDim(this);
        for (int i = 0; i < 3; i++) {
            boolean checked = (i == activeTab);
            int color = checked ? acc : dim;
            if (tabIcons[i] != null) tabIcons[i].setColorFilter(color);
            if (tabLabels[i] != null) {
                tabLabels[i].setTextColor(color);
                tabLabels[i].setTypeface(null, checked ? Typeface.BOLD : Typeface.NORMAL);
            }
        }
    }
}
