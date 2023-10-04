/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.scopes.harness;

import static io.harness.accesscontrol.common.filter.ManagedFilter.ONLY_CUSTOM;
import static io.harness.remote.client.NGRestUtils.getResponse;

import io.harness.accesscontrol.roleassignments.RoleAssignmentFilter;
import io.harness.accesscontrol.roleassignments.RoleAssignmentService;
import io.harness.accesscontrol.roles.RoleService;
import io.harness.accesscontrol.roles.filter.RoleFilter;
import io.harness.accesscontrol.scopes.HarnessScopeLevel;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.dto.OrganizationResponse;
import io.harness.ng.core.dto.ProjectResponse;
import io.harness.organization.remote.OrganizationClient;
import io.harness.project.remote.ProjectClient;
import io.harness.remote.client.CGRestUtils;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Optional;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PL)
@ValidateOnExecution
public class HarnessScopeServiceImpl implements HarnessScopeService {
  private final ProjectClient projectClient;
  private final OrganizationClient organizationClient;
  private final AccountClient accountClient;
  private final ScopeService scopeService;
  private final RoleAssignmentService roleAssignmentService;
  private final RoleService roleService;

  @Inject
  public HarnessScopeServiceImpl(@Named("PRIVILEGED") ProjectClient projectClient,
      @Named("PRIVILEGED") OrganizationClient organizationClient, @Named("PRIVILEGED") AccountClient accountClient,
      ScopeService scopeService, RoleAssignmentService roleAssignmentService, RoleService roleService) {
    this.projectClient = projectClient;
    this.organizationClient = organizationClient;
    this.accountClient = accountClient;
    this.scopeService = scopeService;
    this.roleAssignmentService = roleAssignmentService;
    this.roleService = roleService;
  }

  @Override
  public void sync(Scope scope) {
    HarnessScopeParams params = ScopeMapper.toParams(scope);
    boolean toBeDeleted = false;
    if (scope.getLevel() == HarnessScopeLevel.PROJECT) {
      try {
        Optional<ProjectResponse> response = getResponse(projectClient.getProject(
            params.getProjectIdentifier(), params.getAccountIdentifier(), params.getOrgIdentifier()));
        scope.setInstanceName(response.isPresent() ? response.get().getProject().getName() : null);
      } catch (InvalidRequestException e) {
        toBeDeleted =
            e.getMessage().equals(String.format("Project with orgIdentifier [%s] and identifier [%s] not found",
                scope.getParentScope().getInstanceId(), scope.getInstanceId()));
      }
    } else if (scope.getLevel() == HarnessScopeLevel.ORGANIZATION) {
      try {
        Optional<OrganizationResponse> response =
            getResponse(organizationClient.getOrganization(params.getOrgIdentifier(), params.getAccountIdentifier()));
        scope.setInstanceName(response.isPresent() ? response.get().getOrganization().getName() : null);
      } catch (InvalidRequestException e) {
        toBeDeleted =
            e.getMessage().equals(String.format("Organization with identifier [%s] not found", scope.getInstanceId()));
      }
    } else if (scope.getLevel() == HarnessScopeLevel.ACCOUNT) {
      try {
        AccountDTO response = CGRestUtils.getResponse(accountClient.getAccountDTO(params.getAccountIdentifier()));
        scope.setInstanceName(response.getName());

      } catch (InvalidRequestException e) {
        toBeDeleted = e.getMessage().equals("Account does not exist.");
      }
    }
    if (toBeDeleted) {
      log.info("Removing scope, {}", scope);
      deleteIfPresent(scope);
    } else {
      scopeService.getOrCreate(scope);
    }
  }

  @Override
  public void deleteIfPresent(Scope scope) {
    deleteRoleAssignments(scope);
    deleteRoles(scope);
    scopeService.deleteIfPresent(scope.toString());
  }

  private void deleteRoleAssignments(Scope scope) {
    RoleAssignmentFilter roleAssignmentFilter = RoleAssignmentFilter.builder().scopeFilter(scope.toString()).build();
    roleAssignmentService.deleteMulti(roleAssignmentFilter);
  }

  private void deleteRoles(Scope scope) {
    RoleFilter roleFilter = RoleFilter.builder().scopeIdentifier(scope.toString()).managedFilter(ONLY_CUSTOM).build();
    roleService.deleteMulti(roleFilter);
  }
}
