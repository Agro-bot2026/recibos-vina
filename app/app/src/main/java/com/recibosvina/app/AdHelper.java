package com.recibosvina.app;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

/**
 * 🦇 Recibos Viña — Helper de anuncios AdMob
 * 
 * Interstitial: se muestra al generar un recibo (patrón) o al firmar (contratista)
 * Recompensado: opcional (ej: ver anuncio para desbloquear descarga)
 * App Open: se muestra al abrir la app (ads de inicio)
 */
public class AdHelper {

    private static final String TAG = "AdHelper";
    public static final String AD_UNIT_INTERSTITIAL = "ca-app-pub-4478373683231277/6804747303";
    public static final String AD_UNIT_REWARDED = "ca-app-pub-4478373683231277/5777892085";

    private static InterstitialAd interstitial;
    private static RewardedAd rewardedAd;

    public static void init(Context context) {
        MobileAds.initialize(context, initStatus -> Log.d(TAG, "AdMob inicializado"));
        // Cargar interstitial de entrada
        loadInterstitial(context);
    }

    /** Carga un anuncio interstitial para tenerlo listo */
    public static void loadInterstitial(Context context) {
        AdRequest request = new AdRequest.Builder().build();
        InterstitialAd.load(context, AD_UNIT_INTERSTITIAL, request,
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(InterstitialAd ad) {
                        interstitial = ad;
                        Log.d(TAG, "Interstitial cargado");
                    }

                    @Override
                    public void onAdFailedToLoad(LoadAdError error) {
                        interstitial = null;
                        Log.d(TAG, "Interstitial falló: " + error.getMessage());
                    }
                });
    }

    /**
     * Muestra el anuncio interstitial (si está listo).
     * @param activity la actividad actual
     * @param onComplete callback cuando termina (o si no hay anuncio)
     */
    public static void showInterstitial(Activity activity, Runnable onComplete) {
        if (interstitial != null) {
            interstitial.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    interstitial = null;
                    loadInterstitial(activity);
                    if (onComplete != null) onComplete.run();
                }

                @Override
                public void onAdFailedToShowFullScreenContent(AdError error) {
                    interstitial = null;
                    loadInterstitial(activity);
                    if (onComplete != null) onComplete.run();
                }
            });
            interstitial.show(activity);
        } else {
            // No hay anuncio listo — seguir igual
            Log.d(TAG, "Sin interstitial listo, continuando");
            if (onComplete != null) onComplete.run();
        }
    }

    /** Carga el anuncio recompensado */
    public static void loadRewarded(Context context) {
        RewardedAd.load(context, AD_UNIT_REWARDED, new AdRequest.Builder().build(),
                new RewardedAdLoadCallback() {
                    @Override
                    public void onAdLoaded(RewardedAd ad) {
                        rewardedAd = ad;
                        Log.d(TAG, "Recompensado cargado");
                    }

                    @Override
                    public void onAdFailedToLoad(LoadAdError error) {
                        rewardedAd = null;
                        Log.d(TAG, "Recompensado falló: " + error.getMessage());
                    }
                });
    }
}
