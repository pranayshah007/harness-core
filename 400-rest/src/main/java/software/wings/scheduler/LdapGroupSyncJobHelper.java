/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scheduler;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.ExecutionContext.MANAGER;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.mongo.MongoUtils.setUnset;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.UserInvite.UserInviteBuilder.anUserInvite;
import static software.wings.beans.UserInviteSource.SourceType.SSO;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FeatureName;
import io.harness.eraro.ErrorCode;
import io.harness.exception.ExceptionLogger;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.AutoLogContext;
import io.harness.manage.ManagedExecutorService;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.SyncTaskContext;
import software.wings.beans.User;
import software.wings.beans.User.UserKeys;
import software.wings.beans.UserInvite;
import software.wings.beans.UserInviteSource;
import software.wings.beans.security.UserGroup;
import software.wings.beans.security.UserGroup.UserGroupKeys;
import software.wings.beans.sso.LdapGroupResponse;
import software.wings.beans.sso.LdapSettings;
import software.wings.beans.sso.LdapSettingsMapper;
import software.wings.beans.sso.LdapTestResponse;
import software.wings.beans.sso.LdapTestResponse.Status;
import software.wings.beans.sso.LdapUserResponse;
import software.wings.beans.sso.SSOSettings;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.dl.WingsPersistence;
import software.wings.features.LdapFeature;
import software.wings.features.api.PremiumFeature;
import software.wings.helpers.ext.ldap.LdapConstants;
import software.wings.logcontext.LdapGroupSyncJobLogContext;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.SSOService;
import software.wings.service.intfc.SSOSettingService;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.UserService;
import software.wings.service.intfc.ldap.LdapDelegateService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.utils.CompletableFutures;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import dev.morphia.FindAndModifyOptions;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.util.Pair;

@OwnedBy(PL)
@TargetModule(HarnessModule._360_CG_MANAGER)
@Slf4j
public class LdapGroupSyncJobHelper {
  private static final SecureRandom random = new SecureRandom();
  private static final String SSO_PROVIDER_ID_KEY = "ssoId";

  public static final String GROUP = "LDAP_GROUP_SYNC_CRON_JOB";
  private static final int POLL_INTERVAL = 900; // Seconds

  public static final long MIN_LDAP_SYNC_TIMEOUT = 60 * 1000L; // 1 minute
  public static final long MAX_LDAP_SYNC_TIMEOUT = 3 * 60 * 1000L; // 3 minute

  @Inject private LdapSyncJobConfig ldapSyncJobConfig;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private SSOSettingService ssoSettingService;
  @Inject private SSOService ssoService;
  @Inject private DelegateProxyFactory delegateProxyFactory;
  @Inject private SecretManager secretManager;
  @Inject private UserService userService;
  @Inject private AuthService authService;

  @Inject private UserGroupService userGroupService;
  @Inject private AccountService accountService;
  @Inject private WingsPersistence wingsPersistence;
  @Inject @Named(LdapFeature.FEATURE_NAME) private PremiumFeature ldapFeature;

