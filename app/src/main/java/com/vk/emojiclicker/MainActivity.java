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
import android.widget.CheckBox;
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

    // Settings (PC defaults)
    EditText sensInput;        // 20..98, default 78
    EditText zoneY0Input;      // percent 0..100, default 58
    EditText zoneY1Input;      // percent 0..100, default 88
    EditText cdMinInput;       // seconds, default 1.4
    EditText cdMaxInput;       // seconds, default 1.9

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

    EditText makeWideInput(String def) {
        EditText et = new EditText(this);
        et.setText(def);
        et.setTextSize(18);
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        et.setGravity(Gravity.CENTER);
        et.setTextColor(Color.WHITE);
        et.setBackgroundColor(0xFF444444);
        et.setSingleLine(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(90), dp(44));
        lp.leftMargin = dp(4);
        et.setLayoutParams(lp);
        return et;
    }

    // one settings row: label (left, wide) + one or two inputs
    LinearLayout makeSettingsRow(String label, EditText... inputs) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(2), dp(4), dp(2));
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(14);
        tv.setTextColor(0xFFEEEEEE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                dp(0), ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tv.setLayoutParams(lp);
        row.addView(tv);
        for (int i = 0; i < inputs.length; i++) {
            if (i > 0) {
                TextView sep = new TextView(this);
                sep.setText("до");
                sep.setTextSize(13);
                sep.setTextColor(0xFFBBBBBB);
                sep.setPadding(dp(2), 0, dp(2), 0);
                row.addView(sep);
            }
            row.addView(inputs[i]);
        }
        return row;
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

        // settings: 3 wide rows (sensitivity / zone / cooldown), each full-width
        LinearLayout settingsPanel = new LinearLayout(this);
        settingsPanel.setOrientation(LinearLayout.VERTICAL);
        settingsPanel.setPadding(dp(4), dp(4), dp(4), dp(4));
        settingsPanel.setBackgroundColor(0xFF2F2F2F);

        sensInput = makeWideInput("70");
        zoneY0Input = makeWideInput("58");
        zoneY1Input = makeWideInput("88");
        cdMinInput = makeWideInput("1.4");
        cdMaxInput = makeWideInput("1.9");

        settingsPanel.addView(makeSettingsRow("Чувствительность", sensInput));
        settingsPanel.addView(makeSettingsRow("Зона от %", zoneY0Input, zoneY1Input));
        settingsPanel.addView(makeSettingsRow("КД сек", cdMinInput, cdMaxInput));

        // buttons: Search once / Auto / Stop
        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        Button searchBtn = new Button(this);
        searchBtn.setText("Старт");
        searchBtn.setOnClickListener(v -> startAction());
        CheckBox autoCb = new CheckBox(this);
        autoCb.setText("Авто");
        autoCb.setTextColor(Color.WHITE);
        autoCb.setTextSize(14);
        autoCb.setChecked(false);
        autoCb.setOnCheckedChangeListener((buttonView, isChecked) -> {
            autoLoop = isChecked;
            if (isChecked) {
                Toast.makeText(this, "Авто включён", Toast.LENGTH_SHORT).show();
            }
        });
        Button stopBtn = new Button(this);
        stopBtn.setText("Стоп");
        stopBtn.setOnClickListener(v -> {
            autoLoop = false;
            autoCb.setChecked(false);
            Toast.makeText(this, "Остановлен", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams lpB = new LinearLayout.LayoutParams(
                dp(0), ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        searchBtn.setLayoutParams(lpB); autoCb.setLayoutParams(lpB); stopBtn.setLayoutParams(lpB);
        btns.addView(searchBtn); btns.addView(autoCb); btns.addView(stopBtn);

        toolbar.addView(pickerPanel);
        toolbar.addView(settingsPanel);
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

    void startAction() {
        if (priorityTemplate == null && normalTemplate == null) {
            Toast.makeText(this, "Сначала прикрепи фото эмодзи", Toast.LENGTH_LONG).show();
            return;
        }
        if (autoLoop) {
            Toast.makeText(this, "Авто запущен (КД " + cdMinInput.getText() + "–" + cdMaxInput.getText() + "с)", Toast.LENGTH_SHORT).show();
            new Thread(this::autoLoopWorker).start();
        } else {
            runSearchOnce();
        }
    }

    void runSearchOnce() {
        handler.post(() -> {
            final Bitmap shot = captureWebView();
            new Thread(() -> {
                if (shot == null) {
                    handler.post(() -> Toast.makeText(this, "Не удалось сделать скриншот", Toast.LENGTH_SHORT).show());
                    return;
                }
                if (isEmptyFrame(shot)) {
                    handler.post(() -> Toast.makeText(this, "Скриншот пустой — вкладка не видна или ещё грузится", Toast.LENGTH_LONG).show());
                    return;
                }
                Bitmap roi = cropZone(shot);
                int offset = roi != null ? zoneOffsets[0] : 0;
                boolean found = findMatch(roi, priorityTemplate, "приоритет", getSensitivity(), true, offset);
                if (!found && normalTemplate != null) {
                    found = findMatch(roi, normalTemplate, "не приоритет", getSensitivity(), true, offset);
                }
                if (!found) {
                    handler.post(() -> Toast.makeText(this, "Ничего не найдено", Toast.LENGTH_SHORT).show());
                }
            }).start();
        });
    }

    // auto mode: keeps searching+clicking in a loop while autoLoop is on.
    // Each iteration always waits the cooldown (CD) before the next scan,
    // so it never hot-spins or "freezes".
    void autoLoopWorker() {
        while (autoLoop) {
            try {
                final Bitmap[] holder = new Bitmap[1];
                holder[0] = captureUiThread();
                if (holder[0] != null) {
                    if (isEmptyFrame(holder[0])) {
                        handler.post(() -> Toast.makeText(this, "Скриншот пустой — проверь, что вкладка VK открыта", Toast.LENGTH_LONG).show());
                        try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                        continue;
                    }
                    Bitmap roi = cropZone(holder[0]);
                    int offset = roi != null ? zoneOffsets[0] : 0;
                    final float minScore = getSensitivity();
                    boolean found = findMatch(roi, priorityTemplate, "приоритет", minScore, false, offset);
                    if (!found && normalTemplate != null) {
                        found = findMatch(roi, normalTemplate, "не приоритет", minScore, false, offset);
                    }
                }
                // always wait CD between iterations
                int cd = currentCdDelay();
                long endMs = System.currentTimeMillis() + cd;
                while (autoLoop && System.currentTimeMillis() < endMs) {
                    try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                }
            } catch (Exception e) {
                // keep looping, don't crash
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            }
        }
    }

    // capture on UI thread, returns bitmap or null
    Bitmap captureUiThread() {
        try {
            final Bitmap[] holder = new Bitmap[1];
            final boolean[] done = new boolean[1];
            handler.post(() -> {
                holder[0] = captureWebView();
                done[0] = true;
            });
            long endMs = System.currentTimeMillis() + 2000;
            while (!done[0] && System.currentTimeMillis() < endMs) {
                try { Thread.sleep(20); } catch (InterruptedException ignored) {}
            }
            return holder[0];
        } catch (Exception e) {
            return null;
        }
    }

    // true if frame is (nearly) all white/black — means our capture is broken
    boolean isEmptyFrame(Bitmap bmp) {
        if (bmp == null) return true;
        int w = Math.min(bmp.getWidth(), 64);
        int h = Math.min(bmp.getHeight(), 64);
        int[] p = new int[w * h];
        bmp.getPixels(p, 0, w, 0, 0, w, h);
        int nonUniform = 0;
        for (int v : p) {
            int r = (v >> 16) & 0xff, g = (v >> 8) & 0xff, b = v & 0xff;
            int mx = Math.max(r, Math.max(g, b)), mn = Math.min(r, Math.min(g, b));
            int lum = (r * 299 + g * 587 + b * 114) / 1000;
            if (mx - mn > 40 || (lum > 20 && lum < 235)) nonUniform++;
        }
        return nonUniform < (w * h) / 100; // <1% varied pixels = blank
    }

    float getSensitivity() {
        try {
            float v = Float.parseFloat(sensInput.getText().toString().trim());
            if (v < 20) v = 20;
            if (v > 98) v = 98;
            return v / 100f;
        } catch (Exception e) {
            return 0.70f;
        }
    }

    // returns crop of shot by zone (Y from/to in percents of height), or null
    int[] zoneOffsets = new int[2]; // [offsetTop, cropHeight] lazily used

    Bitmap cropZone(Bitmap shot) {
        int h = shot.getHeight();
        float y0p, y1p;
        try {
            y0p = Float.parseFloat(zoneY0Input.getText().toString().trim());
            y1p = Float.parseFloat(zoneY1Input.getText().toString().trim());
        } catch (Exception e) {
            y0p = 58f; y1p = 88f;
        }
        if (y0p < 0) y0p = 0; if (y0p > 100) y0p = 100;
        if (y1p < 0) y1p = 0; if (y1p > 100) y1p = 100;
        if (y1p <= y0p) y1p = y0p + 10f;
        int y0 = (int)(h * y0p / 100f);
        int y1 = (int)(h * y1p / 100f);
        y0 = Math.max(0, Math.min(y0, h-1));
        y1 = Math.max(y0+1, Math.min(y1, h));
        zoneOffsets[0] = y0;
        zoneOffsets[1] = y1 - y0;
        return Bitmap.createBitmap(shot, 0, y0, shot.getWidth(), y1-y0);
    }

    // returns true if found (then caller taps) / false if not found or too weak
    // roi is the zone strip (already cropped), zoneOffset is its top offset in the full screenshot
    boolean findMatch(final Bitmap roi, final Bitmap tpl, final String label, float minScore, final boolean verbose, int zoneOffset) {
        if (tpl == null) return false;
        if (roi == null) return false;
        TemplateMatcher.Match m = TemplateMatcher.findBest(roi, tpl);
        if (m == null) return false;
        if (m.score < minScore) {
            if (verbose) {
                final float sc = m.score;
                handler.post(() -> Toast.makeText(this, label + ": " + Math.round(sc*100) + "% (слабовато)", Toast.LENGTH_SHORT).show());
            }
            return false;
        }
        // convert back to full screenshot coords, tap slightly below center (PC parity)
        final int absY = m.y + zoneOffset;
        final int tapY = absY + (int)(m.templateH * 0.55f);
        handler.post(() -> {
            cursorOverlay.showAt(m.x, tapY);
            tapAt(m.x, tapY);
            Toast.makeText(this, "Кликнул: " + label + " (" + Math.round(m.score*100) + "%)", Toast.LENGTH_SHORT).show();
        });
        return true;
    }

    int currentCdDelay() {
        float lo, hi;
        try {
            lo = Float.parseFloat(cdMinInput.getText().toString().trim());
            hi = Float.parseFloat(cdMaxInput.getText().toString().trim());
        } catch (Exception e) {
            lo = 1.4f; hi = 1.9f;
        }
        if (hi < lo) hi = lo;
        double d = lo + Math.random() * (hi - lo);
        return (int)(d * 1000);
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
            int w = webView.getWidth();
            int h = webView.getHeight();
            if (w <= 0 || h <= 0) return null;
            // Hardware-accelerated WebView often draws nothing into a CPU canvas.
            // Force a software layer while capturing so pixels are real.
            int prevLayer = webView.getLayerType();
            boolean wasHardwareAccelerated = webView.isHardwareAccelerated();
            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            webView.draw(c);
            webView.setLayerType(prevLayer, null);
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