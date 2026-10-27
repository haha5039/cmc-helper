// Host-only adapter: deliberately not an Android runtime implementation.
package android.content;
import android.content.pm.PackageManager;
import java.io.File;
public abstract class Context {
    public static final int MODE_PRIVATE=0;
    public Context getApplicationContext() { return this; }
    public abstract ContentResolver getContentResolver();
    public abstract File getNoBackupFilesDir();
    public abstract int checkSelfPermission(String permission);
    public abstract PackageManager getPackageManager();
    public abstract SharedPreferences getSharedPreferences(String name,int mode);
}
