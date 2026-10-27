package com.haha5039.cmc;

import java.time.Instant;
import java.util.Locale;
import java.util.TimeZone;

public final class CalculatorChecks {
    private static int count;
    private static void check(boolean condition) { if (!condition) throw new AssertionError(); count++; }
    public static void main(String[] args) {
        long now = Instant.parse("2026-10-27T00:17:00Z").toEpochMilli();
        check(OtpCalculator.normalize(" 1AbCd ").equals("1abcd"));
        OtpCalculator.Result result = OtpCalculator.calculate(" 1ABCD ",now);
        check(result.key.equals("1abcd"));
        check(result.code.equals("1764156171"));
        check(result.utcMinute.equals("2026-10-27 00:17 UTC"));
        check(result.minute == Math.floorDiv(now,60000));
        check(OtpCalculator.calculate("1abcd",now+59999).code.equals(result.code));
        check(OtpCalculator.calculate("1abcd",now+60000).minute == result.minute+1);
        check(!OtpCalculator.calculate("1abcd",now+60000).code.equals(result.code));
        check(OtpCalculator.calculate("1abcd",now+60000).code.equals("1762970250"));
        check(OtpCalculator.calculate("1abcd",Instant.parse("2026-12-31T23:59:00Z").toEpochMilli()).code.equals("977722653"));
        check(OtpCalculator.calculate("1abcd",Instant.parse("2027-01-01T00:00:00Z").toEpochMilli()).code.equals("1765338982"));
        check(OtpCalculator.secondsToNextCode(now)==60);
        check(OtpCalculator.secondsToNextCode(now+1000)==59);
        check(OtpCalculator.secondsToNextCode(now+59000)==1);
        check(OtpCalculator.secondsToNextCode(now+59999)==1);
        check(OtpCalculator.secondsToNextCode(now+60000)==60);
        check(OtpCalculator.secondsToNextCode(-1)==1);
        check(OtpCalculator.secondsToNextCode(0)==60);
        for (String invalid : new String[]{null,"","    ","abcd","abcdef","a bcd","KEY:1abcd","abc$1","가나다라마","ＡＢＣＤＥ"}) {
            try { OtpCalculator.calculate(invalid,now); throw new AssertionError("Invalid key accepted"); }
            catch (IllegalArgumentException expected) { count++; }
        }
        Locale originalLocale = Locale.getDefault();
        TimeZone originalZone = TimeZone.getDefault();
        try {
            Locale.setDefault(new Locale("tr","TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));
            check(OtpCalculator.normalize("I1234").equals("i1234"));
            check(OtpCalculator.calculate("1ABCD",now).code.equals(result.code));
            check(OtpCalculator.calculate("1abcd",now).utcMinute.equals(result.utcMinute));
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
            check(OtpCalculator.calculate("1abcd",now).code.equals(result.code));
        } finally { Locale.setDefault(originalLocale); TimeZone.setDefault(originalZone); }
        System.out.println("Calculator checks passed: " + count);
    }
}
