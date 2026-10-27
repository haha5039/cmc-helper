// Host-only adapter: deliberately not an Android runtime implementation.
package android.os;
public final class Looper {
    private static final Looper MAIN=new Looper();
    public static boolean onMain;
    public static Looper getMainLooper() { return MAIN; }
    public static Looper myLooper() { return onMain ? MAIN : null; }
}
