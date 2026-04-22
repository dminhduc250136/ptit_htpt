package com.ptit.htpt.notificationservice.service;

import com.ptit.htpt.notificationservice.domain.NotificationDispatch;
import com.ptit.htpt.notificationservice.domain.NotificationTemplate;
import com.ptit.htpt.notificationservice.repository.InMemoryNotificationRepository;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationCrudService {
  private final InMemoryNotificationRepository repository;

  public NotificationCrudService(InMemoryNotificationRepository repository) {
    this.repository = repository;
  }

  public Map<String, Object> listTemplates(int page, int size, String sort, boolean includeDeleted) {
    List<NotificationTemplate> all = repository.findAllTemplates().stream()
        .filter(template -> includeDeleted || !template.deleted())
        .sorted(templateComparator(sort))
        .toList();
    return paginate(all, page, size);
  }

  public NotificationTemplate getTemplate(String id, boolean includeDeleted) {
    NotificationTemplate template = repository.findTemplateById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification template not found"));
    if (!includeDeleted && template.deleted()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification template not found");
    }
    return template;
  }

  public NotificationTemplate createTemplate(TemplateUpsertRequest request) {
    NotificationTemplate template = NotificationTemplate.create(request.code(), request.title(), request.body());
    return repository.saveTemplate(template);
  }

  public NotificationTemplate updateTemplate(String id, TemplateUpsertRequest request) {
    NotificationTemplate current = getTemplate(id, true);
    NotificationTemplate updated = current.update(request.code(), request.title(), request.body());
    return repository.saveTemplate(updated);
  }

  public void deleteTemplate(String id) {
    NotificationTemplate current = getTemplate(id, true);
    repository.saveTemplate(current.softDelete());
  }

  public Map<String, Object> listDispatches(int page, int size, String sort, boolean includeDeleted) {
    List<NotificationDispatch> all = repository.findAllDispatches().stream()
        .filter(dispatch -> includeDeleted || !dispatch.deleted())
        .sorted(dispatchComparator(sort))
        .toList();
    return paginate(all, page, size);
  }

  public NotificationDispatch getDispatch(String id, boolean includeDeleted) {
    NotificationDispatch dispatch = repository.findDispatchById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification dispatch not found"));
    if (!includeDeleted && dispatch.deleted()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification dispatch not found");
    }
    return dispatch;
  }

  public NotificationDispatch createDispatch(DispatchUpsertRequest request) {
    NotificationDispatch dispatch = NotificationDispatch.create(
        request.templateId(),
        request.recipient(),
        request.status()
    );
    return repository.saveDispatch(dispatch);
  }

  public NotificationDispatch updateDispatch(String id, DispatchUpsertRequest request) {
    NotificationDispatch current = getDispatch(id, true);
    NotificationDispatch updated = current.update(request.templateId(), request.recipient(), request.status());
    return repository.saveDispatch(updated);
  }

  public NotificationDispatch updateDispatchStatus(String id, DispatchStatusRequest request) {
    NotificationDispatch current = getDispatch(id, true);
    NotificationDispatch updated = current.update(current.templateId(), current.recipient(), request.status());
    return repository.saveDispatch(updated);
  }

  public void deleteDispatch(String id) {
    NotificationDispatch current = getDispatch(id, true);
    repository.saveDispatch(current.softDelete());
  }

  private Comparator<NotificationTemplate> templateComparator(String sort) {
    if (sort == null || sort.isBlank()) {
      return Comparator.comparing(NotificationTemplate::updatedAt).reversed();
    }
    boolean desc = sort.endsWith(",desc");
    Comparator<NotificationTemplate> comparator = sort.startsWith("code")
        ? Comparator.comparing(NotificationTemplate::code, String.CASE_INSENSITIVE_ORDER)
        : Comparator.comparing(NotificationTemplate::id);
    return desc ? comparator.reversed() : comparator;
  }

  private Comparator<NotificationDispatch> dispatchComparator(String sort) {
    if (sort == null || sort.isBlank()) {
      return Comparator.comparing(NotificationDispatch::updatedAt).reversed();
    }
    boolean desc = sort.endsWith(",desc");
    Comparator<NotificationDispatch> comparator = sort.startsWith("status")
        ? Comparator.comparing(NotificationDispatch::status, String.CASE_INSENSITIVE_ORDER)
        : Comparator.comparing(NotificationDispatch::id);
    return desc ? comparator.reversed() : comparator;
  }

  private <T> Map<String, Object> paginate(List<T> source, int page, int size) {
    int safePage = Math.max(page, 0);
    int safeSize = size <= 0 ? 20 : Math.min(size, 100);
    int totalElements = source.size();
    int from = Math.min(safePage * safeSize, totalElements);
    int to = Math.min(from + safeSize, totalElements);
    List<T> content = new ArrayList<>(source.subList(from, to));
    int totalPages = totalElements == 0 ? 1 : (int) Math.ceil((double) totalElements / safeSize);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("content", content);
    result.put("totalElements", totalElements);
    result.put("totalPages", totalPages);
    result.put("currentPage", safePage);
    result.put("pageSize", safeSize);
    result.put("isFirst", safePage <= 0);
    result.put("isLast", safePage >= Math.max(totalPages - 1, 0));
    return result;
  }

  public record TemplateUpsertRequest(@NotBlank String code, @NotBlank String title, @NotBlank String body) {}

  public record DispatchUpsertRequest(@NotBlank String templateId, @NotBlank String recipient, @NotBlank String status) {}

  public record DispatchStatusRequest(@NotBlank String status) {}
}
