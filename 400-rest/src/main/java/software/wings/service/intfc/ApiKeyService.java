/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;

import software.wings.beans.ApiKeyEntry;
import software.wings.beans.security.UserGroup;
import software.wings.security.UserPermissionInfo;
import software.wings.security.UserRestrictionInfo;
import software.wings.service.intfc.ownership.OwnedByAccount;

import java.util.List;
import org.hibernate.validator.constraints.NotEmpty;

public interface ApiKeyService extends OwnedByAccount {
  ApiKeyEntry update(String uuid, String accountId, ApiKeyEntry apiKeyEntry);
  PageResponse<ApiKeyEntry> list(
      PageRequest<ApiKeyEntry> request, String accountId, boolean loadUserGroups, boolean decrypt);
  ApiKeyEntry generate(@NotEmpty String accountId, ApiKeyEntry apiKeyEntry);
  void delete(String accountId, @NotEmpty String uuid);

  ApiKeyEntry get(@NotEmpty String uuid, @NotEmpty String accountId);
  Boolean validate(String key, String accountId);
  String getAccountIdFromApiKey(String apiKey);
  ApiKeyEntry getByKey(String key, String accountId);
  UserPermissionInfo getApiKeyPermissions(ApiKeyEntry apiKeyEntry, String accountId);
  UserRestrictionInfo getApiKeyRestrictions(
      ApiKeyEntry apiKeyEntry, UserPermissionInfo userPermissionInfo, String accountId);
  void evictAndRebuildPermissions(String accountId, boolean rebuild);
  void evictAndRebuildPermissionsAndRestrictions(String accountId, boolean rebuild);
  void evictPermissionsAndRestrictionsForUserGroup(UserGroup userGroup);
  void loadUserGroupsForApiKeys(List<ApiKeyEntry> apiKeyEntries, String accountId);
  Boolean isApiKeyValid(String key, String accountId);
}
