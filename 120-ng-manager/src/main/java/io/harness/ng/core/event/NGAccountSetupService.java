/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.event;

import static io.harness.NGConstants.DEFAULT_ORG_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_PROJECT_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_PROJECT_NAME;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.ng.core.invites.mapper.RoleBindingMapper.getDefaultResourceGroupIdentifierForAdmins;
import static io.harness.ng.core.invites.mapper.RoleBindingMapper.getManagedAdminRole;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

import io.harness.accesscontrol.AccessControlAdminClient;
import io.harness.accesscontrol.principals.PrincipalDTO;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentCreateRequestDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentDTO;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.PageResponse;
import io.harness.beans.Scope;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.GeneralException;
import io.harness.ff.FeatureFlagService;
import io.harness.ng.NextGenConfiguration;
import io.harness.ng.accesscontrol.migrations.models.AccessControlMigration;
import io.harness.ng.accesscontrol.migrations.services.AccessControlMigrationService;
import io.harness.ng.core.AccountOrgProjectValidator;
import io.harness.ng.core.accountsetting.services.NGAccountSettingService;
import io.harness.ng.core.dto.OrganizationDTO;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.services.ProjectService;
import io.harness.ng.core.user.UserInfo;
import io.harness.ng.core.user.UserMembershipUpdateSource;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.remote.client.NGRestUtils;
import io.harness.remote.client.RestClientUtils;
import io.harness.user.remote.UserClient;
import io.harness.utils.CryptoUtils;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;

@OwnedBy(PL)
@Singleton
@Slf4j
public class NGAccountSetupService {
  private final OrganizationService organizationService;
  private final ProjectService projectService;
  private final AccountOrgProjectValidator accountOrgProjectValidator;
  private final AccessControlAdminClient accessControlAdminClient;
  private final NgUserService ngUserService;
  private final UserClient userClient;
  private final AccessControlMigrationService accessControlMigrationService;
  private final HarnessSMManager harnessSMManager;
  private final CIDefaultEntityManager ciDefaultEntityManager;
  private final boolean shouldAssignAdmins;
  private final NGAccountSettingService accountSettingService;
  private final FeatureFlagService featureFlagService;

  @Inject
  public NGAccountSetupService(OrganizationService organizationService,
      AccountOrgProjectValidator accountOrgProjectValidator,
      @Named("PRIVILEGED") AccessControlAdminClient accessControlAdminClient, NgUserService ngUserService,
      UserClient userClient, AccessControlMigrationService accessControlMigrationService,
      HarnessSMManager harnessSMManager, CIDefaultEntityManager ciDefaultEntityManager,
      NextGenConfiguration nextGenConfiguration, NGAccountSettingService accountSettingService,
      ProjectService projectService, FeatureFlagService featureFlagService) {
    this.organizationService = organizationService;
    this.accountOrgProjectValidator = accountOrgProjectValidator;
    this.accessControlAdminClient = accessControlAdminClient;
    this.ngUserService = ngUserService;
    this.userClient = userClient;
    this.accessControlMigrationService = accessControlMigrationService;
    this.harnessSMManager = harnessSMManager;
    this.ciDefaultEntityManager = ciDefaultEntityManager;
    this.shouldAssignAdmins =
        nextGenConfiguration.getAccessControlAdminClientConfiguration().getMockAccessControlService().equals(
            Boolean.FALSE);
    this.accountSettingService = accountSettingService;
    this.projectService = projectService;
    this.featureFlagService = featureFlagService;
  }

