package com.recibosvina.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

/**
 * 🦇 ViñaRecibos — Helper de biometría (huella dactilar)
 * 
 * Flujo: el contratista entra con CUIL+nombre una vez → se guarda.
 * Después puede entrar con la huella (Android verifica la identidad del
 * dueño del teléfono y la app usa el CUIL guardado).
 */
public class BiometricHelper {

    /**
     * Verifica si el dispositivo tiene biometría disponible.
     */
    public static boolean disponible(Context context) {
        BiometricManager bm = BiometricManager.from(context);
        int canAuth = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK);
        return canAuth == BiometricManager.BIOMETRIC_SUCCESS;
    }

    /**
     * Muestra el diálogo de huella dactilar.
     * @param activity actividad (debe ser FragmentActivity)
     * @param onSuccess callback al autenticar con la huella
     */
    public static void autenticar(FragmentActivity activity, Runnable onSuccess) {
        SharedPreferences prefs = activity.getSharedPreferences("recibos", Context.MODE_PRIVATE);
        String cuilGuardado = prefs.getString("cuil", "");
        String nombreGuardado = prefs.getString("nombre", "");

        if (!disponible(activity)) {
            Toast.makeText(activity, "Tu celular no tiene huella configurada", Toast.LENGTH_LONG).show();
            return;
        }
        if (cuilGuardado.isEmpty()) {
            Toast.makeText(activity, "Primero entrá una vez con tu CUIL y nombre", Toast.LENGTH_LONG).show();
            return;
        }

        BiometricPrompt.PromptInfo prompt = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Acceso con huella")
                .setSubtitle("Usá tu huella para entrar como " + nombreGuardado)
                .setNegativeButtonText("Cancelar")
                .build();

        BiometricPrompt bp = new BiometricPrompt((FragmentActivity) activity,
                ContextCompat.getMainExecutor(activity),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        activity.runOnUiThread(onSuccess);
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        Toast.makeText(activity, "Huella cancelada", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        Toast.makeText(activity, "Huella no reconocida, intentá de nuevo", Toast.LENGTH_SHORT).show();
                    }
                });
        bp.authenticate(prompt);
    }
}
