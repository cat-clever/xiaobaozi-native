package com.baozivideo.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.baozivideo.api.Aggregator;
import com.baozivideo.model.Models;
import com.baozivideo.utils.TvHelper;
import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {
    private SwipeRefreshLayout swipe;
    private LinearLayout container;
    private ProgressBar loading;
    private final List<Models.HomeBlock> s1Blocks = new ArrayList<>();
    private final List<Models.HomeBlock> s2Blocks = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        float dp = ctx.getResources().getDisplayMetrics().density;

        swipe = new SwipeRefreshLayout(ctx);
        ScrollView scroll = new ScrollView(ctx);
        container = new LinearLayout(ctx);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding((int) (12 * dp), (int) (12 * dp), (int) (12 * dp), (int) (24 * dp));

        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(ctx);
        title.setText("小包子影视");
        title.setTextSize(24);
        title.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        header.addView(title, tLp);

        Button settingsBtn = new Button(ctx);
        settingsBtn.setText("设置");
        settingsBtn.setOnClickListener(v -> startActivity(new Intent(ctx, SettingsActivity.class)));
        header.addView(settingsBtn);
        container.addView(header);

        loading = new ProgressBar(ctx);
        container.addView(loading);

        scroll.addView(container);
        swipe.addView(scroll);
        swipe.setOnRefreshListener(() -> load(true));

        load(false);
        return swipe;
    }

    private void load(boolean force) {
        if (!force) {
            List<Models.HomeBlock> c1 = Aggregator.peekHomeBlocks(Models.SourceId.s1, 1);
            List<Models.HomeBlock> c2 = Aggregator.peekHomeBlocks(Models.SourceId.s2, 1);
            if (c1 != null && c2 != null) {
                s1Blocks.clear(); s1Blocks.addAll(c1);
                s2Blocks.clear(); s2Blocks.addAll(c2);
                renderBlocks();
                return;
            }
        } else {
            Aggregator.clearHomeCache();
        }

        loading.setVisibility(View.VISIBLE);
        errors.clear();

        new Thread(() -> {
            try {
                List<Models.HomeBlock> b1 = Aggregator.homeBlocksOf(Models.SourceId.s1, 1, force);
                synchronized (s1Blocks) { s1Blocks.clear(); s1Blocks.addAll(b1); }
            } catch (Exception e) {
                synchronized (errors) { errors.add("全网源：" + e.getMessage()); }
            }
            if (isAdded()) requireActivity().runOnUiThread(this::renderBlocks);
        }).start();

        new Thread(() -> {
            try {
                List<Models.HomeBlock> b2 = Aggregator.homeBlocksOf(Models.SourceId.s2, 1, force);
                synchronized (s2Blocks) { s2Blocks.clear(); s2Blocks.addAll(b2); }
            } catch (Exception e) {
                synchronized (errors) { errors.add("精选源：" + e.getMessage()); }
            }
            if (isAdded()) requireActivity().runOnUiThread(this::renderBlocks);
        }).start();
    }

    private void renderBlocks() {
        if (!isAdded()) return;
        Context ctx = requireContext();
        float dp = ctx.getResources().getDisplayMetrics().density;
        int screenW = ctx.getResources().getDisplayMetrics().widthPixels;
        int cols = (TvHelper.isTv(ctx) || screenW > 1400) ? 6 : 3;
        int cardW = (screenW - (int) (36 * dp) - (int) (12 * dp * cols)) / cols;

        while (container.getChildCount() > 2) {
            container.removeViewAt(2);
        }
        loading.setVisibility(View.GONE);
        swipe.setRefreshing(false);

        synchronized (errors) {
            for (String err : errors) {
                TextView tv = new TextView(ctx);
                tv.setText(err);
                tv.setTextColor(0xFFFF6B6B);
                tv.setTextSize(12);
                container.addView(tv);
            }
        }

        List<Models.HomeBlock> all = new ArrayList<>();
        synchronized (s1Blocks) { all.addAll(s1Blocks); }
        synchronized (s2Blocks) { all.addAll(s2Blocks); }

        for (Models.HomeBlock blk : all) {
            TextView secTitle = new TextView(ctx);
            secTitle.setText(blk.title);
            secTitle.setTextSize(17);
            secTitle.setTypeface(null, Typeface.BOLD);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = (int) (14 * dp);
            lp.bottomMargin = (int) (6 * dp);
            container.addView(secTitle, lp);

            RecyclerView rv = new RecyclerView(ctx);
            rv.setClipChildren(false);
            rv.setClipToPadding(false);
            rv.setLayoutManager(new LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false));
            MediaCardAdapter adapter = new MediaCardAdapter(cardW, item -> {
                Intent it = new Intent(ctx, PlayerActivity.class);
                it.putExtra("item", item);
                startActivity(it);
            });
            adapter.setItems(blk.items);
            rv.setAdapter(adapter);
            container.addView(rv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }
}
