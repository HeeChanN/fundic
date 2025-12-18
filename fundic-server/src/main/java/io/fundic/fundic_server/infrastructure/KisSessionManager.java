package io.fundic.fundic_server.infrastructure;

import io.fundic.fundic_server.infrastructure.dto.KisTokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

@Component
@RequiredArgsConstructor
public class KisSessionManager {

    private final KisApiClient authApi;
    private final KisProperties props;
    private final Clock clock = Clock.systemUTC();

    private final ReentrantLock lock = new ReentrantLock();

    private volatile TokenSession tokenSession; // access_token 캐시
    //private volatile ApprovalSession approvalSession; // (선택) ws approval_key 캐시

    /** REST 호출에 사용할 "Bearer ..." 문자열 */
    public String getAuthorizationValue() {
        TokenSession s = ensureTokenSession();
        return "Bearer " + s.accessToken();
    }

    /** access_token raw */
    public String getAccessToken() {
        return ensureTokenSession().accessToken();
    }

    /** (선택) 웹소켓 approval_key */
//    public String getApprovalKey() {
//        ApprovalSession s = ensureApprovalSession();
//        return s.approvalKey();
//    }

    private TokenSession ensureTokenSession() {
        TokenSession current = this.tokenSession;
        if (current != null && !current.isExpiringSoon(clock, props.refreshSkewSeconds())) {
            return current;
        }

        lock.lock();
        try {
            TokenSession again = this.tokenSession;
            if (again != null && !again.isExpiringSoon(clock, props.refreshSkewSeconds())) {
                return again;
            }

            KisTokenResponse res = authApi.issueAccessToken();
            Instant expiresAt = Instant.now(clock).plusSeconds(res.expiresIn());
            TokenSession created = new TokenSession(res.accessToken(), expiresAt);

            this.tokenSession = created;
            return created;
        } finally {
            lock.unlock();
        }
    }

//    private ApprovalSession ensureApprovalSession() {
//        ApprovalSession current = this.approvalSession;
//        if (current != null && !current.isExpired(clock)) {
//            return current;
//        }
//
//        lock.lock();
//        try {
//            ApprovalSession again = this.approvalSession;
//            if (again != null && !again.isExpired(clock)) {
//                return again;
//            }
//
//            KisApprovalKeyResponse res = authApi.issueApprovalKey();
//            // 문서상 유효 24시간이므로(안전하게 23:50 등으로 잡아도 됨) 여기선 24h로 설정 :contentReference[oaicite:9]{index=9}
//            Instant expiresAt = Instant.now(clock).plusSeconds(24 * 60 * 60);
//            ApprovalSession created = new ApprovalSession(res.approvalKey(), expiresAt);
//
//            this.approvalSession = created;
//            return created;
//        } finally {
//            lock.unlock();
//        }
//    }

    private record TokenSession(String accessToken, Instant expiresAt) {
        boolean isExpiringSoon(Clock clock, long skewSeconds) {
            Instant now = Instant.now(clock);
            return now.plusSeconds(skewSeconds).isAfter(expiresAt);
        }
    }

//    private record ApprovalSession(String approvalKey, Instant expiresAt) {
//        boolean isExpired(Clock clock) {
//            return Instant.now(clock).isAfter(expiresAt);
//        }
//    }
}