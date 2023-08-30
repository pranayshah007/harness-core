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

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
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
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverrideAuditEventDTO;
import io.harness.utils.IdentifierRefHelper;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
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

  private ServiceOverrideAuditEventDTO oldOverrideAuditEventDTO;
  private ServiceOverrideAuditEventDTO newOverrideAuditEventDTO;
  private boolean overrideAuditV2;

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
        return getProjectIdentifierFromOverride();
      case INFRASTRUCTURE:
        InfrastructureEntity infrastructure =
            newInfrastructureEntity == null ? oldInfrastructureEntity : newInfrastructureEntity;
        return infrastructure.getProjectIdentifier();
      default:
        return newEnvironment.getProjectIdentifier();
    }
  }

  private String getProjectIdentifierFromOverride() {
    String projectIdentifier = StringUtils.EMPTY;
    if (overrideAuditV2) {
      ServiceOverrideAuditEventDTO overrideAuditEventDTO =
          newOverrideAuditEventDTO == null ? oldOverrideAuditEventDTO : newOverrideAuditEventDTO;
      projectIdentifier = overrideAuditEventDTO.getProjectIdentifier();

    } else {
      NGServiceOverridesEntity ngServiceOverridesEntity =
          newServiceOverridesEntity == null ? oldServiceOverridesEntity : newServiceOverridesEntity;
      projectIdentifier = ngServiceOverridesEntity.getProjectIdentifier();
    }
    return projectIdentifier;
  }

  private String getOrgIdentifier() {
    switch (resourceType) {
      case SERVICE_OVERRIDE:
        return getOrgIdentifierFromOverride();
      case INFRASTRUCTURE:
        InfrastructureEntity infrastructure =
            newInfrastructureEntity == null ? oldInfrastructureEntity : newInfrastructureEntity;
        return infrastructure.getOrgIdentifier();
      default:
        return newEnvironment.getOrgIdentifier();
    }
  }

  private String getOrgIdentifierFromOverride() {
    String orgIdentifier = StringUtils.EMPTY;
    if (overrideAuditV2) {
      ServiceOverrideAuditEventDTO overrideAuditEventDTO =
          newOverrideAuditEventDTO == null ? oldOverrideAuditEventDTO : newOverrideAuditEventDTO;
      orgIdentifier = overrideAuditEventDTO.getOrgIdentifier();

    } else {
      NGServiceOverridesEntity ngServiceOverridesEntity =
          newServiceOverridesEntity == null ? oldServiceOverridesEntity : newServiceOverridesEntity;
      orgIdentifier = ngServiceOverridesEntity.getOrgIdentifier();
    }
    return orgIdentifier;
  }

  private String resourceName() {
    switch (resourceType) {
      case SERVICE_OVERRIDE:
        return getResourceNameFromOverride();
      case INFRASTRUCTURE:
        InfrastructureEntity infrastructure =
            newInfrastructureEntity == null ? oldInfrastructureEntity : newInfrastructureEntity;
        return infrastructure.getIdentifier();
      default:
        return newEnvironment.getName();
    }
  }

  private String getResourceNameFromOverride() {
    String resourceIdentifier = StringUtils.EMPTY;
    if (overrideAuditV2) {
      ServiceOverrideAuditEventDTO overrideAuditEventDTO =
          newOverrideAuditEventDTO == null ? oldOverrideAuditEventDTO : newOverrideAuditEventDTO;
      resourceIdentifier = overrideAuditEventDTO.isEntityV2() ? overrideAuditEventDTO.getIdentifier()
                                                              : overrideAuditEventDTO.getServiceRef() + " Override";
    } else {
      NGServiceOverridesEntity ngServiceOverridesEntity =
          newServiceOverridesEntity == null ? oldServiceOverridesEntity : newServiceOverridesEntity;
      resourceIdentifier = Boolean.TRUE.equals(ngServiceOverridesEntity.getIsV2())
          ? ngServiceOverridesEntity.getIdentifier()
          : ngServiceOverridesEntity.getServiceRef() + " Override";
    }
    return resourceIdentifier;
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
        return getEnvIdentifierFromOverride();
      case INFRASTRUCTURE:
        InfrastructureEntity infrastructure =
            newInfrastructureEntity == null ? oldInfrastructureEntity : newInfrastructureEntity;
        return infrastructure.getEnvIdentifier();
      default:
        return newEnvironment.getIdentifier();
    }
  }

  private String getEnvIdentifierFromOverride() {
    String envRef = StringUtils.EMPTY;
    if (overrideAuditV2) {
      ServiceOverrideAuditEventDTO overrideAuditEventDTO =
          newOverrideAuditEventDTO == null ? oldOverrideAuditEventDTO : newOverrideAuditEventDTO;
      envRef = overrideAuditEventDTO.getEnvironmentRef();
    } else {
      NGServiceOverridesEntity ngServiceOverridesEntity =
          newServiceOverridesEntity == null ? oldServiceOverridesEntity : newServiceOverridesEntity;
      envRef = ngServiceOverridesEntity.getEnvironmentRef();
    }
    return IdentifierRefHelper.getIdentifier(envRef);
  }

  @JsonIgnore
  @Override
  public String getEventType() {
    return OutboxEventConstants.ENVIRONMENT_UPDATED;
  }
}
