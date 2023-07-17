/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ldap.service.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.NgSetupFields.NG;
import static io.harness.delegate.beans.NgSetupFields.OWNER;
import static io.harness.remote.client.CGRestUtils.getResponse;

import static software.wings.beans.TaskType.NG_LDAP_GROUPS_SYNC;
import static software.wings.beans.TaskType.NG_LDAP_SEARCH_GROUPS;
import static software.wings.beans.TaskType.NG_LDAP_TEST_AUTHENTICATION;
import static software.wings.beans.TaskType.NG_LDAP_TEST_CONN_SETTINGS;
import static software.wings.beans.TaskType.NG_LDAP_TEST_GROUP_SETTINGS;
import static software.wings.beans.TaskType.NG_LDAP_TEST_USER_SETTINGS;
import static software.wings.beans.sso.LdapTestResponse.Status.FAILURE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.ldap.LDAPTestAuthenticationRequest;
import io.harness.delegate.beans.ldap.LdapSettingsWithEncryptedDataAndPasswordDetail;
import io.harness.delegate.beans.ldap.LdapSettingsWithEncryptedDataDetail;
import io.harness.delegate.beans.ldap.NGLdapDelegateTaskParameters;
import io.harness.delegate.beans.ldap.NGLdapDelegateTaskResponse;
import io.harness.delegate.beans.ldap.NGLdapGroupSearchTaskParameters;
import io.harness.delegate.beans.ldap.NGLdapGroupSearchTaskResponse;
import io.harness.delegate.beans.ldap.NGLdapGroupSyncTaskResponse;
import io.harness.delegate.beans.ldap.NGLdapTestAuthenticationTaskParameters;
import io.harness.delegate.beans.ldap.NGLdapTestAuthenticationTaskResponse;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.utils.TaskSetupAbstractionHelper;
import io.harness.exception.DelegateNotAvailableException;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.exception.ExplanationException;
import io.harness.exception.GeneralException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.exceptionhandler.DocumentLinksConstants;
import io.harness.ldap.scheduler.NGLdapGroupSyncHelper;
import io.harness.ldap.service.NGLdapService;
import io.harness.ldap.service.impl.errors.LdapErrorHandler;
import io.harness.ng.authenticationsettings.remote.AuthSettingsManagerClient;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.rest.RestResponse;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;

import software.wings.beans.TaskType;
import software.wings.beans.dto.LdapSettings;
import software.wings.beans.sso.LdapGroupResponse;
import software.wings.beans.sso.LdapSettingsMapper;
import software.wings.beans.sso.LdapTestResponse;
import software.wings.helpers.ext.ldap.LdapConstants;
import software.wings.helpers.ext.ldap.LdapResponse;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ldaptive.ResultCode;
import retrofit2.Call;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PL)
public class NGLdapServiceImpl implements NGLdapService {
  public static final String ISSUE_WITH_LDAP_CONNECTION = "Issue with Ldap Connection";
  public static final String ISSUE_WITH_USER_QUERY_SETTINGS_PROVIDED = "Issue with User Query Settings provided";
  public static final String ISSUE_WITH_GROUP_QUERY_SETTINGS_PROVIDED = "Issue with Group Query Settings provided";
  public static final String ISSUE_WITH_LDAP_TEST_AUTHENTICATION = "Issue with Ldap Test Authentication";
  public static final int LDAP_TASK_DEFAULT_MINIMUM_TIMEOUT_MILLIS = 60000; // 60 seconds
  public static final int LDAP_TASK_DEFAULT_MAXIMUM_TIMEOUT_MILLIS = 180000; // 180 seconds
  private final AuthSettingsManagerClient managerClient;
  private final DelegateGrpcClientWrapper delegateService;
  private final TaskSetupAbstractionHelper taskSetupAbstractionHelper;
  private final UserGroupService userGroupService;
  private final NGLdapGroupSyncHelper ngLdapGroupSyncHelper;

  @Override
  public LdapTestResponse validateLdapConnectionSettings(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, software.wings.beans.sso.LdapSettings settings) {
    log.info(
        "Validate ldap connection in NG LDAP case for account: {}, organization: {}, project: {} with ldap settings id {}",
        accountIdentifier, orgIdentifier, projectIdentifier, settings.getUuid());
    NGLdapDelegateTaskParameters parameters = getNgLdapDelegateTaskParameters(accountIdentifier, settings);

    DelegateResponseData delegateResponseData = getDelegateResponseData(accountIdentifier, orgIdentifier,
        projectIdentifier, parameters, NG_LDAP_TEST_CONN_SETTINGS, parameters.getLdapSettings());

    LdapTestResponse ldapTestResponse =
        getLdapTestResponse((NGLdapDelegateTaskResponse) delegateResponseData, ISSUE_WITH_LDAP_CONNECTION);
    log.info("NGLDAP:Delegate response for validateLdapConnectionSettings: " + ldapTestResponse);
    return ldapTestResponse;
  }

