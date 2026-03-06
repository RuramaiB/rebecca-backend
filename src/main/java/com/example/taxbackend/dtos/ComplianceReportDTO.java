package com.example.taxbackend.dtos;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ComplianceReportDTO {
    private String artistId;
    private String artistName;
    private String email;

    // Status
    private boolean taxCompliant;
    private String complianceLevel;
    private String complianceNotes;

    // Financial
    private Double totalRevenueToDate;
    private Double totalTaxPaid;
    private Double outstandingTax;

    // Tracking
    private LocalDateTime lastTaxCalculation;
    private LocalDateTime lastTaxPayment;
    private LocalDateTime nextTaxDue;

    private String lastFiledPeriod;
    private int consecutiveCompliantMonths;
    private int missedPayments;

    // Alerts
    private List<AlertDTO> alerts;
    private boolean hasOverduePayments;
    private boolean needsAttention;

    // Registration
    private String taxIdentificationNumber;
    private LocalDateTime registeredDate;
    private boolean registeredForDigitalTax;
}
