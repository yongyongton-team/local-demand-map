package com.yongyongton.localdemand.common;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * 명세 공통 규칙: 시각은 ISO 8601 한국 시간(예: 2026-11-08T10:00:00+09:00).
 * DB의 LocalDateTime(한국 시간으로 저장)을 응답용 OffsetDateTime으로 바꿔요. 초 아래는 버려요.
 */
public final class KstTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private KstTime() {
    }

    public static OffsetDateTime of(LocalDateTime time) {
        return time == null ? null : time.truncatedTo(ChronoUnit.SECONDS).atZone(ZONE).toOffsetDateTime();
    }
}
