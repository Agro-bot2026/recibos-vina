package com.recibosvina.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
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
 * 🦇 Panel del CONTRATISTA (WebView premium)
 * Ve recibos, sube firma (foto) y firma
 */
public class ContratistaPanelActivity extends WebViewBase {

    private static final int PICK_FIRMA = 200;
    private ValueCallback<Uri[]> filePathCallback;

    @Override
    protected int getLayoutId() { return R.layout.activity_web; }
    @Override
    protected String getHtmlFile() { return "panel_contratista.html"; }

    @Override
    protected Object getBridge() { return new Bridge(); }

    private String getCuil() { return getSharedPreferences("recibos", MODE_PRIVATE).getString("cuil", ""); }
    private String getNombre() { return getSharedPreferences("recibos", MODE_PRIVATE).getString("nombre", ""); }

    /** Permitir elegir imagen para la firma desde el WebView */
    private void setupFileChooser() {
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                ContratistaPanelActivity.this.filePathCallback = filePathCallback;
                Intent intent = fileChooserParams.createIntent();
                startActivityForResult(intent, PICK_FIRMA);
                return true;
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FIRMA) {
            if (filePathCallback != null) {
                Uri[] results = null;
                if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                    results = new Uri[]{data.getData()};
                }
                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            }
        }
    }

    private class Bridge extends BridgeComun {
        @JavascriptInterface
        public String getNombreContratista() { return getNombre(); }

        @JavascriptInterface
        public void subirFirma() {
            runOnUiThread(() -> {
                setupFileChooser();
                // Abrir selector de imagen
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                startActivityForResult(intent, PICK_FIRMA);
            });
        }

        @JavascriptInterface
        public String verRecibos() {
            final String[] result = {"[]"};
            Thread t = new Thread(() -> {
                try {
                    URL url = new URL(MainActivity.API_URL + "/api/contratista/recibos?cuil=" + URLEncoder.encode(getCuil(), "UTF-8") + "&nombre=" + URLEncoder.encode(getNombre(), "UTF-8"));
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
                    runOnUiThread(() -> Toast.makeText(ContratistaPanelActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            });
            t.start();
            try { t.join(15000); } catch (InterruptedException e) {}
            return result[0];
        }

        @JavascriptInterface
        public boolean firmarRecibo(int reciboId) {
            final boolean[] resultado = {false};
            Thread t = new Thread(() -> {
                try {
                    JSONObject body = new JSONObject();
                    body.put("recibo_id", reciboId);
                    body.put("cuil", getCuil());
                    URL url = new URL(MainActivity.API_URL + "/api/contratista/firmar");
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
                    if (!resultado[0] && resp.has("error")) {
                        final String err = resp.optString("error");
                        runOnUiThread(() -> Toast.makeText(ContratistaPanelActivity.this, err, Toast.LENGTH_LONG).show());
                    }
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(ContratistaPanelActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            });
            t.start();
            try { t.join(15000); } catch (InterruptedException e) {}
            return resultado[0];
        }
    }
}
