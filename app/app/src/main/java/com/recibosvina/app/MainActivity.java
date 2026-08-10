package com.recibosvina.app;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 🦇 ViñaRecibos — Pantalla principal con WebView
 * Carga el diseño premium (index.html) y expone el bridge para:
 *   - getDeviceModel(): muestra el modelo del celular
 *   - openPatron() / openContratista(): navega a las pantallas
 */
public class MainActivity extends AppCompatActivity {

    public static final String API_URL = "http://157.250.202.243:8400";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicializar AdMob
        AdHelper.init(this);

        WebView webView = findViewById(R.id.webView);
        webView.setWebViewClient(new WebViewClient());
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.addJavascriptInterface(new Bridge(), "AndroidBridge");
        webView.loadUrl("file:///android_asset/index.html");
    }

    private class Bridge {
        /** Devuelve el modelo del dispositivo (ej: "TCL 20 SE") */
        @JavascriptInterface
        public String getDeviceModel() {
            String manufacturer = Build.MANUFACTURER;
            String model = Build.MODEL;
            // "TCL 20 SE" en vez de "TCL 20 SE (T671E)" si es muy largo
            return manufacturer + " " + model;
        }

        @JavascriptInterface
        public void openPatron() {
            startActivity(new android.content.Intent(MainActivity.this, PatronLoginActivity.class));
        }

        @JavascriptInterface
        public void openContratista() {
            startActivity(new android.content.Intent(MainActivity.this, ContratistaLoginActivity.class));
        }
    }

    @Override
    public void onBackPressed() {
        WebView webView = findViewById(R.id.webView);
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
