package com.freightfox.lane;

import java.time.Instant;

public record LaneDto(
        Long id,
        String externalLaneId,
        String origin,
        String destination,
        Instant createdAt
) {
    public static LaneDto fromEntity(Lane lane) {
        return new LaneDto(
                lane.getId(),
                lane.getExternalLaneId(),
                lane.getOrigin(),
                lane.getDestination(),
                lane.getCreatedAt()
        );
    }
}
