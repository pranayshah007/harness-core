/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.event.handler.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.event.handler.impl.Constants.ACCOUNT_EVENT;
import static io.harness.event.handler.impl.Constants.ACCOUNT_ID;
import static io.harness.event.handler.impl.Constants.CATEGORY;
import static io.harness.event.handler.impl.Constants.COMPANY_NAME;
import static io.harness.event.handler.impl.Constants.COUNTRY;
import static io.harness.event.handler.impl.Constants.CUSTOM_EVENT_NAME;
import static io.harness.event.handler.impl.Constants.EMAIL_ID;
import static io.harness.event.handler.impl.Constants.FREEMIUM_ASSISTED_OPTION;
import static io.harness.event.handler.impl.Constants.FREEMIUM_PRODUCTS;
import static io.harness.event.handler.impl.Constants.PHONE;
import static io.harness.event.handler.impl.Constants.STATE;
import static io.harness.event.handler.impl.Constants.TECH_CATEGORY_NAME;
import static io.harness.event.handler.impl.Constants.TECH_NAME;
import static io.harness.event.handler.impl.Constants.USER_INVITE_ID;
import static io.harness.event.handler.impl.Constants.USER_NAME;
import static io.harness.event.handler.impl.MarketoHandler.Constants.UTM_CAMPAIGN;
import static io.harness.event.handler.impl.MarketoHandler.Constants.UTM_CONTENT;
import static io.harness.event.handler.impl.MarketoHandler.Constants.UTM_MEDIUM;
import static io.harness.event.handler.impl.MarketoHandler.Constants.UTM_SOURCE;
import static io.harness.event.handler.impl.MarketoHandler.Constants.UTM_TERM;

import static software.wings.beans.security.UserGroup.DEFAULT_ACCOUNT_ADMIN_USER_GROUP_NAME;
import static software.wings.beans.security.UserGroup.DEFAULT_NON_PROD_SUPPORT_USER_GROUP_NAME;
import static software.wings.beans.security.UserGroup.DEFAULT_PROD_SUPPORT_USER_GROUP_NAME;
import static software.wings.beans.security.UserGroup.DEFAULT_READ_ONLY_USER_GROUP_NAME;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.EnvironmentType;
import io.harness.beans.FeatureName;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter.Operator;
import io.harness.beans.SortOrder.OrderType;
import io.harness.event.handler.marketo.MarketoConfig;
import io.harness.event.handler.segment.SegmentConfig;
import io.harness.event.model.Event;
import io.harness.event.model.EventData;
import io.harness.event.model.EventType;
import io.harness.event.publisher.EventPublisher;
import io.harness.ff.FeatureFlagService;

import software.wings.beans.Account;
import software.wings.beans.AccountEvent;
import software.wings.beans.AccountEventType;
import software.wings.beans.AccountType;
import software.wings.beans.EntityType;
import software.wings.beans.Pipeline;
import software.wings.beans.TechStack;
import software.wings.beans.User;
import software.wings.beans.User.UserKeys;
import software.wings.beans.UserInvite;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.beans.security.UserGroup;
import software.wings.beans.security.access.Whitelist;
import software.wings.beans.sso.LdapSettings;
import software.wings.beans.sso.SamlSettings;
import software.wings.beans.utm.UtmInfo;
import software.wings.security.UserThreadLocal;
import software.wings.service.impl.analysis.ContinuousVerificationExecutionMetaData;
import software.wings.service.impl.analysis.ContinuousVerificationService;
import software.wings.service.impl.newrelic.LearningEngineAnalysisTask;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.SSOSettingService;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.UserService;
import software.wings.service.intfc.VerificationService;
import software.wings.service.intfc.WhitelistService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.WorkflowService;
import software.wings.service.intfc.verification.CVConfigurationService;
import software.wings.verification.CVConfiguration;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Publishes event if all the criteria is met. MarketoHandler handles the event and converts it into a marketo campaign.
 * @author rktummala on 11/27/18
 */
@OwnedBy(PL)
@Singleton
@Slf4j
@TargetModule(HarnessModule._950_EVENTS_FRAMEWORK)
public class EventPublishHelper {
  @Inject private EventPublisher eventPublisher;
  @Inject private ExecutorService executorService;
  @Inject private WorkflowExecutionService workflowExecutionService;
  @Inject private WorkflowService workflowService;
  @Inject private PipelineService pipelineService;
  @Inject private AppService appService;
  @Inject private AccountService accountService;
  @Inject private UserService userService;
  @Inject private SSOSettingService ssoSettingService;
  @Inject private WhitelistService whitelistService;
  @Inject private UserGroupService userGroupService;
  @Inject private CVConfigurationService cvConfigurationService;
  @Inject @Nullable private MarketoConfig marketoConfig;
  @Inject @Nullable private SegmentConfig segmentConfig;

