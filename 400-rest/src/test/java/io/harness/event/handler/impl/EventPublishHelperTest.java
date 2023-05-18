/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.handler.impl;

import static io.harness.beans.FeatureName.SPG_CG_SEGMENT_EVENT_FIRST_DEPLOYMENT;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.FERNANDOD;
import static io.harness.rule.OwnerRule.RAMA;
import static io.harness.rule.OwnerRule.SOWMYA;
import static io.harness.rule.OwnerRule.VIKAS;

import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.CV_CONFIG_ID;
import static software.wings.utils.WingsTestConstants.STATE_EXECUTION_ID;
import static software.wings.utils.WingsTestConstants.USER1_EMAIL;
import static software.wings.utils.WingsTestConstants.USER_GROUP_ID;
import static software.wings.utils.WingsTestConstants.USER_ID;
import static software.wings.utils.WingsTestConstants.WHITELIST_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_EXECUTION_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.EmbeddedUser;
import io.harness.beans.EnvironmentType;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.PageResponse.PageResponseBuilder;
import io.harness.category.element.UnitTests;
import io.harness.event.handler.marketo.MarketoConfig;
import io.harness.event.handler.segment.SegmentConfig;
import io.harness.event.model.Event;
import io.harness.event.model.EventType;
import io.harness.event.publisher.EventPublisher;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.EntityType;
import software.wings.beans.User;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.security.UserGroup;
import software.wings.beans.security.access.Whitelist;
import software.wings.events.TestUtils;
import software.wings.security.UserThreadLocal;
import software.wings.service.impl.analysis.ContinuousVerificationExecutionMetaData;
import software.wings.service.impl.analysis.ContinuousVerificationService;
import software.wings.service.impl.analysis.MLAnalysisType;
import software.wings.service.impl.newrelic.LearningEngineAnalysisTask;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.UserService;
import software.wings.service.intfc.VerificationService;
import software.wings.service.intfc.WhitelistService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.WorkflowService;
import software.wings.service.intfc.verification.CVConfigurationService;
import software.wings.sm.StateExecutionInstance;
import software.wings.verification.CVConfiguration;
import software.wings.verification.apm.APMCVServiceConfiguration;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * @author rktummala on 12/05/18
 */
public class EventPublishHelperTest extends WingsBaseTest {
  @Mock private AccountService accountService;
  @Mock private AppService appService;
  @Mock private EventPublisher eventPublisher;
  @Mock private DelegateService delegateService;
  @Mock private WorkflowService workflowService;
  @Mock private WorkflowExecutionService executionService;
  @Mock private StateExecutionService stateExecutionService;
  @Mock private CVConfigurationService cvConfigurationService;
  @Mock private UserGroupService userGroupService;
  @Mock private UserService userService;
  @Mock private WhitelistService whitelistService;
  @Inject private TestUtils eventTestHelper;
  @Mock private ContinuousVerificationService continuousVerificationService;
  @Mock private VerificationService learningEngineService;
  @Mock private FeatureFlagService featureFlagService;

  private User user;
  private Account account;

  @InjectMocks @Inject private EventPublishHelper eventPublishHelper = spy(EventPublishHelper.class);

