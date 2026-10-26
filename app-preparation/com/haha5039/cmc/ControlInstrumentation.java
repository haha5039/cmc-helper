package com.haha5039.cmc;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import android.os.Process;

/** ADB-only finite role operation using the app's ordinary permission and engine. */
public final class ControlInstrumentation extends Instrumentation {
    private String operation;
    @Override public void onCreate(Bundle args) {
        operation = args == null ? "status" : args.getString("operation", "status");
        start();
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        result.putInt("actual_uid", Process.myUid());
        try {
            if (Process.myUid() < 10000) throw new SecurityException("일반 앱 UID만 허용합니다.");
            RoleStore store = new RoleStore(getTargetContext());
            RoleStore.Snapshot state;
            switch (operation) {
                case "receiver": state = store.apply("sd"); break;
                case "sender": state = store.apply("pd"); break;
                case "restore": state = store.restore(); break;
                case "status": state = store.snapshot(); break;
                default: throw new IllegalArgumentException("허용된 작업: receiver, sender, restore, status");
            }
            result.putString("result", "CONTROL_OK: " + operation);
            result.putString("state", state.text());
        } catch (Throwable error) {
            result.putString("result", "CONTROL_FAILED: " + error.getClass().getSimpleName()
                    + ": " + error.getMessage());
        }
        result.putInt("game_permission_after", getTargetContext()
                .checkSelfPermission("android.permission.MANAGE_GAME_ACTIVITY"));
        finish(Activity.RESULT_OK, result);
    }
}
