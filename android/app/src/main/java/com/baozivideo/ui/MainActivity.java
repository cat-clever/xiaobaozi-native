package com.baozivideo.ui;

import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {
    private static final int ID_CONTAINER = 10001;
    private static final int ID_TAB_HOME = 1;
    private static final int ID_TAB_CATEGORY = 2;
    private static final int ID_TAB_SEARCH = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        FrameLayout container = new FrameLayout(this);
        container.setId(ID_CONTAINER);
        root.addView(container, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        BottomNavigationView nav = new BottomNavigationView(this);
        Menu menu = nav.getMenu();
        menu.add(Menu.NONE, ID_TAB_HOME, 0, "首页").setIcon(android.R.drawable.ic_menu_compass);
        menu.add(Menu.NONE, ID_TAB_CATEGORY, 1, "分类").setIcon(android.R.drawable.ic_menu_sort_by_size);
        menu.add(Menu.NONE, ID_TAB_SEARCH, 2, "搜索").setIcon(android.R.drawable.ic_menu_search);

        nav.setOnItemSelectedListener(item -> {
            Fragment f;
            if (item.getItemId() == ID_TAB_CATEGORY) f = new CategoryFragment();
            else if (item.getItemId() == ID_TAB_SEARCH) f = new SearchFragment();
            else f = new HomeFragment();
            getSupportFragmentManager().beginTransaction().replace(ID_CONTAINER, f).commit();
            return true;
        });

        root.addView(nav, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction().replace(ID_CONTAINER, new HomeFragment()).commit();
        }
    }
}
