package com.haha5039.cmc;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import rikka.shizuku.Shizuku;

/** Application-scoped authorization/connection; never queues or replays a mutation. */
final class ShizukuSession implements RoleStore.Writer {
    private int permissionSequence = 72;
    private int pendingPermission = -1;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService inspector = Executors.newSingleThreadExecutor();
    private final Shizuku.UserServiceArgs args;
    private final Runnable observer;
    private volatile ICmcService service;
    private volatile int serviceUid = -1;
    private boolean available, granted, binding, requesting;
    private boolean registered;
    private int generation;
    private String problem = "";
    private ServiceConnection connection;

    ShizukuSession(Context context, Runnable observer) {
        this.observer = observer;
        args = new Shizuku.UserServiceArgs(new ComponentName(context, CmcUserService.class))
                .tag("cmc-phone-controls").processNameSuffix("cmc_engine")
                .daemon(false).debuggable(false).version(7);
    }
    void start() {
        Shizuku.addBinderReceivedListenerSticky(() -> main.post(() -> { problem = ""; refresh(); }));
        Shizuku.addBinderDeadListener(() -> main.post(() -> {
            cancelPermissionRequest();
            invalidate();
            available = false;
            granted = false;
            problem = "Shizuku가 종료됐습니다. 다시 시작하고 연결하세요.";
            observer.run();
        }));
        Shizuku.addRequestPermissionResultListener((request, grant) -> main.post(() -> {
            if (request != pendingPermission) return;
            cancelPermissionRequest();
            refresh();
            if (grant == PackageManager.PERMISSION_GRANTED && granted) bind();
            else { problem = "Shizuku의 승인된 앱 목록에서 CMC Helper를 허용하세요."; observer.run(); }
        }));
        refresh();
    }
    void refresh() {
        try {
            available = Shizuku.pingBinder();
            granted = available && !Shizuku.isPreV11() && Shizuku.getVersion() >= 12
                    && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
            if (!available || granted) cancelPermissionRequest();
            if ((!available || !granted) && (registered || service != null)) invalidate();
        } catch (RuntimeException error) {
            cancelPermissionRequest();
            available = false;
            granted = false;
            invalidate();
            problem = "Shizuku 상태를 확인하지 못했습니다. 다시 연결하세요.";
        }
        observer.run();
        // An existing approval only reconnects the fixed engine; it never replays a mutation.
        if (granted && !binding && !requesting && service == null && problem.isEmpty()) bind();
    }
    void authorize() {
        if (binding || requesting) return;
        refresh();
        problem = "";
        if (!available) { problem = "Shizuku 앱에서 무선 디버깅으로 시작한 뒤 연결하세요."; observer.run(); return; }
        try {
            if (Shizuku.isPreV11() || Shizuku.getVersion() < 12) {
                problem = "Shizuku API v12 이상이 필요합니다.";
            } else if (granted) { bind(); return; }
            else if (Shizuku.shouldShowRequestPermissionRationale()) {
                problem = "Shizuku 앱의 승인된 앱 목록에서 CMC Helper를 허용하세요.";
            } else {
                requesting = true;
                final int ticket = ++permissionSequence;
                pendingPermission = ticket;
                Shizuku.requestPermission(ticket);
                main.postDelayed(() -> {
                    if (!requesting || pendingPermission != ticket) return;
                    cancelPermissionRequest();
                    problem = "권한 응답을 확인하지 못했습니다. Shizuku에서 승인한 뒤 다시 연결하세요.";
                    refresh();
                }, 30000);
            }
        } catch (RuntimeException error) { cancelPermissionRequest(); problem = "Shizuku 권한 요청 실패: " + error.getMessage(); }
        observer.run();
    }
    private void cancelPermissionRequest() {
        requesting = false;
        pendingPermission = -1;
    }
    private void bind() {
        if (binding || !granted) return;
        if (available()) { observer.run(); return; }
        invalidate();
        binding = true;
        registered = true;
        final int ticket = ++generation;
        connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                if (binder == null) { main.post(() -> failed(ticket, "Shizuku 엔진 응답이 없습니다.")); return; }
                inspector.execute(() -> {
                    try {
                        ICmcService candidate = ICmcService.Stub.asInterface(binder);
                        int uid = candidate.getUid();
                        if (uid != 2000 && uid != 0) throw new SecurityException("예상하지 않은 엔진 UID: " + uid);
                        binder.linkToDeath(() -> main.post(() -> failed(ticket, "Shizuku 엔진 연결이 끊겼습니다. 다시 연결하세요.")), 0);
                        main.post(() -> {
                            if (ticket != generation || !binding) return;
                            service = candidate;
                            serviceUid = uid;
                            binding = false;
                            problem = "";
                            observer.run();
                        });
                    } catch (Exception error) { main.post(() -> failed(ticket, "Shizuku 엔진 확인 실패: " + error.getMessage())); }
                });
            }
            @Override public void onServiceDisconnected(ComponentName name) {
                main.post(() -> failed(ticket, "Shizuku 엔진 연결이 해제됐습니다. 다시 연결하세요."));
            }
        };
        try {
            Shizuku.bindUserService(args, connection);
            main.postDelayed(() -> { if (ticket == generation && binding) failed(ticket, "Shizuku 연결 시간이 초과됐습니다."); }, 15000);
        } catch (RuntimeException error) { failed(ticket, "Shizuku 연결 실패: " + error.getMessage()); }
        observer.run();
    }
    private void failed(int ticket, String message) {
        if (ticket != generation) return;
        invalidate();
        problem = message;
        observer.run();
    }
    private void invalidate() {
        ServiceConnection old = connection;
        generation++;
        service = null;
        serviceUid = -1;
        binding = false;
        registered = false;
        connection = null;
        if (old != null) try { if (Shizuku.pingBinder()) Shizuku.unbindUserService(args, old, false); }
        catch (RuntimeException ignored) { }
    }
    boolean busy() { return binding || requesting; }
    String label() {
        if (requesting) return "Shizuku 승인 대기 중";
        if (binding) return "Shizuku 엔진 연결 중";
        if (!problem.isEmpty()) return problem;
        if (available()) return serviceUid == 2000 ? "Shizuku 연결됨 · shell UID 2000 (루트 없이)"
                : "Shizuku 연결됨 · root UID 0 (루트로 시작된 서버)";
        if (!available) return "Shizuku에서 무선 디버깅으로 시작하세요.";
        return granted ? "권한 승인됨 · 연결 버튼을 누르세요." : "Shizuku 연결 후 이 앱의 권한을 허용하세요.";
    }
    @Override public boolean available() {
        ICmcService target = service;
        return target != null && (serviceUid == 2000 || serviceUid == 0) && target.asBinder().isBinderAlive();
    }
    @Override public void write(String role) throws IOException {
        ICmcService target = requireService();
        try {
            String result = target.writeRole(role);
            if (!"WRITE_OK".equals(result)) throw new IOException(result == null ? "Shizuku 설정 쓰기 응답 없음" : result);
        } catch (android.os.RemoteException error) { throw new IOException("Shizuku 연결이 끊겼습니다. 역할 복원 기록을 확인하세요.", error); }
    }
    String prepare(boolean enable, String request) throws IOException {
        ICmcService target = requireService();
        try {
            String result = target.prepare(enable, request);
            if (!CommandSpec.completed(result, enable, request))
                throw new IOException("삼성 CMC 준비 완료를 확인하지 못했습니다. 마지막 준비 결과와 현재 역할을 확인하세요.\n"
                        + (result == null ? "Shizuku 응답 없음" : result));
            return enable ? "수신 역할을 위한 준비를 마쳤습니다. 수신 기기 버튼을 눌러 역할을 적용하세요."
                    : "역할 예외 해제를 마쳤습니다. 휴대폰에서 수신 역할은 다시 발신으로 돌아갑니다.";
        } catch (android.os.RemoteException error) { throw new IOException("준비 중 Shizuku 연결이 끊겼습니다. 결과를 확인하기 전에는 다시 실행하지 마세요.", error); }
    }
    int launchNative(String request, boolean developer, int task) throws Exception {
        return requireService().launchNative(request, developer, task);
    }
    void prepareRole(String role, String request) throws IOException {
        if (!"pd".equals(role) && !"sd".equals(role)) throw new IOException("지원하지 않는 역할");
        try {
            String result = requireService().prepareRole(role, request);
            if (!CommandSpec.completed(result, "role_" + role, request))
                throw new IOException("전환 준비를 완료하지 못해 역할 적용 단계로 진행하지 않았습니다. 현재 설정을 확인하세요.\n"
                        + (result == null ? "Shizuku 응답 없음" : result));
        } catch (android.os.RemoteException error) { throw new IOException("전환 준비 중 Shizuku 연결이 끊겼습니다. 현재 설정을 확인하세요.", error); }
    }
    String openDeveloper(String request) throws IOException {
        try {
            String result = requireService().openDeveloper(request);
            if (!CommandSpec.completed(result, "developer", request))
                throw new IOException("개발자 화면 열기를 완료하지 못했습니다.\n" + (result == null ? "Shizuku 응답 없음" : result));
            return "삼성 CMC 개발자 화면을 열었습니다. 화면에서 필요한 항목을 직접 확인하세요.";
        } catch (android.os.RemoteException error) { throw new IOException("개발자 화면 실행 중 Shizuku 연결이 끊겼습니다.", error); }
    }
    boolean nativeApplyApproved() {
        try { return available() && Shizuku.pingBinder()
                && Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED; }
        catch (RuntimeException denied) { return false; }
    }
    boolean applyNativeRole(String role,String request) throws IOException {
        if (!"pd".equals(role) && !"sd".equals(role)) throw new IOException("지원하지 않는 역할");
        ICmcService target;
        try { target=requireService(); } catch (IOException notStarted) { return false; }
        try {
            String result=target.applyNativeRole(role,request);
            if (CommandSpec.completed(result,"native_"+role,request)) return true;
            if (CommandSpec.nativeNotApplied(result,role,request)) return false;
            throw new IOException("초기화 적용 결과를 확인하지 못했습니다. 다시 실행하지 말고 재부팅 후 상태를 확인하세요.");
        } catch (android.os.RemoteException error) {
            throw new IOException("초기화 요청 중 연결이 끊겼습니다. 요청을 재실행하지 말고 재부팅 후 상태를 확인하세요.",error);
        }
    }
    String refreshCmcUi() {
        try {
            String result = requireService().refreshCmcUi();
            return result != null && result.startsWith("UI_REFRESHED:") ? ""
                    : "\n역할은 변경했지만 기존 CMC 설정 창을 정리하지 못했습니다. 창을 닫고 다시 열어 주세요.";
        } catch (Exception error) {
            return "\n역할은 변경했습니다. 기존 CMC 설정 창이 남아 있으면 닫고 다시 열어 주세요.";
        }
    }
    private ICmcService requireService() throws IOException {
        ICmcService target = service;
        if (!available() || target == null) throw new IOException("먼저 Shizuku를 연결하고 권한을 허용하세요.");
        try {
            if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED)
                throw new IOException("Shizuku 권한이 해제됐습니다. 다시 승인하고 연결하세요.");
        } catch (RuntimeException error) { throw new IOException("Shizuku 승인 상태를 확인하지 못했습니다.", error); }
        return target;
    }
}
