package de.grit.teaser;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private WebView web;
    private boolean loaded = false;
    private int pendingMonth = 0;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.parseColor("#F7EEF0"));
        getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#F7EEF0"));
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (u.toString().startsWith(BuildConfig.SITE)) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                loaded = true;
                openPendingMonth();
            }
        });

        pendingMonth = getIntent().getIntExtra("monat", 0);
        if (BuildConfig.APP_KEY.isEmpty()) {
            web.loadData("<p style='font:18px sans-serif;padding:24px'>Diese App wurde ohne Schlüssel gebaut.</p>", "text/html", "utf-8");
        } else {
            web.loadUrl(BuildConfig.SITE + "#app~" + BuildConfig.APP_KEY + (BuildConfig.TEST ? "~test" : ""));
        }

        Teasers.schedule(this);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        pendingMonth = intent.getIntExtra("monat", 0);
        if (loaded) openPendingMonth();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Grit-App: was sie beim Öffnen sieht, gilt als gesehen (keine doppelte Meldung).
        // Test-App: meldet neue Teaser auch beim Öffnen, damit du nicht 15 Minuten warten musst.
        new Thread(() -> {
            try { Teasers.check(this, BuildConfig.TEST); } catch (Exception ignored) { }
        }).start();
    }

    private void openPendingMonth() {
        if (pendingMonth > 0) {
            web.evaluateJavascript("location.hash='#monat-" + pendingMonth + "'", null);
            pendingMonth = 0;
        }
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
}
