package com.baozivideo.ui;

import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.baozivideo.BaoziApplication;
import com.baozivideo.update.UpdaterService;
import com.baozivideo.utils.StorageManager;
import com.baozivideo.utils.TvHelper;

public class SettingsActivity extends AppCompatActivity {
    private TextView updateStatusTv;
    private TextView installBtn;
    private UpdaterService.Manifest latestManifest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        float dp = getResources().getDisplayMetrics().density;

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(UiTheme.bg(this));
        UiTheme.applySystemBarInsets(scroll, true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int) (16 * dp), (int) (12 * dp), (int) (16 * dp), (int) (24 * dp));

        TextView backBtn = UiTheme.makePillButton(this, "‹ 返回", false);
        backBtn.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        root.addView(backBtn, backLp);
        if (TvHelper.isTv(this)) backBtn.requestFocus();

        TextView title = new TextView(this);
        title.setText("设置");
        title.setTextSize(26);
        title.setTextColor(UiTheme.text(this));
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, (int) (12 * dp), 0, (int) (14 * dp));
        root.addView(title);

        TextView secTheme = new TextView(this);
        secTheme.setText("外观");
        secTheme.setTextSize(13);
        secTheme.setTextColor(UiTheme.textDim(this));
        secTheme.setPadding((int) (4 * dp), 0, 0, (int) (6 * dp));
        root.addView(secTheme);

        LinearLayout themeCard = UiTheme.makeCard(this);
        RadioGroup rg = new RadioGroup(this);
        String curMode = StorageManager.getInstance().getThemeMode();
        String[][] modes = {{"system", "跟随系统"}, {"dark", "深色"}, {"light", "亮色"}};
        for (String[] m : modes) {
            RadioButton rb = new RadioButton(this);
            rb.setText(m[1]);
            rb.setTextColor(UiTheme.text(this));
            rb.setTag(m[0]);
            rg.addView(rb);
            if (m[0].equals(curMode)) rb.setChecked(true);
        }
        rg.setOnCheckedChangeListener((group, checkedId) -> {
            View v = group.findViewById(checkedId);
            if (v != null && v.getTag() instanceof String) {
                String mode = (String) v.getTag();
                StorageManager.getInstance().saveThemeMode(mode);
                BaoziApplication.applyThemeMode(mode);
            }
        });
        themeCard.addView(rg);
        root.addView(themeCard);

        TextView secUpdate = new TextView(this);
        secUpdate.setText("版本与更新");
        secUpdate.setTextSize(13);
        secUpdate.setTextColor(UiTheme.textDim(this));
        secUpdate.setPadding((int) (4 * dp), (int) (18 * dp), 0, (int) (6 * dp));
        root.addView(secUpdate);

        LinearLayout updateCard = UiTheme.makeCard(this);
        TextView curVer = new TextView(this);
        curVer.setText("当前版本：" + UpdaterService.getCurrentVersionName(this));
        curVer.setTextColor(UiTheme.text(this));
        curVer.setTextSize(14);
        updateCard.addView(curVer);

        updateStatusTv = new TextView(this);
        updateStatusTv.setText("正在检查更新…");
        updateStatusTv.setTextColor(UiTheme.textDim(this));
        updateStatusTv.setTextSize(13);
        updateStatusTv.setPadding(0, (int) (6 * dp), 0, (int) (10 * dp));
        updateCard.addView(updateStatusTv);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView checkBtn = UiTheme.makePillButton(this, "检查更新", false);
        checkBtn.setOnClickListener(v -> checkUpdate());
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bLp.rightMargin = (int) (8 * dp);
        btnRow.addView(checkBtn, bLp);

        installBtn = UiTheme.makePillButton(this, "下载并安装新版", true);
        installBtn.setVisibility(View.GONE);
        installBtn.setOnClickListener(v -> installUpdate());
        btnRow.addView(installBtn, bLp);

        TextView pageBtn = UiTheme.makePillButton(this, "发布页", false);
        pageBtn.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(UpdaterService.RELEASE_PAGE_URL))));
        btnRow.addView(pageBtn);
        updateCard.addView(btnRow);
        root.addView(updateCard);

        TextView about = new TextView(this);
        about.setText("\n小包子影视 · 纯 Java 原生版\n端内双源直连，遵循 GPLv3 开源协议。");
        about.setTextColor(UiTheme.textDim(this));
        about.setTextSize(12);
        about.setPadding((int) (4 * dp), (int) (12 * dp), 0, 0);
        root.addView(about);

        scroll.addView(root);
        setContentView(scroll);

        checkUpdate();
    }

    private void checkUpdate() {
        updateStatusTv.setText("正在检查更新…");
        installBtn.setVisibility(View.GONE);
        new Thread(() -> {
            try {
                UpdaterService.Manifest m = UpdaterService.fetchManifest();
                latestManifest = m;
                boolean hasNew = UpdaterService.hasUpdate(this, m);
                runOnUiThread(() -> {
                    if (hasNew) {
                        updateStatusTv.setText("发现新版本 v" + m.version + "\n" + String.join("\n• ", m.notes));
                        if (m.androidUrl != null && !m.androidUrl.isEmpty()) installBtn.setVisibility(View.VISIBLE);
                    } else {
                        updateStatusTv.setText("已是最新版本");
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> updateStatusTv.setText("检查更新失败：" + e.getMessage()));
            }
        }).start();
    }

    private void installUpdate() {
        if (latestManifest == null || latestManifest.androidUrl == null) return;
        installBtn.setEnabled(false);
        installBtn.setText("正在下载…");
        new Thread(() -> {
            try {
                UpdaterService.downloadAndInstall(this, latestManifest.androidUrl);
                runOnUiThread(() -> {
                    installBtn.setEnabled(true);
                    installBtn.setText("下载并安装新版");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    installBtn.setEnabled(true);
                    installBtn.setText("下载并安装新版");
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
}
