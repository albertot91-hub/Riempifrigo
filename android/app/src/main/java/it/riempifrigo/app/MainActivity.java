package it.riempifrigo.app;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.webkit.WebViewAssetLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Riempifrigo inside a WebView. The web app is bundled in assets/www and served from
 *  https://appassets.androidplatform.net so localStorage works and nothing needs the network. */
public class MainActivity extends Activity {
    private static final String BASE = "https://appassets.androidplatform.net/www/index.html";
    private static final int PICK_FILE = 1;

    private WebView web;
    private ValueCallback<Uri[]> fileCb;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        web = new WebView(this);
        setContentView(web);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/www/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);

        web.addJavascriptInterface(new Bridge(), "RiempiAndroid");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return loader.shouldInterceptRequest(r.getUrl());
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if ("appassets.androidplatform.net".equals(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) {}
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                try { startActivityForResult(i, PICK_FILE); }
                catch (Exception e) { fileCb = null; return false; }
                return true;
            }
        });

        if (state != null) web.restoreState(state);
        else web.loadUrl(urlFor(getIntent()));
    }

    /** A recipe shared from another app opens the recipe sheet, like the PWA share target. */
    private String urlFor(Intent in) {
        if (in != null && Intent.ACTION_SEND.equals(in.getAction())) {
            String text = in.getStringExtra(Intent.EXTRA_TEXT);
            String title = in.getStringExtra(Intent.EXTRA_SUBJECT);
            Uri.Builder b = Uri.parse(BASE).buildUpon();
            if (text != null) b.appendQueryParameter("text", text);
            if (title != null) b.appendQueryParameter("title", title);
            return b.build().toString();
        }
        return BASE;
    }

    @Override
    protected void onNewIntent(Intent in) {
        super.onNewIntent(in);
        if (Intent.ACTION_SEND.equals(in.getAction())) web.loadUrl(urlFor(in));
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == PICK_FILE && fileCb != null) {
            Uri u = (res == RESULT_OK && data != null) ? data.getData() : null;
            fileCb.onReceiveValue(u != null ? new Uri[]{u} : null);
            fileCb = null;
            return;
        }
        super.onActivityResult(req, res, data);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // The page closes an open sheet or goes back to Lista; only then does Back leave the app.
        web.evaluateJavascript("window.riempiBack ? String(window.riempiBack()) : 'false'", v -> {
            if (!"\"true\"".equals(v)) finish();
        });
    }

    private class Bridge {
        @JavascriptInterface
        public void keepAwake(final boolean on) {
            runOnUiThread(() -> {
                if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }

        /** Saves the backup JSON in Download. Returns true when it worked. */
        @JavascriptInterface
        public boolean saveFile(String name, String text) {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
                    cv.put(MediaStore.Downloads.MIME_TYPE, "application/json");
                    cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                    if (u == null) return false;
                    try (OutputStream os = getContentResolver().openOutputStream(u)) { os.write(bytes); }
                } else {
                    File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                    if (dir == null) return false;
                    try (FileOutputStream os = new FileOutputStream(new File(dir, name))) { os.write(bytes); }
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Salvato in " + dir.getAbsolutePath(), Toast.LENGTH_LONG).show());
                }
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        /** Opens the Android share sheet with plain text. */
        @JavascriptInterface
        public void shareText(final String text) {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(i, "Condividi"));
            });
        }
    }
}
