package com.haha5039.cmc;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Offline copy of the same procedural guide. No scripts, web navigation or calculator input. */
public final class ManualGuideActivity extends Activity {
    private WebView web;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        int background=Color.rgb(16,23,21),text=Color.rgb(240,246,243);
        getWindow().setNavigationBarContrastEnforced(false);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(background);
        root.setOnApplyWindowInsetsListener((view,insets) -> {
            android.graphics.Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            view.setPadding(safe.left,safe.top,safe.right,safe.bottom);return insets;
        });
        Button back=new Button(this);back.setId(R.id.manual_guide_back);back.setAllCaps(false);back.setText("‹ 계산기로 돌아가기");
        back.setTextColor(text);back.setMinHeight(dp(52));back.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(35,51,43)));
        back.setOnClickListener(ignored -> finish());root.addView(back,new LinearLayout.LayoutParams(-1,-2));
        setContentView(root);root.requestApplyInsets();
        String html;
        try (InputStream input=getAssets().open("manual-guide.html");ByteArrayOutputStream output=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[4096];int count;
            while ((count=input.read(buffer))!=-1) {
                if (output.size()+count>524288) throw new IllegalStateException("Guide asset too large");
                output.write(buffer,0,count);
            }
            html=new String(output.toByteArray(),StandardCharsets.UTF_8);
        } catch (Exception error) {
            TextView unavailable=new TextView(this);unavailable.setText("가이드를 열지 못했습니다. 앱을 다시 설치한 뒤 확인해 주세요.");
            unavailable.setTextColor(text);unavailable.setPadding(dp(20),dp(20),dp(20),dp(20));root.addView(unavailable);return;
        }
        try {
            web=new WebView(this);web.setId(R.id.manual_guide_web);web.setBackgroundColor(background);
            web.getSettings().setJavaScriptEnabled(false);
            web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);
            web.getSettings().setBlockNetworkLoads(true);web.getSettings().setDomStorageEnabled(false);
            web.getSettings().setTextZoom(Math.round(getResources().getConfiguration().fontScale*100));
            web.setWebViewClient(new WebViewClient() {
                @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request) { return true; }
            });
            root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
            web.loadDataWithBaseURL("https://guide.invalid/",html,"text/html","UTF-8",null);
        } catch (RuntimeException unavailable) {
            if (web!=null) { root.removeView(web);web.destroy();web=null; }
            int start=html.indexOf("<section");int end=html.indexOf("</section>",start);
            String fragment=start>=0 && end>=0 ? html.substring(start,end+10) : "수동 설정 가이드를 열 수 없습니다.";
            ScrollView scroll=new ScrollView(this);TextView body=new TextView(this);body.setTextColor(text);body.setTextSize(15);
            body.setPadding(dp(20),dp(20),dp(20),dp(24));body.setText(Html.fromHtml(fragment,Html.FROM_HTML_MODE_LEGACY));
            body.setTextIsSelectable(true);scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        }
    }
    @Override protected void onPause() { if (web!=null) web.onPause();super.onPause(); }
    @Override protected void onResume() { super.onResume();if (web!=null) web.onResume(); }
    @Override protected void onDestroy() { if (web!=null) { web.stopLoading();if (web.getParent() instanceof ViewGroup) ((ViewGroup)web.getParent()).removeView(web);web.destroy();web=null; }super.onDestroy(); }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
}
