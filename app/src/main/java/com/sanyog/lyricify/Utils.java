package com.sanyog.lyricify;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.preference.PreferenceManager;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import com.sanyog.lyricify.BuildConfig;

public class Utils {

    static final String SPOTIFY_PACKAGE = "com.spotify.music";

    public static boolean openApp(Context context, String packageName) {
        PackageManager manager = context.getPackageManager();
        try {
            Intent i = manager.getLaunchIntentForPackage(packageName);
            if (i == null) {
                return false;
            }
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            context.startActivity(i);
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }

    public static String getTimeStamp(long millis) {
        // long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        long seconds = (millis / 1000) % 60;
        return String.format(Locale.ENGLISH, "%02d:%02d", minutes, seconds);
    }

    public static String getTimeStampFromDate(long timeStamp) {
        Date date = new Date(timeStamp);
        @SuppressLint("SimpleDateFormat") DateFormat formatter = new SimpleDateFormat("HH:mm:ss");
        formatter.setTimeZone(TimeZone.getDefault());
        return formatter.format(date);
    }

}
