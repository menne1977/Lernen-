package de.lrstrainer;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.util.Locale;

/**
 * LRS-Trainer: zeigt die Übungs-App (index.html aus den Assets) in einer WebView.
 * Die Sprachausgabe kommt von der Android-Sprachausgabe (Deutsch).
 * Kein Internetzugriff, nichts wird nachgeladen. Fortschritt bleibt auf dem Gerät.
 */
public class MainActivity extends Activity {

    private static final String START_URL = "file:///android_asset/index.html";

    private WebView web;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private boolean warned = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#2B5797"));

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#FBF8F1"));
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setTextZoom(100);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !request.getUrl().toString().startsWith("file:///android_asset/");
            }
        });

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int r = tts.setLanguage(Locale.GERMANY);
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    r = tts.setLanguage(Locale.GERMAN);
                }
                ttsReady = (r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED);
            }
        });
        // Muss vor loadUrl gesetzt werden, damit die Seite "AndroidTTS" beim Start sieht.
        web.addJavascriptInterface(new Bridge(), "AndroidTTS");

        web.loadUrl(START_URL);
    }

    /** Aufruf aus der Seite: AndroidTTS.speak("Sonne", 0.85) bzw. AndroidTTS.stop() */
    private class Bridge {
        @JavascriptInterface
        public void speak(String text, float rate) {
            if (text == null || text.isEmpty()) return;
            if (ttsReady) {
                float r = Math.max(0.3f, Math.min(1.5f, rate));
                tts.setSpeechRate(r);
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "lrs");
            } else if (!warned) {
                warned = true;
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this,
                            "Keine deutsche Stimme gefunden. Bitte in den Einstellungen unter Sprachausgabe Deutsch installieren.",
                            Toast.LENGTH_LONG).show();
                    try {
                        startActivity(new Intent("com.android.settings.TTS_SETTINGS"));
                    } catch (Exception ignored) { }
                });
            }
        }

        @JavascriptInterface
        public void stop() {
            if (tts != null) tts.stop();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        web.evaluateJavascript("(window.appBack ? window.appBack() : false)", value -> {
            if (!"true".equals(value)) {
                MainActivity.super.onBackPressed();
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        web.onPause();
        if (tts != null) tts.stop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
