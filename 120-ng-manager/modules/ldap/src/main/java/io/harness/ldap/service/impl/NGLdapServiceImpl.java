/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ldap.service.impl;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.NgSetupFields.NG;
import static io.harness.delegate.beans.NgSetupFields.OWNER;
import static io.harness.remote.client.RestClientUtils.getResponse;

import static software.wings.beans.TaskType.NG_LDAP_GROUPS_SYNC;
import static software.wings.beans.TaskType.NG_LDAP_SEARCH_GROUPS;
import static software.wings.beans.TaskType.NG_LDAP_TEST_CONN_SETTINGS;
import static software.wings.beans.TaskType.NG_LDAP_TEST_GROUP_SETTINGS;
import static software.wings.beans.TaskType.NG_LDAP_TEST_USER_SETTINGS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.ldap.LdapSettingsWithEncryptedDataDetail;
import io.harness.delegate.beans.ldap.NGLdapDelegateTaskParameters;
import io.harness.delegate.beans.ldap.NGLdapDelegateTaskResponse;
import io.harness.delegate.beans.ldap.NGLdapGroupSearchTaskParameters;
import io.harness.delegate.beans.ldap.NGLdapGroupSearchTaskResponse;
import io.harness.delegate.beans.ldap.NGLdapGroupSyncTaskResponse;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.utils.TaskSetupAbstractionHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.ldap.scheduler.NGLdapGroupSyncHelper;
import io.harness.ldap.service.NGLdapService;
import io.harness.ng.authenticationsettings.remote.AuthSettingsManagerClient;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.rest.RestResponse;
import io.harness.service.DelegateGrpcClientWrapper;

import software.wings.beans.TaskType;
import software.wings.beans.dto.LdapSettings;
import software.wings.beans.sso.LdapGroupResponse;
import software.wings.beans.sso.LdapTestResponse;
import software.wings.service.impl.ldap.LdapDelegateException;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Call;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PL)
public class NGLdapServiceImpl implements NGLdapService {
  public static final String UNKNOWN_RESPONSE_FROM_DELEGATE = "Unknown Response from delegate";
  private final AuthSettingsManagerClient managerClient;
  private final DelegateGrpcClientWrapper delegateService;
  private final TaskSetupAbstractionHelper taskSetupAbstractionHelper;
  private final UserGroupService userGroupService;
  private final NGLdapGroupSyncHelper ngLdapGroupSyncHelper;

  @Override
  public LdapTestResponse validateLdapConnectionSettings(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, software.wings.beans.dto.LdapSettings settings) {
    NGLdapDelegateTaskParameters parameters = NGLdapDelegateTaskParameters.builder().ldapSettings(settings).build();

    DelegateResponseData delegateResponseData = getDelegateResponseData(
        accountIdentifier, orgIdentifier, projectIdentifier, parameters, NG_LDAP_TEST_CONN_SETTINGS);

    NGLdapDelegateTaskResponse delegateTaskResponse = (NGLdapDelegateTaskResponse) delegateResponseData;
    log.info("Delegate response for validateLdapConnectionSettings: "
        + delegateTaskResponse.getLdapTestResponse().getStatus());
    return delegateTaskResponse.getLdapTestResponse();
  }

  @Override
  public LdapTestResponse validateLdapUserSettings(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, LdapSettings settings) {
    NGLdapDelegateTaskParameters parameters = NGLdapDelegateTaskParameters.builder().ldapSettings(settings).build();

    DelegateResponseData delegateResponseData = getDelegateResponseData(
        accountIdentifier, orgIdentifier, projectIdentifier, parameters, NG_LDAP_TEST_USER_SETTINGS);

    NGLdapDelegateTaskResponse delegateTaskResponse = (NGLdapDelegateTaskResponse) delegateResponseData;
    log.info(
        "Delegate response for validateLdapUserSettings: " + delegateTaskResponse.getLdapTestResponse().getStatus());
    return delegateTaskResponse.getLdapTestResponse();
  }

  @Override
  public LdapTestResponse validateLdapGroupSettings(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, LdapSettings settings) {
    NGLdapDelegateTaskParameters parameters = NGLdapDelegateTaskParameters.builder().ldapSettings(settings).build();

    DelegateResponseData delegateResponseData = getDelegateResponseData(
        accountIdentifier, orgIdentifier, projectIdentifier, parameters, NG_LDAP_TEST_GROUP_SETTINGS);

    NGLdapDelegateTaskResponse delegateTaskResponse = (NGLdapDelegateTaskResponse) delegateResponseData;
    log.info(
        "Delegate response for validateLdapGroupSettings: " + delegateTaskResponse.getLdapTestResponse().getStatus());
    return delegateTaskResponse.getLdapTestResponse();
  }

