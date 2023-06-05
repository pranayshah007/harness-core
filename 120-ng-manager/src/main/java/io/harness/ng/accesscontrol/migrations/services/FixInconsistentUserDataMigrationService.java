/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.accesscontrol.migrations.services;

import static io.harness.NGConstants.ACCOUNT_ADMIN_ROLE;
import static io.harness.authorization.AuthorizationServiceHeader.NG_MANAGER;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.user.UserMembershipUpdateSource.SYSTEM;
import static io.harness.ng.core.user.service.impl.NgUserServiceImpl.DEFAULT_PAGE_SIZE;
import static io.harness.remote.client.NGRestUtils.getResponse;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import static java.util.Collections.emptyList;

import io.harness.accesscontrol.AccessControlAdminClient;
import io.harness.accesscontrol.principals.PrincipalDTO;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentCreateRequestDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentFilterDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentResponseDTO;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.Scope;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.user.NGRemoveUserFilter;
import io.harness.ng.core.user.UserInfo;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.ng.core.user.entities.UserMembership;
import io.harness.ng.core.user.entities.UserMetadata;
import io.harness.ng.core.user.remote.dto.UserMetadataDTO;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.ng.core.spring.UserGroupRepository;
import io.harness.repositories.user.spring.UserMembershipRepository;
import io.harness.repositories.user.spring.UserMetadataRepository;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;
import io.harness.user.remote.UserFilterNG;
import io.harness.utils.featureflaghelper.NGFeatureFlagHelperService;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.jetbrains.annotations.NotNull;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.util.CloseableIterator;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@OwnedBy(HarnessTeam.PL)
public class FixInconsistentUserDataMigrationService implements Runnable {
  public static final int TIME_GAP_BETWEEN_TWO_ACCOUNTS_IN_MILLIS = 1000;
  private final NgUserService ngUserService;
  private final UserGroupService userGroupService;
  private final PersistentLocker persistentLocker;
  private final AccessControlAdminClient accessControlAdminClient;
  private final NGFeatureFlagHelperService ngFeatureFlagHelperService;
  private final UserMetadataRepository userMetadataRepository;
  private final UserMembershipRepository userMembershipRepository;
  private final UserGroupRepository userGroupRepository;
  private final TransactionTemplate transactionTemplate;
  private static final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;
  private static final String DEBUG_MESSAGE = "FixInconsistentUserDataMigrationService: ";
  private static final String LOCK_NAME = "FixInconsistentUserDataMigrationService";

  @Inject
  public FixInconsistentUserDataMigrationService(NgUserService ngUserService, UserGroupService userGroupService,
      PersistentLocker persistentLocker, AccessControlAdminClient accessControlAdminClient,
      NGFeatureFlagHelperService ngFeatureFlagHelperService, UserMetadataRepository userMetadataRepository,
      UserMembershipRepository userMembershipRepository, UserGroupRepository userGroupRepository,
      TransactionTemplate transactionTemplate) {
    this.ngUserService = ngUserService;
    this.userGroupService = userGroupService;
    this.persistentLocker = persistentLocker;
    this.accessControlAdminClient = accessControlAdminClient;
    this.ngFeatureFlagHelperService = ngFeatureFlagHelperService;
    this.userMetadataRepository = userMetadataRepository;
    this.userMembershipRepository = userMembershipRepository;
    this.userGroupRepository = userGroupRepository;
    this.transactionTemplate = transactionTemplate;
  }

  @Override
  public void run() {
    log.info(DEBUG_MESSAGE + "Started running...");
    log.info(DEBUG_MESSAGE + "Trying to acquire lock...");
    try (AcquiredLock<?> lock =
             persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(LOCK_NAME, Duration.ofSeconds(5))) {
      if (lock == null) {
        log.info(DEBUG_MESSAGE + "failed to acquire lock");
        return;
      }
      try {
        SecurityContextBuilder.setContext(new ServicePrincipal(NG_MANAGER.getServiceId()));
        log.info(DEBUG_MESSAGE + "Setting SecurityContext completed.");
        execute();
      } catch (Exception ex) {
        log.error(DEBUG_MESSAGE + " unexpected error occurred while Setting SecurityContext", ex);
      } finally {
        SecurityContextBuilder.unsetCompleteContext();
        log.info(DEBUG_MESSAGE + "Unsetting SecurityContext completed.");
      }
      log.info(DEBUG_MESSAGE + "Stopped running...");
    } catch (Exception ex) {
      log.error(DEBUG_MESSAGE + " failed to acquire lock", ex);
    }
  }

