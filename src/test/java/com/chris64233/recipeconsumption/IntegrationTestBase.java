package com.chris64233.recipeconsumption;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** 集成测试基类：每个用例前清库，保证业务编码/业务号可重复使用。 */
@SpringBootTest
public abstract class IntegrationTestBase {

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @BeforeEach
    void cleanDatabase() {
        databaseCleaner.clean();
    }
}
