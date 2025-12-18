package io.fundic.fundic_server.presentation;

import io.fundic.fundic_server.infrastructure.KisSessionManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class KisAuthDebugController {

    private final KisSessionManager sessionManager;

    public KisAuthDebugController(KisSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @GetMapping("/debug/kis/token")
    public String token() {
        return sessionManager.getAccessToken();
    }

//    @GetMapping("/debug/kis/approval-key")
//    public String approvalKey() {
//        return sessionManager.getApprovalKey();
//    }
}
