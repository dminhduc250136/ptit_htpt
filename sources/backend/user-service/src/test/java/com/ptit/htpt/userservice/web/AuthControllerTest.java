package com.ptit.htpt.userservice.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Phase 6 / Plan 01 (AUTH-01, AUTH-02): Integration tests cho AuthController.
 * Dùng @SpringBootTest + Testcontainers Postgres (giống UserRepositoryJpaTest pattern).
 *
 * Covers:
 * - register với credentials mới → 201 + accessToken non-null + user.username đúng
 * - register với username đã tồn tại → 409 CONFLICT
 * - register với email đã tồn tại → 409 CONFLICT
 * - login với email + password đúng → 200 + accessToken non-null
 * - login với email không tồn tại → 401 UNAUTHORIZED
 * - login với password sai → 401 UNAUTHORIZED
 * - logout → 200 OK
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class AuthControllerTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tmdt")
            .withUsername("tmdt")
            .withPassword("tmdt");

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry reg) {
        reg.add("spring.datasource.url", () ->
            POSTGRES.getJdbcUrl() + "?currentSchema=user_svc");
        reg.add("spring.datasource.username", POSTGRES::getUsername);
        reg.add("spring.datasource.password", POSTGRES::getPassword);
        reg.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        reg.add("spring.flyway.user", POSTGRES::getUsername);
        reg.add("spring.flyway.password", POSTGRES::getPassword);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    // -------- register tests --------

    @Test
    void register_withNewCredentials_returns201WithToken() throws Exception {
        var body = new RegisterRequest("newuser", "newuser@test.com", "password123");

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value(201))
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.data.user.username").value("newuser"))
            .andExpect(jsonPath("$.data.user.roles").value("CUSTOMER"))
            .andExpect(jsonPath("$.data.user.passwordHash").doesNotExist());
    }

    @Test
    void register_withDuplicateUsername_returns409() throws Exception {
        // Tạo user trước
        var first = new RegisterRequest("dupuser", "dupuser1@test.com", "password123");
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(first)))
            .andExpect(status().isCreated());

        // Dùng cùng username, khác email
        var second = new RegisterRequest("dupuser", "dupuser2@test.com", "password123");
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(second)))
            .andExpect(status().isConflict());
    }

    @Test
    void register_withDuplicateEmail_returns409() throws Exception {
        // Tạo user trước
        var first = new RegisterRequest("emailuser1", "shared@test.com", "password123");
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(first)))
            .andExpect(status().isCreated());

        // Dùng cùng email, khác username
        var second = new RegisterRequest("emailuser2", "shared@test.com", "password123");
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(second)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.code").value("CONFLICT"))
            .andExpect(jsonPath("$.message").value("Email already exists"));
    }

    // -------- login tests --------

    @Test
    void login_withCorrectCredentials_returns200WithToken() throws Exception {
        // Tạo user trước
        var reg = new RegisterRequest("loginuser", "loginuser@test.com", "securepass");
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reg)))
            .andExpect(status().isCreated());

        var body = new LoginRequest("loginuser@test.com", "securepass");
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.data.user.email").value("loginuser@test.com"));
    }

    @Test
    void login_withUnknownEmail_returns401() throws Exception {
        var body = new LoginRequest("notexist@test.com", "password123");
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void login_withWrongPassword_returns401() throws Exception {
        // Tạo user trước
        var reg = new RegisterRequest("wrongpassuser", "wrongpass@test.com", "correctpass");
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reg)))
            .andExpect(status().isCreated());

        var body = new LoginRequest("wrongpass@test.com", "wrongpassword");
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isUnauthorized());
    }

    // -------- logout test --------

    @Test
    void logout_returns200() throws Exception {
        mockMvc.perform(post("/auth/logout"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200));
    }
}
