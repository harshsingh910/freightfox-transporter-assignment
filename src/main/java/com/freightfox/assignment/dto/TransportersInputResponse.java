package com.freightfox.assignment.dto;

public record TransportersInputResponse(
        String status,
        String message
) {
    public static TransportersInputResponse success() {
        return new TransportersInputResponse("success", "Input data saved successfully.");
    }
}
