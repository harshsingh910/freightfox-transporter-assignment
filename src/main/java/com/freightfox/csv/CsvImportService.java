package com.freightfox.csv;

import com.freightfox.assignment.optimizer.OptimizationLane;
import com.freightfox.assignment.optimizer.OptimizationQuote;
import com.freightfox.assignment.optimizer.OptimizationTransporter;
import com.freightfox.lane.Lane;
import com.freightfox.lane.LaneRepository;
import com.freightfox.quote.TransporterQuote;
import com.freightfox.quote.TransporterQuoteRepository;
import com.freightfox.transporter.Transporter;
import com.freightfox.transporter.TransporterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Transactional CSV import service for persisting trade lanes, transporters, and quotes.
 */
@Service
public class CsvImportService {

    private static final Logger log = LoggerFactory.getLogger(CsvImportService.class);

    private final CsvParserService csvParserService;
    private final LaneRepository laneRepository;
    private final TransporterRepository transporterRepository;
    private final TransporterQuoteRepository quoteRepository;

    public CsvImportService(
            CsvParserService csvParserService,
            LaneRepository laneRepository,
            TransporterRepository transporterRepository,
            TransporterQuoteRepository quoteRepository
    ) {
        this.csvParserService = csvParserService;
        this.laneRepository = laneRepository;
        this.transporterRepository = transporterRepository;
        this.quoteRepository = quoteRepository;
    }

    @Transactional
    @CacheEvict(value = {"lanes", "transporters", "assignments"}, allEntries = true)
    public CsvImportSummary importCsv(InputStream inputStream, boolean strictMissingQuotes) {
        CsvParseResult parseResult = csvParserService.parse(inputStream, strictMissingQuotes);

        if (!parseResult.valid()) {
            log.warn("CSV validation failed with {} errors", parseResult.errors().size());
            return CsvImportSummary.failure(
                    parseResult.errors(),
                    parseResult.totalRowsProcessed(),
                    "CSV validation failed. No data was persisted."
            );
        }

        // 1. Upsert Lanes
        Map<String, Lane> persistedLanes = new HashMap<>();
        for (OptimizationLane ol : parseResult.lanes()) {
            Lane lane = laneRepository.findByExternalLaneId(ol.id())
                    .orElseGet(() -> new Lane(ol.id(), ol.origin(), ol.destination()));
            lane.setOrigin(ol.origin());
            lane.setDestination(ol.destination());
            persistedLanes.put(ol.id(), laneRepository.save(lane));
        }

        // 2. Upsert Transporters
        Map<String, Transporter> persistedTransporters = new HashMap<>();
        for (OptimizationTransporter ot : parseResult.transporters()) {
            Transporter transporter = transporterRepository.findByExternalTransporterId(ot.id())
                    .orElseGet(() -> new Transporter(ot.id(), ot.name()));
            transporter.setName(ot.name());
            persistedTransporters.put(ot.id(), transporterRepository.save(transporter));
        }

        // 3. Upsert Quotes
        int quoteCount = 0;
        for (OptimizationQuote oq : parseResult.quotes()) {
            Lane lane = persistedLanes.get(oq.laneId());
            Transporter transporter = persistedTransporters.get(oq.transporterId());

            if (lane != null && transporter != null) {
                TransporterQuote quote = quoteRepository.findByLaneAndTransporter(lane, transporter)
                        .orElseGet(() -> new TransporterQuote(lane, transporter, oq.quoteAmount()));
                quote.setQuoteAmount(oq.quoteAmount());
                quoteRepository.save(quote);
                quoteCount++;
            }
        }

        log.info("CSV import completed: {} lanes, {} transporters, {} quotes persisted.",
                persistedLanes.size(), persistedTransporters.size(), quoteCount);

        return CsvImportSummary.success(
                persistedLanes.size(),
                persistedTransporters.size(),
                quoteCount,
                parseResult.totalRowsProcessed(),
                parseResult.warnings(),
                "Successfully imported CSV dataset."
        );
    }
}
