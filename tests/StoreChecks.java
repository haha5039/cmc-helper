package com.haha5039.cmc;

import android.content.*;
import android.content.pm.*;
import android.os.Looper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.json.JSONObject;

/** Exercises the production RoleStore against host settings/filesystem adapters. */
public final class StoreChecks {
    private static int passed;
    private static void check(boolean condition) { if (!condition) throw new AssertionError(); passed++; }
    private interface Action { void run() throws Exception; }
    private static void rejected(Action action,String text) throws Exception {
        try { action.run(); throw new AssertionError("Unexpected success"); }
        catch (IOException expected) { check(expected.getMessage().contains(text)); }
    }
    private static void journal(HostContext c,String original,String owned,boolean hasOwned,boolean pending) throws Exception {
        JSONObject j=new JSONObject();
        j.put("version",1).put("hasOriginal",true).put("original",original==null ? JSONObject.NULL : original)
                .put("hasOwned",hasOwned).put("owned",owned==null ? JSONObject.NULL : owned)
                .put("pending",pending).put("before","pd").put("target","sd")
                .put("kind","apply").put("startedAt",1).put("lastResult","fixture");
        Files.write(c.journal().toPath(),j.toString().getBytes(StandardCharsets.UTF_8));
    }
    public static void main(String[] args) throws Exception {
        try (HostContext c=new HostContext()) {
            c.values.put("cmc_call_activation","1");
            RoleStore.Snapshot before=c.store.preflight("sd");
            check(before.active()); check(!c.journal().exists()); check(c.writes.isEmpty());
            c.authorized=false;
            check(c.store.preflight("pd").active());
            c.store.applyIfUnchanged("pd","pd");
            check(!c.journal().exists()); check(c.writes.isEmpty());
            rejected(() -> c.store.preflight("sd"),"Shizuku");
        }
        for (String unknown : new String[]{"ad","tablet","","SD"}) try (HostContext c=new HostContext()) {
            c.values.put("cmc_device_type",unknown);
            rejected(() -> c.store.preflight("sd"),"알 수 없는");
            check(!c.journal().exists()); check(c.writes.isEmpty());
        }
        try (HostContext c=new HostContext()) {
            c.installed=false; rejected(() -> c.store.preflight("sd"),"찾을 수");
            c.installed=true; c.info.applicationInfo.enabled=false;
            rejected(() -> c.store.preflight("sd"),"비활성화");
            check(c.writes.isEmpty()); check(!c.journal().exists());
        }
        try (HostContext c=new HostContext()) {
            c.store.preflight("sd");
            RoleStore.Snapshot applied=c.store.applyIfUnchanged("sd","pd");
            check(applied.hasOriginal && applied.originalRole.equals("pd"));
            check(applied.hasOwned && applied.ownedRole.equals("sd") && !applied.transactionPending);
            check(c.writes.equals(Arrays.asList("sd"))); check(c.sawPendingBeforeWrite);
            RoleStore.Snapshot restored=c.store.restore();
            check(restored.role.equals("pd") && !restored.hasOriginal);
            check(c.writes.equals(Arrays.asList("sd","pd")));
        }
        try (HostContext c=new HostContext()) {
            c.values.remove("cmc_device_type");
            RoleStore.Snapshot applied=c.store.applyIfUnchanged("sd",null);
            check(applied.hasOriginal && !applied.originalPresent && applied.originalRole==null);
            check(c.store.restore().role==null); check(!c.values.containsKey("cmc_device_type"));
            check(c.writes.equals(Arrays.asList("sd",null)));
        }
        try (HostContext c=new HostContext()) {
            c.failWrites=1;
            rejected(() -> c.store.applyIfUnchanged("sd","pd"),"되돌렸습니다");
            check(c.values.get("cmc_device_type").equals("pd"));
            check(c.writes.equals(Arrays.asList("sd","pd")));
            check(!c.store.snapshot().transactionPending);
            check(c.store.apply("sd").role.equals("sd"));
        }
        try (HostContext c=new HostContext()) {
            c.failWrites=2;
            rejected(() -> c.store.apply("sd"),"자동 복원을 완료하지");
            check(c.store.snapshot().transactionPending);
            rejected(() -> c.store.preflight("sd"),"중단된");
            c.store.apply("pd"); // Same-role no-op preserves the pending record.
            check(c.store.snapshot().transactionPending); check(c.writes.size()==2);
            check(!c.store.restore().hasOriginal); check(!c.store.snapshot().transactionPending);
        }
        try (HostContext c=new HostContext()) {
            c.externalOnWrite="external";
            rejected(() -> c.store.apply("sd"),"외부 역할 변경");
            check(c.values.get("cmc_device_type").equals("external"));
            check(c.writes.equals(Arrays.asList("sd")));
            check(c.store.snapshot().transactionPending && c.store.snapshot().externalChange);
        }
        for (String drift : new String[]{"sd","other",null}) try (HostContext c=new HostContext()) {
            c.store.preflight("sd");
            if (drift==null) c.values.remove("cmc_device_type"); else c.values.put("cmc_device_type",drift);
            rejected(() -> c.store.applyIfUnchanged("sd","pd"),"외부에서");
            check(c.writes.isEmpty()); check(!c.journal().exists());
        }
        try (HostContext c=new HostContext()) {
            Files.write(c.journal().toPath(),"broken".getBytes(StandardCharsets.UTF_8));
            rejected(() -> c.store.preflight("sd"),"복원 기록"); check(c.writes.isEmpty());
        }
        try (HostContext c=new HostContext()) {
            journal(c,"pd",null,false,false); c.values.put("cmc_device_type","sd");
            byte[] before=Files.readAllBytes(c.journal().toPath());
            rejected(() -> c.store.preflight("pd"),"외부에서");
            check(c.writes.isEmpty()); check(Arrays.equals(before,Files.readAllBytes(c.journal().toPath())));
        }
        try (HostContext c=new HostContext()) {
            journal(c,"pd","sd",true,false);
            byte[] before=Files.readAllBytes(c.journal().toPath());
            c.store.preflight("sd");
            check(Arrays.equals(before,Files.readAllBytes(c.journal().toPath())));
            check(c.store.applyIfUnchanged("sd","pd").role.equals("sd"));
        }
        try (HostContext c=new HostContext()) {
            journal(c,"ad","pd",true,false);
            rejected(() -> c.store.preflight("sd"),"복원할 수"); check(c.writes.isEmpty());
        }
        try (HostContext c=new HostContext()) {
            c.values.put("cmc_activation","1"); c.store.preflight("sd");
            rejected(() -> c.store.applyIfUnchanged("sd","pd"),"사용 중");
            check(c.writes.isEmpty()); check(!c.journal().exists());
        }
        try (HostContext c=new HostContext()) {
            c.authorized=false; c.permission=PackageManager.PERMISSION_GRANTED;
            check(c.store.apply("sd").role.equals("sd"));
            check(c.writes.isEmpty()); check(c.directWrites==1);
        }
        try (HostContext c=new HostContext()) {
            Looper.onMain=true;
            try { c.store.preflight("sd"); throw new AssertionError(); }
            catch (IllegalStateException expected) { check(c.writes.isEmpty()); }
            finally { Looper.onMain=false; }
        }
        System.out.println("RoleStore adapter checks passed: "+passed);
    }
}

