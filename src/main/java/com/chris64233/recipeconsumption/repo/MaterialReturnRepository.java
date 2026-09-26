package com.chris64233.recipeconsumption.repo;

import com.chris64233.recipeconsumption.domain.MaterialReturn;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MaterialReturnRepository extends JpaRepository<MaterialReturn, Long> {

    Optional<MaterialReturn> findByReturnNo(String returnNo);

    @EntityGraph(attributePaths = {"lines", "lines.issueLine", "materialIssue", "productionOrder"})
    Optional<MaterialReturn> findWithDetailsByReturnNo(String returnNo);

    boolean existsByReturnNo(String returnNo);
}
