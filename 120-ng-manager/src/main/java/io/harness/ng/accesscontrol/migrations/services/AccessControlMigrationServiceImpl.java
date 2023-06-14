/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.accesscontrol.migrations.services;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.ng.core.invites.mapper.RoleBindingMapper.getDefaultResourceGroupIdentifierForAdmins;
import static io.harness.ng.core.invites.mapper.RoleBindingMapper.getManagedAdminRole;

import static java.util.Collections.emptyList;

import io.harness.accesscontrol.AccessControlAdminClient;
import io.harness.accesscontrol.principals.PrincipalDTO;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentCreateRequestDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentResponseDTO;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.PageResponse;
import io.harness.beans.Scope;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.GeneralException;
import io.harness.ng.accesscontrol.migrations.dao.AccessControlMigrationDAO;
import io.harness.ng.accesscontrol.migrations.models.AccessControlMigration;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Organization.OrganizationKeys;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.entities.Project.ProjectKeys;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.services.ProjectService;
import io.harness.ng.core.user.UserInfo;
import io.harness.ng.core.user.UserMembershipUpdateSource;
import io.harness.ng.core.user.remote.dto.UserMetadataDTO;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.remote.client.CGRestUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.user.remote.UserClient;
import io.harness.utils.CryptoUtils;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(HarnessTeam.PL)
@Slf4j
public class AccessControlMigrationServiceImpl implements AccessControlMigrationService {
  public static final int BATCH_SIZE = 5;
  private final AccessControlMigrationDAO accessControlMigrationDAO;
  private final ProjectService projectService;
  private final OrganizationService organizationService;
  private final AccessControlAdminClient accessControlAdminClient;
  private final UserClient userClient;
  private final NgUserService ngUserService;
  private final ExecutorService executorService = Executors.newFixedThreadPool(5);

  @Inject
  public AccessControlMigrationServiceImpl(AccessControlMigrationDAO accessControlMigrationDAO,
      ProjectService projectService, OrganizationService organizationService,
      @Named("PRIVILEGED") AccessControlAdminClient accessControlAdminClient, UserClient userClient,
      NgUserService ngUserService) {
    this.accessControlMigrationDAO = accessControlMigrationDAO;
    this.projectService = projectService;
    this.organizationService = organizationService;
    this.accessControlAdminClient = accessControlAdminClient;
    this.userClient = userClient;
    this.ngUserService = ngUserService;
  }

  @Override
  public void save(AccessControlMigration accessControlMigration) {
    try {
      accessControlMigrationDAO.save(accessControlMigration);
    } catch (DuplicateFieldException | DuplicateKeyException duplicateException) {
      // Ignore
    }
  }

  @Override
  public boolean isMigrated(Scope scope) {
    return accessControlMigrationDAO.isMigrated(scope);
  }

  @Override
  public void migrate(String accountIdentifier) {
    migrateInternal(Scope.of(accountIdentifier, null, null));
    for (String orgIdentifier : getOrganizations(accountIdentifier)) {
      migrateInternal(Scope.of(accountIdentifier, orgIdentifier, null));
      for (String projectIdentifier : getProjects(accountIdentifier, orgIdentifier)) {
        migrateInternal(Scope.of(accountIdentifier, orgIdentifier, projectIdentifier));
      }
    }
  }

  private List<UserInfo> getUsers(String accountId) {
    int offset = 0;
    int limit = 500;
    int maxIterations = 50;
    Set<UserInfo> users = new HashSet<>();
    while (maxIterations > 0) {
      PageResponse<UserInfo> usersPage = CGRestUtils.getResponse(
          userClient.list(accountId, String.valueOf(offset), String.valueOf(limit), null, true));
      if (isEmpty(usersPage.getResponse())) {
        break;
      }
      users.addAll(usersPage.getResponse());
      maxIterations--;
      offset += limit;
    }
    return new ArrayList<>(users);
  }

  private long createRoleAssignments(Scope scope, boolean managed, List<RoleAssignmentDTO> roleAssignments) {
    roleAssignments = dedupRoleAssignments(roleAssignments);

    List<List<RoleAssignmentDTO>> batchedRoleAssignments = Lists.partition(roleAssignments, BATCH_SIZE);
    List<Future<List<RoleAssignmentResponseDTO>>> futures = new ArrayList<>();
    batchedRoleAssignments.forEach(batch
        -> futures.add(executorService.submit(
            ()
                -> NGRestUtils.getResponse(accessControlAdminClient.createMultiRoleAssignment(
                    scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), managed,
                    RoleAssignmentCreateRequestDTO.builder().roleAssignments(batch).build())))));

