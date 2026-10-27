// Host-only adapter: deliberately not an Android runtime implementation.
package android.provider;
import android.content.ContentResolver;
public final class Settings { public static final class Global {
    public static String getString(ContentResolver resolver,String key) { return resolver.get(key); }
    public static boolean putString(ContentResolver resolver,String key,String value) { return resolver.put(key,value); }
} }
