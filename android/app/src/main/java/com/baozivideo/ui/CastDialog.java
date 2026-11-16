package com.baozivideo.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import com.baozivideo.cast.DlnaClient;
import com.baozivideo.model.Models;
import com.baozivideo.utils.TvHelper;
import java.util.List;

public class CastDialog {
    public interface CastHost {
        Models.CastDevice getActiveDevice();
        Models.CastSource getActiveSource();
        void castToDevice(Models.CastDevice device, Runnable onSuccess, java.util.function.Consumer<String> onError);
        void stopCast(Runnable onDone);
    }

    private final Context ctx;
    private final CastHost host;
    private final Dialog dialog;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout remoteCard;
    private LinearLayout deviceListBox;
    private ProgressBar scanProgress;
    private TextView errorTv;
    private TextView statusTv;
    private SeekBar remoteSeek;
    private TextView remoteTimeTv;
    private TextView rescanBtn;
    private TextView pauseBtn;
    private Runnable pendingSeekTask;
    private boolean dragging = false;
    private double lastPosSec = 0;

    public CastDialog(Context ctx, CastHost host) {
        this.ctx = ctx;
        this.host = host;
        this.dialog = new Dialog(ctx);
        this.dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.dialog.setCancelable(true);
        this.dialog.setCanceledOnTouchOutside(true);
        buildUi();
    }

