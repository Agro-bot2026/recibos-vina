package com.recibosvina.app;

import android.content.Intent;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 🦇 Registro del PATRÓN (WebView premium)
 */
public class PatronLoginActivity extends WebViewBase {

    @Override
    protected int getLayoutId() { return R.layout.activity_web; }
    @Override
    protected String getHtmlFile() { return "login_patron.html"; }

    @Override
    protected Object getBridge() { return new Bridge(); }

    private class Bridge extends BridgeComun {
        @JavascriptInterface
        public boolean registrarPatron(String nombre, String viniedo) {
            final boolean[] resultado = {false};
            Thread t = new Thread(() -> {
                try {
                    JSONObject body = new JSONObject();
                    body.put("nombre", nombre);
                    body.put("numero_viniedo", viniedo);
                    URL url = new URL(MainActivity.API_URL + "/api/patron/registrar");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setDoOutput(true);
                    OutputStream os = conn.getOutputStream();
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                    os.close();
                    int code = conn.getResponseCode();
                    java.io.InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                    StringBuilder sb = new StringBuilder();
                    int c;
                    while ((c = is.read()) != -1) sb.append((char) c);
                    JSONObject resp = new JSONObject(sb.toString());
                    conn.disconnect();
                    if (resp.optBoolean("ok")) {
                        resultado[0] = true;
                        getSharedPreferences("recibos", MODE_PRIVATE).edit()
                                .putString("rol", "patron")
                                .putInt("patron_id", resp.optInt("patron_id"))
                                .putString("nombre", nombre)
                                .apply();
                    }
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(PatronLoginActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            });
            t.start();
            try { t.join(15000); } catch (InterruptedException e) {}
            if (resultado[0]) {
                runOnUiThread(() -> startActivity(new Intent(PatronLoginActivity.this, PatronPanelActivity.class)));
            }
            return resultado[0];
        }

        /** 🔑 Login de patrón existente (viñedo + nombre) */
        @JavascriptInterface
        public boolean loginPatron(String nombre, String viniedo) {
            final boolean[] resultado = {false};
            Thread t = new Thread(() -> {
                try {
                    JSONObject body = new JSONObject();
                    body.put("nombre", nombre);
                    body.put("numero_viniedo", viniedo);
                    URL url = new URL(MainActivity.API_URL + "/api/patron/login");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setDoOutput(true);
                    OutputStream os = conn.getOutputStream();
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                    os.close();
                    int code = conn.getResponseCode();
                    java.io.InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                    StringBuilder sb = new StringBuilder();
                    int c;
                    while ((c = is.read()) != -1) sb.append((char) c);
                    JSONObject resp = new JSONObject(sb.toString());
                    conn.disconnect();
                    if (resp.optBoolean("ok")) {
                        resultado[0] = true;
                        getSharedPreferences("recibos", MODE_PRIVATE).edit()
                                .putString("rol", "patron")
                                .putInt("patron_id", resp.optInt("patron_id"))
                                .putString("nombre", resp.optString("nombre"))
                                .apply();
                    } else {
                        runOnUiThread(() -> Toast.makeText(PatronLoginActivity.this, resp.optString("error"), Toast.LENGTH_LONG).show());
                    }
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(PatronLoginActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            });
            t.start();
            try { t.join(15000); } catch (InterruptedException e) {}
            if (resultado[0]) {
                runOnUiThread(() -> startActivity(new Intent(PatronLoginActivity.this, PatronPanelActivity.class)));
            }
            return resultado[0];
        }
    }
}
