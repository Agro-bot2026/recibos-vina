package com.recibosvina.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 🦇 Panel del CONTRATISTA
 * Ve sus recibos, sube su firma (foto) y firma
 */
public class ContratistaPanelActivity extends AppCompatActivity {

    private static final int PICK_FIRMA = 100;
    private String cuil;
    private String nombre;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contratista_panel);

        cuil = getSharedPreferences("recibos", MODE_PRIVATE).getString("cuil", "");
        nombre = getSharedPreferences("recibos", MODE_PRIVATE).getString("nombre", "");
        setTitle("✍️ " + nombre);

        Button btnSubirFirma = findViewById(R.id.btnSubirFirma);
        Button btnVerRecibos = findViewById(R.id.btnVerRecibos);
        TextView tvRecibos = findViewById(R.id.tvRecibos);

        // Subir firma (foto del papel)
        btnSubirFirma.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            startActivityForResult(intent, PICK_FIRMA);
        });

        // Ver recibos
        btnVerRecibos.setOnClickListener(v -> {
            new Thread(() -> {
                try {
                    URL url = new URL(MainActivity.API_URL + "/api/contratista/recibos?cuil=" + java.net.URLEncoder.encode(cuil, "UTF-8") + "&nombre=" + java.net.URLEncoder.encode(nombre, "UTF-8"));
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    int code = conn.getResponseCode();
                    InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                    StringBuilder sb = new StringBuilder();
                    int c;
                    while ((c = is.read()) != -1) sb.append((char) c);
                    JSONObject resp = new JSONObject(sb.toString());
                    conn.disconnect();

                    runOnUiThread(() -> {
                        if (!resp.optBoolean("ok")) {
                            Toast.makeText(this, "❌ " + resp.optString("error"), Toast.LENGTH_LONG).show();
                            return;
                        }
                        StringBuilder txt = new StringBuilder("📄 MIS RECIBOS:\n\n");
                        JSONArray arr = resp.optJSONArray("recibos");
                        if (arr != null && arr.length() > 0) {
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject r = arr.optJSONObject(i);
                                String estado = r.optInt("firmado") == 1 ? "✅ Firmado" : "⏳ Sin firmar";
                                txt.append("• ").append(r.optString("periodo")).append(" — ").append(estado).append("\n");
                            }
                        } else {
                            txt.append("No tenés recibos todavía.");
                        }
                        tvRecibos.setText(txt.toString());
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            }).start();
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FIRMA && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            try {
                InputStream is = getContentResolver().openInputStream(uri);
                byte[] bytes = new byte[is.available()];
                is.read(bytes);
                is.close();
                String b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);
                subirFirma(b64);
            } catch (Exception e) {
                Toast.makeText(this, "Error leyendo imagen: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void subirFirma(String b64) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("cuil", cuil);
                body.put("firma_base64", b64);
                URL url = new URL(MainActivity.API_URL + "/api/contratista/firma");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                java.io.OutputStream os = conn.getOutputStream();
                os.write(body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
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
                        Toast.makeText(this, "✅ Firma guardada! Ya podés firmar tus recibos", Toast.LENGTH_LONG).show();
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
