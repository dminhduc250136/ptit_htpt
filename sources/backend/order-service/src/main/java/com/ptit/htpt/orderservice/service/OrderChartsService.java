package com.ptit.htpt.orderservice.service;

import com.ptit.htpt.orderservice.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 19 / Plan 19-01 (ADMIN-01..03): aggregation service cho 3 admin chart endpoints.
 *
 * <p>3 methods:
 * <ul>
 *   <li>{@link #revenueByDay(Range)} — daily DELIVERED revenue, gap-filled với
 *       {@link BigDecimal#ZERO} cho ngày trống (D-05).</li>
 *   <li>{@link #topProducts(Range, String)} — top-10 productId+qty, enrich qua
 *       {@link ProductBatchClient} (D-03 fallback nếu product-svc fail).</li>
 *   <li>{@link #statusDistribution()} — snapshot count theo status (KHÔNG range).</li>
 * </ul>
 */
@Service
public class OrderChartsService {

  private final OrderRepository orderRepo;
  private final ProductBatchClient productBatchClient;

  public OrderChartsService(OrderRepository orderRepo, ProductBatchClient productBatchClient) {
    this.orderRepo = orderRepo;
    this.productBatchClient = productBatchClient;
  }

  @Transactional(readOnly = true)
  public List<RevenuePoint> revenueByDay(Range range) {
    Instant from = range.toFromInstant();
    List<Object[]> rows = orderRepo.aggregateRevenueByDay(from);

    Map<LocalDate, BigDecimal> raw = new HashMap<>();
    for (Object[] r : rows) {
      // r[0] is java.sql.Date (Hibernate FUNCTION('DATE', ...) → Date), r[1] BigDecimal
      LocalDate day = ((java.sql.Date) r[0]).toLocalDate();
      BigDecimal total = (BigDecimal) r[1];
      raw.put(day, total);
    }

    LocalDate start = from != null
        ? from.atZone(ZoneId.systemDefault()).toLocalDate()
        : raw.keySet().stream().min(Comparator.naturalOrder()).orElse(LocalDate.now());
    LocalDate end = LocalDate.now();

    List<RevenuePoint> points = new ArrayList<>();
    for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
      points.add(new RevenuePoint(d.toString(), raw.getOrDefault(d, BigDecimal.ZERO)));
    }
    return points;
  }

  @Transactional(readOnly = true)
  public List<TopProductPoint> topProducts(Range range, String authHeader) {
    Instant from = range.toFromInstant();
    List<Object[]> rows = orderRepo.aggregateTopProducts(from, PageRequest.of(0, 10));
    List<String> ids = rows.stream().map(r -> (String) r[0]).toList();

    Map<String, ProductBatchClient.ProductSummary> enriched =
        productBatchClient.fetchBatch(ids, authHeader);

    List<TopProductPoint> result = new ArrayList<>();
    for (Object[] r : rows) {
      String id = (String) r[0];
      long qty = ((Number) r[1]).longValue();
      ProductBatchClient.ProductSummary s = enriched.get(id);
      if (s != null) {
        result.add(new TopProductPoint(id, s.name(), s.brand(), s.thumbnailUrl(), qty));
      } else {
        // D-03 fallback: name="Product {id[:8]}", brand=null, thumbnailUrl=null
        String fallbackName = "Product " + id.substring(0, Math.min(8, id.length()));
        result.add(new TopProductPoint(id, fallbackName, null, null, qty));
      }
    }
    return result;
  }

  @Transactional(readOnly = true)
  public List<StatusPoint> statusDistribution() {
    return orderRepo.aggregateStatusDistribution().stream()
        .map(r -> new StatusPoint((String) r[0], ((Number) r[1]).longValue()))
        .toList();
  }

  // D-01 response shapes — date là ISO yyyy-MM-dd từ LocalDate.toString()
  public record RevenuePoint(String date, BigDecimal value) {}
  public record TopProductPoint(String productId, String name, String brand,
                                String thumbnailUrl, long qtySold) {}
  public record StatusPoint(String status, long count) {}
}