  private void execute() {
    try {
      Set<String> accountsToProcess =
          ngFeatureFlagHelperService.getFeatureFlagEnabledAccountIds(FeatureName.PL_FIX_INCONSISTENT_USER_DATA.name());
      if (isEmpty(accountsToProcess)) {
        log.info(DEBUG_MESSAGE + "Skipping! No account found with FF PL_FIX_INCONSISTENT_USER_DATA enabled");
      } else {
        log.info(DEBUG_MESSAGE
            + String.format(
                "Fetched %s accounts with FF PL_FIX_INCONSISTENT_USER_DATA enabled", accountsToProcess.size()));
        migrate(accountsToProcess);
      }
    } catch (Exception ex) {
      log.error(DEBUG_MESSAGE + " Fetching all accounts failed : ", ex);
    }

    log.info(DEBUG_MESSAGE + " Execution completed.");
  }

  private void migrate(Set<String> accountsToProcess) {
    /**
     * Cleanup of UserMetaData if this uuid is not found in CG.
     * Should be a daily job till we get rid of UserMetaData
     * Fetch from UserMetaData and search in userMembershipV2
     * Do this ONLY after data has been fixed
     */

    for (String accountIdentifier : accountsToProcess) {
      try {
        log.info(DEBUG_MESSAGE + "Migration starts for account {}", accountIdentifier);

        List<UserMetadataDTO> userMetadataDTOList = getNGUserMetadataDTOS(accountIdentifier);
        Map<String, String> NGUserEmailToCGUserIdMap =
            getNGUserEmailToCGUserIdMap(accountIdentifier, userMetadataDTOList);

        for (UserMetadataDTO userMetadataDTO : userMetadataDTOList) {
          String ngUserEmail = userMetadataDTO.getEmail();
          String ngUserId = userMetadataDTO.getUuid();
          String cgUserId = NGUserEmailToCGUserIdMap.get(ngUserEmail);

          if (isEmpty(cgUserId)) {
            // todo: shashank: review this
            // delete entry from UserMetaData if email of NG DB user isa not found in CG DB user.
            try {
              userMetadataRepository.deleteByEmail(ngUserEmail);
            } catch (Exception exception) {
              log.info(DEBUG_MESSAGE + "Failed to delete user: {} for account {}", ngUserEmail, accountIdentifier);
            }
          } else {
            if (ngUserId.equals(cgUserId)) {
              // skip processing this user as all of its data is already consistent
              log.info(
                  DEBUG_MESSAGE + "Skipping! User with email : {} and id: {} is already in consistent state with CG DB",
                  ngUserEmail, ngUserId);
            } else {
              // fix data for this user to make it consistent between NG, CG and Access Control DB.
              processUserForAccount(userMetadataDTO, ngUserId, cgUserId, accountIdentifier);
            }
          }
        }
        log.info(DEBUG_MESSAGE + "Migration completed for account {}", accountIdentifier);
        Thread.sleep(TIME_GAP_BETWEEN_TWO_ACCOUNTS_IN_MILLIS);
      } catch (Exception exception) {
        log.error(DEBUG_MESSAGE + "Failed to process users for account {} ", accountIdentifier, exception);
      }
    }
  }

  private List<UserMetadataDTO> getNGUserMetadataDTOS(String accountIdentifier) {
    List<String> ngUserIds = fetchUsersFromUserMemberShipForAccount(accountIdentifier);
    List<UserMetadataDTO> userMetadataDTOList = ngUserService.getUserMetadata(ngUserIds);
    return userMetadataDTOList;
  }

  @NotNull
  private Map<String, String> getNGUserEmailToCGUserIdMap(
      String accountIdentifier, List<UserMetadataDTO> userMetadataDTOList) {
    List<String> ngUserEmails = userMetadataDTOList.stream().map(u -> u.getEmail()).collect(Collectors.toList());
    List<UserInfo> cgUserInfoList =
        ngUserService.listCurrentGenUsers(accountIdentifier, UserFilterNG.builder().emailIds(ngUserEmails).build());

    Map<String, String> ngUserEmailTocgUserIdMap = new HashMap<>();
    if (isNotEmpty(cgUserInfoList)) {
      ngUserEmailTocgUserIdMap =
          cgUserInfoList.stream().collect(Collectors.toMap(cgUser -> cgUser.getEmail(), cgUser -> cgUser.getUuid()));
    }
    return ngUserEmailTocgUserIdMap;
  }

