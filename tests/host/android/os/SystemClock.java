// Host-only adapter: deliberately not an Android runtime implementation.
package android.os;
public final class SystemClock {
    // Logical clock advances on each poll; these tests do not measure Android timing.
    private static long tick;
    public static long elapsedRealtime() { return tick+=1000; }
}
