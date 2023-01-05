package io.harness.ng.core.events;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.audit.ResourceTypeConstants.SERVICE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.event.Event;
import io.harness.ng.core.ProjectScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceScope;
import io.harness.ng.core.service.entity.ServiceEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
@OwnedBy(CDC)
@Getter
@Builder
@AllArgsConstructor
public class ServiceForceDeleteEvent implements Event {
  private String accountIdentifier;
  private String orgIdentifier;
  private String projectIdentifier;
  private ServiceEntity service;
  @Override
  public ResourceScope getResourceScope() {
    return new ProjectScope(accountIdentifier, service.getOrgIdentifier(), service.getProjectIdentifier());
  }

  @Override
  public Resource getResource() {
    return Resource.builder().identifier(service.getIdentifier()).type(SERVICE).build();
  }

  @Override
  public String getEventType() {
    return OutboxEventConstants.SERVICE_FORCE_DELETED;
  }
}
