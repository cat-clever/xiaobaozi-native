package com.baozivideo.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.baozivideo.api.source1.Source1Constants;
import com.baozivideo.model.Models;
import com.baozivideo.utils.TvHelper;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import java.util.ArrayList;
import java.util.List;

public class MediaCardAdapter extends RecyclerView.Adapter<MediaCardAdapter.VH> {
    public interface OnItemClick {
        void onClick(Models.VodItem item);
    }

    private static final int TYPE_CARD = 0;
    private static final int TYPE_FOOTER = 1;

    private final List<Models.VodItem> items = new ArrayList<>();
    private final int cardWidthPx;
    private final OnItemClick listener;
    private Runnable onLoadMore;
    private String footerText = "";
    private boolean footerEnabled = false;

    public MediaCardAdapter(int cardWidthPx, OnItemClick listener) {
        this.cardWidthPx = cardWidthPx;
        this.listener = listener;
    }

    public void enableLoadMoreFooter(Runnable onLoadMore) {
        this.onLoadMore = onLoadMore;
    }

    public void setFooterState(String text, boolean enabled) {
        this.footerText = text == null ? "" : text;
        this.footerEnabled = enabled;
        if (hasFooter()) notifyItemChanged(items.size());
    }

    public boolean isFooterPosition(int position) {
        return hasFooter() && position == items.size();
    }

    private boolean hasFooter() {
        return onLoadMore != null && !items.isEmpty();
    }

    public int getMediaCount() {
        return items.size();
    }

