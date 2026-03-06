package com.example.taxbackend.repository;
import com.example.taxbackend.models.ComplianceStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ComplianceStatusRepository extends MongoRepository<ComplianceStatus, String> {

    Optional<ComplianceStatus> findByArtistId(String artistId);

    // Find non-compliant artists
    List<ComplianceStatus> findByTaxCompliantFalse();

    List<ComplianceStatus> findByComplianceLevel(
            ComplianceStatus.ComplianceLevel level
    );

    // Find artists with overdue payments
    List<ComplianceStatus> findByHasOverduePaymentsTrue();

    // Find artists needing attention
    List<ComplianceStatus> findByNeedsAttentionTrue();

    // Find by outstanding amount
    List<ComplianceStatus> findByOutstandingTaxGreaterThan(Double amount);

    // Find by last calculation date
    List<ComplianceStatus> findByLastTaxCalculationBefore(LocalDateTime date);

    // Find unregistered artists
    List<ComplianceStatus> findByRegisteredForDigitalTaxFalse();

    // Count compliant artists
    long countByTaxCompliantTrue();

    long countByTaxCompliantFalse();

    // Count by compliance level
    long countByComplianceLevel(ComplianceStatus.ComplianceLevel level);

    // Delete by artist
    void deleteByArtistId(String artistId);
}
