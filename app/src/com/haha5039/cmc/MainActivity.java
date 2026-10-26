package com.haha5039.cmc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

/** Framework-only Korean UI. All setting and journal I/O runs on the application worker. */
public final class MainActivity extends Activity implements CmcApplication.Listener {
    private static final int BG = Color.rgb(16, 23, 21);
    private static final int CARD = Color.rgb(26, 37, 32);
    private static final int TEXT = Color.rgb(240, 246, 243);
    private static final int MUTED = Color.rgb(178, 196, 187);
    private static final int ACCENT = Color.rgb(146, 227, 202);
    private static final int WARNING = Color.rgb(255, 209, 155);
    private static final String GRANT = "adb shell pm grant com.haha5039.cmc android.permission.WRITE_SECURE_SETTINGS";
    private static final String PREPARE = "adb shell am instrument -w --no-hidden-api-checks com.haha5039.cmc/.PrepareInstrumentation";
    private static final String DISABLE = "adb shell am instrument -w --no-hidden-api-checks -e operation disable com.haha5039.cmc/.PrepareInstrumentation";
    private CmcApplication controller;
    private RoleStore.Snapshot current;
    private TextView roleStatus, activationStatus, cmcStatus, advice, backupStatus;
    private TextView setupStatus, prepStatus, resultStatus, transactionStatus;
    private ProgressBar progress;
    private Button receiver, sender, restore, refresh, openNative;
    private final List<Button> operationButtons = new ArrayList<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        controller = (CmcApplication) getApplication();
        getWindow().setNavigationBarContrastEnforced(false);
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(BG);
        screen.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout content = column();
        content.setPadding(dp(20), dp(20), dp(20), dp(28));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
        setContentView(screen);
        screen.requestApplyInsets();

        TextView eyebrow = text("SAMSUNG  /  기기 역할 도우미", 12, ACCENT);
        eyebrow.setLetterSpacing(0.1f);
        content.addView(eyebrow);
        TextView title = text("CMC Helper", 32, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        add(content, title, 8);
        add(content, text("다른 기기에서도 전화와 메시지를", 15, MUTED), 6);

        LinearLayout summary = card(content, "현재 상태", 22);
        roleStatus = identifiedText(summary, "확인 중…", 27, TEXT, "status_role");
        roleStatus.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        cmcStatus = identifiedText(summary, "삼성 CMC 확인 중", 13, MUTED, "status_cmc");
        activationStatus = identifiedText(summary, "", 14, MUTED, "status_activation");
        advice = identifiedText(summary, "역할 값과 실제 연결 상태는 별도로 확인합니다.", 14, ACCENT, "status_guidance");
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setContentDescription("operation_progress");
        progress.setIndeterminateTintList(ColorStateList.valueOf(ACCENT));
        add(summary, progress, 12);
        resultStatus = identifiedText(summary, "", 14, ACCENT, "operation_result");
        resultStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        refresh = button(summary, "상태 새로고침", "refresh_status", false, () -> controller.refresh());
        operationButtons.add(refresh);

        LinearLayout roles = card(content, "CMC 기기 역할", 14);
        add(roles, text("삼성 CMC의 발신·수신 역할을 바꿉니다. 시스템의 태블릿 모드나 화면 배치를 바꾸는 기능은 아닙니다.", 14, MUTED), 8);
        add(roles, text("역할을 바꾼 뒤에는 삼성 CMC의 초기 설정이나 기기 등록을 다시 진행해야 할 수 있습니다.", 13, WARNING), 8);
        add(roles, text("역할 변경 전 네이티브 CMC에서 전체·통화·메시지 사용을 모두 꺼 주세요. 이미 같은 역할이면 설정을 바꾸지 않습니다.", 14, MUTED), 6);
        receiver = button(roles, "이 기기를 수신 기기로", "role_sd", true, () -> controller.apply("sd"));
        add(roles, text("다른 휴대전화의 전화·메시지를 받는 역할", 12, MUTED), 4);
        sender = button(roles, "이 기기를 발신 기기로", "role_pd", false, () -> controller.apply("pd"));
        add(roles, text("전화·메시지를 다른 기기에 제공하는 역할", 12, MUTED), 4);
        restore = button(roles, "원래 역할 복원", "restore_original", false, () -> controller.restore());
        backupStatus = identifiedText(roles, "", 12, MUTED, "status_backup");
        transactionStatus = identifiedText(roles, "", 13, WARNING, "status_transaction");
        operationButtons.add(receiver);
        operationButtons.add(sender);
        operationButtons.add(restore);

        LinearLayout nativeCard = card(content, "연결은 삼성 CMC에서", 14);
        add(nativeCard, text("이 앱은 기기 역할 설정을 돕습니다. 수신 역할이 표시되어도 등록·연결·전화·문자 수신 성공을 뜻하지 않습니다.", 14, MUTED), 6);
        add(nativeCard, text("역할 적용 후 삼성 계정과 연결 기기를 확인하고, 실제 전화와 문자로 동작을 확인하세요.", 14, MUTED), 8);
        openNative = button(nativeCard, "네이티브 CMC 열기", "open_native_cmc", true, this::openNative);
        operationButtons.add(openNative);

        LinearLayout setup = card(content, "처음 설정 · PC 연결", 14);
        setupStatus = identifiedText(setup, "설정 연결 확인 중", 14, ACCENT, "status_permission");
        add(setup, text("1  설정 변경 연결\nUSB 디버깅을 허용한 PC에서 한 번 실행하세요. 앱이 요청하는 권한은 WRITE_SECURE_SETTINGS 하나입니다.", 14, MUTED), 12);
        button(setup, "설정 연결 명령 복사", "copy_permission_command", false,
                () -> copy("설정 연결 명령", GRANT));
        add(setup, text("2  네이티브 CMC 준비\n수신 역할이 발신으로 되돌아가면 삼성 CMC 내부 준비가 필요합니다. 잠금을 해제하고 아래 준비 명령을 PC에서 실행한 뒤 상태를 새로고침하세요.", 14, MUTED), 16);
        button(setup, "준비 안내와 명령", "show_preparation", false, this::showPreparation);
        button(setup, "역할 예외 해제 안내", "show_disable_exception", false, () ->
                showTextDialog("역할 예외 해제", "먼저 삼성 CMC의 전화·메시지 사용을 모두 끄고 이 기기를 발신 기기로 바꾸세요.\n\n"
                        + "이 명령은 역할 예외만 해제합니다. 삼성 개발자 화면의 기본 끄기나 초기화 버튼은 누르지 마세요.\n\n"
                        + DISABLE + "\n\n예외 해제 후에는 휴대폰에서 수신 역할이 유지되지 않습니다.",
                        "disable_exception_instructions", () -> copy("역할 예외 해제 명령", DISABLE)));
        prepStatus = identifiedText(setup, "아직 준비 결과가 없습니다.", 13, MUTED, "preparation_result");
        add(setup, text("준비 결과는 마지막 실행 기록입니다. 현재 연결이나 삼성 내부 테스트 설정을 보증하지 않습니다. 별도 모듈의 영향이 없는지도 확인하세요.", 12, MUTED), 8);
        button(setup, "자세한 상태 보기", "show_status_details", false, this::showDetails);

        TextView footer = text(Build.MANUFACTURER + " " + Build.MODEL + " · Android "
                + Build.VERSION.RELEASE + "\n필요할 때 한 번 적용 · 백그라운드 강제 유지 없음", 12, MUTED);
        add(content, footer, 22);
    }

