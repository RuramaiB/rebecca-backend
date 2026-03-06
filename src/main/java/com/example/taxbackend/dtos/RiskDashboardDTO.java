package com.example.taxbackend.dtos;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RiskDashboardDTO {
    private Integer totalArtists;
    private Integer assessedArtists;

    private Integer lowRiskCount;
    private Integer mediumRiskCount;
    private Integer highRiskCount;

    private Integer flaggedForAudit;
    private Integer pendingReview;

    private Double averageRiskScore;
    private List<RiskAnalysisResponse> highRiskArtists;

    private LocalDateTime lastAssessment;
    private LocalDateTime nextScheduledAssessment;
}
