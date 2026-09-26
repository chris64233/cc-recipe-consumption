package com.chris64233.recipeconsumption.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderCompletionRepository extends JpaRepository<OrderCompletion, Long> {
    Optional<OrderCompletion> findByOrderId(Long orderId);
}
