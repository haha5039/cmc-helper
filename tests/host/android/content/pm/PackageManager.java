// Host-only adapter: deliberately not an Android runtime implementation.
package android.content.pm;
public abstract class PackageManager {
    public static final int PERMISSION_GRANTED=0,PERMISSION_DENIED=-1;
    public static class NameNotFoundException extends Exception {}
    public abstract PackageInfo getPackageInfo(String name,int flags) throws NameNotFoundException;
}
