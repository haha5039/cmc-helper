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
        return preparation(enable ? "prepare" : "disable", request);
    }
    static String[] preparation(String operation, String request) {
        if (!"prepare".equals(operation) && !"disable".equals(operation)
                && !"role_sd".equals(operation) && !"role_pd".equals(operation) && !"developer".equals(operation))
            throw new IllegalArgumentException("Unsupported preparation");
        if (request == null || !request.matches("[a-f0-9]{32}"))
            throw new IllegalArgumentException("Invalid request identifier");
        return new String[]{"am", "instrument", "-w", "--no-restart", "--no-hidden-api-checks",
                "-e", "operation", operation,
                "-e", "request_id", request, "-e", "bridge", "shizuku", PACKAGE + "/.PrepareInstrumentation"};
    }
    static boolean wrote(String role, int exit, String output) {
        role(role);
        if (exit != 0 || output == null) return false;
        String body = output.trim();
        return role == null ? body.matches("Deleted [01] rows") : body.isEmpty();
    }
    static boolean idleSetting(int exit, String output) {
        if (exit != 0 || output == null) return false;
        String value = output.trim();
        if ("null".equals(value)) return true; // settings get reports an absent key as null.
        if (!value.matches("[+-]?[0-9]+")) return false;
        try { return Long.parseLong(value) == 0; }
        catch (NumberFormatException ignored) { return false; }
    }
    static boolean completed(String output, boolean enable, String request) {
        return completed(output, enable ? "prepare" : "disable", request);
    }
    static boolean completed(String output, String operation, String request) {
        preparation(operation, request);
        if (output == null || output.contains("BRIDGE_FAILED:") || output.contains("PREPARATION_FAILED:")
                || output.contains("INSTRUMENTATION_FAILED") || output.contains("INSTRUMENTATION_ABORTED")) return false;
        String prefix = "prepare".equals(operation) ? "PREPARED:"
                : "disable".equals(operation) ? "EXCEPTION_DISABLED:"
                : "developer".equals(operation) ? "DEVELOPER_OPENED:"
                : "ROLE_READY:" + operation.substring(5);
        return output.contains("INSTRUMENTATION_RESULT: request_id=" + request + "\n")
                && output.contains("INSTRUMENTATION_RESULT: result="
                        + prefix)
                && output.contains("INSTRUMENTATION_RESULT: game_permission_after=-1\n")
                && output.contains("INSTRUMENTATION_CODE: -1\n");
    }
}
