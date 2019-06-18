package cz.lastaapps.brainsize;

import android.app.Application;
import android.content.Context;

import com.google.firebase.analytics.FirebaseAnalytics;

public class App extends Application {
    private static Application app;
    private static FirebaseAnalytics analytics;

    public static Application getApp() {
        return app;
    }

    public static Context getAppContext() {
        return getApp().getApplicationContext();
    }

    public static FirebaseAnalytics getAnalytics() {
        return analytics;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        app = this;

        analytics = FirebaseAnalytics.getInstance(this);
    }


}
