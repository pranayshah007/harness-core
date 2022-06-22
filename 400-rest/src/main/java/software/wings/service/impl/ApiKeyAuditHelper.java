/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import software.wings.beans.ApiKeyEntry;
import software.wings.beans.ApiKeyEntryYaml;
import software.wings.beans.security.UserGroup;
import software.wings.service.intfc.UserGroupService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class ApiKeyAuditHelper {
  @Inject UserGroupService userGroupService;
  public ApiKeyEntryYaml getApiKeyDtoFromApiKey(ApiKeyEntry apiKeyEntry) {
    List<String> userGroupNames = apiKeyEntry.getUserGroupIds()
                                      .stream()
                                      .map(userGroupId -> {
                                        UserGroup userGroup = userGroupService.get(userGroupId);
                                        if (userGroup == null) {
                                          log.error("Usergroup id: [{}] is null for apikey [{}] in account [{}]",
                                              userGroupId, apiKeyEntry.getUuid(), apiKeyEntry.getAccountId());
                                          return null;
                                        }
                                        return userGroup.getName();
                                      })
                                      .filter(Objects::nonNull)
                                      .collect(Collectors.toList());
    return ApiKeyEntryYaml.builder().userGroupsName(userGroupNames).build();
  }
}