  @Inject private ContinuousVerificationService continuousVerificationService;
  @Inject private VerificationService learningEngineService;
  @Inject private FeatureFlagService featureFlagService;

  public void publishLicenseChangeEvent(String accountId, String oldAccountType, String newAccountType) {
    if (!checkIfMarketoOrSegmentIsEnabled()) {
      return;
    }

    executorService.submit(() -> {
      Map<String, String> properties = new HashMap<>();
      properties.put(ACCOUNT_ID, accountId);
      publishEvent(EventType.LICENSE_UPDATE, properties);
    });

    EventType eventType = null;
    if (oldAccountType == null || newAccountType == null) {
      return;
    }

    if (oldAccountType.equals(newAccountType)) {
      return;
    }

    if (AccountType.TRIAL.equals(oldAccountType) && AccountType.PAID.equals(newAccountType)) {
      eventType = EventType.TRIAL_TO_PAID;
    } else if (AccountType.TRIAL.equals(oldAccountType) && AccountType.COMMUNITY.equals(newAccountType)) {
      eventType = EventType.TRIAL_TO_COMMUNITY;
    } else if (AccountType.COMMUNITY.equals(oldAccountType) && AccountType.PAID.equals(newAccountType)) {
      eventType = EventType.COMMUNITY_TO_PAID;
    } else if (AccountType.COMMUNITY.equals(oldAccountType) && AccountType.ESSENTIALS.equals(newAccountType)) {
      eventType = EventType.COMMUNITY_TO_ESSENTIALS;
    } else if (AccountType.ESSENTIALS.equals(oldAccountType) && AccountType.PAID.equals(newAccountType)) {
      eventType = EventType.ESSENTIALS_TO_PAID;
    } else if (AccountType.PAID.equals(oldAccountType) && AccountType.ESSENTIALS.equals(newAccountType)) {
      eventType = EventType.PAID_TO_ESSENTIALS;
    } else if (AccountType.TRIAL.equals(oldAccountType) && AccountType.ESSENTIALS.equals(newAccountType)) {
      eventType = EventType.TRIAL_TO_ESSENTIALS;
    }

    if (eventType != null) {
      EventType finalEventType = eventType;
      executorService.submit(() -> notifyAllUsersOfAccount(accountId, finalEventType));
    }
  }

  public void publishSSOEvent(String accountId) {
    String userEmail = checkIfMarketoOrSegmentIsEnabledAndGetUserEmail(EventType.SETUP_SSO);

    if (isEmpty(userEmail)) {
      return;
    }

    executorService.submit(() -> {
      if (!shouldPublishEventForAccount(accountId)) {
        return;
      }

      SamlSettings samlSettings = ssoSettingService.getSamlSettingsByAccountId(accountId);
      LdapSettings ldapSettings = ssoSettingService.getLdapSettingsByAccountId(accountId);

      boolean hasSamlSetting = samlSettings != null && samlSettings.getCreatedBy().getEmail().equals(userEmail);
      boolean hasLdapSetting = ldapSettings != null && ldapSettings.getCreatedBy().getEmail().equals(userEmail);

      boolean shouldReport = !(hasSamlSetting && hasLdapSetting) && (hasSamlSetting || hasLdapSetting);

      if (!shouldReport) {
        return;
      }

      Map<String, String> properties = new HashMap<>();
      properties.put(ACCOUNT_ID, accountId);
      properties.put(EMAIL_ID, userEmail);
      publishEvent(EventType.SETUP_SSO, properties);
    });
  }

  public void publishSetupCV247Event(String accountId, String cvConfigId) {
    String userEmail = checkIfMarketoOrSegmentIsEnabledAndGetUserEmail(EventType.SETUP_CV_24X7);

    if (isEmpty(userEmail)) {
      return;
    }

    executorService.submit(() -> {
      if (!shouldPublishEventForAccount(accountId)) {
        return;
      }

      if (!isFirstCV247ConfigInAccount(cvConfigId, accountId, userEmail)) {
        return;
      }

      Map<String, String> properties = new HashMap<>();
      properties.put(ACCOUNT_ID, accountId);
      properties.put(EMAIL_ID, userEmail);
      publishEvent(EventType.SETUP_CV_24X7, properties);
    });
  }

