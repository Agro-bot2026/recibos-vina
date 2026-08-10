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

    @Override
    protected void onResume() {
        super.onResume();
        // 📢 Anuncio de apertura (App Open) al volver a la app
        AdHelper.showAppOpen(this);
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

        /** 👆 Huella dactilar: entra directo con el CUIL guardado */
        @JavascriptInterface
        public void biometricAuth() {
            runOnUiThread(() -> BiometricHelper.autenticar(MainActivity.this, () -> {
                SharedPreferences prefs = getSharedPreferences("recibos", MODE_PRIVATE);
                String cuil = prefs.getString("cuil", "");
                if (cuil.isEmpty()) {
                    Toast.makeText(MainActivity.this, "Primero entrá una vez con CUIL y nombre", Toast.LENGTH_LONG).show();
                    return;
                }
                Toast.makeText(MainActivity.this, "✅ Huella OK! Entrando...", Toast.LENGTH_SHORT).show();
                startActivity(new android.content.Intent(MainActivity.this, ContratistaPanelActivity.class));
            }));
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
