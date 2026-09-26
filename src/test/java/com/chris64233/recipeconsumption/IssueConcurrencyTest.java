package com.chris64233.recipeconsumption;

import com.chris64233.recipeconsumption.dto.IssueRequest;
import com.chris64233.recipeconsumption.service.IssueService;
import com.chris64233.recipeconsumption.support.QuantityBalanceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 并发领料：两个不同工单争抢同一批次，库存只够一笔时必须恰好一笔成功、
 * 一笔因不足回滚，绝不出现负库存或两笔都成功。
 */
class IssueConcurrencyTest extends AbstractServiceTest {

    @Autowired private IssueService issueService;

    @BeforeEach
    void setUp() {
        createStandardRecipe();
        createOrder("WO-A", "100");
        createOrder("WO-B", "100");
        // 同一批主料 A 只有 60，两笔各领 60。
        createBatch("A-SHARED", MATERIAL_A, "60");
    }

    @Test
    void concurrentIssuesNeverProduceNegativeStock() throws Exception {
        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger insufficient = new AtomicInteger();

        try {
            List<Future<?>> futures = List.of(
                    pool.submit(() -> runIssue("WO-A", "IS-A", ready, start, success, insufficient)),
                    pool.submit(() -> runIssue("WO-B", "IS-B", ready, start, success, insufficient)));
            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(success.get()).isEqualTo(1);
        assertThat(insufficient.get()).isEqualTo(1);
        assertThat(masterDataService.getBatch("A-SHARED").getAvailableQuantity())
                .isEqualByComparingTo("0");
        assertThat(issueRepository.count()).isEqualTo(1);
    }

    private void runIssue(String orderNo, String issueNo, CountDownLatch ready, CountDownLatch start,
                          AtomicInteger success, AtomicInteger insufficient) {
        try {
            ready.countDown();
            start.await(30, TimeUnit.SECONDS);
            issueService.issue(orderNo, new IssueRequest(issueNo, null,
                    List.of(new IssueRequest.Line(MATERIAL_A, null, new BigDecimal("60")))));
            success.incrementAndGet();
        } catch (QuantityBalanceException e) {
            insufficient.incrementAndGet();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
