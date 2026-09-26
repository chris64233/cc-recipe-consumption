package com.chris64233.recipeconsumption.repo;

import com.chris64233.recipeconsumption.domain.MaterialIssueBatchAllocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaterialIssueBatchAllocationRepository extends JpaRepository<MaterialIssueBatchAllocation, Long> {

    List<MaterialIssueBatchAllocation> findByBatchIdOrderByIdAsc(Long batchId);
}
