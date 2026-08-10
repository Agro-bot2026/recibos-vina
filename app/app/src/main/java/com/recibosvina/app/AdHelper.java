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
    public static final String AD_UNIT_APP_OPEN = "ca-app-pub-4478373683231277/2405769075";

    private static InterstitialAd interstitial;
    private static RewardedAd rewardedAd;
    private static com.google.android.gms.ads.appopen.AppOpenAd appOpenAd;
    private static long appOpenLoadTime = 0;

    public static void init(Context context) {
        MobileAds.initialize(context, initStatus -> Log.d(TAG, "AdMob inicializado"));
        // Cargar interstitial de entrada
        loadInterstitial(context);
        // Cargar app open
        loadAppOpen(context);
        // Cargar recompensado
        loadRewarded(context);
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

    /** Muestra el anuncio recompensado (si está cargado) */
    public static void showRewarded(Activity activity, Runnable onReward) {
        if (rewardedAd != null) {
            final RewardedAd ad = rewardedAd;
            rewardedAd = null;
            ad.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    loadRewarded(activity);
                }
            });
            ad.show(activity, rewardItem -> {
                // usuario vio el anuncio → dar la recompensa
                if (onReward != null) onReward.run();
            });
        } else {
            if (onReward != null) onReward.run();
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

    /** Carga el anuncio de apertura de app (App Open) */
    public static void loadAppOpen(Context context) {
        com.google.android.gms.ads.appopen.AppOpenAd.load(context, AD_UNIT_APP_OPEN, new AdRequest.Builder().build(),
                com.google.android.gms.ads.AdRequest.APP_OPEN_AD_ORIENTATION_PORTRAIT,
                new com.google.android.gms.ads.appopen.AppOpenAd.AppOpenAdLoadCallback() {
                    @Override
                    public void onAdLoaded(com.google.android.gms.ads.appopen.AppOpenAd ad) {
                        appOpenAd = ad;
                        appOpenLoadTime = System.currentTimeMillis();
                        Log.d(TAG, "App Open cargado");
                    }

                    @Override
                    public void onAdFailedToLoad(LoadAdError error) {
                        appOpenAd = null;
                        Log.d(TAG, "App Open falló: " + error.getMessage());
                    }
                });
    }

    /** Muestra el App Open (si está cargado y es reciente, <4h) */
    public static void showAppOpen(Activity activity) {
        long now = System.currentTimeMillis();
        if (appOpenAd == null || now - appOpenLoadTime > 4 * 60 * 60 * 1000) {
            // no hay anuncio o está viejo — recargar para la próxima
            loadAppOpen(activity);
            return;
        }
        final com.google.android.gms.ads.appopen.AppOpenAd ad = appOpenAd;
        appOpenAd = null; // se consume
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                loadAppOpen(activity); // recargar para la próxima apertura
            }
        });
        ad.show(activity);
    }
}
