package com.recibosvina.app;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 🦇 ViñaRecibos — Pantalla principal con WebView
 */
public class MainActivity extends AppCompatActivity {

    // 🔐 Contabo con HTTPS (antes: http://157.250.202.243:8400)
    public static final String API_URL = "https://recibos.charly-tricks.dev";
    private boolean appOpenIntentado = false;
    private WebView webView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        AdHelper.init(this);

        WebView webView = findViewById(R.id.webView);
        this.webView = webView;
        webView.setWebViewClient(new WebViewClient());
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.addJavascriptInterface(new Bridge(), "AndroidBridge");
        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!appOpenIntentado) {
            appOpenIntentado = true;
            webView.postDelayed(() -> AdHelper.showAppOpen(this), 3000);
        }
    }

    private class Bridge {
        @JavascriptInterface
        public String getDeviceModel() {
            String manufacturer = Build.MANUFACTURER;
            String model = Build.MODEL;
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

        /** 👆 Huella dactilar: entra directo con la sesión guardada (CUIL + token) */
        @JavascriptInterface
        public void biometricAuth() {
            runOnUiThread(() -> BiometricHelper.autenticar(MainActivity.this, () -> {
                SharedPreferences prefs = getSharedPreferences("recibos", MODE_PRIVATE);
                String cuil = prefs.getString("cuil", "");
                String token = prefs.getString("token", "");
                if (cuil.isEmpty() || token.isEmpty()) {
                    Toast.makeText(MainActivity.this, "Primero entrá una vez con CUIL, nombre y PIN", Toast.LENGTH_LONG).show();
                    return;
                }
                Toast.makeText(MainActivity.this, "✅ Huella OK! Entrando...", Toast.LENGTH_SHORT).show();
                startActivity(new android.content.Intent(MainActivity.this, ContratistaPanelActivity.class));
            }));
        }

        @JavascriptInterface
        public void openPrivacy() {
            startActivity(new android.content.Intent(MainActivity.this, PrivacyActivity.class));
        }

        @JavascriptInterface
        public void openHelp() {
            startActivity(new android.content.Intent(MainActivity.this, HelpActivity.class));
        }

        @JavascriptInterface
        public void openAdSettings() {
            android.content.Intent i = new android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://adssettings.google.com"));
            try { startActivity(i); } catch (Exception e) {}
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
