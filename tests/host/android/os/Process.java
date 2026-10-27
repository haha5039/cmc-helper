// Host-only UID adapter. Never built into the APK.
package android.os;
public final class Process {
    public static int uid=2000;
    public static int myUid() { return uid; }
}