    private void buildUi() {
        float dp = ctx.getResources().getDisplayMetrics().density;
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int) (16 * dp), (int) (16 * dp), (int) (16 * dp), (int) (16 * dp));
        GradientDrawable rootBg = new GradientDrawable();
        rootBg.setCornerRadius(20 * dp);
        rootBg.setColor(UiTheme.bg(ctx));
        rootBg.setStroke((int) (1 * dp), UiTheme.cardBorder(ctx));
        root.setBackground(rootBg);

        // 标题栏
        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(ctx);
        title.setText("DLNA 投屏");
        title.setTextSize(18);
        title.setTextColor(UiTheme.text(ctx));
        title.setTypeface(null, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView closeBtn = UiTheme.makePillButton(ctx, "关闭", false);
        closeBtn.setOnClickListener(v -> dismiss());
        header.addView(closeBtn);
        root.addView(header);

        errorTv = new TextView(ctx);
        errorTv.setTextColor(0xFFFF6B6B);
        errorTv.setTextSize(12);
        errorTv.setPadding(0, (int) (6 * dp), 0, (int) (6 * dp));
        errorTv.setVisibility(View.GONE);
        root.addView(errorTv);

        // 遥控器卡片
        remoteCard = UiTheme.makeCard(ctx);
        LinearLayout.LayoutParams rcLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rcLp.topMargin = (int) (10 * dp);
        root.addView(remoteCard, rcLp);

        // 扫描栏
        LinearLayout scanRow = new LinearLayout(ctx);
        scanRow.setOrientation(LinearLayout.HORIZONTAL);
        scanRow.setGravity(Gravity.CENTER_VERTICAL);
        scanRow.setPadding(0, (int) (14 * dp), 0, (int) (6 * dp));
        TextView devTitle = new TextView(ctx);
        devTitle.setText("局域网设备");
        devTitle.setTextSize(14);
        devTitle.setTextColor(UiTheme.textDim(ctx));
        scanRow.addView(devTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        scanProgress = new ProgressBar(ctx);
        LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams((int) (20 * dp), (int) (20 * dp));
        spLp.rightMargin = (int) (8 * dp);
        scanRow.addView(scanProgress, spLp);

        rescanBtn = UiTheme.makePillButton(ctx, "重新扫描", false);
        rescanBtn.setOnClickListener(v -> scan());
        scanRow.addView(rescanBtn);
        root.addView(scanRow);

        android.util.DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        ScrollView scroll = new ScrollView(ctx);
        scroll.setClipToPadding(false);
        scroll.setClipChildren(false);
        deviceListBox = new LinearLayout(ctx);
        deviceListBox.setOrientation(LinearLayout.VERTICAL);
        deviceListBox.setClipToPadding(false);
        deviceListBox.setClipChildren(false);
        deviceListBox.setPadding((int) (4 * dp), (int) (4 * dp), (int) (4 * dp), (int) (4 * dp));
        scroll.addView(deviceListBox);
        int listH = Math.min((int) (240 * dp), (int) (dm.heightPixels * 0.38f));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, listH));

        dialog.setContentView(root);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            int w = Math.min((int) (dm.widthPixels * 0.92f), (int) (540 * dp));
            dialog.getWindow().setLayout(w, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.setOnDismissListener(d -> handler.removeCallbacksAndMessages(null));
    }

    public void show() {
        renderRemoteCard();
        dialog.show();
        if (pauseBtn != null && remoteCard.getVisibility() == View.VISIBLE) {
            pauseBtn.requestFocus();
        } else if (rescanBtn != null) {
            rescanBtn.requestFocus();
        }
        scan();
        startStatusPolling();
    }

    public void dismiss() {
        handler.removeCallbacksAndMessages(null);
        dialog.dismiss();
    }

    private void renderRemoteCard() {
        remoteCard.removeAllViews();
        Models.CastDevice dev = host.getActiveDevice();
        Models.CastSource src = host.getActiveSource();
        if (dev == null || src == null) {
            remoteCard.setVisibility(View.GONE);
            pauseBtn = null;
            return;
        }
        remoteCard.setVisibility(View.VISIBLE);
        float dp = ctx.getResources().getDisplayMetrics().density;

        TextView nameTv = new TextView(ctx);
        nameTv.setText("正在投屏到 " + dev.name);
        nameTv.setTextSize(15);
        nameTv.setTextColor(UiTheme.text(ctx));
        nameTv.setTypeface(null, Typeface.BOLD);
        remoteCard.addView(nameTv);

        TextView modeTv = new TextView(ctx);
        modeTv.setText((src.direct ? "直投模式（可息屏）" : "转发模式（请保持前台）") + " · " + src.lineName);
        modeTv.setTextSize(12);
        modeTv.setTextColor(UiTheme.accent(ctx));
        modeTv.setPadding(0, (int) (2 * dp), 0, (int) (6 * dp));
        remoteCard.addView(modeTv);

        statusTv = new TextView(ctx);
        statusTv.setText("状态：连接中…");
        statusTv.setTextSize(12);
        statusTv.setTextColor(UiTheme.textDim(ctx));
        remoteCard.addView(statusTv);

        remoteTimeTv = new TextView(ctx);
        remoteTimeTv.setText("00:00 / 00:00");
        remoteTimeTv.setTextSize(11);
        remoteTimeTv.setTextColor(UiTheme.textDim(ctx));
        remoteTimeTv.setPadding(0, (int) (4 * dp), 0, (int) (2 * dp));
        remoteCard.addView(remoteTimeTv);

        remoteSeek = new SeekBar(ctx);
        remoteSeek.getProgressDrawable().setTint(UiTheme.accent(ctx));
        remoteSeek.getThumb().setTint(UiTheme.accent(ctx));
        TvHelper.applySeekBarFocus(remoteSeek);
        remoteSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) {
                    remoteTimeTv.setText(fmt(progress) + " / " + fmt(sb.getMax()));
                    if (!dragging) {
                        // 遥控器左右键触发时防抖发送 seek
                        if (pendingSeekTask != null) handler.removeCallbacks(pendingSeekTask);
                        pendingSeekTask = () -> new Thread(() -> {
                            try { DlnaClient.seek(dev, progress); } catch (Exception ignored) {}
                        }).start();
                        handler.postDelayed(pendingSeekTask, 350);
                    }
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar sb) { dragging = true; }
            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                dragging = false;
                if (pendingSeekTask != null) handler.removeCallbacks(pendingSeekTask);
                int target = sb.getProgress();
                new Thread(() -> { try { DlnaClient.seek(dev, target); } catch (Exception ignored) {} }).start();
            }
        });
        remoteCard.addView(remoteSeek);

        LinearLayout ctrlRow = new LinearLayout(ctx);
        ctrlRow.setOrientation(LinearLayout.HORIZONTAL);
        ctrlRow.setPadding(0, (int) (8 * dp), 0, 0);
        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        pLp.rightMargin = (int) (6 * dp);

        TextView playBtn = UiTheme.makePillButton(ctx, "播放", false);
        playBtn.setOnClickListener(v -> new Thread(() -> { try { DlnaClient.play(dev); } catch (Exception ignored) {} }).start());
        pauseBtn = UiTheme.makePillButton(ctx, "暂停", false);
        pauseBtn.setOnClickListener(v -> new Thread(() -> { try { DlnaClient.pause(dev); } catch (Exception ignored) {} }).start());
        TextView rwBtn = UiTheme.makePillButton(ctx, "-15s", false);
        rwBtn.setOnClickListener(v -> new Thread(() -> { try { DlnaClient.seek(dev, Math.max(0, lastPosSec - 15)); } catch (Exception ignored) {} }).start());
        TextView ffBtn = UiTheme.makePillButton(ctx, "+15s", false);
        ffBtn.setOnClickListener(v -> new Thread(() -> { try { DlnaClient.seek(dev, lastPosSec + 15); } catch (Exception ignored) {} }).start());
        TextView stopBtn = UiTheme.makePillButton(ctx, "退出", true);
        stopBtn.setOnClickListener(v -> host.stopCast(() -> handler.post(() -> {
            renderRemoteCard();
            if (rescanBtn != null) rescanBtn.requestFocus();
        })));

        ctrlRow.addView(playBtn, pLp);
        ctrlRow.addView(pauseBtn, pLp);
        ctrlRow.addView(rwBtn, pLp);
        ctrlRow.addView(ffBtn, pLp);
        ctrlRow.addView(stopBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        remoteCard.addView(ctrlRow);
    }

    private void scan() {
        scanProgress.setVisibility(View.VISIBLE);
        errorTv.setVisibility(View.GONE);
        new Thread(() -> {
            List<Models.CastDevice> list = DlnaClient.discoverDevices(ctx);
            handler.post(() -> {
                scanProgress.setVisibility(View.GONE);
                renderDevices(list);
            });
        }).start();
    }

    private void renderDevices(List<Models.CastDevice> devices) {
        deviceListBox.removeAllViews();
        float dp = ctx.getResources().getDisplayMetrics().density;
        if (devices.isEmpty()) {
            TextView empty = new TextView(ctx);
            empty.setText("未发现 DLNA 电视或盒子，请确认与设备连接在同一 Wi-Fi。");
            empty.setTextColor(UiTheme.textDim(ctx));
            empty.setTextSize(13);
            empty.setPadding(0, (int) (16 * dp), 0, 0);
            deviceListBox.addView(empty);
            return;
        }
        for (Models.CastDevice dev : devices) {
            LinearLayout row = UiTheme.makeCard(ctx);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = (int) (8 * dp);

            TextView name = new TextView(ctx);
            name.setText(dev.name);
            name.setTextColor(UiTheme.text(ctx));
            name.setTextSize(14);
            name.setTypeface(null, Typeface.BOLD);
            row.addView(name);

            String sub = (dev.model != null && !dev.model.isEmpty() ? dev.model + " · " : "") + (dev.address != null ? dev.address : "");
            if (!sub.isEmpty()) {
                TextView subTv = new TextView(ctx);
                subTv.setText(sub);
                subTv.setTextColor(UiTheme.textDim(ctx));
                subTv.setTextSize(12);
                row.addView(subTv);
            }

            TvHelper.applyListRowFocus(row);
            row.setOnClickListener(v -> {
                scanProgress.setVisibility(View.VISIBLE);
                errorTv.setVisibility(View.GONE);
                host.castToDevice(dev, () -> handler.post(() -> {
                    scanProgress.setVisibility(View.GONE);
                    renderRemoteCard();
                    if (pauseBtn != null) pauseBtn.requestFocus();
                }), err -> handler.post(() -> {
                    scanProgress.setVisibility(View.GONE);
                    errorTv.setText("投屏失败：" + err);
                    errorTv.setVisibility(View.VISIBLE);
                }));
            });
            deviceListBox.addView(row, lp);
        }
        if (remoteCard.getVisibility() != View.VISIBLE && deviceListBox.getChildCount() > 0) {
            deviceListBox.getChildAt(0).requestFocus();
        }
    }

    private final Runnable pollTask = new Runnable() {
        @Override
        public void run() {
            Models.CastDevice dev = host.getActiveDevice();
            if (dev != null) {
                new Thread(() -> {
                    Models.CastTransportState st = DlnaClient.status(dev);
                    handler.post(() -> {
                        if (host.getActiveDevice() == null || statusTv == null) return;
                        statusTv.setText("状态：" + st.state);
                        if (!dragging && st.positionSec != null && st.durationSec != null && st.durationSec > 0) {
                            lastPosSec = st.positionSec;
                            remoteSeek.setMax(st.durationSec.intValue());
                            remoteSeek.setProgress(st.positionSec.intValue());
                            remoteTimeTv.setText(fmt(st.positionSec.intValue()) + " / " + fmt(st.durationSec.intValue()));
                        }
                    });
                }).start();
            }
            handler.postDelayed(this, 2000);
        }
    };

    private void startStatusPolling() {
        handler.removeCallbacks(pollTask);
        handler.post(pollTask);
    }

    private static String fmt(int sec) {
        int h = sec / 3600;
        int m = (sec % 3600) / 60;
        int s = sec % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%02d:%02d", m, s);
    }
}
