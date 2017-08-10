package com.topvision.videodemo;

import android.app.Application;
import android.content.Context;

import com.topvision.videodemo.util.*;

/**
 * User: jack(jackgu@topvision-cv.com)
 * Date: 2017-03-29
 * Time: 18:22
 */
public class MyApplication extends Application {
    public static Context instance ;
    static {
        System.loadLibrary("yuv_utils");
        System.loadLibrary("yuv");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = getApplicationContext();
        com.topvision.videodemo.util.CrashHandler crashHandler = com.topvision.videodemo.util.CrashHandler.getInstance();
        crashHandler.init(getApplicationContext());
    }
}