package com.recibosvina.app;

import android.webkit.JavascriptInterface;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 🦇 Panel del PATRÓN (WebView premium)
 * Registra contratistas y genera recibos
 */
public class PatronPanelActivity extends WebViewBase {

    @Override
    protected int getLayoutId() { return R.layout.activity_web; }
    @Override
    protected String getHtmlFile() { return "panel_patron.html"; }

    @Override
    protected Object getBridge() { return new Bridge(); }

    private int getPatronId() {
        return getSharedPreferences("recibos", MODE_PRIVATE).getInt("patron_id", 0);
    }

    private class Bridge extends BridgeComun {
        @JavascriptInterface
        public boolean registrarContratista(String cuil, String nombre) {
            final boolean[] resultado = {false};
            Thread t = new Thread(() -> {
                try {
                    JSONObject body = new JSONObject();
                    body.put("patron_id", getPatronId());
                    body.put("cuil", cuil);
                    body.put("nombre", nombre);
                    URL url = new URL(MainActivity.API_URL + "/api/patron/contratista");
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
                    resultado[0] = resp.optBoolean("ok");
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(PatronPanelActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            });
            t.start();
            try { t.join(15000); } catch (InterruptedException e) {}
            return resultado[0];
        }

        @JavascriptInterface
        public boolean generarRecibo(String cuil, String periodo, String concepto, String rem) {
            final boolean[] resultado = {false};
            Thread t = new Thread(() -> {
                try {
                    JSONObject body = new JSONObject();
                    body.put("patron_id", getPatronId());
                    body.put("contratista_cuil", cuil);
                    body.put("periodo", periodo);
                    body.put("concepto", concepto);
                    body.put("remunerativo", Double.parseDouble(rem.replace(".", "").replace(",", ".")));
                    URL url = new URL(MainActivity.API_URL + "/api/patron/generar_recibo");
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
                    resultado[0] = resp.optBoolean("ok");
                    if (resultado[0]) {
                        // 📢 Anuncio al generar el recibo
                        runOnUiThread(() -> AdHelper.showInterstitial(PatronPanelActivity.this, () -> {}));
                    }
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(PatronPanelActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            });
            t.start();
            try { t.join(20000); } catch (InterruptedException e) {}
            return resultado[0];
        }
    }
}
