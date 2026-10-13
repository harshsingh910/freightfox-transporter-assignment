package com.freightfox.transporter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TransporterRepository extends JpaRepository<Transporter, Long> {

    Optional<Transporter> findByExternalTransporterId(String externalTransporterId);

    boolean existsByExternalTransporterId(String externalTransporterId);
}
