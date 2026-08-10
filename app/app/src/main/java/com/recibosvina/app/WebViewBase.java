package com.recibosvina.app;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 🦇 ViñaRecibos — Base para pantallas con WebView (mismo estilo premium)
 */
public abstract class WebViewBase extends AppCompatActivity {

    protected WebView webView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getLayoutId());
        webView = findViewById(R.id.webView);
        webView.setWebViewClient(new WebViewClient());
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        // Cada pantalla registra su bridge
        webView.addJavascriptInterface(getBridge(), "AndroidBridge");
        webView.loadUrl("file:///android_asset/" + getHtmlFile());
    }

    protected abstract int getLayoutId();
    protected abstract String getHtmlFile();
    protected abstract Object getBridge();

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    /** Bridge común: volver e ir al inicio (lo usan todas las pantallas) */
    protected class BridgeComun {
        @JavascriptInterface
        public void goBack() {
            finish();
        }

        @JavascriptInterface
        public void goHome() {
            Intent i = new Intent(WebViewBase.this, MainActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
            finish();
        }
    }
}
