package com.recibosvina.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;

/**
 * 🦇 Panel del CONTRATISTA (WebView premium)
 * Ve recibos, sube firma (foto) y firma.
 * Ya NO manda cuil en cada request: el backend identifica al contratista
 * por el token de sesión.
 */
public class ContratistaPanelActivity extends WebViewBase {

    private static final int PICK_FIRMA = 200;

    @Override
    protected int getLayoutId() { return R.layout.activity_web; }
    @Override
    protected String getHtmlFile() { return "panel_contratista.html"; }

    @Override
    protected Object getBridge() { return new Bridge(); }

    private String getNombre() { return getSharedPreferences("recibos", MODE_PRIVATE).getString("nombre", ""); }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FIRMA && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                InputStream is = getContentResolver().openInputStream(uri);
                byte[] bytes = new byte[is.available()];
                is.read(bytes);
                is.close();
                String base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);
                new Thread(() -> {
                    try {
                        JSONObject body = new JSONObject();
                        body.put("firma_base64", base64);
                        JSONObject resp = ApiClient.post(ContratistaPanelActivity.this, "/api/contratista/firma", body, true);
                        if (resp.optBoolean("ok")) {
                            runOnUiThread(() -> {
                                Toast.makeText(ContratistaPanelActivity.this, "✅ Firma guardada!", Toast.LENGTH_SHORT).show();
                                AdHelper.showInterstitial(ContratistaPanelActivity.this, () -> {});
                            });
                        } else {
                            final String err = resp.optString("error", "Error guardando la firma");
                            runOnUiThread(() -> Toast.makeText(ContratistaPanelActivity.this, err, Toast.LENGTH_LONG).show());
                        }
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(ContratistaPanelActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                }).start();
            } catch (Exception e) {
                Toast.makeText(this, "Error leyendo la imagen: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private class Bridge extends BridgeComun {
        @JavascriptInterface
        public String getNombreContratista() { return getNombre(); }

        @JavascriptInterface
        public void subirFirma() {
            runOnUiThread(() -> {
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
                    JSONObject resp = ApiClient.get(ContratistaPanelActivity.this, "/api/contratista/recibos", true);
                    if (resp.optBoolean("ok")) {
                        JSONArray arr = resp.optJSONArray("recibos");
                        result[0] = arr != null ? arr.toString() : "[]";
                    } else {
                        final String err = resp.optString("error", "Error desconocido");
                        runOnUiThread(() -> Toast.makeText(ContratistaPanelActivity.this, err, Toast.LENGTH_LONG).show());
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
                    JSONObject resp = ApiClient.post(ContratistaPanelActivity.this, "/api/contratista/firmar", body, true);
                    resultado[0] = resp.optBoolean("ok");
                    if (!resultado[0]) {
                        final String err = resp.optString("error", "Error desconocido");
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
