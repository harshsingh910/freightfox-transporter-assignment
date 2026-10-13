package com.freightfox.transporter;

import com.freightfox.exception.ResourceNotFoundException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TransporterService {

    private final TransporterRepository transporterRepository;

    public TransporterService(TransporterRepository transporterRepository) {
        this.transporterRepository = transporterRepository;
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "transporters", key = "'all'")
    public List<TransporterDto> getAllTransporters() {
        return transporterRepository.findAll().stream()
                .map(TransporterDto::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<TransporterDto> getTransportersPage(Pageable pageable) {
        return transporterRepository.findAll(pageable)
                .map(TransporterDto::fromEntity);
    }

    @Transactional(readOnly = true)
    public TransporterDto getTransporterById(Long id) {
        return transporterRepository.findById(id)
                .map(TransporterDto::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("Transporter not found with ID: " + id));
    }

    @Transactional
    @CacheEvict(value = "transporters", allEntries = true)
    public Transporter findOrCreate(String externalId, String name) {
        return transporterRepository.findByExternalTransporterId(externalId)
                .orElseGet(() -> transporterRepository.save(new Transporter(externalId, name)));
    }
}