  public void syncJob(SSOSettings ssoSettings) {
    if (ssoSettings instanceof LdapSettings) {
      LdapSettings ldapSettings = (LdapSettings) ssoSettings;
      String accountId = ldapSettings.getAccountId();
      if (isEmpty(accountId) || !ldapFeature.isAvailableForAccount(accountId)) {
        log.info("LDAPIterator: Skipping LDAP sync for accountId {}", accountId);
        return;
      }
      String ssoId = ldapSettings.getUuid();
      try (AutoLogContext ignore = new LdapGroupSyncJobLogContext(accountId, ssoId, OVERRIDE_ERROR)) {
        log.info("LDAPIterator: Executing ldap group sync job for ssoId: {} and accountId: {}", ssoId, accountId);
        long startTime = System.nanoTime();
        LdapTestResponse ldapTestResponse = ssoService.validateLdapConnectionSettings(ldapSettings, accountId);
        if (ldapTestResponse.getStatus() == Status.FAILURE) {
          if (ssoSettingService.isDefault(accountId, ssoId)) {
            ssoSettingService.sendSSONotReachableNotification(accountId, ldapSettings);
          } else {
            ssoSettingService.raiseSyncFailureAlert(accountId, ssoId,
                String.format(LdapConstants.SSO_PROVIDER_NOT_REACHABLE, ldapSettings.getDisplayName()));
          }
          return;
        }

        List<UserGroup> userGroupsToSync = userGroupService.getUserGroupsBySsoId(accountId, ssoId);
        if (isEmpty(userGroupsToSync)) {
          log.info("LDAPIterator: No linked user groups to process for ssoId {} accountId {}", ssoId, accountId);
        } else if (featureFlagService.isEnabled(FeatureName.PL_LDAP_PARALLEL_GROUP_SYNC, ssoSettings.getAccountId())) {
          log.info("ParallelLDAPIterator: Executing Parallel ldap group sync job for ssoId: {} and accountId: {}",
              ssoId, accountId);
          syncUserGroupsParallel(accountId, ldapSettings, userGroupsToSync, ssoId);
        } else {
          syncUserGroups(accountId, ldapSettings, userGroupsToSync, ssoId);
        }
        long endTime = System.nanoTime();
        log.info("LDAPIterator: Ldap group sync job done for ssoId {} accountId {} in time(ns): {}", ssoId, accountId,
            endTime - startTime);
      } catch (WingsException exception) {
        if (exception.getCode() == ErrorCode.USER_GROUP_SYNC_FAILURE) {
          ssoSettingService.raiseSyncFailureAlert(accountId, ssoId, exception.getMessage());
        } else {
          ssoSettingService.raiseSyncFailureAlert(accountId, ssoId,
              String.format(LdapConstants.USER_GROUP_SYNC_FAILED, ldapSettings.getDisplayName())
                  + exception.getMessage());
        }
        ExceptionLogger.logProcessedMessages(exception, MANAGER, log);
      } catch (Exception ex) {
        ssoSettingService.raiseSyncFailureAlert(accountId, ssoId,
            String.format(LdapConstants.USER_GROUP_SYNC_FAILED, ldapSettings.getDisplayName()) + ex.getMessage());
        log.error("LDAPIterator: Error while syncing ssoId {} in accountId {}", ssoId, accountId, ex);
      }
    }
  }

  private void updateRemovedGroupMembers(UserGroup userGroup, Collection<LdapUserResponse> expectedMembers,
      Map<UserGroup, Set<User>> removedGroupMembers) {
    if (isEmpty(userGroup.getMembers())) {
      return;
    }
    Set<User> removedUsers;
    if (featureFlagService.isEnabled(FeatureName.LDAP_SYNC_WITH_USERID, userGroup.getAccountId())) {
      Set<String> expectedMemberIds = expectedMembers.stream()
                                          .map(LdapUserResponse::getUserId)
                                          .filter(Objects::nonNull)
                                          .collect(Collectors.toSet());

      removedUsers = userGroup.getMembers()
                         .stream()
                         .filter(member -> !expectedMemberIds.contains(member.getExternalUserId()))
                         .collect(Collectors.toSet());
      log.info("LDAPIterator: Removing users {} as part of sync with user IDs for usergroup {} in accountId {}",
          removedUsers, userGroup.getUuid(), userGroup.getAccountId());
    } else {
      Set<String> expectedMemberEmails =
          expectedMembers.stream().map(LdapUserResponse::getEmail).filter(Objects::nonNull).collect(Collectors.toSet());

      removedUsers = userGroup.getMembers()
                         .stream()
                         .filter(member -> !expectedMemberEmails.contains(member.getEmail()))
                         .collect(Collectors.toSet());
    }

    if (!removedGroupMembers.containsKey(userGroup)) {
      removedGroupMembers.put(userGroup, Sets.newHashSet());
    }
    log.info("LDAPIterator: Removing users {} as part of sync with usergroup {} in accountId {}", removedUsers,
        userGroup.getUuid(), userGroup.getAccountId());
    removedGroupMembers.getOrDefault(userGroup, Sets.newHashSet()).addAll(removedUsers);
  }

