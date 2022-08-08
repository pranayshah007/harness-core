/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.compliance;

import static io.harness.data.structure.CollectionUtils.emptyIfNull;

import io.harness.exception.InvalidRequestException;
import io.harness.governance.AllUserGroupFilter;
import io.harness.governance.CustomUserGroupFilter;
import io.harness.governance.UserGroupFilter;

import software.wings.beans.Base;
import software.wings.service.intfc.UserGroupService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class GovernanceUserGroupHelper {
  @Inject UserGroupService userGroupService;

  public List<String> getUserGroups(UserGroupFilter userGroupFilter, String accountId) {
    if (userGroupFilter instanceof AllUserGroupFilter) {
      return emptyIfNull(userGroupService.listByAccountId(accountId))
          .stream()
          .map(Base::getUuid)
          .collect(Collectors.toList());
    } else if (userGroupFilter instanceof CustomUserGroupFilter) {
      return ((CustomUserGroupFilter) userGroupFilter).getUserGroups();
    } else {
      throw new InvalidRequestException("Unknown usergroup filter type");
    }
  }
}
