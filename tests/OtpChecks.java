package com.haha5039.cmc;
import java.time.Instant;
public final class OtpChecks {
    private static int count;
    private static void eq(String expected, String actual) {
        if (!expected.equals(actual)) throw new AssertionError(expected + " != " + actual);
        count++;
    }
    public static void main(String[] args) {
        long t = Instant.parse("2026-10-27T00:17:00Z").toEpochMilli();
        eq("1764156171", CmcOtp.response("1abcd", t));
        eq(CmcOtp.response("1abcd", t), CmcOtp.response("1abcd", t + 59999));
        if (CmcOtp.response("1abcd", t).equals(CmcOtp.response("1abcd", t + 60000)))
            throw new AssertionError("minute boundary");
        count++;
        for (String invalid : new String[]{"", "abcdef", "abcd", "A1234", "a bcd", "a;123"}) {
            try { CmcOtp.response(invalid, t); throw new AssertionError("accepted invalid challenge"); }
            catch (IllegalArgumentException expected) { count++; }
        }
        try { CmcOtp.response(null, t); throw new AssertionError("null accepted"); }
        catch (IllegalArgumentException expected) { count++; }
        System.out.println("OTP checks passed: " + count);
    }
}