  private void processUserForAccount(
      UserMetadataDTO userMetadataDTO, String ngUserId, String cgUserId, String accountIdentifier) {
    boolean result = false;

    result = deleteOldUserMetaDataAndCreateNew(userMetadataDTO, cgUserId, accountIdentifier);

    if (result) {
      rectifyDataForUserWithNewCGUserId(userMetadataDTO, ngUserId, cgUserId, accountIdentifier);
    }
  }

  /**
   * - Fetch all entries from NG:userMembershipV2 for ng_uuid
   * - Add corresponding new entries to userMembershipV2 with cg_uuid
   * - This will create ACLs for these newmemberships created for the new cg userId
   */
  private boolean rectifyDataForUserWithNewCGUserId(
      UserMetadataDTO userMetadataDTO, String ngUserId, String cgUserId, String accountIdentifier) {
    Criteria criteria = Criteria.where(UserMembership.UserMembershipKeys.userId)
                            .is(userMetadataDTO.getUuid())
                            .and(UserMembership.UserMembershipKeys.scope + "." + Scope.ScopeKeys.accountIdentifier)
                            .is(accountIdentifier);
    try (CloseableIterator<UserMembership> iterator = userMembershipRepository.stream(criteria)) {
      while (iterator.hasNext()) {
        UserMembership userMembership = iterator.next();
        Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
          Scope scope = userMembership.getScope();

          List<String> identifierListsOfNonManagedUserGroups = emptyList();
          identifierListsOfNonManagedUserGroups = getUserGroupsForUser(accountIdentifier, ngUserId)
                                                      .stream()
                                                      .filter(ug -> !ug.isHarnessManaged())
                                                      .map(ug -> ug.getIdentifier())
                                                      .collect(Collectors.toList());

          // Create new membership entry with cg_uuid corresponding to ones present with ng_uuid
          // Add user with cg_uuid to all scopes and User groups which ng_uuid was part of.
          ngUserService.addUserToScope(cgUserId, scope, emptyList(), identifierListsOfNonManagedUserGroups, SYSTEM);

          // Next Fetch all Role assignments and add  to Access Control
          PrincipalDTO principalDTO =
              PrincipalDTO.builder().identifier(userMetadataDTO.getUuid()).type(PrincipalType.USER).build();
          PageResponse<RoleAssignmentResponseDTO> fetchCurrentRoleAssignment =
              getResponse(accessControlAdminClient.getFilteredRoleAssignments(scope.getAccountIdentifier(),
                  scope.getOrgIdentifier(), scope.getProjectIdentifier(), 0, DEFAULT_PAGE_SIZE,
                  RoleAssignmentFilterDTO.builder().principalFilter(Collections.singleton(principalDTO)).build()));
          // Apply pagination for above
          // Add new Role assignments to Access Control DB
          List<RoleAssignmentResponseDTO> roleAssignmentContent = fetchCurrentRoleAssignment.getContent();
          List<RoleAssignmentDTO> existingRoleAssignmentDTOS =
              roleAssignmentContent.stream().map(r -> r.getRoleAssignment()).collect(Collectors.toList());
          List<RoleAssignmentDTO> newRoleAssignmentDTOS = new ArrayList<>();
          for (RoleAssignmentDTO roleAssignmentDTO : existingRoleAssignmentDTOS) {
            if (!roleAssignmentDTO.isManaged()) {
              PrincipalDTO existingPrincipal = roleAssignmentDTO.getPrincipal();
              PrincipalDTO newPrincipal = PrincipalDTO.builder()
                                              .identifier(cgUserId)
                                              .type(existingPrincipal.getType())
                                              .scopeLevel(existingPrincipal.getScopeLevel())
                                              .build();
              newRoleAssignmentDTOS.add(roleAssignmentDTO.toBuilder().principal(newPrincipal).build());
            }
          }
          Set<String> listOfRoleAssignmentIdentifers = existingRoleAssignmentDTOS.stream()
                                                           .filter(rA -> !rA.isManaged())
                                                           .map(rA -> rA.getIdentifier())
                                                           .collect(Collectors.toSet());
          if (isNotEmpty(listOfRoleAssignmentIdentifers)) {
            // Remove user from Role assignments in Access Control DB
            NGRestUtils.getResponse(accessControlAdminClient.bulkDelete(scope.getAccountIdentifier(),
                scope.getOrgIdentifier(), scope.getProjectIdentifier(), listOfRoleAssignmentIdentifers));
          }
          // Create corresponding entries for role assignments in RBAC to match user's old access control
          NGRestUtils.getResponse(accessControlAdminClient.createMultiRoleAssignment(scope.getAccountIdentifier(),
              scope.getOrgIdentifier(), scope.getProjectIdentifier(), false,
              RoleAssignmentCreateRequestDTO.builder().roleAssignments(newRoleAssignmentDTOS).build()));

          // Remove user with ng_uuid from userMemberships
          ngUserService.removeUserFromScope(ngUserId, scope, SYSTEM, NGRemoveUserFilter.STRICTLY_FORCE_REMOVE_USER);

          // Remove user with ng_uuid from all user groups.
          userGroupService.removeMemberAll(
              scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), ngUserId);
          return userMembership;
        }));
      }
    }
    return true;
  }

  public List<UserGroup> getUserGroupsForUser(String accountIdentifier, String userId) {
    Criteria criteria = new Criteria();
    criteria.and(UserGroup.UserGroupKeys.accountIdentifier).is(accountIdentifier);
    criteria.and(UserGroup.UserGroupKeys.users).in(userId);
    return userGroupRepository.findAll(criteria);
  }

  /**
   * in single transaction, using mongotemplate -
   * DELETE UserMetaData with ng_uuid
   * ADD userMetaData with cg_uuid
   */
  private boolean deleteOldUserMetaDataAndCreateNew(
      UserMetadataDTO userMetadataDTO, String cgUserId, String accountIdentifier) {
    try {
      userMetadataRepository.deleteByEmail(userMetadataDTO.getEmail());
    } catch (Exception ex) {
      log.warn(DEBUG_MESSAGE + "Skipping! Exception while Deleting old userMetadata entry for NG user id {}",
          userMetadataDTO.getUuid(), accountIdentifier);
      return false;
    }
    try {
      UserMetadata updatedUserMetaData =
          UserMetadata.builder()
              .userId(cgUserId)
              .name(userMetadataDTO.getName())
              .email(userMetadataDTO.getEmail())
              .locked(userMetadataDTO.isLocked())
              .disabled(userMetadataDTO.isDisabled())
              .externallyManaged(userMetadataDTO.isExternallyManaged())
              .twoFactorAuthenticationEnabled(userMetadataDTO.isTwoFactorAuthenticationEnabled())
              .build();
      userMetadataRepository.save(updatedUserMetaData);
    } catch (DuplicateKeyException e) {
      log.warn(DEBUG_MESSAGE
              + "Skipping! DuplicateKeyException while creating new userMetadata entry for CG user id {} for account: {}",
          cgUserId, accountIdentifier);
      return false;
    }
    log.info(DEBUG_MESSAGE
            + "For user: {}, successfully deleted user with userId:{} and created user with userId:{} for account: {}",
        userMetadataDTO.getEmail(), userMetadataDTO.getUuid(), cgUserId, accountIdentifier);
    return true;
  }

  private List<String> fetchUsersFromUserMemberShipForAccount(String accountId) {
    Scope scope = Scope.builder().accountIdentifier(accountId).build();
    List<String> userIds = new ArrayList<>();
    userIds = ngUserService.listUserIds(scope);
    return userIds;
  }

  private void fetchAllRoleAssignments(Set<String> accountIds) {
    for (String accountId : accountIds) {
      Scope scope = Scope.builder().accountIdentifier(accountId).build();
      List<String> userIds = ngUserService.listUserIds(scope);
      for (String userId : userIds) {
        PrincipalDTO principalDTO = PrincipalDTO.builder().identifier(userId).type(PrincipalType.USER).build();
        PageResponse<RoleAssignmentResponseDTO> roleAssignmentPages =
            getResponse(accessControlAdminClient.getFilteredRoleAssignments(accountId, null, null, 0, DEFAULT_PAGE_SIZE,
                RoleAssignmentFilterDTO.builder()
                    .roleFilter(Collections.singleton(ACCOUNT_ADMIN_ROLE))
                    .principalFilter(Collections.singleton(principalDTO))
                    .build()));
        log.info("roleAssignmentPages: " + roleAssignmentPages);
      }
    }
  }
}
