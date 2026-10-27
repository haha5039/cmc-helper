package com.haha5039.cmc;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** No shell parser, bounded output and lifetime; no generic IPC exposes this runner. */
final class BoundedCommand {
    static final class Result {
        final int exit;
        final String output;
        Result(int exit, String output) { this.exit = exit; this.output = output; }
    }
    static Result run(long timeoutMillis, String... arguments) throws Exception {
        if (timeoutMillis < 1 || timeoutMillis > 75000) throw new IllegalArgumentException("Invalid deadline");
        Process process = new ProcessBuilder(arguments).redirectErrorStream(true).start();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        AtomicReference<Exception> failure = new AtomicReference<>();
        Thread reader = new Thread(() -> {
            try (InputStream input = process.getInputStream()) {
                byte[] buffer = new byte[2048];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    synchronized (bytes) {
                        if (bytes.size() + count > 24000) {
                            failure.set(new IllegalStateException("명령 출력 제한을 초과했습니다."));
                            process.destroyForcibly();
                            return;
                        }
                        bytes.write(buffer, 0, count);
                    }
                }
            } catch (Exception error) { failure.compareAndSet(null, error); }
        }, "cmc-command-output");
        reader.setDaemon(true);
        reader.start();
        try {
            if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS))
                throw new IllegalStateException("작업 시간이 초과되었습니다. 현재 역할과 마지막 준비 결과를 확인하세요.");
            reader.join(1500);
            if (reader.isAlive()) throw new IllegalStateException("명령 출력 완료를 확인하지 못했습니다.");
            if (failure.get() != null) throw failure.get();
            synchronized (bytes) {
                return new Result(process.exitValue(), new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            }
        } finally {
            process.destroyForcibly();
            try { process.getInputStream().close(); } catch (Exception ignored) { }
            try { process.getOutputStream().close(); } catch (Exception ignored) { }
            try { process.getErrorStream().close(); } catch (Exception ignored) { }
        }
    }
}
