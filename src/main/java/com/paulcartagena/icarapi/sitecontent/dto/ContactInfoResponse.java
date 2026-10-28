package com.paulcartagena.icarapi.sitecontent.dto;

public record ContactInfoResponse(
        String address,
        String phone,
        String email,
        String mapUrl
) {
}