  private boolean isFirstCV247ConfigInAccount(String cvConfigId, String accountId, String userEmail) {
    PageRequest<CVConfiguration> pageRequest = aPageRequest()
                                                   .addFilter("accountId", Operator.EQ, accountId)
                                                   .addFilter("createdBy.email", Operator.EQ, userEmail)
                                                   .addOrder(CVConfiguration.CREATED_AT_KEY, OrderType.ASC)
                                                   .addFieldsIncluded("_id")
                                                   .withLimit("1")
                                                   .build();
    pageRequest.setOptions(Arrays.asList(PageRequest.Option.SKIPCOUNT));
    List<CVConfiguration> cvConfigurations = cvConfigurationService.listConfigurations(accountId, pageRequest);

    if (isEmpty(cvConfigurations)) {
      return false;
    }

    if (cvConfigId.equals(cvConfigurations.get(0).getUuid())) {
      return true;
    }

    return false;
  }

  public void publishSetupRbacEvent(String accountId, String entityId, EntityType entityType) {
    String userEmail = checkIfMarketoOrSegmentIsEnabledAndGetUserEmail(EventType.SETUP_RBAC);

    if (isEmpty(userEmail)) {
      return;
    }

    executorService.submit(() -> {
      if (!shouldPublishEventForAccount(accountId)) {
        return;
      }

      if (!isFirstRbacConfigInAccount(accountId, entityId, entityType, userEmail)) {
        return;
      }

      Map<String, String> properties = new HashMap<>();
      properties.put(ACCOUNT_ID, accountId);
      properties.put(EMAIL_ID, userEmail);
      publishEvent(EventType.SETUP_RBAC, properties);
    });
  }

  private boolean isFirstRbacConfigInAccount(
      String accountId, String entityId, EntityType entityType, String userEmail) {
    if (EntityType.USER_GROUP == entityType) {
      PageRequest<UserGroup> pageRequest =
          aPageRequest()
              .withLimit(Long.toString(userGroupService.getCountOfUserGroups(accountId)))
              .addFilter("accountId", Operator.EQ, accountId)
              .addFilter("createdBy.email", Operator.EQ, userEmail)
              .addOrder(UserGroup.CREATED_AT_KEY, OrderType.ASC)
              .build();
      pageRequest.setOptions(Arrays.asList(PageRequest.Option.SKIPCOUNT));
      PageResponse<UserGroup> pageResponse = userGroupService.list(accountId, pageRequest, false, null, null);
      List<UserGroup> userGroups = pageResponse.getResponse();
      if (isEmpty(userGroups)) {
        return false;
      }

      Optional<UserGroup> firstUserGroupOptional = userGroups.stream()
                                                       .filter(userGroup -> {
                                                         String userGroupName = userGroup.getName();
                                                         switch (userGroupName) {
                                                           case DEFAULT_ACCOUNT_ADMIN_USER_GROUP_NAME:
                                                           case DEFAULT_PROD_SUPPORT_USER_GROUP_NAME:
                                                           case DEFAULT_NON_PROD_SUPPORT_USER_GROUP_NAME:
                                                           case DEFAULT_READ_ONLY_USER_GROUP_NAME:
                                                             return false;
                                                           default:
                                                             return true;
                                                         }
                                                       })
                                                       .findFirst();

      if (!firstUserGroupOptional.isPresent()) {
        return false;
      }

      if (entityId.equals(firstUserGroupOptional.get().getUuid())) {
        return true;
      }
    } else if (EntityType.USER == entityType) {
      Account account = accountService.getFromCache(accountId);
      PageRequest<User> pageRequest = aPageRequest()
                                          .addFilter(UserKeys.accounts, Operator.IN, account)
                                          .addFilter("createdBy.email", Operator.EQ, userEmail)
                                          .addOrder(User.CREATED_AT_KEY, OrderType.ASC)
                                          .withLimit("10")
                                          .build();
      pageRequest.setOptions(Arrays.asList(PageRequest.Option.SKIPCOUNT));

      PageResponse<User> pageResponse = userService.list(pageRequest, false);
      List<User> users = pageResponse.getResponse();
      if (isEmpty(users)) {
        return false;
      }

      Optional<User> firstUserOptional =
          users.stream().filter(user -> !user.getEmail().endsWith("@harness.io")).findFirst();

      if (!firstUserOptional.isPresent()) {
        return false;
      }

      if (entityId.equals(firstUserOptional.get().getUuid())) {
        return true;
      }

      return false;
    }
    return false;
  }

  public void publishSetupIPWhitelistingEvent(String accountId, String whitelistId) {
    String userEmail = checkIfMarketoOrSegmentIsEnabledAndGetUserEmail(EventType.SETUP_IP_WHITELISTING);

    if (isEmpty(userEmail)) {
      return;
    }

    executorService.submit(() -> {
      if (!shouldPublishEventForAccount(accountId)) {
        return;
      }

      if (!isFirstWhitelistConfigInAccount(whitelistId, accountId, userEmail)) {
        return;
      }

      Map<String, String> properties = new HashMap<>();
      properties.put(ACCOUNT_ID, accountId);
      properties.put(EMAIL_ID, userEmail);
      publishEvent(EventType.SETUP_IP_WHITELISTING, properties);
    });
  }

