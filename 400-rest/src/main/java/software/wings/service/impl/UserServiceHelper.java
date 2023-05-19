/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.common.beans.Generation.NG;

import io.harness.beans.FeatureName;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.ng.core.common.beans.Generation;
import io.harness.ng.core.common.beans.UserSource;
import io.harness.ng.core.user.NGRemoveUserFilter;
import io.harness.ng.core.user.UserAccountLevelData;
import io.harness.remote.client.NGRestUtils;
import io.harness.usermembership.remote.UserMembershipClient;

import software.wings.beans.Account;
import software.wings.beans.User;
import software.wings.beans.User.UserKeys;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.UserGroupService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import dev.morphia.query.UpdateOperations;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class UserServiceHelper {
  @Inject private AccountService accountService;
  @Inject private UserGroupService userGroupService;
  @Inject private FeatureFlagService featureFlagService;
  @Inject @Named("PRIVILEGED") private UserMembershipClient userMembershipClient;

  /**
   * Checking first in UserAccountLevelData but if its not found, we check in user membership as a backup.
   * We will remove check on usermembership once migration has completed.
   * @param user
   * @param accountId
   * @return
   */
  public boolean isUserActiveInNG(User user, String accountId) {
    if (accountService.isNextGenEnabled(accountId) && isUserPartOfDeletedAccount(user, accountId)) {
      if (isUserProvisionedInThisGenerationInThisAccount(user, accountId, NG)) {
        return true;
      } else {
        Boolean userMembershipCheck =
            NGRestUtils.getResponse(userMembershipClient.isUserInScope(user.getUuid(), accountId, null, null));
        log.info("User {} is {} part of nextgen in account {}", user.getUuid(),
            Boolean.TRUE.equals(userMembershipCheck) ? "" : "not", accountId);
        if (Boolean.TRUE.equals(userMembershipCheck)) {
          return true;
        }
      }
    }
    return false;
  }

  public boolean isUserProvisionedInThisGenerationInThisAccount(User user, String accountId, Generation generation) {
    if (featureFlagService.isEnabled(FeatureName.PL_USER_ACCOUNT_LEVEL_DATA_FLOW, accountId)
        && validationForUserAccountLevelDataFlow(user, accountId)) {
      Set<Generation> userProvisionedTo = user.getUserAccountLevelDataMap().get(accountId).getUserProvisionedTo();
      return userProvisionedTo.contains(generation);
    }
    return false;
  }

  public boolean isUserProvisionedInThisAccount(User user, String accountId) {
    if (featureFlagService.isEnabled(FeatureName.PL_USER_ACCOUNT_LEVEL_DATA_FLOW, accountId)
        && validationForUserAccountLevelDataFlow(user, accountId)) {
      return isEmpty(user.getUserAccountLevelDataMap().get(accountId).getUserProvisionedTo());
    }
    return false;
  }

  public boolean isUserPartOfDeletedAccount(User user, String deletedAccountId) {
    List<String> userAccounts = user.getAccountIds();
    List<String> userPendingAccounts =
        user.getPendingAccounts().stream().map(Account::getUuid).collect(Collectors.toList());
    if (userAccounts.isEmpty() && userPendingAccounts.isEmpty()) {
      throw new InvalidRequestException("User is not part of any accounts");
    }
    return userAccounts.contains(deletedAccountId) || userPendingAccounts.contains(deletedAccountId);
  }

  public List<Account> updatedActiveAccounts(User user, String deletedAccountId) {
    List<Account> updatedActiveAccounts = new ArrayList<>();
    if (isNotEmpty(user.getAccounts())) {
      for (Account account : user.getAccounts()) {
        if (!account.getUuid().equals(deletedAccountId)) {
          updatedActiveAccounts.add(account);
        }
      }
    }
    return updatedActiveAccounts;
  }

  public List<Account> updatedPendingAccount(User user, String deletedAccountId) {
    List<Account> updatedPendingAccounts = new ArrayList<>();
    if (isNotEmpty(user.getPendingAccounts())) {
      for (Account account : user.getPendingAccounts()) {
        if (!account.getUuid().equals(deletedAccountId)) {
          updatedPendingAccounts.add(account);
        }
      }
    }
    return updatedPendingAccounts;
  }

  public void deleteUserFromNG(String userId, String deletedAccountId, NGRemoveUserFilter removeUserFilter) {
    Boolean deletedFromNG = NGRestUtils.getResponse(
        userMembershipClient.removeUserInternal(userId, deletedAccountId, null, null, removeUserFilter));
    if (!Boolean.TRUE.equals(deletedFromNG)) {
      throw new InvalidRequestException(
          "User could not be removed from NG. User might be the last account admin in NG.");
    }
  }

  public void removeUserProvisioningFromGenerationInAccount(
      String accountId, User user, UpdateOperations<User> updateOp, Generation generation) {
    if (featureFlagService.isEnabled(FeatureName.PL_USER_ACCOUNT_LEVEL_DATA_FLOW, accountId)
        && validationForUserAccountLevelDataFlow(user, accountId)) {
      UserAccountLevelData userAccountLevelData = user.getUserAccountLevelDataMap().get(accountId);
      Set<Generation> userProvisionedTo = userAccountLevelData.getUserProvisionedTo();
      userProvisionedTo.remove(generation);
      userAccountLevelData.getSourceOfProvisioning().remove(generation);
      if (isEmpty(userProvisionedTo)) {
        removeUserAccountLevelDataForThisAccount(accountId, user, updateOp);
      } else {
        updateOp.set(UserKeys.userAccountLevelDataMap, user.getUserAccountLevelDataMap());
      }
    }
  }

  public void removeUserAccountLevelDataForThisAccount(String accountId, User user, UpdateOperations<User> updateOp) {
    if (validationForUserAccountLevelDataFlow(user, accountId)) {
      user.getUserAccountLevelDataMap().remove(accountId);
      updateOp.set(UserKeys.userAccountLevelDataMap, user.getUserAccountLevelDataMap());
    }
  }
  public boolean validationForUserAccountLevelDataFlow(User user, String accountId) {
    return null != user && isNotEmpty(user.getUserAccountLevelDataMap())
        && null != user.getUserAccountLevelDataMap().get(accountId);
  }

  public void populateAccountToUserMapping(User user, String accountId, Generation generation, UserSource userSource) {
    Set<Generation> userProvisionedTo = new HashSet<>();
    Map<Generation, UserSource> userSourceMap = new HashMap<>();
    Map<String, UserAccountLevelData> userAccountLevelDataMap = user.getUserAccountLevelDataMap();
    Map<String, UserAccountLevelData> userAccountLevelDataMapping =
        isEmpty(userAccountLevelDataMap) ? new HashMap<>() : userAccountLevelDataMap;

    // Handling for existing user
    if (isNotEmpty(userAccountLevelDataMap) && null != userAccountLevelDataMap.get(accountId)) {
      userAccountLevelDataMapping = userAccountLevelDataMap;
      UserAccountLevelData userAccountLevelData = userAccountLevelDataMapping.get(accountId);
      userProvisionedTo = userAccountLevelData.getUserProvisionedTo();
      userSourceMap = userAccountLevelData.getSourceOfProvisioning();
    }

    userProvisionedTo.add(generation);
    userSourceMap.put(generation, userSource);
    UserAccountLevelData userAccountLevelData =
        UserAccountLevelData.builder().userProvisionedTo(userProvisionedTo).sourceOfProvisioning(userSourceMap).build();

    userAccountLevelDataMapping.put(accountId, userAccountLevelData);
    user.setUserAccountLevelDataMap(userAccountLevelDataMapping);
  }
}