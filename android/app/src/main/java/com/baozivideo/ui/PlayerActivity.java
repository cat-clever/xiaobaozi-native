package com.baozivideo.ui;

import android.app.AlertDialog;
import android.app.PictureInPictureParams;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Rational;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Lifecycle;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import com.baozivideo.api.Aggregator;
import com.baozivideo.api.source1.Source1Service;
import com.baozivideo.api.source2.Source2Service;
import com.baozivideo.cast.DlnaClient;
import com.baozivideo.cast.RelayKeepAliveService;
import com.baozivideo.media.StreamResolver;
import com.baozivideo.model.Models;
import com.baozivideo.utils.StorageManager;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class PlayerActivity extends AppCompatActivity {
    private Models.VodItem item;
    private Models.VodDetail detail;
    private String currentVodId;
    private int episodeIndex = 0;

    private List<Models.PlayLine> lines = new ArrayList<>();
    private Models.PlayLine selectedLine;
    private Integer selectedQuality;
    private List<JSONObject> s2Candidates = new ArrayList<>();

    private Models.ResolvedMediaStream currentStream;
    private Models.CastSource activeCastSource;
    private Models.CastDevice activeCastDevice;

    private ExoPlayer player;
    private PlayerView playerView;
    private FrameLayout videoBox;
    private LinearLayout topBtns;
    private ScrollView detailScroll;
    private ProgressBar loadingBar;
    private LinearLayout infoContainer;
    private TextView errorTv;
    private float playbackSpeed = 1.0f;
    private boolean isFullscreen = false;
    private int playGeneration = 0;

    private static final float[] SPEEDS = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        item = (Models.VodItem) getIntent().getSerializableExtra("item");
        if (item == null) {
            finish();
            return;
        }
        currentVodId = item.id;
        float dp = getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        videoBox = new FrameLayout(this);
        videoBox.setBackgroundColor(Color.BLACK);
        playerView = new PlayerView(this);
        AudioAttributes audioAttrs = new AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build();
        player = new ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttrs, true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build();
        playerView.setPlayer(player);
        videoBox.addView(playerView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        loadingBar = new ProgressBar(this);
        FrameLayout.LayoutParams lpLoad = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpLoad.gravity = android.view.Gravity.CENTER;
        videoBox.addView(loadingBar, lpLoad);

        topBtns = new LinearLayout(this);
        topBtns.setOrientation(LinearLayout.HORIZONTAL);
        Button backBtn = new Button(this); backBtn.setText("‹");
        backBtn.setOnClickListener(v -> finish());
        Button castBtn = new Button(this); castBtn.setText("投屏");
        castBtn.setOnClickListener(v -> showCastDialog());
        Button pipBtn = new Button(this); pipBtn.setText("画中画");
        pipBtn.setOnClickListener(v -> enterPip());
        Button fsBtn = new Button(this); fsBtn.setText("全屏");
        fsBtn.setOnClickListener(v -> toggleFullscreen(videoBox));

        topBtns.addView(backBtn);
        topBtns.addView(castBtn);
        topBtns.addView(pipBtn);
        topBtns.addView(fsBtn);
        videoBox.addView(topBtns);

        root.addView(videoBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (240 * dp)));

        detailScroll = new ScrollView(this);
        infoContainer = new LinearLayout(this);
        infoContainer.setOrientation(LinearLayout.VERTICAL);
        infoContainer.setPadding((int) (14 * dp), (int) (12 * dp), (int) (14 * dp), (int) (24 * dp));

        errorTv = new TextView(this);
        errorTv.setTextColor(0xFFFF6B6B);
        errorTv.setVisibility(View.GONE);
        infoContainer.addView(errorTv);

        detailScroll.addView(infoContainer);
        root.addView(detailScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        loadDetailAndPlay(currentVodId, 0);
    }

    private void toggleFullscreen(FrameLayout videoBox) {
        isFullscreen = !isFullscreen;
        setRequestedOrientation(isFullscreen ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        ViewGroup.LayoutParams lp = videoBox.getLayoutParams();
        lp.height = isFullscreen ? ViewGroup.LayoutParams.MATCH_PARENT : (int) (240 * getResources().getDisplayMetrics().density);
        videoBox.setLayoutParams(lp);
    }

    private void enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PictureInPictureParams params = new PictureInPictureParams.Builder()
                .setAspectRatio(new Rational(16, 9))
                .build();
            enterPictureInPictureMode(params);
        }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && player != null && player.isPlaying()) {
            enterPip();
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (playerView != null) {
            playerView.setUseController(!isInPictureInPictureMode);
        }
        if (topBtns != null) {
            topBtns.setVisibility(isInPictureInPictureMode ? View.GONE : View.VISIBLE);
        }
        if (detailScroll != null) {
            detailScroll.setVisibility(isInPictureInPictureMode ? View.GONE : View.VISIBLE);
        }
        if (videoBox != null) {
            ViewGroup.LayoutParams lp = videoBox.getLayoutParams();
            if (isInPictureInPictureMode || isFullscreen) {
                lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            } else {
                lp.height = (int) (240 * getResources().getDisplayMetrics().density);
            }
            videoBox.setLayoutParams(lp);
        }
        if (!isInPictureInPictureMode && getLifecycle().getCurrentState() == Lifecycle.State.CREATED) {
            if (player != null) player.pause();
        }
    }

    private void loadDetailAndPlay(String vodId, int epIdx) {
        currentVodId = vodId;
        loadingBar.setVisibility(View.VISIBLE);
        errorTv.setVisibility(View.GONE);

        new Thread(() -> {
            try {
                Models.VodDetail d = Aggregator.detail(item.sourceId, vodId);
                detail = d;
                int target = Math.min(epIdx, Math.max(0, d.episodes.size() - 1));
                runOnUiThread(() -> {
                    renderDetailUi();
                    if (!d.episodes.isEmpty()) {
                        playEpisode(d.episodes.get(target), selectedQuality);
                    } else {
                        loadingBar.setVisibility(View.GONE);
                        showError("没有可播放的分集");
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    loadingBar.setVisibility(View.GONE);
                    showError(e.getMessage());
                });
            }
        }).start();
    }

    private void playEpisode(Models.Episode ep, Integer quality) {
        final int gen = ++playGeneration;
        episodeIndex = ep.episodeIndex;
        loadingBar.setVisibility(View.VISIBLE);
        errorTv.setVisibility(View.GONE);
        if (player != null) {
            player.stop();
            player.clearMediaItems();
        }
        if (currentStream != null && currentStream.dispose != null) {
            currentStream.dispose.run();
            currentStream = null;
        }

        new Thread(() -> {
            try {
                Models.ResolvedMediaStream resolved;
                if (item.sourceId == Models.SourceId.s1) {
                    lines = Source1Service.playLinesFor(currentVodId, ep);
                    selectedLine = StreamResolver.pickPlayableLine(lines);
                    resolved = StreamResolver.resolveSource1(lines, selectedLine);
                } else {
                    s2Candidates = Source2Service.playFor(currentVodId, ep, quality);
                    if (s2Candidates.isEmpty()) throw new Exception("没有拿到播放地址");
                    resolved = StreamResolver.resolveSource2Candidate(s2Candidates.get(0));
                }
                if (gen != playGeneration) {
                    if (resolved.dispose != null) resolved.dispose.run();
                    return;
                }
                runOnUiThread(() -> {
                    if (gen == playGeneration) startPlayback(resolved);
                    else if (resolved.dispose != null) resolved.dispose.run();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (gen == playGeneration) {
                        loadingBar.setVisibility(View.GONE);
                        showError(e.getMessage());
                    }
                });
            }
        }).start();
    }

    private void switchS1Line(Models.PlayLine line) {
        final int gen = ++playGeneration;
        selectedLine = line;
        loadingBar.setVisibility(View.VISIBLE);
        if (player != null) {
            player.stop();
            player.clearMediaItems();
        }
        if (currentStream != null && currentStream.dispose != null) {
            currentStream.dispose.run();
            currentStream = null;
        }
        new Thread(() -> {
            try {
                Models.ResolvedMediaStream resolved = StreamResolver.resolveSource1(lines, line);
                if (gen != playGeneration) {
                    if (resolved.dispose != null) resolved.dispose.run();
                    return;
                }
                runOnUiThread(() -> {
                    if (gen == playGeneration) startPlayback(resolved);
                    else if (resolved.dispose != null) resolved.dispose.run();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (gen == playGeneration) {
                        loadingBar.setVisibility(View.GONE);
                        showError(e.getMessage());
                    }
                });
            }
        }).start();
    }

    private void startPlayback(Models.ResolvedMediaStream resolved) {
        currentStream = resolved;
        loadingBar.setVisibility(View.GONE);
        player.setMediaItem(MediaItem.fromUri(resolved.streamUrl));
        player.setPlaybackParameters(new PlaybackParameters(playbackSpeed));
        player.prepare();
        player.play();
        renderDetailUi();
    }

    private void renderDetailUi() {
        while (infoContainer.getChildCount() > 1) {
            infoContainer.removeViewAt(1);
        }
        float dp = getResources().getDisplayMetrics().density;

        TextView titleTv = new TextView(this);
        titleTv.setText((detail != null ? detail.name : item.title) + "  [" + item.sourceId.label + "]");
        titleTv.setTextSize(19);
        titleTv.setTypeface(null, Typeface.BOLD);
        infoContainer.addView(titleTv);

        if (currentStream != null) {
            TextView lineTv = new TextView(this);
            lineTv.setText("当前线路：" + currentStream.lineName);
            lineTv.setTextSize(12);
            infoContainer.addView(lineTv);
        }

        // 倍速
        HorizontalScrollView speedScroll = new HorizontalScrollView(this);
        LinearLayout speedRow = new LinearLayout(this);
        for (float sp : SPEEDS) {
            Button b = new Button(this);
            b.setText(sp + "x");
            if (Math.abs(playbackSpeed - sp) < 0.01f) b.setTextColor(0xFFFFB454);
            b.setOnClickListener(v -> {
                playbackSpeed = sp;
                player.setPlaybackParameters(new PlaybackParameters(sp));
                renderDetailUi();
            });
            speedRow.addView(b);
        }
        speedScroll.addView(speedRow);
        infoContainer.addView(speedScroll);

        // 分季
        if (detail != null && detail.seasons.size() > 1) {
            TextView st = new TextView(this); st.setText("分季"); st.setTypeface(null, Typeface.BOLD);
            infoContainer.addView(st);
            HorizontalScrollView hsv = new HorizontalScrollView(this);
            LinearLayout row = new LinearLayout(this);
            for (Models.SeasonOption s : detail.seasons) {
                Button b = new Button(this);
                b.setText(s.title);
                if (s.sid.equals(currentVodId)) b.setTextColor(0xFFFFB454);
                b.setOnClickListener(v -> loadDetailAndPlay(s.sid, 0));
                row.addView(b);
            }
            hsv.addView(row);
            infoContainer.addView(hsv);
        }

        // 源1线路
        if (!lines.isEmpty()) {
            TextView lt = new TextView(this); lt.setText("线路"); lt.setTypeface(null, Typeface.BOLD);
            infoContainer.addView(lt);
            HorizontalScrollView hsv = new HorizontalScrollView(this);
            LinearLayout row = new LinearLayout(this);
            for (Models.PlayLine l : lines) {
                Button b = new Button(this);
                b.setText(l.name);
                b.setEnabled(l.playable);
                if (selectedLine == l) b.setTextColor(0xFFFFB454);
                b.setOnClickListener(v -> switchS1Line(l));
                row.addView(b);
            }
            hsv.addView(row);
            infoContainer.addView(hsv);
        }

        // 源2清晰度
        if (detail != null && !detail.qualities.isEmpty()) {
            TextView qt = new TextView(this); qt.setText("清晰度"); qt.setTypeface(null, Typeface.BOLD);
            infoContainer.addView(qt);
            HorizontalScrollView hsv = new HorizontalScrollView(this);
            LinearLayout row = new LinearLayout(this);
            for (Models.QualityOption q : detail.qualities) {
                Button b = new Button(this);
                b.setText(q.label);
                if (selectedQuality != null && selectedQuality == q.value) b.setTextColor(0xFFFFB454);
                b.setOnClickListener(v -> {
                    selectedQuality = q.value;
                    if (episodeIndex < detail.episodes.size()) playEpisode(detail.episodes.get(episodeIndex), q.value);
                });
                row.addView(b);
            }
            hsv.addView(row);
            infoContainer.addView(hsv);
        }

        // 选集
        if (detail != null && !detail.episodes.isEmpty()) {
            TextView et = new TextView(this); et.setText("选集（" + detail.episodes.size() + "）"); et.setTypeface(null, Typeface.BOLD);
            et.setPadding(0, (int) (10 * dp), 0, (int) (6 * dp));
            infoContainer.addView(et);
            HorizontalScrollView hsv = new HorizontalScrollView(this);
            LinearLayout row = new LinearLayout(this);
            for (Models.Episode ep : detail.episodes) {
                TextView btn = new TextView(this);
                btn.setText(ep.title);
                boolean active = ep.episodeIndex == episodeIndex;
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(8 * dp);
                bg.setColor(active ? 0xFFFFB454 : 0x22888888);
                btn.setTextColor(active ? 0xFF241503 : 0xFF888888);
                btn.setBackground(bg);
                btn.setPadding((int) (14 * dp), (int) (8 * dp), (int) (14 * dp), (int) (8 * dp));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.rightMargin = (int) (8 * dp);
                btn.setOnClickListener(v -> playEpisode(ep, selectedQuality));
                row.addView(btn, lp);
            }
            hsv.addView(row);
            infoContainer.addView(hsv);
        }

        // 简介
        if (detail != null && detail.blurb != null && !detail.blurb.isEmpty()) {
            TextView bt = new TextView(this);
            bt.setText("\n简介：\n" + detail.blurb);
            bt.setTextSize(12);
            infoContainer.addView(bt);
        }
    }

    private void showCastDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("正在搜索 DLNA 设备…");
        builder.setNegativeButton("关闭", null);
        if (activeCastDevice != null) {
            builder.setNeutralButton("停止投屏", (d, w) -> stopCast());
        }
        AlertDialog dlg = builder.show();

        new Thread(() -> {
            List<Models.CastDevice> devices = DlnaClient.discoverDevices(this);
            runOnUiThread(() -> {
                dlg.dismiss();
                if (devices.isEmpty()) {
                    Toast.makeText(this, "未发现 DLNA 电视或盒子", Toast.LENGTH_SHORT).show();
                    return;
                }
                String[] names = new String[devices.size()];
                for (int i = 0; i < devices.size(); i++) names[i] = devices.get(i).name;
                new AlertDialog.Builder(this)
                    .setTitle("选择投屏设备")
                    .setItems(names, (d, idx) -> startCast(devices.get(idx)))
                    .setNegativeButton("取消", null)
                    .show();
            });
        }).start();
    }

    private void startCast(Models.CastDevice device) {
        new Thread(() -> {
            try {
                if (activeCastSource != null && activeCastSource.dispose != null) activeCastSource.dispose.run();
                Models.CastSource src;
                if (item.sourceId == Models.SourceId.s1) {
                    src = StreamResolver.castSourceForSource1(lines, selectedLine);
                } else {
                    if (s2Candidates.isEmpty()) throw new Exception("没有可投屏的地址");
                    src = StreamResolver.castSourceForSource2Candidate(s2Candidates.get(0));
                }
                String title = (detail != null ? detail.name : item.title) + " - 第" + (episodeIndex + 1) + "集";
                DlnaClient.cast(device, src.url, title);
                activeCastSource = src;
                activeCastDevice = device;
                if (!src.direct) RelayKeepAliveService.start(this, title);
                runOnUiThread(() -> Toast.makeText(this, "已投屏到 " + device.name + (src.direct ? "（直投）" : "（转发）"), Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "投屏失败：" + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void stopCast() {
        new Thread(() -> {
            if (activeCastDevice != null) {
                try { DlnaClient.stop(activeCastDevice); } catch (Exception ignored) {}
            }
            if (activeCastSource != null && activeCastSource.dispose != null) activeCastSource.dispose.run();
            activeCastSource = null;
            activeCastDevice = null;
            RelayKeepAliveService.stop(this);
        }).start();
    }

    private void showError(String msg) {
        errorTv.setText(msg);
        errorTv.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        if (player != null) {
            long pos = player.getCurrentPosition() / 1000;
            if (pos > 0) {
                StorageManager.getInstance().saveWatchRecord(item.id, item.sourceId.name(), item.title, item.coverUrl, episodeIndex, pos);
            }
            player.release();
        }
        if (currentStream != null && currentStream.dispose != null) currentStream.dispose.run();
        stopCast();
        super.onDestroy();
    }
}
