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
 * 🦇 Login del CONTRATISTA
 * Entra con CUIL + nombre (los registró su patrón)
 */
public class ContratistaLoginActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contratista_login);

        EditText etCuil = findViewById(R.id.etCuil);
        EditText etNombre = findViewById(R.id.etNombreContratista);
        Button btnEntrar = findViewById(R.id.btnEntrarContratista);

        btnEntrar.setOnClickListener(v -> {
            String cuil = etCuil.getText().toString().trim();
            String nombre = etNombre.getText().toString().trim();
            if (cuil.isEmpty() || nombre.isEmpty()) {
                Toast.makeText(this, "Completá tu CUIL y nombre", Toast.LENGTH_SHORT).show();
                return;
            }
            new Thread(() -> {
                try {
                    URL url = new URL(MainActivity.API_URL + "/api/contratista/recibos?cuil=" + java.net.URLEncoder.encode(cuil, "UTF-8") + "&nombre=" + java.net.URLEncoder.encode(nombre, "UTF-8"));
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    int code = conn.getResponseCode();
                    java.io.InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                    StringBuilder sb = new StringBuilder();
                    int c;
                    while ((c = is.read()) != -1) sb.append((char) c);
                    JSONObject resp = new JSONObject(sb.toString());
                    conn.disconnect();

                    runOnUiThread(() -> {
                        if (resp.optBoolean("ok")) {
                            getSharedPreferences("recibos", MODE_PRIVATE).edit()
                                    .putString("rol", "contratista")
                                    .putString("cuil", cuil)
                                    .putString("nombre", nombre)
                                    .apply();
                            Toast.makeText(this, "✅ Bienvenido " + nombre + "!", Toast.LENGTH_SHORT).show();
                            startActivity(new android.content.Intent(this, ContratistaPanelActivity.class));
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
