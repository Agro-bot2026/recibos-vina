package com.recibosvina.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 🦇 Panel del PATRÓN (WebView premium)
 * Registra contratistas, genera recibos, sube recibos y ve el historial
 */
public class PatronPanelActivity extends WebViewBase {

    private static final int PICK_RECIBO = 300;
    private ValueCallback<Uri[]> filePathCallback;
    private String pendingCuil = "";
    private String pendingPeriodo = "";

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

        /** 📎 Subir recibo: abre el selector de archivo y lo envía al backend */
        @JavascriptInterface
        public void subirRecibo(String cuil, String periodo) {
            pendingCuil = cuil;
            pendingPeriodo = periodo;
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(Intent.createChooser(intent, "Elegí el recibo (foto o PDF)"), PICK_RECIBO);
            });
        }

        /** 📋 Historial de recibos del patrón */
        @JavascriptInterface
        public String verRecibosPatron() {
            final String[] result = {"[]"};
            Thread t = new Thread(() -> {
                try {
                    URL url = new URL(MainActivity.API_URL + "/api/patron/recibos?patron_id=" + getPatronId());
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
                        JSONArray arr = resp.optJSONArray("recibos");
                        result[0] = arr != null ? arr.toString() : "[]";
                    }
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(PatronPanelActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            });
            t.start();
            try { t.join(15000); } catch (InterruptedException e) {}
            return result[0];
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_RECIBO && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                InputStream is = getContentResolver().openInputStream(uri);
                byte[] bytes = new byte[is.available()];
                is.read(bytes);
                is.close();
                String b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);
                subirAlBackend(b64);
            } catch (Exception e) {
                Toast.makeText(this, "Error leyendo archivo: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void subirAlBackend(String b64) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("patron_id", getPatronId());
                body.put("contratista_cuil", pendingCuil);
                body.put("periodo", pendingPeriodo);
                body.put("archivo_base64", b64);
                URL url = new URL(MainActivity.API_URL + "/api/patron/subir_recibo");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                OutputStream os = conn.getOutputStream();
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                os.close();
                int code = conn.getResponseCode();
                InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                StringBuilder sb = new StringBuilder();
                int c;
                while ((c = is.read()) != -1) sb.append((char) c);
                JSONObject resp = new JSONObject(sb.toString());
                conn.disconnect();
                runOnUiThread(() -> {
                    if (resp.optBoolean("ok")) {
                        Toast.makeText(this, "✅ Recibo subido!", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, "❌ " + resp.optString("error"), Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }
}