  @Override
  public LdapTestResponse validateLdapUserSettings(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, software.wings.beans.sso.LdapSettings settings) {
    log.info(
        "Validate ldap user query in NG LDAP case for account: {}, organization: {}, project: {} with ldap settings id {}",
        accountIdentifier, orgIdentifier, projectIdentifier, settings.getUuid());
    NGLdapDelegateTaskParameters parameters = getNgLdapDelegateTaskParameters(accountIdentifier, settings);

    DelegateResponseData delegateResponseData = getDelegateResponseData(accountIdentifier, orgIdentifier,
        projectIdentifier, parameters, NG_LDAP_TEST_USER_SETTINGS, parameters.getLdapSettings());

    LdapTestResponse ldapTestResponse =
        getLdapTestResponse((NGLdapDelegateTaskResponse) delegateResponseData, ISSUE_WITH_USER_QUERY_SETTINGS_PROVIDED);
    log.info("NGLDAP:Delegate response for validateLdapUserSettings: " + ldapTestResponse);
    return ldapTestResponse;
  }

  @Override
  public LdapTestResponse validateLdapGroupSettings(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, software.wings.beans.sso.LdapSettings settings) {
    log.info(
        "NGLDAP: Validate ldap group query in NG LDAP case for account: {}, organization: {}, project: {} with ldap settings id {}",
        accountIdentifier, orgIdentifier, projectIdentifier, settings.getUuid());
    NGLdapDelegateTaskParameters parameters = getNgLdapDelegateTaskParameters(accountIdentifier, settings);

    DelegateResponseData delegateResponseData = getDelegateResponseData(accountIdentifier, orgIdentifier,
        projectIdentifier, parameters, NG_LDAP_TEST_GROUP_SETTINGS, parameters.getLdapSettings());

    LdapTestResponse ldapTestResponse = getLdapTestResponse(
        (NGLdapDelegateTaskResponse) delegateResponseData, ISSUE_WITH_GROUP_QUERY_SETTINGS_PROVIDED);
    log.info("NGLDAP:Delegate response for validateLdapGroupSettings: " + ldapTestResponse);
    return ldapTestResponse;
  }

  private LdapTestResponse getLdapTestResponse(NGLdapDelegateTaskResponse delegateResponseData, String errorMessage) {
    LdapTestResponse ldapTestResponse = delegateResponseData.getLdapTestResponse();

    String ldapTestResponseMessage = ldapTestResponse.getMessage();
    if (FAILURE == ldapTestResponse.getStatus() && null != ldapTestResponseMessage) {
      handleErrorResponseMessageFromDelegate(errorMessage, ldapTestResponseMessage);
    }
    return ldapTestResponse;
  }

  @Override
  public Collection<LdapGroupResponse> searchLdapGroupsByName(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String ldapId, String name) {
    log.info("NGLDAP:Search user group by name in NG LDAP case for account: {}, organization: {}, project: {}",
        accountIdentifier, orgIdentifier, projectIdentifier);
    LdapSettingsWithEncryptedDataDetail settingsWithEncryptedDataDetail =
        getLdapSettingsWithEncryptedDataInternal(accountIdentifier);
    LdapSettings ldapSettings = settingsWithEncryptedDataDetail.getLdapSettings();

    NGLdapGroupSearchTaskParameters parameters =
        NGLdapGroupSearchTaskParameters.builder()
            .ldapSettings(ldapSettings)
            .encryptedDataDetail(settingsWithEncryptedDataDetail.getEncryptedDataDetail())
            .name(name)
            .build();

    DelegateResponseData delegateResponseData = getDelegateResponseData(
        accountIdentifier, orgIdentifier, projectIdentifier, parameters, NG_LDAP_SEARCH_GROUPS, ldapSettings);
    // TODO: Need to send back exception form Delegate but thius will impact CG too.
    NGLdapGroupSearchTaskResponse groupSearchResponse = (NGLdapGroupSearchTaskResponse) delegateResponseData;
    log.info(
        "NGLDAP:Received delegate response for searchLdapGroupsByName in NG LDAP for account: {}", accountIdentifier);
    return groupSearchResponse.getLdapListGroupsResponses();
  }

