package com.haha5039.cmc;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Keeps a finite operation alive across Activity recreation without retaining its views. */
public final class CmcApplication extends Application {
    interface Listener { void changed(RoleStore.Snapshot snapshot, boolean busy, String message, boolean error); }
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private RoleStore store;
    private ShizukuSession shizuku;
    private Listener listener;
    private RoleStore.Snapshot snapshot;
    private boolean busy, error;
    private String message = "";
    private volatile int helperTask = -1;
    private volatile String nativeResetNonce,nativeResetRole,nativeResetBeforeRole;
    private boolean nativeResetPressClaimed;

    @Override public void onCreate() {
        super.onCreate();
        shizuku = new ShizukuSession(this, this::publish);
        store = new RoleStore(this, shizuku);
        shizuku.start();
    }

    void attach(Listener listener) {
        this.listener = listener;
        if (listener instanceof android.app.Activity) helperTask = ((android.app.Activity) listener).getTaskId();
        publish();
    }
    void detach(Listener listener) { if (this.listener == listener) this.listener = null; }
    void refresh() { shizuku.refresh(); execute("refresh", null); }
    void apply(String role) { execute("apply", role); }
    void applyConfirmed(String role) { execute("apply-confirmed", role); }
    void restore() { execute("restore", null); }
    void applyViaDeveloper(String role) { execute("native-apply",role); }
    void acceptNativeState() { execute("accept-native",null); }
    void adoptCurrentRole(String role) { execute("adopt-current",role); }
    void connectShizuku() { if (!busy) shizuku.authorize(); }
    String connectionStatus() { return shizuku.label(); }
    boolean shizukuReady() { return shizuku.available(); }
    boolean preparationSupported() {
        if (android.os.Build.VERSION.SDK_INT < 36) return false;
        try {
            android.content.pm.PackageInfo info = getPackageManager().getPackageInfo(RoleStore.CMC_PACKAGE, 0);
            return info.getLongVersionCode() == 910015100L && "9.1.00.15".equals(info.versionName);
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) { return false; }
    }
    void prepare(boolean enable) { execute(enable ? "prepare" : "disable", null); }
    void openDeveloper() { execute("developer", null); }
    int launchForPreparation(String request, boolean developer, int task) throws Exception {
        return shizuku.launchNative(request, developer, task);
    }
    int preparationTaskId() { return helperTask; }
    synchronized boolean nativeApplyAuthorized(String request,String role) {
        return request!=null && request.equals(nativeResetNonce) && role.equals(nativeResetRole)
                && !nativeResetPressClaimed && shizuku.nativeApplyApproved()
                && java.util.Objects.equals(nativeResetBeforeRole,
                        android.provider.Settings.Global.getString(getContentResolver(),RoleStore.ROLE_KEY));
    }
    synchronized boolean claimNativeApply(String request,String role) {
        if (!nativeApplyAuthorized(request,role)) return false;
        nativeResetPressClaimed=true;return true;
    }

