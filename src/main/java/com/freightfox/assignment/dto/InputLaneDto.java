package com.freightfox.assignment.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InputLaneDto(
        @NotNull(message = "Lane ID is required")
        Object id,

        @NotBlank(message = "Origin must not be blank")
        String origin,

        @NotBlank(message = "Destination must not be blank")
        String destination
) {
    public String getExternalId() {
        return String.valueOf(id);
    }
}
