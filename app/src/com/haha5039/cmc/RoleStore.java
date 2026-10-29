package com.haha5039.cmc;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.AtomicFile;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/** Finite, journaled role operations. No services, observers, or activation writes. */
final class RoleStore {
    interface Writer {
        boolean available();
        void write(String value) throws IOException;
    }
    static final String CMC_PACKAGE = "com.samsung.android.mdecservice";
    static final String ROLE_KEY = "cmc_device_type";
    static final String OOBE_KEY = "cmc_oobe";
    static final String ACTIVATION_KEY = "cmc_activation";
    static final String CALL_KEY = "cmc_call_activation";
    static final String MESSAGE_KEY = "cmc_message_activation";
    private static final long VERIFY_MS = 3000;
    private static final Object PROCESS_LOCK = new Object();
    private final Context context;
    private final ContentResolver resolver;
    private final AtomicFile state;
    private final File lockFile;
    private final Writer writer;

    RoleStore(Context context) {
        this(context, null);
    }

    RoleStore(Context context, Writer writer) {
        this.context = context.getApplicationContext();
        this.writer = writer;
        this.resolver = this.context.getContentResolver();
        File directory = this.context.getNoBackupFilesDir();
        state = new AtomicFile(new File(directory, "cmc-role-journal.json"));
        lockFile = new File(directory, "cmc-role-journal.lock");
    }

    synchronized Snapshot apply(String role) throws IOException {
        return apply(role, null, false);
    }

    /** Validate before native preparation, without changing settings or the journal. */
    synchronized Snapshot preflight(String role) throws IOException {
        requireWorker();
        synchronized (PROCESS_LOCK) {
            try (RandomAccessFile file = new RandomAccessFile(lockFile, "rw");
                 FileLock ignored = file.getChannel().lock()) {
                Journal journal = load();
                Snapshot before = snapshot(journal, null);
                checkApply(journal, before.role, role);
                if (!Objects.equals(before.role, role)) requireAccess();
                return before;
            }
        }
    }

    synchronized Snapshot applyIfUnchanged(String role, String expectedRole) throws IOException {
        return apply(role, expectedRole, true);
    }

    private Snapshot apply(String role, String expectedRole, boolean checkExpected) throws IOException {
        if (!"pd".equals(role) && !"sd".equals(role))
            throw new IllegalArgumentException("지원하는 역할은 pd와 sd입니다.");
        requireWorker();
        synchronized (PROCESS_LOCK) {
            try (RandomAccessFile file = new RandomAccessFile(lockFile, "rw");
                 FileLock ignored = file.getChannel().lock()) {
                Journal journal = load();
                String before = readRole();
                if (checkExpected && !Objects.equals(before, expectedRole)) throw conflict();
                checkApply(journal, before, role);
                // A same-role request never writes settings or creates a misleading backup.
                // In particular it is safe when CMC is already registered and active.
                if (Objects.equals(before, role))
                    return snapshot(journal, "이미 " + roleName(role) + "입니다. 설정을 변경하지 않았습니다."
                            + (journal.pending ? " 중단된 작업 기록은 원래 역할 복원에서 확인하세요." : ""));
                requireReady();
                // Returning to the saved original outside this app needs no ownership claim.
                if (journal.hasOriginal && Objects.equals(before, journal.original))
                    journal.hasOwned = false;
                if (!journal.hasOriginal) {
                    journal.hasOriginal = true;
                    journal.original = before; // null explicitly means the key was absent.
                }
                return transact(journal, before, role, false);
            }
        }
    }

    private static void checkApply(Journal journal, String before, String role) throws IOException {
        if (!"pd".equals(role) && !"sd".equals(role))
            throw new IllegalArgumentException("지원하는 역할은 pd와 sd입니다.");
        if (journal.pending && "native-reset".equals(journal.kind))
            throw new IOException("초기화 적용 요청이 대기 중입니다. 재부팅 후 결과를 확인하세요.");
        if (Objects.equals(before, role)) return; // No-op remains safe for ordinary interrupted writes.
        checkChangeContext(journal,before);
    }