  @Override
  public void syncUserGroupsJob(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    LdapSettingsWithEncryptedDataDetail settingsWithEncryptedDataDetail =
        getLdapSettingsWithEncryptedDataInternal(accountIdentifier);

    if (checkAndLogIfLDAPAuthorizationIsEnabled(
            accountIdentifier, orgIdentifier, projectIdentifier, settingsWithEncryptedDataDetail)) {
      return;
    }

    log.info("NGLDAP: Sync user group for NG LDAP starting for account: {}, organization: {}, project: {}",
        accountIdentifier, orgIdentifier, projectIdentifier);
    List<UserGroup> userGroupsToSync = userGroupService.getUserGroupsBySsoId(
        accountIdentifier, settingsWithEncryptedDataDetail.getLdapSettings().getUuid());
    syncUserGroupsJobInternal(accountIdentifier, settingsWithEncryptedDataDetail, userGroupsToSync);
  }

  @Override
  public LdapResponse testLDAPLogin(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String email, String password) {
    log.info("NGLDAP: Test LDAP authentication for account: {}, with email: {}", accountIdentifier, email);
    LDAPTestAuthenticationRequest authenticationRequest =
        LDAPTestAuthenticationRequest.builder().email(email).password(password).build();
    LdapSettingsWithEncryptedDataAndPasswordDetail withEncryptedDataAndPasswordDetail =
        getResponse(managerClient.getLdapSettingsAndEncryptedPassword(accountIdentifier, authenticationRequest));

    NGLdapTestAuthenticationTaskParameters taskParameters =
        NGLdapTestAuthenticationTaskParameters.builder()
            .ldapSettings(withEncryptedDataAndPasswordDetail.getLdapSettings())
            .settingsEncryptedDataDetail(withEncryptedDataAndPasswordDetail.getEncryptedDataDetail())
            .passwordEncryptedDataDetail(withEncryptedDataAndPasswordDetail.getEncryptedPwdDataDetail())
            .username(email)
            .build();

    DelegateResponseData delegateResponseData = getDelegateResponseData(accountIdentifier, orgIdentifier,
        projectIdentifier, taskParameters, NG_LDAP_TEST_AUTHENTICATION, taskParameters.getLdapSettings());
    NGLdapTestAuthenticationTaskResponse authResponse = (NGLdapTestAuthenticationTaskResponse) delegateResponseData;
    LdapResponse ldapAuthTestResponse = authResponse.getLdapAuthenticationResponse();
    if (null != ldapAuthTestResponse) {
      final String ldapAuthTestResponseMessage = ldapAuthTestResponse.getMessage();
      if (LdapResponse.Status.FAILURE == ldapAuthTestResponse.getStatus() && isNotEmpty(ldapAuthTestResponseMessage)) {
        handleErrorResponseMessageFromDelegate(ISSUE_WITH_LDAP_TEST_AUTHENTICATION, ldapAuthTestResponseMessage);
      }
    }
    return ldapAuthTestResponse;
  }

  @Override
  public void syncAUserGroupJob(
      String userGroupIdentifier, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    LdapSettingsWithEncryptedDataDetail settingsWithEncryptedDataDetail =
        getLdapSettingsWithEncryptedDataInternal(accountIdentifier);

    if (checkAndLogIfLDAPAuthorizationIsEnabled(
            accountIdentifier, orgIdentifier, projectIdentifier, settingsWithEncryptedDataDetail)) {
      return;
    }

    log.info("NGLDAP: Sync a user group with id {} for NG LDAP starting for account: {}, organization: {}, project: {}",
        userGroupIdentifier, accountIdentifier, orgIdentifier, projectIdentifier);
    Optional<UserGroup> userGroup =
        userGroupService.get(accountIdentifier, orgIdentifier, projectIdentifier, userGroupIdentifier);

    if (userGroup.isEmpty()) {
      log.warn(
          "NGLDAP: User group with identifier {} not found to trigger LDAP sync in account: {}, organization: {}, project: {}",
          userGroupIdentifier, accountIdentifier, orgIdentifier, projectIdentifier);
      return;
    }

    List<UserGroup> userGroupsToSync = Collections.singletonList(userGroup.get());
    syncUserGroupsJobInternal(accountIdentifier, settingsWithEncryptedDataDetail, userGroupsToSync);
  }