    @Override protected void onStart() { super.onStart(); controller.attach(this); }
    @Override protected void onResume() { super.onResume(); controller.refresh(); }
    @Override protected void onStop() { controller.detach(this); super.onStop(); }

    @Override public void changed(RoleStore.Snapshot snapshot, boolean busy, String message, boolean error) {
        current = snapshot;
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        for (Button button : operationButtons) {
            button.setEnabled(!busy);
            button.setAlpha(busy ? 0.45f : 1f);
        }
        resultStatus.setText(message);
        resultStatus.setTextColor(error ? WARNING : ACCENT);
        resultStatus.setVisibility(message.isEmpty() ? View.GONE : View.VISIBLE);
        if (snapshot == null) {
            roleStatus.setText(busy ? "확인 중…" : "상태 확인 필요");
            activationStatus.setText("상태를 읽기 전에는 역할을 변경하지 않습니다.");
            receiver.setEnabled(false);
            sender.setEnabled(false);
            restore.setEnabled(false);
            return;
        }
        roleStatus.setText(RoleStore.roleName(snapshot.role));
        cmcStatus.setText(snapshot.cmcInstalled
                ? "삼성 CMC " + snapshot.cmcVersion + (snapshot.cmcEnabled ? "" : " · 비활성화")
                : "삼성 CMC 앱을 찾을 수 없습니다");
        activationStatus.setText("전체 " + activation(snapshot.activation) + "  ·  통화 "
                + activation(snapshot.callActivation) + "  ·  메시지 " + activation(snapshot.messageActivation));
        if (snapshot.transactionPending)
            advice.setText("중단된 작업 기록이 있습니다. 현재 상태를 확인하고 원래 역할을 복원하세요.");
        else if (snapshot.externalChange)
            advice.setText("외부 역할 변경을 감지했습니다. 보호를 위해 자동으로 덮어쓰지 않습니다.");
        else if (snapshot.active())
            advice.setText("CMC 사용 설정이 켜져 있습니다. 실제 연결은 네이티브 CMC에서 확인하세요. 다른 역할로 바꾸려면 먼저 CMC를 꺼 주세요.");
        else if ("sd".equals(snapshot.role))
            advice.setText("수신 역할 값이 설정되어 있습니다. 네이티브 CMC에서 등록과 실제 수신을 확인하세요.");
        else advice.setText("수신 역할이 되돌아간다면 하단의 네이티브 CMC 준비를 확인하세요.");
        backupStatus.setText(snapshot.hasOriginal
                ? "저장된 원래 역할: " + RoleStore.roleName(snapshot.originalRole)
                    + (snapshot.originalPresent ? "" : " · 키가 없던 상태까지 복원")
                : "아직 저장된 원래 역할이 없습니다. 실제 변경 직전에 자동으로 보관합니다.");
        transactionStatus.setText(snapshot.transactionPending ? "복원 확인 필요 · 중단 기록 보존 중"
                : snapshot.externalChange ? "외부 변경 보호 중" : "");
        transactionStatus.setVisibility(transactionStatus.getText().length() == 0 ? View.GONE : View.VISIBLE);
        setupStatus.setText(snapshot.permissionGranted ? "앱에서 역할 변경 가능 (WRITE_SECURE_SETTINGS)"
                : "역할 변경 권한 없음 · 이미 적용한 역할은 유지됩니다");
        prepStatus.setText(snapshot.preparationResult);
        restore.setEnabled(!busy && snapshot.hasOriginal);
        restore.setAlpha(restore.isEnabled() ? 1f : 0.45f);
        // Keep role controls usable for same-role no-ops while active; RoleStore guards writes.
    }

