package com.baozivideo.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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
import com.baozivideo.api.source2.Source2Client;
import com.baozivideo.api.source2.Source2Service;
import com.baozivideo.model.Models;
import com.baozivideo.utils.TvHelper;
import java.util.List;

public class CategoryFragment extends Fragment {
    private Models.SourceId source = Models.SourceId.s1;
    private Source1Service.CategoryTree tree1;
    private Source2Client.CateResult tree2;

    private int s1TypeId = 0;
    private String s1Genre = "", s1Tag = "", s1Area = "", s1Year = "", s1Sort = "";
    private Integer s2Stype = null, s2Cid = null;
    private String s2Year = "", s2Sort = "";

    private LinearLayout filtersBox;
    private MediaCardAdapter adapter;
    private ProgressBar progress;
    private TextView errorTv;
    private int page = 1;
    private boolean loading = false;
    private boolean reachedEnd = false;

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
        root.setBackgroundColor(UiTheme.bg(ctx));
        root.setPadding((int) (14 * dp), (int) (10 * dp), (int) (14 * dp), 0);

        LinearLayout srcRow = new LinearLayout(ctx);
        srcRow.setPadding(0, 0, 0, (int) (8 * dp));
        TextView b1 = UiTheme.makePillButton(ctx, "全网源", true);
        TextView b2 = UiTheme.makePillButton(ctx, "精选源", false);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        bLp.rightMargin = (int) (8 * dp);
        LinearLayout.LayoutParams b2Lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        b1.setOnClickListener(v -> {
            source = Models.SourceId.s1;
            UiTheme.stylePill(b1, true);
            UiTheme.stylePill(b2, false);
            renderFilters();
            fetchPage(1);
        });
        b2.setOnClickListener(v -> {
            source = Models.SourceId.s2;
            UiTheme.stylePill(b1, false);
            UiTheme.stylePill(b2, true);
            renderFilters();
            fetchPage(1);
        });
        srcRow.addView(b1, bLp);
        srcRow.addView(b2, b2Lp);
        root.addView(srcRow);

        filtersBox = new LinearLayout(ctx);
        filtersBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(filtersBox);

        errorTv = new TextView(ctx);
        errorTv.setTextColor(0xFFFF6B6B);
        errorTv.setVisibility(View.GONE);
        root.addView(errorTv);

        progress = new ProgressBar(ctx);
        progress.setVisibility(View.GONE);
        root.addView(progress);

