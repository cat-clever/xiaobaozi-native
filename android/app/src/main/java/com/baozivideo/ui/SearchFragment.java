package com.baozivideo.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.baozivideo.api.Aggregator;
import com.baozivideo.api.source1.Source1Service;
import com.baozivideo.utils.StorageManager;
import com.baozivideo.utils.TvHelper;
import java.util.List;

public class SearchFragment extends Fragment {
    private EditText input;
    private LinearLayout suggestionsBox;
    private MediaCardAdapter adapter;
    private ProgressBar progress;
    private TextView errorTv;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        float dp = ctx.getResources().getDisplayMetrics().density;
        int screenW = ctx.getResources().getDisplayMetrics().widthPixels;
        int cols = (TvHelper.isTv(ctx) || screenW > 1400) ? 6 : 3;
        int cardW = (screenW - (int) (24 * dp) - (int) (12 * dp * cols)) / cols;

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int) (12 * dp), (int) (12 * dp), (int) (12 * dp), 0);

        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        input = new EditText(ctx);
        input.setHint("搜剧名、演员…");
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setOnEditorActionListener((v, actionId, event) -> {
            runSearch(input.getText().toString());
            return true;
        });
        bar.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button btn = new Button(ctx);
        btn.setText("搜索");
        btn.setOnClickListener(v -> runSearch(input.getText().toString()));
        bar.addView(btn);
        root.addView(bar);

        errorTv = new TextView(ctx);
        errorTv.setTextColor(0xFFFF6B6B);
        errorTv.setVisibility(View.GONE);
        root.addView(errorTv);

        progress = new ProgressBar(ctx);
        progress.setVisibility(View.GONE);
        root.addView(progress);

        suggestionsBox = new LinearLayout(ctx);
        suggestionsBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(suggestionsBox);

        RecyclerView rv = new RecyclerView(ctx);
        rv.setLayoutManager(new GridLayoutManager(ctx, cols));
        adapter = new MediaCardAdapter(cardW, item -> {
            Intent it = new Intent(ctx, PlayerActivity.class);
            it.putExtra("item", item);
            startActivity(it);
        });
        rv.setAdapter(adapter);
        root.addView(rv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        loadSuggestions();
        return root;
    }

    private void loadSuggestions() {
        List<String> history = StorageManager.getInstance().getSearchHistory();
        new Thread(() -> {
            List<String> hot = Source1Service.hotSearch();
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> renderSuggestions(history, hot));
            }
        }).start();
    }

    private void renderSuggestions(List<String> history, List<String> hot) {
        if (!isAdded()) return;
        suggestionsBox.removeAllViews();
        if (!history.isEmpty()) addChipSection("最近搜索", history);
        if (!hot.isEmpty()) addChipSection("热搜", hot.subList(0, Math.min(20, hot.size())));
    }

    private void addChipSection(String title, List<String> list) {
        Context ctx = requireContext();
        float dp = ctx.getResources().getDisplayMetrics().density;
        TextView tv = new TextView(ctx);
        tv.setText(title);
        tv.setTextSize(14);
        tv.setPadding(0, (int) (8 * dp), 0, (int) (4 * dp));
        suggestionsBox.addView(tv);

        HorizontalScrollView hsv = new HorizontalScrollView(ctx);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(ctx);
        for (String kw : list) {
            TextView chip = new TextView(ctx);
            chip.setText(kw);
            chip.setTextSize(12);
            chip.setPadding((int) (10 * dp), (int) (5 * dp), (int) (10 * dp), (int) (5 * dp));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(14 * dp);
            bg.setColor(0x22888888);
            chip.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = (int) (6 * dp);
            chip.setOnClickListener(v -> {
                input.setText(kw);
                runSearch(kw);
            });
            TvHelper.applyChipFocus(chip);
            row.addView(chip, lp);
        }
        hsv.addView(row);
        suggestionsBox.addView(hsv);
    }

    private void runSearch(String raw) {
        String kw = raw == null ? "" : raw.trim();
        if (kw.isEmpty()) return;
        suggestionsBox.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);
        errorTv.setVisibility(View.GONE);
        StorageManager.getInstance().pushSearchHistory(kw);

        new Thread(() -> {
            Aggregator.SearchResult res = Aggregator.searchAll(kw);
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    adapter.setItems(res.items);
                    if (!res.errors.isEmpty()) {
                        errorTv.setText(String.join("\n", res.errors));
                        errorTv.setVisibility(View.VISIBLE);
                    }
                });
            }
        }).start();
    }
}
