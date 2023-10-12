/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.PageRequest.UNLIMITED;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.beans.SearchFilter.Operator.IN;

import static software.wings.beans.security.UserGroup.DEFAULT_ACCOUNT_ADMIN_USER_GROUP_NAME;
import static software.wings.beans.security.UserGroup.DEFAULT_NON_PROD_SUPPORT_USER_GROUP_NAME;
import static software.wings.beans.security.UserGroup.DEFAULT_PROD_SUPPORT_USER_GROUP_NAME;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.migrations.Migration;

import software.wings.beans.Account;
import software.wings.beans.security.UserGroup;
import software.wings.service.impl.security.auth.AuthHandler;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.UserGroupService;

import com.google.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Migration script to create default support user groups and rename account admin user group
 * This script is meant to be idempotent, so it could be run any number of times.
 * @author rktummala on 3/21/18
 */

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_FIRST_GEN})
@Slf4j
public class CreateSupportUserGroupsAndRenameAccountAdmin implements Migration {
  private static final String DEFAULT_OLD_USER_GROUP_NAME = "ADMIN";

  @Inject private AuthHandler authHandler;
  @Inject private AccountService accountService;
  @Inject private UserGroupService userGroupService;

  @Override
  public void migrate() {
    PageRequest<Account> accountPageRequest = aPageRequest().withLimit(UNLIMITED).build();
    List<Account> accountList = accountService.list(accountPageRequest);

    if (accountList != null) {
      accountList.forEach(account -> {
        String accountId = account.getUuid();
        PageRequest<UserGroup> pageRequest =
            aPageRequest()
                .addFilter("accountId", EQ, accountId)
                .addFilter("name", IN, DEFAULT_OLD_USER_GROUP_NAME, DEFAULT_ACCOUNT_ADMIN_USER_GROUP_NAME,
                    DEFAULT_PROD_SUPPORT_USER_GROUP_NAME, DEFAULT_NON_PROD_SUPPORT_USER_GROUP_NAME)
                .build();
        PageResponse<UserGroup> pageResponse = userGroupService.list(accountId, pageRequest, true, null, null, false);

        List<UserGroup> userGroupList = pageResponse.getResponse();

        Set<UserGroup> userGroupSet = new HashSet<>(userGroupList);

        if (!isUserGroupPresent(DEFAULT_ACCOUNT_ADMIN_USER_GROUP_NAME, userGroupSet)) {
          UserGroup userGroup = getUserGroup(DEFAULT_OLD_USER_GROUP_NAME, userGroupSet);
          if (userGroup != null) {
            userGroup.setName(DEFAULT_ACCOUNT_ADMIN_USER_GROUP_NAME);
            userGroupService.updateOverview(userGroup);
          } else {
            UserGroup defaultAdminUserGroup = authHandler.buildDefaultAdminUserGroup(accountId, null);
            userGroupService.save(defaultAdminUserGroup);
          }
        }

        if (!isUserGroupPresent(DEFAULT_PROD_SUPPORT_USER_GROUP_NAME, userGroupSet)) {
          UserGroup prodSupportUserGroup = authHandler.buildProdSupportUserGroup(accountId);
          userGroupService.save(prodSupportUserGroup);
        }

        if (!isUserGroupPresent(DEFAULT_NON_PROD_SUPPORT_USER_GROUP_NAME, userGroupSet)) {
          UserGroup nonProdSupportUserGroup = authHandler.buildNonProdSupportUserGroup(accountId);
          userGroupService.save(nonProdSupportUserGroup);
        }
      });
    }
  }

  private UserGroup getUserGroup(String userGroupName, Set<UserGroup> userGroupSet) {
    Optional<UserGroup> userGroupOptional =
        userGroupSet.stream().filter(userGroup -> userGroupName.equals(userGroup.getName())).findFirst();
    if (userGroupOptional.isPresent()) {
      return userGroupOptional.get();
    }
    return null;
  }

  private boolean isUserGroupPresent(String userGroupName, Set<UserGroup> userGroupSet) {
    return userGroupSet.stream().filter(userGroup -> userGroupName.equals(userGroup.getName())).findFirst().isPresent();
  }
}
