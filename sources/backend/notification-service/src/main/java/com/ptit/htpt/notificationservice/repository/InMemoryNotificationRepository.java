package com.ptit.htpt.notificationservice.repository;

import com.ptit.htpt.notificationservice.domain.NotificationDispatch;
import com.ptit.htpt.notificationservice.domain.NotificationTemplate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryNotificationRepository {
  private final Map<String, NotificationTemplate> templates = new LinkedHashMap<>();
  private final Map<String, NotificationDispatch> dispatches = new LinkedHashMap<>();

  public Collection<NotificationTemplate> findAllTemplates() {
    return templates.values();
  }

  public Optional<NotificationTemplate> findTemplateById(String id) {
    return Optional.ofNullable(templates.get(id));
  }

  public NotificationTemplate saveTemplate(NotificationTemplate template) {
    templates.put(template.id(), template);
    return template;
  }

  public Collection<NotificationDispatch> findAllDispatches() {
    return dispatches.values();
  }

  public Optional<NotificationDispatch> findDispatchById(String id) {
    return Optional.ofNullable(dispatches.get(id));
  }

  public NotificationDispatch saveDispatch(NotificationDispatch dispatch) {
    dispatches.put(dispatch.id(), dispatch);
    return dispatch;
  }
}