  private boolean isFirstWhitelistConfigInAccount(String whitelistId, String accountId, String userEmail) {
    PageRequest<Whitelist> pageRequest = aPageRequest()
                                             .addFilter("accountId", Operator.EQ, accountId)
                                             .addFilter("createdBy.email", Operator.EQ, userEmail)
                                             .addOrder(Whitelist.CREATED_AT_KEY, OrderType.ASC)
                                             .addFieldsIncluded("_id")
                                             .withLimit("1")
                                             .build();
    pageRequest.setOptions(Arrays.asList(PageRequest.Option.SKIPCOUNT));
    PageResponse<Whitelist> pageResponse = whitelistService.list(accountId, pageRequest);
    List<Whitelist> whitelistConfigs = pageResponse.getResponse();
    if (isEmpty(whitelistConfigs)) {
      return false;
    }

    if (whitelistId.equals(whitelistConfigs.get(0).getUuid())) {
      return true;
    }

    return false;
  }

  public void publishSetup2FAEvent(String accountId) {
    String userEmail = checkIfMarketoOrSegmentIsEnabledAndGetUserEmail(EventType.SETUP_2FA);

    if (isEmpty(userEmail)) {
      return;
    }

    executorService.submit(() -> {
      if (!shouldPublishEventForAccount(accountId)) {
        return;
      }

      Map<String, String> properties = new HashMap<>();
      properties.put(ACCOUNT_ID, accountId);
      properties.put(EMAIL_ID, userEmail);
      publishEvent(EventType.SETUP_2FA, properties);
    });
  }

  private void notifyAllUsersOfAccount(String accountId, EventType eventType) {
    Map<String, String> properties = new HashMap<>();
    properties.put(ACCOUNT_ID, accountId);
    publishEvent(eventType, properties);
  }

  public void publishUserInviteFromAccountEvent(String accountId, String email) {
    if (!checkIfMarketoOrSegmentIsEnabled()) {
      return;
    }

    executorService.submit(() -> {
      if (!shouldPublishEventForAccount(accountId)) {
        return;
      }

      Map<String, String> properties = new HashMap<>();
      properties.put(ACCOUNT_ID, accountId);
      properties.put(EMAIL_ID, email);
      publishEvent(EventType.USER_INVITED_FROM_EXISTING_ACCOUNT, properties);
    });
  }

  public void publishUserInviteVerifiedFromAccountEvent(String accountId, String email) {
    if (!checkIfSegmentIsEnabled()) {
      return;
    }

    executorService.submit(() -> {
      if (!shouldPublishEventForAccount(accountId)) {
        return;
      }

      Map<String, String> properties = new HashMap<>();
      properties.put(ACCOUNT_ID, accountId);
      properties.put(EMAIL_ID, email);
      publishEvent(EventType.USER_INVITE_ACCEPTED_FOR_TRIAL_ACCOUNT, properties);
    });
  }

  private void publishEvent(EventType eventType, Map<String, String> properties) {
    eventPublisher.publishEvent(
        Event.builder().eventType(eventType).eventData(EventData.builder().properties(properties).build()).build());
  }

  public void publishWorkflowCreatedEvent(Workflow workflow, String accountId) {
    String userEmail = checkIfMarketoOrSegmentIsEnabledAndGetUserEmail(EventType.FIRST_WORKFLOW_CREATED);

    if (isEmpty(userEmail)) {
      return;
    }

    publishAccountEvent(
        accountId, AccountEvent.builder().accountEventType(AccountEventType.WORKFLOW_CREATED).build(), true, true);

    executorService.submit(() -> {
      if (!shouldPublishEventForAccount(accountId)) {
        return;
      }

      if (!isFirstWorkflowInAccount(workflow, accountId, userEmail)) {
        return;
      }

      Map<String, String> properties = new HashMap<>();
      properties.put(ACCOUNT_ID, accountId);
      properties.put(EMAIL_ID, userEmail);
      publishEvent(EventType.FIRST_WORKFLOW_CREATED, properties);
    });
  }

