package io.fundic.fundic_server.presentation.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 포트폴리오 추천 요청 DTO
 * 사용자가 선택한 섹터 ID 3개와 프로필 정보를 받음
 */
@Getter
@Setter
@NoArgsConstructor
public class PortfolioRecommendRequest {

    // 사용자 프로필
    private UserProfileRequest.Horizon horizon;
    private UserProfileRequest.Risk risk;
    private UserProfileRequest.Goal goal;

    // 사용자가 선택한 섹터 ID
    private String leaderSectorId;
    private String supportSectorId;
    private String bufferSectorId;

    /**
     * UserProfileRequest로 변환
     */
    public UserProfileRequest toUserProfileRequest() {
        UserProfileRequest userProfile = new UserProfileRequest();
        userProfile.setHorizon(this.horizon);
        userProfile.setRisk(this.risk);
        userProfile.setGoal(this.goal);
        return userProfile;
    }
}
