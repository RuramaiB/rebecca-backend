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
@Document(collection = "tax_configuration")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TaxConfiguration {
    @Id
    private String id;

    private String country;             // "Zimbabwe"
    private String taxType;             // "DIGITAL_SERVICES_TAX"

    // Tax rates
    private Double standardRate;        // 10% = 0.10
    private Double reducedRate;         // For small earners
    private Double thresholdAmount;     // Minimum revenue for taxation
    private Double operationTaxRate; // Operation tax rate for low-revenue accounts (e.g., 0.03 for 3%)

    private Double operationTaxThreshold;

    // Deductions
    private boolean allowDeductions;
    private Double standardDeduction;   // Fixed amount
    private Double deductionPercentage; // % of revenue
    private List<String> allowedDeductionTypes;

    // Payment terms
    private int paymentDueDays;         // Days after period end
    private boolean quarterlyFiling;
    private boolean monthlyFiling;

    // Penalties
    private Double latePenaltyRate;     // Per month
    private Double interestRate;        // Annual interest on overdue

    // Effective dates
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;

    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
