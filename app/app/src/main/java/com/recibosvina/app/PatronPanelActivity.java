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
    private String ultimoBase64 = null;
    private String ultimoNombre = "recibo";
    private String ultimoPeriodo = "";
    private org.json.JSONObject ultimosDatos = null;

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

        /** 📎 Elegir recibo: abre el selector, hace OCR y devuelve los datos */
        @JavascriptInterface
        public String leerRecibo() {
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(Intent.createChooser(intent, "Elegí el recibo (foto o PDF)"), PICK_RECIBO);
            });
            // El resultado llega por onActivityResult → procesarReciboElegido()
            return "{\"ok\":true,\"elegido\":true}";
        }

        /** 💾 Guardar el recibo tal cual (el archivo ya quedó en memoria) */
        @JavascriptInterface
        public boolean guardarReciboTalCual(String cuil, String periodo) {
            final boolean[] resultado = {false};
            if (ultimoBase64 == null) return false;
            final String b64 = ultimoBase64;
            final String per = periodo != null && !periodo.isEmpty() ? periodo : ultimoPeriodo;
            Thread t = new Thread(() -> {
                try {
                    JSONObject body = new JSONObject();
                    body.put("patron_id", getPatronId());
                    body.put("contratista_cuil", cuil);
                    body.put("periodo", per);
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
                    resultado[0] = resp.optBoolean("ok");
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(PatronPanelActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            });
            t.start();
            try { t.join(20000); } catch (InterruptedException e) {}
            return resultado[0];
        }

        /** ✨ Convertir al formato nuevo: usa los datos (corregidos) del recibo viejo */
        @JavascriptInterface
        public String convertirRecibo(String cuil, String periodo, String rem, String norem, String concepto) {
            final String[] resultado = {"false"};
            Thread t = new Thread(() -> {
                try {
                    JSONObject body = new JSONObject();
                    body.put("patron_id", getPatronId());
                    body.put("contratista_cuil", cuil);
                    body.put("periodo", periodo);
                    body.put("concepto", concepto.isEmpty() ? "HAS EN PRODUCCIÓN" : concepto);
                    double r = 0, nr = 0;
                    try { r = Double.parseDouble(rem.replace(".", "").replace(",", ".")); } catch (Exception e) {}
                    try { nr = Double.parseDouble(norem.replace(".", "").replace(",", ".")); } catch (Exception e) {}
                    body.put("remunerativo", r);
                    body.put("no_remunerativo", nr);
                    URL url = new URL(MainActivity.API_URL + "/api/patron/generar_recibo");
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
                    if (resp.optBoolean("ok")) {
                        resultado[0] = "true";
                        runOnUiThread(() -> AdHelper.showInterstitial(PatronPanelActivity.this, () -> {}));
                    } else {
                        // Devolver el error real del backend (ej: "Contratista no encontrado")
                        resultado[0] = "false|" + resp.optString("error", "Error desconocido");
                    }
                } catch (Exception e) {
                    resultado[0] = "false|" + e.getMessage();
                }
            });
            t.start();
            try { t.join(25000); } catch (InterruptedException e) {}
            return resultado[0];
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
                ultimoBase64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);
                ultimoNombre = getFileName(uri);
                // Llamar al OCR del backend
                new Thread(() -> {
                    try {
                        JSONObject body = new JSONObject();
                        body.put("archivo_base64", ultimoBase64);
                        body.put("nombre_archivo", ultimoNombre);
                        URL url = new URL(MainActivity.API_URL + "/api/patron/leer_recibo");
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("POST");
                        conn.setRequestProperty("Content-Type", "application/json");
                        conn.setDoOutput(true);
                        OutputStream os = conn.getOutputStream();
                        os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                        os.close();
                        int code = conn.getResponseCode();
                        InputStream is2 = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                        StringBuilder sb = new StringBuilder();
                        int c;
                        while ((c = is2.read()) != -1) sb.append((char) c);
                        JSONObject resp = new JSONObject(sb.toString());
                        conn.disconnect();
                        if (resp.optBoolean("ok")) {
                            ultimosDatos = resp.optJSONObject("datos");
                            if (ultimosDatos == null) ultimosDatos = new JSONObject();
                            // Enviar los datos al JS
                            runOnUiThread(() -> {
                                String json = ultimosDatos.toString().replace("'", "\\'");
                                webView.evaluateJavascript("reciboLeido('" + json + "')", null);
                            });
                        } else {
                            runOnUiThread(() -> Toast.makeText(this, "No pude leer: " + resp.optString("error"), Toast.LENGTH_LONG).show());
                        }
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(this, "Error OCR: " + e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                }).start();
            } catch (Exception e) {
                Toast.makeText(this, "Error leyendo archivo: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private String getFileName(Uri uri) {
        String name = "recibo";
        try {
            android.database.Cursor cur = getContentResolver().query(uri, null, null, null, null);
            if (cur != null && cur.moveToFirst()) {
                int idx = cur.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = cur.getString(idx);
                cur.close();
            }
        } catch (Exception e) {}
        return name;
    }
}