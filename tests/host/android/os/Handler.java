// Host-only adapter: deliberately not an Android runtime implementation.
package android.os;
import java.util.ArrayList;
import java.util.List;
public final class Handler {
    private static final List<Task> tasks=new ArrayList<>();
    private static long now;
    private static final class Task {
        final Runnable action; final long at;
        Task(Runnable action,long at) { this.action=action; this.at=at; }
    }
    public Handler(Looper ignored) {}
    public boolean post(Runnable action) { tasks.add(new Task(action,now)); return true; }
    public boolean postDelayed(Runnable action,long delay) { tasks.add(new Task(action,now+delay)); return true; }
    public static void drain() {
        while (true) {
            Task next=null;
            for (Task task : tasks) if (task.at<=now && (next==null || task.at<next.at)) next=task;
            if (next==null) return;
            tasks.remove(next); next.action.run();
        }
    }
    public static void advance(long millis) { now+=millis; drain(); }
    public static void reset() { tasks.clear(); now=0; }
}
