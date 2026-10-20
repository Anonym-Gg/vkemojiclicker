package com.vk.emojiclicker;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebViewClient;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.InputStream;

public class MainActivity extends AppCompatActivity {

    static final String VK_URL = "https://vk.com/im";

    // region License
    LinearLayout licenseLayout;
    EditText keyInput;
    Button activateBtn;
    TextView statusText;

    // region Main UI
    FrameLayout mainFrame;
    WebView webView;
    CursorOverlay cursorOverlay;
    ScrollView pickerPanel;
    Handler handler = new Handler(Looper.getMainLooper());

    boolean autoLoop = false;

    // Attached photo templates (from gallery), priority & normal
    Bitmap priorityTemplate = null;
    Bitmap normalTemplate = null;
    static final int PICK_PRIORITY = 1001;
    static final int PICK_NORMAL = 1002;
    TextView priorityFileTv = null;
    TextView normalFileTv = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        licenseLayout = findViewById(R.id.licenseLayout);
        // licenseLayout is root LinearLayout in activity_main (which we reuse)
        keyInput = findViewById(R.id.keyInput);
        activateBtn = findViewById(R.id.activateBtn);
        statusText = findViewById(R.id.statusText);

        activateBtn.setOnClickListener(v -> tryActivate());
    }

    void tryActivate() {
        String key = keyInput.getText().toString().trim();
        if (key.isEmpty()) {
            statusText.setText("Введи ключ");
            return;
        }
        statusText.setText("Проверка...");
        activateBtn.setEnabled(false);
        new Thread(() -> {
            JSONObject res = LicenseClient.activate(this, key);
            boolean ok = res != null && res.optBoolean("ok", false);
            String msg = res != null ? res.optString("message", "Ошибка") : "Сервер недоступен";
            handler.post(() -> {
                activateBtn.setEnabled(true);
                if (ok) {
                    statusText.setText("Успех! Запуск...");
                    handler.postDelayed(this::buildMainUI, 600);
                } else {
                    statusText.setText(msg + " " + res);
                }
            });
        }).start();
    }

    void addPhotoPanel(LinearLayout parent, String label, final int requestCode, final boolean priority) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(4), dp(4), dp(4), dp(4));
        panel.setBackgroundColor(0xFF333333);

        TextView title = new TextView(this);
        title.setText(label);
        title.setTextSize(12);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFFFFFFFF);
        title.setPadding(dp(4), 0, 0, 0);
        panel.addView(title);

        final TextView fileName = new TextView(this);
        fileName.setText("Фото не выбрано");
        fileName.setTextSize(11);
        fileName.setTextColor(0xFFAAAAAA);
        fileName.setPadding(dp(4), dp(2), 0, dp(2));
        panel.addView(fileName);

        if (priority) priorityFileTv = fileName;
        else normalFileTv = fileName;

        Button pickBtn = new Button(this);
        pickBtn.setText("Прикрепить фото");
        pickBtn.setTextSize(12);
        pickBtn.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(38));
        pickBtn.setLayoutParams(lp);
        pickBtn.setOnClickListener(v -> pickPhoto(requestCode));
        panel.addView(pickBtn);

        parent.addView(panel);
    }

    void pickPhoto(int requestCode) {
        Intent i = new Intent(Intent.ACTION_PICK);
        i.setType("image/*");
        try {
            startActivityForResult(i, requestCode);
        } catch (Exception e) {
            Toast.makeText(this, "Нет галереи: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        Bitmap bmp = null;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            InputStream is = getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(is, null, o);
            if (is != null) is.close();
            int sample = 1;
            while (o.outWidth / sample > 400 || o.outHeight / sample > 400) sample *= 2;
            if (sample < 1) sample = 1;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            InputStream is2 = getContentResolver().openInputStream(uri);
            bmp = BitmapFactory.decodeStream(is2, null, o2);
            if (is2 != null) is2.close();
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось открыть фото", Toast.LENGTH_SHORT).show();
            return;
        }
        if (bmp == null) {
            Toast.makeText(this, "Фото не прочитано", Toast.LENGTH_SHORT).show();
            return;
        }
        bmp = trim(bmp);
        if (requestCode == PICK_PRIORITY) {
            priorityTemplate = bmp;
            if (priorityFileTv != null)
                priorityFileTv.setText("Фото прикреплено ✓ (" + bmp.getWidth() + "x" + bmp.getHeight() + ")");
        } else if (requestCode == PICK_NORMAL) {
            normalTemplate = bmp;
            if (normalFileTv != null)
                normalFileTv.setText("Фото прикреплено ✓ (" + bmp.getWidth() + "x" + bmp.getHeight() + ")");
        }
        Toast.makeText(this, "Фото прикреплено", Toast.LENGTH_SHORT).show();
    }

    void buildMainUI() {
        // replace license content with main UI
        licenseLayout.removeAllViews();
        mainFrame = new FrameLayout(this);
        mainFrame.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // bottom toolbar
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.VERTICAL);
        toolbar.setPadding(dp(8), dp(4), dp(8), dp(4));
        toolbar.setBackgroundColor(0xFF2B2B2B);

        // photo picker panels: priority & normal (no emoji grid at all)
        pickerPanel = new ScrollView(this);
        pickerPanel.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(170)));
        LinearLayout pickerWrap = new LinearLayout(this);
        pickerWrap.setOrientation(LinearLayout.VERTICAL);

        addPhotoPanel(pickerWrap, "ПРИОРИТЕТ (прикрепи фото эмодзи)", PICK_PRIORITY, true);
        addPhotoPanel(pickerWrap, "НЕ ПРИОРИТЕТ (прикрепи фото эмодзи)", PICK_NORMAL, false);

        pickerPanel.addView(pickerWrap);

        // buttons: Search once / Auto / Stop
        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        Button searchBtn = new Button(this);
        searchBtn.setText("Найти и кликнуть");
        searchBtn.setOnClickListener(v -> runSearchOnce());
        Button autoBtn = new Button(this);
        autoBtn.setText("Авто");
        autoBtn.setOnClickListener(v -> {
            if (!autoLoop) {
                if (priorityTemplate == null && normalTemplate == null) {
                    Toast.makeText(this, "Сначала прикрепи фото эмодзи", Toast.LENGTH_LONG).show();
                    return;
                }
                autoLoop = true;
                Toast.makeText(this, "Авто: приоритет → не приоритет", Toast.LENGTH_SHORT).show();
                runSearchLoop();
            } else {
                autoLoop = false;
                Toast.makeText(this, "Авто остановлен", Toast.LENGTH_SHORT).show();
            }
        });
        Button stopBtn = new Button(this);
        stopBtn.setText("Стоп");
        stopBtn.setOnClickListener(v -> {
            autoLoop = false;
            Toast.makeText(this, "Авто остановлен", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams lpB = new LinearLayout.LayoutParams(
                dp(0), ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        searchBtn.setLayoutParams(lpB); autoBtn.setLayoutParams(lpB); stopBtn.setLayoutParams(lpB);
        btns.addView(searchBtn); btns.addView(autoBtn); btns.addView(stopBtn);

        toolbar.addView(pickerPanel);
        toolbar.addView(btns);

        // WebView fills the rest
        webView = new WebView(this);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        webView.setLayoutParams(wlp);
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setUserAgentString(ws.getUserAgentString().replace("; wv", ""));
        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl(VK_URL);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(webView);
        content.addView(toolbar);

        mainFrame.addView(content);
        cursorOverlay = new CursorOverlay(this);
        mainFrame.addView(cursorOverlay);

        licenseLayout.addView(mainFrame);
    }

    void runSearchOnce() {
        if (priorityTemplate == null && normalTemplate == null) {
            Toast.makeText(this, "Сначала прикрепи фото эмодзи", Toast.LENGTH_LONG).show();
            return;
        }
        handler.post(() -> {
            final Bitmap shot = captureWebView();
            new Thread(() -> {
                if (shot == null) {
                    handler.post(() -> Toast.makeText(this, "Не удалось сделать скриншот", Toast.LENGTH_SHORT).show());
                    return;
                }
                // priority first, then normal
                if (tryFindAndTap(shot, priorityTemplate, "приоритет")) return;
                if (tryFindAndTap(shot, normalTemplate, "не приоритет")) return;
                handler.post(() -> Toast.makeText(this, "Ничего не найдено", Toast.LENGTH_SHORT).show());
            }).start();
        });
    }

    // returns true if found & tapped; must be called on background thread
    boolean tryFindAndTap(final Bitmap shot, final Bitmap tpl, final String label) {
        if (tpl == null) return false;
        TemplateMatcher.Match m = TemplateMatcher.findBest(shot, tpl);
        if (m == null) return false;
        if (m.score < 0.6f) {
            final float sc = m.score;
            handler.post(() -> Toast.makeText(this, label + ": совпадение " + Math.round(sc*100) + "% (не кликнул)", Toast.LENGTH_SHORT).show());
            return false;
        }
        handler.post(() -> {
            cursorOverlay.showAt(m.x, m.y);
            tapAt(m.x, m.y);
            Toast.makeText(this, "Кликнул: " + label + " (" + Math.round(m.score*100) + "%)", Toast.LENGTH_SHORT).show();
        });
        return true;
    }

    void runSearchLoop() {
        if (!autoLoop) return;
        handler.post(() -> {
            final Bitmap shot = captureWebView();
            new Thread(() -> {
                if (shot != null) {
                    // priority first
                    if (tryFindAndTap(shot, priorityTemplate, "приоритет")) {
                        handler.postDelayed(this::runSearchLoop, 1200);
                        return;
                    }
                    // then normal
                    if (tryFindAndTap(shot, normalTemplate, "не приоритет")) {
                        handler.postDelayed(this::runSearchLoop, 1200);
                        return;
                    }
                }
                handler.postDelayed(this::runSearchLoop, 1200);
            }).start();
        });
    }

    Bitmap trim(Bitmap bmp) {
        // find bounds of non-background pixels and crop
        int w = bmp.getWidth(), h = bmp.getHeight();
        int[] p = new int[w*h];
        bmp.getPixels(p, 0, w, 0, 0, w, h);
        int minX=w, minY=h, maxX=-1, maxY=-1;
        for (int y=0; y<h; y++) for (int x=0; x<w; x++) {
            int c = p[y*w+x];
            int a = (c>>>24) & 0xff;
            int r=(c>>16)&0xff,g=(c>>8)&0xff,b=c&0xff;
            boolean background = (a < 16) || (a > 240 && r > 240 && g > 240 && b > 240);
            if (!background) {
                if(x<minX)minX=x; if(x>maxX)maxX=x;
                if(y<minY)minY=y; if(y>maxY)maxY=y;
            }
        }
        if (maxX < minX) return bmp;
        int pad=6;
        int x0=Math.max(0,minX-pad), y0=Math.max(0,minY-pad);
        int x1=Math.min(w,maxX+pad), y1=Math.min(h,maxY+pad);
        if (x1 - x0 < 8 || y1 - y0 < 8) return bmp; // too small -> keep original
        return Bitmap.createBitmap(bmp, x0, y0, x1-x0, y1-y0);
    }

    Bitmap captureWebView() {
        try {
            // MUST be called on the UI thread. getDrawingCache() is unreliable
            // for hardware-accelerated WebView, so draw into a fresh bitmap instead.
            int w = webView.getWidth();
            int h = webView.getHeight();
            if (w <= 0 || h <= 0) return null;
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            webView.draw(c);
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    void tapAt(int x, int y) {
        // dispatch a tap in app coordinates via TouchUtils
        TouchUtils.tap(this, webView, x, y);
    }

    int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}