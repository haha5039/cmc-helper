// Host-only adapter: deliberately not an Android runtime implementation.
package android.util;
import java.io.*;
import java.nio.file.*;
public final class AtomicFile {
    private final File base,stage;
    public AtomicFile(File base) { this.base=base; stage=new File(base+".new"); }
    public File getBaseFile() { return base; }
    public byte[] readFully() throws IOException {
        if (!base.exists()) throw new FileNotFoundException();
        return Files.readAllBytes(base.toPath());
    }
    public FileOutputStream startWrite() throws IOException { return new FileOutputStream(stage); }
    public void finishWrite(FileOutputStream output) {
        try { output.close(); Files.move(stage.toPath(),base.toPath(),StandardCopyOption.REPLACE_EXISTING); }
        catch (IOException error) { throw new IllegalStateException(error); }
    }
    public void failWrite(FileOutputStream output) {
        try { output.close(); } catch (IOException ignored) {}
        stage.delete();
    }
}
