/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roleassignments;

import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import static lombok.AccessLevel.PRIVATE;

import io.harness.accesscontrol.publicaccess.PublicAccessUtils;
import io.harness.accesscontrol.roleassignments.events.RoleAssignmentCreateEventV2;
import io.harness.accesscontrol.roleassignments.events.RoleAssignmentDeleteEventV2;
import io.harness.accesscontrol.roleassignments.events.RoleAssignmentUpdateEventV2;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDao;
import io.harness.accesscontrol.roleassignments.validator.RoleAssignmentValidationRequest;
import io.harness.accesscontrol.roleassignments.validator.RoleAssignmentValidationResult;
import io.harness.accesscontrol.roleassignments.validator.RoleAssignmentValidator;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.outbox.api.OutboxService;

import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import javax.validation.executable.ValidateOnExecution;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.transaction.support.TransactionTemplate;

@ValidateOnExecution
@FieldDefaults(level = PRIVATE, makeFinal = true)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.PL)
public class RoleAssignmentServiceImpl implements RoleAssignmentService {
  RoleAssignmentDao roleAssignmentDao;
  RoleAssignmentValidator roleAssignmentValidator;
  TransactionTemplate outboxTransactionTemplate;
  OutboxService outboxService;
  ScopeService scopeService;
  private static final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;

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
    if (!PublicAccessUtils.isPrincipalPublic(roleAssignment.getPrincipalIdentifier())
        && !result.getPrincipalValidationResult().isValid()) {
      throw new InvalidRequestException(result.getPrincipalValidationResult().getErrorMessage());
    }
    if (!PublicAccessUtils.isResourceGroupPublic(roleAssignment.getResourceGroupIdentifier())
        && !result.getResourceGroupValidationResult().isValid()) {
      throw new InvalidRequestException(result.getResourceGroupValidationResult().getErrorMessage());
    }
    if (!result.getRoleValidationResult().isValid()) {
      throw new InvalidRequestException(result.getRoleValidationResult().getErrorMessage());
    }
    return Failsafe.with(transactionRetryPolicy).get(() -> outboxTransactionTemplate.execute(status -> {
      RoleAssignment createdRoleAssignment = roleAssignmentDao.create(roleAssignment);
      Scope scope = scopeService.buildScopeFromScopeIdentifier(roleAssignment.getScopeIdentifier());
      outboxService.save(new RoleAssignmentCreateEventV2(createdRoleAssignment, scope.toString()));
      return createdRoleAssignment;
    }));
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
  public RoleAssignment update(RoleAssignment roleAssignmentUpdate) {
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
      return Failsafe.with(transactionRetryPolicy).get(() -> outboxTransactionTemplate.execute(status -> {
        RoleAssignment updatedRoleAssignment = roleAssignmentDao.update(roleAssignmentUpdate);
        Scope scope = scopeService.buildScopeFromScopeIdentifier(roleAssignment.getScopeIdentifier());
        outboxService.save(new RoleAssignmentUpdateEventV2(roleAssignment, updatedRoleAssignment, scope.toString()));
        return updatedRoleAssignment;
      }));
    }
    throw new InvalidRequestException(
        String.format("Could not find the role assignment in the scope %s", roleAssignmentUpdate.getScopeIdentifier()));
  }

  @Override
  public Optional<RoleAssignment> delete(String identifier, String scopeIdentifier) {
    return Failsafe.with(transactionRetryPolicy).get(status -> {
      Optional<RoleAssignment> deletedRoleAssignmentOptional = roleAssignmentDao.delete(identifier, scopeIdentifier);
      deletedRoleAssignmentOptional.ifPresent(roleAssignment -> {
        Scope scope = scopeService.buildScopeFromScopeIdentifier(roleAssignment.getScopeIdentifier());
        outboxService.save(new RoleAssignmentDeleteEventV2(deletedRoleAssignmentOptional.get(), scope.toString(), false));
      });
      return deletedRoleAssignmentOptional;
    });
  }

  @Override
  public long deleteMulti(RoleAssignmentFilter roleAssignmentFilter) {
    return (long) Failsafe.with(transactionRetryPolicy).get(() -> outboxTransactionTemplate.execute(status -> {
      List<RoleAssignment> roleAssignmentsDeleted = roleAssignmentDao.findAndRemove(roleAssignmentFilter);
      for (RoleAssignment roleAssignment : roleAssignmentsDeleted) {
        Scope scope = scopeService.buildScopeFromScopeIdentifier(roleAssignment.getScopeIdentifier());
        outboxService.save(new RoleAssignmentDeleteEventV2(roleAssignment, scope.toString(), true));
      }
      return roleAssignmentsDeleted.size();
    }));
  }

  @Override
  public List<RoleAssignment> deleteMulti(String scopeIdentifier, List<String> identifiers) {
    return Failsafe.with(transactionRetryPolicy).get(() -> outboxTransactionTemplate.execute(status -> {
      List<RoleAssignment> roleAssignmentsDeleted = roleAssignmentDao.findAndRemove(scopeIdentifier, identifiers);
      for (RoleAssignment roleAssignment : roleAssignmentsDeleted) {
        Scope scope = scopeService.buildScopeFromScopeIdentifier(roleAssignment.getScopeIdentifier());
        outboxService.save(new RoleAssignmentDeleteEventV2(roleAssignment, scope.toString(), false));
      }
      return roleAssignmentsDeleted;
    }));
  }

  @Override
  public RoleAssignmentValidationResult validate(RoleAssignmentValidationRequest validationRequest) {
    return roleAssignmentValidator.validate(validationRequest);
  }
}
