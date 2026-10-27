package com.haha5039.cmc;

import java.util.Arrays;

public final class BridgeChecks {
    private static int passed;
    private static final String REQUEST = "0123456789abcdef0123456789abcdef";
    private static void check(boolean condition) { if (!condition) throw new AssertionError(); passed++; }
    private static String response(boolean enable) {
        return "INSTRUMENTATION_RESULT: request_id=" + REQUEST + "\n"
                + "INSTRUMENTATION_RESULT: result=" + (enable ? "PREPARED:" : "EXCEPTION_DISABLED:") + " done\n"
                + "INSTRUMENTATION_RESULT: game_permission_after=-1\nINSTRUMENTATION_CODE: -1\n";
    }
    public static void main(String[] args) throws Exception {
        check(Arrays.equals(CommandSpec.role("sd"), new String[]{"settings","put","global","cmc_device_type","sd"}));
        check(Arrays.equals(CommandSpec.role("pd"), new String[]{"settings","put","global","cmc_device_type","pd"}));
        check(Arrays.equals(CommandSpec.role(null), new String[]{"settings","delete","global","cmc_device_type"}));
        check(CommandSpec.wrote("pd",0,""));
        check(!CommandSpec.wrote("sd",1,""));
        check(!CommandSpec.wrote("pd",0,"Deleted 1 rows\n"));
        check(CommandSpec.wrote(null,0,"Deleted 1 rows\n"));
        check(CommandSpec.wrote(null,0,"Deleted 0 rows\n"));
        check(!CommandSpec.wrote(null,0,""));
        check(!CommandSpec.wrote(null,0,"Deleted 1 rows\nSecurityException\n"));
        for (String role : new String[]{"", "tablet", "SD", "pd;reboot", "sd\nanything", "--help"}) {
            try { CommandSpec.role(role); throw new AssertionError(); }
            catch (IllegalArgumentException expected) { passed++; }
        }
        for (String request : new String[]{"", "../bad", "x", "0123456789abcdef0123456789abcdef;id"}) {
            try { CommandSpec.preparation(true,request); throw new AssertionError(); }
            catch (IllegalArgumentException expected) { passed++; }
        }
        for (boolean enable : new boolean[]{false,true}) {
            String[] command = CommandSpec.preparation(enable, REQUEST);
            check(Arrays.asList(command).contains("--no-restart"));
            check(Arrays.asList(command).contains("shizuku"));
            check(command[command.length-1].equals("com.haha5039.cmc/.PrepareInstrumentation"));
            check(CommandSpec.completed(response(enable), enable, REQUEST));
            check(!CommandSpec.completed(response(!enable), enable, REQUEST));
            check(!CommandSpec.completed(response(enable).replace(REQUEST, "ffffffffffffffffffffffffffffffff"), enable, REQUEST));
            check(!CommandSpec.completed(response(enable).replace("permission_after=-1", "permission_after=0"), enable, REQUEST));
            check(!CommandSpec.completed(response(enable).replace("permission_after=-1", "permission_after=-10"), enable, REQUEST));
            check(!CommandSpec.completed(response(enable).replace("INSTRUMENTATION_CODE: -1\n", ""), enable, REQUEST));
            check(!CommandSpec.completed("BRIDGE_FAILED:\n"+response(enable), enable, REQUEST));
            check(!CommandSpec.completed("PREPARATION_FAILED:\n"+response(enable), enable, REQUEST));
            check(!CommandSpec.completed(null, enable, REQUEST));
        }
        for (String role : new String[]{"sd","pd"}) {
            String operation = "role_" + role;
            String[] command = CommandSpec.preparation(operation,REQUEST);
            check(Arrays.asList(command).contains(operation));
            check(Arrays.asList(command).contains("--no-restart"));
            String result = response(true).replace("PREPARED:","ROLE_READY:"+role);
            check(CommandSpec.completed(result,operation,REQUEST));
            check(!CommandSpec.completed(result,"role_"+("sd".equals(role)?"pd":"sd"),REQUEST));
            check(!CommandSpec.completed(result.replace(REQUEST,"ffffffffffffffffffffffffffffffff"),operation,REQUEST));
            check(!CommandSpec.completed("PREPARATION_FAILED:\n"+result,operation,REQUEST));
            check(!CommandSpec.completed(response(true),operation,REQUEST));
        }
        try { CommandSpec.preparation("role_x",REQUEST); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { passed++; }
        String developer = response(true).replace("PREPARED:","DEVELOPER_OPENED:");
        check(Arrays.asList(CommandSpec.preparation("developer",REQUEST)).contains("developer"));
        check(CommandSpec.completed(developer,"developer",REQUEST));
        check(!CommandSpec.completed(developer,"prepare",REQUEST));
        check(!CommandSpec.completed(developer,"role_pd",REQUEST));
        check(!CommandSpec.completed(developer.replace(REQUEST,"ffffffffffffffffffffffffffffffff"),"developer",REQUEST));
        check(!CommandSpec.completed(developer.replace("permission_after=-1","permission_after=0"),"developer",REQUEST));
        try { CommandSpec.preparation("DeveloperActivity",REQUEST); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { passed++; }
        String java = System.getProperty("java.home") + "/bin/java";
        String classpath = System.getProperty("java.class.path");
        String fixture = "com.haha5039.cmc.BridgeFixture";
        BoundedCommand.Result ok = BoundedCommand.run(3000, java, "-cp", classpath, fixture, "print", "literal;$(nothing)");
        check(ok.exit == 0 && ok.output.equals("literal;$(nothing)"));
        check(BoundedCommand.run(3000, java, "-cp", classpath, fixture, "exit").exit == 7);
        long before = System.nanoTime();
        try { BoundedCommand.run(150, java, "-cp", classpath, fixture, "sleep"); throw new AssertionError(); }
        catch (IllegalStateException expected) { passed++; }
        check((System.nanoTime()-before)/1000000 < 3000);
        try { BoundedCommand.run(3000, java, "-cp", classpath, fixture, "flood"); throw new AssertionError(); }
        catch (IllegalStateException expected) { passed++; }
        System.out.println("Bridge checks passed: " + passed);
    }
}

final class BridgeFixture {
    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "print": System.out.print(args[1]); break;
            case "exit": System.exit(7); break;
            case "sleep": Thread.sleep(10000); break;
            case "flood": for (int i=0;i<30000;i++) System.out.print('x'); break;
        }
    }
}
