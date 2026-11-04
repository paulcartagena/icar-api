package com.paulcartagena.icarapi.donation.paypal;

import lombok.Getter;

// PayPal failed or rejected a call. status/issue are set when PayPal answered with an error (e.g. 422 INSTRUMENT_DECLINED).
@Getter
public class PayPalException extends RuntimeException {

    private final Integer status;
    private final String issue;

    public PayPalException(String message) {
        this(message, null, null);
    }

    public PayPalException(String message, Integer status, String issue) {
        super(message);
        this.status = status;
        this.issue = issue;
    }

    // The request reached PayPal and it refused it (4xx), e.g. a declined card or an order not approved yet
    public boolean rejected() {
        return status != null && status >= 400 && status < 500;
    }
}