  private boolean checkAndLogIfLDAPAuthorizationIsEnabled(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, LdapSettingsWithEncryptedDataDetail settingsWithEncryptedDataDetail) {
    if (null != settingsWithEncryptedDataDetail && null != settingsWithEncryptedDataDetail.getLdapSettings()
        && settingsWithEncryptedDataDetail.getLdapSettings().isDisabled()) {
      log.info(
          "NGLDAP: Sync user group is disabled at LDAP setting level for NG LDAP on account: {}, organization: {}, project: {}. Skipping user group sync",
          accountIdentifier, orgIdentifier, projectIdentifier);
      return true;
    }
    return false;
  }

  private void syncUserGroupsJobInternal(String accountIdentifier,
      LdapSettingsWithEncryptedDataDetail settingsWithEncryptedDataDetail, List<UserGroup> userGroupsToSync) {
    if (isEmpty(userGroupsToSync)) {
      log.info("NGLDAP: No User groups to sync for acccountIdentifer:{}", accountIdentifier);
      return;
    }

    Map<UserGroup, LdapGroupResponse> userGroupsToLdapGroupMap = new HashMap<>();

    for (UserGroup userGroup : userGroupsToSync) {
      try {
        NGLdapGroupSearchTaskParameters parameters =
            NGLdapGroupSearchTaskParameters.builder()
                .ldapSettings(settingsWithEncryptedDataDetail.getLdapSettings())
                .encryptedDataDetail(settingsWithEncryptedDataDetail.getEncryptedDataDetail())
                .name(userGroup.getSsoGroupId())
                .build();

        DelegateResponseData delegateResponseData =
            getDelegateResponseData(userGroup.getAccountIdentifier(), userGroup.getOrgIdentifier(),
                userGroup.getProjectIdentifier(), parameters, NG_LDAP_GROUPS_SYNC, parameters.getLdapSettings());

        NGLdapGroupSyncTaskResponse groupSearchResponse = (NGLdapGroupSyncTaskResponse) delegateResponseData;
        log.info("NGLDAP: Received delegate response for syncLdapGroupByDn in NG LDAP for group {} in account: {}",
            userGroup.getIdentifier(), accountIdentifier);

        if (null != groupSearchResponse.getLdapGroupsResponse()) {
          userGroupsToLdapGroupMap.put(userGroup, groupSearchResponse.getLdapGroupsResponse());
        } else {
          log.error(
              "NGLDAP: No LDAP group response received in delegate response. Points to some error in delegate task execution for group: {} in account: {}",
              userGroup, accountIdentifier);
        }
      } catch (Exception e) {
        log.error("NGLDAP: Sync error for user group {} and accountId {}", userGroup.getName(), accountIdentifier, e);
      }
    }

    ngLdapGroupSyncHelper.reconcileAllUserGroups(
        userGroupsToLdapGroupMap, settingsWithEncryptedDataDetail.getLdapSettings().getUuid(), accountIdentifier);
  }

  private void handleErrorResponseMessageFromDelegate(String errorMessage, String ldapTestResponseMessage) {
    try {
      if (LdapConstants.INVALID_CREDENTIALS.equals(ldapTestResponseMessage)) {
        LdapErrorHandler.handleError(ResultCode.INVALID_CREDENTIALS, errorMessage, true);
      } else {
        LdapErrorHandler.handleError(ResultCode.valueOf(ldapTestResponseMessage), errorMessage, false);
      }
    } catch (IllegalArgumentException exception) {
      log.error("NGLDAP: Received {} error code from Delegate. Check if this case is not handled in Delegate.",
          ldapTestResponseMessage, exception);
      throw NestedExceptionUtils.hintWithExplanationException(HintException.LDAP_ATTRIBUTES_INCORRECT,
          ExplanationException.LDAP_ATTRIBUTES_INCORRECT, new GeneralException(errorMessage));
    }
  }

  private NGLdapDelegateTaskParameters getNgLdapDelegateTaskParameters(
      String accountIdentifier, software.wings.beans.sso.LdapSettings settings) {
    LdapSettingsWithEncryptedDataDetail settingsWithEncryptedDataDetail =
        getLdapSettingsWithEncryptedDataInternal(accountIdentifier, settings);
    EncryptedDataDetail encryptedDataDetail = settingsWithEncryptedDataDetail.getEncryptedDataDetail();
    return NGLdapDelegateTaskParameters.builder()
        .ldapSettings(settingsWithEncryptedDataDetail.getLdapSettings())
        .encryptedDataDetail(encryptedDataDetail)
        .build();
  }