  private void updateAddedGroupMembers(UserGroup userGroup, Collection<LdapUserResponse> expectedMembers,
      Map<LdapUserResponse, Set<UserGroup>> addedGroupMembers) {
    if (featureFlagService.isEnabled(FeatureName.LDAP_SYNC_WITH_USERID, userGroup.getAccountId())) {
      Set<String> existingUserIds;
      if (isEmpty(userGroup.getMembers())) {
        existingUserIds = Sets.newHashSet();
      } else {
        existingUserIds = userGroup.getMembers().stream().map(User::getExternalUserId).collect(Collectors.toSet());
      }

      expectedMembers.stream()
          .filter(member -> member.getUserId() != null && !existingUserIds.contains(member.getUserId()))
          .forEach(member -> {
            if (!addedGroupMembers.containsKey(member)) {
              addedGroupMembers.put(member, Sets.newHashSet());
            }
            addedGroupMembers.get(member).add(userGroup);
          });
      log.info("LDAPIterator: Adding user members {} as part of LDAP sync with User Ids for group {} in accountId {}",
          addedGroupMembers, userGroup.getUuid(), userGroup.getAccountId());
    } else {
      Set<String> existingUserEmails;
      if (isEmpty(userGroup.getMembers())) {
        existingUserEmails = Sets.newHashSet();
      } else {
        existingUserEmails = userGroup.getMembers().stream().map(User::getEmail).collect(Collectors.toSet());
      }

      expectedMembers.stream()
          .filter(member -> member.getEmail() != null && !existingUserEmails.contains(member.getEmail()))
          .forEach(member -> {
            if (!addedGroupMembers.containsKey(member)) {
              addedGroupMembers.put(member, Sets.newHashSet());
            }
            addedGroupMembers.get(member).add(userGroup);
          });
    }
    log.info("LDAPIterator: Adding user members {} as part of LDAP sync for group {} in accountId {}",
        addedGroupMembers, userGroup.getUuid(), userGroup.getAccountId());
  }

  @VisibleForTesting
  UserGroup syncUserGroupMetadata(UserGroup userGroup, LdapGroupResponse groupResponse) {
    UpdateOperations<UserGroup> updateOperations = wingsPersistence.createUpdateOperations(UserGroup.class);
    setUnset(updateOperations, UserGroupKeys.ssoGroupName, groupResponse.getName());
    Query<UserGroup> query = wingsPersistence.createQuery(UserGroup.class)
                                 .filter("_id", userGroup.getUuid())
                                 .field(UserGroupKeys.accountId)
                                 .equal(userGroup.getAccountId());
    return wingsPersistence.findAndModify(query, updateOperations, new FindAndModifyOptions());
  }

