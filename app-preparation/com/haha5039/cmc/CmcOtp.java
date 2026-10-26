package com.haha5039.cmc;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Locale;

/** Matches the locally displayed CMC development challenge in 9.1.00.15. */
public final class CmcOtp {
    private CmcOtp() {}
    public static String response(String challenge, long epochMillis) {
        if (challenge == null || !challenge.matches("[a-z0-9]{5}"))
            throw new IllegalArgumentException("CMC 화면의 5자리 KEY가 필요합니다.");
        ZonedDateTime now = Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC);
        String input = challenge + String.format(Locale.ROOT, "%02d%02d%02d%02d%02d",
                now.getYear() - 2000, now.getMonthValue(), now.getMinute(),
                now.getDayOfMonth(), now.getHour());
        int hash = 0;
        for (int i = 0; i < input.length(); i++) hash = input.charAt(i) + (hash << 5) + hash;
        return Integer.toString(hash < 0 ? -hash : hash);
    }
}