  public void setupAccountForNG(String accountIdentifier) {
    if (!accountOrgProjectValidator.isPresent(accountIdentifier, null, null)) {
      log.info(String.format(
          "Account with accountIdentifier %s not found, skipping creation of Default Organization", accountIdentifier));
      return;
    }

    Organization defaultOrg = createDefaultOrg(accountIdentifier);
    if (featureFlagService.isGlobalEnabled(FeatureName.CREATE_DEFAULT_PROJECT)) {
      Project defaultProject = createDefaultProject(accountIdentifier, defaultOrg.getIdentifier());
      setupAllLevelRBAC(defaultOrg.getAccountIdentifier(), defaultOrg.getIdentifier(), defaultProject.getIdentifier());
    } else {
      setupRBAC(defaultOrg.getAccountIdentifier(), defaultOrg.getIdentifier());
    }
    log.info("[NGAccountSetupService]: Creating global SM for account{}", accountIdentifier);
    harnessSMManager.createGlobalSecretManager();
    log.info("[NGAccountSetupService]: Global SM Created Successfully for account{}", accountIdentifier);
    harnessSMManager.createHarnessSecretManager(accountIdentifier, null, null);
    ciDefaultEntityManager.createCIDefaultEntities(accountIdentifier, null, null);
    accountSettingService.setUpDefaultAccountSettings(accountIdentifier);
  }

  private Organization createDefaultOrg(String accountIdentifier) {
    Optional<Organization> organization = organizationService.get(accountIdentifier, DEFAULT_ORG_IDENTIFIER);
    if (organization.isPresent()) {
      log.info(String.format("Default Organization for account %s already present", accountIdentifier));
      return organization.get();
    }
    OrganizationDTO createOrganizationDTO = OrganizationDTO.builder().build();
    createOrganizationDTO.setIdentifier(DEFAULT_ORG_IDENTIFIER);
    createOrganizationDTO.setName("default");
    createOrganizationDTO.setTags(emptyMap());
    createOrganizationDTO.setDescription("Default Organization");
    createOrganizationDTO.setHarnessManaged(true);
    return organizationService.create(accountIdentifier, createOrganizationDTO);
  }

  private Project createDefaultProject(String accountIdentifier, String organizationIdentifier) {
    Optional<Project> project =
        projectService.get(accountIdentifier, organizationIdentifier, DEFAULT_PROJECT_IDENTIFIER);
    if (project.isPresent()) {
      log.info(String.format(
          "Default Project for account %s organization %s already present", accountIdentifier, organizationIdentifier));
      return project.get();
    }
    ProjectDTO createProjectDTO = ProjectDTO.builder().build();
    createProjectDTO.setIdentifier(DEFAULT_PROJECT_IDENTIFIER);
    createProjectDTO.setName(DEFAULT_PROJECT_NAME);
    return projectService.create(accountIdentifier, organizationIdentifier, createProjectDTO);
  }

  private void setupAllLevelRBAC(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    setupRBAC(accountIdentifier, orgIdentifier);

    Collection<UserInfo> cgUsers = getCGUsers(accountIdentifier);
    Collection<String> cgAdmins =
        cgUsers.stream().filter(UserInfo::isAdmin).map(UserInfo::getUuid).collect(Collectors.toSet());

    Scope projectScope = Scope.of(accountIdentifier, orgIdentifier, projectIdentifier);
    if (!hasAdmin(projectScope)) {
      cgAdmins.forEach(user -> upsertUserMembership(projectScope, user));
      assignAdminRoleToUsers(projectScope, cgAdmins);
      if (shouldAssignAdmins && !hasAdmin(projectScope)) {
        throw new GeneralException(String.format("No Admin could be assigned in scope %s", projectScope));
      }
      accessControlMigrationService.save(AccessControlMigration.builder()
                                             .accountIdentifier(accountIdentifier)
                                             .orgIdentifier(orgIdentifier)
                                             .projectIdentifier(projectIdentifier)
                                             .build());
    }
  }

  private void setupRBAC(String accountIdentifier, String orgIdentifier) {
    Collection<UserInfo> cgUsers = getCGUsers(accountIdentifier);
    Collection<String> cgAdmins =
        cgUsers.stream().filter(UserInfo::isAdmin).map(UserInfo::getUuid).collect(Collectors.toSet());

    Scope accountScope = Scope.of(accountIdentifier, null, null);
    if (!hasAdmin(accountScope)) {
      cgUsers.forEach(user -> upsertUserMembership(accountScope, user.getUuid()));
      assignAdminRoleToUsers(accountScope, cgAdmins);
      if (shouldAssignAdmins && !hasAdmin(accountScope)) {
        throw new GeneralException(String.format("No Admin could be assigned in scope %s", accountScope));
      }
      accessControlMigrationService.save(AccessControlMigration.builder().accountIdentifier(accountIdentifier).build());
    }

    Scope orgScope = Scope.of(accountIdentifier, orgIdentifier, null);
    if (!hasAdmin(orgScope)) {
      cgAdmins.forEach(user -> upsertUserMembership(orgScope, user));
      assignAdminRoleToUsers(orgScope, cgAdmins);
      if (shouldAssignAdmins && !hasAdmin(orgScope)) {
        throw new GeneralException(String.format("No Admin could be assigned in scope %s", orgScope));
      }
      accessControlMigrationService.save(
          AccessControlMigration.builder().accountIdentifier(accountIdentifier).orgIdentifier(orgIdentifier).build());
    }
  }