  @VisibleForTesting
  public void syncUserGroupMembers(String accountId, Map<UserGroup, Set<User>> removedGroupMembers,
      Map<LdapUserResponse, Set<UserGroup>> addedGroupMembers) {
    if (isNotEmpty(removedGroupMembers)) {
      log.info("LDAPIterator: users to be removed {}", removedGroupMembers);
      removedGroupMembers.forEach((userGroup, users) -> userGroupService.removeMembers(userGroup, users, false, true));
    }
    if (isNotEmpty(addedGroupMembers)) {
      for (Map.Entry<LdapUserResponse, Set<UserGroup>> entry : addedGroupMembers.entrySet()) {
        LdapUserResponse ldapUserResponse = entry.getKey();
        Set<UserGroup> userGroups = entry.getValue();

        try {
          User user = userService.getUserByEmail(ldapUserResponse.getEmail());
          log.info("LDAPIterator: user found from by email Id {} is {}", ldapUserResponse.getEmail(), user);

          if (featureFlagService.isEnabled(FeatureName.LDAP_SYNC_WITH_USERID, accountId)) {
            user = userService.getUserByUserId(accountId, ldapUserResponse.getUserId());
            log.info("LDAPIterator: Fetching user with user Id {}", ldapUserResponse.getUserId());
          }
          log.info("LDAPIterator: user found from system is {}", user);

          if (user != null && userService.isUserAssignedToAccount(user, accountId)) {
            log.info("LDAPIterator: user {} already assigned to account {}", user.getEmail(), accountId);
            userService.addUserToUserGroups(accountId, user, Lists.newArrayList(userGroups), true, true);
            log.info("LDAPIterator: adding existing user {} to groups {}  in accountId {}", user.getUuid(),
                Lists.newArrayList(userGroups), accountId);
          } else {
            UserInvite userInvite = anUserInvite()
                                        .withAccountId(accountId)
                                        .withEmail(ldapUserResponse.getEmail())
                                        .withName(ldapUserResponse.getName())
                                        .withUserGroups(Lists.newArrayList(userGroups))
                                        .withUserId(ldapUserResponse.getUserId())
                                        .withSource(UserInviteSource.builder().type(SSO).build())
                                        .build();
            log.info(
                "LDAPIterator: creating user invite for account {} and user Invite {} and user Groups {} and externalUserId {}",
                accountId, userInvite.getEmail(), Lists.newArrayList(userGroups), ldapUserResponse.getUserId());
            userService.inviteUser(userInvite, false, true);
            // Get the newly added user and add them to this user group
            user = userService.getUserByEmail(ldapUserResponse.getEmail());
            userService.addUserToUserGroups(accountId, user, Lists.newArrayList(userGroups), true, true);
            log.info("LDAPIterator: adding new user {} to groups {}  in accountId {}", user.getUuid(),
                Lists.newArrayList(userGroups), accountId);
          }
        } catch (Exception e) {
          if (ldapUserResponse != null && isNotEmpty(ldapUserResponse.getEmail())) {
            log.error("LDAPIterator: could not sync user {} of account {} with error", ldapUserResponse.getEmail(),
                accountId, e);
          } else {
            log.error("LDAPIterator: could not sync for account {} as email was not found ", accountId, e);
          }
        }
      }
    }
  }

  @VisibleForTesting
  LdapGroupResponse fetchGroupDetails(
      LdapSettings ldapSettings, EncryptedDataDetail encryptedDataDetail, UserGroup userGroup) {
    long userProvidedTimeout = ldapSettings.getConnectionSettings().getResponseTimeout();
    // if user specified time
    long ldapSyncTimeout = getLdapSyncTimeout(userProvidedTimeout);
    log.info("LDAPIterator: Fetching LDAP group details for {} with timeout {}", ldapSettings.getAccountId(),
        ldapSyncTimeout);
    SyncTaskContext syncTaskContext = SyncTaskContext.builder()
                                          .accountId(ldapSettings.getAccountId())
                                          .appId(GLOBAL_APP_ID)
                                          .timeout(ldapSyncTimeout)
                                          .build();
    LdapGroupResponse groupResponse = delegateProxyFactory.getV2(LdapDelegateService.class, syncTaskContext)
                                          .fetchGroupByDn(LdapSettingsMapper.ldapSettingsDTO(ldapSettings),
                                              encryptedDataDetail, userGroup.getSsoGroupId());
    if (null == groupResponse) {
      String message = String.format(LdapConstants.USER_GROUP_SYNC_INVALID_REMOTE_GROUP, userGroup.getName());
      log.info("LDAPIterator: Group Response from delegate is null");
      throw new WingsException(ErrorCode.USER_GROUP_SYNC_FAILURE, message);
    }
    log.info("LDAPIterator: Group Response from delegate {}", groupResponse);
    return groupResponse;
  }

  @VisibleForTesting
  public long getLdapSyncTimeout(long userProvidedTimeout) {
    if (userProvidedTimeout < MIN_LDAP_SYNC_TIMEOUT) {
      return MIN_LDAP_SYNC_TIMEOUT;
    } else {
      return Math.min(userProvidedTimeout, MAX_LDAP_SYNC_TIMEOUT);
    }
  }