final class HostContext extends Context implements AutoCloseable {
    final File directory;
    final Map<String,String> values=new HashMap<>();
    final List<String> writes=new ArrayList<>();
    final PackageInfo info=new PackageInfo();
    boolean installed=true,authorized=true,sawPendingBeforeWrite;
    int permission=PackageManager.PERMISSION_DENIED,failWrites,directWrites;
    String externalOnWrite;
    final RoleStore store;
    HostContext() throws IOException {
        directory=Files.createTempDirectory("cmc-store-test-").toFile();
        values.put("cmc_device_type","pd"); values.put("cmc_oobe","1");
        for (String key : new String[]{"cmc_activation","cmc_call_activation","cmc_message_activation"}) values.put(key,"0");
        store=new RoleStore(this,new RoleStore.Writer() {
            public boolean available() { return authorized; }
            public void write(String value) throws IOException {
                try {
                    JSONObject j=new JSONObject(new String(Files.readAllBytes(journal().toPath()),StandardCharsets.UTF_8));
                    sawPendingBeforeWrite=j.getBoolean("pending") && j.getBoolean("hasOriginal");
                    if (!sawPendingBeforeWrite) throw new AssertionError("Missing durable intent");
                } catch (IOException error) { throw error; }
                catch (Exception error) { throw new AssertionError(error); }
                writes.add(value);
                if (value==null) values.remove("cmc_device_type"); else values.put("cmc_device_type",value);
                if (externalOnWrite!=null) values.put("cmc_device_type",externalOnWrite);
                if (failWrites>0) { failWrites--; throw new IOException("simulated write response failure"); }
            }
        });
    }
    File journal() { return new File(directory,"cmc-role-journal.json"); }
    public ContentResolver getContentResolver() { return new ContentResolver() {
        public String get(String key) { return values.get(key); }
        public boolean put(String key,String value) {
            directWrites++;
            if (value==null) values.remove(key); else values.put(key,value);
            return true;
        }
    }; }
    public File getNoBackupFilesDir() { return directory; }
    public int checkSelfPermission(String ignored) { return permission; }
    public PackageManager getPackageManager() { return new PackageManager() {
        public PackageInfo getPackageInfo(String name,int flags) throws NameNotFoundException {
            if (!installed) throw new NameNotFoundException(); return info;
        }
    }; }
    public SharedPreferences getSharedPreferences(String name,int mode) { return () -> Collections.emptyMap(); }
    public void close() throws IOException {
        try (java.util.stream.Stream<Path> paths=Files.walk(directory.toPath())) {
            for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) Files.delete(path);
        }
    }
}
