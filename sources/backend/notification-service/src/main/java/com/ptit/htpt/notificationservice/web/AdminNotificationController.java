package com.ptit.htpt.notificationservice.web;

import com.ptit.htpt.notificationservice.api.ApiResponse;
import com.ptit.htpt.notificationservice.service.NotificationCrudService;
import com.ptit.htpt.notificationservice.service.NotificationCrudService.DispatchStatusRequest;
import com.ptit.htpt.notificationservice.service.NotificationCrudService.DispatchUpsertRequest;
import com.ptit.htpt.notificationservice.service.NotificationCrudService.TemplateUpsertRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/notifications")
public class AdminNotificationController {
  private final NotificationCrudService notificationCrudService;

  public AdminNotificationController(NotificationCrudService notificationCrudService) {
    this.notificationCrudService = notificationCrudService;
  }

  @GetMapping("/templates")
  public ApiResponse<Map<String, Object>> listTemplates(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "updatedAt,desc") String sort,
      @RequestParam(defaultValue = "true") boolean includeDeleted
  ) {
    return ApiResponse.of(
        200,
        "Admin notification templates listed",
        notificationCrudService.listTemplates(page, size, sort, includeDeleted)
    );
  }

  @PostMapping("/templates")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<Object> createTemplate(@Valid @RequestBody TemplateUpsertRequest request) {
    return ApiResponse.of(201, "Admin notification template created", notificationCrudService.createTemplate(request));
  }

  @PutMapping("/templates/{id}")
  public ApiResponse<Object> updateTemplate(@PathVariable String id, @Valid @RequestBody TemplateUpsertRequest request) {
    return ApiResponse.of(200, "Admin notification template updated", notificationCrudService.updateTemplate(id, request));
  }

  @DeleteMapping("/templates/{id}")
  public ApiResponse<Map<String, Object>> deleteTemplate(@PathVariable String id) {
    notificationCrudService.deleteTemplate(id);
    return ApiResponse.of(200, "Admin notification template soft deleted", Map.of("id", id, "deleted", true));
  }

  @GetMapping("/dispatches")
  public ApiResponse<Map<String, Object>> listDispatches(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "updatedAt,desc") String sort,
      @RequestParam(defaultValue = "true") boolean includeDeleted
  ) {
    return ApiResponse.of(
        200,
        "Admin notification dispatches listed",
        notificationCrudService.listDispatches(page, size, sort, includeDeleted)
    );
  }

  @PostMapping("/dispatches")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<Object> createDispatch(@Valid @RequestBody DispatchUpsertRequest request) {
    return ApiResponse.of(201, "Admin notification dispatch created", notificationCrudService.createDispatch(request));
  }

  @PutMapping("/dispatches/{id}")
  public ApiResponse<Object> updateDispatch(@PathVariable String id, @Valid @RequestBody DispatchUpsertRequest request) {
    return ApiResponse.of(200, "Admin notification dispatch updated", notificationCrudService.updateDispatch(id, request));
  }

  @PatchMapping("/dispatches/{id}/status")
  public ApiResponse<Object> updateDispatchStatus(
      @PathVariable String id,
      @Valid @RequestBody DispatchStatusRequest request
  ) {
    return ApiResponse.of(200, "Admin notification dispatch status updated", notificationCrudService.updateDispatchStatus(id, request));
  }

  @DeleteMapping("/dispatches/{id}")
  public ApiResponse<Map<String, Object>> deleteDispatch(@PathVariable String id) {
    notificationCrudService.deleteDispatch(id);
    return ApiResponse.of(200, "Admin notification dispatch soft deleted", Map.of("id", id, "deleted", true));
  }
}
