/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;

import io.harness.beans.PageRequest;
import io.harness.beans.SearchFilter;
import io.harness.migrations.Migration;

import software.wings.beans.Account;
import software.wings.beans.security.UserGroup;
import software.wings.beans.security.UserGroup.UserGroupKeys;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.UserGroupService;

import com.google.inject.Inject;
import dev.morphia.query.UpdateOperations;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
@Slf4j
public class RemoveDuplicateUserGroupNameMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private AccountService accountService;
  @Inject private UserGroupService userGroupService;
  @Override
  public void migrate() {
    try {
      List<Account> accounts = accountService.getAccountsWithBasicInfo(false);
      accounts.forEach(account -> {
        try {
          PageRequest pageRequest =
              aPageRequest()
                  .withLimit(Long.toString(userGroupService.getCountOfUserGroups(account.getUuid())))
                  .addFilter(UserGroup.ACCOUNT_ID_KEY, SearchFilter.Operator.EQ, account.getUuid())
                  .build();
          List<UserGroup> userGroups =
              userGroupService.list(account.getUuid(), pageRequest, false, null, null, false).getResponse();
          Set<String> alreadyUsedNames = new HashSet<>();
          userGroups.forEach(userGroup -> {
            if (userGroup.isImportedByScim()) {
              alreadyUsedNames.add(userGroup.getName());
            }
          });
          userGroups.forEach(userGroup -> {
            String key = userGroup.getName();
            boolean isNameNotUsedSoFar = alreadyUsedNames.add(userGroup.getName());
            if (key == null || userGroup.isImportedByScim() || isNameNotUsedSoFar) {
              return;
            }
            String newUserGroupName = changeNameOfUserGroup(alreadyUsedNames, userGroup);
            alreadyUsedNames.add(newUserGroupName);
          });
        } catch (Exception e) {
          log.error("Duplicate User Group removal failed for accountId: {}", account.getUuid(), e);
        }
      });
    } catch (Exception e) {
      log.error("Duplicate User Group removal migration failed ", e);
    }
  }
  private String changeNameOfUserGroup(Set<String> alreadyUsedNames, UserGroup userGroup) {
    String name = userGroup.getName();
    int val = 1;
    while (alreadyUsedNames.contains(name + "_" + val)) {
      val++;
    }
    UpdateOperations<UserGroup> updateOps = wingsPersistence.createUpdateOperations(UserGroup.class)
                                                .set(UserGroupKeys.name, userGroup.getName() + "_" + val);
    wingsPersistence.update(userGroup, updateOps);
    return name + "_" + val;
  }
}
