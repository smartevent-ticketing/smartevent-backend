package com.smartevent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

@SpringBootTest
class SmartEventApplicationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void verifyMigrations() {
        System.out.println("=== FLYWAY MIGRATION HISTORY ===");
        List<Map<String, Object>> history = jdbcTemplate.queryForList(
                "SELECT installed_rank, version, description, type, script, success FROM flyway_schema_history ORDER BY installed_rank"
        );
        history.forEach(row -> System.out.println(row));

        System.out.println("\n=== DATABASE TABLES CREATED ===");
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY table_name",
                String.class
        );
        System.out.println("Total tables: " + tables.size());
        tables.forEach(table -> System.out.println(" - " + table));
    }
}

