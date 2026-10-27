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
    private static final String SHIZUKU_PACKAGE = "moe.shizuku.privileged.api";
    private static final String SUPPORTED_CMC = "지원: Android 16 이상 · 삼성 CMC 9.1.00.15 (910015100)";
    private CmcApplication controller;
    private RoleStore.Snapshot current;
    private TextView roleStatus, activationStatus, cmcStatus, advice, backupStatus;
    private TextView connectionStatus, preparationStatus, prepStatus, resultStatus, transactionStatus;
    private ProgressBar progress;
    private Button receiver, sender, restore, refresh, openNative, connect, prepareReceiver, disableException;
    private AlertDialog disableDialog;
    private boolean operationBusy;
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
        add(content, text("휴대전화에서 준비하고, 삼성 CMC에서 연결하세요", 15, MUTED), 6);

        LinearLayout connection = card(content, "1 · Shizuku 연결", 22);
        connectionStatus = identifiedText(connection, "연결 상태 확인 중…", 16, ACCENT, "shizuku_status");
        connectionStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        add(connection, text("개발자 옵션에서 무선 디버깅을 켜고 Shizuku를 시작하세요. 이 앱의 권한 요청을 허용하면 휴대전화에서 준비와 역할 변경을 진행할 수 있습니다.", 14, MUTED), 8);
        connect = button(connection, "Shizuku 연결", "shizuku_connect", true, () -> controller.connectShizuku());
        Button openShizuku = button(connection, "Shizuku 열기", "shizuku_open", false, this::openShizuku);
        button(connection, "휴대전화에서 시작하는 방법", "shizuku_help", false, this::showShizukuHelp);
        operationButtons.add(connect);
        operationButtons.add(openShizuku);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setContentDescription("operation_progress");
        progress.setIndeterminateTintList(ColorStateList.valueOf(ACCENT));
        add(connection, progress, 12);
        resultStatus = identifiedText(connection, "", 14, ACCENT, "operation_result");
        resultStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);

        LinearLayout summary = card(content, "현재 CMC 설정", 14);
        roleStatus = identifiedText(summary, "확인 중…", 27, TEXT, "status_role");
        roleStatus.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        cmcStatus = identifiedText(summary, "삼성 CMC 확인 중", 13, MUTED, "status_cmc");
        activationStatus = identifiedText(summary, "", 14, MUTED, "status_activation");
        advice = identifiedText(summary, "CMC 설정을 확인하고 있습니다.", 14, ACCENT, "status_guidance");
        refresh = button(summary, "상태 새로고침", "refresh_status", false, () -> controller.refresh());
        operationButtons.add(refresh);

        LinearLayout preparation = card(content, "2 · 수신 기능 준비", 14);
        add(preparation, text("잠금을 해제한 뒤 준비를 실행하세요. 삼성 CMC의 역할 예외를 켭니다(ON). 완료 후 아래에서 수신 기기 역할을 적용하세요.", 14, MUTED), 8);
        preparationStatus = identifiedText(preparation, SUPPORTED_CMC, 13, MUTED, "status_preparation_support");
        prepareReceiver = button(preparation, "수신 기능 준비", "prepare_receiver", true, () -> controller.prepare(true));
        prepStatus = identifiedText(preparation, "아직 준비 결과가 없습니다.", 13, MUTED, "preparation_result");
        operationButtons.add(prepareReceiver);

        LinearLayout roles = card(content, "3 · CMC 기기 역할", 14);
        add(roles, text("삼성 CMC의 발신·수신 역할을 바꿉니다. 시스템의 태블릿 모드나 화면 배치를 바꾸는 기능은 아닙니다.", 14, MUTED), 8);
        add(roles, text("다른 역할로 바꾸기 전에는 삼성 CMC의 전체·통화·메시지 사용을 모두 꺼 주세요. 이미 같은 역할이면 설정을 바꾸지 않습니다.", 14, MUTED), 8);
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

        LinearLayout nativeCard = card(content, "4 · 삼성 CMC에서 기기 연결", 14);
        add(nativeCard, text("삼성 CMC를 열어 삼성 계정과 연결 기기를 확인하세요. 초기 설정이나 기기 등록이 필요하면 화면의 기기 연결·QR 코드 안내를 따라 진행하세요.", 14, MUTED), 8);
        openNative = button(nativeCard, "삼성 CMC 설정 열기", "open_native_cmc", true, this::openNative);
        button(nativeCard, "QR 코드로 기기 연결하기", "show_qr_pairing", false, this::showQrHelp);
        add(nativeCard, text("위 상태는 CMC의 설정값입니다. 실제 연결·통화·문자 수신 여부는 삼성 CMC와 실제 전화·문자로 확인하세요.", 13, MUTED), 12);
        operationButtons.add(openNative);

        LinearLayout maintenance = card(content, "준비 해제 및 도움말", 14);
        add(maintenance, text("수신 기능 준비가 더 필요 없으면 CMC 사용을 끄고 발신 기기로 바꾼 뒤 역할 예외를 해제하세요.", 14, MUTED), 8);
        disableException = button(maintenance, "역할 예외 해제", "disable_exception", false, this::confirmDisableException);
        operationButtons.add(disableException);
        add(maintenance, text("재부팅 후에는 무선 디버깅으로 Shizuku를 다시 시작해야 할 수 있습니다. CMC 준비와 적용한 역할은 Shizuku 연결과 별도로 저장됩니다.", 13, MUTED), 14);
        add(maintenance, text("새 Shizuku 경로는 아직 기기에서 동작을 검증하지 않았습니다. 준비 결과는 마지막 작업 기록입니다.", 13, WARNING), 10);
        button(maintenance, "자세한 상태 보기", "show_status_details", false, this::showDetails);

        TextView footer = text(Build.MANUFACTURER + " " + Build.MODEL + " · Android "
                + Build.VERSION.RELEASE + "\n필요할 때 한 번 적용 · 백그라운드 강제 유지 없음", 12, MUTED);
        add(content, footer, 22);
    }

    @Override protected void onStart() { super.onStart(); controller.attach(this); }
    @Override protected void onResume() { super.onResume(); controller.refresh(); }
    @Override protected void onStop() { controller.detach(this); super.onStop(); }

    @Override public void changed(RoleStore.Snapshot snapshot, boolean busy, String message, boolean error) {
        current = snapshot;
        operationBusy = busy;
        boolean ready = controller.shizukuReady();
        boolean supported = controller.preparationSupported();
        String connection = controller.connectionStatus();
        connectionStatus.setText(connection == null || connection.trim().isEmpty()
                ? "Shizuku 연결 상태를 확인하세요" : connection);
        connectionStatus.setTextColor(ready ? ACCENT : MUTED);
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        for (Button button : operationButtons) {
            setAvailable(button, !busy);
        }
        boolean canPrepare = !busy && ready && supported && snapshot != null
                && snapshot.cmcInstalled && snapshot.cmcEnabled;
        setAvailable(prepareReceiver, canPrepare);
        setAvailable(disableException, canPrepare);
        if (snapshot == null)
            preparationStatus.setText(SUPPORTED_CMC + "\nCMC 버전을 확인하고 있습니다.");
        else if (!supported)
            preparationStatus.setText(SUPPORTED_CMC + "\n현재 CMC에서는 준비·예외 해제를 사용할 수 없습니다.");
        else if (!ready)
            preparationStatus.setText(SUPPORTED_CMC + "\nShizuku에 먼저 연결하세요.");
        else preparationStatus.setText(SUPPORTED_CMC + "\n잠금을 해제한 상태에서 진행하세요.");
        updateDisableConfirmation();
        String result = message == null ? "" : message;
        resultStatus.setText(result);
        resultStatus.setTextColor(error ? WARNING : ACCENT);
        resultStatus.setVisibility(result.isEmpty() ? View.GONE : View.VISIBLE);
        if (snapshot == null) {
            roleStatus.setText(busy ? "확인 중…" : "상태 확인 필요");
            cmcStatus.setText("삼성 CMC 상태를 새로고침하세요");
            activationStatus.setText("상태를 읽기 전에는 역할을 변경하지 않습니다.");
            advice.setText("Shizuku 연결 상태와 삼성 CMC 설정을 확인하세요.");
            backupStatus.setText("원래 역할 기록을 확인하고 있습니다.");
            transactionStatus.setVisibility(View.GONE);
            prepStatus.setText("준비 결과를 아직 읽지 못했습니다.");
            setAvailable(receiver, false);
            setAvailable(sender, false);
            setAvailable(restore, false);
            return;
        }
        roleStatus.setText(RoleStore.roleName(snapshot.role));
        cmcStatus.setText(snapshot.cmcInstalled
                ? "삼성 CMC " + snapshot.cmcVersion + (snapshot.cmcEnabled ? "" : " · 비활성화")
                : "삼성 CMC 앱을 찾을 수 없습니다");
        activationStatus.setText("사용 설정: 전체 " + activation(snapshot.activation) + "  ·  통화 "
                + activation(snapshot.callActivation) + "  ·  메시지 " + activation(snapshot.messageActivation));
        if (snapshot.transactionPending)
            advice.setText("중단된 작업 기록이 있습니다. 현재 상태를 확인하고 원래 역할을 복원하세요.");
        else if (snapshot.externalChange)
            advice.setText("외부 역할 변경을 감지했습니다. 보호를 위해 자동으로 덮어쓰지 않습니다.");
        else if (snapshot.active())
            advice.setText("CMC 사용 설정이 켜져 있습니다. 다른 역할로 바꾸려면 먼저 삼성 CMC 사용을 꺼 주세요.");
        else if ("sd".equals(snapshot.role))
            advice.setText("수신 역할이 설정되어 있습니다. 다음 단계는 삼성 CMC에서 기기 연결입니다.");
        else advice.setText("수신 역할이 발신으로 되돌아가면 ‘수신 기능 준비’를 실행하세요.");
        backupStatus.setText(snapshot.hasOriginal
                ? "저장된 원래 역할: " + RoleStore.roleName(snapshot.originalRole)
                    + (snapshot.originalPresent ? "" : " · 키가 없던 상태까지 복원")
                : "아직 저장된 원래 역할이 없습니다. 실제 변경 직전에 자동으로 보관합니다.");
        transactionStatus.setText(snapshot.transactionPending ? "복원 확인 필요 · 중단 기록 보존 중"
                : snapshot.externalChange ? "외부 변경 보호 중" : "");
        transactionStatus.setVisibility(transactionStatus.getText().length() == 0 ? View.GONE : View.VISIBLE);
        prepStatus.setText(snapshot.preparationResult);
        boolean canWriteRole = ready || snapshot.permissionGranted;
        // A same-role request is still available without a write connection, even while active.
        // RoleStore remains responsible for all activation, journaling, and conflict guards.
        setAvailable(receiver, !busy && (canWriteRole || "sd".equals(snapshot.role)));
        setAvailable(sender, !busy && (canWriteRole || "pd".equals(snapshot.role)));
        setAvailable(restore, !busy && snapshot.hasOriginal && (canWriteRole
                || java.util.Objects.equals(snapshot.role, snapshot.originalRole)));
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

    private void openShizuku() {
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage(SHIZUKU_PACKAGE);
            if (intent == null) {
                showTextDialog("Shizuku를 찾을 수 없습니다",
                        "Shizuku 앱을 설치하거나 활성화한 뒤 다시 눌러 주세요. 설치되어 있다면 앱 목록에서 Shizuku를 직접 열 수 있습니다.",
                        "shizuku_open_error", null);
                return;
            }
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException error) {
            showTextDialog("Shizuku를 열 수 없습니다",
                    "앱 목록에서 Shizuku를 직접 열어 시작한 뒤 CMC Helper로 돌아와 연결해 주세요.",
                    "shizuku_open_error", null);
        }
    }

    private void showShizukuHelp() {
        showTextDialog("휴대전화에서 시작하기",
                "1. 휴대전화 설정의 개발자 옵션에서 ‘무선 디버깅’을 켭니다.\n\n"
                + "2. Shizuku 앱의 무선 디버깅 안내에 따라 페어링하고 Shizuku를 시작합니다.\n\n"
                + "3. CMC Helper로 돌아와 ‘Shizuku 연결’을 누르고 이 앱의 권한 요청을 허용합니다.\n\n"
                + "4. 기기 잠금을 해제한 상태에서 ‘수신 기능 준비’를 누릅니다. 실행 중에는 화면을 조작하지 마세요.\n\n"
                + "5. 삼성 CMC의 전체·통화·메시지 사용을 모두 끈 뒤 ‘이 기기를 수신 기기로’를 누릅니다. 삼성 CMC를 열어 기기 연결을 진행하세요.\n\n"
                + "Shizuku를 시작한 뒤에는 PC나 터미널 명령 없이 앱의 버튼으로 진행할 수 있습니다.",
                "shizuku_instructions", null);
    }

    private void showQrHelp() {
        showTextDialog("삼성 CMC · QR 기기 연결",
                "1. 역할 적용 후 ‘삼성 CMC 설정 열기’를 눌러 초기 설정과 삼성 계정 확인을 진행하세요. 역할 변경 후에는 기기를 다시 등록해야 할 수 있습니다.\n\n"
                + "2. 연결할 다른 휴대전화에서도 삼성 CMC를 열고 ‘기기 연결’의 QR 코드 안내를 따르세요. QR 코드 표시·스캔은 각 기기의 CMC 화면에서 진행합니다.\n\n"
                + "3. 연결 기기 목록과 사용 설정을 확인한 뒤 실제 전화와 문자로 수신을 확인하세요.",
                "qr_pairing_instructions", null);
    }

    private void confirmDisableException() {
        disableDialog = new AlertDialog.Builder(this).setTitle("역할 예외를 해제할까요?")
                .setMessage("먼저 삼성 CMC의 전체·통화·메시지 사용을 모두 끄고 역할을 발신 기기(pd)로 바꿔 주세요.\n\n"
                        + "역할 예외만 끕니다(OFF). 삼성 CMC의 Reset·초기화는 실행하지 않습니다.\n\n"
                        + "해제 후에는 휴대전화에서 수신 역할이 유지되지 않을 수 있습니다.")
                .setNegativeButton("취소", null)
                .setPositiveButton("예외 해제", (dialog, which) -> {
                    if (canDisableException()) controller.prepare(false);
                    else Toast.makeText(this, "Shizuku 연결과 CMC 꺼짐·발신 역할을 확인하세요.", Toast.LENGTH_LONG).show();
                }).create();
        disableDialog.setOnDismissListener(dialog -> disableDialog = null);
        disableDialog.show();
        disableDialog.getButton(AlertDialog.BUTTON_POSITIVE).setContentDescription("disable_exception_confirm");
        disableDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setContentDescription("disable_exception_cancel");
        updateDisableConfirmation();
    }

    private boolean canDisableException() {
        return !operationBusy && controller.shizukuReady() && controller.preparationSupported()
                && current != null && current.cmcInstalled && current.cmcEnabled
                && !current.active() && "pd".equals(current.role);
    }

    private void updateDisableConfirmation() {
        if (disableDialog != null && disableDialog.isShowing())
            setAvailable(disableDialog.getButton(AlertDialog.BUTTON_POSITIVE), canDisableException());
    }

    private void setAvailable(Button button, boolean available) {
        button.setEnabled(available);
        button.setAlpha(available ? 1f : 0.45f);
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
