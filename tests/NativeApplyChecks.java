package com.haha5039.cmc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import org.json.JSONObject;

/** Synthetic native-reset journal/reboot scenarios; no device operations. */
public final class NativeApplyChecks {
    private static int passed;
    private static final String REQUEST="0123456789abcdef0123456789abcdef";
    private static void check(boolean value) { if (!value) throw new AssertionError();passed++; }
    private interface Action { void run() throws Exception; }
    private static void rejected(Action action,String text) throws Exception {
        try { action.run();throw new AssertionError("Unexpected success"); }
        catch (IOException expected) { check(expected.getMessage().contains(text)); }
    }
    private static JSONObject record(HostContext c) throws Exception { return new JSONObject(new String(Files.readAllBytes(c.journal().toPath()),StandardCharsets.UTF_8)); }
    private static void write(HostContext c,JSONObject value) throws Exception { Files.write(c.journal().toPath(),value.toString().getBytes(StandardCharsets.UTF_8)); }
    public static void main(String[] args) throws Exception {
        try (HostContext c=new HostContext()) {
            rejected(() -> c.store.stageNativeReset("sd",REQUEST),"재부팅 결과");check(!c.journal().exists());
            c.values.put("boot_count","7");c.values.put("cmc_oobe","0");
            rejected(() -> c.store.stageNativeReset("sd",REQUEST),"초기 설정");check(!c.journal().exists());
        }
        try (HostContext c=new HostContext()) {
            c.values.put("boot_count","7");c.values.put("cmc_activation","1");
            RoleStore.Snapshot state=c.store.stageNativeReset("sd",REQUEST);
            check(state.nativeResetPending && state.hasOriginal && state.originalRole.equals("pd"));
            check(c.writes.isEmpty() && c.directWrites==0);
            rejected(() -> c.store.restore(),"초기화 적용");
            rejected(() -> c.store.preflight("sd"),"초기화 적용");
            rejected(() -> c.store.apply("pd"),"초기화 적용");
            rejected(() -> c.store.stageNativeReset("pd",REQUEST),"이전 작업");
            check(c.store.reconcileNativeReset().nativeResetPending);
            c.values.put("cmc_device_type","sd");check(c.store.reconcileNativeReset().nativeResetPending);
            rejected(() -> c.store.cancelNativeResetBeforeClick(REQUEST),"기기 상태");
            rejected(() -> c.store.acceptNativeResetState(),"2분");
            c.values.put("boot_count","8");c.values.put("cmc_activation","0");
            RoleStore.Snapshot recovered=c.store.reconcileNativeReset();
            check(!recovered.nativeResetPending && recovered.hasOwned && recovered.ownedRole.equals("sd"));
            check(c.writes.isEmpty() && c.directWrites==0);
            check(c.store.restore().role.equals("pd"));check(c.writes.equals(Arrays.asList("pd")));
        }
        try (HostContext c=new HostContext()) {
            c.values.put("boot_count","7");c.store.stageNativeReset("sd",REQUEST);
            rejected(() -> c.store.cancelNativeResetBeforeClick("ffffffffffffffffffffffffffffffff"),"일치");
            c.store.cancelNativeResetBeforeClick(REQUEST);
            check(!c.store.snapshot().nativeResetPending);check(c.writes.isEmpty());
            c.store.preflight("sd");check(true);
        }
        try (HostContext c=new HostContext()) {
            c.values.put("boot_count","7");c.store.stageNativeReset("sd",REQUEST);c.values.put("boot_count","8");
            check(c.store.reconcileNativeReset().nativeResetPending);
            check(c.store.reconcileNativeReset().lastResult.contains("다릅니다"));
            RoleStore.Snapshot accepted=c.store.acceptNativeResetState();
            check(!accepted.nativeResetPending && accepted.ownedRole.equals("pd"));check(c.writes.isEmpty());
        }
        try (HostContext c=new HostContext()) {
            c.values.put("boot_count","7");c.store.stageNativeReset("sd",REQUEST);
            JSONObject j=record(c);j.put("startedAt",System.currentTimeMillis()-121000);write(c,j);
            check(!c.store.acceptNativeResetState().nativeResetPending);check(c.writes.isEmpty());
        }
        for (String field : new String[]{"nativeRequest","nativeBootCount","target"}) try (HostContext c=new HostContext()) {
            c.values.put("boot_count","7");c.store.stageNativeReset("sd",REQUEST);
            JSONObject j=record(c);j.put(field,field.equals("nativeBootCount") ? -1 : "invalid");write(c,j);
            rejected(() -> c.store.snapshot(),"복원 기록");check(c.writes.isEmpty());
        }
        try (HostContext c=new HostContext()) {
            c.values.put("boot_count","7");check(c.store.stageNativeReset("pd",REQUEST).nativeResetPending);
            check(c.writes.isEmpty());
        }
        try (HostContext c=new HostContext()) {
            c.values.put("boot_count","7");c.authorized=false;
            rejected(() -> c.store.stageNativeReset("pd",REQUEST),"Shizuku");check(!c.journal().exists());
        }
        try (HostContext c=new HostContext()) {
            c.values.put("boot_count","7");c.store.apply("sd");
            JSONObject j=record(c);j.put("hasOwned",false).put("owned",JSONObject.NULL);write(c,j);
            check(c.store.snapshot().externalChange);
            rejected(() -> c.store.adoptExternalRole("pd"),"외부에서");
            RoleStore.Snapshot adopted=c.store.adoptExternalRole("sd");
            check(!adopted.externalChange && adopted.originalRole.equals("pd") && adopted.ownedRole.equals("sd"));
            check(c.writes.size()==1 && c.directWrites==0);
            check(c.store.stageNativeReset("sd",REQUEST).nativeResetPending);
        }
        System.out.println("Native apply journal checks passed: "+passed);
    }
}