    public void setItems(List<Models.VodItem> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    public void appendItems(List<Models.VodItem> list) {
        if (list == null || list.isEmpty()) return;
        int start = items.size();
        items.addAll(list);
        notifyItemRangeInserted(start, list.size());
    }

    @Override
    public int getItemViewType(int position) {
        return isFooterPosition(position) ? TYPE_FOOTER : TYPE_CARD;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context ctx = parent.getContext();
        float dp = ctx.getResources().getDisplayMetrics().density;

        if (viewType == TYPE_FOOTER) {
            FrameLayout wrap = new FrameLayout(ctx);
            RecyclerView.LayoutParams wrapLp = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            wrapLp.setMargins((int) (6 * dp), (int) (10 * dp), (int) (6 * dp), (int) (16 * dp));
            wrap.setLayoutParams(wrapLp);

            TextView btn = new TextView(ctx);
            btn.setGravity(Gravity.CENTER);
            btn.setTextSize(14);
            btn.setTypeface(null, android.graphics.Typeface.BOLD);
            btn.setPadding((int) (16 * dp), (int) (12 * dp), (int) (16 * dp), (int) (12 * dp));
            GradientDrawable fBg = new GradientDrawable();
            fBg.setCornerRadius(12 * dp);
            fBg.setColor(UiTheme.cardBg(ctx));
            fBg.setStroke((int) (1 * dp), UiTheme.cardBorder(ctx));
            btn.setBackground(fBg);
            btn.setTextColor(UiTheme.text(ctx));
            TvHelper.applyListRowFocus(btn);
            wrap.addView(btn, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new VH(wrap, btn);
        }

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        RecyclerView.LayoutParams rootLp = new RecyclerView.LayoutParams(cardWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT);
        int margin = (int) (6 * dp);
        rootLp.setMargins(margin, margin, margin, margin);
        root.setLayoutParams(rootLp);
        root.setFocusable(true);

        int imgH = (int) (cardWidthPx * 1.42f);
        FrameLayout posterBox = new FrameLayout(ctx);
        posterBox.setLayoutParams(new LinearLayout.LayoutParams(cardWidthPx, imgH));
        GradientDrawable boxBg = new GradientDrawable();
        boxBg.setColor(0x22888888);
        boxBg.setCornerRadius(12 * dp);
        posterBox.setBackground(boxBg);
        posterBox.setClipToOutline(true);

        ImageView image = new ImageView(ctx);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        posterBox.addView(image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView badge = new TextView(ctx);
        badge.setTextSize(10);
        badge.setPadding((int) (6 * dp), (int) (2 * dp), (int) (6 * dp), (int) (2 * dp));
        FrameLayout.LayoutParams badgeLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeLp.gravity = Gravity.TOP | Gravity.START;
        badgeLp.setMargins((int) (6 * dp), (int) (6 * dp), 0, 0);
        posterBox.addView(badge, badgeLp);

        TextView rating = new TextView(ctx);
        rating.setTextSize(10);
        rating.setTextColor(0xFFFFB454);
        rating.setPadding((int) (5 * dp), (int) (2 * dp), (int) (5 * dp), (int) (2 * dp));
        GradientDrawable rBg = new GradientDrawable();
        rBg.setColor(0xB8000000);
        rBg.setCornerRadius(6 * dp);
        rating.setBackground(rBg);
        FrameLayout.LayoutParams ratingLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ratingLp.gravity = Gravity.TOP | Gravity.END;
        ratingLp.setMargins(0, (int) (6 * dp), (int) (6 * dp), 0);
        posterBox.addView(rating, ratingLp);

        TextView remarks = new TextView(ctx);
        remarks.setTextSize(10);
        remarks.setTextColor(Color.WHITE);
        remarks.setMaxLines(1);
        remarks.setEllipsize(TextUtils.TruncateAt.END);
        remarks.setBackgroundColor(0x99000000);
        remarks.setPadding((int) (6 * dp), (int) (3 * dp), (int) (6 * dp), (int) (3 * dp));
        FrameLayout.LayoutParams remLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        remLp.gravity = Gravity.BOTTOM;
        posterBox.addView(remarks, remLp);

        TextView title = new TextView(ctx);
        title.setTextSize(13);
        title.setTextColor(UiTheme.text(ctx));
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tLp.topMargin = (int) (6 * dp);
        root.addView(posterBox);
        root.addView(title, tLp);
        TvHelper.applyCardFocus(root, posterBox);

        return new VH(root, image, badge, rating, remarks, title);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        if (isFooterPosition(position)) {
            if (h.footerBtn != null) {
                h.footerBtn.setText(footerText);
                h.footerBtn.setAlpha(footerEnabled ? 1.0f : 0.6f);
                h.footerBtn.setClickable(footerEnabled);
                h.footerBtn.setOnClickListener(v -> {
                    if (footerEnabled && onLoadMore != null) onLoadMore.run();
                });
            }
            return;
        }
        Models.VodItem item = items.get(position);
        h.title.setText(item.title);

        float dp = h.itemView.getResources().getDisplayMetrics().density;
        GradientDrawable bBg = new GradientDrawable();
        bBg.setCornerRadius(10 * dp);
        if (item.sourceId == Models.SourceId.s2) {
            bBg.setColor(0xFFFFB454);
            h.badge.setTextColor(0xFF241503);
        } else {
            bBg.setColor(0xC8000000);
            h.badge.setTextColor(Color.WHITE);
        }
        h.badge.setBackground(bBg);
        h.badge.setText(item.sourceId.label);

        if (item.rating != null && !item.rating.isEmpty() && !"0".equals(item.rating) && !"0.0".equals(item.rating)) {
            h.rating.setVisibility(View.VISIBLE);
            h.rating.setText(item.rating);
        } else {
            h.rating.setVisibility(View.GONE);
        }

        if (item.remarks != null && !item.remarks.isEmpty()) {
            h.remarks.setVisibility(View.VISIBLE);
            h.remarks.setText(item.remarks);
        } else {
            h.remarks.setVisibility(View.GONE);
        }

        if (item.coverUrl != null && item.coverUrl.startsWith("http")) {
            GlideUrl glideUrl = new GlideUrl(item.coverUrl, new LazyHeaders.Builder()
                .addHeader("User-Agent", Source1Constants.IMAGE_UA)
                .build());
            Glide.with(h.image.getContext()).load(glideUrl).into(h.image);
        } else {
            h.image.setImageDrawable(null);
        }

        h.itemView.setOnClickListener(v -> listener.onClick(item));
    }

    @Override
    public int getItemCount() {
        return items.size() + (hasFooter() ? 1 : 0);
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView badge, rating, remarks, title;
        final TextView footerBtn;
        VH(View itemView, ImageView image, TextView badge, TextView rating, TextView remarks, TextView title) {
            super(itemView);
            this.image = image;
            this.badge = badge;
            this.rating = rating;
            this.remarks = remarks;
            this.title = title;
            this.footerBtn = null;
        }
        VH(View itemView, TextView footerBtn) {
            super(itemView);
            this.image = null;
            this.badge = null;
            this.rating = null;
            this.remarks = null;
            this.title = null;
            this.footerBtn = footerBtn;
        }
    }
}
