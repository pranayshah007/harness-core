/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roleassignments;

import static lombok.AccessLevel.PRIVATE;

import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDao;
import io.harness.accesscontrol.roleassignments.validator.RoleAssignmentValidationRequest;
import io.harness.accesscontrol.roleassignments.validator.RoleAssignmentValidationResult;
import io.harness.accesscontrol.roleassignments.validator.RoleAssignmentValidator;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;

import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import javax.validation.executable.ValidateOnExecution;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@ValidateOnExecution
@FieldDefaults(level = PRIVATE, makeFinal = true)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.PL)
public class RoleAssignmentServiceImpl implements RoleAssignmentService {
  RoleAssignmentDao roleAssignmentDao;
  RoleAssignmentValidator roleAssignmentValidator;

  @Override
  public RoleAssignment create(RoleAssignment roleAssignment) {
    RoleAssignmentValidationResult result = roleAssignmentValidator.validate(RoleAssignmentValidationRequest.builder()
                                                                                 .roleAssignment(roleAssignment)
                                                                                 .validateScope(true)
                                                                                 .validatePrincipal(true)
                                                                                 .validateRole(true)
                                                                                 .validateResourceGroup(true)
                                                                                 .build());
    if (!result.getScopeValidationResult().isValid()) {
      throw new InvalidRequestException(result.getScopeValidationResult().getErrorMessage());
    }
    if (!result.getPrincipalValidationResult().isValid()) {
      throw new InvalidRequestException(result.getPrincipalValidationResult().getErrorMessage());
    }
    if (!result.getResourceGroupValidationResult().isValid()) {
      throw new InvalidRequestException(result.getResourceGroupValidationResult().getErrorMessage());
    }
    if (!result.getRoleValidationResult().isValid()) {
      throw new InvalidRequestException(result.getRoleValidationResult().getErrorMessage());
    }
    return roleAssignmentDao.create(roleAssignment);
  }

  @Override
  public PageResponse<RoleAssignment> list(PageRequest pageRequest, RoleAssignmentFilter roleAssignmentFilter) {
    return list(pageRequest, roleAssignmentFilter, true);
  }

  @Override
  public PageResponse<RoleAssignment> list(
      PageRequest pageRequest, RoleAssignmentFilter roleAssignmentFilter, boolean hideInternal) {
    return roleAssignmentDao.list(pageRequest, roleAssignmentFilter, hideInternal);
  }

  @Override
  public Optional<RoleAssignment> get(String identifier, String scopeIdentifier) {
    return roleAssignmentDao.get(identifier, scopeIdentifier);
  }

  @Override
  public RoleAssignmentUpdateResult update(RoleAssignment roleAssignmentUpdate) {
    Optional<RoleAssignment> currentRoleAssignmentOptional =
        get(roleAssignmentUpdate.getIdentifier(), roleAssignmentUpdate.getScopeIdentifier());
    if (currentRoleAssignmentOptional.isPresent()) {
      RoleAssignment roleAssignment = currentRoleAssignmentOptional.get();
      if (!roleAssignmentUpdate.getResourceGroupIdentifier().equals(roleAssignment.getResourceGroupIdentifier())) {
        throw new InvalidRequestException("Cannot change resource group in the role assignment");
      }
      if (!roleAssignmentUpdate.getPrincipalIdentifier().equals(roleAssignment.getPrincipalIdentifier())) {
        throw new InvalidRequestException("Cannot change principal in the role assignment");
      }
      if (!roleAssignmentUpdate.getPrincipalType().equals(roleAssignment.getPrincipalType())) {
        throw new InvalidRequestException("Cannot change principal type in the role assignment");
      }
      if (roleAssignmentUpdate.getPrincipalScopeLevel() != null
          && !roleAssignmentUpdate.getPrincipalScopeLevel().equals(roleAssignment.getPrincipalScopeLevel())
          && roleAssignment.getPrincipalScopeLevel() != null) {
        throw new InvalidRequestException("Cannot change principal scope in the role assignment");
      }
      if (!roleAssignmentUpdate.getRoleIdentifier().equals(roleAssignment.getRoleIdentifier())) {
        throw new InvalidRequestException("Cannot change role in the role assignment");
      }
      roleAssignmentUpdate.setManaged(roleAssignment.isManaged());
      roleAssignmentUpdate.setVersion(roleAssignment.getVersion());
      RoleAssignment updatedRoleAssignment = roleAssignmentDao.update(roleAssignmentUpdate);
      return RoleAssignmentUpdateResult.builder()
          .updatedRoleAssignment(updatedRoleAssignment)
          .originalRoleAssignment(roleAssignment)
          .build();
    }
    throw new InvalidRequestException(
        String.format("Could not find the role assignment in the scope %s", roleAssignmentUpdate.getScopeIdentifier()));
  }

  @Override
  public Optional<RoleAssignment> delete(String identifier, String scopeIdentifier) {
    return roleAssignmentDao.delete(identifier, scopeIdentifier);
  }

  @Override
  public long deleteMulti(RoleAssignmentFilter roleAssignmentFilter) {
    return roleAssignmentDao.deleteMulti(roleAssignmentFilter);
  }

  @Override
  public long deleteMulti(String scopeIdentifier, List<String> identifiers) {
    return roleAssignmentDao.deleteMulti(scopeIdentifier, identifiers);
  }

  @Override
  public RoleAssignmentValidationResult validate(RoleAssignmentValidationRequest validationRequest) {
    return roleAssignmentValidator.validate(validationRequest);
  }
}
