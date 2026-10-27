package com.haha5039.cmc;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Intent;
import java.lang.reflect.Method;
import java.util.List;

/** Hidden platform calls run exclusively inside the already authorized shell UserService. */
final class NativeLauncher {
    static int closeCmcSettings() throws Exception {
        int uid = android.os.Process.myUid();
        if (uid != 2000 && uid != 0) throw new SecurityException("Shizuku 엔진에서만 실행합니다.");
        Class<?> iface = Class.forName("android.app.IActivityTaskManager");
        Object atm = Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);
        List<?> tasks = (List<?>) iface.getMethod("getTasks", int.class, boolean.class, boolean.class, int.class)
                .invoke(atm, 64, false, false, 0);
        int closed = 0;
        String cmc = "com.samsung.android.mdecservice";
        for (Object item : tasks) {
            ActivityManager.RunningTaskInfo task = (ActivityManager.RunningTaskInfo) item;
            ComponentName intent = task.baseIntent == null ? null : task.baseIntent.getComponent();
            if (task.baseActivity == null || task.topActivity == null || intent == null
                    || !cmc.equals(task.baseActivity.getPackageName()) || !cmc.equals(task.topActivity.getPackageName())
                    || !cmc.equals(intent.getPackageName())) continue;
            if (Boolean.TRUE.equals(iface.getMethod("removeTask", int.class).invoke(atm, task.taskId))) closed++;
        }
        return closed;
    }
    static int open(boolean developer, int taskId) throws Exception {
        int uid = android.os.Process.myUid();
        if (uid != 2000 && uid != 0) throw new SecurityException("Shizuku 엔진에서만 실행합니다.");
        Class<?> iface = Class.forName("android.app.IActivityTaskManager");
        Object atm = Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);
        List<?> tasks = (List<?>) iface.getMethod("getTasks", int.class, boolean.class, boolean.class, int.class)
                .invoke(atm, 64, false, false, 0);
        boolean owned = false;
        for (Object item : tasks) {
            ActivityManager.RunningTaskInfo task = (ActivityManager.RunningTaskInfo) item;
            if (task.taskId == taskId && new ComponentName(CommandSpec.PACKAGE,
                    CommandSpec.PACKAGE + ".MainActivity").equals(task.topActivity)) owned = true;
        }
        if (!owned) throw new SecurityException("준비 화면의 작업을 확인하지 못했습니다.");
        Method launch = null;
        for (Method method : iface.getMethods()) {
            if (!method.getName().equals("startActivityFromGameSession") || method.getParameterTypes().length != 8) continue;
            if (launch != null) throw new IllegalStateException("플랫폼 API가 모호합니다.");
            launch = method;
        }
        if (launch == null) throw new IllegalStateException("게임 세션 실행 API가 없습니다.");
        String cmc = "com.samsung.android.mdecservice";
        String target = "com.samsung.android.cmcsettings.view.aboutpage."
                + (developer ? "DeveloperActivity" : "AboutPageActivity");
        Intent intent = new Intent().setComponent(new ComponentName(cmc, target)).setPackage(cmc);
        return (Integer) launch.invoke(atm, null, "com.android.shell", null, -1, 1000, intent, taskId, 0);
    }
}
