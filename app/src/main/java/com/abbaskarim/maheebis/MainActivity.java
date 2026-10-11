package com.abbaskarim.maheebis;

import android.app.Activity;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.util.Locale;

/**
 * لعبة المحيبس: شاشة اللعبة + المعلّق الصوتي.
 * يحمل اللعبة المحلية فقط من assets/index.html ويتيح لها نطق التعليقات عبر Android TTS.
 */
public class MainActivity extends Activity {
    private WebView webView;
    private TextToSpeech speaker;
    private boolean speakerReady = false;
    private String pendingLine = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep the game running in its existing landscape setup (set by AndroidManifest.xml).
        webView = new WebView(this);
        webView.setWebViewClient(new WebViewClient());
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.addJavascriptInterface(new IraqiVoiceBridge(), "IraqiVoice");
        setContentView(webView);

        speaker = new TextToSpeech(getApplicationContext(), status -> {
            runOnUiThread(() -> {
                if (isFinishing() || speaker == null) return;
                if (status == TextToSpeech.SUCCESS) {
                    // Prefer the Iraqi Arabic voice; use another Arabic voice if unavailable.
                    int availability = speaker.setLanguage(new Locale("ar", "IQ"));
                    if (availability == TextToSpeech.LANG_MISSING_DATA
                            || availability == TextToSpeech.LANG_NOT_SUPPORTED) {
                        availability = speaker.setLanguage(new Locale("ar"));
                    }
                    speakerReady = availability != TextToSpeech.LANG_MISSING_DATA
                            && availability != TextToSpeech.LANG_NOT_SUPPORTED;
                    speaker.setSpeechRate(0.90f);
                    speaker.setPitch(0.95f);
                    if (speakerReady && pendingLine != null) {
                        String firstLine = pendingLine;
                        pendingLine = null;
                        speaker.speak(firstLine, TextToSpeech.QUEUE_FLUSH, null, "maheebis_intro");
                    }
                }
            });
        });

        // The page contains the Iraqi phrases and triggers the voice automatically.
        webView.loadUrl("file:///android_asset/index.html");
    }

    private final class IraqiVoiceBridge {
        @JavascriptInterface
        public void speak(String phrase) {
            if (phrase == null || phrase.trim().isEmpty()) return;
            final String safePhrase = phrase.length() > 350 ? phrase.substring(0, 350) : phrase;
            runOnUiThread(() -> {
                if (speakerReady && speaker != null) {
                    speaker.speak(safePhrase, TextToSpeech.QUEUE_FLUSH, null, "maheebis_line");
                } else {
                    pendingLine = safePhrase;
                }
            });
        }

        @JavascriptInterface
        public void stop() {
            runOnUiThread(() -> {
                pendingLine = null;
                if (speaker != null) speaker.stop();
            });
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (speaker != null) speaker.stop();
    }

    @Override
    protected void onDestroy() {
        pendingLine = null;
        if (webView != null) {
            webView.removeJavascriptInterface("IraqiVoice");
            webView.destroy();
        }
        if (speaker != null) {
            speaker.stop();
            speaker.shutdown();
            speaker = null;
        }
        super.onDestroy();
    }
}