  private boolean isFirstWorkflowInAccount(Workflow workflow, String accountId, String userEmail) {
    if (workflow.isSample()) {
      return false;
    }

    PageRequest<Workflow> pageRequest = aPageRequest()
                                            .addFilter("accountId", Operator.EQ, accountId)
                                            .addFilter("createdBy.email", Operator.EQ, userEmail)
                                            .addFilter("sample", Operator.EQ, false)
                                            .addOrder(Workflow.CREATED_AT_KEY, OrderType.ASC)
                                            .addFieldsIncluded("_id")
                                            .withLimit("1")
                                            .build();
    pageRequest.setOptions(Arrays.asList(PageRequest.Option.SKIPCOUNT));
    PageResponse<Workflow> pageResponse = workflowService.listWorkflows(pageRequest);
    List<Workflow> workflows = pageResponse.getResponse();
    if (isEmpty(workflows)) {
      return false;
    }

    if (workflow.getUuid().equals(workflows.get(0).getUuid())) {
      return true;
    }

    return false;
  }

  public void publishUserRegistrationCompletionEvent(String accountId, User user) {
    if (user == null) {
      return;
    }

    if (!shouldPublishEventForAccount(accountId)) {
      return;
    }

    Map<String, String> properties = new HashMap<>();
    properties.put(ACCOUNT_ID, accountId);
    properties.put(EMAIL_ID, user.getEmail());
    setUTMDataToProperties(properties, user.getUtmInfo());
    publishEvent(EventType.COMPLETE_USER_REGISTRATION, properties);
  }

  public void publishTrialUserSignupEvent(String inviteId, String email, UserInvite userInvite) {
    if (isEmpty(email)) {
      return;
    }

    Map<String, String> properties = getProperties(email, inviteId, userInvite);

    setUTMDataToProperties(properties, userInvite.getUtmInfo());
    publishEvent(EventType.NEW_TRIAL_SIGNUP, properties);
  }

  public void setUTMDataToProperties(Map<String, String> properties, UtmInfo utmInfo) {
    if (utmInfo != null) {
      properties.put(UTM_SOURCE, utmInfo.getUtmSource());
      properties.put(UTM_CONTENT, utmInfo.getUtmContent());
      properties.put(UTM_MEDIUM, utmInfo.getUtmMedium());
      properties.put(UTM_TERM, utmInfo.getUtmTerm());
      properties.put(UTM_CAMPAIGN, utmInfo.getUtmCampaign());
    }
  }

  public void publishTrialUserSignupEvent(String email, String userName, String inviteId, String companyName) {
    if (isEmpty(email)) {
      return;
    }

    publishEvent(EventType.NEW_TRIAL_SIGNUP, getProperties(null, email, userName, inviteId, companyName));
  }

  public void publishJoinAccountEvent(String email, String name, String companyName) {
    if (isEmpty(email)) {
      return;
    }

    publishEvent(EventType.JOIN_ACCOUNT_REQUEST, getProperties(null, email, name, null, companyName));
  }

  public void publishTechStackEvent(String accountId, Set<TechStack> techStacks) {
    if (isEmpty(accountId) || isEmpty(techStacks)) {
      return;
    }

    String userEmail = checkIfMarketoOrSegmentIsEnabledAndGetUserEmail(EventType.TECH_STACK);

    if (isEmpty(userEmail)) {
      return;
    }

    executorService.submit(() -> {
      if (!shouldPublishEventForAccount(accountId)) {
        return;
      }

      Map<String, String> properties = getProperties(accountId, userEmail);
      techStacks.forEach(techStack -> {
        properties.put(TECH_NAME, techStack.getTechnology());
        properties.put(TECH_CATEGORY_NAME, techStack.getCategory());
        publishEvent(EventType.TECH_STACK, properties);
      });
    });
  }

  private String checkIfMarketoOrSegmentIsEnabledAndGetUserEmail(EventType eventType) {
    if (!checkIfMarketoOrSegmentIsEnabled()) {
      return null;
    }

    User user = UserThreadLocal.get();
    if (!shouldPublishEventForUser(user)) {
      return null;
    }

    if (isEventAlreadyReportedForUser(user, eventType)) {
      return null;
    }

    return user.getEmail();
  }

  private String checkIfMarketoOrSegmentIsEnabledAndGetUserEmail(String customEvent) {
    if (!checkIfMarketoOrSegmentIsEnabled()) {
      return null;
    }

    User user = UserThreadLocal.get();
    if (!shouldPublishEventForUser(user)) {
      return null;
    }

    if (isCustomEventAlreadyReportedForUser(user, customEvent)) {
      return null;
    }

    return user.getEmail();
  }

  private boolean shouldPublishEventForUser(User user) {
    if (user == null) {
      return false;
    }

    List<Account> accounts = user.getAccounts();
    if (isEmpty(accounts)) {
      return false;
    }

    //    if (accounts.size() > 1) {
    //      return false;
    //    }

    return true;
  }