    private static void checkChangeContext(Journal journal,String before) throws IOException {
        if (journal.pending)
            throw new IOException("중단된 작업 기록이 있습니다. 먼저 작업 결과를 확인하세요.");
        if (journal.hasOriginal && !Objects.equals(before, journal.original)
                && (!journal.hasOwned || !Objects.equals(before, journal.owned))) throw conflict();
        if (!writableRole(before) || (journal.hasOriginal && !writableRole(journal.original)))
            throw new IOException("알 수 없는 CMC 역할이 있어 안전하게 복원할 수 없습니다. 삼성 CMC 설정에서 현재 역할을 확인하세요.");
    }

    private static boolean writableRole(String role) {
        return role == null || "pd".equals(role) || "sd".equals(role);
    }

    synchronized Snapshot restore() throws IOException {
        requireWorker();
        synchronized (PROCESS_LOCK) {
            try (RandomAccessFile file = new RandomAccessFile(lockFile, "rw");
                 FileLock ignored = file.getChannel().lock()) {
                Journal journal = load();
                if (journal.pending && "native-reset".equals(journal.kind))
                    throw new IOException("초기화 적용 결과를 먼저 확인하세요. 역할 복원으로 초기화를 취소할 수는 없습니다.");
                if (!journal.hasOriginal)
                    throw new IOException("저장된 원래 역할이 없습니다. 아직 이 앱에서 역할을 바꾸지 않았습니다.");
                String current = readRole();
                if (Objects.equals(current, journal.original)) {
                    // Already restored, including an absent key. No global write is needed.
                    clearBackup(journal);
                    journal.lastResult = "이미 원래 역할입니다. 복원 기록을 정리했습니다.";
                    save(journal);
                    return snapshot(journal, null);
                }
                requireReady();
                boolean owned = journal.pending
                        ? Objects.equals(current, journal.target) || Objects.equals(current, journal.before)
                        : journal.hasOwned && Objects.equals(current, journal.owned);
                if (!owned) throw conflict();
                return transact(journal, current, journal.original, true);
            }
        }
    }

    synchronized Snapshot snapshot() throws IOException {
        synchronized (PROCESS_LOCK) {
            try (RandomAccessFile file = new RandomAccessFile(lockFile, "rw");
                 FileLock ignored = file.getChannel().lock()) {
                return snapshot(load(), null);
            }
        }
    }

    synchronized Snapshot adoptExternalRole(String expected) throws IOException {
        requireWorker();
        synchronized (PROCESS_LOCK) {
            try (RandomAccessFile file=new RandomAccessFile(lockFile,"rw");FileLock ignored=file.getChannel().lock()) {
                Journal journal=load();String current=readRole();requireUnchanged(expected);
                if (journal.pending) throw new IOException("대기 중인 작업 결과를 먼저 확인하세요.");
                if (!writableRole(current)) throw new IOException("현재 역할을 확인할 수 없습니다.");
                boolean external=journal.hasOriginal && !Objects.equals(current,journal.original)
                        && (!journal.hasOwned || !Objects.equals(current,journal.owned));
                if (!external) return snapshot(journal,"현재 역할 기록을 변경할 필요가 없습니다.");
                journal.hasOwned=true;journal.owned=current;
                journal.lastResult="현재 역할을 앱의 관리 기준으로 확인했습니다. 저장된 원래 역할과 기기 설정은 변경하지 않았습니다.";
                save(journal);return snapshot(journal,null);
            }
        }
    }

