package com.freightfox.auth;

import java.time.Instant;

public record UserDto(
        Long id,
        String username,
        String email,
        String role,
        boolean enabled,
        Instant createdAt
) {
    public static UserDto fromEntity(UserEntity entity) {
        return new UserDto(
                entity.getId(),
                entity.getUsername(),
                entity.getEmail(),
                entity.getRole().name(),
                entity.isEnabled(),
                entity.getCreatedAt()
        );
    }
}