  @VisibleForTesting
  boolean validateUserGroupStates(Collection<UserGroup> userGroups) {
    for (UserGroup userGroup : userGroups) {
      UserGroup savedUserGroup = userGroupService.get(userGroup.getAccountId(), userGroup.getUuid(), false);
      if (!savedUserGroup.isSsoLinked()) {
        return false;
      }
      if (!savedUserGroup.getSsoGroupId().equals(userGroup.getSsoGroupId())) {
        return false;
      }
    }
    return true;
  }

  private void updateUserIdsGroupMembers(Collection<LdapUserResponse> users) {
    for (LdapUserResponse user : users) {
      if (isNotBlank(user.getUserId())) {
        UpdateOperations<User> updateOperations = wingsPersistence.createUpdateOperations(User.class);
        setUnset(updateOperations, UserKeys.externalUserId, user.getUserId());
        Query<User> query = wingsPersistence.createQuery(User.class).filter(UserKeys.email, user.getEmail());
        wingsPersistence.findAndModify(query, updateOperations, new FindAndModifyOptions());
      }
    }
  }

  @VisibleForTesting
  void syncUserGroups(String accountId, LdapSettings ldapSettings, List<UserGroup> userGroups, String ssoId) {
    Map<UserGroup, Set<User>> removedGroupMembers = new HashMap<>();
    Map<LdapUserResponse, Set<UserGroup>> addedGroupMembers = new HashMap<>();

    EncryptedDataDetail encryptedDataDetail = ldapSettings.getEncryptedDataDetails(secretManager);

    List<UserGroup> userGroupsFailedToSync = new ArrayList<>();
    for (UserGroup userGroup : userGroups) {
      try {
        LdapGroupResponse groupResponse = fetchGroupDetails(ldapSettings, encryptedDataDetail, userGroup);

        if (!groupResponse.isSelectable()) {
          String message = String.format(
              LdapConstants.USER_GROUP_SYNC_NOT_ELIGIBLE, userGroup.getName(), groupResponse.getMessage());
          throw new UnsupportedOperationException(message);
        }
        log.info("LDAPIterator: Fetched  LdapGroupResponse {} of group {} accountId {}", groupResponse,
            userGroup.getUuid(), accountId);
        syncUserGroupMetadata(userGroup, groupResponse);
        if (featureFlagService.isEnabled(FeatureName.LDAP_USER_ID_SYNC, accountId)) {
          updateUserIdsGroupMembers(groupResponse.getUsers());
        }
        updateRemovedGroupMembers(userGroup, groupResponse.getUsers(), removedGroupMembers);
        updateAddedGroupMembers(userGroup, groupResponse.getUsers(), addedGroupMembers);
        log.info("LDAPIterator: Updated members of group {} accountId {}", userGroup.getUuid(), accountId);
      } catch (Exception e) {
        log.error("LDAPIterator: LDAP sync failed for userGroup {}", userGroup.getName(), e);
        userGroupsFailedToSync.add(userGroup);
      }
    }

    if (userGroupsFailedToSync.isEmpty()) {
      ssoSettingService.closeSyncFailureAlertIfOpen(accountId, ssoId);
      log.info("LDAPIterator: closeSyncFailureAlertIfOpen ssoId {} accountId {}", ssoId, accountId);
    } else {
      String userGroupsFailed =
          userGroupsFailedToSync.stream().map(UserGroup::getName).collect(Collectors.joining(", ", "[", "]"));
      ssoSettingService.raiseSyncFailureAlert(
          accountId, ssoId, String.format("LDAPIterator: Ldap Sync failed for groups: %s", userGroupsFailed));
    }
    // Sync the groups only if the state is still the same as we started. Else any change in the groups would have
    // already triggered another cron job and it will handle it.
    if (validateUserGroupStates(userGroups)) {
      syncUserGroupMembers(accountId, removedGroupMembers, addedGroupMembers);
    }
  }
  @VisibleForTesting
  void syncUserGroupsParallel(String accountId, LdapSettings ldapSettings, List<UserGroup> userGroups, String ssoId) {
    Map<UserGroup, Set<User>> removedGroupMembers = new ConcurrentHashMap<>();
    Map<LdapUserResponse, Set<UserGroup>> addedGroupMembers = new ConcurrentHashMap<>();
    EncryptedDataDetail encryptedDataDetail = ldapSettings.getEncryptedDataDetails(secretManager);
    Set<UserGroup> userGroupsFailedToSync = ConcurrentHashMap.newKeySet();
    Map<UserGroup, LdapGroupResponse> userGroupLdapGroupResponseMap = new ConcurrentHashMap<>();
    List<Pair<UserGroup, LdapGroupResponse>> userGroupLdapGroupResponsePairList = new ArrayList<>();

    userGroupLdapGroupResponsePairList =
        asynchGetUserGroupLdapGroupResponsePairList(ssoId, accountId, ldapSettings, userGroups, encryptedDataDetail,
            userGroupsFailedToSync, userGroupLdapGroupResponseMap, userGroupLdapGroupResponsePairList);

    processUserGroupLdapGroupResponseList(
        accountId, removedGroupMembers, addedGroupMembers, userGroupsFailedToSync, userGroupLdapGroupResponsePairList);

    if (userGroupsFailedToSync.isEmpty()) {
      ssoSettingService.closeSyncFailureAlertIfOpen(accountId, ssoId);
      log.info("ParallelLDAPIterator: closeSyncFailureAlertIfOpen ssoId {} accountId {}", ssoId, accountId);
    } else {
      String userGroupsFailed =
          userGroupsFailedToSync.stream().map(UserGroup::getName).collect(Collectors.joining(", ", "[", "]"));
      ssoSettingService.raiseSyncFailureAlert(
          accountId, ssoId, String.format("ParallelLDAPIterator: Ldap Sync failed for groups: %s", userGroupsFailed));
    }
    // Sync the groups only if the state is still the same as we started. Else any change in the groups would have
    // already triggered another cron job and it will handle it.
    if (validateUserGroupStates(userGroups)) {
      syncUserGroupMembers(accountId, removedGroupMembers, addedGroupMembers);
    }
  }

