package com.chris64233.recipeconsumption.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IssueRecordRepository extends JpaRepository<IssueRecord, Long> {

    Optional<IssueRecord> findByBizNo(String bizNo);

    List<IssueRecord> findByOrderIdOrderByCreatedAtAsc(Long orderId);
}
