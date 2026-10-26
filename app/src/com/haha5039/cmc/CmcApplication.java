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
    private Listener listener;
    private RoleStore.Snapshot snapshot;
    private boolean busy, error;
    private String message = "";

    @Override public void onCreate() {
        super.onCreate();
        store = new RoleStore(this);
    }

    void attach(Listener listener) { this.listener = listener; publish(); }
    void detach(Listener listener) { if (this.listener == listener) this.listener = null; }
    void refresh() { execute("refresh", null); }
    void apply(String role) { execute("apply", role); }
    void restore() { execute("restore", null); }

    private void execute(String operation, String role) {
        if (busy) return;
        busy = true;
        if (!"refresh".equals(operation)) {
            message = "역할을 확인하고 있습니다. 변경이 필요한 경우 약 3초 동안 검증합니다.";
            error = false;
        }
        publish();
        worker.execute(() -> {
            RoleStore.Snapshot next = null;
            String result = message;
            boolean failed = error;
            try {
                if ("apply".equals(operation)) next = store.apply(role);
                else if ("restore".equals(operation)) next = store.restore();
                else next = store.snapshot();
                if (!"refresh".equals(operation)) {
                    result = next.lastResult;
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
        if (listener != null) listener.changed(snapshot, busy, message, error);
    }
}