  private void processUserGroupLdapGroupResponseList(String accountId, Map<UserGroup, Set<User>> removedGroupMembers,
      Map<LdapUserResponse, Set<UserGroup>> addedGroupMembers, Set<UserGroup> userGroupsFailedToSync,
      List<Pair<UserGroup, LdapGroupResponse>> userGroupLdapGroupResponsePairList) {
    for (Pair<UserGroup, LdapGroupResponse> userGroupLdapGroupResponseEntry : userGroupLdapGroupResponsePairList) {
      if (null == userGroupLdapGroupResponseEntry || null == userGroupLdapGroupResponseEntry.getKey()
          || null == userGroupLdapGroupResponseEntry.getValue()) {
        log.info("ParallelLDAPIterator:Null value: " + userGroupLdapGroupResponseEntry);
      } else {
        calculateUserGroupDataModification(accountId, removedGroupMembers, addedGroupMembers, userGroupsFailedToSync,
            userGroupLdapGroupResponseEntry.getKey(), userGroupLdapGroupResponseEntry.getValue());
      }
    }
  }

  private List<Pair<UserGroup, LdapGroupResponse>> asynchGetUserGroupLdapGroupResponsePairList(String ssoId,
      String accountId, LdapSettings ldapSettings, List<UserGroup> userGroups, EncryptedDataDetail encryptedDataDetail,
      Set<UserGroup> userGroupsFailedToSync, Map<UserGroup, LdapGroupResponse> userGroupLdapGroupResponseMap,
      List<Pair<UserGroup, LdapGroupResponse>> userGroupLdapGroupResponsePairList) {
    int poolSize = 4;
    if (null != ldapSyncJobConfig && ldapSyncJobConfig.getPoolSize() > 0) {
      poolSize = Math.max(20, ldapSyncJobConfig.getPoolSize());
    }

    log.info(
        "ParallelLDAPIterator: Starting UserGroup Sync with number of threads {} for userGroups {} ssoId {} accountId {}",
        poolSize, userGroups, ssoId, accountId);
    ExecutorService managedDelegateTaskExecutor = new ManagedExecutorService(Executors.newFixedThreadPool(poolSize));
    CompletableFutures<Pair<UserGroup, LdapGroupResponse>> ldapDelegateTasks =
        new CompletableFutures<>(managedDelegateTaskExecutor);
    for (UserGroup userGroup : userGroups) {
      ldapDelegateTasks.supplyAsync(()
                                        -> fetchDataFromDelegate(ldapSettings, encryptedDataDetail,
                                            userGroupLdapGroupResponseMap, userGroup, userGroupsFailedToSync));
    }
    try {
      userGroupLdapGroupResponsePairList = executeParallelTasks(ldapDelegateTasks);
      log.info("ParallelLDAPIterator: All LDAP sync Delegate task completed for userGroups {} ssoId {} accountId {}",
          userGroups, ssoId, accountId);
    } catch (Exception ex) {
      log.error("ParallelLDAPIterator: CompletableFutures of LDAP sync failed for userGroups {}", userGroups, ex);
      userGroupsFailedToSync.addAll(userGroups);
    } finally {
      managedDelegateTaskExecutor.shutdown();
    }
    return userGroupLdapGroupResponsePairList;
  }