  private boolean isEventAlreadyReportedForUser(User user, EventType eventType) {
    // only report event if not reported already
    Set<String> reportedMarketoCampaigns = user.getReportedMarketoCampaigns();
    Set<String> reportedSegmentTracks = user.getReportedSegmentTracks();

    if (isEmpty(reportedMarketoCampaigns) || isEmpty(reportedSegmentTracks)) {
      return false;
    }

    return reportedMarketoCampaigns.contains(eventType.name()) && reportedSegmentTracks.contains(eventType.name());
  }

  // Custom events are only being sent to
  private boolean isCustomEventAlreadyReportedForUser(User user, String event) {
    // only report event if not reported already
    Set<String> reportedSegmentTracks = user.getReportedSegmentTracks();

    if (isEmpty(reportedSegmentTracks)) {
      return false;
    }

    return reportedSegmentTracks.contains(event);
  }

  private boolean shouldPublishEventForAccount(String accountId) {
    Account account = accountService.getFromCache(accountId);
    return isTrialAccount(account);
  }

  private boolean isTrialAccount(Account account) {
    if (account == null) {
      return false;
    }

    if (account.getLicenseInfo() == null) {
      return false;
    }

    if (!AccountType.TRIAL.equals(account.getLicenseInfo().getAccountType())) {
      return false;
    }
    return true;
  }

  private boolean shouldPublishAccountEventForAccount(
      String accountId, AccountEvent accountEvent, boolean oneTimeOnly, boolean trialOnly) {
    Account account = accountService.getFromCache(accountId);
    if (trialOnly && !isTrialAccount(account)) {
      return false;
    }

    if (oneTimeOnly && isNotEmpty(account.getAccountEvents()) && account.getAccountEvents().contains(accountEvent)) {
      return false;
    }

    return true;
  }

  private boolean checkIfMarketoOrSegmentIsEnabled() {
    return (marketoConfig != null && marketoConfig.isEnabled()) || (segmentConfig != null && segmentConfig.isEnabled());
  }

  private boolean checkIfSegmentIsEnabled() {
    return segmentConfig != null && segmentConfig.isEnabled();
  }

  public boolean publishVerificationWorkflowMetrics(
      WorkflowExecution workflowExecution, List<String> appIds, String accountId, boolean isVerificationRolledBack) {
    PageRequest<ContinuousVerificationExecutionMetaData> cvPageRequest =
        aPageRequest()
            .addFilter("appId", Operator.IN, appIds.toArray())
            .addFilter("workflowExecutionId", Operator.EQ, workflowExecution.getUuid())
            .addFieldsIncluded("_id")
            .withLimit("1")
            .build();
    cvPageRequest.setOptions(Arrays.asList(PageRequest.Option.SKIPCOUNT));

    List<ContinuousVerificationExecutionMetaData> cvExecutionMetaDataList =
        continuousVerificationService.getCVDeploymentData(cvPageRequest);

    if (!isEmpty(cvExecutionMetaDataList)) {
      Account account = accountService.get(accountId);
      Map<String, String> properties = new HashMap<>();
      properties.put("accountId", accountId);
      properties.put("workflowExecutionId", workflowExecution.getUuid());
      properties.put("rollback", String.valueOf(isVerificationRolledBack));
      properties.put("envType", workflowExecution.getEnvType().name());
      properties.put("workflowStatus", workflowExecution.getStatus().name());
      properties.put("rollbackType", "MANUAL");
      properties.put("accountName", account.getAccountName());
      if (account.getLicenseInfo() != null) {
        properties.put("licenseType", account.getLicenseInfo().getAccountType());
      }

      publishEvent(EventType.DEPLOYMENT_VERIFIED, properties);
      return true;
    }

    return false;
  }

  public void publishServiceGuardSetupEvent(@NonNull Account account, String verificationProviderType,
      List<String> configIds, long alerts, EnvironmentType environmentType, boolean enabled) {
    Optional<LearningEngineAnalysisTask> lastTask = learningEngineService.getLatestTaskForCvConfigIds(configIds);
    executorService.submit(() -> {
      Map<String, String> properties = new HashMap<>();
      properties.put("accountId", account.getUuid());
      properties.put("verificationProviderType", verificationProviderType);
      properties.put("configs", String.valueOf(configIds.size()));
      properties.put("alerts", String.valueOf(alerts));
      if (account.getLicenseInfo() != null) {
        properties.put("licenseType", account.getLicenseInfo().getAccountType());
      }
      properties.put("accountName", account.getAccountName());
      properties.put("environmentType", environmentType.name());
      properties.put("enabled", String.valueOf(enabled));
      lastTask.ifPresent(task -> {
        boolean hasData = learningEngineService.checkIfAnalysisHasData(
            task.getCvConfigId(), task.getMl_analysis_type(), task.getAnalysis_minute());
        properties.put("lastExecutionTime", String.valueOf(task.getLastUpdatedAt()));
        properties.put("hasData", String.valueOf(hasData));
      });
      publishEvent(EventType.SERVICE_GUARD_SETUP, properties);
    });
  }

