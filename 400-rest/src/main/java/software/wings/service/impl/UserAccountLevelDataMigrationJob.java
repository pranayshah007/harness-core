/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.authorization.AuthorizationServiceHeader.MANAGER;
import static io.harness.beans.FeatureName.PL_USER_ACCOUNT_LEVEL_DATA_MIGRATION;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.common.beans.Generation.CG;
import static io.harness.ng.core.common.beans.Generation.NG;
import static io.harness.ng.core.common.beans.UserSource.LDAP;
import static io.harness.ng.core.common.beans.UserSource.MANUAL;
import static io.harness.ng.core.common.beans.UserSource.SCIM;

import io.harness.beans.PageRequest;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.ng.core.common.beans.Generation;
import io.harness.ng.core.common.beans.UserSource;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.remote.client.NGRestUtils;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;
import io.harness.usergroups.UserGroupClient;
import io.harness.usermembership.remote.UserMembershipClient;

import software.wings.beans.User;
import software.wings.beans.User.UserKeys;
import software.wings.beans.security.UserGroup;
import software.wings.beans.sso.LdapSettings;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.SSOSettingService;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.UserService;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.dropwizard.lifecycle.Managed;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class UserAccountLevelDataMigrationJob implements Managed {
  private static final long DELAY_IN_MINUTES = 120;

  private static final String LOCK_NAME = "USER_ACCOUNT_LEVEL_DATA_LOCK";
  private final String DEBUG_MESSAGE = "UserAccountLevelDataMigrationJob: ";
  @Inject AccountService accountService;
  @Inject UserServiceHelper userServiceHelper;
  @Inject private SSOSettingService ssoSettingService;
  @Inject private UserGroupService userGroupService;
  @Inject private UserService userService;
  @Inject private WingsPersistence wingsPersistence;
  @Inject @Named("PRIVILEGED") private UserGroupClient userGroupClient;
  @Inject @Named("PRIVILEGED") private UserMembershipClient userMembershipClient;

  @Inject private PersistentLocker persistentLocker;

  private ScheduledExecutorService executorService;

  @Override
  public void start() throws Exception {
    Random random = new Random();
    executorService = Executors.newSingleThreadScheduledExecutor(
        new ThreadFactoryBuilder().setNameFormat("user-account-level-data-migration-job").build());
    executorService.scheduleWithFixedDelay(this::run, 15 + random.nextInt(15), DELAY_IN_MINUTES, TimeUnit.MINUTES);
  }

  @Override
  public void stop() throws Exception {
    log.warn(DEBUG_MESSAGE + " is stopped");
    executorService.shutdownNow();
    executorService.awaitTermination(30, TimeUnit.SECONDS);
  }

  @VisibleForTesting
  public void run() {
    log.info(DEBUG_MESSAGE + "started...");
    try (AcquiredLock<?> lock =
             persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(LOCK_NAME, Duration.ofSeconds(5))) {
      if (lock == null) {
        log.info(DEBUG_MESSAGE + "failed to acquire lock");
        return;
      }
      try {
        SecurityContextBuilder.setContext(new ServicePrincipal(MANAGER.getServiceId()));
        log.info(DEBUG_MESSAGE + "Setting SecurityContext completed and migration started");
        execute();
        log.info(DEBUG_MESSAGE + "Migration completed");
      } catch (Exception ex) {
        log.error(DEBUG_MESSAGE + " unexpected error occurred while Setting SecurityContext", ex);
      } finally {
        SecurityContextBuilder.unsetCompleteContext();
        log.info(DEBUG_MESSAGE + "Unsetting SecurityContext completed.");
      }
    } catch (Exception ex) {
      log.error(DEBUG_MESSAGE + " failed to acquire lock", ex);
    }
    log.info(DEBUG_MESSAGE + " completed...");
  }

  private void execute() {
    Set<String> ffEnabledAccountList = new HashSet<>();
    try {
      ffEnabledAccountList =
          accountService.getFeatureFlagEnabledAccountIds(PL_USER_ACCOUNT_LEVEL_DATA_MIGRATION.name());
    } catch (Exception ex) {
      log.error(DEBUG_MESSAGE + "Failed to fetch all accounts with FF PL_USER_ACCOUNT_LEVEL_DATA_MIGRATION", ex);
    }
    if (isEmpty(ffEnabledAccountList)) {
      log.info(DEBUG_MESSAGE + "Migration skipped for this iteration because no FF enabled account");
      return;
    }

    for (String ffEnabledAccount : ffEnabledAccountList) {
      try {
        log.info(DEBUG_MESSAGE + "Migration starts for account {}", ffEnabledAccount);
        provisionSCIMAndManual(ffEnabledAccount);
        provisionLDAP(ffEnabledAccount);
        Thread.sleep(1000);
      } catch (Exception ex) {
        log.error(DEBUG_MESSAGE + "Failed to migrate users for account {} ", ffEnabledAccount, ex);
      }
    }
  }

  private void provisionSCIMAndManual(String accountId) {
    processCGUsersForSCIMAndManual(accountId);
    processNGUsersForSCIMAndManual(accountId);
  }

  private void provisionLDAP(String accountId) {
    LdapSettings settings = ssoSettingService.getLdapSettingsByAccountId(accountId);
    if (null != settings) {
      String ssoId = settings.getUuid();
      processCGUsersLDAP(accountId, ssoId);
      processNGUsersLDAP(accountId, ssoId);
    } else {
      log.info(DEBUG_MESSAGE + "Skipping Account! No Ldap setting exists for account {}", accountId);
    }
  }

  private void processCGUsersForSCIMAndManual(String accountId) {
    long totalUserCount = 0;
    try {
      totalUserCount = userService.getTotalUserCount(accountId, true);
    } catch (Exception exception) {
      log.error(DEBUG_MESSAGE + "Skipping Account! Call to get total count of Users for account {} failed.", accountId,
          exception);
    }
    Integer pageSize = 100;
    if (totalUserCount > 0) {
      for (int i = 0; i <= totalUserCount / pageSize; i++) {
        Integer offset = i * pageSize;
        PageRequest pageRequest =
            aPageRequest().withOffset(String.valueOf(offset)).withLimit(String.valueOf(pageSize)).build();
        List<User> allUsers = userService.listUsers(pageRequest, accountId, null, offset, pageSize, false, true, true);
        List<User> scimUsers = allUsers.stream().filter(user -> user.isImported()).collect(Collectors.toList());
        scimUsers = scimUsers.stream()
                        .filter(user -> !isAlreadyProcessedForThisGenAndUserSource(accountId, user, CG, SCIM))
                        .collect(Collectors.toList());
        if (isEmpty(scimUsers)) {
          log.info(
              DEBUG_MESSAGE + "No SCIM managed users to process for accountId {} for offset {}", accountId, offset);
        } else {
          for (User scimUser : scimUsers) {
            updateAndPersistUserAccountLevelData(accountId, scimUser, CG, SCIM);
          }
        }
        // processMANUAL
        List<User> nonScimUsers = allUsers.stream().filter(user -> !user.isImported()).collect(Collectors.toList());
        nonScimUsers = nonScimUsers.stream()
                           .filter(user -> !isAlreadyProcessedForThisGenAndUserSource(accountId, user, CG, MANUAL))
                           .collect(Collectors.toList());
        if (isEmpty(nonScimUsers)) {
          log.info(
              DEBUG_MESSAGE + "No Non-SCIM managed users to process for accountId {} for offset {}", accountId, offset);
        } else {
          for (User nonScimUser : nonScimUsers) {
            updateAndPersistUserAccountLevelData(accountId, nonScimUser, CG, MANUAL);
          }
        }
      }
    }
  }

  private void processNGUsersForSCIMAndManual(String accountId) {
    if (accountService.isNextGenEnabled(accountId)) {
      long totalUserCount = 0;
      try {
        totalUserCount = userService.getTotalUserCount(accountId, true);
      } catch (Exception exception) {
        log.error(DEBUG_MESSAGE + "NG: Skipping Account! Call to get total count of Users for account {} failed.",
            accountId, exception);
      }
      Integer pageSize = 500;
      if (totalUserCount > 0) {
        for (int i = 0; i <= totalUserCount / pageSize; i++) {
          Integer offset = i * pageSize;
          PageRequest pageRequest =
              aPageRequest().withOffset(String.valueOf(offset)).withLimit(String.valueOf(pageSize)).build();
          List<User> allUsers =
              userService.listUsers(pageRequest, accountId, null, offset, pageSize, false, true, true);
          List<User> scimUsers = allUsers.stream().filter(user -> user.isImported()).collect(Collectors.toList());
          scimUsers = scimUsers.stream()
                          .filter(user -> !isAlreadyProcessedForThisGenAndUserSource(accountId, user, NG, SCIM))
                          .collect(Collectors.toList());
          if (isEmpty(scimUsers)) {
            log.info(DEBUG_MESSAGE + "NG: No SCIM managed users to process for accountId {} for offset {}", accountId,
                offset);
          } else {
            for (User scimUser : scimUsers) {
              updateIfNGUserForUserSource(accountId, scimUser, SCIM);
            }
          }
          // processMANUAL
          List<User> nonScimUsers = allUsers.stream().filter(user -> !user.isImported()).collect(Collectors.toList());
          nonScimUsers = nonScimUsers.stream()
                             .filter(user -> !isAlreadyProcessedForThisGenAndUserSource(accountId, user, NG, MANUAL))
                             .collect(Collectors.toList());
          if (isEmpty(nonScimUsers)) {
            log.info(DEBUG_MESSAGE + "NG: No NOn-SCIM managed users to process for accountId {} for offset {}",
                accountId, offset);
          } else {
            for (User nonScimUser : nonScimUsers) {
              updateIfNGUserForUserSource(accountId, nonScimUser, MANUAL);
            }
          }
        }
      }
    }
  }

  private void updateIfNGUserForUserSource(String accountId, User scimUser, UserSource userSource) {
    try {
      Boolean userMembershipCheck =
          NGRestUtils.getResponse(userMembershipClient.isUserInScope(scimUser.getUuid(), accountId, null, null));
      if (Boolean.TRUE.equals(userMembershipCheck)) {
        updateAndPersistUserAccountLevelData(accountId, scimUser, NG, userSource);
      }
    } catch (Exception ex) {
      log.error(DEBUG_MESSAGE + " NG: Skipping! Call to get user {} on NG failed for account {} ", scimUser.getUuid(),
          accountId, ex);
    }
  }

  private void processNGUsersLDAP(String accountId, String ssoId) {
    List<UserGroupDTO> userGroups = null;
    if (accountService.isNextGenEnabled(accountId)) {
      try {
        userGroups = NGRestUtils.getResponse(userGroupClient.getSsoLinkedUserGroups(ssoId, accountId));
      } catch (Exception ex) {
        log.error(
            DEBUG_MESSAGE + " NG: Skipping! Call to find linked SSO groups on NG failed For account {} and ssoId {} ",
            accountId, ssoId, ex);
      }

      if (isEmpty(userGroups)) {
        log.info(DEBUG_MESSAGE + " NG: No linked user groups to process for ssoId {} accountId {}", ssoId, accountId);
      } else {
        for (UserGroupDTO userGroup : userGroups) {
          if (isEmpty(userGroup.getUsers())) {
            log.info(DEBUG_MESSAGE + " NG: No user present in userGroup {} accountId {}", userGroup, accountId);
          } else {
            List<User> users = userService.getUsers(userGroup.getUsers(), accountId);
            users = users.stream()
                        .filter(user -> !isAlreadyProcessedForThisGenAndUserSource(accountId, user, NG, LDAP))
                        .collect(Collectors.toList());
            for (User user : users) {
              updateAndPersistUserAccountLevelData(accountId, user, NG, LDAP);
            }
          }
        }
      }
    }
  }

  private void processCGUsersLDAP(String accountId, String ssoId) {
    List<UserGroup> userGroups = userGroupService.getUserGroupsBySsoId(accountId, ssoId);
    if (isEmpty(userGroups)) {
      log.info(DEBUG_MESSAGE + "No linked user groups to process for ssoId {} accountId {}", ssoId, accountId);
    } else {
      for (UserGroup userGroup : userGroups) {
        if (isEmpty(userGroup.getMemberIds())) {
          log.info(DEBUG_MESSAGE + "No user present in userGroup {} accountId {}", userGroup, accountId);
        } else {
          List<User> users = userGroup.getMembers();
          users = users.stream()
                      .filter(user -> !isAlreadyProcessedForThisGenAndUserSource(accountId, user, CG, LDAP))
                      .collect(Collectors.toList());
          for (User user : users) {
            updateAndPersistUserAccountLevelData(accountId, user, CG, LDAP);
          }
        }
      }
    }
  }

  private void updateAndPersistUserAccountLevelData(
      String accountId, User user, Generation generation, UserSource userSource) {
    userServiceHelper.populateAccountToUserMapping(user, accountId, generation, userSource);
    wingsPersistence.update(user,
        wingsPersistence.createUpdateOperations(User.class)
            .set(UserKeys.userAccountLevelDataMap, user.getUserAccountLevelDataMap()));
  }

  private boolean isAlreadyProcessedForThisGenAndUserSource(
      String accountId, User user, Generation generation, UserSource userSource) {
    return null != user && isNotEmpty(user.getUserAccountLevelDataMap())
        && null != user.getUserAccountLevelDataMap().get(accountId)
        && isNotEmpty(user.getUserAccountLevelDataMap().get(accountId).getSourceOfProvisioning())
        && userSource.equals(
            user.getUserAccountLevelDataMap().get(accountId).getSourceOfProvisioning().get(generation));
  }
}
