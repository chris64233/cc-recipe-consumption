package com.chris64233.recipeconsumption;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 每个测试方法前清空全部业务表。 */
@Component
public class DatabaseCleaner {

    private static final String[] TABLES = {
            "issue_line_batch",
            "issue_line",
            "issue_record",
            "return_record",
            "adjustment_record",
            "order_completion_loss",
            "order_completion",
            "production_order",
            "material_batch",
            "recipe_substitution",
            "recipe_item",
            "recipe_version",
    };

    private final JdbcTemplate jdbcTemplate;

    public DatabaseCleaner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void clean() {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        try {
            for (String table : TABLES) {
                jdbcTemplate.execute("DELETE FROM " + table);
            }
        } finally {
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }
}
