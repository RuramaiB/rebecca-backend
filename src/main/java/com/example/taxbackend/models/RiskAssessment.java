package com.example.taxbackend.models;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;

import java.time.LocalDateTime;
import java.util.List;

/**
 * RiskAssessment Entity - Stores AI-generated compliance risk analysis
 *
 * Academic Note: This stores results from Python ML risk engine
 * for audit trail and compliance monitoring
 */
@Document(collection = "risk_assessments")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@CompoundIndexes({
        @CompoundIndex(name = "artist_assessed_idx", def = "{'artistId': 1, 'assessedAt': -1}")
})
public class RiskAssessment {

    @Id
    private String id;

    @Indexed
    private String artistId;

    // Risk Scores
    private Double riskScore;              // Final combined score (0.0 - 1.0)
    private String riskLevel;              // LOW, MEDIUM, HIGH
    private Double mlAnomalyScore;         // ML-based anomaly score
    private Double ruleBasedScore;         // Rule-based risk score

    // Risk Indicators
    private List<String> indicators;       // Explainable risk factors

    // Assessment Metadata
    private LocalDateTime assessedAt;
    private String analysisVersion;        // Version of Python script
    private Integer artistsAnalyzed;       // Number of artists in comparison pool

    // Follow-up Actions
    private boolean flaggedForAudit;       // Requires manual review
    private boolean reviewed;              // Has been reviewed by admin
    private LocalDateTime reviewedAt;
    private String reviewedBy;
    private String reviewNotes;

    // Historical Tracking
    private String previousRiskLevel;      // Previous assessment's risk level
    private Double riskTrend;              // Change in risk score

    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
}