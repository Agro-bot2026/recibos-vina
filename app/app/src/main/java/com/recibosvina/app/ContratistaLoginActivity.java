package com.recibosvina.app;

import android.content.Intent;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * 🦇 Login del CONTRATISTA (WebView premium)
 * Entra con CUIL + nombre (los registró su patrón)
 */
public class ContratistaLoginActivity extends WebViewBase {

    @Override
    protected int getLayoutId() { return R.layout.activity_web; }
    @Override
    protected String getHtmlFile() { return "login_contratista.html"; }

    @Override
    protected Object getBridge() { return new Bridge(); }

    private class Bridge extends BridgeComun {
        @JavascriptInterface
        public boolean loginContratista(String cuil, String nombre) {
            final boolean[] resultado = {false};
            final String[] error = {""};
            Thread t = new Thread(() -> {
                try {
                    URL url = new URL(MainActivity.API_URL + "/api/contratista/recibos?cuil=" + URLEncoder.encode(cuil, "UTF-8") + "&nombre=" + URLEncoder.encode(nombre, "UTF-8"));
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    int code = conn.getResponseCode();
                    InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                    StringBuilder sb = new StringBuilder();
                    int c;
                    while ((c = is.read()) != -1) sb.append((char) c);
                    JSONObject resp = new JSONObject(sb.toString());
                    conn.disconnect();
                    if (resp.optBoolean("ok")) {
                        resultado[0] = true;
                        getSharedPreferences("recibos", MODE_PRIVATE).edit()
                                .putString("rol", "contratista")
                                .putString("cuil", cuil)
                                .putString("nombre", nombre)
                                .apply();
                    } else {
                        error[0] = resp.optString("error");
                    }
                } catch (Exception e) {
                    error[0] = e.getMessage();
                }
            });
            t.start();
            try { t.join(15000); } catch (InterruptedException e) {}
            if (!resultado[0] && !error[0].isEmpty()) {
                runOnUiThread(() -> Toast.makeText(ContratistaLoginActivity.this, error[0], Toast.LENGTH_LONG).show());
            }
            if (resultado[0]) {
                runOnUiThread(() -> startActivity(new Intent(ContratistaLoginActivity.this, ContratistaPanelActivity.class)));
            }
            return resultado[0];
        }
    }
}
