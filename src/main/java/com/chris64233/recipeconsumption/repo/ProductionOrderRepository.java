package com.chris64233.recipeconsumption.repo;

import com.chris64233.recipeconsumption.domain.ProductionOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductionOrderRepository extends JpaRepository<ProductionOrder, Long> {

    Optional<ProductionOrder> findByOrderNo(String orderNo);

    /** 只读详情：连同不可变配方版本及其配方一起加载。 */
    @EntityGraph(attributePaths = {"recipeVersion", "recipeVersion.recipe"})
    Optional<ProductionOrder> findWithDetailsByOrderNo(String orderNo);

    boolean existsByOrderNo(String orderNo);

    /**
     * 悲观锁加载工单，串行化同一工单上的领料/退料/调整/完工操作；
     * 已完工工单在锁内再次校验状态。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from ProductionOrder o where o.orderNo = :orderNo")
    Optional<ProductionOrder> findForUpdateByOrderNo(@Param("orderNo") String orderNo);
}
