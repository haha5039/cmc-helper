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
import android.widget.RadioGroup;
import android.widget.RadioButton;
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
    // Controller messages survive Activity recreation. Do not toast a retained error again.
    private static String lastToastedFailure = "";
    private CmcApplication controller;
    private RoleStore.Snapshot current;
    private TextView roleStatus, activationStatus, cmcStatus, advice, backupStatus;
    private TextView connectionStatus, preparationStatus, prepStatus, resultStatus, roleResultStatus, transactionStatus;
    private ProgressBar progress;
    private Button receiver, sender, restore, refresh, openNative, connect, prepareReceiver, disableException, openDeveloper, nativeApply, acceptNative;
    private AlertDialog disableDialog, roleDialog, developerDialog, informationDialog, nativeApplyDialog, nativePendingDialog;
    private boolean operationBusy, developerSetupDialog;
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
        scroll.setId(R.id.main_scroll);
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
        Button calculator = button(content, "KEY 인증코드 계산기", R.id.open_key_calculator, false,
                () -> startActivity(new Intent(this, OtpCalculatorActivity.class)));
        operationButtons.add(calculator);

        LinearLayout connection = card(content, "1 · Shizuku 연결", 22);
        connectionStatus = identifiedText(connection, "연결 상태 확인 중…", 16, ACCENT, R.id.shizuku_status);
        connectionStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        add(connection, text("개발자 옵션에서 무선 디버깅을 켜고 Shizuku를 시작하세요. 이 앱의 권한 요청을 허용하면 휴대전화에서 준비와 역할 변경을 진행할 수 있습니다.", 14, MUTED), 8);
        connect = button(connection, "Shizuku 연결", R.id.shizuku_connect, true, () -> controller.connectShizuku());
        Button openShizuku = button(connection, "Shizuku 열기", R.id.shizuku_open, false, this::openShizuku);
        button(connection, "휴대전화에서 시작하는 방법", R.id.shizuku_help, false, this::showShizukuHelp);
        operationButtons.add(connect);
        operationButtons.add(openShizuku);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setId(R.id.operation_progress);
        progress.setContentDescription("작업 진행 중");
        progress.setIndeterminateTintList(ColorStateList.valueOf(ACCENT));
        add(connection, progress, 12);
        resultStatus = identifiedText(connection, "", 14, ACCENT, R.id.operation_result);
        resultStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);

        LinearLayout summary = card(content, "현재 CMC 설정", 14);
        roleStatus = identifiedText(summary, "확인 중…", 27, TEXT, R.id.status_role);
        roleStatus.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        cmcStatus = identifiedText(summary, "삼성 CMC 확인 중", 13, MUTED, R.id.status_cmc);
        activationStatus = identifiedText(summary, "", 14, MUTED, R.id.status_activation);
        advice = identifiedText(summary, "CMC 설정을 확인하고 있습니다.", 14, ACCENT, R.id.status_guidance);
        refresh = button(summary, "상태 새로고침", R.id.refresh_status, false, () -> controller.refresh());
        operationButtons.add(refresh);

        LinearLayout roles = card(content, "2 · CMC 기기 역할", 14);
        add(roles, text("삼성 CMC의 발신·수신 역할을 바꿉니다. 시스템의 태블릿 모드나 화면 배치를 바꾸는 기능은 아닙니다.", 14, MUTED), 8);
        add(roles, text("다른 역할을 선택하면 먼저 변경 내용을 확인합니다. Shizuku와 지원 버전에서는 CMC 통화·메시지 사용을 자동으로 끄고 필요한 준비를 함께 진행합니다. 이미 같은 역할이면 설정을 바꾸지 않습니다.", 14, MUTED), 8);
        receiver = button(roles, "이 기기를 수신 기기로", R.id.role_sd, true, () -> requestRole("sd"));
        add(roles, text("다른 휴대전화의 전화·메시지를 받는 역할", 12, MUTED), 4);
        sender = button(roles, "이 기기를 발신 기기로", R.id.role_pd, false, () -> requestRole("pd"));
        add(roles, text("전화·메시지를 다른 기기에 제공하는 역할", 12, MUTED), 4);
        restore = button(roles, "원래 역할 복원", R.id.restore_original, false, () -> controller.restore());
        add(roles,text("일반 전환은 재부팅 없이 역할 값을 적용합니다. 개발자 화면의 선택값은 별도로 보일 수 있습니다.",12,MUTED),8);
        nativeApply=button(roles,"가이드 방식 전환 · 초기화/재부팅",R.id.native_apply_open,false,this::requestNativeApply);
        add(roles,text("인증 후 Device Type 선택과 Apply and Reset APP까지 진행합니다. CMC 등록을 다시 설정할 준비가 필요합니다.",12,MUTED),6);
        acceptNative=button(roles,"현재 상태로 적용 대기 기록 정리",R.id.native_pending_resolve,false,this::confirmAcceptNative);
        operationButtons.add(nativeApply);operationButtons.add(acceptNative);
        roleResultStatus = identifiedText(roles, "", 14, ACCENT, R.id.role_operation_result);
        roleResultStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        backupStatus = identifiedText(roles, "", 12, MUTED, R.id.status_backup);
        transactionStatus = identifiedText(roles, "", 13, WARNING, R.id.status_transaction);
        operationButtons.add(receiver);
        operationButtons.add(sender);
        operationButtons.add(restore);

        LinearLayout nativeCard = card(content, "3 · 삼성 CMC에서 기기 연결", 14);
        add(nativeCard, text("삼성 CMC를 열어 삼성 계정과 연결 기기를 확인하세요. 초기 설정이나 기기 등록이 필요하면 화면의 기기 연결·QR 코드 안내를 따라 진행하세요.", 14, MUTED), 8);
        openNative = button(nativeCard, "삼성 CMC 설정 열기", R.id.open_native_cmc, true, this::openNative);
        button(nativeCard, "QR 코드로 기기 연결하기", R.id.show_qr_pairing, false, this::showQrHelp);
        add(nativeCard, text("위 상태는 CMC의 설정값입니다. 실제 연결·통화·문자 수신 여부는 삼성 CMC와 실제 전화·문자로 확인하세요.", 13, MUTED), 12);
        operationButtons.add(openNative);

        LinearLayout preparation = card(content, "수신 기능 준비 · 별도 실행", 14);
        add(preparation, text("수신 역할로 변경할 때 필요한 준비도 함께 진행합니다. 준비만 따로 실행하려면 잠금을 해제한 뒤 누르세요. 삼성 CMC의 역할 예외를 켭니다(ON).", 14, MUTED), 8);
        preparationStatus = identifiedText(preparation, SUPPORTED_CMC, 13, MUTED, R.id.status_preparation_support);
        prepareReceiver = button(preparation, "수신 기능 준비", R.id.prepare_receiver, true, () -> controller.prepare(true));
        prepStatus = identifiedText(preparation, "아직 준비 결과가 없습니다.", 13, MUTED, R.id.preparation_result);
        operationButtons.add(prepareReceiver);

        LinearLayout maintenance = card(content, "준비 해제 및 도움말", 14);
        add(maintenance, text("수신 기능 준비가 더 필요 없으면 CMC 사용을 끄고 발신 기기로 바꾼 뒤 역할 예외를 해제하세요.", 14, MUTED), 8);
        disableException = button(maintenance, "역할 예외 해제", R.id.disable_exception, false, this::confirmDisableException);
        operationButtons.add(disableException);
        openDeveloper = button(maintenance, "삼성 CMC 개발자 화면 열기", R.id.open_developer, false, this::confirmOpenDeveloper);
        operationButtons.add(openDeveloper);
        add(maintenance, text("삼성 CMC의 인증 절차를 거쳐 개발자 화면을 엽니다.", 12, MUTED), 6);
        add(maintenance, text("재부팅 후에는 무선 디버깅으로 Shizuku를 다시 시작해야 할 수 있습니다. CMC 준비와 적용한 역할은 Shizuku 연결과 별도로 저장됩니다.", 13, MUTED), 14);
        add(maintenance, text("준비 결과는 마지막 작업 기록입니다. 실제 통화·문자 수신은 기기 연결 후 별도로 확인하세요.", 13, MUTED), 10);
        button(maintenance, "자세한 상태 보기", R.id.show_status_details, false, this::showDetails);

        TextView footer = text(Build.MANUFACTURER + " " + Build.MODEL + " · Android "
                + Build.VERSION.RELEASE + "\n필요할 때 한 번 적용 · 백그라운드 강제 유지 없음", 12, MUTED);
        add(content, footer, 22);
    }

    @Override protected void onStart() { super.onStart(); controller.attach(this); }
    @Override protected void onResume() { super.onResume(); controller.refresh(); }
    @Override protected void onStop() { controller.detach(this); super.onStop(); }

    @Override protected void onDestroy() {
        for (AlertDialog dialog : new AlertDialog[]{disableDialog,roleDialog,developerDialog,informationDialog,nativeApplyDialog,nativePendingDialog})
            if (dialog != null) dialog.dismiss();
        super.onDestroy();
    }

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
        boolean nativePending=snapshot!=null && snapshot.nativeResetPending;
        boolean canPrepare = !busy && ready && supported && snapshot != null
                && snapshot.cmcInstalled && snapshot.cmcEnabled && !nativePending;
        setAvailable(nativeApply,canPrepare);
        boolean external=snapshot!=null && snapshot.externalChange && !snapshot.transactionPending;
        acceptNative.setVisibility(nativePending || external ? View.VISIBLE : View.GONE);
        acceptNative.setText(nativePending?"현재 상태로 적용 대기 기록 정리":"현재 역할로 기록 기준 맞추기");
        setAvailable(acceptNative,!busy && (nativePending || external));
        if (nativeApplyDialog!=null && nativeApplyDialog.isShowing())
            setAvailable(nativeApplyDialog.getButton(AlertDialog.BUTTON_POSITIVE),canPrepare && "1".equals(snapshot.oobe));
        if (nativePendingDialog!=null && nativePendingDialog.isShowing())
            setAvailable(nativePendingDialog.getButton(AlertDialog.BUTTON_POSITIVE),!busy && (nativePending || external));
        setAvailable(prepareReceiver, canPrepare);
        setAvailable(disableException, canPrepare);
        setAvailable(openDeveloper, canPrepare);
        if (snapshot == null)
            preparationStatus.setText(SUPPORTED_CMC + "\nCMC 버전을 확인하고 있습니다.");
        else if (!supported)
            preparationStatus.setText(SUPPORTED_CMC + "\n현재 CMC에서는 준비·예외 해제를 사용할 수 없습니다.");
        else if (!ready)
            preparationStatus.setText(SUPPORTED_CMC + "\nShizuku에 먼저 연결하세요.");
        else preparationStatus.setText(SUPPORTED_CMC + "\n잠금을 해제한 상태에서 진행하세요.");
        updateDisableConfirmation();
        updateRoleDialog();
        updateDeveloperDialog();
        if (busy && !error) lastToastedFailure="";
        String result = message == null ? "" : message;
        resultStatus.setText(result);
        resultStatus.setTextColor(error ? WARNING : ACCENT);
        resultStatus.setVisibility(result.isEmpty() ? View.GONE : View.VISIBLE);
        roleResultStatus.setText(result);
        roleResultStatus.setTextColor(error ? WARNING : ACCENT);
        roleResultStatus.setVisibility(result.isEmpty() ? View.GONE : View.VISIBLE);
        if (!busy && error && !result.trim().isEmpty() && !result.equals(lastToastedFailure)) {
            lastToastedFailure = result;
            Toast.makeText(this, result, Toast.LENGTH_LONG).show();
        }
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
        if (!snapshot.cmcInstalled)
            advice.setText("이 기기에서 삼성 CMC를 찾을 수 없습니다. CMC가 설치된 지원 갤럭시에서 역할을 변경하세요. KEY 계산기는 사용할 수 있습니다.");
        else if (!snapshot.cmcEnabled)
            advice.setText("삼성 CMC가 비활성화되어 있습니다. 기기 설정에서 활성화한 뒤 다시 확인하세요.");
        else if (snapshot.nativeResetPending)
            advice.setText("초기화 적용 요청이 기록되어 있습니다. 재부팅 후 상태를 확인하세요. 요청은 자동 재실행하지 않습니다.");
        else if (snapshot.transactionPending)
            advice.setText("중단된 작업 기록이 있습니다. 현재 상태를 확인하고 원래 역할을 복원하세요.");
        else if (snapshot.externalChange)
            advice.setText("외부 역할 변경을 감지했습니다. 현재 역할을 앱의 관리 기준으로 확인한 뒤 계속할 수 있습니다.");
        else if (snapshot.active())
            advice.setText(ready && supported
                    ? "역할 변경을 확인하면 CMC 통화·메시지 사용을 자동으로 끄고 진행합니다."
                    : "CMC 사용 설정이 켜져 있습니다. 역할 버튼에서 Shizuku 연결 또는 직접 끄는 방법을 확인하세요.");
        else if ("sd".equals(snapshot.role))
            advice.setText("수신 역할이 설정되어 있습니다. 다음 단계는 삼성 CMC에서 기기 연결입니다.");
        else advice.setText("수신 역할이 발신으로 되돌아가면 ‘수신 기능 준비’를 실행하세요.");
        backupStatus.setText(snapshot.hasOriginal
                ? "저장된 원래 역할: " + RoleStore.roleName(snapshot.originalRole)
                    + (snapshot.originalPresent ? "" : " · 키가 없던 상태까지 복원")
                : "아직 저장된 원래 역할이 없습니다. 실제 변경 직전에 자동으로 보관합니다.");
        transactionStatus.setText(snapshot.nativeResetPending ? "초기화 적용 결과 확인 대기 중"
                : snapshot.transactionPending ? "복원 확인 필요 · 중단 기록 보존 중"
                : snapshot.externalChange ? "외부 변경 보호 중" : "");
        transactionStatus.setVisibility(transactionStatus.getText().length() == 0 ? View.GONE : View.VISIBLE);
        prepStatus.setText(snapshot.preparationResult);
        boolean canWriteRole = ready || snapshot.permissionGranted;
        setAvailable(openNative, !busy && snapshot.cmcInstalled && snapshot.cmcEnabled);
        // Keep role buttons available for no-ops and actionable connection/manual-off help.
        // The controller and RoleStore enforce the native-off, journal, and write guards.
        setAvailable(receiver, !busy && !nativePending);
        setAvailable(sender, !busy && !nativePending);
        setAvailable(restore, !busy && !nativePending && !snapshot.externalChange && snapshot.hasOriginal && (canWriteRole
                || java.util.Objects.equals(snapshot.role, snapshot.originalRole)));
    }

    private static String activation(String value) {
        if (value == null) return "미설정";
        if ("0".equals(value.trim())) return "꺼짐";
        if ("1".equals(value.trim())) return "켜짐";
        return "확인 필요 (" + value + ")";
    }

    private void requestRole(String role) {
        if (operationBusy || (roleDialog != null && roleDialog.isShowing())) return;
        if (current == null) {
            showTextDialog("상태를 먼저 확인하세요", "상태를 새로고침한 뒤 역할 버튼을 다시 눌러 주세요.",
                    R.id.role_switch_status_required, null);
            return;
        }
        if (!current.cmcInstalled || !current.cmcEnabled) {
            showTextDialog("삼성 CMC를 확인하세요", current.cmcInstalled
                    ? "삼성 CMC가 비활성화되어 있습니다. 기기 설정에서 활성화한 뒤 상태를 새로고침하세요."
                    : "이 기기에서 삼성 CMC를 찾을 수 없습니다. CMC가 설치된 지원 갤럭시에서 역할을 변경하세요. KEY 계산기는 Shizuku와 CMC 없이 사용할 수 있습니다.",
                    R.id.cmc_unavailable, null);
            return;
        }
        if (role.equals(current.role)) {
            controller.apply(role); // Same-role requests never need confirmation or a write.
            return;
        }
        boolean automatic = controller.shizukuReady() && controller.preparationSupported();
        if ((current.active() && !automatic)
                || (!controller.shizukuReady() && !current.permissionGranted)) {
            showRoleConnectionHelp();
            return;
        }
        String target = RoleStore.roleName(role);
        String workflow = automatic
                ? "현재 삼성 CMC의 통화·메시지 사용을 자동으로 끈 다음 필요한 준비와 역할 변경을 진행합니다. 기기 잠금을 해제하고 진행 중에는 화면을 조작하지 마세요."
                : "삼성 CMC의 전체·통화·메시지 사용이 꺼져 있는지 다시 확인한 뒤 역할을 변경합니다.";
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(target + "로 바꿀까요?")
                .setMessage("대상 역할: " + target + " (" + role + ")\n\n" + workflow
                        + "\n\n변경 후 삼성 CMC 등록이나 QR 기기 연결을 다시 진행해야 할 수 있습니다. CMC 사용을 자동으로 다시 켜지 않습니다."
                        + "\n\n데이터 초기화나 재부팅은 하지 않습니다.")
                .setNegativeButton("취소", null)
                .setPositiveButton(target + "로 변경", (dialog, which) -> {
                    if (!operationBusy) controller.applyConfirmed(role);
                });
        showRoleDialog(builder, "sd".equals(role) ? R.id.role_sd_confirm : R.id.role_pd_confirm);
    }

    private void requestNativeApply() {
        if (operationBusy || current==null || current.nativeResetPending || !controller.shizukuReady()
                || !controller.preparationSupported() || !current.cmcInstalled || !current.cmcEnabled) return;
        if (current.externalChange && !current.transactionPending) { confirmAcceptNative();return; }
        if (!"1".equals(current.oobe)) {
            showTextDialog("삼성 CMC 초기 설정이 필요합니다",
                    "먼저 삼성 CMC 초기 설정을 완료하세요. 기존 발신 폰을 이 기기로 교체한다는 창이 나오면 연결에 영향을 주므로 변경 여부를 직접 결정해야 합니다.",R.id.native_setup_help,null);
            return;
        }
        if (nativeApplyDialog!=null && nativeApplyDialog.isShowing()) return;
        LinearLayout content=column();content.setPadding(dp(22),dp(8),dp(22),dp(8));
        add(content,text("선택한 역할을 개발자 화면에서 지정하고 Apply and Reset APP을 누릅니다. CMC의 등록·설정이 초기화되고 휴대전화가 재부팅될 수 있습니다. 기존 발신 폰과 연결 기기의 연동에 영향을 줄 수 있으며, 원래 역할 복원만으로 이 초기화를 되돌릴 수 없습니다.",14,TEXT),0);
        RadioGroup choices=new RadioGroup(this);
        RadioButton pd=new RadioButton(this);pd.setId(R.id.native_role_pd);pd.setText("발신 기기 (pd)");pd.setTextColor(TEXT);
        RadioButton sd=new RadioButton(this);sd.setId(R.id.native_role_sd);sd.setText("수신 기기 (sd)");sd.setTextColor(TEXT);
        choices.addView(pd);choices.addView(sd);choices.check("pd".equals(current.role)?R.id.native_role_pd:R.id.native_role_sd);
        add(content,choices,12);
        nativeApplyDialog=new AlertDialog.Builder(this).setTitle("가이드 방식으로 적용")
                .setView(content).setNegativeButton("취소",null)
                .setPositiveButton("초기화 후 역할 적용",(dialog,which) -> {
                    if (!operationBusy && current!=null && !current.nativeResetPending && controller.shizukuReady())
                        controller.applyViaDeveloper(choices.getCheckedRadioButtonId()==R.id.native_role_pd?"pd":"sd");
                }).create();
        nativeApplyDialog.setOnDismissListener(ignored -> nativeApplyDialog=null);
        nativeApplyDialog.show();
        nativeApplyDialog.getButton(AlertDialog.BUTTON_POSITIVE).setId(R.id.native_apply_confirm);
        Runnable updateLabel=() -> {
            if (nativeApplyDialog!=null) nativeApplyDialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("초기화 후 "+RoleStore.roleName(choices.getCheckedRadioButtonId()==R.id.native_role_pd?"pd":"sd")+" 적용");
        };
        choices.setOnCheckedChangeListener((group,id) -> updateLabel.run());updateLabel.run();
        nativeApplyDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setId(R.id.native_apply_cancel);
    }

    private void confirmAcceptNative() {
        if (operationBusy || current==null || (!current.nativeResetPending && !current.externalChange)) return;
        if (nativePendingDialog!=null && nativePendingDialog.isShowing()) return;
        final boolean waiting=current.nativeResetPending;final String reviewedRole=current.role;
        String body=waiting
                ? "현재 역할: "+RoleStore.roleName(reviewedRole)+"\n요청 역할: "+RoleStore.roleName(current.transactionTarget)
                    +"\n\n기기 설정을 쓰지 않고 현재 역할을 앱의 기준으로 채택합니다. 초기화·재부팅 요청은 다시 보내지 않습니다. CMC 등록·설정의 복구는 삼성 CMC에서 직접 진행해야 합니다. 재부팅 전에는 처리 중인 요청을 보호하기 위해 2분 동안 기록 정리를 제한합니다."
                : "현재 역할: "+RoleStore.roleName(reviewedRole)+"\n저장된 원래 역할: "+RoleStore.roleName(current.originalRole)
                    +"\n\n다른 곳에서 바꾼 현재 역할을 앱의 관리 기준으로 확인합니다. 기기 설정을 쓰거나 CMC를 초기화하지 않으며, 저장된 원래 역할도 유지합니다.";
        nativePendingDialog=new AlertDialog.Builder(this).setTitle(waiting?"현재 상태로 대기 기록을 정리할까요?":"현재 역할로 기록 기준을 맞출까요?")
                .setMessage(body).setNegativeButton("취소",null).setPositiveButton("현재 상태 확인",(dialog,which) -> {
                    if (!operationBusy && current!=null && current.nativeResetPending==waiting
                            && java.util.Objects.equals(current.role,reviewedRole)) {
                        if (waiting) controller.acceptNativeState();else controller.adoptCurrentRole(reviewedRole);
                    }
                }).create();
        nativePendingDialog.setOnDismissListener(ignored -> nativePendingDialog=null);nativePendingDialog.show();
        nativePendingDialog.getButton(AlertDialog.BUTTON_POSITIVE).setId(R.id.native_pending_confirm);
        nativePendingDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setId(R.id.native_pending_cancel);
    }

    private void showRoleConnectionHelp() {
        boolean needsConnection = !controller.shizukuReady();
        String message;
        if (needsConnection) {
            message = current != null && current.active()
                    ? "CMC의 통화·메시지 사용이 켜져 있습니다. 자동으로 끄고 역할을 바꾸려면 Shizuku를 먼저 연결하세요."
                    : "역할을 변경하려면 Shizuku를 먼저 연결하세요.";
            message += "\n\n이 앱의 Shizuku 권한을 허용한 뒤 역할 버튼을 다시 눌러 주세요.";
        } else {
            message = "현재 CMC 버전에서는 사용 설정을 자동으로 끌 수 없습니다. 삼성 CMC에서 전체·통화·메시지 사용을 모두 끈 뒤 역할 버튼을 다시 눌러 주세요.\n\n" + SUPPORTED_CMC;
        }
        if (needsConnection && current != null && current.active())
            message += "\n\n기존 역할 변경 권한이 있다면 삼성 CMC에서 전체·통화·메시지 사용을 직접 끈 뒤 진행할 수도 있습니다.";
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(needsConnection ? "Shizuku 연결이 필요합니다" : "삼성 CMC를 먼저 꺼 주세요")
                .setMessage(message)
                .setNegativeButton("취소", null);
        if (needsConnection) {
            builder.setPositiveButton("Shizuku 연결", (dialog, which) -> {
                if (!operationBusy) controller.connectShizuku();
            });
            builder.setNeutralButton("삼성 CMC 열기", (dialog, which) -> {
                if (!operationBusy) openNative();
            });
        } else builder.setPositiveButton("삼성 CMC 열기", (dialog, which) -> {
            if (!operationBusy) openNative();
        });
        showRoleDialog(builder, needsConnection ? R.id.role_switch_connect : R.id.role_switch_open_native);
    }

    private void showRoleDialog(AlertDialog.Builder builder, int confirmId) {
        roleDialog = builder.create();
        roleDialog.setOnDismissListener(dialog -> roleDialog = null);
        roleDialog.show();
        roleDialog.getButton(AlertDialog.BUTTON_POSITIVE).setId(confirmId);
        roleDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setId(R.id.role_switch_cancel);
        Button alternate = roleDialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        if (alternate != null) alternate.setId(R.id.role_switch_open_native);
        updateRoleDialog();
    }

    private void updateRoleDialog() {
        if (roleDialog == null || !roleDialog.isShowing()) return;
        setAvailable(roleDialog.getButton(AlertDialog.BUTTON_POSITIVE), !operationBusy);
        Button alternate = roleDialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        if (alternate != null) setAvailable(alternate, !operationBusy);
    }

    private void openNative() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("cmc://setting?said=samsungservice"))
                .setPackage(RoleStore.CMC_PACKAGE);
        try { startActivity(intent); }
        catch (ActivityNotFoundException | SecurityException e) {
            showTextDialog("CMC 화면을 열 수 없습니다",
                    "삼성 CMC 설치와 활성화 상태를 확인하세요. 기기 설정에서 ‘다른 기기에서도 전화/문자하기’를 직접 열 수 있습니다.",
                    R.id.cmc_open_error, null);
        }
    }

    private void openShizuku() {
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage(SHIZUKU_PACKAGE);
            if (intent == null) {
                showTextDialog("Shizuku를 찾을 수 없습니다",
                        "Shizuku 앱을 설치하거나 활성화한 뒤 다시 눌러 주세요. 설치되어 있다면 앱 목록에서 Shizuku를 직접 열 수 있습니다.",
                        R.id.shizuku_open_error, null);
                return;
            }
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException error) {
            showTextDialog("Shizuku를 열 수 없습니다",
                    "앱 목록에서 Shizuku를 직접 열어 시작한 뒤 CMC Helper로 돌아와 연결해 주세요.",
                    R.id.shizuku_open_error, null);
        }
    }

    private void showShizukuHelp() {
        showTextDialog("휴대전화에서 시작하기",
                "1. 휴대전화 설정의 개발자 옵션에서 ‘무선 디버깅’을 켭니다.\n\n"
                + "2. Shizuku 앱의 무선 디버깅 안내에 따라 페어링하고 Shizuku를 시작합니다.\n\n"
                + "3. CMC Helper로 돌아와 ‘Shizuku 연결’을 누르고 이 앱의 권한 요청을 허용합니다.\n\n"
                + "4. 잠금을 해제하고 ‘이 기기를 수신 기기로’를 누른 뒤 변경 내용을 확인합니다. 지원 버전에서는 CMC 통화·메시지 사용을 자동으로 끄고 수신 준비와 역할 변경을 진행합니다. 실행 중에는 화면을 조작하지 마세요.\n\n"
                + "5. 삼성 CMC를 열어 등록·QR 기기 연결과 사용 설정을 직접 진행하세요. 역할 변경 후 CMC 사용을 자동으로 다시 켜지 않습니다.\n\n"
                + "Shizuku를 시작한 뒤에는 PC나 터미널 명령 없이 앱의 버튼으로 진행할 수 있습니다.",
                R.id.shizuku_instructions, null);
    }

    private void showQrHelp() {
        showTextDialog("삼성 CMC · QR 기기 연결",
                "1. 역할 적용 후 ‘삼성 CMC 설정 열기’를 눌러 초기 설정과 삼성 계정 확인을 진행하세요. 역할 변경 후에는 기기를 다시 등록해야 할 수 있습니다.\n\n"
                + "2. 연결할 다른 휴대전화에서도 삼성 CMC를 열고 ‘기기 연결’의 QR 코드 안내를 따르세요. QR 코드 표시·스캔은 각 기기의 CMC 화면에서 진행합니다.\n\n"
                + "3. 연결 기기 목록과 사용 설정을 확인한 뒤 실제 전화와 문자로 수신을 확인하세요.",
                R.id.qr_pairing_instructions, null);
    }

    private void confirmOpenDeveloper() {
        if (!canOpenDeveloper() || (developerDialog != null && developerDialog.isShowing())) return;
        final boolean setup = needsCmcSetup();
        developerSetupDialog = setup;
        developerDialog = new AlertDialog.Builder(this)
                .setTitle(setup ? "삼성 CMC 초기 설정이 필요합니다" : "삼성 CMC 개발자 화면 열기")
                .setMessage(setup
                        ? "먼저 삼성 CMC를 열어 초기 설정을 완료하세요. 이 앱으로 돌아와 상태를 새로고침한 뒤 개발자 화면 열기를 다시 눌러 주세요."
                        : "인증을 거쳐 삼성 CMC의 개발자 화면을 엽니다. 이 바로가기는 화면의 스위치나 버튼을 누르지 않습니다.\n\n"
                            + "화면을 열 때 기존 체크 상태에 따라 역할 예외에 영향이 생길 수 있습니다.\n\n"
                            + "화면의 OFF 또는 ‘Apply and Reset’을 누르면 CMC가 초기화되거나 기기가 재부팅될 수 있습니다.")
                .setNegativeButton("취소", null)
                .setPositiveButton(setup ? "삼성 CMC 열기" : "개발자 화면 열기", (dialog, which) -> {
                    if (setup) {
                        if (!operationBusy) openNative();
                    } else if (canOpenDeveloper() && !needsCmcSetup()) {
                        controller.openDeveloper();
                    } else {
                        Toast.makeText(this, "CMC 초기 설정과 Shizuku 연결 상태를 확인하세요.", Toast.LENGTH_LONG).show();
                    }
                }).create();
        developerDialog.setOnDismissListener(dialog -> developerDialog = null);
        developerDialog.show();
        developerDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setId(setup ? R.id.open_developer_setup : R.id.open_developer_confirm);
        developerDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setId(R.id.open_developer_cancel);
        updateDeveloperDialog();
    }

    private boolean canOpenDeveloper() {
        return !operationBusy && controller.shizukuReady() && controller.preparationSupported()
                && current != null && current.cmcInstalled && current.cmcEnabled && !current.nativeResetPending;
    }

    private boolean needsCmcSetup() {
        return current != null && "0".equals(current.oobe);
    }

    private void updateDeveloperDialog() {
        if (developerDialog != null && developerDialog.isShowing())
            setAvailable(developerDialog.getButton(AlertDialog.BUTTON_POSITIVE), !operationBusy
                    && (developerSetupDialog || (canOpenDeveloper() && !needsCmcSetup())));
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
        disableDialog.getButton(AlertDialog.BUTTON_POSITIVE).setId(R.id.disable_exception_confirm);
        disableDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setId(R.id.disable_exception_cancel);
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
                R.id.status_details, current == null ? null : () -> copy("CMC 상태", details));
    }

    private void showTextDialog(String title, String body, int viewId, Runnable copyAction) {
        ScrollView scroll = new ScrollView(this);
        TextView text = text(body, 14, TEXT);
        text.setId(viewId);
        text.setSaveEnabled(false);
        text.setTextIsSelectable(true);
        text.setPadding(dp(22), dp(14), dp(22), dp(14));
        scroll.addView(text);
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle(title).setView(scroll)
                .setPositiveButton("닫기", null);
        if (copyAction != null) builder.setNeutralButton("복사", (dialog, which) -> copyAction.run());
        if (informationDialog != null) informationDialog.dismiss();
        AlertDialog dialog = builder.create();
        informationDialog = dialog;
        dialog.setOnDismissListener(ignored -> { if (informationDialog == dialog) informationDialog = null; });
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setId(R.id.dialog_close);
        if (copyAction != null)
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setId(R.id.dialog_copy);
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

    private TextView identifiedText(LinearLayout parent, String value, int size, int color, int id) {
        TextView view = text(value, size, color);
        view.setId(id);
        view.setSaveEnabled(false);
        view.setTextIsSelectable(true);
        add(parent, view, 10);
        return view;
    }

    private Button button(LinearLayout parent, String label, int id, boolean primary, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setId(id);
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
