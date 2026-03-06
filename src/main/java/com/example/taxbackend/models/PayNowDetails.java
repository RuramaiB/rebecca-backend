package com.example.taxbackend.models;


import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "pay_now_details")
public class PayNowDetails {

    @Id
    private String paymentID;
    private LocalDate dateOfPayment;
    private LocalTime timeOfPayment;
    private String email;
    private String phoneNumber;
    private Double amount;
    private String artistId;
    private String method;
    private TaxRecord.PaymentStatus paymentStatus; // Stored as String
    private String narration;
    @DBRef
    private ComplianceStatus complianceStatus;
}

