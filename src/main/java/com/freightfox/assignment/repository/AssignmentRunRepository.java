package com.freightfox.assignment.repository;

import com.freightfox.assignment.entity.AssignmentRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AssignmentRunRepository extends JpaRepository<AssignmentRun, Long> {

    @EntityGraph(attributePaths = {"laneAssignments", "laneAssignments.lane", "laneAssignments.transporter"})
    Optional<AssignmentRun> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"laneAssignments", "laneAssignments.lane", "laneAssignments.transporter"})
    @Query("SELECT DISTINCT r FROM AssignmentRun r WHERE r.id IN :ids")
    List<AssignmentRun> findAllWithDetailsByIdIn(@Param("ids") Collection<Long> ids);

    Page<AssignmentRun> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
