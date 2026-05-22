package com.ptit.htpt.userservice.service;

import com.ptit.htpt.userservice.domain.VerificationTokenEntity;
import com.ptit.htpt.userservice.repository.VerificationTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase 27 / Plan 27-02 — TDD RED phase.
 *
 * Unit test cho VerificationTokenService (sử dụng Mockito mock repository).
 * Test 1: generateToken() trả chuỗi 43 ký tự URL-safe, 2 lần gọi khác nhau
 * Test 2: verifyAndConsume token hợp lệ chưa dùng → markUsed, usedAt != null
 * Test 3: verifyAndConsume token đã used → ResponseStatusException 410 GONE
 * Test 4: verifyAndConsume token hết hạn → ResponseStatusException 410 GONE
 * Test 5: verifyAndConsume token sai type → ResponseStatusException 400 BAD_REQUEST
 */
class VerificationTokenServiceTest {

    private static final Pattern URL_SAFE = Pattern.compile("^[A-Za-z0-9\\-_]+$");

    private VerificationTokenRepository repository;
    private VerificationTokenService service;

    @BeforeEach
    void setUp() {
        repository = mock(VerificationTokenRepository.class);
        service = new VerificationTokenService(repository);
    }

    @Test
    @DisplayName("Test 1: generateToken() trả chuỗi 43 ký tự URL-safe, 2 lần gọi khác nhau")
    void generateToken_returnsUrlSafe43Chars_andUnique() {
        String t1 = service.generateToken();
        String t2 = service.generateToken();

        assertThat(t1).hasSize(43);
        assertThat(t1).matches(URL_SAFE);
        assertThat(t2).hasSize(43);
        assertThat(t2).matches(URL_SAFE);
        assertThat(t1).isNotEqualTo(t2);
    }

    @Test
    @DisplayName("Test 2: verifyAndConsume token hợp lệ chưa dùng → markUsed, usedAt != null")
    void verifyAndConsume_validToken_marksUsed() {
        String token = "validToken123";
        String userId = "user-1";
        String type = "EMAIL_VERIFY";
        VerificationTokenEntity entity = new VerificationTokenEntity(
                token, userId, type, Instant.now().plus(Duration.ofHours(24)));

        when(repository.findByTokenForUpdate(token)).thenReturn(Optional.of(entity));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        VerificationTokenEntity result = service.verifyAndConsume(token, type);

        assertThat(result.usedAt()).isNotNull();
    }

    @Test
    @DisplayName("Test 3: verifyAndConsume token đã used → ResponseStatusException 410 GONE")
    void verifyAndConsume_alreadyUsed_throwsGone() {
        String token = "usedToken";
        String type = "EMAIL_VERIFY";
        VerificationTokenEntity entity = new VerificationTokenEntity(
                token, "user-1", type, Instant.now().plus(Duration.ofHours(24)));
        entity.markUsed(); // đánh dấu đã dùng

        when(repository.findByTokenForUpdate(token)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.verifyAndConsume(token, type))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(HttpStatus.GONE.value());
                });
    }

    @Test
    @DisplayName("Test 4: verifyAndConsume token hết hạn → ResponseStatusException 410 GONE")
    void verifyAndConsume_expired_throwsGone() {
        String token = "expiredToken";
        String type = "PASSWORD_RESET";
        VerificationTokenEntity entity = new VerificationTokenEntity(
                token, "user-1", type, Instant.now().minus(Duration.ofMinutes(1)));

        when(repository.findByTokenForUpdate(token)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.verifyAndConsume(token, type))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(HttpStatus.GONE.value());
                });
    }

    @Test
    @DisplayName("Test 5: verifyAndConsume token sai type → ResponseStatusException 400 BAD_REQUEST")
    void verifyAndConsume_wrongType_throwsBadRequest() {
        String token = "validToken";
        VerificationTokenEntity entity = new VerificationTokenEntity(
                token, "user-1", "EMAIL_VERIFY", Instant.now().plus(Duration.ofHours(24)));

        when(repository.findByTokenForUpdate(token)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.verifyAndConsume(token, "PASSWORD_RESET"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value());
                });
    }
}
