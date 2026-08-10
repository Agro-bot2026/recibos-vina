package com.recibosvina.app;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 🦇 Recibos Viña — Pantalla principal
 * Elegís el rol: Patrón (sube recibos) o Contratista (los firma)
 */
public class MainActivity extends AppCompatActivity {

    public static final String API_URL = "http://157.250.202.243:8400";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicializar AdMob y precargar anuncios
        AdHelper.init(this);

        Button btnPatron = findViewById(R.id.btnPatron);
        Button btnContratista = findViewById(R.id.btnContratista);

        btnPatron.setOnClickListener(v -> {
            // Ir al login de patrón
            startActivity(new android.content.Intent(this, PatronLoginActivity.class));
        });

        btnContratista.setOnClickListener(v -> {
            // Ir al login de contratista
            startActivity(new android.content.Intent(this, ContratistaLoginActivity.class));
        });
    }
}
