package com.recibosvina.app;

/**
 * 🦇 Política de privacidad — pantalla dedicada (accesible desde toda la app)
 */
public class PrivacyActivity extends WebViewBase {

    @Override
    protected int getLayoutId() { return R.layout.activity_web; }
    @Override
    protected String getHtmlFile() { return "privacidad.html"; }
    @Override
    protected Object getBridge() { return new BridgeComun(); }
}
