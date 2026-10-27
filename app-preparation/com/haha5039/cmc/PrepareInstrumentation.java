package com.haha5039.cmc;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.app.UiAutomation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.accessibility.AccessibilityNodeInfo;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finite self-instrumentation started by Shizuku or ADB. Never invokes ResetService. */
public final class PrepareInstrumentation extends Instrumentation {
    private static final String CMC = "com.samsung.android.mdecservice";
    private static final String PREFIX = CMC + ":id/";
    private UiAutomation automation;
    private Bundle arguments;
    private long deadline;

    @Override public void onCreate(Bundle args) { arguments = args; start(); }

    @Override public void onStart() {
        Bundle out = new Bundle();
        deadline = SystemClock.uptimeMillis() + 45000;
        out.putString("request_id", arguments == null ? "" : arguments.getString("request_id", ""));
        out.putInt("actual_uid", Process.myUid());
        try {
            Context context = getTargetContext();
            if (Process.myUid() < 10000) throw new SecurityException("일반 앱 UID에서만 실행합니다.");
            KeyguardManager keyguard = context.getSystemService(KeyguardManager.class);
            if (keyguard == null || keyguard.isDeviceLocked())
                throw new IllegalStateException("먼저 기기 잠금을 해제하세요.");
            PackageInfo info = context.getPackageManager().getPackageInfo(CMC, 0);
            if (info.getLongVersionCode() != 910015100L || !"9.1.00.15".equals(info.versionName))
                throw new IllegalStateException("이 준비 절차는 CMC 9.1.00.15에서만 검증됐습니다.");
            automation = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            if (automation == null) throw new IllegalStateException("ADB am instrument로 실행하세요.");
            String operation = arguments == null ? "prepare" : arguments.getString("operation", "prepare");
            boolean switching = "role_sd".equals(operation) || "role_pd".equals(operation);
            boolean developerShortcut = "developer".equals(operation);
            if (!"prepare".equals(operation) && !"disable".equals(operation) && !switching && !developerShortcut)
                throw new IllegalArgumentException("지원하지 않는 작업");
            if ("disable".equals(operation)) {
                for (String key : new String[]{"cmc_activation", "cmc_call_activation", "cmc_message_activation"})
                    if (!"0".equals(Settings.Global.getString(context.getContentResolver(), key)))
                        throw new IllegalStateException("예외 해제 전 CMC의 전화·메시지를 모두 끄세요.");
                if (!"pd".equals(Settings.Global.getString(context.getContentResolver(), "cmc_device_type")))
                    throw new IllegalStateException("예외 해제 전 발신 역할(pd)로 전환하세요.");
            }
            // A live MainActivity can be reused by Android. startActivitySync would wait
            // indefinitely for a creation callback which never arrives in that case.
            returnToHelper(context);
            final int task = ((CmcApplication) context.getApplicationContext()).preparationTaskId();
            if (task < 0) throw new IllegalStateException("준비 화면의 작업 ID를 확인하지 못했습니다.");
            if (switching) {
                boolean needsFlag = "role_sd".equals(operation)
                        && "1".equals(Settings.Global.getString(context.getContentResolver(), "cmc_oobe"));
                if (!needsFlag) {
                    deactivateUsage(context);
                    returnToHelper(context);
                    out.putString("result", "ROLE_READY:" + operation.substring(5)
                            + " CMC 꺼짐을 확인했습니다. 역할 허용 여부는 적용 후 확인합니다.");
                    return;
                }
            }
            if (("prepare".equals(operation) || developerShortcut)
                    && !"1".equals(Settings.Global.getString(context.getContentResolver(), "cmc_oobe")))
                throw new IllegalStateException("삼성 CMC의 초기 설정을 먼저 완료하세요. 초기 설정 전에는 정보 화면이 자동 종료됩니다.");
            boolean phone = arguments != null && "shizuku".equals(arguments.getString("bridge", ""));
            Class<?> iface = phone ? null : Class.forName("android.app.IActivityTaskManager");
            Object atm = phone ? null : Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);
            Method launch = null;
            if (!phone) for (Method method : iface.getMethods())
                if (method.getName().equals("startActivityFromGameSession") && method.getParameterTypes().length == 8) {
                    if (launch != null) throw new IllegalStateException("플랫폼 API가 모호합니다.");
                    launch = method;
                }
            if (!phone && launch == null) throw new IllegalStateException("플랫폼에 게임 세션 실행 API가 없습니다.");
            if ("disable".equals(operation)) {
                // The activity consumes and deletes the one-shot verification marker.
                // Two fresh entries guarantee that the second entry is unverified.
                for (int pass = 0; pass < 2; pass++) {
                    requestLaunch(phone, launch, atm, task, true);
                    waitId(PREFIX + "save_restart_btn", 5000);
                    AccessibilityNodeInfo toggle = checkable(waitId(PREFIX + "dev_switch_bar", 3000));
                    if (toggle == null) throw new IllegalStateException("CMC 테스트 스위치를 찾지 못했습니다.");
                    // Unverified onSwitchChanged(true) stores test_flag=false. A false
                    // transition would call ResetService, so it is NEVER performed here.
                    if (!toggle.isChecked()) click(toggle);
                    SystemClock.sleep(400);
                    if (!automation.performGlobalAction(1)) throw new IllegalStateException("CMC 화면 복귀 실패");
                    SystemClock.sleep(400);
                }
                String message = "EXCEPTION_DISABLED: 인증되지 않은 삼성 화면 경로로 역할 예외를 해제했습니다. "
                        + "개발자 UI의 스위치는 켜져 있어도 내부 test_flag는 별개입니다. 초기화·재부팅 요청 없음.";
                context.getSharedPreferences("cmc_helper", 0).edit().putString("preparation_result", message).commit();
                out.putString("result", message);
                context.startActivity(new Intent(context, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                return;
            }
            requestLaunch(phone, launch, atm, task, false);
            waitId(PREFIX + "about_cmc_app_name_text_view", 8000);
            for (int i = 0; i < 10; i++) {
                click(waitId(PREFIX + "about_cmc_app_name_text_view", 2000));
                SystemClock.sleep(70);
            }
            AccessibilityNodeInfo edit = waitId(PREFIX + "about_cmc_dialog_pwd_edit_text", 5000);
            String text = allText(root());
            Matcher match = Pattern.compile("KEY\\s*:\\s*([a-z0-9]{5})").matcher(text);
            String response;
            if (match.find()) response = CmcOtp.response(match.group(1), System.currentTimeMillis());
            else throw new IllegalStateException("예상한 CMC KEY 대화상자가 없습니다. 자동 입력을 중단했습니다.");
            Bundle input = new Bundle();
            input.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, response);
            if (!edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, input))
                throw new IllegalStateException("CMC 대화상자 입력 실패");
            click(waitId("android:id/button1", 2000));
            waitId(PREFIX + "save_restart_btn", 5000);
            if (developerShortcut) {
                String message = "DEVELOPER_OPENED: 삼성 CMC 개발자 화면을 열었습니다. 버튼이나 스위치는 조작하지 않았습니다.";
                context.getSharedPreferences("cmc_helper", 0).edit().putString("developer_result", message).commit();
                out.putString("result", message);
                return;
            }
            AccessibilityNodeInfo bar = waitId(PREFIX + "dev_switch_bar", 3000);
            AccessibilityNodeInfo toggle = checkable(bar);
            if (toggle == null) throw new IllegalStateException("CMC 테스트 스위치를 찾지 못했습니다.");
            if (!toggle.isChecked()) click(toggle);
            SystemClock.sleep(500);
            toggle = checkable(waitId(PREFIX + "dev_switch_bar", 2000));
            if (toggle == null || !toggle.isChecked()) throw new IllegalStateException("테스트 스위치 켜짐을 확인하지 못했습니다.");
            if (switching) {
                // AboutPageActivity exits when cmc_oobe is off. Prepare the flag
                // before the native service-off flow can clear that value.
                returnToHelper(context);
                deactivateUsage(context);
                returnToHelper(context);
            }
            // No reset, reboot, server override, role change, or activation write.
            String message = "PREPARED: 삼성 CMC 역할 예외를 켰습니다. "
                    + "역할 적용 및 실제 전화·문자 수신은 별도 확인이 필요합니다.";
            context.getSharedPreferences("cmc_helper", 0).edit().putString("preparation_result", message).commit();
            out.putString("result", switching ? "ROLE_READY:sd CMC 사용을 끄고 수신 역할 예외를 준비했습니다." : message);
            out.putString("role_after", Settings.Global.getString(context.getContentResolver(), "cmc_device_type"));
            // Return to our app without touching Samsung's Apply and Reset control.
            context.startActivity(new Intent(context, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        } catch (Throwable error) {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            String failure = "PREPARATION_FAILED: " + cause.getClass().getSimpleName() + ": " + cause.getMessage();
            getTargetContext().getSharedPreferences("cmc_helper", 0).edit()
                    .putString("preparation_result", failure).commit();
            out.putString("result", failure);
            try {
                getTargetContext().startActivity(new Intent(getTargetContext(), MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            } catch (RuntimeException ignored) { }
        } finally {
            out.putInt("game_permission_after", getTargetContext()
                    .checkSelfPermission("android.permission.MANAGE_GAME_ACTIVITY"));
            out.putInt("uid_after", Process.myUid());
            finish(Activity.RESULT_OK, out);
        }
    }

    private boolean allOff(Context context) {
        for (String key : new String[]{"cmc_activation", "cmc_call_activation", "cmc_message_activation"})
            if (!"0".equals(Settings.Global.getString(context.getContentResolver(), key))) return false;
        return true;
    }

    /** Operate the actual service switches; never forge activation values in Settings.Global. */
    private void deactivateUsage(Context context) {
        if (allOff(context)) return;
        context.startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("cmc://setting?said=samsungservice"))
                .setPackage(CMC).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        for (int index = 0; index < 2; index++) {
            String key = index == 0 ? "cmc_call_activation" : "cmc_message_activation";
            long until = Math.min(deadline, SystemClock.uptimeMillis() + 9000);
            AccessibilityNodeInfo toggle = null;
            while (SystemClock.uptimeMillis() < until) {
                if ("0".equals(Settings.Global.getString(context.getContentResolver(), key))) break;
                AccessibilityNodeInfo current = root();
                toggle = find(current, PREFIX + (index == 0 ? "call_switch_widget" : "message_switch_widget"));
                if (toggle == null) {
                    List<AccessibilityNodeInfo> candidates = new ArrayList<>();
                    collect(current, "android:id/switch_widget", candidates);
                    if (candidates.size() == 2) toggle = candidates.get(index);
                }
                if (toggle != null && toggle.isCheckable()) break;
                SystemClock.sleep(100);
            }
            if (!"0".equals(Settings.Global.getString(context.getContentResolver(), key))) {
                if (toggle == null || !toggle.isCheckable())
                    throw new IllegalStateException("삼성 CMC 사용 스위치를 확인하지 못했습니다. 삼성 설정에서 직접 끈 뒤 다시 전환하세요.");
                if (toggle.isChecked()) click(toggle);
                waitOff(context, key);
            }
        }
        long until = Math.min(deadline, SystemClock.uptimeMillis() + 6000);
        while (!allOff(context) && SystemClock.uptimeMillis() < until) SystemClock.sleep(100);
        if (!allOff(context)) throw new IllegalStateException("CMC 꺼짐을 확인하지 못했습니다. 역할 적용 단계는 진행하지 않았습니다.");
        context.getSharedPreferences("cmc_helper", 0).edit()
                .putString("deactivation_result", "삼성 사용 스위치와 전체·전화·메시지 꺼짐을 확인했습니다.").commit();
    }

    private void waitOff(Context context, String key) {
        long until = Math.min(deadline, SystemClock.uptimeMillis() + 7000);
        while (SystemClock.uptimeMillis() < until) {
            if ("0".equals(Settings.Global.getString(context.getContentResolver(), key))) return;
            SystemClock.sleep(100);
        }
        throw new IllegalStateException("삼성 CMC의 끄기 처리가 완료되지 않았습니다. 삼성 설정에서 상태를 확인하세요.");
    }

    private void returnToHelper(Context context) {
        context.startActivity(new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        long until = Math.min(deadline, SystemClock.uptimeMillis() + 4000);
        while (SystemClock.uptimeMillis() < until) {
            AccessibilityNodeInfo node = automation.getRootInActiveWindow();
            if (node != null && context.getPackageName().equals(String.valueOf(node.getPackageName()))) return;
            SystemClock.sleep(100);
        }
        throw new IllegalStateException("전환 준비 화면으로 돌아오지 못했습니다.");
    }

    private static void collect(AccessibilityNodeInfo node, String id, List<AccessibilityNodeInfo> result) {
        if (node == null) return;
        if (id.equals(node.getViewIdResourceName())) result.add(node);
        for (int i = 0; i < node.getChildCount(); i++) collect(node.getChild(i), id, result);
    }

    private void requestLaunch(boolean phone, Method launch, Object atm, int task, boolean developer) throws Exception {
        if (phone) {
            String request = arguments.getString("request_id", "");
            int result = ((CmcApplication) getTargetContext().getApplicationContext())
                    .launchForPreparation(request, developer, task);
            if (result != 0) throw new IllegalStateException("삼성 화면 실행 실패: " + result);
        } else {
            launchNative(launch, atm, task, "com.samsung.android.cmcsettings.view.aboutpage."
                    + (developer ? "DeveloperActivity" : "AboutPageActivity"));
        }
    }

    private void launchNative(Method launch, Object atm, int task, String clazz) throws Exception {
        Intent target = new Intent().setComponent(new ComponentName(CMC, clazz)).setPackage(CMC);
        automation.adoptShellPermissionIdentity("android.permission.MANAGE_GAME_ACTIVITY");
        Object result;
        try { result = launch.invoke(atm, null, getTargetContext().getPackageName(), null, -1, 1000, target, task, 0); }
        finally { automation.dropShellPermissionIdentity(); }
        if (!Integer.valueOf(0).equals(result)) throw new IllegalStateException("CMC 화면 실행 실패: " + result);
    }

    private AccessibilityNodeInfo root() {
        AccessibilityNodeInfo node = automation.getRootInActiveWindow();
        if (node == null || !CMC.equals(String.valueOf(node.getPackageName()))) return null;
        return node;
    }
    private AccessibilityNodeInfo waitId(String id, long timeout) {
        long until = Math.min(deadline, SystemClock.uptimeMillis() + timeout);
        do {
            AccessibilityNodeInfo node = find(root(), id);
            if (node != null) return node;
            SystemClock.sleep(100);
        } while (SystemClock.uptimeMillis() < until);
        throw new IllegalStateException("예상한 CMC 화면이 아닙니다: " + id);
    }
    private static AccessibilityNodeInfo find(AccessibilityNodeInfo node, String id) {
        if (node == null) return null;
        if (id.equals(node.getViewIdResourceName())) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = find(node.getChild(i), id);
            if (found != null) return found;
        }
        return null;
    }
    private static AccessibilityNodeInfo checkable(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isCheckable()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = checkable(node.getChild(i));
            if (found != null) return found;
        }
        return null;
    }
    private static void click(AccessibilityNodeInfo node) {
        if (node == null || !node.isEnabled() || !node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            throw new IllegalStateException("CMC 컨트롤 조작 실패");
    }
    private static String allText(AccessibilityNodeInfo node) {
        if (node == null) return "";
        StringBuilder text = new StringBuilder();
        if (node.getText() != null) text.append(node.getText()).append('\n');
        for (int i = 0; i < node.getChildCount(); i++) text.append(allText(node.getChild(i)));
        return text.toString();
    }
}
