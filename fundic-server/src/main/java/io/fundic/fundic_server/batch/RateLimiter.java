package io.fundic.fundic_server.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * API Rate Limiter
 * KIS API 호출 제한을 관리하여 안전한 데이터 수집 보장
 */
@Slf4j
@Component
public class RateLimiter {

    // 설정 가능한 제한값
    private static final int MAX_REQUESTS_PER_SECOND = 5;      // 초당 5회 (안전하게)
    private static final int MAX_REQUESTS_PER_MINUTE = 200;    // 분당 200회
    private static final int MAX_REQUESTS_PER_HOUR = 10000;    // 시간당 10,000회

    // 카운터
    private final AtomicInteger requestsThisSecond = new AtomicInteger(0);
    private final AtomicInteger requestsThisMinute = new AtomicInteger(0);
    private final AtomicInteger requestsThisHour = new AtomicInteger(0);

    // 시간 추적
    private final AtomicLong lastSecondReset = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong lastMinuteReset = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong lastHourReset = new AtomicLong(System.currentTimeMillis());

    /**
     * API 호출 전에 이 메서드를 호출하여 제한 확인 및 대기
     */
    public void acquirePermit() {
        while (true) {
            long now = System.currentTimeMillis();

            // 카운터 리셋 (시간 경과 시)
            resetCountersIfNeeded(now);

            // 제한 확인
            if (canProceed()) {
                // 카운터 증가
                requestsThisSecond.incrementAndGet();
                requestsThisMinute.incrementAndGet();
                requestsThisHour.incrementAndGet();
                return;
            }

            // 제한 초과 시 대기
            try {
                long waitTime = calculateWaitTime(now);
                if (waitTime > 0) {
                    log.debug("Rate limit reached, waiting {}ms", waitTime);
                    Thread.sleep(waitTime);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Rate limiter interrupted", e);
            }
        }
    }

    /**
     * 현재 진행 가능한지 확인
     */
    private boolean canProceed() {
        return requestsThisSecond.get() < MAX_REQUESTS_PER_SECOND &&
               requestsThisMinute.get() < MAX_REQUESTS_PER_MINUTE &&
               requestsThisHour.get() < MAX_REQUESTS_PER_HOUR;
    }

    /**
     * 대기 시간 계산
     */
    private long calculateWaitTime(long now) {
        // 초당 제한 초과 시
        if (requestsThisSecond.get() >= MAX_REQUESTS_PER_SECOND) {
            long timeSinceLastReset = now - lastSecondReset.get();
            return Math.max(0, 1000 - timeSinceLastReset);
        }

        // 분당 제한 초과 시
        if (requestsThisMinute.get() >= MAX_REQUESTS_PER_MINUTE) {
            long timeSinceLastReset = now - lastMinuteReset.get();
            return Math.max(0, 60000 - timeSinceLastReset);
        }

        // 시간당 제한 초과 시
        if (requestsThisHour.get() >= MAX_REQUESTS_PER_HOUR) {
            long timeSinceLastReset = now - lastHourReset.get();
            return Math.max(0, 3600000 - timeSinceLastReset);
        }

        return 200; // 기본 대기 시간 (초당 5회 = 200ms)
    }

    /**
     * 시간 경과에 따른 카운터 리셋
     */
    private void resetCountersIfNeeded(long now) {
        // 초 단위 리셋
        if (now - lastSecondReset.get() >= 1000) {
            requestsThisSecond.set(0);
            lastSecondReset.set(now);
        }

        // 분 단위 리셋
        if (now - lastMinuteReset.get() >= 60000) {
            requestsThisMinute.set(0);
            lastMinuteReset.set(now);
        }

        // 시간 단위 리셋
        if (now - lastHourReset.get() >= 3600000) {
            requestsThisHour.set(0);
            lastHourReset.set(now);
        }
    }

    /**
     * 현재 통계 조회
     */
    public RateLimitStats getStats() {
        return new RateLimitStats(
            requestsThisSecond.get(),
            requestsThisMinute.get(),
            requestsThisHour.get(),
            MAX_REQUESTS_PER_SECOND,
            MAX_REQUESTS_PER_MINUTE,
            MAX_REQUESTS_PER_HOUR
        );
    }

    /**
     * 통계 정보 레코드
     */
    public record RateLimitStats(
        int currentSecond,
        int currentMinute,
        int currentHour,
        int maxPerSecond,
        int maxPerMinute,
        int maxPerHour
    ) {
        public String toString() {
            return String.format(
                "Rate Limit Stats - Second: %d/%d, Minute: %d/%d, Hour: %d/%d",
                currentSecond, maxPerSecond,
                currentMinute, maxPerMinute,
                currentHour, maxPerHour
            );
        }
    }
}
