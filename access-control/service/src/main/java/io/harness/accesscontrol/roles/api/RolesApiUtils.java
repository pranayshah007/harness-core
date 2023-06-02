/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roles.api;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.accesscontrol.roles.api.RoleDTO.ScopeLevel;
import io.harness.accesscontrol.scopes.ScopeDTO;
import io.harness.annotations.dev.OwnedBy;
import io.harness.spec.server.accesscontrol.v1.model.CreateRoleRequest;
import io.harness.spec.server.accesscontrol.v1.model.RoleScope;
import io.harness.spec.server.accesscontrol.v1.model.RolesResponse;
import io.harness.spec.server.accesscontrol.v1.model.RolesResponse.AllowedScopeLevelsEnum;

import com.google.inject.Inject;
import io.dropwizard.jersey.validation.JerseyViolationException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;

@OwnedBy(PL)
public class RolesApiUtils {
  private final Validator validator;

  @Inject
  public RolesApiUtils(Validator validator) {
    this.validator = validator;
  }

  public RoleDTO getRoleAccDTO(CreateRoleRequest roleRequest) {
    RoleDTO roleDTO = RoleDTO.builder()
                          .identifier(roleRequest.getIdentifier())
                          .name(roleRequest.getName())
                          .permissions(new HashSet<>(roleRequest.getPermissions()))
                          .allowedScopeLevels(Collections.singleton(ScopeLevel.ACCOUNT))
                          .description(roleRequest.getDescription())
                          .tags(roleRequest.getTags())
                          .build();
    validate(roleDTO);
    return roleDTO;
  }

  public RoleDTO getRoleOrgDTO(CreateRoleRequest roleRequest) {
    RoleDTO roleDTO = RoleDTO.builder()
                          .identifier(roleRequest.getIdentifier())
                          .name(roleRequest.getName())
                          .permissions(new HashSet<>(roleRequest.getPermissions()))
                          .allowedScopeLevels(Collections.singleton(ScopeLevel.ORGANIZATION))
                          .description(roleRequest.getDescription())
                          .tags(roleRequest.getTags())
                          .build();
    validate(roleDTO);
    return roleDTO;
  }

  public RoleDTO getRoleProjectDTO(CreateRoleRequest roleRequest) {
    RoleDTO roleDTO = RoleDTO.builder()
                          .identifier(roleRequest.getIdentifier())
                          .name(roleRequest.getName())
                          .permissions(new HashSet<>(roleRequest.getPermissions()))
                          .allowedScopeLevels(Collections.singleton(ScopeLevel.PROJECT))
                          .description(roleRequest.getDescription())
                          .tags(roleRequest.getTags())
                          .build();
    validate(roleDTO);
    return roleDTO;
  }

  private void validate(RoleDTO roleDTO) {
    Set<ConstraintViolation<RoleDTO>> violations = validator.validate(roleDTO);
    if (!violations.isEmpty()) {
      throw new JerseyViolationException(violations, null);
    }
  }

  public static RolesResponse getRolesResponse(RoleResponseDTO responseDTO) {
    if (responseDTO.getRole() == null) {
      return null;
    }
    RolesResponse rolesResponse = new RolesResponse();
    rolesResponse.setIdentifier(responseDTO.getRole().getIdentifier());
    rolesResponse.setName(responseDTO.getRole().getName());
    Set<String> permissions = responseDTO.getRole().getPermissions();
    if (permissions != null) {
      rolesResponse.setPermissions(new ArrayList<>(permissions));
    }
    Set<ScopeLevel> allowedScopeLevels = responseDTO.getRole().getAllowedScopeLevels();
    if (allowedScopeLevels != null) {
      rolesResponse.setAllowedScopeLevels(new ArrayList<>(allowedScopeLevels.stream()
                                                              .map(ScopeLevel::toString)
                                                              .map(RolesApiUtils::getAllowedScopeEnum)
                                                              .collect(Collectors.toList())));
    }
    rolesResponse.setDescription(responseDTO.getRole().getDescription());
    rolesResponse.setTags(responseDTO.getRole().getTags());
    rolesResponse.setScope(getRoleScope(responseDTO.getScope()));
    rolesResponse.setHarnessManaged(responseDTO.isHarnessManaged());
    rolesResponse.setCreated(responseDTO.getCreatedAt());
    rolesResponse.setUpdated(responseDTO.getLastModifiedAt());
    return rolesResponse;
  }

  public static AllowedScopeLevelsEnum getAllowedScopeEnum(String scopeLevels) {
    switch (scopeLevels) {
      case "account":
        return AllowedScopeLevelsEnum.ACCOUNT;
      case "organization":
        return AllowedScopeLevelsEnum.ORGANIZATION;
      case "project":
        return AllowedScopeLevelsEnum.PROJECT;
      default:
        return null;
    }
  }

  public static RoleScope getRoleScope(ScopeDTO scopeDTO) {
    if (scopeDTO == null) {
      return null;
    }
    RoleScope roleScope = new RoleScope();
    roleScope.setAccount(scopeDTO.getAccountIdentifier());
    roleScope.setOrg(scopeDTO.getOrgIdentifier());
    roleScope.setProject(scopeDTO.getProjectIdentifier());
    return roleScope;
  }
}
