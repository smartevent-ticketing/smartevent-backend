package com.smartevent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;

/** Migration verification owns a disposable DB, never the application's configured database. */
@Tag("postgres")
class SmartEventApplicationTests {

    @Test
    void verifyMigrations() {
        try (var database = new PostgreSQLContainer("postgres:16-alpine").withDatabaseName("migration_test")) {
            database.start();
            var source = new DriverManagerDataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword());
            var flyway = Flyway.configure().dataSource(source).locations("classpath:db/migration").load();
            assertTrue(flyway.migrate().migrationsExecuted > 0);
            flyway.validate();
            var jdbc = new JdbcTemplate(source);
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE NOT success", Integer.class));
            assertTrue(jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE'", Integer.class) > 1);
        }
    }
}