  public void handleDeploymentCompleted(WorkflowExecution workflowExecution) {
    if (workflowExecution == null) {
      return;
    }

    executorService.submit(() -> {
      String appId = workflowExecution.getAppId();
      String workflowExecutionId = workflowExecution.getUuid();

      String accountId = appService.getAccountIdByAppId(appId);

      List<String> appIds = appService.getAppIdsByAccountId(accountId);

      boolean workflowRolledBack = workflowExecution.getRollbackDuration() != null;
      boolean workflowWithVerification =
          publishVerificationWorkflowMetrics(workflowExecution, appIds, accountId, workflowRolledBack);

      if (!checkIfMarketoOrSegmentIsEnabled()) {
        return;
      }

      if (!shouldPublishEventForAccount(accountId)) {
        return;
      }

      EmbeddedUser createdBy = workflowExecution.getCreatedBy();

      if (createdBy == null) {
        log.info("CreatedBy is null for execution id {}", workflowExecutionId);
        return;
      }

      String userEmail = createdBy.getEmail();

      if (isEmpty(userEmail)) {
        log.info("CreatedBy user email is null for execution id {}", workflowExecutionId);
        return;
      }

      String userId = createdBy.getUuid();
      if (isEmpty(userId)) {
        log.info("CreatedBy user id is null for execution id {}", workflowExecutionId);
        return;
      }

      User user = userService.getUserFromCacheOrDB(userId);
      if (!shouldPublishEventForUser(user)) {
        log.info("Skipping publish event for user {} and execution id {}", userEmail, workflowExecutionId);
        return;
      }

      if (!isEventAlreadyReportedForUser(user, EventType.FIRST_DEPLOYMENT_EXECUTED)) {
        publishIfFirstDeployment(workflowExecutionId, appIds, accountId, userEmail);
      }

      if (workflowExecution.getPipelineSummary() != null) {
        Pipeline pipeline =
            pipelineService.readPipeline(appId, workflowExecution.getPipelineSummary().getPipelineId(), false);
        if (!pipeline.isSample()) {
          publishAccountEvent(accountId,
              AccountEvent.builder().accountEventType(AccountEventType.PIPELINE_DEPLOYED).build(), user, true, true);
        }
      } else {
        Workflow workflow = workflowService.readWorkflowWithoutServices(appId, workflowExecution.getWorkflowId());
        if (!workflow.isSample()) {
          publishAccountEvent(accountId,
              AccountEvent.builder().accountEventType(AccountEventType.WORKFLOW_DEPLOYED).build(), user, true, true);
        }
      }

      if (!isEventAlreadyReportedForUser(user, EventType.FIRST_ROLLED_BACK_DEPLOYMENT) && workflowRolledBack) {
        publishEvent(EventType.FIRST_ROLLED_BACK_DEPLOYMENT, getProperties(accountId, userEmail));
      }

      if (!isEventAlreadyReportedForUser(user, EventType.FIRST_VERIFIED_DEPLOYMENT) && workflowWithVerification) {
        publishEvent(EventType.FIRST_VERIFIED_DEPLOYMENT, getProperties(accountId, userEmail));
      }
    });
  }

  @VisibleForTesting
  void publishIfFirstDeployment(String workflowExecutionId, List<String> appIds, String accountId, String userEmail) {
    if (featureFlagService.isNotEnabled(FeatureName.SPG_CG_SEGMENT_EVENT_FIRST_DEPLOYMENT, accountId)) {
      PageRequest<WorkflowExecution> executionPageRequest =
          aPageRequest()
              .addFilter("accountId", Operator.EQ, accountId)
              .addFilter("createdBy.email", Operator.EQ, userEmail)
              .addOrder(WorkflowExecutionKeys.createdAt, OrderType.ASC)
              .withLimit("1")
              .build();
      executionPageRequest.setOptions(Arrays.asList(PageRequest.Option.SKIPCOUNT));

      PageResponse<WorkflowExecution> executionPageResponse =
          workflowExecutionService.listExecutions(executionPageRequest, false);
      List<WorkflowExecution> workflowExecutions = executionPageResponse.getResponse();

      if (isNotEmpty(workflowExecutions)) {
        WorkflowExecution workflowExecution = workflowExecutions.get(0);
        if (workflowExecutionId.equals(workflowExecution.getUuid())) {
          publishEvent(EventType.FIRST_DEPLOYMENT_EXECUTED, getProperties(accountId, userEmail));
        }
      }
    }
  }

