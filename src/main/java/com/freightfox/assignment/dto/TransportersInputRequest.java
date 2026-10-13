package com.freightfox.assignment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record TransportersInputRequest(
        @NotEmpty(message = "Lanes list must not be empty")
        @Valid
        List<InputLaneDto> lanes,

        @NotEmpty(message = "Transporters list must not be empty")
        @Valid
        List<InputTransporterDto> transporters
) {
}
