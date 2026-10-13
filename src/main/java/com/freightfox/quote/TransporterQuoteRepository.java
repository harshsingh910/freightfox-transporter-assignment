package com.freightfox.quote;

import com.freightfox.lane.Lane;
import com.freightfox.transporter.Transporter;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransporterQuoteRepository extends JpaRepository<TransporterQuote, Long> {

    Optional<TransporterQuote> findByLaneAndTransporter(Lane lane, Transporter transporter);

    @Query("SELECT q FROM TransporterQuote q JOIN FETCH q.lane JOIN FETCH q.transporter")
    List<TransporterQuote> findAllWithDetails();

    long deleteByLane(Lane lane);

    long deleteByTransporter(Transporter transporter);
}