    private void execute(String operation, String role) {
        if (busy || (!"refresh".equals(operation) && shizuku.busy())) return;
        busy = true;
        if (!"refresh".equals(operation)) {
            message = "prepare".equals(operation) || "disable".equals(operation)
                    ? "삼성 CMC 화면을 준비하고 있습니다. 완료될 때까지 화면을 조작하지 마세요."
                    : "역할을 확인하고 있습니다. 변경이 필요한 경우 약 3초 동안 검증합니다.";
            error = false;
        }
        publish();
        worker.execute(() -> {
            RoleStore.Snapshot next = null;
            String result = message;
            String advisory = "";
            boolean failed = error;
            try {
                if ("native-apply".equals(operation)) {
                    if (!preparationSupported() || !shizuku.available())
                        throw new java.io.IOException("지원 CMC 버전과 Shizuku 연결을 확인하세요.");
                    String request=java.util.UUID.randomUUID().toString().replace("-", "");
                    RoleStore.Snapshot before=store.stageNativeReset(role,request);
                    synchronized (this) {
                        nativeResetNonce=request;nativeResetRole=role;nativeResetBeforeRole=before.role;nativeResetPressClaimed=false;
                    }
                    try {
                        boolean accepted=shizuku.applyNativeRole(role,request);
                        if (!accepted) {
                            store.cancelNativeResetBeforeClick(request);
                            throw new java.io.IOException("적용 버튼을 누르기 전에 중단했습니다. CMC 화면과 초기 설정 상태를 확인하세요.");
                        }
                        result="삼성 CMC에 초기화 적용을 요청했습니다. 재부팅과 초기 설정 후 이 앱을 다시 열어 결과를 확인하세요.";
                        next=store.snapshot();failed=false;
                    } finally {
                        synchronized (this) { nativeResetNonce=null;nativeResetRole=null;nativeResetBeforeRole=null; }
                    }
                } else if ("adopt-current".equals(operation)) {
                    next=store.adoptExternalRole(role);
                } else if ("accept-native".equals(operation)) {
                    next=store.acceptNativeResetState();
                } else if ("developer".equals(operation)) {
                    if (store.snapshot().nativeResetPending) throw new java.io.IOException("초기화 요청 결과를 먼저 확인하세요.");
                    if (!preparationSupported()) throw new java.io.IOException("이 기기의 CMC에서는 개발자 바로가기를 지원하지 않습니다.");
                    result = shizuku.openDeveloper(java.util.UUID.randomUUID().toString().replace("-", ""));
                    next = store.snapshot();
                    failed = false;
                } else if ("prepare".equals(operation) || "disable".equals(operation)) {
                    if (!preparationSupported()) throw new java.io.IOException("삼성 CMC 9.1.00.15와 Android 16 이상에서 준비 기능을 지원합니다.");
                    RoleStore.Snapshot before = store.snapshot();
                    if (before.transactionPending) throw new java.io.IOException("중단된 역할 변경을 먼저 복원하세요.");
                    if ("disable".equals(operation) && (before.active() || !"pd".equals(before.role)))
                        throw new java.io.IOException("CMC 전화·메시지를 끄고 발신 역할로 바꾼 뒤 예외를 해제하세요.");
                    result = shizuku.prepare("prepare".equals(operation), java.util.UUID.randomUUID().toString().replace("-", ""));
                    next = store.snapshot();
                    failed = false;
                } else if ("apply".equals(operation) || "apply-confirmed".equals(operation)) {
                    // Reject interrupted/conflicting/unrestorable states before native UI
                    // preparation can turn off calls or messages or enable the exception.
                    RoleStore.Snapshot before = store.preflight(role);
                    if (!java.util.Objects.equals(before.role, role) && !"apply-confirmed".equals(operation))
                        throw new java.io.IOException("현재 역할이 바뀌었습니다. 상태를 새로고침한 뒤 전환 내용을 다시 확인하세요.");
                    if (!java.util.Objects.equals(before.role, role)
                            && shizuku.available() && preparationSupported()) {
                        if (before.transactionPending) throw new java.io.IOException("중단된 역할 변경을 먼저 복원하세요.");
                        stage("삼성 CMC 사용을 끄고 " + RoleStore.roleName(role) + " 전환을 준비합니다. 화면을 조작하지 마세요.");
                        shizuku.prepareRole(role, java.util.UUID.randomUUID().toString().replace("-", ""));
                        stage("준비 완료 · 역할을 적용하고 3초 동안 확인합니다.");
                    }
                    // Native preparation must not authorize overwriting a role changed
                    // by Samsung or another app while its screens were open.
                    next = store.applyIfUnchanged(role, before.role);
                    if (!java.util.Objects.equals(before.role, role) && shizuku.available()) advisory = shizuku.refreshCmcUi();
                }
                else if ("restore".equals(operation)) next = store.restore();
                else {
                    next=store.reconcileNativeReset();
                    if (next.nativeResetPending || "native-reset".equals(next.transactionKind)) { result=next.lastResult;failed=false; }
                }
                if (!"refresh".equals(operation) && !"prepare".equals(operation) && !"disable".equals(operation) && !"developer".equals(operation) && !"native-apply".equals(operation)) {
                    result = next.lastResult + advisory;
                    failed = false;
                }
            } catch (Exception exception) {
                result = RoleStore.safeMessage(exception);
                failed = true;
                try { next = store.snapshot(); } catch (Exception ignored) { }
            }
            final RoleStore.Snapshot completed = next;
            final String completedMessage = result;
            final boolean completedError = failed;
            main.post(() -> {
                snapshot = completed;
                message = completedMessage;
                error = completedError;
                busy = false;
                publish();
            });
        });
    }

    private void publish() {
        if (listener != null) listener.changed(snapshot, busy || (shizuku != null && shizuku.busy()), message, error);
    }
    private void stage(String value) {
        android.util.Log.i("CmcHelper", value);
        main.post(() -> { message = value; publish(); });
    }
}
