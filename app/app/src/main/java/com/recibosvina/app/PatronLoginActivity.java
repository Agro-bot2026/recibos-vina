package com.recibosvina.app;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 🦇 Login/Registro del PATRÓN
 * Se registra con nombre + número de viñedo (la clave del contrato)
 */
public class PatronLoginActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patron_login);

        EditText etNombre = findViewById(R.id.etNombrePatron);
        EditText etViniedo = findViewById(R.id.etViniedo);
        Button btnRegistrar = findViewById(R.id.btnRegistrarPatron);

        btnRegistrar.setOnClickListener(v -> {
            String nombre = etNombre.getText().toString().trim();
            String viniedo = etViniedo.getText().toString().trim();
            if (nombre.isEmpty() || viniedo.isEmpty()) {
                Toast.makeText(this, "Completá nombre y número de viñedo", Toast.LENGTH_SHORT).show();
                return;
            }
            new Thread(() -> {
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

                    runOnUiThread(() -> {
                        if (resp.optBoolean("ok")) {
                            Toast.makeText(this, "✅ Patrón registrado! N° " + resp.optString("patron_id"), Toast.LENGTH_LONG).show();
                            // Guardar id y pasar a la pantalla del patrón
                            getSharedPreferences("recibos", MODE_PRIVATE).edit()
                                    .putString("rol", "patron")
                                    .putInt("patron_id", resp.optInt("patron_id"))
                                    .putString("nombre", nombre)
                                    .apply();
                            startActivity(new android.content.Intent(this, PatronPanelActivity.class));
                        } else {
                            Toast.makeText(this, "❌ " + resp.optString("error"), Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(this, "Error de conexión: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            }).start();
        });
    }
}
