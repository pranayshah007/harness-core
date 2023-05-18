/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.events;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.audit.ResourceTypeConstants.ENVIRONMENT;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.ResourceConstants.INFRASTRUCTURE_ID;
import static io.harness.ng.core.ResourceConstants.RESOURCE_TYPE;
import static io.harness.ng.core.ResourceConstants.SERVICE_OVERRIDE_NAME;
import static io.harness.ng.core.ResourceConstants.STATUS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.event.Event;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.OrgScope;
import io.harness.ng.core.ProjectScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceConstants;
import io.harness.ng.core.ResourceScope;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.utils.IdentifierRefHelper;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@OwnedBy(PIPELINE)
@Getter
@Builder
@AllArgsConstructor
public class EnvironmentUpdatedEvent implements Event {
  private String accountIdentifier;
  private String orgIdentifier;
  private String projectIdentifier;
  private Status status;
  private ResourceType resourceType;

  private InfrastructureEntity oldInfrastructureEntity;
  private InfrastructureEntity newInfrastructureEntity;

  private NGServiceOverridesEntity oldServiceOverridesEntity;
  private NGServiceOverridesEntity newServiceOverridesEntity;

  private Environment newEnvironment;
  private Environment oldEnvironment;

  public enum Status { CREATED, UPDATED, UPSERTED, DELETED, FORCE_DELETED }

  public enum ResourceType { SERVICE_OVERRIDE, INFRASTRUCTURE, ENVIRONMENT }

  @JsonIgnore
  @Override
  public ResourceScope getResourceScope() {
    if (isNotEmpty(getProjectIdentifier())) {
      return new ProjectScope(accountIdentifier, getOrgIdentifier(), getProjectIdentifier());
    } else if (isNotEmpty(getOrgIdentifier())) {
      return new OrgScope(accountIdentifier, getOrgIdentifier());
    }
    return new AccountScope(accountIdentifier);
  }

  private String getProjectIdentifier() {
    switch (resourceType) {
      case SERVICE_OVERRIDE:
        NGServiceOverridesEntity ngServiceOverridesEntity =
            newServiceOverridesEntity == null ? oldServiceOverridesEntity : newServiceOverridesEntity;
        return ngServiceOverridesEntity.getProjectIdentifier();
      case INFRASTRUCTURE:
        InfrastructureEntity infrastructure =
            newInfrastructureEntity == null ? oldInfrastructureEntity : newInfrastructureEntity;
        return infrastructure.getProjectIdentifier();
      default:
        return newEnvironment.getProjectIdentifier();
    }
  }
  private String getOrgIdentifier() {
    switch (resourceType) {
      case SERVICE_OVERRIDE:
        NGServiceOverridesEntity ngServiceOverridesEntity =
            newServiceOverridesEntity == null ? oldServiceOverridesEntity : newServiceOverridesEntity;
        return ngServiceOverridesEntity.getOrgIdentifier();
      case INFRASTRUCTURE:
        InfrastructureEntity infrastructure =
            newInfrastructureEntity == null ? oldInfrastructureEntity : newInfrastructureEntity;
        return infrastructure.getOrgIdentifier();
      default:
        return newEnvironment.getOrgIdentifier();
    }
  }

  private String resourceName() {
    switch (resourceType) {
      case SERVICE_OVERRIDE:
        NGServiceOverridesEntity ngServiceOverridesEntity =
            newServiceOverridesEntity == null ? oldServiceOverridesEntity : newServiceOverridesEntity;
        return ngServiceOverridesEntity.getServiceRef() + " Override";
      case INFRASTRUCTURE:
        InfrastructureEntity infrastructure =
            newInfrastructureEntity == null ? oldInfrastructureEntity : newInfrastructureEntity;
        return infrastructure.getIdentifier();
      default:
        return newEnvironment.getName();
    }
  }

  @JsonIgnore
  @Override
  public Resource getResource() {
    Map<String, String> labels = new HashMap<>();
    labels.put(STATUS, status.name());
    labels.put(RESOURCE_TYPE, resourceType.name());
    labels.put(ResourceConstants.LABEL_KEY_RESOURCE_NAME, resourceName());
    if (resourceType.equals(ResourceType.SERVICE_OVERRIDE)) {
      labels.put(SERVICE_OVERRIDE_NAME, resourceName());
    } else if (resourceType.equals(ResourceType.INFRASTRUCTURE)) {
      labels.put(INFRASTRUCTURE_ID, resourceName());
    }
    return Resource.builder().identifier(getEnvIdentifier()).type(ENVIRONMENT).labels(labels).build();
  }

  private String getEnvIdentifier() {
    switch (resourceType) {
      case SERVICE_OVERRIDE:
        NGServiceOverridesEntity ngServiceOverridesEntity =
            newServiceOverridesEntity == null ? oldServiceOverridesEntity : newServiceOverridesEntity;
        return IdentifierRefHelper.getIdentifier(ngServiceOverridesEntity.getEnvironmentRef());
      case INFRASTRUCTURE:
        InfrastructureEntity infrastructure =
            newInfrastructureEntity == null ? oldInfrastructureEntity : newInfrastructureEntity;
        return infrastructure.getEnvIdentifier();
      default:
        return newEnvironment.getIdentifier();
    }
  }

  @JsonIgnore
  @Override
  public String getEventType() {
    return OutboxEventConstants.ENVIRONMENT_UPDATED;
  }
}
