// Host-only adapter: deliberately not an Android runtime implementation.
package rikka.shizuku;
import android.content.*;
import android.content.pm.PackageManager;
public final class Shizuku {
    public interface BinderReceived { void onBinderReceived(); }
    public interface BinderDead { void onBinderDead(); }
    public interface PermissionResult { void onRequestPermissionResult(int request,int grant); }
    public static boolean alive=true,granted,rationale;
    public static int lastRequest=-1,binds,unbinds;
    public static BinderReceived received;
    public static BinderDead dead;
    public static PermissionResult permission;
    public static ServiceConnection connection;
    public static final class UserServiceArgs {
        public UserServiceArgs(ComponentName ignored) {}
        public UserServiceArgs tag(String ignored) { return this; }
        public UserServiceArgs processNameSuffix(String ignored) { return this; }
        public UserServiceArgs daemon(boolean ignored) { return this; }
        public UserServiceArgs debuggable(boolean ignored) { return this; }
        public UserServiceArgs version(int ignored) { return this; }
    }
    public static void addBinderReceivedListenerSticky(BinderReceived listener) { received=listener; }
    public static void addBinderDeadListener(BinderDead listener) { dead=listener; }
    public static void addRequestPermissionResultListener(PermissionResult listener) { permission=listener; }
    public static boolean pingBinder() { return alive; }
    public static boolean isPreV11() { return false; }
    public static int getVersion() { return 13; }
    public static int checkSelfPermission() { return granted ? PackageManager.PERMISSION_GRANTED : PackageManager.PERMISSION_DENIED; }
    public static boolean shouldShowRequestPermissionRationale() { return rationale; }
    public static void requestPermission(int request) { lastRequest=request; }
    public static void bindUserService(UserServiceArgs args,ServiceConnection callback) { binds++; connection=callback; }
    public static void unbindUserService(UserServiceArgs args,ServiceConnection callback,boolean remove) { unbinds++; }
    public static void reset() { alive=true; granted=false; rationale=false; lastRequest=-1; binds=unbinds=0; }
}
