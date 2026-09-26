package com.chris64233.recipeconsumption.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductionOrderRepository extends JpaRepository<ProductionOrder, Long> {

    Optional<ProductionOrder> findByOrderNo(String orderNo);

    /** 悲观锁加载工单，串行化同一工单上的并发领料/退料/调整 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from ProductionOrder o where o.id = :id")
    Optional<ProductionOrder> findByIdForUpdate(@Param("id") Long id);

    /** 只取 id 的标量查询，避免把实体读入持久化上下文后再加锁 */
    @Query("select o.id from ProductionOrder o where o.orderNo = :orderNo")
    Optional<Long> findIdByOrderNo(@Param("orderNo") String orderNo);
}
