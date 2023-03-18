/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.audit.mapper;

import static io.harness.beans.SortOrder.OrderType.DESC;

import io.harness.ModuleType;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditFilterPropertiesDTO;
import io.harness.audit.beans.Environment;
import io.harness.audit.beans.Principal;
import io.harness.audit.beans.PrincipalType;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.entities.AuditEvent.AuditEventKeys;
import io.harness.audit.remote.StaticAuditFilterV2;
import io.harness.beans.SortOrder;
import io.harness.exception.UnknownEnumTypeException;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.utils.PageUtils;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ApiUtils {
  public Action getAction(io.harness.spec.server.audit.v1.model.Action action) {
    try {
      return Action.valueOf(action.name());
    } catch (IllegalArgumentException exception) {
      throw new UnknownEnumTypeException("Unknown action", action.name());
    }
  }

  public EnvironmentType getEnvironmentType(io.harness.spec.server.audit.v1.model.EnvironmentType type) {
    try {
      return EnvironmentType.valueOf(type.name());
    } catch (IllegalArgumentException exception) {
      throw new UnknownEnumTypeException("Unknown action", type.name());
    }
  }

  public Environment getEnvironment(io.harness.spec.server.audit.v1.model.Environment environment) {
    return Environment.builder()
        .identifier(environment.getIdentifier())
        .type(getEnvironmentType(environment.getType()))
        .build();
  }

  public PrincipalType getPrincipalType(io.harness.spec.server.audit.v1.model.PrincipalType principalType) {
    try {
      return PrincipalType.valueOf(principalType.name());
    } catch (IllegalArgumentException exception) {
      throw new UnknownEnumTypeException("Unknown action", principalType.name());
    }
  }
  public Principal getPrincipal(io.harness.spec.server.audit.v1.model.Principal principal) {
    return Principal.builder()
        .identifier(principal.getIdentifier())
        .type(getPrincipalType(principal.getType()))
        .build();
  }

  public ResourceDTO getResourceDTO(io.harness.spec.server.audit.v1.model.ResourceDTO resourceDTO) {
    return ResourceDTO.builder()
        .type(resourceDTO.getType())
        .identifier(resourceDTO.getIdentifier())
        .labels(resourceDTO.getLabels())
        .build();
  }
  public StaticAuditFilterV2 getStaticAuditFilter(
      io.harness.spec.server.audit.v1.model.StaticAuditFilter staticAuditFilter) {
    try {
      return StaticAuditFilterV2.valueOf(staticAuditFilter.name());
    } catch (IllegalArgumentException exception) {
      throw new UnknownEnumTypeException("Unknown action", staticAuditFilter.name());
    }
  }

  public ModuleType getModuleType(io.harness.spec.server.audit.v1.model.ModuleType moduleType) {
    try {
      return ModuleType.valueOf(moduleType.name());
    } catch (IllegalArgumentException exception) {
      throw new UnknownEnumTypeException("Unknown action", moduleType.name());
    }
  }

  public ResourceScopeDTO getResourceScopeDTO(io.harness.spec.server.audit.v1.model.ResourceScopeDTO resourceScopeDTO) {
    return ResourceScopeDTO.builder()
        .accountIdentifier(resourceScopeDTO.getAccountIdentifier())
        .orgIdentifier(resourceScopeDTO.getOrgIdentifier())
        .projectIdentifier(resourceScopeDTO.getProjectIdentifier())
        .labels(resourceScopeDTO.getLabels())
        .build();
  }

  public AuditFilterPropertiesDTO getAuditFilterPropertiesV1DTO(
      io.harness.spec.server.audit.v1.model.AuditFilterPropertiesV1DTO auditFilterPropertiesV1DTO) {
    if (auditFilterPropertiesV1DTO == null) {
      return null;
    }
    return AuditFilterPropertiesDTO.builder()
        .actions(auditFilterPropertiesV1DTO.getActions().stream().map(this::getAction).collect(Collectors.toList()))
        .endTime(auditFilterPropertiesV1DTO.getEndTime())
        .environments(auditFilterPropertiesV1DTO.getEnvironments()
                          .stream()
                          .map(this::getEnvironment)
                          .collect(Collectors.toList()))
        .principals(
            auditFilterPropertiesV1DTO.getPrincipals().stream().map(this::getPrincipal).collect(Collectors.toList()))
        .scopes(
            auditFilterPropertiesV1DTO.getScopes().stream().map(this::getResourceScopeDTO).collect(Collectors.toList()))
        .resources(
            auditFilterPropertiesV1DTO.getResources().stream().map(this::getResourceDTO).collect(Collectors.toList()))
        .staticFilters(auditFilterPropertiesV1DTO.getStaticFilter()
                           .stream()
                           .map(this::getStaticAuditFilter)
                           .collect(Collectors.toList()))
        .modules(auditFilterPropertiesV1DTO.getModules().stream().map(this::getModuleType).collect(Collectors.toList()))
        .startTime(auditFilterPropertiesV1DTO.getStartTime())
        .build();
  }

  public PageRequest getPageRequest(int page, int limit, String sort, String order) {
    List<SortOrder> sortOrders = new ArrayList<>();
    if (PageUtils.SortFields.fromValue(sort) == null) {
      sortOrders = ImmutableList.of(SortOrder.Builder.aSortOrder().withField(AuditEventKeys.timestamp, DESC).build());
    }
    return new PageRequest(page, limit, sortOrders);
  }
}
