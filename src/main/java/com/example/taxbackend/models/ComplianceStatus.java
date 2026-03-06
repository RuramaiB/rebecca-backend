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

@Document(collection = "compliance_status")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ComplianceStatus {
    @Id
    private String id;

    @Indexed(unique = true)
    private String artistId;

    // Overall compliance
    private boolean taxCompliant;
    private ComplianceLevel complianceLevel;
    private String complianceNotes;

    // Financial summary
    private Double totalRevenueToDate; // Lifetime revenue
    private Double totalTaxPaid; // Lifetime tax paid
    private Double outstandingTax; // Current outstanding amount
    private Double currentYearRevenue; // This year's revenue
    private Double currentYearTax; // This year's tax

    // Tracking dates
    private LocalDateTime lastTaxCalculation;
    private LocalDateTime lastTaxPayment;
    private LocalDateTime nextTaxDue;

    // Period tracking
    private String lastFiledPeriod; // "2024-12"
    private int consecutiveCompliantMonths;
    private int missedPayments;

    // Warnings & alerts
    private List<ComplianceAlert> alerts;
    private boolean hasOverduePayments;
    private boolean needsAttention;

    // Registration info
    private String taxIdentificationNumber; // TIN or equivalent
    private LocalDateTime registeredDate;
    private boolean registeredForDigitalTax;

    // Content summary
    private int totalVideosToDate;
    private int totalShotsToDate;

    // Audit info
    private LocalDateTime lastAuditDate;
    private String lastAuditResult;

    private LocalDateTime updatedAt;

    public enum ComplianceLevel {
        EXCELLENT, // Always on time, no issues
        GOOD, // Mostly compliant, minor delays
        FAIR, // Some delays or missed payments
        POOR, // Multiple missed payments
        CRITICAL // Serious non-compliance
    }
}