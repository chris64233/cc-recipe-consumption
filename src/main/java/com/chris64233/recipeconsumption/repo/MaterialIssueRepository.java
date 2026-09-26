package com.chris64233.recipeconsumption.repo;

import com.chris64233.recipeconsumption.domain.MaterialIssue;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MaterialIssueRepository extends JpaRepository<MaterialIssue, Long> {

    Optional<MaterialIssue> findByIssueNo(String issueNo);

    @EntityGraph(attributePaths = {"lines", "lines.allocations", "productionOrder"})
    Optional<MaterialIssue> findWithDetailsByIssueNo(String issueNo);

    boolean existsByIssueNo(String issueNo);
}
