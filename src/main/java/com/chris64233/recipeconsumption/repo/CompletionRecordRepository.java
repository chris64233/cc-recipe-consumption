package com.chris64233.recipeconsumption.repo;

import com.chris64233.recipeconsumption.domain.CompletionRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompletionRecordRepository extends JpaRepository<CompletionRecord, Long> {

    Optional<CompletionRecord> findByCompletionNo(String completionNo);

    boolean existsByCompletionNo(String completionNo);

    Optional<CompletionRecord> findByProductionOrder_Id(Long orderId);

    Optional<CompletionRecord> findByProductionOrder_OrderNo(String orderNo);
}
