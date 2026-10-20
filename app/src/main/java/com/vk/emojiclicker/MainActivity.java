package com.vk.emojiclicker;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
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

import java.util.ArrayList;
import java.util.List;

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
    ScrollView emojiPicker;
    Handler handler = new Handler(Looper.getMainLooper());

    String[] priorityEmojis = {
            "⚔️","😀","😂","😍","😘","😎","🤔","😅","😭","🤣",
            "😉","👍","👎","🙏","👏","💪","🤝","❤️","💕","💯"
    };

    String[] normalEmojis = {
            "🔥","⭐","🎉","🥰","😊","🤷","🙄","💀","👀","😱","🥺",
            "✅","❌","💋","🤡","👌","✌️","🤟","🖤","💔","😴","😡"
    };

    String currentEmoji = "⚔️";
    boolean autoLoop = false;
    String[] autoList = priorityEmojis;
    int autoIndex = 0;

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

    void addSectionHeader(LinearLayout parent, String text) {
        TextView header = new TextView(this);
        header.setText(text);
        header.setTextSize(12);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setTextColor(0xFF9E9E9E);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(28));
        lp.topMargin = dp(4);
        header.setLayoutParams(lp);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(4), 0, 0, 0);
        parent.addView(header);
    }

    void addEmojiGridRow(LinearLayout parent, String[] list) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (String emoji : list) {
            Button b = new Button(this);
            b.setText(emoji);
            b.setTextSize(20);
            b.setMinHeight(dp(48));
            b.setMinWidth(dp(48));
            b.setPadding(0, 0, 0, 0);
            b.setBackgroundColor(0xFF3A3A3A);
            b.setTextColor(Color.WHITE);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    dp(48), dp(48));
            b.setLayoutParams(lp);
            b.setOnClickListener(view -> {
                currentEmoji = ((Button) view).getText().toString();
                if (autoLoop) runSearchLoop();
                else runSearchOnce();
            });
            row.addView(b);
        }
        parent.addView(row);
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

        // emoji picker: priority section + normal section
        emojiPicker = new ScrollView(this);
        emojiPicker.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(180)));
        LinearLayout gridWrap = new LinearLayout(this);
        gridWrap.setOrientation(LinearLayout.VERTICAL);

        addSectionHeader(gridWrap, "ПРИОРИТЕТ");
        addEmojiGridRow(gridWrap, priorityEmojis);
        addSectionHeader(gridWrap, "НЕ ПРИОРИТЕТ");
        addEmojiGridRow(gridWrap, normalEmojis);

        emojiPicker.addView(gridWrap);

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
                // start: iterate priority first, then normal
                autoLoop = true;
                autoList = priorityEmojis;
                autoIndex = 0;
                currentEmoji = autoList[0];
                Toast.makeText(this, "Авто: приоритетные → обычные", Toast.LENGTH_SHORT).show();
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

        toolbar.addView(emojiPicker);
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
        new Thread(() -> {
            Bitmap tpl = renderEmoji(currentEmoji);
            Bitmap shot = captureWebView();
            if (tpl == null || shot == null) {
                handler.post(() -> Toast.makeText(this, "Не удалось сделать поиск", Toast.LENGTH_SHORT).show());
                return;
            }
            TemplateMatcher.Match m = TemplateMatcher.find(shot, tpl, 0.6);
            handler.post(() -> {
                if (m == null) {
                    Toast.makeText(this, "Не найдено: " + currentEmoji, Toast.LENGTH_SHORT).show();
                } else {
                    cursorOverlay.showAt(m.x, m.y);
                    tapAt(m.x, m.y);
                    Toast.makeText(this, "Кликнул: " + currentEmoji + " (" + Math.round(m.score*100) + "%)", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    void runSearchLoop() {
        if (!autoLoop) return;
        final String emoji = autoList[autoIndex];
        currentEmoji = emoji;
        new Thread(() -> {
            Bitmap tpl = renderEmoji(emoji);
            Bitmap shot = captureWebView();
            if (tpl != null && shot != null) {
                TemplateMatcher.Match m = TemplateMatcher.find(shot, tpl, 0.6);
                if (m != null) {
                    final TemplateMatcher.Match fm = m;
                    handler.post(() -> {
                        cursorOverlay.showAt(fm.x, fm.y);
                        tapAt(fm.x, fm.y);
                        Toast.makeText(this, "Кликнул: " + emoji, Toast.LENGTH_SHORT).show();
                    });
                }
            }
            // advance to next emoji; when priority list is done, switch to normal; wrap around
            handler.postDelayed(() -> {
                autoIndex++;
                if (autoIndex >= autoList.length) {
                    if (autoList == priorityEmojis) {
                        autoList = normalEmojis;
                        autoIndex = 0;
                    } else {
                        autoList = priorityEmojis;
                        autoIndex = 0;
                    }
                }
                runSearchLoop();
            }, 1200);
        }).start();
    }

    Bitmap renderEmoji(String emoji) {
        try {
            TextView tv = new TextView(this);
            tv.setText(emoji);
            tv.setTextSize(48);
            tv.setTypeface(Typeface.DEFAULT);
            tv.measure(View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY));
            int w = tv.getMeasuredWidth(), h = tv.getMeasuredHeight();
            if (w <= 0 || h <= 0) return null;
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            c.drawColor(Color.WHITE);
            tv.layout(0, 0, w, h);
            tv.draw(c);
            return trim(bmp);
        } catch (Exception e) {
            return null;
        }
    }

    Bitmap trim(Bitmap bmp) {
        // find white bounds and crop with small margin
        int w = bmp.getWidth(), h = bmp.getHeight();
        int[] p = new int[w*h];
        bmp.getPixels(p, 0, w, 0, 0, w, h);
        int minX=w, minY=h, maxX=-1, maxY=-1;
        for (int y=0; y<h; y++) for (int x=0; x<w; x++) {
            int c = p[y*w+x];
            int r=(c>>16)&0xff,g=(c>>8)&0xff,b=c&0xff;
            // non-white pixel
            if (r<240 || g<240 || b<240) {
                if(x<minX)minX=x; if(x>maxX)maxX=x;
                if(y<minY)minY=y; if(y>maxY)maxY=y;
            }
        }
        if (maxX < minX) return bmp;
        int pad=6;
        int x0=Math.max(0,minX-pad), y0=Math.max(0,minY-pad);
        int x1=Math.min(w,maxX+pad), y1=Math.min(h,maxY+pad);
        return Bitmap.createBitmap(bmp, x0, y0, x1-x0, y1-y0);
    }

    Bitmap captureWebView() {
        try {
            webView.setDrawingCacheEnabled(true);
            Bitmap b = webView.getDrawingCache();
            return b != null ? Bitmap.createBitmap(b) : null;
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