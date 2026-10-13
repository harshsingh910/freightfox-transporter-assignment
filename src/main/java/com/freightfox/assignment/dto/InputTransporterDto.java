package com.freightfox.assignment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record InputTransporterDto(
        @NotNull(message = "Transporter ID is required")
        Object id,

        @NotBlank(message = "Transporter name must not be blank")
        String name,

        @NotNull(message = "Lane quotes list must not be null")
        @Valid
        List<InputLaneQuoteDto> laneQuotes
) {
    public String getExternalId() {
        return String.valueOf(id);
    }
}