        RecyclerView rv = new RecyclerView(ctx);
        GridLayoutManager gm = new GridLayoutManager(ctx, cols);
        rv.setLayoutManager(gm);
        adapter = new MediaCardAdapter(cardW, item -> {
            Intent it = new Intent(ctx, PlayerActivity.class);
            it.putExtra("item", item);
            startActivity(it);
        });
        rv.setAdapter(adapter);
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (!loading && !reachedEnd && gm.findLastVisibleItemPosition() >= adapter.getItemCount() - 4) {
                    fetchPage(page + 1);
                }
            }
        });
        root.addView(rv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        loadTreesAndFirstPage();
        return root;
    }

    private void loadTreesAndFirstPage() {
        new Thread(() -> {
            try {
                tree1 = Source1Service.categoryTree();
                if (!tree1.categories.isEmpty()) s1TypeId = Integer.parseInt(tree1.categories.get(0).value);
            } catch (Exception ignored) {}
            try {
                tree2 = Source2Service.categoryTree();
                if (!tree2.groups.isEmpty()) s2Stype = tree2.groups.get(0).value;
            } catch (Exception ignored) {}
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    renderFilters();
                    fetchPage(1);
                });
            }
        }).start();
    }

    private void renderFilters() {
        if (!isAdded()) return;
        filtersBox.removeAllViews();
        if (source == Models.SourceId.s1 && tree1 != null) {
            addChipRow("频道", tree1.categories, String.valueOf(s1TypeId), val -> {
                s1TypeId = Integer.parseInt(val); s1Genre = ""; s1Tag = "";
                renderFilters(); fetchPage(1);
            });
            for (Models.FilterRow r : Source1Service.filterRowsFor(tree1, s1TypeId, s1Genre)) {
                String sel = "题材".equals(r.title) ? s1Genre : ("标签".equals(r.title) ? s1Tag : ("地区".equals(r.title) ? s1Area : ("年份".equals(r.title) ? s1Year : s1Sort)));
                addChipRow(r.title, r.options, sel, val -> {
                    if ("题材".equals(r.title)) { s1Genre = val; s1Tag = ""; }
                    else if ("标签".equals(r.title)) s1Tag = val;
                    else if ("地区".equals(r.title)) s1Area = val;
                    else if ("年份".equals(r.title)) s1Year = val;
                    else s1Sort = val;
                    renderFilters(); fetchPage(1);
                });
            }
        } else if (source == Models.SourceId.s2 && tree2 != null) {
            List<Models.FilterOption> grpOpts = new java.util.ArrayList<>();
            for (Source2Client.CateResult.Group g : tree2.groups) {
                grpOpts.add(new Models.FilterOption(g.label, String.valueOf(g.value)));
            }
            addChipRow("分组", grpOpts, s2Stype == null ? "" : String.valueOf(s2Stype), val -> {
                s2Stype = Integer.parseInt(val); s2Cid = null;
                renderFilters(); fetchPage(1);
            });
            for (Source2Client.CateResult.Group g : tree2.groups) {
                if (s2Stype != null && g.value == s2Stype) {
                    addChipRow("分类", g.cates, s2Cid == null ? "" : String.valueOf(s2Cid), val -> {
                        s2Cid = Integer.parseInt(val); renderFilters(); fetchPage(1);
                    });
                    break;
                }
            }
            if (!tree2.years.isEmpty()) addChipRow("年份", tree2.years, s2Year, val -> { s2Year = val; renderFilters(); fetchPage(1); });
            if (!tree2.sorts.isEmpty()) addChipRow("排序", tree2.sorts, s2Sort, val -> { s2Sort = val; renderFilters(); fetchPage(1); });
        }
    }

    private interface ChipSelect { void onSelect(String val); }

    private void addChipRow(String label, List<Models.FilterOption> opts, String selected, ChipSelect cb) {
        Context ctx = requireContext();
        float dp = ctx.getResources().getDisplayMetrics().density;
        HorizontalScrollView hsv = new HorizontalScrollView(ctx);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, (int) (4 * dp), 0, (int) (4 * dp));

        TextView title = new TextView(ctx);
        title.setText(label + " ");
        title.setTextSize(12);
        title.setTextColor(UiTheme.textDim(ctx));
        row.addView(title);

        for (Models.FilterOption opt : opts) {
            boolean active = opt.value.equals(selected);
            TextView chip = new TextView(ctx);
            chip.setText(opt.label);
            chip.setTextSize(12);
            chip.setPadding((int) (12 * dp), (int) (5 * dp), (int) (12 * dp), (int) (5 * dp));
            UiTheme.stylePill(chip, active);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = (int) (6 * dp);
            chip.setOnClickListener(v -> cb.onSelect(opt.value));
            TvHelper.applyChipFocus(chip);
            row.addView(chip, lp);
        }
        hsv.addView(row);
        filtersBox.addView(hsv);
    }

    private void fetchPage(int targetPage) {
        loading = true;
        page = targetPage;
        if (targetPage == 1) {
            reachedEnd = false;
            progress.setVisibility(View.VISIBLE);
        }
        errorTv.setVisibility(View.GONE);

        Models.CategoryQuery q = new Models.CategoryQuery();
        q.page = targetPage;
        if (source == Models.SourceId.s1) {
            q.typeId = s1TypeId; q.typeValue = s1Genre; q.tagType = s1Tag;
            q.area = s1Area; q.year = s1Year; q.sort = s1Sort;
        } else {
            q.stype = s2Stype; q.cid = s2Cid; q.year = s2Year; q.sort = s2Sort;
        }

        new Thread(() -> {
            try {
                List<Models.VodItem> list = Aggregator.listByCategory(source, q);
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        progress.setVisibility(View.GONE);
                        loading = false;
                        if (targetPage == 1) adapter.setItems(list);
                        else adapter.appendItems(list);
                        if (list.isEmpty()) reachedEnd = true;
                    });
                }
            } catch (Exception e) {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        progress.setVisibility(View.GONE);
                        loading = false;
                        errorTv.setText(e.getMessage());
                        errorTv.setVisibility(View.VISIBLE);
                    });
                }
            }
        }).start();
    }
}