  @Override
  public Collection<LdapGroupResponse> searchLdapGroupsByName(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String ldapId, String name) {
    LdapSettingsWithEncryptedDataDetail settingsWithEncryptedDataDetail =
        getLdapSettingsWithEncryptedDataInternal(accountIdentifier);
    LdapSettings ldapSettings = settingsWithEncryptedDataDetail.getLdapSettings();

    NGLdapGroupSearchTaskParameters parameters =
        NGLdapGroupSearchTaskParameters.builder()
            .ldapSettings(ldapSettings)
            .encryptedDataDetail(settingsWithEncryptedDataDetail.getEncryptedDataDetail())
            .name(name)
            .build();

    DelegateResponseData delegateResponseData =
        getDelegateResponseData(accountIdentifier, orgIdentifier, projectIdentifier, parameters, NG_LDAP_SEARCH_GROUPS);

    NGLdapGroupSearchTaskResponse groupSearchResponse = (NGLdapGroupSearchTaskResponse) delegateResponseData;
    log.info("Delegate response for searchLdapGroupsByName returned groups whose size: {}",
        groupSearchResponse.getLdapListGroupsResponses().size());
    return groupSearchResponse.getLdapListGroupsResponses();
  }

  @Override
  public void syncUserGroupsJob(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    LdapSettingsWithEncryptedDataDetail settingsWithEncryptedDataDetail =
        getLdapSettingsWithEncryptedDataInternal(accountIdentifier);

    List<UserGroup> userGroupsToSync = userGroupService.getUserGroupsBySsoId(
        accountIdentifier, settingsWithEncryptedDataDetail.getLdapSettings().getUuid());
    Map<UserGroup, LdapGroupResponse> userGroupsToLdapGroupMap = new HashMap<>();

    for (UserGroup userGroup : userGroupsToSync) {
      NGLdapGroupSearchTaskParameters parameters =
          NGLdapGroupSearchTaskParameters.builder()
              .ldapSettings(settingsWithEncryptedDataDetail.getLdapSettings())
              .encryptedDataDetail(settingsWithEncryptedDataDetail.getEncryptedDataDetail())
              .name(userGroup.getSsoGroupId())
              .build();

      DelegateResponseData delegateResponseData = getDelegateResponseData(userGroup.getAccountIdentifier(),
          userGroup.getOrgIdentifier(), userGroup.getProjectIdentifier(), parameters, NG_LDAP_GROUPS_SYNC);

      NGLdapGroupSyncTaskResponse groupSearchResponse = (NGLdapGroupSyncTaskResponse) delegateResponseData;
      log.info("Delegate response for syncLdapGroupByDn returned group whose total members size: {}",
          groupSearchResponse.getLdapGroupsResponse().getTotalMembers());

      userGroupsToLdapGroupMap.put(userGroup, groupSearchResponse.getLdapGroupsResponse());
    }

    ngLdapGroupSyncHelper.reconcileAllUserGroups(
        userGroupsToLdapGroupMap, settingsWithEncryptedDataDetail.getLdapSettings().getUuid(), accountIdentifier);
  }

  private LdapSettingsWithEncryptedDataDetail getLdapSettingsWithEncryptedDataInternal(String accountIdentifier) {
    Call<RestResponse<LdapSettingsWithEncryptedDataDetail>> settingsWithEncryptedDataDetails =
        managerClient.getLdapSettingsWithEncryptedDataDetails(accountIdentifier);
    if (null == settingsWithEncryptedDataDetails) {
      log.warn(
          "Failed to get ldap settings with encrypted data detail from manager for account: {}", accountIdentifier);
      throw new InvalidRequestException("Failed to get LDAPSettings with encrypted data detail for the request");
    }
    return getResponse(settingsWithEncryptedDataDetails);
  }

  private DelegateResponseData getDelegateResponseData(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, TaskParameters parameters, TaskType taskType) {
    DelegateTaskRequest delegateTaskRequest =
        DelegateTaskRequest.builder()
            .taskType(taskType.name())
            .taskParameters(parameters)
            .executionTimeout(Duration.ofMillis(TaskData.DEFAULT_SYNC_CALL_TIMEOUT))
            .accountId(accountIdentifier)
            .taskSetupAbstractions(buildAbstractions(accountIdentifier, orgIdentifier, projectIdentifier))
            .build();

    DelegateResponseData delegateResponseData = delegateService.executeSyncTask(delegateTaskRequest);
    validateDelegateTaskResponse(delegateResponseData);
    return delegateResponseData;
  }

  private void validateDelegateTaskResponse(DelegateResponseData delegateResponseData) {
    if (delegateResponseData instanceof ErrorNotifyResponseData) {
      throw new LdapDelegateException(
          UNKNOWN_RESPONSE_FROM_DELEGATE, ((ErrorNotifyResponseData) delegateResponseData).getException());
    } else if (delegateResponseData instanceof RemoteMethodReturnValueData
        && (((RemoteMethodReturnValueData) delegateResponseData).getException() instanceof InvalidRequestException)) {
      throw(InvalidRequestException)((RemoteMethodReturnValueData) delegateResponseData).getException();
    }
  }

  private Map<String, String> buildAbstractions(
      String accountIdIdentifier, String orgIdentifier, String projectIdentifier) {
    Map<String, String> abstractions = new HashMap<>(2);
    String owner = taskSetupAbstractionHelper.getOwner(accountIdIdentifier, orgIdentifier, projectIdentifier);
    if (isNotEmpty(owner)) {
      abstractions.put(OWNER, owner);
    }
    abstractions.put(NG, "true");
    return abstractions;
  }
}
