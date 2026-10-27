package com.haha5039.cmc;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Local-only manual calculator; shares the exact native challenge algorithm. */
final class OtpCalculator {
    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm", Locale.ROOT)
            .withZone(ZoneOffset.UTC);
    static String normalize(String input) {
        String key = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
        if (!key.matches("[a-z0-9]{5}"))
            throw new IllegalArgumentException("영문 소문자·숫자로 된 KEY 5자리를 입력하세요.");
        return key;
    }
    static Result calculate(String input, long nowMillis) {
        String key = normalize(input);
        return new Result(key, CmcOtp.response(key, nowMillis),
                Math.floorDiv(nowMillis, 60000L), UTC.format(Instant.ofEpochMilli(nowMillis)) + " UTC");
    }
    static int secondsToNextCode(long nowMillis) {
        return 60 - (int) (Math.floorMod(nowMillis, 60000L) / 1000L);
    }
    static final class Result {
        final String key, code, utcMinute;
        final long minute;
        Result(String key, String code, long minute, String utcMinute) {
            this.key = key;
            this.code = code;
            this.minute = minute;
            this.utcMinute = utcMinute;
        }
    }
}
