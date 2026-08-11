package com.recibosvina.app;

import android.content.Intent;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import org.json.JSONObject;

/**
 * 🦇 Login del CONTRATISTA (WebView premium)
 * Entra con CUIL + nombre + PIN (el patrón le dio el PIN al registrarlo).
 * El JS debe llamar: loginContratista(cuil, nombre, pin)
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
        public String loginContratista(String cuil, String nombre, String pin) {
            final String[] resultado = {"false|Error desconocido"};
            Thread t = new Thread(() -> {
                try {
                    JSONObject body = new JSONObject();
                    body.put("cuil", cuil);
                    body.put("nombre", nombre);
                    body.put("pin", pin);
                    JSONObject resp = ApiClient.post(ContratistaLoginActivity.this, "/api/contratista/validar_acceso", body, false);
                    if (resp.optBoolean("ok")) {
                        ApiClient.guardarToken(ContratistaLoginActivity.this, resp.optString("token"));
                        getSharedPreferences("recibos", MODE_PRIVATE).edit()
                                .putString("rol", "contratista")
                                .putString("cuil", cuil)
                                .putString("nombre", nombre)
                                .apply();
                        resultado[0] = "true";
                    } else {
                        resultado[0] = "false|" + resp.optString("error", "No se pudo iniciar sesión");
                    }
                } catch (Exception e) {
                    resultado[0] = "false|Error de conexión: " + e.getMessage();
                }
            });
            t.start();
            try { t.join(15000); } catch (InterruptedException e) {}
            if (resultado[0].equals("true")) {
                runOnUiThread(() -> startActivity(new Intent(ContratistaLoginActivity.this, ContratistaPanelActivity.class)));
            } else {
                final String err = resultado[0].substring(resultado[0].indexOf('|') + 1);
                runOnUiThread(() -> Toast.makeText(ContratistaLoginActivity.this, err, Toast.LENGTH_LONG).show());
            }
            return resultado[0];
        }
    }
}
