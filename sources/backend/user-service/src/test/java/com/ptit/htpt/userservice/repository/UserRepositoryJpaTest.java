package com.ptit.htpt.userservice.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ptit.htpt.userservice.domain.UserDto;
import com.ptit.htpt.userservice.domain.UserEntity;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
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
 * Phase 5 / Plan 04 (DB-02..05): JPA repository test cho user-service.
 * Testcontainers spin Postgres 16, áp dụng V1 (Flyway) tạo schema `user_svc`.
 * Cover: save/findById, findByUsername, findByEmail, soft-delete, DTO không leak passwordHash/deleted.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryJpaTest {

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

  private UserEntity newUser(String username, String email) {
    Instant now = Instant.now();
    return UserEntity.create(username, email,
        "$2a$10$TMH2spmmPRD90vJz8w5yz.G0o4AR/Hio2RU1yBwjjT1ClTLqF5lFu", "USER");
  }

  @Test
  void saveAndFindById_returnsSameUser() {
    UserEntity saved = userRepo.save(newUser("alice", "alice@tmdt.local"));
    em.flush();
    em.clear();

    Optional<UserEntity> found = userRepo.findById(saved.id());
    assertThat(found).isPresent();
    assertThat(found.get().username()).isEqualTo("alice");
    assertThat(found.get().email()).isEqualTo("alice@tmdt.local");
  }

  @Test
  void findByUsername_returnsExistingUser_emptyForMissing() {
    userRepo.save(newUser("bob", "bob@tmdt.local"));
    em.flush();
    em.clear();

    assertThat(userRepo.findByUsername("bob")).isPresent();
    assertThat(userRepo.findByUsername("missing")).isEmpty();
  }

  @Test
  void findByEmail_returnsExistingUser_emptyForMissing() {
    userRepo.save(newUser("carol", "carol@tmdt.local"));
    em.flush();
    em.clear();

    assertThat(userRepo.findByEmail("carol@tmdt.local")).isPresent();
    assertThat(userRepo.findByEmail("nope@tmdt.local")).isEmpty();
  }

  @Test
  void softDelete_filtersOutFromFindAll() {
    UserEntity u1 = userRepo.save(newUser("dave", "dave@tmdt.local"));
    userRepo.save(newUser("eve",  "eve@tmdt.local"));
    em.flush();

    u1.softDelete();
    userRepo.save(u1);
    em.flush();
    em.clear();

    List<UserEntity> all = userRepo.findAll();
    assertThat(all).hasSize(1);
    assertThat(all.get(0).username()).isEqualTo("eve");
  }

  @Test
  void userDto_doesNotLeakPasswordHashOrDeleted() {
    List<String> componentNames = Arrays.stream(UserDto.class.getRecordComponents())
        .map(RecordComponent::getName)
        .toList();
    assertThat(componentNames).doesNotContain("passwordHash", "deleted", "password_hash");
  }
}