  @Before
  public void setup() throws IllegalAccessException {
    account = eventTestHelper.createAccount();
    MarketoConfig marketoConfig = eventTestHelper.initializeMarketoConfig();
    FieldUtils.writeField(eventPublishHelper, "marketoConfig", marketoConfig, true);
    FieldUtils.writeField(eventPublishHelper, "learningEngineService", learningEngineService, true);
    when(accountService.get(ACCOUNT_ID)).thenReturn(account);
    when(accountService.getFromCache(ACCOUNT_ID)).thenReturn(account);
    when(accountService.save(any(), eq(false))).thenReturn(account);
    user = eventTestHelper.createUser(account);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testSendFirstWorkflowEvent() {
    UserThreadLocal.set(user);
    try {
      Workflow workflow = aWorkflow().uuid(WORKFLOW_ID).sample(false).build();
      when(workflowService.listWorkflows(any(PageRequest.class)))
          .thenReturn(PageResponseBuilder.aPageResponse().withResponse(Arrays.asList(workflow)).withTotal(1).build());
      eventPublishHelper.publishWorkflowCreatedEvent(workflow, ACCOUNT_ID);
      verify(eventPublisher, times(2)).publishEvent(any(Event.class));
    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testSendFirstCV247Event() {
    UserThreadLocal.set(user);
    try {
      when(cvConfigurationService.listConfigurations(anyString(), any(PageRequest.class)))
          .thenReturn(PageResponseBuilder.aPageResponse().build());
      eventPublishHelper.publishSetupCV247Event(ACCOUNT_ID, CV_CONFIG_ID);
      verify(eventPublisher, never()).publishEvent(any(Event.class));

      CVConfiguration cvConfiguration = new APMCVServiceConfiguration();
      cvConfiguration.setUuid("invalid");
      when(cvConfigurationService.listConfigurations(anyString(), any(PageRequest.class)))
          .thenReturn(
              PageResponseBuilder.aPageResponse().withResponse(Arrays.asList(cvConfiguration)).withTotal(1).build());
      eventPublishHelper.publishSetupCV247Event(ACCOUNT_ID, CV_CONFIG_ID);
      verify(eventPublisher, never()).publishEvent(any(Event.class));

      cvConfiguration = new APMCVServiceConfiguration();
      cvConfiguration.setUuid(CV_CONFIG_ID);
      when(cvConfigurationService.listConfigurations(anyString(), any(PageRequest.class)))
          .thenReturn(
              PageResponseBuilder.aPageResponse().withResponse(Arrays.asList(cvConfiguration)).withTotal(1).build());
      eventPublishHelper.publishSetupCV247Event(ACCOUNT_ID, CV_CONFIG_ID);
      verify(eventPublisher, times(1)).publishEvent(any(Event.class));
    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testSendRBACEventForFirstUserGroup() {
    UserThreadLocal.set(user);
    try {
      when(userGroupService.list(anyString(), any(PageRequest.class), anyBoolean(), any(), any()))
          .thenReturn(PageResponseBuilder.aPageResponse().build());
      eventPublishHelper.publishSetupRbacEvent(ACCOUNT_ID, USER_GROUP_ID, EntityType.USER_GROUP);
      verify(eventPublisher, never()).publishEvent(any(Event.class));

      UserGroup userGroup = UserGroup.builder().uuid("invalid").name("userGroup1").build();
      when(userGroupService.list(anyString(), any(PageRequest.class), anyBoolean(), any(), any()))
          .thenReturn(PageResponseBuilder.aPageResponse().withResponse(Arrays.asList(userGroup)).withTotal(1).build());
      eventPublishHelper.publishSetupRbacEvent(ACCOUNT_ID, USER_GROUP_ID, EntityType.USER_GROUP);
      verify(eventPublisher, never()).publishEvent(any(Event.class));

      userGroup = UserGroup.builder().uuid(USER_GROUP_ID).name("userGroup1").build();
      when(userGroupService.list(anyString(), any(PageRequest.class), anyBoolean(), any(), any()))
          .thenReturn(PageResponseBuilder.aPageResponse().withResponse(Arrays.asList(userGroup)).withTotal(1).build());
      eventPublishHelper.publishSetupRbacEvent(ACCOUNT_ID, USER_GROUP_ID, EntityType.USER_GROUP);
      verify(eventPublisher, times(1)).publishEvent(any(Event.class));
    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testSendRBACEventForFirstUser() {
    UserThreadLocal.set(user);
    try {
      when(userService.list(any(PageRequest.class), anyBoolean()))
          .thenReturn(PageResponseBuilder.aPageResponse().build());
      eventPublishHelper.publishSetupRbacEvent(ACCOUNT_ID, USER_ID, EntityType.USER);
      verify(eventPublisher, never()).publishEvent(any(Event.class));

      User user = User.Builder.anUser().uuid("invalid").email("invalid@abcd.com").build();
      when(userService.list(any(PageRequest.class), anyBoolean()))
          .thenReturn(PageResponseBuilder.aPageResponse().withResponse(Arrays.asList(user)).withTotal(1).build());
      eventPublishHelper.publishSetupRbacEvent(ACCOUNT_ID, USER_ID, EntityType.USER);
      verify(eventPublisher, never()).publishEvent(any(Event.class));

      user = User.Builder.anUser().uuid(USER_ID).email("valid@abcd.com").build();
      when(userService.list(any(PageRequest.class), anyBoolean()))
          .thenReturn(PageResponseBuilder.aPageResponse().withResponse(Arrays.asList(user)).withTotal(1).build());
      eventPublishHelper.publishSetupRbacEvent(ACCOUNT_ID, USER_ID, EntityType.USER);
      verify(eventPublisher, times(1)).publishEvent(any(Event.class));
    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testSendWhitelistEvent() {
    UserThreadLocal.set(user);
    try {
      when(whitelistService.list(anyString(), any(PageRequest.class)))
          .thenReturn(PageResponseBuilder.aPageResponse().build());
      eventPublishHelper.publishSetupIPWhitelistingEvent(ACCOUNT_ID, WHITELIST_ID);
      verify(eventPublisher, never()).publishEvent(any(Event.class));

      Whitelist whitelist = Whitelist.builder().uuid("invalid").build();
      when(whitelistService.list(anyString(), any(PageRequest.class)))
          .thenReturn(PageResponseBuilder.aPageResponse().withResponse(Arrays.asList(whitelist)).withTotal(1).build());
      eventPublishHelper.publishSetupIPWhitelistingEvent(ACCOUNT_ID, WHITELIST_ID);
      verify(eventPublisher, never()).publishEvent(any(Event.class));

      whitelist = Whitelist.builder().uuid(WHITELIST_ID).build();
      when(whitelistService.list(anyString(), any(PageRequest.class)))
          .thenReturn(PageResponseBuilder.aPageResponse().withResponse(Arrays.asList(whitelist)).withTotal(1).build());
      eventPublishHelper.publishSetupIPWhitelistingEvent(ACCOUNT_ID, WHITELIST_ID);
      verify(eventPublisher, times(1)).publishEvent(any(Event.class));
    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testSend2FASetupEvent() {
    UserThreadLocal.set(user);
    try {
      eventPublishHelper.publishSetup2FAEvent(ACCOUNT_ID);
      verify(eventPublisher, times(1)).publishEvent(any(Event.class));
    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testSendUserInviteEvent() {
    UserThreadLocal.set(user);
    try {
      eventPublishHelper.publishUserInviteFromAccountEvent(ACCOUNT_ID, "abcd@abcd.com");
      verify(eventPublisher, times(1)).publishEvent(any(Event.class));
    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testConfirmUserRegistrationEvent() {
    UserThreadLocal.set(user);
    try {
      User newUser = User.Builder.anUser().email("abcd@abcd.com").build();
      eventPublishHelper.publishUserRegistrationCompletionEvent(ACCOUNT_ID, newUser);
      verify(eventPublisher, times(1)).publishEvent(any(Event.class));
    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testFirstDeploymentEvent() {
    UserThreadLocal.set(user);
    try {
      when(userService.getUserFromCacheOrDB(user.getUuid())).thenReturn(user);
      when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
      when(appService.getAppIdsByAccountId(ACCOUNT_ID)).thenReturn(Arrays.asList(APP_ID));
      when(featureFlagService.isNotEnabled(SPG_CG_SEGMENT_EVENT_FIRST_DEPLOYMENT, ACCOUNT_ID)).thenReturn(true);

      WorkflowExecution workflowExecution =
          WorkflowExecution.builder()
              .uuid(WORKFLOW_EXECUTION_ID)
              .createdBy(EmbeddedUser.builder().email("abcd@abcd.com").uuid(user.getUuid()).build())
              .appId(APP_ID)
              .build();
      when(stateExecutionService.list(any(PageRequest.class))).thenReturn(PageResponseBuilder.aPageResponse().build());
      when(continuousVerificationService.getCVDeploymentData(any(PageRequest.class)))
          .thenReturn(PageResponseBuilder.aPageResponse().build());
      when(executionService.listExecutions(any(PageRequest.class), anyBoolean()))
          .thenReturn(
              PageResponseBuilder.aPageResponse().withResponse(Arrays.asList(workflowExecution)).withTotal(1).build());
      eventPublishHelper.handleDeploymentCompleted(workflowExecution);
      verify(eventPublisher, times(1)).publishEvent(any(Event.class));
    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testFirstRollbackEvent() {
    UserThreadLocal.set(user);
    try {
      when(userService.getUserFromCacheOrDB(user.getUuid())).thenReturn(user);
      when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
      when(appService.getAppIdsByAccountId(ACCOUNT_ID)).thenReturn(Arrays.asList(APP_ID));
      when(featureFlagService.isNotEnabled(SPG_CG_SEGMENT_EVENT_FIRST_DEPLOYMENT, ACCOUNT_ID)).thenReturn(true);
      WorkflowExecution workflowExecution =
          WorkflowExecution.builder()
              .uuid(WORKFLOW_EXECUTION_ID)
              .appId(APP_ID)
              .createdBy(EmbeddedUser.builder().email("abcd@abcd.com").uuid(user.getUuid()).build())
              .build();
      when(executionService.getExecutionWithoutSummary(APP_ID, WORKFLOW_EXECUTION_ID)).thenReturn(workflowExecution);
      when(executionService.listExecutions(any(PageRequest.class), anyBoolean()))
          .thenReturn(
              PageResponseBuilder.aPageResponse().withResponse(Arrays.asList(workflowExecution)).withTotal(1).build());
      when(stateExecutionService.list(any(PageRequest.class)))
          .thenReturn(
              PageResponseBuilder.aPageResponse().withResponse(PageResponseBuilder.aPageResponse().build()).build());
      when(continuousVerificationService.getCVDeploymentData(any(PageRequest.class)))
          .thenReturn(PageResponseBuilder.aPageResponse().build());
      StateExecutionInstance stateExecutionInstance = StateExecutionInstance.Builder.aStateExecutionInstance()
                                                          .uuid(STATE_EXECUTION_ID)
                                                          .executionUuid(WORKFLOW_EXECUTION_ID)
                                                          .build();
      when(stateExecutionService.list(any(PageRequest.class)))
          .thenReturn(PageResponseBuilder.aPageResponse()
                          .withResponse(Arrays.asList(stateExecutionInstance))
                          .withTotal(1)
                          .build());
      eventPublishHelper.handleDeploymentCompleted(workflowExecution);
      verify(eventPublisher, times(1)).publishEvent(any(Event.class));
    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testFirstVerifiedEvent() {
    UserThreadLocal.set(user);
    try {
      when(userService.getUserFromCacheOrDB(user.getUuid())).thenReturn(user);
      when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
      when(appService.getAppIdsByAccountId(ACCOUNT_ID)).thenReturn(Arrays.asList(APP_ID));
      when(featureFlagService.isNotEnabled(SPG_CG_SEGMENT_EVENT_FIRST_DEPLOYMENT, ACCOUNT_ID)).thenReturn(true);
      WorkflowExecution workflowExecution =
          WorkflowExecution.builder()
              .uuid(WORKFLOW_EXECUTION_ID)
              .appId(APP_ID)
              .createdBy(EmbeddedUser.builder().email("abcd@abcd.com").uuid(user.getUuid()).build())
              .build();
      when(executionService.getExecutionWithoutSummary(APP_ID, WORKFLOW_EXECUTION_ID)).thenReturn(workflowExecution);
      when(executionService.listExecutions(any(PageRequest.class), anyBoolean()))
          .thenReturn(
              PageResponseBuilder.aPageResponse().withResponse(Arrays.asList(workflowExecution)).withTotal(1).build());
      when(stateExecutionService.list(any(PageRequest.class)))
          .thenReturn(
              PageResponseBuilder.aPageResponse().withResponse(PageResponseBuilder.aPageResponse().build()).build());
      when(continuousVerificationService.getCVDeploymentData(any(PageRequest.class)))
          .thenReturn(PageResponseBuilder.aPageResponse()
                          .withResponse(Arrays.asList(ContinuousVerificationExecutionMetaData.builder().build()))
                          .withTotal(1)
                          .build());
      StateExecutionInstance stateExecutionInstance = StateExecutionInstance.Builder.aStateExecutionInstance()
                                                          .uuid(STATE_EXECUTION_ID)
                                                          .executionUuid(WORKFLOW_EXECUTION_ID)
                                                          .build();
      when(stateExecutionService.list(any(PageRequest.class)))
          .thenReturn(PageResponseBuilder.aPageResponse()
                          .withResponse(Arrays.asList(stateExecutionInstance))
                          .withTotal(1)
                          .build());
      workflowExecution.setEnvType(EnvironmentType.NON_PROD);
      workflowExecution.setStatus(ExecutionStatus.SUCCESS);
      eventPublishHelper.handleDeploymentCompleted(workflowExecution);
      verify(eventPublisher, times(2)).publishEvent(any(Event.class));
    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testPublishVerificationWorkflowMetrics() {
    WorkflowExecution workflowExecution = WorkflowExecution.builder()
                                              .uuid(generateUuid())
                                              .envType(EnvironmentType.NON_PROD)
                                              .status(ExecutionStatus.SUCCESS)
                                              .build();
    List<String> appIds = Lists.newArrayList("app1", "app2");

    ContinuousVerificationExecutionMetaData metaData = ContinuousVerificationExecutionMetaData.builder()
                                                           .applicationId(appIds.get(0))
                                                           .workflowExecutionId(workflowExecution.getUuid())
                                                           .build();
    doReturn(Collections.singletonList(metaData)).when(continuousVerificationService).getCVDeploymentData(any());

    eventPublishHelper.publishVerificationWorkflowMetrics(workflowExecution, appIds, account.getUuid(), false);
    ArgumentCaptor<Event> taskCaptorValue = ArgumentCaptor.forClass(Event.class);

    verify(eventPublisher, times(1)).publishEvent(taskCaptorValue.capture());

    Event event = taskCaptorValue.getValue();

    assertThat(event).isNotNull();
    assertThat(event.getEventType()).isEqualByComparingTo(EventType.DEPLOYMENT_VERIFIED);
    assertThat(event.getEventData()).isNotNull();

    Map<String, String> properties = event.getEventData().getProperties();
    assertThat(properties).containsKey("accountId");
    assertThat(properties.get("accountId")).isEqualTo(account.getUuid());
    assertThat(properties).containsKey("workflowExecutionId");
    assertThat(properties.get("workflowExecutionId")).isEqualTo(workflowExecution.getUuid());
    assertThat(properties).containsKey("rollback");
    assertThat(properties.get("rollback")).isEqualTo(String.valueOf(false));
    assertThat(properties).containsKey("envType");
    assertThat(properties.get("envType")).isEqualTo(EnvironmentType.NON_PROD.name());
    assertThat(properties).containsKey("workflowStatus");
    assertThat(properties.get("workflowStatus")).isEqualTo(ExecutionStatus.SUCCESS.name());
    assertThat(properties).containsKey("rollbackType");
    assertThat(properties.get("rollbackType")).isEqualTo("MANUAL");
    assertThat(properties).containsKey("accountName");
    assertThat(properties.get("accountName")).isEqualTo(account.getAccountName());
    assertThat(properties).containsKey("licenseType");
    assertThat(properties.get("licenseType")).isEqualTo(account.getLicenseInfo().getAccountType());
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testPublishVerificationWorkflowMetrics_NullLicenseInfo() {
    account.setLicenseInfo(null);
    WorkflowExecution workflowExecution = WorkflowExecution.builder()
                                              .uuid(generateUuid())
                                              .envType(EnvironmentType.NON_PROD)
                                              .status(ExecutionStatus.SUCCESS)
                                              .build();
    List<String> appIds = Lists.newArrayList("app1", "app2");

    ContinuousVerificationExecutionMetaData metaData = ContinuousVerificationExecutionMetaData.builder()
                                                           .applicationId(appIds.get(0))
                                                           .workflowExecutionId(workflowExecution.getUuid())
                                                           .build();
    doReturn(Collections.singletonList(metaData)).when(continuousVerificationService).getCVDeploymentData(any());

    eventPublishHelper.publishVerificationWorkflowMetrics(workflowExecution, appIds, account.getUuid(), false);
    ArgumentCaptor<Event> taskCaptorValue = ArgumentCaptor.forClass(Event.class);

    verify(eventPublisher, times(1)).publishEvent(taskCaptorValue.capture());

    Event event = taskCaptorValue.getValue();

    assertThat(event).isNotNull();
    assertThat(event.getEventType()).isEqualByComparingTo(EventType.DEPLOYMENT_VERIFIED);
    assertThat(event.getEventData()).isNotNull();

    Map<String, String> properties = event.getEventData().getProperties();
    assertThat(properties).containsKey("accountId");
    assertThat(properties.get("accountId")).isEqualTo(account.getUuid());
    assertThat(properties).containsKey("workflowExecutionId");
    assertThat(properties.get("workflowExecutionId")).isEqualTo(workflowExecution.getUuid());
    assertThat(properties).containsKey("rollback");
    assertThat(properties.get("rollback")).isEqualTo(String.valueOf(false));
    assertThat(properties).containsKey("envType");
    assertThat(properties.get("envType")).isEqualTo(EnvironmentType.NON_PROD.name());
    assertThat(properties).containsKey("workflowStatus");
    assertThat(properties.get("workflowStatus")).isEqualTo(ExecutionStatus.SUCCESS.name());
    assertThat(properties).containsKey("rollbackType");
    assertThat(properties.get("rollbackType")).isEqualTo("MANUAL");
    assertThat(properties).containsKey("accountName");
    assertThat(properties.get("accountName")).isEqualTo(account.getAccountName());
    assertThat(properties).doesNotContainKey("licenseType");
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testPublishServiceGuardSetupEvent_LastTaskNull() {
    String verificationProviderType = "LOGS";
    List<String> configIds = Lists.newArrayList("configId1", "configId2");
    long alerts = 2;
    EnvironmentType environmentType = EnvironmentType.PROD;
    boolean enabled = true;

    doReturn(Optional.empty()).when(learningEngineService).getLatestTaskForCvConfigIds(configIds);

    eventPublishHelper.publishServiceGuardSetupEvent(
        account, verificationProviderType, configIds, alerts, environmentType, enabled);
    ArgumentCaptor<Event> taskCaptorValue = ArgumentCaptor.forClass(Event.class);

    verify(eventPublisher, times(1)).publishEvent(taskCaptorValue.capture());

    Event event = taskCaptorValue.getValue();

    assertThat(event).isNotNull();
    assertThat(event.getEventType()).isEqualByComparingTo(EventType.SERVICE_GUARD_SETUP);
    assertThat(event.getEventData()).isNotNull();

    Map<String, String> properties = event.getEventData().getProperties();

    assertThat(properties).containsKey("alerts");
    assertThat(properties.get("alerts")).isEqualTo(String.valueOf(alerts));
    assertThat(properties).containsKey("accountId");
    assertThat(properties.get("accountId")).isEqualTo(account.getUuid());
    assertThat(properties).containsKey("verificationProviderType");
    assertThat(properties.get("verificationProviderType")).isEqualTo(verificationProviderType);
    assertThat(properties).containsKey("configs");
    assertThat(properties.get("configs")).isEqualTo(String.valueOf(configIds.size()));
    assertThat(properties).containsKey("licenseType");
    assertThat(properties.get("licenseType")).isEqualTo(account.getLicenseInfo().getAccountType());
    assertThat(properties).containsKey("accountName");
    assertThat(properties.get("accountName")).isEqualTo(account.getAccountName());
    assertThat(properties).containsKey("environmentType");
    assertThat(properties.get("environmentType")).isEqualTo(environmentType.name());
    assertThat(properties).containsKey("enabled");
    assertThat(properties.get("enabled")).isEqualTo(String.valueOf(enabled));
    assertThat(properties).doesNotContainKey("lastExecutionTime");
    assertThat(properties).doesNotContainKey("hasData");
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testPublishServiceGuardSetupEvent_LastTaskNull_LicenseInfoNull() {
    String verificationProviderType = "LOGS";
    List<String> configIds = Lists.newArrayList("configId1", "configId2");
    long alerts = 2;
    EnvironmentType environmentType = EnvironmentType.PROD;
    boolean enabled = true;

    doReturn(Optional.empty()).when(learningEngineService).getLatestTaskForCvConfigIds(configIds);
    account.setLicenseInfo(null);

    eventPublishHelper.publishServiceGuardSetupEvent(
        account, verificationProviderType, configIds, alerts, environmentType, enabled);
    ArgumentCaptor<Event> taskCaptorValue = ArgumentCaptor.forClass(Event.class);

    verify(eventPublisher, times(1)).publishEvent(taskCaptorValue.capture());

    Event event = taskCaptorValue.getValue();

    assertThat(event).isNotNull();
    assertThat(event.getEventType()).isEqualByComparingTo(EventType.SERVICE_GUARD_SETUP);
    assertThat(event.getEventData()).isNotNull();

    Map<String, String> properties = event.getEventData().getProperties();

    assertThat(properties).containsKey("alerts");
    assertThat(properties.get("alerts")).isEqualTo(String.valueOf(alerts));
    assertThat(properties).containsKey("accountId");
    assertThat(properties.get("accountId")).isEqualTo(account.getUuid());
    assertThat(properties).containsKey("verificationProviderType");
    assertThat(properties.get("verificationProviderType")).isEqualTo(verificationProviderType);
    assertThat(properties).containsKey("configs");
    assertThat(properties.get("configs")).isEqualTo(String.valueOf(configIds.size()));
    assertThat(properties).doesNotContainKey("licenseType");
    assertThat(properties).containsKey("accountName");
    assertThat(properties.get("accountName")).isEqualTo(account.getAccountName());
    assertThat(properties).containsKey("environmentType");
    assertThat(properties.get("environmentType")).isEqualTo(environmentType.name());
    assertThat(properties).containsKey("enabled");
    assertThat(properties.get("enabled")).isEqualTo(String.valueOf(enabled));
    assertThat(properties).doesNotContainKey("lastExecutionTime");
    assertThat(properties).doesNotContainKey("hasData");
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testPublishServiceGuardSetupEvent_LastTaskNotNull() {
    String verificationProviderType = "LOGS";
    List<String> configIds = Lists.newArrayList("configId1", "configId2");
    long alerts = 2;
    EnvironmentType environmentType = EnvironmentType.PROD;

    long lastUpdatedAt = System.currentTimeMillis();
    LearningEngineAnalysisTask task = LearningEngineAnalysisTask.builder()
                                          .cvConfigId(configIds.get(0))
                                          .analysis_minute(1)
                                          .ml_analysis_type(MLAnalysisType.LOG_ML)
                                          .build();
    task.setLastUpdatedAt(lastUpdatedAt);
    doReturn(Optional.of(task)).when(learningEngineService).getLatestTaskForCvConfigIds(configIds);

    doReturn(false).when(learningEngineService).checkIfAnalysisHasData(configIds.get(0), MLAnalysisType.LOG_ML, 1);

    eventPublishHelper.publishServiceGuardSetupEvent(
        account, verificationProviderType, configIds, alerts, environmentType, true);
    ArgumentCaptor<Event> taskCaptorValue = ArgumentCaptor.forClass(Event.class);

    verify(eventPublisher, times(1)).publishEvent(taskCaptorValue.capture());

    Event event = taskCaptorValue.getValue();

    assertThat(event).isNotNull();
    assertThat(event.getEventType()).isEqualByComparingTo(EventType.SERVICE_GUARD_SETUP);
    assertThat(event.getEventData()).isNotNull();

    Map<String, String> properties = event.getEventData().getProperties();

    assertThat(properties).containsKey("alerts");
    assertThat(properties.get("alerts")).isEqualTo(String.valueOf(alerts));
    assertThat(properties).containsKey("accountId");
    assertThat(properties.get("accountId")).isEqualTo(account.getUuid());
    assertThat(properties).containsKey("verificationProviderType");
    assertThat(properties.get("verificationProviderType")).isEqualTo(verificationProviderType);
    assertThat(properties).containsKey("configs");
    assertThat(properties.get("configs")).isEqualTo(String.valueOf(configIds.size()));
    assertThat(properties).containsKey("licenseType");
    assertThat(properties.get("licenseType")).isEqualTo(account.getLicenseInfo().getAccountType());
    assertThat(properties).containsKey("accountName");
    assertThat(properties.get("accountName")).isEqualTo(account.getAccountName());
    assertThat(properties).containsKey("environmentType");
    assertThat(properties.get("environmentType")).isEqualTo(environmentType.name());
    assertThat(properties).containsKey("enabled");
    assertThat(properties.get("enabled")).isEqualTo(String.valueOf(true));
    assertThat(properties).containsKey("lastExecutionTime");
    assertThat(properties.get("lastExecutionTime")).isEqualTo(String.valueOf(lastUpdatedAt));
    assertThat(properties).containsKey("hasData");
    assertThat(properties.get("hasData")).isEqualTo(String.valueOf(false));
  }

  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  public void testPublishUserInviteVerifiedFromAccountEvent() throws IllegalAccessException {
    UserThreadLocal.set(user);
    try {
      SegmentConfig segmentConfig = SegmentConfig.builder().enabled(true).build();
      FieldUtils.writeField(eventPublishHelper, "segmentConfig", segmentConfig, true);
      eventPublishHelper.publishUserInviteVerifiedFromAccountEvent(ACCOUNT_ID, "abcd@abcd.com");
      verify(eventPublisher, times(1)).publishEvent(any(Event.class));
    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  public void testPublishUserInviteVerifiedFromAccountEvent_SegmentIsDisabled() throws IllegalAccessException {
    UserThreadLocal.set(user);
    try {
      SegmentConfig segmentConfig = SegmentConfig.builder().enabled(false).build();
      FieldUtils.writeField(eventPublishHelper, "segmentConfig", segmentConfig, true);
      eventPublishHelper.publishUserInviteVerifiedFromAccountEvent(ACCOUNT_ID, "abcd@abcd.com");
      verify(eventPublisher, times(0)).publishEvent(any(Event.class));
    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldPublishIfFirstDeploymentNotExecuteWhenEnabledFF() throws IllegalAccessException {
    when(featureFlagService.isNotEnabled(SPG_CG_SEGMENT_EVENT_FIRST_DEPLOYMENT, ACCOUNT_ID)).thenReturn(false);

    eventPublishHelper.publishIfFirstDeployment(
        WORKFLOW_EXECUTION_ID, Collections.emptyList(), ACCOUNT_ID, USER1_EMAIL);

    verify(executionService, never()).listExecutions(any(PageRequest.class), anyBoolean());
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldPublishIfFirstDeploymentNotExecuteWhenDisabledFF() throws IllegalAccessException {
    when(featureFlagService.isNotEnabled(SPG_CG_SEGMENT_EVENT_FIRST_DEPLOYMENT, ACCOUNT_ID)).thenReturn(true);

    List<WorkflowExecution> workflowExecutions =
        Collections.singletonList(WorkflowExecution.builder().uuid(WORKFLOW_EXECUTION_ID).build());
    PageResponse<WorkflowExecution> pageResponse = new PageResponse<>();
    pageResponse.setResponse(workflowExecutions);
    when(executionService.listExecutions(any(PageRequest.class), eq(false))).thenReturn(pageResponse);

    eventPublishHelper.publishIfFirstDeployment(
        WORKFLOW_EXECUTION_ID, Collections.emptyList(), ACCOUNT_ID, USER1_EMAIL);

    verify(executionService).listExecutions(any(PageRequest.class), anyBoolean());
  }
}
