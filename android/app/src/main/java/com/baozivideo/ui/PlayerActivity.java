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
import android.os.Handler;
import android.os.Looper;
import android.util.Rational;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.Lifecycle;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import com.baozivideo.R;
import com.baozivideo.api.Aggregator;
import com.baozivideo.api.source1.Source1Service;
import com.baozivideo.api.source2.Source2Service;
import com.baozivideo.cast.DlnaClient;
import com.baozivideo.cast.RelayKeepAliveService;
import com.baozivideo.media.StreamResolver;
import com.baozivideo.model.Models;
import com.baozivideo.utils.StorageManager;
import com.baozivideo.utils.TvHelper;
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
    private LinearLayout root;
    private FrameLayout videoBox;
    private FrameLayout controlsOverlay;
    private ImageView playPauseBtn;
    private ImageView fsBtn;
    private TextView curTimeTv;
    private TextView durTimeTv;
    private SeekBar seekBar;
    private boolean isDraggingSeek = false;
    private boolean controlsVisible = true;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
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

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(UiTheme.bg(this));
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            if (isFullscreen || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode())) {
                v.setPadding(0, 0, 0, 0);
            } else {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            }
            return insets;
        });
        ViewCompat.requestApplyInsets(root);

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
        playerView.setUseController(false);
        playerView.setFocusable(true);
        videoBox.addView(playerView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        loadingBar = new ProgressBar(this);
        FrameLayout.LayoutParams lpLoad = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpLoad.gravity = Gravity.CENTER;
        videoBox.addView(loadingBar, lpLoad);

        buildCustomControls(dp);
        videoBox.setOnClickListener(v -> toggleControls());
        player.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                updatePlayPauseIcon();
                if (isPlaying) {
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    scheduleHideControls();
                } else {
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode()) {
                    setPictureInPictureParams(buildPipParams());
                }
            }
        });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pipReceiver, new IntentFilter(ACTION_PIP_CONTROL), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(pipReceiver, new IntentFilter(ACTION_PIP_CONTROL));
        }
        startProgressTicker();

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
        setRequestedOrientation(isFullscreen ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            if (isFullscreen) {
                controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(WindowInsetsCompat.Type.systemBars());
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars());
            }
        }
        if (detailScroll != null) {
            detailScroll.setVisibility(isFullscreen ? View.GONE : View.VISIBLE);
        }
        if (fsBtn != null) {
            fsBtn.setImageResource(isFullscreen ? R.drawable.ic_player_fullscreen_exit : R.drawable.ic_player_fullscreen);
        }
        ViewGroup.LayoutParams lp = videoBox.getLayoutParams();
        lp.height = isFullscreen ? ViewGroup.LayoutParams.MATCH_PARENT : (int) (240 * getResources().getDisplayMetrics().density);
        videoBox.setLayoutParams(lp);
        if (root != null) {
            if (isFullscreen) root.setPadding(0, 0, 0, 0);
            else ViewCompat.requestApplyInsets(root);
        }
    }

    private void enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPictureInPictureMode(buildPipParams());
        }
    }

    @androidx.annotation.RequiresApi(api = Build.VERSION_CODES.O)
    private PictureInPictureParams buildPipParams() {
        java.util.ArrayList<RemoteAction> actions = new java.util.ArrayList<>();
        actions.add(makePipAction(android.R.drawable.ic_media_rew, "快退10秒", PIP_REWIND));
        boolean playing = player != null && player.isPlaying();
        actions.add(makePipAction(
            playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
            playing ? "暂停" : "播放",
            PIP_TOGGLE
        ));
        actions.add(makePipAction(android.R.drawable.ic_media_ff, "快进10秒", PIP_FORWARD));
        return new PictureInPictureParams.Builder()
            .setAspectRatio(new Rational(16, 9))
            .setActions(actions)
            .build();
    }

    @androidx.annotation.RequiresApi(api = Build.VERSION_CODES.O)
    private RemoteAction makePipAction(int iconRes, String title, int type) {
        Intent intent = new Intent(ACTION_PIP_CONTROL).setPackage(getPackageName()).putExtra(EXTRA_PIP_TYPE, type);
        PendingIntent pi = PendingIntent.getBroadcast(
            this,
            type,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        return new RemoteAction(Icon.createWithResource(this, iconRes), title, title, pi);
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
        if (controlsOverlay != null) {
            controlsOverlay.setVisibility(isInPictureInPictureMode ? View.GONE : View.VISIBLE);
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
                if (selectedQuality == null && !d.qualities.isEmpty()) {
                    for (Models.QualityOption q : d.qualities) {
                        if ((q.label != null && q.label.toLowerCase().contains("720")) || q.value == 2) {
                            selectedQuality = q.value;
                            break;
                        }
                    }
                    if (selectedQuality == null) {
                        selectedQuality = d.qualities.get(0).value;
                    }
                }
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
        titleTv.setTextColor(UiTheme.text(this));
        titleTv.setTypeface(null, Typeface.BOLD);
        infoContainer.addView(titleTv);

        if (currentStream != null) {
            TextView lineTv = new TextView(this);
            lineTv.setText("当前线路：" + currentStream.lineName);
            lineTv.setTextSize(12);
            lineTv.setTextColor(UiTheme.textDim(this));
            lineTv.setPadding(0, (int) (4 * dp), 0, (int) (8 * dp));
            infoContainer.addView(lineTv);
        }

        LinearLayout.LayoutParams pillLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pillLp.rightMargin = (int) (8 * dp);

        // 倍速
        HorizontalScrollView speedScroll = new HorizontalScrollView(this);
        speedScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout speedRow = new LinearLayout(this);
        speedRow.setPadding(0, (int) (4 * dp), 0, (int) (8 * dp));
        for (float sp : SPEEDS) {
            boolean active = Math.abs(playbackSpeed - sp) < 0.01f;
            TextView b = UiTheme.makePillButton(this, sp + "x", active);
            b.setOnClickListener(v -> {
                playbackSpeed = sp;
                player.setPlaybackParameters(new PlaybackParameters(sp));
                renderDetailUi();
            });
            speedRow.addView(b, pillLp);
        }
        speedScroll.addView(speedRow);
        infoContainer.addView(speedScroll);

        // 分季
        if (detail != null && detail.seasons.size() > 1) {
            TextView st = new TextView(this); st.setText("分季"); st.setTextColor(UiTheme.text(this)); st.setTypeface(null, Typeface.BOLD);
            st.setPadding(0, (int) (8 * dp), 0, (int) (4 * dp));
            infoContainer.addView(st);
            HorizontalScrollView hsv = new HorizontalScrollView(this);
            hsv.setHorizontalScrollBarEnabled(false);
            LinearLayout row = new LinearLayout(this);
            for (Models.SeasonOption s : detail.seasons) {
                TextView b = UiTheme.makePillButton(this, s.title, s.sid.equals(currentVodId));
                b.setOnClickListener(v -> loadDetailAndPlay(s.sid, 0));
                row.addView(b, pillLp);
            }
            hsv.addView(row);
            infoContainer.addView(hsv);
        }

        // 源1线路
        if (!lines.isEmpty()) {
            TextView lt = new TextView(this); lt.setText("线路"); lt.setTextColor(UiTheme.text(this)); lt.setTypeface(null, Typeface.BOLD);
            lt.setPadding(0, (int) (8 * dp), 0, (int) (4 * dp));
            infoContainer.addView(lt);
            HorizontalScrollView hsv = new HorizontalScrollView(this);
            hsv.setHorizontalScrollBarEnabled(false);
            LinearLayout row = new LinearLayout(this);
            for (Models.PlayLine l : lines) {
                TextView b = UiTheme.makePillButton(this, l.name, selectedLine == l);
                b.setEnabled(l.playable);
                if (!l.playable) b.setAlpha(0.45f);
                b.setOnClickListener(v -> switchS1Line(l));
                row.addView(b, pillLp);
            }
            hsv.addView(row);
            infoContainer.addView(hsv);
        }

        // 源2清晰度
        if (detail != null && !detail.qualities.isEmpty()) {
            TextView qt = new TextView(this); qt.setText("清晰度"); qt.setTextColor(UiTheme.text(this)); qt.setTypeface(null, Typeface.BOLD);
            qt.setPadding(0, (int) (8 * dp), 0, (int) (4 * dp));
            infoContainer.addView(qt);
            HorizontalScrollView hsv = new HorizontalScrollView(this);
            hsv.setHorizontalScrollBarEnabled(false);
            LinearLayout row = new LinearLayout(this);
            for (Models.QualityOption q : detail.qualities) {
                boolean active = selectedQuality != null && selectedQuality == q.value;
                TextView b = UiTheme.makePillButton(this, q.label, active);
                b.setOnClickListener(v -> {
                    selectedQuality = q.value;
                    if (episodeIndex < detail.episodes.size()) playEpisode(detail.episodes.get(episodeIndex), q.value);
                });
                row.addView(b, pillLp);
            }
            hsv.addView(row);
            infoContainer.addView(hsv);
        }

        // 选集
        if (detail != null && !detail.episodes.isEmpty()) {
            TextView et = new TextView(this); et.setText("选集（" + detail.episodes.size() + "）"); et.setTextColor(UiTheme.text(this)); et.setTypeface(null, Typeface.BOLD);
            et.setPadding(0, (int) (10 * dp), 0, (int) (6 * dp));
            infoContainer.addView(et);
            HorizontalScrollView hsv = new HorizontalScrollView(this);
            hsv.setHorizontalScrollBarEnabled(false);
            LinearLayout row = new LinearLayout(this);
            for (Models.Episode ep : detail.episodes) {
                boolean active = ep.episodeIndex == episodeIndex;
                TextView btn = UiTheme.makePillButton(this, ep.title, active);
                btn.setOnClickListener(v -> playEpisode(ep, selectedQuality));
                row.addView(btn, pillLp);
            }
            hsv.addView(row);
            infoContainer.addView(hsv);
        }

        // 简介
        if (detail != null && detail.blurb != null && !detail.blurb.isEmpty()) {
            TextView bt = new TextView(this);
            bt.setText("\n简介：\n" + detail.blurb);
            bt.setTextSize(13);
            bt.setTextColor(UiTheme.textDim(this));
            infoContainer.addView(bt);
        }
    }

    private void showCastDialog() {
        new CastDialog(this, new CastDialog.CastHost() {
            @Override
            public Models.CastDevice getActiveDevice() { return activeCastDevice; }

            @Override
            public Models.CastSource getActiveSource() { return activeCastSource; }

            @Override
            public void castToDevice(Models.CastDevice device, Runnable onSuccess, java.util.function.Consumer<String> onError) {
                startCast(device, onSuccess, onError);
            }

            @Override
            public void stopCast(Runnable onDone) {
                PlayerActivity.this.stopCast(onDone);
            }
        }).show();
    }

    private void startCast(Models.CastDevice device, Runnable onSuccess, java.util.function.Consumer<String> onError) {
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
                runOnUiThread(() -> {
                    if (player != null) {
                        player.pause();
                        updatePlayPauseIcon();
                    }
                    Toast.makeText(this, "已投屏到 " + device.name + (src.direct ? "（直投）" : "（转发）"), Toast.LENGTH_SHORT).show();
                    if (onSuccess != null) onSuccess.run();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (onError != null) onError.accept(e.getMessage());
                });
            }
        }).start();
    }

    private void stopCast() {
        stopCast(null);
    }

    private void stopCast(Runnable onDone) {
        new Thread(() -> {
            if (activeCastDevice != null) {
                try { DlnaClient.stop(activeCastDevice); } catch (Exception ignored) {}
            }
            if (activeCastSource != null && activeCastSource.dispose != null) activeCastSource.dispose.run();
            activeCastSource = null;
            activeCastDevice = null;
            RelayKeepAliveService.stop(this);
            if (onDone != null) onDone.run();
        }).start();
    }

    private void showError(String msg) {
        errorTv.setText(msg);
        errorTv.setVisibility(View.VISIBLE);
    }

    private void buildCustomControls(float dp) {
        controlsOverlay = new FrameLayout(this);
        videoBox.addView(controlsOverlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 顶部栏：左上返回，右上投屏 + 画中画（纯图标）
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(0x55000000);
        topBar.setPadding((int) (8 * dp), (int) (6 * dp), (int) (8 * dp), (int) (6 * dp));

        ImageView backBtn = makeIconBtn(dp, R.drawable.ic_player_back, 36);
        backBtn.setOnClickListener(v -> {
            if (isFullscreen) toggleFullscreen(videoBox);
            else finish();
        });
        topBar.addView(backBtn);

        View spacer = new View(this);
        topBar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        ImageView castBtn = makeIconBtn(dp, R.drawable.ic_player_cast, 36);
        castBtn.setOnClickListener(v -> { showCastDialog(); scheduleHideControls(); });
        topBar.addView(castBtn);

        ImageView pipBtn = makeIconBtn(dp, R.drawable.ic_player_pip, 36);
        pipBtn.setOnClickListener(v -> enterPip());
        topBar.addView(pipBtn);

        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        topLp.gravity = Gravity.TOP;
        controlsOverlay.addView(topBar, topLp);

        // 中央播放/暂停按钮
        playPauseBtn = makeIconBtn(dp, R.drawable.ic_player_pause, 52);
        GradientDrawable centerBg = new GradientDrawable();
        centerBg.setShape(GradientDrawable.OVAL);
        centerBg.setColor(0x88000000);
        playPauseBtn.setBackground(centerBg);
        playPauseBtn.setPadding((int) (10 * dp), (int) (10 * dp), (int) (10 * dp), (int) (10 * dp));
        playPauseBtn.setOnClickListener(v -> {
            if (player.isPlaying()) player.pause();
            else player.play();
            updatePlayPauseIcon();
            scheduleHideControls();
        });
        FrameLayout.LayoutParams centerLp = new FrameLayout.LayoutParams((int) (52 * dp), (int) (52 * dp));
        centerLp.gravity = Gravity.CENTER;
        controlsOverlay.addView(playPauseBtn, centerLp);

        // 底部栏：左下全屏图标 + 当前时间 + 进度条 + 总时长 + 右下齿轮图标（同一水平线）
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);
        bottomBar.setBackgroundColor(0x66000000);
        bottomBar.setPadding((int) (6 * dp), (int) (4 * dp), (int) (6 * dp), (int) (4 * dp));

        fsBtn = makeIconBtn(dp, R.drawable.ic_player_fullscreen, 32);
        fsBtn.setOnClickListener(v -> { toggleFullscreen(videoBox); scheduleHideControls(); });
        bottomBar.addView(fsBtn);

        curTimeTv = new TextView(this);
        curTimeTv.setText("00:00");
        curTimeTv.setTextColor(Color.WHITE);
        curTimeTv.setTextSize(11);
        curTimeTv.setPadding((int) (4 * dp), 0, (int) (4 * dp), 0);
        bottomBar.addView(curTimeTv);

        seekBar = new SeekBar(this);
        seekBar.getProgressDrawable().setTint(0xFFFFB454);
        seekBar.getThumb().setTint(0xFFFFB454);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser && curTimeTv != null) curTimeTv.setText(formatTime(progress));
            }
            @Override
            public void onStartTrackingTouch(SeekBar sb) {
                isDraggingSeek = true;
                uiHandler.removeCallbacks(hideControlsTask);
            }
            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                isDraggingSeek = false;
                if (player != null) player.seekTo(sb.getProgress());
                scheduleHideControls();
            }
        });
        bottomBar.addView(seekBar, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        durTimeTv = new TextView(this);
        durTimeTv.setText("00:00");
        durTimeTv.setTextColor(Color.WHITE);
        durTimeTv.setTextSize(11);
        durTimeTv.setPadding((int) (4 * dp), 0, (int) (4 * dp), 0);
        bottomBar.addView(durTimeTv);

        ImageView gearBtn = makeIconBtn(dp, R.drawable.ic_player_settings, 32);
        gearBtn.setOnClickListener(v -> { showSpeedDialog(); scheduleHideControls(); });
        bottomBar.addView(gearBtn);

        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bottomLp.gravity = Gravity.BOTTOM;
        controlsOverlay.addView(bottomBar, bottomLp);

        scheduleHideControls();
    }

    private ImageView makeIconBtn(float dp, int resId, int sizeDp) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(resId);
        int pad = (int) (6 * dp);
        iv.setPadding(pad, pad, pad, pad);
        iv.setLayoutParams(new LinearLayout.LayoutParams((int) (sizeDp * dp), (int) (sizeDp * dp)));
        TvHelper.applyChipFocus(iv);
        return iv;
    }

    private void showSpeedDialog() {
        String[] labels = new String[SPEEDS.length];
        for (int i = 0; i < SPEEDS.length; i++) labels[i] = SPEEDS[i] + "x";
        new AlertDialog.Builder(this)
            .setTitle("播放倍速")
            .setItems(labels, (d, idx) -> {
                playbackSpeed = SPEEDS[idx];
                if (player != null) player.setPlaybackParameters(new PlaybackParameters(playbackSpeed));
                renderDetailUi();
            })
            .show();
    }

    private final Runnable hideControlsTask = () -> {
        if (controlsOverlay != null && player != null && player.isPlaying()) {
            controlsVisible = false;
            controlsOverlay.setVisibility(View.GONE);
        }
    };

    private void toggleControls() {
        if (controlsOverlay == null) return;
        controlsVisible = !controlsVisible;
        controlsOverlay.setVisibility(controlsVisible ? View.VISIBLE : View.GONE);
        if (controlsVisible) scheduleHideControls();
    }

    private void showControllerNow() {
        if (controlsOverlay == null) return;
        controlsVisible = true;
        controlsOverlay.setVisibility(View.VISIBLE);
        scheduleHideControls();
    }

    private void scheduleHideControls() {
        uiHandler.removeCallbacks(hideControlsTask);
        uiHandler.postDelayed(hideControlsTask, 4000);
    }

    private void updatePlayPauseIcon() {
        if (playPauseBtn != null && player != null) {
            playPauseBtn.setImageResource(player.isPlaying() ? R.drawable.ic_player_pause : R.drawable.ic_player_play);
        }
    }

    private final Runnable progressTicker = new Runnable() {
        @Override
        public void run() {
            if (player != null && !isDraggingSeek) {
                long dur = Math.max(0, player.getDuration());
                long pos = Math.max(0, player.getCurrentPosition());
                if (dur > 0 && seekBar != null) {
                    seekBar.setMax((int) dur);
                    seekBar.setProgress((int) pos);
                }
                if (curTimeTv != null) curTimeTv.setText(formatTime(pos));
                if (durTimeTv != null) durTimeTv.setText(formatTime(dur));
            }
            uiHandler.postDelayed(this, 500);
        }
    };

    private void startProgressTicker() {
        uiHandler.removeCallbacks(progressTicker);
        uiHandler.post(progressTicker);
    }

    private static String formatTime(long ms) {
        long totalSec = Math.max(0, ms / 1000);
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        long s = totalSec % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%02d:%02d", m, s);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && player != null) {
            int code = event.getKeyCode();
            if (code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                if (player.isPlaying()) player.pause();
                else player.play();
                showControllerNow();
                return true;
            } else if (code == KeyEvent.KEYCODE_MEDIA_PLAY) {
                player.play();
                showControllerNow();
                return true;
            } else if (code == KeyEvent.KEYCODE_MEDIA_PAUSE) {
                player.pause();
                showControllerNow();
                return true;
            } else if (code == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD || (isFullscreen && code == KeyEvent.KEYCODE_DPAD_RIGHT && playerView.hasFocus())) {
                player.seekTo(Math.max(0, player.getCurrentPosition() + 10000));
                showControllerNow();
                return true;
            } else if (code == KeyEvent.KEYCODE_MEDIA_REWIND || (isFullscreen && code == KeyEvent.KEYCODE_DPAD_LEFT && playerView.hasFocus())) {
                player.seekTo(Math.max(0, player.getCurrentPosition() - 10000));
                showControllerNow();
                return true;
            } else if (code == KeyEvent.KEYCODE_BACK && isFullscreen) {
                toggleFullscreen(videoBox);
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(pipReceiver); } catch (Exception ignored) {}
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        uiHandler.removeCallbacksAndMessages(null);
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
