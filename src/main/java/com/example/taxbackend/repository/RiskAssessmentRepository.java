package com.example.taxbackend.repository;

import com.example.taxbackend.models.RiskAssessment;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * RiskAssessment Repository
 * Data access for compliance risk assessments
 */
@Repository
public interface RiskAssessmentRepository extends MongoRepository<RiskAssessment, String> {

    // Find latest assessment for artist
    Optional<RiskAssessment> findFirstByArtistIdOrderByAssessedAtDesc(String artistId);

    // Find all assessments for artist
    List<RiskAssessment> findByArtistIdOrderByAssessedAtDesc(String artistId);

    // Find by risk level
    List<RiskAssessment> findByRiskLevel(String riskLevel);

    List<RiskAssessment> findByRiskLevelOrderByRiskScoreDesc(String riskLevel);

    // Find high-risk artists
    @Query("{ 'riskLevel': { $in: ['HIGH', 'CRITICAL'] } }")
    List<RiskAssessment> findHighRiskAssessments();

    // Find flagged for audit
    List<RiskAssessment> findByFlaggedForAuditTrue();

    List<RiskAssessment> findByFlaggedForAuditTrueAndReviewedFalse();

    // Find by date range
    List<RiskAssessment> findByAssessedAtBetween(LocalDateTime start, LocalDateTime end);

    // Find artists needing reassessment
    @Query("{ 'assessedAt': { $lt: ?0 } }")
    List<RiskAssessment> findAssessmentsOlderThan(LocalDateTime date);

    // Count by risk level
    long countByRiskLevel(String riskLevel);

    // Find latest assessments (one per artist)
    @Query(value = "{}", sort = "{ 'assessedAt': -1 }")
    List<RiskAssessment> findLatestAssessments();

    // Delete old assessments
    void deleteByAssessedAtBefore(LocalDateTime date);
}
