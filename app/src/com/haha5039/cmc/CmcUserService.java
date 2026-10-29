package com.haha5039.cmc;

import android.content.Context;

/** Loaded only by the authorized Shizuku UserService mechanism, not an exported Service. */
public final class CmcUserService extends ICmcService.Stub {
    private volatile String activeRequest;
    private volatile long requestDeadline;
    public CmcUserService() {}
    public CmcUserService(Context ignored) {}
    @Override public int getUid() { return android.os.Process.myUid(); }
    private void requireShell() {
        int uid = getUid();
        if (uid != 2000 && uid != 0) throw new SecurityException("Shizuku shell/root UID가 필요합니다.");
    }
    @Override public synchronized String writeRole(String role) {
        requireShell();
        try {
            String[] command = CommandSpec.role(role);
            for (String key : new String[]{"cmc_activation", "cmc_call_activation", "cmc_message_activation"}) {
                BoundedCommand.Result state = BoundedCommand.run(5000, "settings", "get", "global", key);
                if (!CommandSpec.idleSetting(state.exit, state.output))
                    return "WRITE_FAILED: CMC 꺼짐을 확인하지 못했습니다. 삼성 CMC에서 사용 설정을 확인하세요.";
            }
            BoundedCommand.Result result = BoundedCommand.run(5000, command);
            if (!CommandSpec.wrote(role, result.exit, result.output))
                return "WRITE_FAILED: 설정 쓰기를 완료하지 못했습니다. " + result.output;
            return "WRITE_OK";
        } catch (Exception error) { return "WRITE_FAILED: " + error.getMessage(); }
    }
    @Override public synchronized String prepare(boolean enable, String requestId) {
        return runPreparation(enable ? "prepare" : "disable", requestId);
    }
    @Override public synchronized String prepareRole(String role, String requestId) {
        CommandSpec.role(role);
        if (role == null) throw new IllegalArgumentException("Role required");
        return runPreparation("role_" + role, requestId);
    }
    @Override public synchronized String openDeveloper(String requestId) {
        return runPreparation("developer", requestId);
    }
    @Override public synchronized String applyNativeRole(String role,String requestId) {
        if (!"pd".equals(role) && !"sd".equals(role)) throw new IllegalArgumentException("Role required");
        return runPreparation("native_"+role,requestId);
    }
    @Override public synchronized String refreshCmcUi() {
        requireShell();
        try {
            for (String key : new String[]{"cmc_activation", "cmc_call_activation", "cmc_message_activation"}) {
                BoundedCommand.Result result = BoundedCommand.run(5000, "settings", "get", "global", key);
                if (result.exit != 0 || !"0".equals(result.output.trim())) return "UI_BUSY";
            }
            return "UI_REFRESHED:" + NativeLauncher.closeCmcSettings();
        } catch (Exception error) { return "UI_FAILED"; }
    }
    private String runPreparation(String operation, String requestId) {
        requireShell();
        try {
            CommandSpec.preparation(operation, requestId);
            activeRequest = requestId;
            requestDeadline = android.os.SystemClock.elapsedRealtime() + 75000;
            BoundedCommand.Result result = BoundedCommand.run(75000, CommandSpec.preparation(operation, requestId));
            if (result.exit != 0) return "BRIDGE_FAILED: 자체 준비 실행을 완료하지 못했습니다.\n" + result.output;
            return result.output;
        } catch (Exception error) { return "BRIDGE_FAILED: " + error.getMessage(); }
        finally { activeRequest = null; requestDeadline = 0; }
    }
    // Deliberately not synchronized: the preparation IPC is waiting for this callback.
    @Override public int launchNative(String requestId, boolean developer, int taskId) {
        requireShell();
        if (requestId == null || !requestId.equals(activeRequest)
                || android.os.SystemClock.elapsedRealtime() >= requestDeadline)
            throw new SecurityException("활성 준비 요청이 없습니다.");
        try { return NativeLauncher.open(developer, taskId); }
        catch (Exception error) { throw new IllegalStateException("삼성 화면 실행 실패: " + error.getMessage()); }
    }
    @Override public synchronized void destroy() { System.exit(0); }
}