  private Map<String, String> getProperties(String accountId, String userEmail) {
    Map<String, String> properties = new HashMap<>();
    properties.put(ACCOUNT_ID, accountId);
    properties.put(EMAIL_ID, userEmail);
    return properties;
  }

  private Map<String, String> getProperties(
      String accountId, String userEmail, String userName, String userInviteId, String companyName) {
    Map<String, String> properties = new HashMap<>();
    properties.put(ACCOUNT_ID, accountId);
    properties.put(EMAIL_ID, userEmail);
    properties.put(USER_NAME, userName);
    properties.put(USER_INVITE_ID, userInviteId);
    properties.put(COMPANY_NAME, companyName);
    return properties;
  }

  private Map<String, String> getProperties(String userEmail, String userInviteId, UserInvite userInvite) {
    Map<String, String> properties = new HashMap<>();
    properties.put(ACCOUNT_ID, null);
    properties.put(EMAIL_ID, userEmail);
    properties.put(USER_NAME, userInvite.getEmail());
    properties.put(USER_INVITE_ID, userInviteId);
    properties.put(COMPANY_NAME, userInvite.getCompanyName());

    List<String> freemiumProducts = userInvite.getFreemiumProducts();
    if (isNotEmpty(freemiumProducts)) {
      properties.put(FREEMIUM_PRODUCTS, String.join(", ", freemiumProducts));
    }
    Boolean freemiumAssistedOption = userInvite.getFreemiumAssistedOption();
    if (freemiumAssistedOption != null) {
      properties.put(FREEMIUM_ASSISTED_OPTION, String.valueOf(freemiumAssistedOption));
    }
    String country = userInvite.getCountry();
    if (country != null) {
      properties.put(COUNTRY, country);
    }
    String state = userInvite.getState();
    if (state != null) {
      properties.put(STATE, state);
    }
    String phone = userInvite.getPhone();
    if (phone != null) {
      properties.put(PHONE, phone);
    }
    return properties;
  }

  public void publishCustomEvent(String accountId, String customEvent) {
    String userEmail = checkIfMarketoOrSegmentIsEnabledAndGetUserEmail(customEvent);

    if (isEmpty(userEmail)) {
      return;
    }

    executorService.submit(() -> {
      if (!shouldPublishEventForAccount(accountId)) {
        return;
      }

      Map<String, String> properties = new HashMap<>();
      properties.put(ACCOUNT_ID, accountId);
      properties.put(EMAIL_ID, userEmail);
      properties.put(CUSTOM_EVENT_NAME, customEvent);
      publishEvent(EventType.CUSTOM, properties);
    });
  }

  /**
   * Run sample pipeline
   * Connect to a cloud provider
   * Connect to an artifact repo
   * Create an app
   * Create a service
   * Create an env
   * Create a workflow
   * Deploy your workflow
   * @param accountId
   * @param accountEvent
   */
  public void publishAccountEvent(String accountId, AccountEvent accountEvent, boolean oneTimeOnly, boolean trialOnly) {
    User user = UserThreadLocal.get();
    publishAccountEvent(accountId, accountEvent, user, oneTimeOnly, trialOnly);
  }

  public void publishAccountEvent(
      String accountId, AccountEvent accountEvent, User user, boolean oneTimeOnly, boolean trialOnly) {
    if (!checkIfMarketoOrSegmentIsEnabled()) {
      return;
    }

    if (!shouldPublishEventForUser(user)) {
      return;
    }

    String userEmail = user.getEmail();

    executorService.submit(() -> {
      if (!shouldPublishAccountEventForAccount(accountId, accountEvent, oneTimeOnly, trialOnly)) {
        return;
      }

      if (oneTimeOnly) {
        accountService.updateAccountEvents(accountId, accountEvent);
      }

      Map<String, String> properties =
          accountEvent.getProperties() == null ? new HashMap<>() : accountEvent.getProperties();
      properties.put(ACCOUNT_ID, accountId);
      properties.put(EMAIL_ID, userEmail);
      properties.put(ACCOUNT_EVENT, String.valueOf(true));
      properties.put(CUSTOM_EVENT_NAME, accountEvent.getCustomMsg());
      if (isNotEmpty(accountEvent.getCategory())) {
        properties.put(CATEGORY, accountEvent.getCategory());
      }
      publishEvent(EventType.CUSTOM, properties);
    });
  }
}
