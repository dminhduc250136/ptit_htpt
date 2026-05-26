package com.ptit.htpt.userservice.service;

import com.ptit.htpt.userservice.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 19 / Plan 19-02 (ADMIN-04): aggregation service cho user-svc admin chart endpoint.
 *
 * <p>1 method:
 * <ul>
 *   <li>{@link #signupsByDay(Range)} — daily user signups, gap-filled với
 *       {@code 0L} cho ngày trống (D-05).</li>
 * </ul>
 *
 * <p>Pattern mirror {@code OrderChartsService.revenueByDay} nhưng dùng {@code long} count
 * thay {@code BigDecimal} value.
 */
@Service
public class UserChartsService {

  private final UserRepository userRepo;

  public UserChartsService(UserRepository userRepo) {
    this.userRepo = userRepo;
  }

  @Transactional(readOnly = true)
  public List<SignupPoint> signupsByDay(Range range) {
    Instant from = range.toFromInstant();
    List<Object[]> rows = userRepo.aggregateSignupsByDay(from);

    Map<LocalDate, Long> raw = new HashMap<>();
    for (Object[] r : rows) {
      // r[0] is java.sql.Date (Hibernate FUNCTION('DATE', ...) → Date), r[1] Long
      LocalDate day = ((java.sql.Date) r[0]).toLocalDate();
      long count = ((Number) r[1]).longValue();
      raw.put(day, count);
    }

    LocalDate start = from != null
        ? from.atZone(ZoneId.systemDefault()).toLocalDate()
        : raw.keySet().stream().min(Comparator.naturalOrder()).orElse(LocalDate.now());
    LocalDate end = LocalDate.now();

    List<SignupPoint> points = new ArrayList<>();
    for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
      points.add(new SignupPoint(d.toString(), raw.getOrDefault(d, 0L)));
    }
    return points;
  }

  // D-01 response shape — date là ISO yyyy-MM-dd từ LocalDate.toString()
  public record SignupPoint(String date, long count) {}
}
