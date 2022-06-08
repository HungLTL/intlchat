package com.example.intlchat;

import android.app.Application;
import android.content.res.Configuration;

import java.util.Locale;

public class ChatApplication extends Application {
    public static String sysLang;

    @Override
    public void onCreate() {
        super.onCreate();
        sysLang = Locale.getDefault().getLanguage();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        sysLang = newConfig.locale.getLanguage();
    }
}
