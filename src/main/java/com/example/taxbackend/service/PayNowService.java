package com.example.taxbackend.service;




import com.example.taxbackend.dtos.PayNowRequest;
import com.example.taxbackend.models.Artist;
import com.example.taxbackend.models.ComplianceStatus;
import com.example.taxbackend.models.PayNowDetails;
import com.example.taxbackend.models.TaxRecord;
import com.example.taxbackend.repository.ArtistRepository;
import com.example.taxbackend.repository.ComplianceStatusRepository;
import com.example.taxbackend.repository.PaymentRepository;
import com.example.taxbackend.repository.TaxRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import zw.co.paynow.constants.MobileMoneyMethod;
import zw.co.paynow.core.Payment;
import zw.co.paynow.core.Paynow;
import zw.co.paynow.responses.MobileInitResponse;
import zw.co.paynow.responses.StatusResponse;


import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
@RequiredArgsConstructor
public class PayNowService {

    @Autowired
    private PaymentRepository paymentRepository;

    @Value("${payment.paynowID}")
    private String payNowID;
    @Value("${payment.paynowKey}")
    private String payNowKey;
    private final ArtistRepository artistRepository;
    private final ComplianceStatusRepository complianceStatusRepository;
    public String makePayment(PayNowRequest payNowRequest) {
        Artist artist = artistRepository.findByEmail(payNowRequest.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Artist with email " + payNowRequest.getEmail() + " not found."));
        PayNowDetails payNowDetails = new PayNowDetails();
        if (payNowRequest.getComplianceID() == null || payNowRequest.getComplianceID().isBlank()) {
            throw new IllegalArgumentException("Compliance ID is required");
        }
        ComplianceStatus complianceStatus = complianceStatusRepository.findById(payNowRequest.getComplianceID())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Compliance record with ID " + payNowRequest.getComplianceID() + " not found."));
        if (!Objects.equals(complianceStatus.getArtistId(), artist.getId())) {
            throw new IllegalArgumentException("Compliance ID does not belong to this artist.");
        }
        payNowDetails.setComplianceStatus(complianceStatus);
        payNowDetails.setNarration(payNowRequest.getNarration());
        payNowDetails.setPaymentStatus(TaxRecord.PaymentStatus.PENDING);
        payNowDetails.setAmount(payNowRequest.getAmount());
        payNowDetails.setArtistId(artist.getId());
        payNowDetails.setMethod(payNowRequest.getMethod());
        payNowDetails.setEmail(payNowRequest.getEmail());
        payNowDetails.setPhoneNumber(payNowRequest.getPhoneNumber());
        payNowDetails.setDateOfPayment(LocalDate.now());
        payNowDetails.setTimeOfPayment(LocalTime.now());
        PayNowDetails paymentDetails1 = paymentRepository.save(payNowDetails);

        Paynow paynow = new Paynow(payNowID, payNowKey);
        Payment payment = paynow.createPayment("Tax Compliance Fee", paymentDetails1.getEmail());
        payment.add(paymentDetails1.getArtistId(), paymentDetails1.getAmount());
        MobileInitResponse response = paynow.sendMobile(payment, paymentDetails1.getPhoneNumber(), MobileMoneyMethod.valueOf(paymentDetails1.getMethod()));
        if (response.success()) {
            // Get the instructions to show to the user
            String instructions  = response.instructions();
            // Get the poll URL of the transaction
            String pollUrl = response.pollUrl();
            StatusResponse status = paynow.pollTransaction(pollUrl);
            if (status.paid()) {
                return pollUrl;
            }
//            else {
//                    PaymentDetails paymentRecord = paymentRepository.findPaymentByStudentNumber(paymentDetails1.getStudentNumber());
////                            .orElseThrow(() -> new ResourceNotFoundException(("Payment was not found.")));
//                    paymentRepository.delete(paymentRecord);
//                }

            return instructions;
        } else {
            throw new RuntimeException("Payment failed");
        }
    }

    public List<PayNowDetails> findAllPayments() {
        return paymentRepository.findAll();
    }

    public List<PayNowDetails> findAllPaymentsByArtistId(String artistId) {
        return paymentRepository.findByArtistId(artistId);
    }


}