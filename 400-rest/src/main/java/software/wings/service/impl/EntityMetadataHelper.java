/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.beans.FeatureName.SPG_REMOVE_REDUNDANT_UPDATE_IN_AUDIT;

import io.harness.beans.EmbeddedUser;
import io.harness.ff.FeatureFlagService;

import software.wings.audit.AuditHeader;
import software.wings.audit.AuditHeader.AuditHeaderKeys;
import software.wings.beans.Account;
import software.wings.beans.ApiKeyEntry;
import software.wings.beans.User;
import software.wings.beans.security.UserGroup;
import software.wings.common.AuditHelper;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.ApiKeyService;
import software.wings.service.intfc.UserService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.UpdateOperations;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class EntityMetadataHelper {
  @Inject private UserService userService;
  @Inject private AuditHelper auditHelper;
  @Inject private AccountService accountService;
  @Inject private ApiKeyService apiKeyService;

  @Inject private FeatureFlagService featureFlagService;

  private WingsPersistence wingsPersistence;

  @Inject
  public EntityMetadataHelper(WingsPersistence wingsPersistence) {
    this.wingsPersistence = wingsPersistence;
  }

  public <T> void addUserEntityDetails(String accountId, T entity, AuditHeader header) {
    List<String> userGroupNamesList = new ArrayList<>();
    HashMap<String, Object> details = new HashMap<>();

    Account account = accountService.get(accountId);
    details.put("Authentication", account.getAuthenticationMechanism());

    User user = (User) entity;
    details.put("MFA", user.isTwoFactorAuthenticationEnabled());

    List<UserGroup> userGroupList = userService.getUserGroupsOfUserAudit(accountId, user.getUuid());
    userGroupList.forEach(userGroup -> userGroupNamesList.add(userGroup.getName()));
    details.put("Groups", userGroupNamesList);

    UpdateOperations<AuditHeader> updateOperation = wingsPersistence.createUpdateOperations(AuditHeader.class);
    updateOperation.set(AuditHeaderKeys.details, details);
    wingsPersistence.update(header, updateOperation);
  }

  public <T> void addAPIKeyDetails(String accountId, T entity, AuditHeader header) {
    List<String> userGroupNamesList = new ArrayList<>();
    HashMap<String, Object> details = new HashMap<>();

    details.put("Authentication", "API Key");
    details.put("resourcePath", header.getResourcePath());

    ApiKeyEntry apiKeyEntry = (ApiKeyEntry) entity;
    List<ApiKeyEntry> apiKeyEntryList = new ArrayList<>();
    apiKeyEntryList.add(apiKeyEntry);
    apiKeyService.loadUserGroupsForApiKeys(apiKeyEntryList, accountId);
    apiKeyEntry.getUserGroups().forEach(userGroup -> { userGroupNamesList.add(userGroup.getName()); });
    details.put("Groups", userGroupNamesList);

    UpdateOperations<AuditHeader> updateOperation = wingsPersistence.createUpdateOperations(AuditHeader.class);
    updateOperation.set(AuditHeaderKeys.details, details);

    if (!featureFlagService.isEnabled(SPG_REMOVE_REDUNDANT_UPDATE_IN_AUDIT, accountId)) {
      updateOperation.set(AuditHeaderKeys.createdBy, EmbeddedUser.builder().name("API").build());
    }
    wingsPersistence.update(header, updateOperation);
  }

  public <T> void addUserDetails(String accountId, T entity, AuditHeader header) {
    List<String> userGroupNamesList = new ArrayList<>();
    HashMap<String, Object> details = new HashMap<>();

    String userUuid = header.getCreatedBy().getUuid();
    User user = userService.get(userUuid);
    Account account = accountService.get(accountId);
    details.put("Authentication", account.getAuthenticationMechanism());

    details.put("MFA", user.isTwoFactorAuthenticationEnabled());

    List<UserGroup> userGroupList = userService.getUserGroupsOfUserAudit(accountId, user.getUuid());
    userGroupList.forEach(userGroup -> userGroupNamesList.add(userGroup.getName()));
    details.put("Groups", userGroupNamesList);

    UpdateOperations<AuditHeader> updateOperation = wingsPersistence.createUpdateOperations(AuditHeader.class);
    updateOperation.set(AuditHeaderKeys.details, details);
    wingsPersistence.update(header, updateOperation);
  }
}
