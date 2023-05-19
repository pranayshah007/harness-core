/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.user.NGRemoveUserFilter;
import io.harness.remote.client.NGRestUtils;
import io.harness.usermembership.remote.UserMembershipClient;

import software.wings.beans.Account;
import software.wings.beans.User;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.UserGroupService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class UserServiceHelper {
  @Inject private AccountService accountService;
  @Inject private UserGroupService userGroupService;
  @Inject @Named("PRIVILEGED") private UserMembershipClient userMembershipClient;

  public boolean isUserActiveInNG(User user, String accountId) {
    if (accountService.isNextGenEnabled(accountId) && isUserPartOfDeletedAccount(user, accountId)) {
      Boolean userMembershipCheck =
          NGRestUtils.getResponse(userMembershipClient.isUserInScope(user.getUuid(), accountId, null, null));
      log.info("User {} is {} part of nextgen in account {}", user.getUuid(),
          Boolean.TRUE.equals(userMembershipCheck) ? "" : "not", accountId);
      if (Boolean.TRUE.equals(userMembershipCheck)) {
        return true;
      }
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
}