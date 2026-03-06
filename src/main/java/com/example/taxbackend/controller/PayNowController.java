package com.example.taxbackend.controller;


import com.example.taxbackend.dtos.PayNowRequest;
import com.example.taxbackend.models.PayNowDetails;
import com.example.taxbackend.repository.PaymentRepository;
import com.example.taxbackend.service.PayNowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/paynow")
@RequiredArgsConstructor
public class PayNowController {

    private final PayNowService payNowService;



    @PostMapping("/makePayment")
    public String makePayment(@RequestBody PayNowRequest payNowRequest){
        return payNowService.makePayment(payNowRequest);
    }

    @GetMapping("/getAllPayments")
    public List<PayNowDetails> getAllPayments(){
        return payNowService.findAllPayments();
    }
    @GetMapping("/get-all-payments-by-artist/{artistId}")
    public List<PayNowDetails> getAllPaymentsByArtist(@PathVariable String artistId) {
        return payNowService.findAllPaymentsByArtistId(artistId);
    }
}
