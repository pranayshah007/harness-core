/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CV;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.RAGHU;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.DeploymentType;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.Service;
import software.wings.beans.TemplateExpression;
import software.wings.beans.dto.ThirdPartyApiCallLog.FieldType;
import software.wings.common.TemplateExpressionProcessor;
import software.wings.delegatetasks.cv.DataCollectionException;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.ThirdPartyApiCallLog;
import software.wings.service.impl.analysis.AnalysisContext;
import software.wings.service.impl.instance.ContainerInstanceHandler;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.verification.CVActivityLogService;
import software.wings.service.intfc.verification.CVActivityLogger;
import software.wings.sm.ExecutionContext;
import software.wings.sm.states.AbstractAnalysisState.AbstractAnalysisStateKeys;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;

/**
 * Created by sriram_parthasarathy on 12/7/17.
 */
@OwnedBy(CV)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class AbstractAnalysisStateTest extends WingsBaseTest {
  @Inject private HPersistence persistence;
  @Inject private WorkflowExecutionService workflowExecutionService;
  @Inject private TemplateExpressionProcessor templateExpressionProcessor;
  @Mock private StateExecutionService stateExecutionService;
  @Mock private ContainerInstanceHandler containerInstanceHandler;
  @Mock private InfrastructureMappingService infraMappingService;
  @Mock private InfrastructureMapping infrastructureMapping;
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private ExecutionContext executionContext;
  @Mock private CVActivityLogService cvActivityLogService;

  private final String workflowId = UUID.randomUUID().toString();
  private final String envId = UUID.randomUUID().toString();
  private final String appId = UUID.randomUUID().toString();
  private final String serviceId = UUID.randomUUID().toString();

  @Before
  public void setup() {
    initMocks(this);
    when(containerInstanceHandler.isContainerDeployment(any())).thenReturn(false);
    when(infrastructureMapping.getDeploymentType()).thenReturn(DeploymentType.KUBERNETES.name());
    when(infraMappingService.get(anyString(), anyString())).thenReturn(infrastructureMapping);
    when(serviceResourceService.getDeploymentType(any(), any(), any())).thenReturn(DeploymentType.KUBERNETES);
    when(serviceResourceService.get(anyString(), anyString(), anyBoolean()))
        .thenReturn(Service.builder().uuid(serviceId).name("ServiceA").build());
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGenerateDemoActivityLogs_whenStateIsSuccessful() {
    AbstractAnalysisState abstractAnalysisState = mock(AbstractAnalysisState.class, Mockito.CALLS_REAL_METHODS);
    when(abstractAnalysisState.getTimeDuration(executionContext)).thenReturn("15");
    CVActivityLogger activityLogger = mock(CVActivityLogger.class);
    abstractAnalysisState.generateDemoActivityLogs(executionContext, activityLogger, false);
    verify(activityLogger, times(46)).info(anyString(), anyLong(), anyLong());
    verify(activityLogger, times(1)).info(eq("Analysis successful"));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGenerateDemoActivityLogs_whenStateFailed() {
    AbstractAnalysisState abstractAnalysisState = mock(AbstractAnalysisState.class, Mockito.CALLS_REAL_METHODS);
    when(abstractAnalysisState.getTimeDuration(executionContext)).thenReturn("15");
    CVActivityLogger activityLogger = mock(CVActivityLogger.class);
    abstractAnalysisState.generateDemoActivityLogs(executionContext, activityLogger, true);
    verify(activityLogger, times(45)).info(anyString(), anyLong(), anyLong());
    verify(activityLogger, times(1)).error(anyString(), anyLong(), anyLong());
    verify(activityLogger, times(1)).error(eq("Analysis failed"));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGenerateDemoThirdPartyApiCallLogs_whenStateIsSuccessful() throws IllegalAccessException {
    AbstractAnalysisState abstractAnalysisState = mock(AbstractAnalysisState.class, Mockito.CALLS_REAL_METHODS);
    WingsPersistence wingsPersistence = mock(WingsPersistence.class);
    FieldUtils.writeField(abstractAnalysisState, "wingsPersistence", wingsPersistence, true);
    when(abstractAnalysisState.getTimeDuration(executionContext)).thenReturn("15");
    String accountId = generateUuid();
    String stateExecutionId = generateUuid();
    abstractAnalysisState.generateDemoThirdPartyApiCallLogs(
        executionContext, accountId, stateExecutionId, false, "request body", "response body");

    ArgumentCaptor<List> argumentCaptor = ArgumentCaptor.forClass(List.class);
    verify(wingsPersistence).save(argumentCaptor.capture());
    List<ThirdPartyApiCallLog> savedCallLogs = argumentCaptor.getValue();
    assertThat(savedCallLogs).hasSize(15);
    savedCallLogs.forEach(callLog -> {
      assertThat(callLog.getStateExecutionId()).isEqualTo(stateExecutionId);
      assertThat(callLog.getAccountId()).isEqualTo(accountId);
      assertThat(callLog.getRequest()).hasSize(2);
      assertThat(callLog.getRequest().get(1).getValue()).isEqualTo("request body");
      assertThat(callLog.getRequest().get(1).getType()).isEqualTo(FieldType.JSON);
      assertThat(callLog.getResponse()).hasSize(2);
      assertThat(callLog.getRequest().get(0).getType()).isEqualTo(FieldType.URL);
      assertThat(callLog.getResponse().get(1).getType()).isEqualTo(FieldType.JSON);
      assertThat(callLog.getTitle()).isEqualTo("Demo third party API call log");
      assertThat(callLog.getResponse().get(0).getValue()).isEqualTo("200");
      assertThat(callLog.getResponse().get(1).getValue()).isEqualTo("response body");
    });
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGenerateDemoThirdPartyApiCallLogs_whenStateFailed() throws IllegalAccessException {
    AbstractAnalysisState abstractAnalysisState = mock(AbstractAnalysisState.class, Mockito.CALLS_REAL_METHODS);
    WingsPersistence wingsPersistence = mock(WingsPersistence.class);
    FieldUtils.writeField(abstractAnalysisState, "wingsPersistence", wingsPersistence, true);
    when(abstractAnalysisState.getTimeDuration(executionContext)).thenReturn("15");
    String accountId = generateUuid();
    String stateExecutionId = generateUuid();
    abstractAnalysisState.generateDemoThirdPartyApiCallLogs(
        executionContext, accountId, stateExecutionId, true, "request body", "response body");
    ArgumentCaptor<List> argumentCaptor = ArgumentCaptor.forClass(List.class);
    verify(wingsPersistence).save(argumentCaptor.capture());
    List<ThirdPartyApiCallLog> savedCallLogs = argumentCaptor.getValue();
    assertThat(savedCallLogs).hasSize(15);
    for (int minute = 0; minute < 15; minute++) {
      ThirdPartyApiCallLog callLog = savedCallLogs.get(minute);
      assertThat(callLog.getStateExecutionId()).isEqualTo(stateExecutionId);
      assertThat(callLog.getAccountId()).isEqualTo(accountId);
      assertThat(callLog.getRequest()).hasSize(2);
      assertThat(callLog.getRequest().get(1).getValue()).isEqualTo("request body");
      assertThat(callLog.getRequest().get(1).getType()).isEqualTo(FieldType.JSON);
      assertThat(callLog.getResponse()).hasSize(2);
      assertThat(callLog.getRequest().get(0).getType()).isEqualTo(FieldType.URL);
      assertThat(callLog.getResponse().get(1).getType()).isEqualTo(FieldType.JSON);
      assertThat(callLog.getTitle()).isEqualTo("Demo third party API call log");
      if (minute == 15 / 2) {
        assertThat(callLog.getResponse().get(0).getValue()).isEqualTo("408");
        assertThat(callLog.getResponse().get(1).getValue()).isEqualTo("Timeout from service provider");
      } else {
        assertThat(callLog.getResponse().get(0).getValue()).isEqualTo("200");
        assertThat(callLog.getResponse().get(1).getValue()).isEqualTo("response body");
      }
    }
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testSampleHosts_whenFFIsDisabled() throws IllegalAccessException {
    AbstractAnalysisState abstractAnalysisState = mock(AbstractAnalysisState.class, Mockito.CALLS_REAL_METHODS);
    String accountId = generateUuid();
    FeatureFlagService featureFlagService = mock(FeatureFlagService.class);
    FieldUtils.writeField(abstractAnalysisState, "featureFlagService", featureFlagService, true);
    when(featureFlagService.isEnabled(eq(FeatureName.CV_HOST_SAMPLING), eq(accountId))).thenReturn(false);
    AnalysisContext analysisContext = AnalysisContext.builder()
                                          .accountId(accountId)
                                          .testNodes(getHostsMap(20, 5))
                                          .controlNodes(getHostsMap(20, 5))
                                          .build();
    abstractAnalysisState.sampleHostsMap(analysisContext);
    assertThat(analysisContext.getControlNodes()).hasSize(100);
    assertThat(analysisContext.getTestNodes()).hasSize(100);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testSampleHosts_whenFFIsEnabledAndNoSamplingRequired() throws IllegalAccessException {
    AbstractAnalysisState abstractAnalysisState = mock(AbstractAnalysisState.class, Mockito.CALLS_REAL_METHODS);
    String accountId = generateUuid();
    FeatureFlagService featureFlagService = mock(FeatureFlagService.class);
    FieldUtils.writeField(abstractAnalysisState, "featureFlagService", featureFlagService, true);
    when(featureFlagService.isEnabled(eq(FeatureName.CV_HOST_SAMPLING), eq(accountId))).thenReturn(true);
    AnalysisContext analysisContext = AnalysisContext.builder()
                                          .accountId(accountId)
                                          .testNodes(getHostsMap(10, 5))
                                          .controlNodes(getHostsMap(9, 5))
                                          .build();
    Map<String, String> testNodes = analysisContext.getTestNodes();
    Map<String, String> controlNodes = analysisContext.getControlNodes();
    abstractAnalysisState.sampleHostsMap(analysisContext);
    assertThat(analysisContext.getControlNodes()).hasSize(45);
    assertThat(analysisContext.getTestNodes()).hasSize(50);
    assertThat(analysisContext.getTestNodes()).isEqualTo(testNodes);
    assertThat(analysisContext.getControlNodes()).isEqualTo(controlNodes);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testSampleHosts_whenFFIsEnabledAndSamplingRequired() throws IllegalAccessException {
    AbstractAnalysisState abstractAnalysisState = mock(AbstractAnalysisState.class, Mockito.CALLS_REAL_METHODS);
    String accountId = generateUuid();
    FeatureFlagService featureFlagService = mock(FeatureFlagService.class);
    FieldUtils.writeField(abstractAnalysisState, "featureFlagService", featureFlagService, true);
    when(featureFlagService.isEnabled(eq(FeatureName.CV_HOST_SAMPLING), eq(accountId))).thenReturn(true);
    AnalysisContext analysisContext = AnalysisContext.builder()
                                          .accountId(accountId)
                                          .testNodes(getHostsMap(20, 5))
                                          .controlNodes(getHostsMap(11, 5))
                                          .build();
    abstractAnalysisState.sampleHostsMap(analysisContext);
    assertThat(analysisContext.getControlNodes()).hasSize(50);
    assertThat(analysisContext.getTestNodes()).hasSize(50);
  }

  private Map<String, String> getHostsMap(int numberOfHostsPerGroup, int numberOfGroups) {
    Map<String, String> hosts = new HashMap<>();
    for (int i = 0; i < numberOfGroups; i++) {
      String groupName = generateUuid();
      for (int j = 0; j < numberOfHostsPerGroup; j++) {
        hosts.put(generateUuid(), groupName);
      }
    }
    return hosts;
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testGetResolvedFieldValue_whenNoTemplatizationOrExpression() {
    AbstractAnalysisState abstractAnalysisState = mock(AbstractAnalysisState.class, Mockito.CALLS_REAL_METHODS);
    abstractAnalysisState.setHostnameTemplate(generateUuid());
    final String resolvedFieldValue = abstractAnalysisState.getResolvedFieldValue(
        executionContext, AbstractAnalysisStateKeys.hostnameTemplate, abstractAnalysisState.getHostnameTemplate());
    assertThat(resolvedFieldValue).isEqualTo(abstractAnalysisState.getHostnameTemplate());
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testGetResolvedFieldValue_whenTemplatizedWithInvalidValue() throws IllegalAccessException {
    AbstractAnalysisState abstractAnalysisState = mock(AbstractAnalysisState.class, Mockito.CALLS_REAL_METHODS);
    FieldUtils.writeField(abstractAnalysisState, "templateExpressionProcessor", templateExpressionProcessor, true);
    abstractAnalysisState.setHostnameTemplate("${hostnameTemplate}");
    abstractAnalysisState.setTemplateExpressions(asList(TemplateExpression.builder()
                                                            .fieldName(AbstractAnalysisStateKeys.hostnameTemplate)
                                                            .expression("${hostnameTemplate}")
                                                            .metadata(ImmutableMap.of("entityType", "CONFIG"))
                                                            .build()));
    when(executionContext.renderExpression("${workflow.variables.hostnameTemplate}")).thenReturn("${hostnameTemplate}");

    assertThatThrownBy(()
                           -> abstractAnalysisState.getResolvedFieldValue(executionContext,
                               AbstractAnalysisStateKeys.hostnameTemplate, abstractAnalysisState.getHostnameTemplate()))
        .isInstanceOf(DataCollectionException.class)
        .hasMessage("Template expression ${hostnameTemplate} could not be resolved");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testGetResolvedFieldValue_whenTemplatizedWithValidValue() throws IllegalAccessException {
    AbstractAnalysisState abstractAnalysisState = mock(AbstractAnalysisState.class, Mockito.CALLS_REAL_METHODS);
    FieldUtils.writeField(abstractAnalysisState, "templateExpressionProcessor", templateExpressionProcessor, true);
    abstractAnalysisState.setTemplateExpressions(asList(TemplateExpression.builder()
                                                            .fieldName(AbstractAnalysisStateKeys.hostnameTemplate)
                                                            .expression("${hostnameTemplate}")
                                                            .metadata(ImmutableMap.of("entityType", "CONFIG"))
                                                            .build()));
    when(executionContext.renderExpression("${workflow.variables.hostnameTemplate}")).thenReturn("resolved template");

    final String resolvedFieldValue = abstractAnalysisState.getResolvedFieldValue(
        executionContext, AbstractAnalysisStateKeys.hostnameTemplate, abstractAnalysisState.getHostnameTemplate());
    assertThat(resolvedFieldValue).isEqualTo("resolved template");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testGetResolvedFieldValue_whenExpressionInvalidValue() throws Exception {
    AbstractAnalysisState abstractAnalysisState = mock(AbstractAnalysisState.class, Mockito.CALLS_REAL_METHODS);
    FieldUtils.writeField(abstractAnalysisState, "cvActivityLogService", cvActivityLogService, true);
    when(cvActivityLogService.getLoggerByStateExecutionId(any(), any())).thenReturn(mock(CVActivityLogger.class));
    abstractAnalysisState.setHostnameTemplate("${hostnameTemplate}");
    when(executionContext.renderExpression("${hostnameTemplate}")).thenReturn("${hostnameTemplate}");

    assertThatThrownBy(()
                           -> abstractAnalysisState.getResolvedFieldValue(executionContext,
                               AbstractAnalysisStateKeys.hostnameTemplate, abstractAnalysisState.getHostnameTemplate()))
        .isInstanceOf(DataCollectionException.class)
        .hasMessage("Expression ${hostnameTemplate} could not be resolved");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testGetResolvedFieldValue_whenExpressionValidValue() throws Exception {
    AbstractAnalysisState abstractAnalysisState = mock(AbstractAnalysisState.class, Mockito.CALLS_REAL_METHODS);
    FieldUtils.writeField(abstractAnalysisState, "cvActivityLogService", cvActivityLogService, true);
    when(cvActivityLogService.getLoggerByStateExecutionId(any(), any())).thenReturn(mock(CVActivityLogger.class));
    abstractAnalysisState.setHostnameTemplate("${hostnameTemplate}");
    when(executionContext.renderExpression("${hostnameTemplate}")).thenReturn("resolved template");

    final String resolvedFieldValue = abstractAnalysisState.getResolvedFieldValue(
        executionContext, AbstractAnalysisStateKeys.hostnameTemplate, abstractAnalysisState.getHostnameTemplate());
    assertThat(resolvedFieldValue).isEqualTo("resolved template");
  }
}
