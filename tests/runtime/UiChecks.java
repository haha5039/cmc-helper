package com.haha5039.cmc.test;

import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.os.*;
import android.provider.Settings;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Isolated emulator-only UI checks. Does not use any CMC/Shizuku mutation path. */
public final class UiChecks extends Instrumentation {
    private static final String PACKAGE="com.haha5039.cmc";
    private final AtomicReference<Activity> current=new AtomicReference<>();
    private int passed;
    private Application application;
    private final Application.ActivityLifecycleCallbacks lifecycle=new Application.ActivityLifecycleCallbacks() {
        public void onActivityCreated(Activity a,Bundle state) {}
        public void onActivityStarted(Activity a) {}
        public void onActivityResumed(Activity a) { current.set(a); }
        public void onActivityPaused(Activity a) {}
        public void onActivityStopped(Activity a) {}
        public void onActivitySaveInstanceState(Activity a,Bundle state) {}
        public void onActivityDestroyed(Activity a) {}
    };
    @Override public void onCreate(Bundle args) { start(); }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            // The launcher script also checks ro.kernel.qemu before installing or executing.
            if (!Build.FINGERPRINT.contains("generic") && !Build.MODEL.toLowerCase(Locale.ROOT).contains("sdk"))
                throw new SecurityException("Only run these UI checks on an isolated Android emulator");
            application=(Application)getTargetContext().getApplicationContext();
            application.registerActivityLifecycleCallbacks(lifecycle);
            Map<String,String> before=cmcState();
            launch("MainActivity");
            waitUntil(() -> ui(() -> text("status_cmc").contains("찾을 수")),10000);
            check(ui(() -> find("role_sd").getContentDescription()==null),"role button speaks its visible label");
            check(ui(() -> find("status_cmc").getContentDescription()==null),"status speaks its current value");
            check(ui(() -> text("status_guidance").contains("KEY 계산기")),"missing CMC guidance");
            check(ui(() -> !find("open_native_cmc").isEnabled()),"missing CMC settings disabled");
            screenshot("home.png");
            tap("role_sd");
            waitNode(PACKAGE+":id/cmc_unavailable");
            clickNode(PACKAGE+":id/dialog_close");
            check(ui(() -> text("shizuku_status").contains("Shizuku")),"role help does not request permission");
            ui(() -> { ((ScrollView)find("main_scroll")).scrollTo(0,900); return null; });
            int scroll=ui(() -> find("main_scroll").getScrollY());
            check(scroll>0,"main screen scrolled");
            rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            check(ui(() -> find("main_scroll").getScrollY())==scroll,"main scroll restored after rotation");
            rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            ui(() -> { ((ScrollView)find("main_scroll")).scrollTo(0,0); return null; });
            tap("open_key_calculator");
            waitActivity("OtpCalculatorActivity",null);
            check(ui(() -> find("calculator_key").getContentDescription()==null),"edit field uses label and text");
            check(ui(() -> find("calculator_back").getContentDescription()==null),"calculator button label");
            check(ui(() -> !find("calculator_copy").isEnabled()),"initial copy disabled");
            tap("calculator_calculate");
            check(ui(() -> text("calculator_error").contains("5자리")),"empty KEY error");
            check(ui(() -> find("calculator_error").getContentDescription()==null),"validation reads current error");
            tap("calculator_key_help");
            AccessibilityNodeInfo help=waitNode("android:id/message");
            check(String.valueOf(help.getText()).contains("10번") && String.valueOf(help.getText()).contains("버전 번호"),"KEY entry instructions");
            clickNode(PACKAGE+":id/dialog_close");
            ui(() -> { ((EditText)find("calculator_key")).setText(" 1ABCD "); return null; });
            tap("calculator_calculate");
            assertCurrentCode();
            check(ui(() -> text("calculator_basis").startsWith("KEY 1abcd")),"input normalization");
            check(ui(() -> find("calculator_code").getContentDescription().toString().equals("인증코드 "+text("calculator_code").replace(""," ").trim())),"code speaks individual digits");
            screenshot("calculator.png");
            ui(() -> { ((ScrollView)find("calculator_scroll")).fullScroll(View.FOCUS_DOWN); return null; });
            screenshot("calculator-result.png");
            ui(() -> { ((EditText)find("calculator_key")).setSelection(2); return null; });
            tap("calculator_key_help"); // Destroying a screen with an open dialog must dismiss its window.
            waitNode("android:id/message");
            rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            check(ui(() -> text("calculator_key").equals(" 1ABCD ")),"typed KEY restored");
            check(ui(() -> ((EditText)find("calculator_key")).getSelectionStart())==2,"cursor restored");
            check(ui(() -> find("calculator_copy").isEnabled()),"calculated result restored");
            assertCurrentCode();
            rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            assertCurrentCode();
            tap("calculator_copy");
            check(ui(() -> {
                ClipData clip=current.get().getSystemService(ClipboardManager.class).getPrimaryClip();
                return clip!=null && clip.getItemAt(0).getText().toString().equals(text("calculator_code"))
                        && clip.getDescription().getExtras().getBoolean("android.content.extra.IS_SENSITIVE");
            }),"clipboard value and sensitive flag");
            phase("Waiting for the next UTC minute to verify the foreground timer");
            final long minute=System.currentTimeMillis()/60000;
            waitUntil(() -> System.currentTimeMillis()/60000!=minute,63000);
            waitUntil(() -> ui(() -> text("calculator_code").equals(expectedCode(System.currentTimeMillis()))),4000);
            assertCurrentCode();
            check(ui(() -> text("calculator_countdown").contains("초")),"countdown visible after minute change");
            ui(() -> { ((EditText)find("calculator_key")).setText("zzzzz"); return null; });
            check(ui(() -> text("calculator_code").equals("—") && !find("calculator_copy").isEnabled()),"KEY edits invalidate result");
            rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            check(ui(() -> text("calculator_key").equals("zzzzz") && text("calculator_code").equals("—") && !find("calculator_copy").isEnabled()),"uncalculated input stays uncalculated after rotation");
            check(before.equals(cmcState()),"no CMC state mutations");
            result.putString("result","PASS"); result.putInt("assertions",passed);
        } catch (Throwable error) {
            result.putString("result","FAIL: "+error); result.putInt("assertions",passed);
            java.io.StringWriter trace=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(trace));
            result.putString("trace",trace.toString());
        } finally {
            if (application!=null) application.unregisterActivityLifecycleCallbacks(lifecycle);
            finish(Activity.RESULT_OK,result);
        }
    }
    private Map<String,String> cmcState() {
        Map<String,String> state=new LinkedHashMap<>();
        for (String key : new String[]{"cmc_device_type","cmc_oobe","cmc_activation","cmc_call_activation","cmc_message_activation"})
            state.put(key,Settings.Global.getString(getTargetContext().getContentResolver(),key));
        return state;
    }
    private void launch(String simple) {
        ui(() -> { getTargetContext().startActivity(new Intent().setClassName(PACKAGE,PACKAGE+"."+simple).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return null; });
        waitActivity(simple,null);
    }
    private void rotate(int orientation) {
        Activity old=current.get();
        String name=old.getClass().getSimpleName();
        ui(() -> { old.setRequestedOrientation(orientation); return null; });
        waitActivity(name,old);
    }
    private void waitActivity(String simple,Activity previous) {
        waitUntil(() -> current.get()!=null && current.get()!=previous && current.get().getClass().getSimpleName().equals(simple),10000);
        waitForIdleSync(); SystemClock.sleep(250); waitForIdleSync();
    }
    private void waitUntil(BooleanSupplier ready,long millis) {
        long until=SystemClock.elapsedRealtime()+millis;
        do { if (ready.getAsBoolean()) return; SystemClock.sleep(80); } while (SystemClock.elapsedRealtime()<until);
        throw new AssertionError("Timed out; current="+current.get());
    }
    private <T> T ui(Callable<T> task) {
        AtomicReference<T> value=new AtomicReference<>(); AtomicReference<Throwable> error=new AtomicReference<>();
        runOnMainSync(() -> { try { value.set(task.call()); } catch (Throwable failure) { error.set(failure); } });
        if (error.get()!=null) throw new AssertionError(error.get()); return value.get();
    }
    private View find(String name) {
        int id=getTargetContext().getResources().getIdentifier(name,"id",PACKAGE);
        View view=current.get().findViewById(id);
        if (view==null) throw new AssertionError("Missing view "+name); return view;
    }
    private String text(String name) { return ((TextView)find(name)).getText().toString(); }
    private void tap(String name) { check(ui(() -> find(name).performClick()),"click "+name); waitForIdleSync(); }
    private AccessibilityNodeInfo waitNode(String id) {
        AtomicReference<AccessibilityNodeInfo> node=new AtomicReference<>();
        waitUntil(() -> {
            AccessibilityNodeInfo root=getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).getRootInActiveWindow();
            if (root==null) return false;
            List<AccessibilityNodeInfo> found=root.findAccessibilityNodeInfosByViewId(id);
            if (found.size()!=1) return false; node.set(found.get(0)); return true;
        },5000);
        return node.get();
    }
    private void clickNode(String id) { check(waitNode(id).performAction(AccessibilityNodeInfo.ACTION_CLICK),"dialog click"); waitForIdleSync(); }
    private void assertCurrentCode() {
        check(ui(() -> text("calculator_code").equals(expectedCode(System.currentTimeMillis()))),"code matches independent UTC calculation");
    }
    private static String expectedCode(long millis) {
        ZonedDateTime time=Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC);
        String input="1abcd"+String.format(Locale.ROOT,"%02d%02d%02d%02d%02d",time.getYear()-2000,time.getMonthValue(),time.getMinute(),time.getDayOfMonth(),time.getHour());
        int hash=0;for (int i=0;i<input.length();i++) hash=hash*33+input.charAt(i);
        return Integer.toString(hash<0 ? -hash : hash);
    }
    private void check(boolean value,String message) { if (!value) throw new AssertionError(message); passed++; }
    private void screenshot(String name) throws Exception {
        waitForIdleSync(); SystemClock.sleep(300);
        android.graphics.Bitmap bitmap=getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).takeScreenshot();
        if (bitmap==null) throw new AssertionError("No screenshot");
        java.io.File folder=new java.io.File(getTargetContext().getExternalFilesDir(null),"ui-checks");
        if (!folder.isDirectory() && !folder.mkdirs()) throw new AssertionError("No screenshot directory");
        try (java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(folder,name))) {
            if (!bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out)) throw new AssertionError("Screenshot failed");
        } finally { bitmap.recycle(); }
    }
    private void phase(String message) { Bundle status=new Bundle();status.putString("phase",message);sendStatus(0,status); }
}
