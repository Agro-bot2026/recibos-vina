package com.recibosvina.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 🦇 ViñaRecibos — Cliente HTTP centralizado
 * Agrega automáticamente el header "Authorization: Bearer <token>" a cada
 * request que lo necesite, y guarda/lee el token de SharedPreferences.
 * Uso siempre desde un hilo de fondo (no en el hilo principal).
 */
public class ApiClient {

    public static void guardarToken(Context ctx, String token) {
        ctx.getSharedPreferences("recibos", Context.MODE_PRIVATE)
                .edit().putString("token", token).apply();
    }

    public static String getToken(Context ctx) {
        return ctx.getSharedPreferences("recibos", Context.MODE_PRIVATE).getString("token", "");
    }

    public static void cerrarSesion(Context ctx) {
        ctx.getSharedPreferences("recibos", Context.MODE_PRIVATE).edit().clear().apply();
    }

    /** POST con JSON. Si requiereAuth=true, agrega el Bearer token guardado. */
    public static JSONObject post(Context ctx, String endpoint, JSONObject body, boolean requiereAuth) throws Exception {
        URL url = new URL(MainActivity.API_URL + endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        if (requiereAuth) {
            conn.setRequestProperty("Authorization", "Bearer " + getToken(ctx));
        }
        conn.setDoOutput(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        OutputStream os = conn.getOutputStream();
        os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        os.close();
        int code = conn.getResponseCode();
        InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = is.read()) != -1) sb.append((char) c);
        conn.disconnect();
        return new JSONObject(sb.toString());
    }

    /** GET. Si requiereAuth=true, agrega el Bearer token guardado. */
    public static JSONObject get(Context ctx, String endpoint, boolean requiereAuth) throws Exception {
        URL url = new URL(MainActivity.API_URL + endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        if (requiereAuth) {
            conn.setRequestProperty("Authorization", "Bearer " + getToken(ctx));
        }
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        int code = conn.getResponseCode();
        InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = is.read()) != -1) sb.append((char) c);
        conn.disconnect();
        return new JSONObject(sb.toString());
    }
}