  private Pair<UserGroup, LdapGroupResponse> fetchDataFromDelegate(LdapSettings ldapSettings,
      EncryptedDataDetail encryptedDataDetail, Map<UserGroup, LdapGroupResponse> userGroupLdapGroupResponseMap,
      UserGroup userGroup, Set<UserGroup> userGroupsFailedToSync) {
    Pair<UserGroup, LdapGroupResponse> userGroupLdapGroupResponsePair = null;
    try {
      return new Pair<>(userGroup, fetchGroupDetails(ldapSettings, encryptedDataDetail, userGroup));
    } catch (Exception ex) {
      log.error("ParallelLDAPIterator: Delegate task for LDAP sync failed for userGroup {}", userGroup.getName(), ex);
      userGroupsFailedToSync.add(userGroup);
      return new Pair<>(userGroup, null);
    }
  }

  private void calculateUserGroupDataModification(String accountId, Map<UserGroup, Set<User>> removedGroupMembers,
      Map<LdapUserResponse, Set<UserGroup>> addedGroupMembers, Set<UserGroup> userGroupsFailedToSync,
      UserGroup userGroup, LdapGroupResponse groupResponse) {
    try {
      if (!groupResponse.isSelectable()) {
        String message =
            String.format(LdapConstants.USER_GROUP_SYNC_NOT_ELIGIBLE, userGroup.getName(), groupResponse.getMessage());
        throw new UnsupportedOperationException(message);
      }
      log.info("ParallelLDAPIterator: Fetched  LdapGroupResponse {} of group {} accountId {}", groupResponse,
          userGroup.getUuid(), accountId);
      syncUserGroupMetadata(userGroup, groupResponse);
      if (featureFlagService.isEnabled(FeatureName.LDAP_USER_ID_SYNC, accountId)) {
        updateUserIdsGroupMembers(groupResponse.getUsers());
      }
      updateRemovedGroupMembers(userGroup, groupResponse.getUsers(), removedGroupMembers);
      updateAddedGroupMembers(userGroup, groupResponse.getUsers(), addedGroupMembers);
      log.info("ParallelLDAPIterator: Updated members of group {} accountId {}", userGroup.getUuid(), accountId);
    } catch (Exception e) {
      log.error("ParallelLDAPIterator: LDAP sync failed for userGroup {}", userGroup.getName(), e);
      userGroupsFailedToSync.add(userGroup);
    }
  }

  private List<Pair<UserGroup, LdapGroupResponse>> executeParallelTasks(
      @NotNull CompletableFutures<Pair<UserGroup, LdapGroupResponse>> ldapTasks)
      throws InterruptedException, ExecutionException {
    CompletableFuture<List<Pair<UserGroup, LdapGroupResponse>>> ldapResults = ldapTasks.allOf();
    try {
      return new ArrayList<>(ldapResults.get());
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw ex;
    } catch (ExecutionException ex) {
      if (ex.getCause() instanceof WingsException) {
        throw(WingsException) ex.getCause();
      }
      throw ex;
    }
  }
}