package com.ptit.htpt.userservice.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ptit.htpt.userservice.repository.UserRepository;
import com.ptit.htpt.userservice.repository.VerificationTokenRepository;
import com.ptit.htpt.userservice.service.VerificationTokenService;
import com.ptit.htpt.userservice.web.dto.ForgotPasswordRequest;
import com.ptit.htpt.userservice.web.dto.ResetPasswordRequest;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Phase 27 / Plan 27-04 (MAIL-02) — TDD RED phase → AuthControllerIT.
 *
 * Integration test cho 3 endpoint mới:
 *   GET  /auth/verify-email?token=...
 *   POST /auth/password/forgot
 *   POST /auth/password/reset
 *
 * Test 1 (testVerifyEmail): GET /auth/verify-email với valid token → 200; emailVerified=true
 * Test 2 (testForgotReturns200Always): POST /auth/password/forgot email KHÔNG tồn tại → 200
 * Test 3 (testForgotWithExistingEmail): POST /auth/password/forgot email tồn tại → 200
 * Test 4 (testResetPassword): POST /auth/password/reset với token hợp lệ → 200; login thành công bằng pass mới
 * Test 5 (testVerifyEmailTokenReplay): GET /auth/verify-email token đã used → 410
 *
 * Dùng @SpringBootTest + Testcontainers Postgres (pattern từ AuthControllerTest.java).
 * RabbitMQ: AccountEventPublisher afterCommit không fail nếu broker không có — chỉ log error.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class AuthControllerIT {

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
        // Disable RabbitMQ auto-connect trong test (không có broker)
        reg.add("spring.rabbitmq.host", () -> "localhost");
        reg.add("spring.rabbitmq.port", () -> "5672");
        reg.add("spring.autoconfigure.exclude",
            () -> "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    VerificationTokenRepository verificationTokenRepository;

    @Autowired
    VerificationTokenService verificationTokenService;

    @Autowired
    PasswordEncoder passwordEncoder;

    // Helper: register user trả về userId
    private String registerUser(String username, String email, String password) throws Exception {
        var body = new RegisterRequest(username, email, password);
        var result = mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andReturn();
        var responseStr = result.getResponse().getContentAsString();
        var node = objectMapper.readTree(responseStr);
        return node.at("/data/user/id").asText();
    }

    @BeforeEach
    void cleanTokens() {
        verificationTokenRepository.deleteAll();
    }

    // Test 1: GET /auth/verify-email với valid token → 200 + emailVerified=true
    @Test
    @DisplayName("Test 1: verify-email với token hợp lệ → 200 + lật email_verified=true")
    void testVerifyEmail() throws Exception {
        String userId = registerUser("verifyme", "verifyme@test.com", "pass1234");

        // Tạo token trực tiếp qua service (bỏ qua token đã tạo khi register)
        verificationTokenRepository.deleteAll();
        var tokenEntity = verificationTokenService.createToken(userId, "EMAIL_VERIFY", Duration.ofHours(24));

        mockMvc.perform(get("/auth/verify-email")
                .param("token", tokenEntity.token()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200));

        // Kiểm tra emailVerified đã lật
        var user = userRepository.findById(userId).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(user.emailVerified()).isTrue();
    }

    // Test 2: POST /auth/password/forgot email KHÔNG tồn tại → vẫn 200 (anti-enumeration)
    @Test
    @DisplayName("Test 2: forgot với email không tồn tại → vẫn 200 (anti-enumeration D-08)")
    void testForgotReturns200Always() throws Exception {
        var body = new ForgotPasswordRequest("nonexistent@nowhere.com");

        mockMvc.perform(post("/auth/password/forgot")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200));
    }

    // Test 3: POST /auth/password/forgot email tồn tại → 200
    @Test
    @DisplayName("Test 3: forgot với email tồn tại → 200")
    void testForgotWithExistingEmail() throws Exception {
        registerUser("forgotuser", "forgotuser@test.com", "pass1234");
        var body = new ForgotPasswordRequest("forgotuser@test.com");

        mockMvc.perform(post("/auth/password/forgot")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200));
    }

    // Test 4: POST /auth/password/reset với token hợp lệ → 200; login bằng mật khẩu mới thành công
    @Test
    @DisplayName("Test 4: reset password với token hợp lệ → 200; login bằng pass mới thành công")
    void testResetPassword() throws Exception {
        String userId = registerUser("resetuser", "resetuser@test.com", "oldpassword");

        // Tạo reset token
        verificationTokenRepository.deleteAll();
        var tokenEntity = verificationTokenService.createToken(userId, "PASSWORD_RESET", Duration.ofHours(1));

        var resetBody = new ResetPasswordRequest(tokenEntity.token(), "newpassword123");
        mockMvc.perform(post("/auth/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(resetBody)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200));

        // Login bằng mật khẩu mới phải thành công
        var loginBody = new LoginRequest("resetuser@test.com", "newpassword123");
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginBody)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    // Test 5: GET /auth/verify-email với token đã used → 410 GONE
    @Test
    @DisplayName("Test 5: verify-email với token đã dùng → 410 GONE")
    void testVerifyEmailTokenReplay() throws Exception {
        String userId = registerUser("replayuser", "replayuser@test.com", "pass1234");

        verificationTokenRepository.deleteAll();
        var tokenEntity = verificationTokenService.createToken(userId, "EMAIL_VERIFY", Duration.ofHours(24));
        String token = tokenEntity.token();

        // Lần 1: thành công
        mockMvc.perform(get("/auth/verify-email").param("token", token))
            .andExpect(status().isOk());

        // Lần 2: token replay → 410 GONE
        mockMvc.perform(get("/auth/verify-email").param("token", token))
            .andExpect(status().isGone());
    }
}
