package com.haha5039.cmc;

/** Fixed command vocabulary shared by the phone-only bridge and host checks. */
final class CommandSpec {
    static final String PACKAGE = "com.haha5039.cmc";
    private CommandSpec() {}
    static String[] role(String value) {
        if (value == null) return new String[]{"settings", "delete", "global", "cmc_device_type"};
        if (!"pd".equals(value) && !"sd".equals(value)) throw new IllegalArgumentException("Unsupported role");
        return new String[]{"settings", "put", "global", "cmc_device_type", value};
    }
    static String[] preparation(boolean enable, String request) {
        if (request == null || !request.matches("[a-f0-9]{32}"))
            throw new IllegalArgumentException("Invalid request identifier");
        return new String[]{"am", "instrument", "-w", "--no-restart", "--no-hidden-api-checks",
                "-e", "operation", enable ? "prepare" : "disable",
                "-e", "request_id", request, "-e", "bridge", "shizuku", PACKAGE + "/.PrepareInstrumentation"};
    }
    static boolean wrote(String role, int exit, String output) {
        role(role);
        if (exit != 0 || output == null) return false;
        String body = output.trim();
        return role == null ? body.matches("Deleted [01] rows") : body.isEmpty();
    }
    static boolean completed(String output, boolean enable, String request) {
        preparation(enable, request); // Validate even when the output is malformed.
        if (output == null || output.contains("BRIDGE_FAILED:") || output.contains("PREPARATION_FAILED:")
                || output.contains("INSTRUMENTATION_FAILED") || output.contains("INSTRUMENTATION_ABORTED")) return false;
        return output.contains("INSTRUMENTATION_RESULT: request_id=" + request + "\n")
                && output.contains("INSTRUMENTATION_RESULT: result="
                        + (enable ? "PREPARED:" : "EXCEPTION_DISABLED:"))
                && output.contains("INSTRUMENTATION_RESULT: game_permission_after=-1\n")
                && output.contains("INSTRUMENTATION_CODE: -1\n");
    }
}
