// Host-only adapter: deliberately not an Android runtime implementation.
package android.content;
public abstract class ContentResolver {
    public abstract String get(String key);
    public abstract boolean put(String key,String value);
}