    private static String activation(String value) {
        if (value == null) return "미설정";
        if ("0".equals(value.trim())) return "꺼짐";
        if ("1".equals(value.trim())) return "켜짐";
        return "확인 필요 (" + value + ")";
    }

    private void openNative() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("cmc://setting?said=samsungservice"))
                .setPackage(RoleStore.CMC_PACKAGE);
        try { startActivity(intent); }
        catch (ActivityNotFoundException | SecurityException e) {
            new AlertDialog.Builder(this).setTitle("CMC 화면을 열 수 없습니다")
                    .setMessage("삼성 CMC 설치와 활성화 상태를 확인하세요. 기기 설정에서 ‘다른 기기에서도 전화/문자하기’를 직접 열 수 있습니다.")
                    .setPositiveButton("확인", null).show();
        }
    }

    private void showPreparation() {
        showTextDialog("네이티브 CMC 준비", "PC의 ADB에서 실행하세요.\n\n"
                + "기기 잠금을 풀고 삼성 CMC가 설치되어 있는지 확인하세요. 준비 과정은 삼성 CMC 화면을 열어 내부 설정을 준비합니다. 실행 중에는 화면을 조작하지 마세요.\n\n"
                + PREPARE + "\n\n완료 후 이 앱으로 돌아와 상태를 새로고침하세요. 준비가 완료되어도 연결·전화·문자 수신은 별도 확인이 필요합니다.",
                "preparation_instructions", () -> copy("네이티브 CMC 준비 명령", PREPARE));
    }

    private void showDetails() {
        final String details = current == null ? "상태를 먼저 새로고침하세요." : current.text();
        showTextDialog("자세한 상태", details,
                "status_details", current == null ? null : () -> copy("CMC 상태", details));
    }

    private void showTextDialog(String title, String body, String description, Runnable copyAction) {
        ScrollView scroll = new ScrollView(this);
        TextView text = text(body, 14, TEXT);
        text.setContentDescription(description);
        text.setTextIsSelectable(true);
        text.setPadding(dp(22), dp(14), dp(22), dp(14));
        scroll.addView(text);
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle(title).setView(scroll)
                .setPositiveButton("닫기", null);
        if (copyAction != null) builder.setNeutralButton("복사", (dialog, which) -> copyAction.run());
        AlertDialog dialog = builder.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setContentDescription(description + "_close");
        if (copyAction != null)
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setContentDescription(description + "_copy");
    }

    private void copy(String label, String value) {
        ClipboardManager clipboard = getSystemService(ClipboardManager.class);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
            Toast.makeText(this, "복사했습니다", Toast.LENGTH_SHORT).show();
        }
    }

    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    private LinearLayout card(LinearLayout parent, String title, int topMargin) {
        LinearLayout card = column();
        card.setPadding(dp(20), dp(20), dp(20), dp(20));
        card.setBackground(shape(CARD, 22, Color.rgb(43, 61, 52)));
        add(parent, card, topMargin);
        TextView heading = text(title, 16, TEXT);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setAccessibilityHeading(true);
        card.addView(heading);
        return card;
    }

    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setLineSpacing(dp(3), 1f);
        return view;
    }

    private TextView identifiedText(LinearLayout parent, String value, int size, int color, String id) {
        TextView view = text(value, size, color);
        view.setContentDescription(id);
        view.setTextIsSelectable(true);
        add(parent, view, 10);
        return view;
    }

    private Button button(LinearLayout parent, String label, String id, boolean primary, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setContentDescription(id);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTextColor(primary ? BG : TEXT);
        button.setMinHeight(dp(52));
        button.setMinimumHeight(dp(52));
        button.setPadding(dp(14), dp(10), dp(14), dp(10));
        button.setBackgroundTintList(null);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x337DE8C3),
                shape(primary ? ACCENT : Color.rgb(35, 51, 43), 14,
                        primary ? ACCENT : Color.rgb(67, 88, 76)), null));
        button.setOnClickListener(view -> action.run());
        add(parent, button, 12);
        return button;
    }

    private GradientDrawable shape(int color, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private void add(LinearLayout parent, View view, int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(top);
        parent.addView(view, params);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
