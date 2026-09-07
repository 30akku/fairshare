package com.anshi.fairshare;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class DatabaseMigrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("fairshare_test")
            .withUsername("fairshare")
            .withPassword("fairshare");

    @Test
    void flywayCreatesTheCompleteLedgerSchema() throws Exception {
        var result = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").load().migrate();
        assertThat(result.migrationsExecuted).isEqualTo(1);

        Set<String> tables = new TreeSet<>();
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.prepareStatement("select table_name from information_schema.tables where table_schema = 'public'");
             var rows = statement.executeQuery()) {
            while (rows.next()) tables.add(rows.getString(1));
        }
        assertThat(tables).contains("app_users", "expense_groups", "group_members", "expenses", "expense_payers",
                "expense_shares", "settlements", "recurring_expenses", "activity_events", "notifications",
                "device_registrations", "invitations", "idempotency_records");
    }
}

