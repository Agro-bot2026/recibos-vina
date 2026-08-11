package com.recibosvina.app;

/**
 * 🦇 Centro de ayuda — pantalla con preguntas frecuentes
 */
public class HelpActivity extends WebViewBase {

    @Override
    protected int getLayoutId() { return R.layout.activity_web; }
    @Override
    protected String getHtmlFile() { return "help.html"; }
    @Override
    protected Object getBridge() { return new BridgeComun(); }
}