    synchronized Snapshot stageNativeReset(String role,String request) throws IOException {
        requireWorker();
        if (!("pd".equals(role)||"sd".equals(role)) || request==null || !request.matches("[a-f0-9]{32}"))
            throw new IllegalArgumentException("잘못된 초기화 적용 요청");
        synchronized (PROCESS_LOCK) {
            try (RandomAccessFile file=new RandomAccessFile(lockFile,"rw");FileLock ignored=file.getChannel().lock()) {
                Journal journal=load();String before=readRole();
                if (journal.pending) throw new IOException("이전 작업 기록을 먼저 확인하세요.");
                checkChangeContext(journal,before);
                requireAccess();
                if (!"1".equals(read(OOBE_KEY))) throw new IOException("삼성 CMC 초기 설정을 먼저 완료하세요.");
                long boot=bootCount();
                if (boot<0) throw new IOException("재부팅 결과를 확인할 수 없어 초기화 적용을 시작하지 않습니다.");
                if (!journal.hasOriginal) { journal.hasOriginal=true;journal.original=before; }
                journal.pending=true;journal.kind="native-reset";journal.before=before;journal.target=role;
                journal.nativeRequest=request;journal.nativeBootCount=boot;journal.startedAt=System.currentTimeMillis();
                journal.lastResult="초기화 적용 요청을 기록했습니다. 재부팅 후 이 앱을 다시 열어 결과를 확인하세요.";
                save(journal);return snapshot(journal,null);
            }
        }
    }

    synchronized void cancelNativeResetBeforeClick(String request) throws IOException {
        requireWorker();
        synchronized (PROCESS_LOCK) {
            try (RandomAccessFile file=new RandomAccessFile(lockFile,"rw");FileLock ignored=file.getChannel().lock()) {
                Journal journal=load();
                if (!journal.pending || !"native-reset".equals(journal.kind) || !Objects.equals(request,journal.nativeRequest))
                    throw new IOException("초기화 요청 기록이 일치하지 않습니다.");
                if (bootCount()!=journal.nativeBootCount || !Objects.equals(readRole(),journal.before))
                    throw new IOException("기기 상태가 바뀌어 대기 기록을 보존했습니다.");
                journal.pending=false;journal.nativeRequest=null;journal.nativeBootCount=-1;
                journal.lastResult="적용 버튼을 누르기 전에 중단했습니다. 초기화 요청은 실행하지 않았습니다.";
                save(journal);
            }
        }
    }

    synchronized Snapshot reconcileNativeReset() throws IOException {
        requireWorker();
        synchronized (PROCESS_LOCK) {
            try (RandomAccessFile file=new RandomAccessFile(lockFile,"rw");FileLock ignored=file.getChannel().lock()) {
                Journal journal=load();
                if (!journal.pending || !"native-reset".equals(journal.kind)) return snapshot(journal,null);
                long boot=bootCount();
                if (boot<=journal.nativeBootCount)
                    return snapshot(journal,"초기화 적용 결과를 기다리고 있습니다. 재부팅 후 다시 확인하세요. 자동으로 재실행하지 않습니다.");
                if (!Objects.equals(readRole(),journal.target))
                    return snapshot(journal,"재부팅 후 역할이 요청값과 다릅니다. 삼성 CMC를 확인하고 현재 상태로 대기 기록을 정리할 수 있습니다.");
                verify(journal.target); // Read-only verification; no Shizuku required after boot.
                journal.pending=false;journal.hasOwned=true;journal.owned=journal.target;
                journal.nativeRequest=null;journal.nativeBootCount=-1;
                journal.lastResult="재부팅 후 "+roleName(journal.target)+" 역할 값을 확인했습니다. 삼성 CMC 초기 설정과 기기 연결을 진행하세요.";
                save(journal);return snapshot(journal,null);
            }
        }
    }

    synchronized Snapshot acceptNativeResetState() throws IOException {
        requireWorker();
        synchronized (PROCESS_LOCK) {
            try (RandomAccessFile file=new RandomAccessFile(lockFile,"rw");FileLock ignored=file.getChannel().lock()) {
                Journal journal=load();
                if (!journal.pending || !"native-reset".equals(journal.kind)) throw new IOException("확인할 초기화 대기 기록이 없습니다.");
                if (bootCount()<=journal.nativeBootCount && System.currentTimeMillis()-journal.startedAt<120000)
                    throw new IOException("초기화 처리가 진행 중일 수 있습니다. 재부팅 후 또는 2분 뒤 다시 확인하세요.");
                String current=readRole();
                if (!writableRole(current)) throw new IOException("현재 역할을 확인할 수 없습니다. 삼성 CMC 설정을 확인하세요.");
                journal.pending=false;journal.hasOwned=true;journal.owned=current;
                journal.nativeRequest=null;journal.nativeBootCount=-1;
                journal.lastResult="현재 역할을 앱의 기준으로 확인하고 대기 기록을 정리했습니다. CMC 등록·설정은 변경하지 않았습니다.";
                save(journal);return snapshot(journal,null);
            }
        }
    }