  private boolean hasAdmin(Scope scope) {
    return !isEmpty(ngUserService.listUsersHavingRole(scope, getManagedAdminRole(scope)));
  }

  private void assignAdminRoleToUsers(Scope scope, Collection<String> users) {
    createRoleAssignments(scope,
        buildRoleAssignments(users, getManagedAdminRole(scope), getDefaultResourceGroupIdentifierForAdmins(scope)));
  }

  private List<RoleAssignmentDTO> buildRoleAssignments(
      Collection<String> userIds, String roleIdentifier, String resourceGroupIdentifier) {
    return userIds.stream()
        .map(userId
            -> RoleAssignmentDTO.builder()
                   .disabled(false)
                   .identifier("role_assignment_".concat(CryptoUtils.secureRandAlphaNumString(20)))
                   .roleIdentifier(roleIdentifier)
                   .resourceGroupIdentifier(resourceGroupIdentifier)
                   .principal(PrincipalDTO.builder().identifier(userId).type(PrincipalType.USER).build())
                   .build())
        .collect(Collectors.toList());
  }

  private void createRoleAssignments(Scope scope, List<RoleAssignmentDTO> roleAssignments) {
    List<List<RoleAssignmentDTO>> batchedRoleAssignments = Lists.partition(roleAssignments, 2);
    for (List<RoleAssignmentDTO> batchOfRoleAssignment : batchedRoleAssignments) {
      NGRestUtils.getResponse(accessControlAdminClient.createMultiRoleAssignment(scope.getAccountIdentifier(),
          scope.getOrgIdentifier(), scope.getProjectIdentifier(), false,
          RoleAssignmentCreateRequestDTO.builder().roleAssignments(batchOfRoleAssignment).build()));
    }
  }

  private void upsertUserMembership(Scope scope, String userId) {
    try {
      ngUserService.addUserToScope(userId,
          Scope.builder()
              .accountIdentifier(scope.getAccountIdentifier())
              .orgIdentifier(scope.getOrgIdentifier())
              .projectIdentifier(scope.getProjectIdentifier())
              .build(),
          emptyList(), emptyList(), UserMembershipUpdateSource.SYSTEM);
    } catch (DuplicateKeyException | DuplicateFieldException duplicateException) {
      // ignore
    }
  }

  private Collection<UserInfo> getCGUsers(String accountId) {
    Set<UserInfo> users = new HashSet<>();
    Stopwatch stopwatch = Stopwatch.createStarted();
    while (users.isEmpty() && stopwatch.elapsed(TimeUnit.SECONDS) <= 5) {
      // From CG side, account setup event is fired before setting up users in the account first. To handle that, we are
      // waiting up to 5 seconds for users to get setup correctly on CG side.
      sleep();
      int offset = 0;
      int limit = 500;
      int maxIterations = 50;
      while (maxIterations > 0) {
        PageResponse<UserInfo> usersPage = RestClientUtils.getResponse(
            userClient.list(accountId, String.valueOf(offset), String.valueOf(limit), null, true));
        if (isEmpty(usersPage.getResponse())) {
          break;
        }
        users.addAll(usersPage.getResponse());
        maxIterations--;
        offset += limit;
      }
    }
    return users;
  }

  private void sleep() {
    try {
      TimeUnit.MILLISECONDS.sleep(500);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      log.warn("Thread Interrupted", ex);
    }
  }
}
