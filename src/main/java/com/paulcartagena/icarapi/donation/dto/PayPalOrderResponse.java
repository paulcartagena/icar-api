package com.paulcartagena.icarapi.donation.dto;

// The JS SDK's createOrder callback returns this id to PayPal
public record PayPalOrderResponse(String orderId) {
}
