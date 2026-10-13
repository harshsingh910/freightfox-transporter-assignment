package com.freightfox.lane;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LaneRepository extends JpaRepository<Lane, Long> {

    Optional<Lane> findByExternalLaneId(String externalLaneId);

    boolean existsByExternalLaneId(String externalLaneId);
}
