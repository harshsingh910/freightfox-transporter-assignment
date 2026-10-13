package com.freightfox.lane;

import com.freightfox.exception.ResourceNotFoundException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LaneService {

    private final LaneRepository laneRepository;

    public LaneService(LaneRepository laneRepository) {
        this.laneRepository = laneRepository;
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "lanes", key = "'all'")
    public List<LaneDto> getAllLanes() {
        return laneRepository.findAll().stream()
                .map(LaneDto::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<LaneDto> getLanesPage(Pageable pageable) {
        return laneRepository.findAll(pageable)
                .map(LaneDto::fromEntity);
    }

    @Transactional(readOnly = true)
    public LaneDto getLaneById(Long id) {
        return laneRepository.findById(id)
                .map(LaneDto::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("Lane not found with ID: " + id));
    }

    @Transactional
    @CacheEvict(value = "lanes", allEntries = true)
    public Lane findOrCreate(String externalId, String origin, String destination) {
        return laneRepository.findByExternalLaneId(externalId)
                .orElseGet(() -> laneRepository.save(new Lane(externalId, origin, destination)));
    }
}
