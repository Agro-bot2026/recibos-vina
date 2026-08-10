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
 * 🦇 Panel del PATRÓN
 * Registra contratistas (CUIL + nombre) y sube recibos
 */
public class PatronPanelActivity extends AppCompatActivity {

    private int patronId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patron_panel);

        patronId = getSharedPreferences("recibos", MODE_PRIVATE).getInt("patron_id", 0);
        String nombre = getSharedPreferences("recibos", MODE_PRIVATE).getString("nombre", "");

        EditText etCuil = findViewById(R.id.etCuilContratista);
        EditText etNombre = findViewById(R.id.etNombreContratistaNuevo);
        Button btnRegistrarContratista = findViewById(R.id.btnRegistrarContratista);

        EditText etPeriodo = findViewById(R.id.etPeriodo);
        EditText etConcepto = findViewById(R.id.etConcepto);
        EditText etRemunerativo = findViewById(R.id.etRemunerativo);
        EditText etNoRemunerativo = findViewById(R.id.etNoRemunerativo);
        Button btnGenerarRecibo = findViewById(R.id.btnGenerarRecibo);

        setTitle("🍇 Patrón: " + nombre);

        // Registrar contratista
        btnRegistrarContratista.setOnClickListener(v -> {
            String cuil = etCuil.getText().toString().trim();
            String nom = etNombre.getText().toString().trim();
            if (cuil.isEmpty() || nom.isEmpty()) {
                Toast.makeText(this, "Completá CUIL y nombre del contratista", Toast.LENGTH_SHORT).show();
                return;
            }
            new Thread(() -> {
                try {
                    JSONObject body = new JSONObject();
                    body.put("patron_id", patronId);
                    body.put("cuil", cuil);
                    body.put("nombre", nom);
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
                    runOnUiThread(() -> {
                        if (resp.optBoolean("ok")) {
                            Toast.makeText(this, "✅ Contratista registrado!", Toast.LENGTH_LONG).show();
                            etCuil.setText(""); etNombre.setText("");
                        } else {
                            Toast.makeText(this, "❌ " + resp.optString("error"), Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            }).start();
        });

        // Generar recibo (el backend genera el PDF formato 407/2026)
        btnGenerarRecibo.setOnClickListener(v -> {
            String cuil = etCuil.getText().toString().trim();
            String periodo = etPeriodo.getText().toString().trim();
            String concepto = etConcepto.getText().toString().trim();
            String rem = etRemunerativo.getText().toString().trim();
            String noRem = etNoRemunerativo.getText().toString().trim();
            if (cuil.isEmpty() || periodo.isEmpty() || rem.isEmpty()) {
                Toast.makeText(this, "Completá CUIL, período y remunerativo", Toast.LENGTH_SHORT).show();
                return;
            }
            new Thread(() -> {
                try {
                    JSONObject body = new JSONObject();
                    body.put("patron_id", patronId);
                    body.put("contratista_cuil", cuil);
                    body.put("periodo", periodo);
                    body.put("concepto", concepto.isEmpty() ? "HAS EN PRODUCCIÓN" : concepto);
                    body.put("remunerativo", Double.parseDouble(rem.replace(".", "").replace(",", ".")));
                    if (!noRem.isEmpty()) body.put("no_remunerativo", Double.parseDouble(noRem.replace(".", "").replace(",", ".")));
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
                    runOnUiThread(() -> {
                        if (resp.optBoolean("ok")) {
                            Toast.makeText(this, "✅ Recibo generado! Se lo avisamos al contratista", Toast.LENGTH_LONG).show();
                            etPeriodo.setText(""); etRemunerativo.setText("");
                        } else {
                            Toast.makeText(this, "❌ " + resp.optString("error"), Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            }).start();
        });
    }
}
