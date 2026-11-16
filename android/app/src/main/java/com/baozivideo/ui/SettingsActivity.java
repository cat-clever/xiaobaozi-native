package com.baozivideo.ui;

import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
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

public class SettingsActivity extends AppCompatActivity {
    private TextView updateStatusTv;
    private Button installBtn;
    private UpdaterService.Manifest latestManifest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        float dp = getResources().getDisplayMetrics().density;

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int) (16 * dp), (int) (16 * dp), (int) (16 * dp), (int) (24 * dp));

        Button backBtn = new Button(this);
        backBtn.setText("‹ 返回");
        backBtn.setOnClickListener(v -> finish());
        root.addView(backBtn);

        TextView title = new TextView(this);
        title.setText("设置");
        title.setTextSize(24);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, (int) (8 * dp), 0, (int) (12 * dp));
        root.addView(title);

        TextView secTheme = new TextView(this);
        secTheme.setText("外观主题");
        secTheme.setTypeface(null, Typeface.BOLD);
        root.addView(secTheme);

        RadioGroup rg = new RadioGroup(this);
        String curMode = StorageManager.getInstance().getThemeMode();
        String[][] modes = {{"system", "跟随系统"}, {"dark", "深色"}, {"light", "亮色"}};
        for (String[] m : modes) {
            RadioButton rb = new RadioButton(this);
            rb.setText(m[1]);
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
        root.addView(rg);

        Switch glassSwitch = new Switch(this);
        glassSwitch.setText("液态玻璃效果");
        glassSwitch.setChecked(StorageManager.getInstance().getGlassEnabled());
        glassSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> StorageManager.getInstance().saveGlassEnabled(isChecked));
        root.addView(glassSwitch);

        TextView secUpdate = new TextView(this);
        secUpdate.setText("版本与更新");
        secUpdate.setTypeface(null, Typeface.BOLD);
        secUpdate.setPadding(0, (int) (16 * dp), 0, (int) (6 * dp));
        root.addView(secUpdate);

        TextView curVer = new TextView(this);
        curVer.setText("当前版本：" + UpdaterService.getCurrentVersionName(this));
        root.addView(curVer);

        updateStatusTv = new TextView(this);
        updateStatusTv.setText("正在检查更新…");
        updateStatusTv.setPadding(0, (int) (6 * dp), 0, (int) (6 * dp));
        root.addView(updateStatusTv);

        Button checkBtn = new Button(this);
        checkBtn.setText("检查更新");
        checkBtn.setOnClickListener(v -> checkUpdate());
        root.addView(checkBtn);

        installBtn = new Button(this);
        installBtn.setText("下载并安装新版");
        installBtn.setVisibility(View.GONE);
        installBtn.setOnClickListener(v -> installUpdate());
        root.addView(installBtn);

        Button pageBtn = new Button(this);
        pageBtn.setText("打开发布页");
        pageBtn.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(UpdaterService.RELEASE_PAGE_URL))));
        root.addView(pageBtn);

        TextView about = new TextView(this);
        about.setText("\n小包子影视 · 纯 Java 原生版\n端内双源直连，遵循 GPLv3 开源协议。");
        about.setTextSize(12);
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
