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
 * TaxRecord Entity - Stores calculated tax information per period
 *
 * Academic Note: This represents the core tax calculation result
 * for each artist per tax period (monthly/quarterly/annually)
 */
@Document(collection = "tax_records")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@CompoundIndexes({
        @CompoundIndex(name = "artist_period_idx", def = "{'artistId': 1, 'period': 1}"),
        @CompoundIndex(name = "payment_status_idx", def = "{'paymentStatus': 1, 'dueDate': 1}")
})
public class TaxRecord {
    @Id
    private String id;

    @Indexed
    private String artistId;

    // Revenue breakdown
    private Double grossRevenue; // Total revenue before tax
    private Double youtubeRevenue; // From YouTube Analytics
    private Double adsenseRevenue; // From AdSense (if available)
    private Double otherRevenue; // From other platforms (future)

    // Tax calculation
    private Double taxableIncome; // After deductions
    private Double taxRate; // Percentage (e.g., 0.10 for 10%)
    private Double taxAmount; // Calculated tax
    private Double netRevenue; // After tax

    // Content metrics for the period
    private int videoCount; // Number of videos uploaded in this period
    private int shotCount; // Number of shorts uploaded in this period

    // Deductions (if applicable)
    private Double deductions;
    private String deductionDetails; // JSON string of deduction breakdown

    // Period information
    private String period; // Format: "YYYY-MM" for monthly
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private String taxYear; // "2024"
    private String taxQuarter; // "Q1", "Q2", "Q3", "Q4"

    // Calculation metadata
    private LocalDateTime calculatedAt;
    private String calculatedBy; // "SYSTEM" or admin user ID
    private String calculationMethod; // "AUTOMATIC" or "MANUAL"

    // Payment tracking
    private PaymentStatus paymentStatus;
    private LocalDateTime dueDate;
    private LocalDateTime paidDate;

    private String paymentReference;
    private String paymentMethod;

    // Compliance
    private boolean filed; // Tax return filed
    private LocalDateTime filedDate;
    private String filingReference;

    // Audit trail
    private String notes;
    private List<String> attachments; // Document IDs for receipts, etc.
    private LocalDateTime lastModified;
    private String taxType;

    public enum PaymentStatus {
        PENDING, // Tax calculated, not yet paid
        PAID, // Tax paid in full
        PARTIAL, // Partially paid
        OVERDUE, // Past due date
        WAIVED, // Tax waived/exempted
        DISPUTED // Under dispute/review
    }

    public enum TaxType {
        STANDARD, // Regular digital services tax
        OPERATION_TAX, // Nominal tax for accounts with videos but low revenue
        EXEMPT, // Tax exempt accounts
        PENALTY // Penalty tax for non-compliance
    }
}