package com.ptit.htpt.userservice.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ptit.htpt.userservice.domain.UserEntity;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Phase 19 / Plan 19-02 Task 1 — Testcontainers Postgres tests cho
 * {@link UserRepository#aggregateSignupsByDay(Instant)}.
 *
 * <p>Pattern mirror {@code UserRepositoryJpaTest}: Flyway apply V1 schema, reflection set
 * createdAt vì {@code UserEntity.create()} hardcode {@code Instant.now()}.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositorySignupsIT {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("tmdt")
          .withUsername("tmdt")
          .withPassword("tmdt");

  @BeforeAll
  static void initSchema() throws Exception {
    POSTGRES.start();
    try (var conn = java.sql.DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var st = conn.createStatement()) {
      st.execute("CREATE SCHEMA IF NOT EXISTS user_svc");
    }
  }

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url",
        () -> POSTGRES.getJdbcUrl() + "?currentSchema=user_svc");
    r.add("spring.datasource.username", POSTGRES::getUsername);
    r.add("spring.datasource.password", POSTGRES::getPassword);
    r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    r.add("spring.jpa.properties.hibernate.default_schema", () -> "user_svc");
    r.add("spring.flyway.enabled", () -> "true");
    r.add("spring.flyway.schemas", () -> "user_svc");
    r.add("spring.flyway.default-schema", () -> "user_svc");
    r.add("spring.flyway.locations", () -> "classpath:db/migration");
  }

  @Autowired UserRepository userRepo;
  @Autowired TestEntityManager em;

  private UserEntity newUserAt(String username, String email, Instant createdAt) {
    UserEntity u = UserEntity.create(username, email,
        "$2a$10$TMH2spmmPRD90vJz8w5yz.G0o4AR/Hio2RU1yBwjjT1ClTLqF5lFu", "USER");
    try {
      Field f = UserEntity.class.getDeclaredField("createdAt");
      f.setAccessible(true);
      f.set(u, createdAt);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return u;
  }

  @Test
  void aggregateSignupsByDay_nullFrom_includesAllDays_sortedAsc() {
    Instant now = Instant.now();
    // 5 users on 3 distinct days: day-10 (2), day-5 (1), today (2)
    userRepo.saveAndFlush(newUserAt("u1", "u1@x", now.minus(10, ChronoUnit.DAYS)));
    userRepo.saveAndFlush(newUserAt("u2", "u2@x", now.minus(10, ChronoUnit.DAYS).plusSeconds(60)));
    userRepo.saveAndFlush(newUserAt("u3", "u3@x", now.minus(5, ChronoUnit.DAYS)));
    userRepo.saveAndFlush(newUserAt("u4", "u4@x", now));
    userRepo.saveAndFlush(newUserAt("u5", "u5@x", now.plusSeconds(1)));
    em.flush();
    em.clear();

    List<Object[]> rows = userRepo.aggregateSignupsByDay(null);
    assertThat(rows).hasSize(3);
    // Sorted ASC by day (oldest first)
    java.sql.Date d0 = (java.sql.Date) rows.get(0)[0];
    java.sql.Date d1 = (java.sql.Date) rows.get(1)[0];
    java.sql.Date d2 = (java.sql.Date) rows.get(2)[0];
    assertThat(d0.toLocalDate()).isBefore(d1.toLocalDate());
    assertThat(d1.toLocalDate()).isBefore(d2.toLocalDate());
    // Counts per day
    assertThat(((Number) rows.get(0)[1]).longValue()).isEqualTo(2L);
    assertThat(((Number) rows.get(1)[1]).longValue()).isEqualTo(1L);
    assertThat(((Number) rows.get(2)[1]).longValue()).isEqualTo(2L);
  }

  @Test
  void aggregateSignupsByDay_withFrom_filtersOlderUsers() {
    Instant now = Instant.now();
    userRepo.saveAndFlush(newUserAt("old1", "old1@x", now.minus(10, ChronoUnit.DAYS)));
    userRepo.saveAndFlush(newUserAt("new1", "new1@x", now));
    em.flush();
    em.clear();

    Instant from = now.minus(7, ChronoUnit.DAYS);
    List<Object[]> rows = userRepo.aggregateSignupsByDay(from);
    assertThat(rows).hasSize(1);
    assertThat(((Number) rows.get(0)[1]).longValue()).isEqualTo(1L);
  }
}