    private long bootCount() throws IOException {
        String value=read("boot_count");
        try { long parsed=Long.parseLong(value);return parsed>=0 ? parsed : -1; }
        catch (RuntimeException unavailable) { return -1; }
    }

    private Snapshot transact(Journal journal, String before, String target, boolean restoring)
            throws IOException {
        final boolean previousHasOwned = journal.hasOwned;
        final String previousOwned = journal.owned;
        journal.pending = true;
        journal.before = before;
        journal.target = target;
        journal.kind = restoring ? "restore" : "apply";
        journal.startedAt = System.currentTimeMillis();
        journal.lastResult = "역할 변경 전 기록을 저장했습니다. 검증 완료 전에는 성공으로 처리하지 않습니다.";
        save(journal); // Durable original + transaction BEFORE any Settings.Global write.
        boolean attemptedWrite = false;
        try {
            requireReady();
            requireUnchanged(before);
            attemptedWrite = true; // A provider exception does not prove the write did not land.
            putRole(target);
            verify(target);
            journal.lastResult = restoring ? "원래 역할을 복원하고 3초 동안 확인했습니다."
                    : roleName(target) + " 역할 값을 적용하고 3초 동안 확인했습니다. 실제 연결은 네이티브 CMC에서 확인하세요.";
            if (restoring) clearBackup(journal);
            else {
                journal.pending = false;
                journal.hasOwned = true;
                journal.owned = target;
            }
            save(journal);
            return snapshot(journal, null);
        } catch (Exception failure) {
            // Restore the pending record in memory even if completing the journal failed.
            journal.hasOriginal = true;
            if (restoring) journal.original = target;
            journal.pending = true;
            journal.before = before;
            journal.target = target;
            journal.kind = restoring ? "restore" : "apply";
            journal.hasOwned = previousHasOwned;
            journal.owned = previousOwned;
            String recovery;
            try {
                String observed = readRole();
                if (Objects.equals(observed, before)) {
                    journal.pending = false;
                    recovery = "변경 전 역할을 유지하고 있습니다.";
                } else if (attemptedWrite && Objects.equals(observed, target)) {
                    requireReady();
                    requireUnchanged(target);
                    putRole(before);
                    verify(before);
                    journal.pending = false;
                    recovery = "변경 전 역할로 되돌렸습니다.";
                } else {
                    recovery = "외부 역할 변경을 감지해 덮어쓰지 않았습니다. 복원 기록을 보존했습니다.";
                }
            } catch (Exception rollbackFailure) {
                recovery = "자동 복원을 완료하지 못했습니다. CMC를 끈 뒤 ‘원래 역할 복원’을 실행하세요. "
                        + safeMessage(rollbackFailure);
            }
            String nativeSetup = "sd".equals(target)
                    ? " 수신 역할이 유지되지 않으면 삼성 CMC 초기 설정을 먼저 완료하고 수신 기능 준비를 실행하세요." : "";
            journal.lastResult = "역할 변경을 완료하지 못했습니다. " + safeMessage(failure) + " "
                    + recovery + nativeSetup;
            try {
                save(journal);
            } catch (IOException saveFailure) {
                throw new IOException(journal.lastResult
                        + " 복구 결과 기록에도 실패했습니다. 이전 작업 기록으로 복원을 확인하세요.", saveFailure);
            }
            throw new IOException(journal.lastResult, failure);
        }
    }

    private void requireReady() throws IOException {
        requireAccess();
        if (isActive(read(ACTIVATION_KEY)) || isActive(read(CALL_KEY)) || isActive(read(MESSAGE_KEY)))
            throw new IOException("CMC가 사용 중입니다. 네이티브 CMC에서 전체·통화·메시지 사용을 모두 끈 뒤 역할을 바꾸세요.");
    }

