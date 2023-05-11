/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.rule.OwnerRule.RAGHU;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.WingsException;
import io.harness.persistence.HPersistence;
import io.harness.persistence.PersistentEntity;
import io.harness.rule.Owner;

import software.wings.beans.Application;
import software.wings.beans.NewRelicConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TemplateExpression;
import software.wings.common.TemplateExpressionProcessor;
import software.wings.service.impl.newrelic.NewRelicApplication;
import software.wings.service.impl.newrelic.NewRelicDataCollectionInfo;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.newrelic.NewRelicService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class NewRelicDeploymentMarkerStateTest extends APMStateVerificationTestBase {
  @Inject private SettingsService settingsService;
  @Inject private HPersistence persistence;
  @Inject private SecretManager secretManager;
  @Inject private TemplateExpressionProcessor templateExpressionProcessor;
  @Mock private WorkflowExecutionService workflowExecutionService;
  @Mock private ExecutionContext executionContext;
  @Mock private NewRelicService newRelicService;
  @Mock private DelegateService delegateService;
  private String accountId;
  private NewRelicDeploymentMarkerState newRelicDeploymentMarkerState;
  private String settingId;

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
    accountId = generateUuid();
    when(workflowExecutionService.isTriggerBasedDeployment(any(ExecutionContext.class))).thenReturn(true);
    newRelicDeploymentMarkerState = new NewRelicDeploymentMarkerState("NewRelicDeploymentMarkerState");
    FieldUtils.writeField(newRelicDeploymentMarkerState, "settingsService", settingsService, true);
    FieldUtils.writeField(newRelicDeploymentMarkerState, "workflowExecutionService", workflowExecutionService, true);
    FieldUtils.writeField(newRelicDeploymentMarkerState, "newRelicService", newRelicService, true);
    FieldUtils.writeField(newRelicDeploymentMarkerState, "secretManager", secretManager, true);
    FieldUtils.writeField(newRelicDeploymentMarkerState, "delegateService", delegateService, true);
    FieldUtils.writeField(
        newRelicDeploymentMarkerState, "templateExpressionProcessor", templateExpressionProcessor, true);
    when(executionContext.getApp())
        .thenReturn(Application.Builder.anApplication().appId(generateUuid()).accountId(accountId).build());
    when(delegateService.queueTaskV2(any(DelegateTask.class)))
        .thenAnswer(invocationOnMock -> persistence.save((PersistentEntity) invocationOnMock.getArguments()[0]));

    NewRelicConfig newRelicConfig = NewRelicConfig.builder()
                                        .accountId(accountId)
                                        .newRelicUrl("newrelic-url")
                                        .apiKey(generateUuid().toCharArray())
                                        .build();
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(accountId)
                                            .withName("relic-config")
                                            .withValue(newRelicConfig)
                                            .build();

    settingId = persistence.save(settingAttribute);

    doReturn(NewRelicApplication.builder().id(1234).build())
        .when(newRelicService)
        .resolveApplicationName(settingId, "valid_app", appId, workflowExecutionId);
    doThrow(new RuntimeException("Invalid app"))
        .when(newRelicService)
        .resolveApplicationName(settingId, "invalid_app", appId, workflowExecutionId);
    setupCvActivityLogService(newRelicDeploymentMarkerState);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void shouldTestTriggered() {
    doThrow(new WingsException("Can not find application by id"))
        .when(newRelicService)
        .resolveApplicationId(any(), any(), any(), any());

    doThrow(new WingsException("Can not find application by name"))
        .when(newRelicService)
        .resolveApplicationName(any(), any(), any(), any());

    when(executionContext.renderExpression("${workflow.variables.NewRelic_Server}")).thenReturn(settingId);

    final NewRelicDeploymentMarkerState spyRelicDeploymentMarkerState = spy(this.newRelicDeploymentMarkerState);
    spyRelicDeploymentMarkerState.setAnalysisServerConfigId(settingId);
    doReturn(asList(TemplateExpression.builder()
                        .fieldName("analysisServerConfigId")
                        .expression("${NewRelic_Server}")
                        .metadata(ImmutableMap.of("entityType", "NEWRELIC_CONFIGID"))
                        .build(),
                 TemplateExpression.builder()
                     .fieldName("applicationId")
                     .expression("${NewRelic_App}")
                     .metadata(ImmutableMap.of("entityType", "NEWRELIC_APPID"))
                     .build()))
        .when(spyRelicDeploymentMarkerState)
        .getTemplateExpressions();

    try {
      spyRelicDeploymentMarkerState.execute(executionContext);
      fail("Passed with wrong app name");
    } catch (WingsException e) {
      assertThat(e.getMessage()).isEqualTo("Can not find application by name");
    }

    doReturn(NewRelicApplication.builder().id(30444).build())
        .when(newRelicService)
        .resolveApplicationName(any(), any(), any(), any());
    ExecutionResponse executionResponse = spyRelicDeploymentMarkerState.execute(executionContext);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.RUNNING);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void invalidAppExpression() {
    newRelicDeploymentMarkerState.setApplicationId("${app.appName}");
    newRelicDeploymentMarkerState.setAnalysisServerConfigId(settingId);
    when(executionContext.renderExpression("${app.appName}")).thenReturn("invalid_app");
    assertThatExceptionOfType(RuntimeException.class)
        .isThrownBy(() -> newRelicDeploymentMarkerState.execute(executionContext));
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void validAppExpression() {
    newRelicDeploymentMarkerState.setApplicationId("${app.appName}");
    newRelicDeploymentMarkerState.setAnalysisServerConfigId(settingId);
    when(executionContext.renderExpression("${app.appName}")).thenReturn("valid_app");
    final ExecutionResponse executionResponse = newRelicDeploymentMarkerState.execute(executionContext);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.RUNNING);
    final DelegateTask delegateTask = persistence.createQuery(DelegateTask.class, excludeAuthority).get();
    final TaskData taskData = delegateTask.getData();
    NewRelicDataCollectionInfo newRelicDataCollectionInfo = (NewRelicDataCollectionInfo) taskData.getParameters()[0];
    assertThat(newRelicDataCollectionInfo.getSettingAttributeId()).isEqualTo(settingId);
    assertThat(newRelicDataCollectionInfo.getNewRelicAppId()).isEqualTo(1234);
  }
}