  private LdapSettingsWithEncryptedDataDetail getLdapSettingsWithEncryptedDataInternal(
      String accountIdentifier, software.wings.beans.sso.LdapSettings ldapSettings) {
    Call<RestResponse<LdapSettingsWithEncryptedDataDetail>> settingsWithEncryptedDataDetails =
        managerClient.getLdapSettingsUsingAccountIdAndLdapSettings(
            accountIdentifier, LdapSettingsMapper.ldapSettingsDTO(ldapSettings));
    if (null == settingsWithEncryptedDataDetails) {
      log.warn(
          "Failed to get ldap settings with encrypted data detail from manager for account: {}", accountIdentifier);
      throw new InvalidRequestException("Failed to get LDAPSettings with encrypted data detail for the request");
    }
    return getResponse(settingsWithEncryptedDataDetails);
  }

  private LdapSettingsWithEncryptedDataDetail getLdapSettingsWithEncryptedDataInternal(String accountIdentifier) {
    Call<RestResponse<LdapSettingsWithEncryptedDataDetail>> settingsWithEncryptedDataDetails =
        managerClient.getLdapSettingsUsingAccountId(accountIdentifier);
    if (null == settingsWithEncryptedDataDetails) {
      log.warn("NGLDAP: Failed to get ldap settings with encrypted data detail from manager for account: {}",
          accountIdentifier);
      throw new InvalidRequestException("Failed to get LDAPSettings with encrypted data detail for the request");
    }
    return getResponse(settingsWithEncryptedDataDetails);
  }

  private DelegateResponseData getDelegateResponseData(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, TaskParameters parameters, TaskType taskType, LdapSettings ldapSettings) {
    DelegateTaskRequest delegateTaskRequest =
        DelegateTaskRequest.builder()
            .taskType(taskType.name())
            .taskParameters(parameters)
            .executionTimeout(getLdapDelegateTaskResponseTimeout(
                ldapSettings.getConnectionSettings().getResponseTimeout(), taskType.name(), accountIdentifier))
            .accountId(accountIdentifier)
            .taskSetupAbstractions(buildAbstractions(accountIdentifier, orgIdentifier, projectIdentifier))
            .taskSelectors(getDelegateSelectors(ldapSettings))
            .build();

    return executeDelegateSyncTask(delegateTaskRequest);
  }

  private Set<String> getDelegateSelectors(LdapSettings ldapSettings) {
    return null != ldapSettings && null != ldapSettings.getConnectionSettings()
            && isNotEmpty(ldapSettings.getConnectionSettings().getDelegateSelectors())
        ? ldapSettings.getConnectionSettings().getDelegateSelectors()
        : new HashSet<>();
  }

  private DelegateResponseData executeDelegateSyncTask(DelegateTaskRequest delegateTaskRequest) {
    final DelegateResponseData delegateResponseData;
    String delegateDownErrorMessage = "Delegates are not available for performing operation.";
    try {
      delegateResponseData = delegateService.executeSyncTaskV2(delegateTaskRequest);
    } catch (DelegateServiceDriverException ex) {
      log.error("NGLDAP: Error occurred while executing delegate task.", ex);
      throw buildDelegateNotAvailableHintException(delegateDownErrorMessage);
    }

    if (delegateResponseData instanceof ErrorNotifyResponseData) {
      throw buildDelegateNotAvailableHintException(delegateDownErrorMessage);
    }
    return delegateResponseData;
  }

  private HintException buildDelegateNotAvailableHintException(String delegateDownErrorMessage) {
    return new HintException(
        String.format(HintException.DELEGATE_NOT_AVAILABLE, DocumentLinksConstants.DELEGATE_INSTALLATION_LINK),
        new DelegateNotAvailableException(delegateDownErrorMessage, WingsException.USER));
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

  @VisibleForTesting
  Duration getLdapDelegateTaskResponseTimeout(
      int responseTimeout, final String taskType, final String accountIdentifier) {
    Duration duration;
    if (responseTimeout < LDAP_TASK_DEFAULT_MINIMUM_TIMEOUT_MILLIS) {
      duration = Duration.ofMillis(LDAP_TASK_DEFAULT_MINIMUM_TIMEOUT_MILLIS);
    } else if (responseTimeout > LDAP_TASK_DEFAULT_MAXIMUM_TIMEOUT_MILLIS) {
      duration = Duration.ofMillis(LDAP_TASK_DEFAULT_MAXIMUM_TIMEOUT_MILLIS);
    } else {
      duration = Duration.ofMillis(responseTimeout);
    }
    log.info(
        "NG_LDAP_DELEGATE_TASK_RESPONSE_TIMEOUT: Setting delegate tasks response timeout of {} seconds for task type: {} in account: {}",
        duration.getSeconds(), taskType, accountIdentifier);
    return duration;
  }
}