    private void requireAccess() throws IOException {
        if (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                != PackageManager.PERMISSION_GRANTED && (writer == null || !writer.available()))
            throw new IOException("Shizuku를 연결하고 권한을 허용한 뒤 다시 시도하세요.");
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(CMC_PACKAGE, 0);
            if (info.applicationInfo == null || !info.applicationInfo.enabled)
                throw new IOException("삼성 CMC 앱이 비활성화되어 있습니다. 앱을 활성화한 뒤 다시 시도하세요.");
        } catch (PackageManager.NameNotFoundException e) {
            throw new IOException("삼성 CMC 앱을 찾을 수 없습니다.", e);
        }
    }

    static boolean isActive(String value) {
        if (value == null) return false;
        try { return Long.parseLong(value.trim()) != 0; }
        catch (NumberFormatException ignored) { return true; } // Unknown state fails closed.
    }

    private void requireUnchanged(String expected) throws IOException {
        if (!Objects.equals(readRole(), expected)) throw conflict();
    }

    private static IOException conflict() {
        return new IOException("외부에서 역할이 변경되어 보호를 위해 중단했습니다. 현재 값을 덮어쓰지 않았습니다. 네이티브 CMC 상태를 확인하세요.");
    }

    private String readRole() throws IOException { return read(ROLE_KEY); }

    private String read(String key) throws IOException {
        try { return Settings.Global.getString(resolver, key); }
        catch (RuntimeException e) { throw new IOException("CMC 상태를 읽지 못했습니다. 다시 새로고침하세요.", e); }
    }

    private void putRole(String role) throws IOException {
        if (writer != null && writer.available()) {
            writer.write(role);
            return;
        }
        try {
            // A null value restores an originally absent setting, rather than inventing pd.
            if (!Settings.Global.putString(resolver, ROLE_KEY, role))
                throw new IOException("시스템이 역할 값 쓰기를 거부했습니다.");
        } catch (SecurityException e) {
            throw new IOException("설정 연결이 해제되었습니다. ‘처음 설정’의 PC 연결 명령을 다시 실행하세요.", e);
        }
    }

    private void verify(String expected) throws IOException {
        long deadline = SystemClock.elapsedRealtime() + VERIFY_MS;
        for (;;) {
            if (!Objects.equals(readRole(), expected))
                throw new IOException("역할 값이 다시 바뀌었습니다. 네이티브 CMC 설정을 확인하세요.");
            long remaining = deadline - SystemClock.elapsedRealtime();
            if (remaining <= 0) return;
            try { Thread.sleep(Math.min(200, remaining)); }
            catch (InterruptedException e) {
                // Clear the interrupt so the finite rollback can still verify its write.
                throw new IOException("확인이 중단되었습니다.", e);
            }
        }
    }

    private static void requireWorker() {
        if (Looper.myLooper() == Looper.getMainLooper())
            throw new IllegalStateException("역할 변경은 UI 스레드에서 실행할 수 없습니다.");
    }

    private static void clearBackup(Journal journal) {
        journal.hasOriginal = false;
        journal.original = null;
        journal.hasOwned = false;
        journal.owned = null;
        journal.pending = false;
        journal.before = null;
        journal.target = null;
        journal.kind = "";
        journal.nativeRequest=null; journal.nativeBootCount=-1;
    }

    private Journal load() throws IOException {
        byte[] bytes;
        try { bytes = state.readFully(); }
        catch (FileNotFoundException e) {
            if (state.getBaseFile().exists()
                    || new File(state.getBaseFile().getPath() + ".bak").exists())
                throw new IOException("기존 역할 복원 기록에 접근할 수 없습니다. 설정을 바꾸지 않습니다.", e);
            return new Journal();
        }
        if (bytes.length == 0 || bytes.length > 65536)
            throw new IOException("역할 복원 기록이 손상되었습니다. 보호를 위해 설정을 바꾸지 않습니다.");
        try {
            JSONObject json = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            if (json.getInt("version") != 1) throw new JSONException("version");
            Journal journal = new Journal();
            journal.hasOriginal = json.getBoolean("hasOriginal");
            journal.original = nullable(json, "original");
            journal.hasOwned = json.getBoolean("hasOwned");
            journal.owned = nullable(json, "owned");
            journal.pending = json.getBoolean("pending");
            journal.before = nullable(json, "before");
            journal.target = nullable(json, "target");
            journal.kind = json.getString("kind");
            journal.startedAt = json.getLong("startedAt");
            journal.lastResult = json.getString("lastResult");
            journal.nativeRequest=json.has("nativeRequest") ? nullable(json,"nativeRequest") : null;
            journal.nativeBootCount=json.optLong("nativeBootCount",-1);
            if ((journal.pending || journal.hasOwned) && !journal.hasOriginal)
                throw new JSONException("missing original");
            if (journal.pending && !("apply".equals(journal.kind) || "restore".equals(journal.kind) || "native-reset".equals(journal.kind)))
                throw new JSONException("transaction kind");
            if (journal.pending && ("apply".equals(journal.kind) || "native-reset".equals(journal.kind))
                    && !("sd".equals(journal.target) || "pd".equals(journal.target)))
                throw new JSONException("transaction target");
            if (journal.pending && "native-reset".equals(journal.kind)
                    && (journal.nativeBootCount<0 || journal.nativeRequest==null || !journal.nativeRequest.matches("[a-f0-9]{32}")))
                throw new JSONException("native request");
            return journal;
        } catch (JSONException e) {
            throw new IOException("역할 복원 기록을 읽을 수 없습니다. 보호를 위해 설정을 바꾸지 않습니다.", e);
        }
    }

    private static String nullable(JSONObject object, String key) throws JSONException {
        Object value = object.get(key);
        if (value == JSONObject.NULL) return null;
        if (!(value instanceof String)) throw new JSONException(key);
        return (String) value;
    }

    private void save(Journal journal) throws IOException {
        byte[] bytes;
        try {
            JSONObject json = new JSONObject();
            json.put("version", 1);
            json.put("hasOriginal", journal.hasOriginal);
            json.put("original", jsonValue(journal.original));
            json.put("hasOwned", journal.hasOwned);
            json.put("owned", jsonValue(journal.owned));
            json.put("pending", journal.pending);
            json.put("before", jsonValue(journal.before));
            json.put("target", jsonValue(journal.target));
            json.put("kind", journal.kind);
            json.put("startedAt", journal.startedAt);
            json.put("lastResult", journal.lastResult);
            json.put("nativeRequest",jsonValue(journal.nativeRequest));
            json.put("nativeBootCount",journal.nativeBootCount);
            bytes = json.toString().getBytes(StandardCharsets.UTF_8);
        } catch (JSONException e) { throw new IOException("역할 기록을 만들지 못했습니다.", e); }
        FileOutputStream output = null;
        try {
            output = state.startWrite();
            output.write(bytes);
            output.getFD().sync();
            state.finishWrite(output);
            output = null;
            if (!Arrays.equals(bytes, state.readFully()))
                throw new IOException("역할 기록 저장을 확인하지 못했습니다.");
        } catch (IOException | RuntimeException e) {
            if (output != null) state.failWrite(output);
            throw new IOException("역할 복원 기록을 저장하지 못했습니다. 설정 쓰기를 중단합니다.", e);
        }
    }

    private static Object jsonValue(String value) { return value == null ? JSONObject.NULL : value; }

    private Snapshot snapshot(Journal journal, String result) throws IOException {
        String version = "설치되지 않음";
        boolean installed = false;
        boolean enabled = false;
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(CMC_PACKAGE, 0);
            installed = true;
            enabled = info.applicationInfo != null && info.applicationInfo.enabled;
            version = info.versionName + " (" + info.getLongVersionCode() + ")";
        } catch (PackageManager.NameNotFoundException ignored) { }
        Object preparation = context.getSharedPreferences("cmc_helper", Context.MODE_PRIVATE)
                .getAll().get("preparation_result");
        return new Snapshot(readRole(), read(OOBE_KEY), read(ACTIVATION_KEY), read(CALL_KEY),
                read(MESSAGE_KEY), context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED, installed, enabled, version, journal,
                preparation == null ? "아직 준비 결과가 없습니다." : String.valueOf(preparation), result);
    }

    static String roleName(String role) {
        if ("sd".equals(role)) return "수신 기기";
        if ("pd".equals(role)) return "발신 기기";
        return role == null ? "미설정" : "알 수 없는 역할";
    }

    static String valueText(String value) { return value == null ? "없음 (키 미설정)" : value; }
    static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? "작업을 완료하지 못했습니다." : message;
    }

    private static final class Journal {
        boolean hasOriginal, hasOwned, pending;
        String original, owned, before, target;
        String kind = "";
        long startedAt;
        String nativeRequest;
        long nativeBootCount=-1;
        String lastResult = "아직 이 앱에서 역할을 변경하지 않았습니다.";
    }

    static final class Snapshot {
        final String role, oobe, activation, callActivation, messageActivation;
        final boolean permissionGranted, cmcInstalled, cmcEnabled;
        final String cmcVersion;
        final boolean hasOriginal, originalPresent, hasOwned, transactionPending, externalChange, nativeResetPending;
        final String originalRole, ownedRole, transactionBefore, transactionTarget, transactionKind;
        final long transactionStartedAt;
        final String preparationResult, lastResult;

        Snapshot(String role, String oobe, String activation, String call, String message,
                 boolean permission, boolean installed, boolean enabled, String version,
                 Journal journal, String preparation, String result) {
            this.role = role;
            this.oobe = oobe;
            this.activation = activation;
            this.callActivation = call;
            this.messageActivation = message;
            this.permissionGranted = permission;
            this.cmcInstalled = installed;
            this.cmcEnabled = enabled;
            this.cmcVersion = version;
            this.hasOriginal = journal.hasOriginal;
            this.originalPresent = journal.hasOriginal && journal.original != null;
            this.originalRole = journal.original;
            this.hasOwned = journal.hasOwned;
            this.ownedRole = journal.owned;
            this.transactionPending = journal.pending;
            this.nativeResetPending = journal.pending && "native-reset".equals(journal.kind);
            this.transactionBefore = journal.before;
            this.transactionTarget = journal.target;
            this.transactionKind = journal.kind;
            this.transactionStartedAt = journal.startedAt;
            this.externalChange = journal.hasOriginal && !Objects.equals(role, journal.original)
                    && (journal.pending
                    ? !Objects.equals(role, journal.target) && !Objects.equals(role, journal.before)
                    : !journal.hasOwned || !Objects.equals(role, journal.owned));
            this.preparationResult = preparation;
            this.lastResult = result == null ? journal.lastResult : result;
        }

        boolean active() {
            return isActive(activation) || isActive(callActivation) || isActive(messageActivation);
        }

        String text() {
            return "cmc_device_type=" + valueText(role)
                    + "\ncmc_oobe=" + valueText(oobe)
                    + "\ncmc_activation=" + valueText(activation)
                    + "\ncmc_call_activation=" + valueText(callActivation)
                    + "\ncmc_message_activation=" + valueText(messageActivation)
                    + "\nWRITE_SECURE_SETTINGS=" + permissionGranted
                    + "\nCMC installed=" + cmcInstalled + ", enabled=" + cmcEnabled
                    + "\nCMC version=" + cmcVersion
                    + "\nhasOriginal=" + hasOriginal + ", originalPresent=" + originalPresent
                    + "\noriginalRole=" + valueText(originalRole)
                    + "\nhasOwned=" + hasOwned + ", ownedRole=" + valueText(ownedRole)
                    + "\ntransactionPending=" + transactionPending + ", kind=" + transactionKind
                    + "\ntransactionBefore=" + valueText(transactionBefore)
                    + "\ntransactionTarget=" + valueText(transactionTarget)
                    + "\ntransactionStartedAt=" + transactionStartedAt
                    + "\nexternalChange=" + externalChange
                    + "\npreparation_result=" + preparationResult
                    + "\nlastResult=" + lastResult;
        }

        @Override public String toString() { return text(); }
    }
}
