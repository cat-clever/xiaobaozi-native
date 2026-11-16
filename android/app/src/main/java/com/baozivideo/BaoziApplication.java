package com.baozivideo;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;
import com.baozivideo.utils.StorageManager;

public class BaoziApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        StorageManager.init(this);
        applyThemeMode(StorageManager.getInstance().getThemeMode());
    }

    public static void applyThemeMode(String mode) {
        if ("dark".equals(mode)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else if ("light".equals(mode)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }
}
