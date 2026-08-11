package com.recibosvina.app;

import android.content.Intent;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import org.json.JSONObject;

/**
 * 🦇 Registro / login del PATRÓN (WebView premium)
 * Ahora con contraseña real — el JS debe llamar:
 *   registrarPatron(nombre, viniedo, password)
 *   loginPatron(viniedo, password)
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
        public String registrarPatron(String nombre, String viniedo, String password) {
            final String[] resultado = {"false|Error desconocido"};
            Thread t = new Thread(() -> {
                try {
                    JSONObject body = new JSONObject();
                    body.put("nombre", nombre);
                    body.put("numero_viniedo", viniedo);
                    body.put("password", password);
                    JSONObject resp = ApiClient.post(PatronLoginActivity.this, "/api/patron/registrar", body, false);
                    if (resp.optBoolean("ok")) {
                        ApiClient.guardarToken(PatronLoginActivity.this, resp.optString("token"));
                        getSharedPreferences("recibos", MODE_PRIVATE).edit()
                                .putString("rol", "patron")
                                .putInt("patron_id", resp.optInt("patron_id"))
                                .putString("nombre", nombre)
                                .apply();
                        resultado[0] = "true";
                    } else {
                        resultado[0] = "false|" + resp.optString("error", "No se pudo registrar");
                    }
                } catch (Exception e) {
                    resultado[0] = "false|Error de conexión: " + e.getMessage();
                }
            });
            t.start();
            try { t.join(15000); } catch (InterruptedException e) {}
            if (resultado[0].equals("true")) {
                runOnUiThread(() -> startActivity(new Intent(PatronLoginActivity.this, PatronPanelActivity.class)));
            } else {
                final String err = resultado[0].substring(resultado[0].indexOf('|') + 1);
                runOnUiThread(() -> Toast.makeText(PatronLoginActivity.this, err, Toast.LENGTH_LONG).show());
            }
            return resultado[0];
        }

        /** 🔑 Login de patrón existente (viñedo + contraseña) */
        @JavascriptInterface
        public String loginPatron(String viniedo, String password) {
            final String[] resultado = {"false|Error desconocido"};
            Thread t = new Thread(() -> {
                try {
                    JSONObject body = new JSONObject();
                    body.put("numero_viniedo", viniedo);
                    body.put("password", password);
                    JSONObject resp = ApiClient.post(PatronLoginActivity.this, "/api/patron/login", body, false);
                    if (resp.optBoolean("ok")) {
                        ApiClient.guardarToken(PatronLoginActivity.this, resp.optString("token"));
                        getSharedPreferences("recibos", MODE_PRIVATE).edit()
                                .putString("rol", "patron")
                                .putInt("patron_id", resp.optInt("patron_id"))
                                .putString("nombre", resp.optString("nombre"))
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
                runOnUiThread(() -> startActivity(new Intent(PatronLoginActivity.this, PatronPanelActivity.class)));
            } else {
                final String err = resultado[0].substring(resultado[0].indexOf('|') + 1);
                runOnUiThread(() -> Toast.makeText(PatronLoginActivity.this, err, Toast.LENGTH_LONG).show());
            }
            return resultado[0];
        }
    }
}
