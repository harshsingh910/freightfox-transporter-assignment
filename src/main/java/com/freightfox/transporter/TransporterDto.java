package com.freightfox.transporter;

import java.time.Instant;

public record TransporterDto(
        Long id,
        String externalTransporterId,
        String name,
        Instant createdAt
) {
    public static TransporterDto fromEntity(Transporter transporter) {
        return new TransporterDto(
                transporter.getId(),
                transporter.getExternalTransporterId(),
                transporter.getName(),
                transporter.getCreatedAt()
        );
    }
}
