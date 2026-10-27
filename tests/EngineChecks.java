package com.haha5039.cmc;

import android.os.Process;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.json.JSONObject;

/** Actual engine + actual ProcessBuilder runner, with an isolated fake settings CLI. */
public final class EngineChecks {
    private static int passed;
    private static final Path ROOT=Paths.get(System.getenv("CMC_TEST_SETTINGS_DIR"));
    private static void check(boolean value) { if (!value) throw new AssertionError(); passed++; }
    private static void state(String role,String key,Object value) throws Exception {
        JSONObject data=new JSONObject();
        data.put("cmc_device_type",role);
        for (String name : new String[]{"cmc_activation","cmc_call_activation","cmc_message_activation"}) data.put(name,"0");
        if (key!=null) data.put(key,value);
        Files.write(ROOT.resolve("state.json"),data.toString().getBytes(StandardCharsets.UTF_8));
        Files.deleteIfExists(ROOT.resolve("events.jsonl"));
    }
    private static List<String> events() throws Exception {
        return Files.exists(ROOT.resolve("events.jsonl")) ? Files.readAllLines(ROOT.resolve("events.jsonl")) : Collections.emptyList();
    }
    private static JSONObject read() throws Exception {
        return new JSONObject(new String(Files.readAllBytes(ROOT.resolve("state.json")),StandardCharsets.UTF_8));
    }
    public static void main(String[] args) throws Exception {
        CmcUserService engine=new CmcUserService();
        state("pd",null,null);
        check(engine.writeRole("sd").equals("WRITE_OK"));
        check(read().getString("cmc_device_type").equals("sd"));
        check(events().size()==4 && events().get(3).contains("put"));
        state("sd",null,null);
        check(engine.writeRole(null).equals("WRITE_OK"));
        check(!read().has("cmc_device_type"));
        check(events().size()==4 && events().get(3).contains("delete"));
        for (String key : new String[]{"cmc_activation","cmc_call_activation","cmc_message_activation"}) {
            for (Object value : new Object[]{"1","-1","unknown","",new JSONObject().put("output","0").put("exit",1)}) {
                state("pd",key,value);
                check(engine.writeRole("sd").startsWith("WRITE_FAILED:"));
                check(read().getString("cmc_device_type").equals("pd"));
                check(events().size()<=3);
                for (String event : events()) check(event.contains("get") && !event.contains("put"));
            }
        }
        state("pd","cmc_activation","null");
        check(engine.writeRole("sd").equals("WRITE_OK"));
        state("pd",null,null);
        check(engine.writeRole("pd;reboot").startsWith("WRITE_FAILED:")); check(events().isEmpty());
        Process.uid=10001;
        try { engine.writeRole("sd"); throw new AssertionError(); }
        catch (SecurityException expected) { check(events().isEmpty()); }
        finally { Process.uid=2000; }
        System.out.println("Engine fixture checks passed: "+passed);
    }
}