    long createdRoleAssignments = 0;
    for (Future<List<RoleAssignmentResponseDTO>> future : futures) {
      try {
        createdRoleAssignments += future.get().size();
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new GeneralException(
            String.format("Error occurred while trying to create role assignments for scope : %s", scope), ex);
      } catch (ExecutionException ex) {
        throw new GeneralException(
            String.format("Error occurred while trying to create role assignments for scope : %s", scope),
            ex.getCause());
      }
    }
    return createdRoleAssignments;
  }

  private List<RoleAssignmentDTO> dedupRoleAssignments(List<RoleAssignmentDTO> roleAssignments) {
    Map<RoleAssignmentKey, RoleAssignmentDTO> map = new HashMap<>();
    for (RoleAssignmentDTO roleAssignment : roleAssignments) {
      RoleAssignmentKey key = new RoleAssignmentKey(roleAssignment.getResourceGroupIdentifier(),
          roleAssignment.getRoleIdentifier(), roleAssignment.getPrincipal().getIdentifier(),
          roleAssignment.getPrincipal().getType(), roleAssignment.isDisabled());
      map.put(key, roleAssignment);
    }
    return new ArrayList<>(map.values());
  }

  @Value
  private static class RoleAssignmentKey {
    String resourceGroupIdentifier;
    String roleIdentifier;
    String principalIdentifier;
    PrincipalType principalType;
    boolean disabled;
  }

  private void migrateInternal(Scope scope) {
    if (isMigrated(scope)) {
      log.info("Scope {} already migrated", scope);
      return;
    }
    log.info("Access control migration started for scope : {}", scope);
    Stopwatch stopwatch = Stopwatch.createStarted();

    try {
      assignViewerRoleToUsers(scope);
      if (!hasAdmin(scope)) {
        assignAdminRoleToUsers(scope);
      }

      if (!hasAdmin(scope)) {
        assignAdminAndViewerRoleToCGUsers(scope);
      }

      if (!hasAdmin(scope)) {
        log.error(String.format("Access control migration failed for scope : %s", scope.toString()));
        return;
      }

      long durationInSeconds = stopwatch.elapsed(TimeUnit.SECONDS);
      log.info(
          "Access control migration finished for scope : {} in {} seconds", scope, stopwatch.elapsed(TimeUnit.SECONDS));

      save(AccessControlMigration.builder()
               .accountIdentifier(scope.getAccountIdentifier())
               .orgIdentifier(scope.getOrgIdentifier())
               .projectIdentifier(scope.getProjectIdentifier())
               .durationInSeconds(durationInSeconds)
               .build());
    } catch (Exception ex) {
      log.error(String.format("Access control migration failed for scope : %s", scope.toString()), ex);
    }
  }

  private void assignViewerRoleToUsers(Scope scope) {
    Set<String> users = new HashSet<>(getUsersInScope(scope));
    if (users.isEmpty()) {
      return;
    }
    users.forEach(userId -> upsertUserMembership(scope, userId));
  }

  private boolean hasAdmin(Scope scope) {
    List<UserMetadataDTO> admins = ngUserService.listUsersHavingRole(scope, getManagedAdminRole(scope));
    log.info("Admins in scope {} are {}", scope, admins == null ? emptyList() : admins);
    return !isEmpty(admins);
  }

  private void assignAdminRoleToUsers(Scope scope) {
    Set<String> users = new HashSet<>(getUsersInScope(scope));
    if (users.isEmpty()) {
      return;
    }

    log.info("Created {} NON-MANAGED role assignments from UserMembership for scope: {}",
        createRoleAssignments(scope, false,
            buildRoleAssignments(users, getManagedAdminRole(scope), getDefaultResourceGroupIdentifierForAdmins(scope))),
        scope);
  }

  private void assignAdminAndViewerRoleToCGUsers(Scope scope) {
    Set<String> currentGenUsers =
        getUsers(scope.getAccountIdentifier()).stream().map(UserInfo::getUuid).collect(Collectors.toSet());
    log.info("Number of CG Users : {}", currentGenUsers.size());
    if (currentGenUsers.isEmpty()) {
      return;
    }

    currentGenUsers.forEach(userId -> upsertUserMembership(scope, userId));

    log.info("Created {} NON-MANAGED role assignments from CG Users for scope: {}",
        createRoleAssignments(scope, false,
            buildRoleAssignments(
                currentGenUsers, getManagedAdminRole(scope), getDefaultResourceGroupIdentifierForAdmins(scope))),
        scope);
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

  private List<String> getOrganizations(String accountIdentifier) {
    List<String> orgs =
        organizationService
            .list(Criteria.where(OrganizationKeys.accountIdentifier)
                      .is(accountIdentifier)
                      .and(OrganizationKeys.deleted)
                      .ne(true))
            .stream()
            .map(Organization::getIdentifier)
            .collect(Collectors.toCollection(ArrayList::new)); // this has been done explicitly so that shuffle does not
                                                               // throw UnsupportedOperationException
    Collections.shuffle(orgs);
    return orgs;
  }

  private List<String> getProjects(String accountIdentifier, String orgIdentifier) {
    List<String> projects =
        projectService
            .list(Criteria.where(ProjectKeys.accountIdentifier)
                      .is(accountIdentifier)
                      .and(ProjectKeys.deleted)
                      .ne(true)
                      .and(ProjectKeys.orgIdentifier)
                      .is(orgIdentifier))
            .stream()
            .map(Project::getIdentifier)
            .collect(Collectors.toCollection(ArrayList::new)); // this has been done explicitly so that shuffle does not
                                                               // throw UnsupportedOperationException
    Collections.shuffle(projects);
    return projects;
  }

  private List<String> getUsersInScope(Scope scope) {
    return ngUserService.listUserIds(scope);
  }
}
