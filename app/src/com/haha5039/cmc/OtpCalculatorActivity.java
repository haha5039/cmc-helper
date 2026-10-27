package com.haha5039.cmc;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Manual calculation never calls the controller, Shizuku, or Samsung settings. */
public final class OtpCalculatorActivity extends Activity {
    private static final int BG = Color.rgb(16,23,21), CARD = Color.rgb(26,37,32);
    private static final int TEXT = Color.rgb(240,246,243), MUTED = Color.rgb(178,196,187);
    private static final int ACCENT = Color.rgb(146,227,202), ERROR = Color.rgb(255,209,155);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private EditText keyInput;
    private TextView code, basis, countdown, validation;
    private Button copy;
    private OtpCalculator.Result result;
    private boolean resumed;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!resumed || result == null) return;
            updateNow();
            scheduleTick();
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setNavigationBarContrastEnforced(false);
        LinearLayout screen = column();
        screen.setBackgroundColor(BG);
        screen.setOnApplyWindowInsetsListener((view,insets) -> {
            android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            view.setPadding(safe.left,safe.top,safe.right,safe.bottom);
            return insets;
        });
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = column();
        content.setPadding(dp(20),dp(18),dp(20),dp(24));
        scroll.addView(content,new ScrollView.LayoutParams(-1,-2));
        screen.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        setContentView(screen);
        screen.requestApplyInsets();

        button(content,"‹ CMC Helper로 돌아가기","calculator_back",false,this::finish);
        TextView title = text("KEY 인증코드 계산기",27,TEXT);
        title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        add(content,title,20);
        add(content,text("삼성 CMC 화면에 표시된 KEY를 입력하세요. Shizuku 연결 없이 기기 안에서 계산합니다.",14,MUTED),8);

        LinearLayout inputCard = card(content);
        add(inputCard,text("화면의 KEY 5자리",15,TEXT),0);
        keyInput = new EditText(this);
        keyInput.setContentDescription("calculator_key");
        keyInput.setHint("예: 1abcd");
        keyInput.setTextColor(TEXT);
        keyInput.setHintTextColor(MUTED);
        keyInput.setSingleLine(true);
        keyInput.setTextSize(22);
        keyInput.setTypeface(Typeface.MONOSPACE);
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        keyInput.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        keyInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64)});
        keyInput.setBackgroundTintList(ColorStateList.valueOf(ACCENT));
        add(inputCard,keyInput,10);
        add(inputCard,text("앞뒤 공백은 제외하고, 대문자는 소문자로 바꿔 계산합니다.",12,MUTED),6);
        validation = text("",13,ERROR);
        validation.setContentDescription("calculator_error");
        validation.setVisibility(View.GONE);
        validation.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        add(inputCard,validation,6);
        button(inputCard,"인증코드 계산","calculator_calculate",true,this::calculate);

        LinearLayout outputCard = card(content);
        add(outputCard,text("현재 인증코드",15,TEXT),0);
        code = text("—",32,ACCENT);
        code.setContentDescription("calculator_code");
        code.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
        code.setSingleLine(true);
        code.setTextIsSelectable(true);
        code.setAutoSizeTextTypeUniformWithConfiguration(18,34,1,android.util.TypedValue.COMPLEX_UNIT_SP);
        add(outputCard,code,12);
        basis = text("KEY를 입력하고 계산을 누르세요.",13,MUTED);
        basis.setContentDescription("calculator_basis");
        add(outputCard,basis,8);
        countdown = text("",13,MUTED);
        countdown.setContentDescription("calculator_countdown");
        add(outputCard,countdown,4);
        copy = button(outputCard,"인증코드 복사","calculator_copy",false,this::copyCode);
        setCopyEnabled(false);
        add(content,text("화면을 보는 동안 분이 바뀌면 코드가 자동으로 갱신됩니다. 새 KEY가 표시되면 다시 입력하세요.\n\n계산식은 CMC 9.1.00.15 기준이며, 기기의 날짜·시간이 정확해야 합니다. KEY와 코드를 별도로 저장하거나 전송하지 않습니다.",12,MUTED),16);

        if (state != null) keyInput.setText(state.getString("typed_key",""));
        keyInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count) {
                validation.setVisibility(View.GONE);
                if (result != null) {
                    try { if (result.key.equals(OtpCalculator.normalize(s.toString()))) return; }
                    catch (IllegalArgumentException ignored) { }
                }
                clearResult();
            }
            @Override public void afterTextChanged(Editable value) {}
        });
        keyInput.setOnEditorActionListener((view,action,event) -> {
            if (action == EditorInfo.IME_ACTION_DONE) { calculate(); return true; }
            return false;
        });
        if (state != null && state.getString("calculated_key") != null) {
            try {
                if (state.getString("calculated_key").equals(OtpCalculator.normalize(keyInput.getText().toString())))
                    show(OtpCalculator.calculate(keyInput.getText().toString(),System.currentTimeMillis()));
            } catch (IllegalArgumentException ignored) { clearResult(); }
        }
    }

    private void calculate() {
        try {
            show(OtpCalculator.calculate(keyInput.getText().toString(),System.currentTimeMillis()));
            validation.setVisibility(View.GONE);
            InputMethodManager keyboard = getSystemService(InputMethodManager.class);
            if (keyboard != null) keyboard.hideSoftInputFromWindow(keyInput.getWindowToken(),0);
        } catch (IllegalArgumentException error) {
            clearResult();
            validation.setText(error.getMessage());
            validation.setVisibility(View.VISIBLE);
        }
    }
    private void updateNow() {
        if (result == null) return;
        long now = System.currentTimeMillis();
        if (result.minute != Math.floorDiv(now,60000L)) show(OtpCalculator.calculate(result.key,now));
        countdown.setText("다음 코드 갱신까지 " + OtpCalculator.secondsToNextCode(now) + "초");
    }
    private void show(OtpCalculator.Result computed) {
        result = computed;
        code.setText(computed.code);
        basis.setText("KEY " + computed.key + "\n기준: " + computed.utcMinute);
        countdown.setText("다음 코드 갱신까지 " + OtpCalculator.secondsToNextCode(System.currentTimeMillis()) + "초");
        setCopyEnabled(true);
        scheduleTick();
    }
    private void clearResult() {
        result = null;
        handler.removeCallbacks(tick);
        code.setText("—");
        basis.setText("KEY를 입력하고 계산을 누르세요.");
        countdown.setText("");
        setCopyEnabled(false);
    }
    private void copyCode() {
        if (result == null) return;
        try {
            String key = OtpCalculator.normalize(keyInput.getText().toString());
            if (!key.equals(result.key)) { clearResult(); return; }
            // Recalculate at the tap itself, including a boundary crossed before the next UI tick.
            show(OtpCalculator.calculate(key,System.currentTimeMillis()));
            ClipboardManager clipboard = getSystemService(ClipboardManager.class);
            if (clipboard == null) throw new IllegalStateException("클립보드에 접근할 수 없습니다.");
            ClipData clip = ClipData.newPlainText("CMC 인증코드",result.code);
            PersistableBundle extras = new PersistableBundle();
            extras.putBoolean("android.content.extra.IS_SENSITIVE",true);
            clip.getDescription().setExtras(extras);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this,"인증코드를 복사했습니다",Toast.LENGTH_SHORT).show();
        } catch (IllegalArgumentException error) { clearResult(); }
        catch (IllegalStateException error) { Toast.makeText(this,error.getMessage(),Toast.LENGTH_LONG).show(); }
    }
    private void setCopyEnabled(boolean enabled) { copy.setEnabled(enabled); copy.setAlpha(enabled ? 1f : 0.45f); }
    private void scheduleTick() {
        handler.removeCallbacks(tick);
        if (resumed && result != null)
            handler.postDelayed(tick,1000L - Math.floorMod(System.currentTimeMillis(),1000L));
    }
    @Override protected void onResume() {
        super.onResume(); resumed = true; updateNow(); scheduleTick();
    }
    @Override protected void onPause() { resumed = false; handler.removeCallbacks(tick); super.onPause(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("typed_key",keyInput.getText().toString());
        if (result != null) state.putString("calculated_key",result.key);
        super.onSaveInstanceState(state);
    }
    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view;
    }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout view = column(); view.setPadding(dp(18),dp(18),dp(18),dp(18));
        view.setBackground(shape(CARD)); add(parent,view,18); return view;
    }
    private TextView text(String value,int size,int color) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setLineSpacing(dp(3),1f); return view;
    }
    private Button button(LinearLayout parent,String label,String description,boolean primary,Runnable action) {
        Button view = new Button(this); view.setText(label); view.setAllCaps(false); view.setTextSize(15);
        view.setContentDescription(description); view.setTextColor(primary ? BG : TEXT);
        view.setMinHeight(dp(50)); view.setPadding(dp(12),dp(10),dp(12),dp(10));
        view.setBackgroundTintList(null); view.setBackground(shape(primary ? ACCENT : Color.rgb(35,51,43)));
        view.setOnClickListener(v -> action.run()); add(parent,view,12); return view;
    }
    private GradientDrawable shape(int color) {
        GradientDrawable value = new GradientDrawable(); value.setColor(color); value.setCornerRadius(dp(16));
        value.setStroke(dp(1),Color.rgb(67,88,76)); return value;
    }
    private void add(LinearLayout parent,View view,int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1,-2); params.topMargin = dp(top); parent.addView(view,params);
    }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
}
