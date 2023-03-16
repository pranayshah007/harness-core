/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.beans.ExecutionStatus.ERROR;
import static io.harness.beans.ExecutionStatus.RUNNING;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.PRANJAL;
import static io.harness.rule.OwnerRule.PRAVEEN;
import static io.harness.rule.OwnerRule.RAGHU;
import static io.harness.rule.OwnerRule.SRINIVAS;

import static software.wings.beans.AwsInfrastructureMapping.Builder.anAwsInfrastructureMapping;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.DelegateTask;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.context.ContextElementType;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;

import software.wings.api.DeploymentType;
import software.wings.api.PhaseElement;
import software.wings.beans.AccountType;
import software.wings.beans.AppDynamicsConfig;
import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.beans.TemplateExpression;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.delegatetasks.DelegateStateType;
import software.wings.delegatetasks.cv.DataCollectionException;
import software.wings.metrics.MetricType;
import software.wings.metrics.appdynamics.AppdynamicsConstants;
import software.wings.service.impl.analysis.AnalysisContext;
import software.wings.service.impl.analysis.ContinuousVerificationExecutionMetaData;
import software.wings.service.impl.appdynamics.AppdynamicsDataCollectionInfo;
import software.wings.service.impl.appdynamics.AppdynamicsTier;
import software.wings.service.impl.newrelic.NewRelicApplication;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.MetricDataAnalysisService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.appdynamics.AppdynamicsService;
import software.wings.service.intfc.verification.CVActivityLogger;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.WorkflowStandardParamsExtensionService;
import software.wings.sm.states.AbstractAnalysisState.CVInstanceApiResponse;
import software.wings.sm.states.AppDynamicsState.AppDynamicsStateKeys;
import software.wings.verification.VerificationStateAnalysisExecutionData;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.text.ParseException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * author Srinivas
 */
public class AppDynamicsStateTest extends APMStateVerificationTestBase {
  @Mock private MetricDataAnalysisService metricAnalysisService;
  @Mock private InfrastructureMappingService infraMappingService;
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;

  @Mock private AppdynamicsService appdynamicsService;
  @Mock private PhaseElement phaseElement;
  @Mock private DelegateService delegateService;

  private AppDynamicsState appDynamicsState;
  private String infraMappingId;
  private String tierId = "456";
  private String applicationId = "30444";
  @Before
  public void setup() throws IOException, IllegalAccessException {
    setupCommon();

    MockitoAnnotations.initMocks(this);
    infraMappingId = generateUuid();

    AppService appService = mock(AppService.class);
    when(appService.getAccountIdByAppId(anyString())).thenReturn(generateUuid());
    when(appService.get(anyString()))
        .thenReturn(Application.Builder.anApplication().name(generateUuid()).accountId(accountId).build());

    AccountService accountService = mock(AccountService.class);
    when(accountService.getAccountType(anyString())).thenReturn(Optional.of(AccountType.PAID));

    appDynamicsState = new AppDynamicsState("AppDynamicsState");
    appDynamicsState.setApplicationId(applicationId);
    appDynamicsState.setTierId(tierId);
    appDynamicsState.setTimeDuration("10");

    when(appdynamicsService.getTiers(anyString(), anyLong(), anyString(), anyString(), any()))
        .thenReturn(Sets.newHashSet(AppdynamicsTier.builder().id(Long.parseLong(tierId)).name("tier").build()));
    FieldUtils.writeField(appDynamicsState, "appService", appService, true);
    FieldUtils.writeField(appDynamicsState, "configuration", configuration, true);
    FieldUtils.writeField(appDynamicsState, "settingsService", settingsService, true);
    FieldUtils.writeField(appDynamicsState, "waitNotifyEngine", waitNotifyEngine, true);
    FieldUtils.writeField(appDynamicsState, "delegateService", delegateService, true);
    FieldUtils.writeField(appDynamicsState, "wingsPersistence", wingsPersistence, true);
    FieldUtils.writeField(appDynamicsState, "secretManager", secretManager, true);
    FieldUtils.writeField(appDynamicsState, "metricAnalysisService", metricAnalysisService, true);
    FieldUtils.writeField(appDynamicsState, "templateExpressionProcessor", templateExpressionProcessor, true);
    FieldUtils.writeField(appDynamicsState, "workflowExecutionService", workflowExecutionService, true);
    FieldUtils.writeField(appDynamicsState, "continuousVerificationService", continuousVerificationService, true);
    FieldUtils.writeField(appDynamicsState, "workflowExecutionBaselineService", workflowExecutionBaselineService, true);
    FieldUtils.writeField(appDynamicsState, "appdynamicsService", appdynamicsService, true);
    FieldUtils.writeField(appDynamicsState, "featureFlagService", featureFlagService, true);
    FieldUtils.writeField(appDynamicsState, "infraMappingService", infraMappingService, true);
    FieldUtils.writeField(appDynamicsState, "versionInfoManager", versionInfoManager, true);
    FieldUtils.writeField(appDynamicsState, "serviceResourceService", serviceResourceService, true);
    FieldUtils.writeField(appDynamicsState, "appService", appService, true);
    FieldUtils.writeField(appDynamicsState, "accountService", accountService, true);
    FieldUtils.writeField(appDynamicsState, "cvActivityLogService", cvActivityLogService, true);
    FieldUtils.writeField(
        appDynamicsState, "workflowStandardParamsExtensionService", workflowStandardParamsExtensionService, true);
    FieldUtils.writeField(
        appDynamicsState, "workflowVerificationResultService", workflowVerificationResultService, true);
    when(cvActivityLogService.getLoggerByStateExecutionId(any(), any())).thenReturn(mock(CVActivityLogger.class));

    when(executionContext.getContextElement(ContextElementType.PARAM, AbstractAnalysisStateTestBase.PHASE_PARAM))
        .thenReturn(phaseElement);
    when(executionContext.fetchInfraMappingId()).thenReturn(infraMappingId);
    when(executionContext.getAppId()).thenReturn(appId);
    when(infraMappingService.get(anyString(), anyString()))
        .thenReturn(anAwsInfrastructureMapping().withDeploymentType(DeploymentType.AWS_CODEDEPLOY.name()).build());
    when(serviceResourceService.getDeploymentType(anyObject(), anyObject(), anyObject()))
        .thenReturn(DeploymentType.AWS_CODEDEPLOY);
    setupCommonMocks();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTestNonTemplatized() {
    AppDynamicsState spyAppDynamicsState = setupNonTemplatized(false);
    doReturn(CVInstanceApiResponse.builder()
                 .controlNodes(Collections.emptySet())
                 .testNodes(Collections.singleton("node"))
                 .newNodesTrafficShiftPercent(Optional.empty())
                 .build())
        .when(spyAppDynamicsState)
        .getCVInstanceAPIResponse(any());
    ExecutionResponse executionResponse = spyAppDynamicsState.execute(executionContext);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.RUNNING);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void shouldTestExpressionForTierAndApplicationNameWhenExpressionDoesNotRender() {
    String tierIdExpression = "${workflow.variables.appd_tier_name}";
    appDynamicsState.setTierId(tierIdExpression);
    String applicationIdExpression = "${workflow.variables.applicationId}";
    appDynamicsState.setApplicationId(applicationIdExpression);
    AppDynamicsState spyAppDynamicsState = setupNonTemplatized(false);
    doReturn(CVInstanceApiResponse.builder()
                 .controlNodes(Collections.emptySet())
                 .testNodes(Collections.singleton("node"))
                 .newNodesTrafficShiftPercent(Optional.empty())
                 .build())
        .when(spyAppDynamicsState)
        .getCVInstanceAPIResponse(any());
    ExecutionResponse executionResponse = spyAppDynamicsState.execute(executionContext);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.ERROR);
    assertThat(executionResponse.getErrorMessage())
        .isEqualTo("Error: Expression ${workflow.variables.applicationId} could not be resolved");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void shouldTestExpressionForTierAndApplicationNameWhenExpressionDoesNotRenderForTierId() {
    String tierIdExpression = "${workflow.variables.appd_tier_name}";
    appDynamicsState.setTierId(tierIdExpression);
    String applicationIdExpression = "${workflow.variables.applicationId}";
    appDynamicsState.setApplicationId(applicationIdExpression);
    String applicationName = generateUuid();
    when(executionContext.renderExpression(eq(applicationIdExpression))).thenReturn(applicationName);
    when(appdynamicsService.getAppDynamicsApplicationByName(any(), eq(applicationName), anyString(), anyString()))
        .thenReturn(applicationId);
    AppDynamicsState spyAppDynamicsState = setupNonTemplatized(false);
    doReturn(CVInstanceApiResponse.builder()
                 .controlNodes(Collections.emptySet())
                 .testNodes(Collections.singleton("node"))
                 .newNodesTrafficShiftPercent(Optional.empty())
                 .build())
        .when(spyAppDynamicsState)
        .getCVInstanceAPIResponse(any());
    ExecutionResponse executionResponse = spyAppDynamicsState.execute(executionContext);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.ERROR);
    assertThat(executionResponse.getErrorMessage())
        .isEqualTo("Error: Expression ${workflow.variables.appd_tier_name} could not be resolved");
  }
  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void shouldTestExpressionForTierAndApplicationNameWhenTierNameIsNotResolved() {
    String tierIdExpression = "${workflow.variables.appd_tier_name}";
    appDynamicsState.setTierId(tierIdExpression);
    String applicationIdExpression = "${workflow.variables.applicationId}";
    appDynamicsState.setApplicationId(applicationIdExpression);
    String applicationName = generateUuid();
    when(executionContext.renderExpression(eq(applicationIdExpression))).thenReturn(applicationName);
    when(appdynamicsService.getAppDynamicsApplicationByName(any(), eq(applicationName), anyString(), anyString()))
        .thenReturn(applicationId);
    when(executionContext.renderExpression(eq(applicationIdExpression))).thenReturn("tierName");
    AppDynamicsState spyAppDynamicsState = setupNonTemplatized(false);
    doReturn(CVInstanceApiResponse.builder()
                 .controlNodes(Collections.emptySet())
                 .testNodes(Collections.singleton("node"))
                 .newNodesTrafficShiftPercent(Optional.empty())
                 .build())
        .when(spyAppDynamicsState)
        .getCVInstanceAPIResponse(any());
    ExecutionResponse executionResponse = spyAppDynamicsState.execute(executionContext);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.ERROR);
    assertThat(executionResponse.getErrorMessage())
        .isEqualTo("Error: Expression ${workflow.variables.appd_tier_name} could not be resolved");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void shouldTestExpressionForTierAndApplicationNameWhenExpressionRendersToValidIds() {
    String tierIdExpression = "${workflow.variables.appd_tier_name}";
    appDynamicsState.setTierId(tierIdExpression);
    String applicationIdExpression = "${workflow.variables.applicationId}";
    appDynamicsState.setApplicationId(applicationIdExpression);
    AppDynamicsState spyAppDynamicsState = setupNonTemplatized(false);
    String applicationName = "appdApplicationName";
    String tierName = "appDTierName";
    when(executionContext.renderExpression(eq(tierIdExpression))).thenReturn(tierName);
    when(executionContext.renderExpression(eq(applicationIdExpression))).thenReturn(applicationName);
    when(appdynamicsService.getAppDynamicsApplicationByName(any(), eq(applicationName), anyString(), anyString()))
        .thenReturn(applicationId);
    when(appdynamicsService.getTierByName(any(), any(), eq(tierName), anyString(), anyString(), any()))
        .thenReturn(tierId);
    doReturn(CVInstanceApiResponse.builder()
                 .controlNodes(Collections.emptySet())
                 .testNodes(Collections.singleton("node"))
                 .newNodesTrafficShiftPercent(Optional.empty())
                 .build())
        .when(spyAppDynamicsState)
        .getCVInstanceAPIResponse(any());
    ExecutionResponse executionResponse = spyAppDynamicsState.execute(executionContext);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(RUNNING);
  }

  private AppDynamicsState setupNonTemplatized(boolean isBadTier) {
    AppDynamicsConfig appDynamicsConfig = AppDynamicsConfig.builder()
                                              .accountId(accountId)
                                              .controllerUrl("appd-url")
                                              .username("appd-user")
                                              .password("appd-pwd".toCharArray())
                                              .build();
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(accountId)
                                            .withName("appd-config")
                                            .withValue(appDynamicsConfig)
                                            .build();
    wingsPersistence.save(settingAttribute);
    appDynamicsState.setAnalysisServerConfigId(settingAttribute.getUuid());

    if (isBadTier) {
      appDynamicsState.setTierId("123aa");
    }
    AppDynamicsState spyAppDynamicsState = spy(appDynamicsState);
    doReturn(CVInstanceApiResponse.builder()
                 .controlNodes(Collections.singleton("control"))
                 .testNodes(Collections.singleton("test"))
                 .newNodesTrafficShiftPercent(Optional.empty())
                 .build())
        .when(spyAppDynamicsState)
        .getCVInstanceAPIResponse(any());
    doReturn(workflowId).when(spyAppDynamicsState).getWorkflowId(executionContext);
    doReturn(serviceId).when(spyAppDynamicsState).getPhaseServiceId(executionContext);
    when(workflowStandardParamsExtensionService.getEnv(workflowStandardParams))
        .thenReturn(Environment.Builder.anEnvironment().uuid(UUID.randomUUID().toString()).build());
    when(executionContext.getContextElement(ContextElementType.STANDARD)).thenReturn(workflowStandardParams);

    when(metricAnalysisService.getLastSuccessfulWorkflowExecutionIdWithData(
             DelegateStateType.APP_DYNAMICS, appId, workflowId, serviceId, infraMappingId, environment.getUuid()))
        .thenReturn(workflowExecutionId);
    return spyAppDynamicsState;
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldTestNonTemplatizedBadTier() {
    AppDynamicsState spyAppDynamicsState = setupNonTemplatized(true);
    doReturn(CVInstanceApiResponse.builder()
                 .controlNodes(Collections.emptySet())
                 .testNodes(Collections.singleton("node"))
                 .newNodesTrafficShiftPercent(Optional.empty())
                 .build())
        .when(spyAppDynamicsState)
        .getCVInstanceAPIResponse(any());
    ExecutionResponse executionResponse = spyAppDynamicsState.execute(executionContext);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ERROR);
    assertThat(executionResponse.getErrorMessage())
        .isEqualTo(
            "IllegalStateException: Not able to resolve  tier ID for tier name 123aa. Please check your expression or tier name");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTestAllTemplatized() throws ParseException, IOException {
    AppDynamicsConfig appDynamicsConfig = AppDynamicsConfig.builder()
                                              .accountId(accountId)
                                              .controllerUrl("appd-url")
                                              .username("appd-user")
                                              .password("appd-pwd".toCharArray())
                                              .build();
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(accountId)
                                            .withName("appd-config")
                                            .withValue(appDynamicsConfig)
                                            .build();
    wingsPersistence.save(settingAttribute);
    appDynamicsState.setAnalysisServerConfigId(settingAttribute.getUuid());

    appDynamicsState.setTemplateExpressions(asList(TemplateExpression.builder()
                                                       .fieldName("analysisServerConfigId")
                                                       .expression("${AppDynamics_Server}")
                                                       .metadata(ImmutableMap.of("entityType", "APPDYNAMICS_CONFIGID"))
                                                       .build(),
        TemplateExpression.builder()
            .fieldName("applicationId")
            .expression("${AppDynamics_App}")
            .metadata(ImmutableMap.of("entityType", "APPDYNAMICS_APPID"))
            .build(),
        TemplateExpression.builder()
            .fieldName("tierId")
            .expression("${AppDynamics_Tier}")
            .metadata(ImmutableMap.of("entityType", "APPDYNAMICS_TIERID"))
            .build()));

    AppDynamicsState spyAppDynamicsState = spy(appDynamicsState);
    doReturn(AbstractAnalysisState.CVInstanceApiResponse.builder()
                 .controlNodes(Collections.singleton("control"))
                 .testNodes(Collections.singleton("test"))
                 .newNodesTrafficShiftPercent(Optional.empty())
                 .build())
        .when(spyAppDynamicsState)
        .getCVInstanceAPIResponse(any());
    doReturn(workflowId).when(spyAppDynamicsState).getWorkflowId(executionContext);
    doReturn(serviceId).when(spyAppDynamicsState).getPhaseServiceId(executionContext);

    when(metricAnalysisService.getLastSuccessfulWorkflowExecutionIdWithData(
             DelegateStateType.APP_DYNAMICS, appId, workflowId, serviceId, infraMappingId, environment.getUuid()))
        .thenReturn(workflowExecutionId);
    when(executionContext.renderExpression("${workflow.variables.AppDynamics_Server}"))
        .thenReturn(settingAttribute.getUuid());
    when(executionContext.renderExpression("${workflow.variables.AppDynamics_App}")).thenReturn("30444");
    when(executionContext.renderExpression("${workflow.variables.AppDynamics_Tier}")).thenReturn("30889");
    when(appdynamicsService.getTiers(anyString(), anyLong(), anyString(), anyString(), any()))
        .thenReturn(Sets.newHashSet(AppdynamicsTier.builder().id(30889).name("tier").build()));
    when(workflowStandardParamsExtensionService.getEnv(workflowStandardParams))
        .thenReturn(Environment.Builder.anEnvironment().uuid(UUID.randomUUID().toString()).build());
    when(executionContext.getContextElement(ContextElementType.STANDARD)).thenReturn(workflowStandardParams);
    ExecutionResponse executionResponse = spyAppDynamicsState.execute(executionContext);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.RUNNING);
    Map<Long,
        LinkedHashMap<String,
            LinkedHashMap<String,
                LinkedHashMap<String, LinkedHashMap<String, List<ContinuousVerificationExecutionMetaData>>>>>>
        cvExecutionMetaData =
            continuousVerificationService.getCVExecutionMetaData(accountId, 1519200000000L, 1519200000001L, user);
    assertThat(cvExecutionMetaData).isNotNull();
    ContinuousVerificationExecutionMetaData continuousVerificationExecutionMetaData1 =
        cvExecutionMetaData.get(1519171200000L)
            .get("dummy artifact")
            .get("dummy env/dummy workflow")
            .values()
            .iterator()
            .next()
            .get("BASIC")
            .get(0);
    assertThat(accountId).isEqualTo(continuousVerificationExecutionMetaData1.getAccountId());
    assertThat("dummy artifact").isEqualTo(continuousVerificationExecutionMetaData1.getArtifactName());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetMetricType() {
    String errType = AppDynamicsState.getMetricTypeForMetric(AppdynamicsConstants.ERRORS_PER_MINUTE);
    assertThat(errType).isNotNull();
    assertThat(errType).isEqualTo(MetricType.ERROR.name());
    String throughput = AppDynamicsState.getMetricTypeForMetric(AppdynamicsConstants.CALLS_PER_MINUTE);
    assertThat(throughput).isNotNull();
    assertThat(throughput).isEqualTo(MetricType.THROUGHPUT.name());
    String respTime = AppDynamicsState.getMetricTypeForMetric(AppdynamicsConstants.AVG_RESPONSE_TIME);
    assertThat(respTime).isNotNull();
    assertThat(respTime).isEqualTo(MetricType.RESP_TIME.name());

    String dummy = AppDynamicsState.getMetricTypeForMetric("incorrectName");
    assertThat(dummy).isNull();
  }

  @Test
  @Owner(developers = PRANJAL)
  @Category(UnitTests.class)
  public void testValidateFieldsMissingFieldsCase() {
    AppDynamicsState appDynamicsState = new AppDynamicsState("dummy");
    // not adding any metrics for verification
    Map<String, String> invalidFields = appDynamicsState.validateFields();
    assertThat(invalidFields.size() == 1).isTrue();
    assertThat(invalidFields.keySet().iterator().next()).isEqualTo("Required Fields missing");
  }

  @Test
  @Owner(developers = PRANJAL)
  @Category(UnitTests.class)
  public void testValidateFieldsPartialMissingFieldsCase() {
    AppDynamicsState appDynamicsState = new AppDynamicsState("dummy");
    appDynamicsState.setApplicationId("test");
    appDynamicsState.setTierId("test12");
    // not adding any metrics for verification
    Map<String, String> invalidFields = appDynamicsState.validateFields();
    assertThat(invalidFields.size() == 1).isTrue();
    assertThat(invalidFields.keySet().iterator().next()).isEqualTo("Required Fields missing");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testEmptyParam() {
    AppDynamicsState appDynamicsState = new AppDynamicsState("dummy");
    final Map<String, String> validationResult = appDynamicsState.validateFields();
    assertThat(validationResult.size()).isEqualTo(1);
    assertThat(validationResult.get("Required Fields missing"))
        .isEqualTo("Connector, Application and tier should be provided");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testValidNonTemplatized() {
    AppDynamicsState appDynamicsState = new AppDynamicsState("dummy");
    appDynamicsState.setAnalysisServerConfigId(generateUuid());
    appDynamicsState.setApplicationId("123");
    appDynamicsState.setTierId("456");

    final Map<String, String> validationResult = appDynamicsState.validateFields();
    assertThat(validationResult.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testOnlyConnectorTemplatized() {
    AppDynamicsState appDynamicsState = new AppDynamicsState("dummy");
    appDynamicsState.setAnalysisServerConfigId("${AppDynamics_Server}");
    appDynamicsState.setApplicationId("123");
    appDynamicsState.setTierId("456");
    appDynamicsState.setTemplateExpressions(
        Lists.newArrayList(TemplateExpression.builder()
                               .fieldName("analysisServerConfigId")
                               .expression("${AppDynamics_Server}")
                               .metadata(ImmutableMap.of("entityType", "APPDYNAMICS_CONFIGID"))
                               .build()));

    final Map<String, String> validationResult = appDynamicsState.validateFields();
    assertThat(validationResult.size()).isEqualTo(1);
    assertThat(validationResult.get("Invalid templatization for application"))
        .isEqualTo(
            "If connector is templatized then application should be either templatized or should be an expression");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testConnectorAndAppTemplatized() {
    AppDynamicsState appDynamicsState = new AppDynamicsState("dummy");
    appDynamicsState.setAnalysisServerConfigId("${AppDynamics_Server}");
    appDynamicsState.setApplicationId("123");
    appDynamicsState.setTierId("456");
    appDynamicsState.setTemplateExpressions(
        Lists.newArrayList(TemplateExpression.builder()
                               .fieldName("analysisServerConfigId")
                               .expression("${AppDynamics_Server}")
                               .metadata(ImmutableMap.of("entityType", "APPDYNAMICS_CONFIGID"))
                               .build(),
            TemplateExpression.builder()
                .fieldName("applicationId")
                .expression("${AppDynamics_App}")
                .metadata(ImmutableMap.of("entityType", "APPDYNAMICS_APPID"))
                .build()));

    final Map<String, String> validationResult = appDynamicsState.validateFields();
    assertThat(validationResult.size()).isEqualTo(1);
    assertThat(validationResult.get("Invalid templatization for tier"))
        .isEqualTo("If application is templatized then tier should be either templatized or should be an expression");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testAllTemplatized() {
    AppDynamicsState appDynamicsState = new AppDynamicsState("dummy");
    appDynamicsState.setTemplateExpressions(
        Lists.newArrayList(TemplateExpression.builder()
                               .fieldName("analysisServerConfigId")
                               .expression("${AppDynamics_Server}")
                               .metadata(ImmutableMap.of("entityType", "APPDYNAMICS_CONFIGID"))
                               .build(),
            TemplateExpression.builder()
                .fieldName("applicationId")
                .expression("${AppDynamics_App}")
                .metadata(ImmutableMap.of("entityType", "APPDYNAMICS_APPID"))
                .build(),
            TemplateExpression.builder()
                .fieldName("tierId")
                .expression("${AppDynamics_Tier}")
                .metadata(ImmutableMap.of("entityType", "APPDYNAMICS_TIERID"))
                .build()));

    final Map<String, String> validationResult = appDynamicsState.validateFields();
    assertThat(validationResult.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testValidateFields_whenAppExpressionAndTierTemplatized() {
    AppDynamicsState appDynamicsState = new AppDynamicsState("dummy");
    appDynamicsState.setAnalysisServerConfigId("${AppDynamics_Server}");
    appDynamicsState.setApplicationId("${app.name}");
    appDynamicsState.setTemplateExpressions(
        Lists.newArrayList(TemplateExpression.builder()
                               .fieldName("analysisServerConfigId")
                               .expression("${AppDynamics_Server}")
                               .metadata(ImmutableMap.of("entityType", "APPDYNAMICS_CONFIGID"))
                               .build(),
            TemplateExpression.builder()
                .fieldName("tierId")
                .expression("${AppDynamics_Tier}")
                .metadata(ImmutableMap.of("entityType", "APPDYNAMICS_TIERID"))
                .build()));

    final Map<String, String> validationResult = appDynamicsState.validateFields();
    assertThat(validationResult.size()).isEqualTo(1);
    assertThat(validationResult.get("Invalid expression for tier"))
        .isEqualTo("If application is an expression then tier should be an expression as well");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testValidateFields_whenAppAndTierExpressionAreValidExpressions() {
    AppDynamicsState appDynamicsState = new AppDynamicsState("dummy");
    appDynamicsState.setAnalysisServerConfigId("${AppDynamics_Server}");
    appDynamicsState.setApplicationId("${app.name}");
    appDynamicsState.setTierId("${service.name}");
    appDynamicsState.setTemplateExpressions(
        Lists.newArrayList(TemplateExpression.builder()
                               .fieldName("analysisServerConfigId")
                               .expression("${AppDynamics_Server}")
                               .metadata(ImmutableMap.of("entityType", "APPDYNAMICS_CONFIGID"))
                               .build()));

    final Map<String, String> validationResult = appDynamicsState.validateFields();
    assertThat(validationResult.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testValidateFields_whenAllFieldsSelected() {
    AppDynamicsState appDynamicsState = new AppDynamicsState("dummy");
    appDynamicsState.setAnalysisServerConfigId(generateUuid());
    appDynamicsState.setApplicationId("123");
    appDynamicsState.setTierId("456");
    final Map<String, String> validationResult = appDynamicsState.validateFields();
    assertThat(validationResult.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void shouldTestTriggered() {
    AppDynamicsConfig appDynamicsConfig = AppDynamicsConfig.builder()
                                              .accountId(accountId)
                                              .controllerUrl("appd-url")
                                              .username("appd-user")
                                              .password("appd-pwd".toCharArray())
                                              .build();
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(accountId)
                                            .withName("appd-config")
                                            .withValue(appDynamicsConfig)
                                            .build();
    wingsPersistence.save(settingAttribute);
    appDynamicsState.setAnalysisServerConfigId(settingAttribute.getUuid());
    wingsPersistence.save(WorkflowExecution.builder()
                              .appId(appId)
                              .uuid(workflowExecutionId)
                              .startTs(System.currentTimeMillis())
                              .triggeredBy(EmbeddedUser.builder().name("Deployment Trigger workflow").build())
                              .build());

    AppDynamicsState spyAppDynamicsState = spy(appDynamicsState);
    when(appdynamicsService.getAppDynamicsApplication(anyString(), anyString())).thenReturn(null);
    doThrow(new WingsException("Can not find application by name"))
        .when(appdynamicsService)
        .getAppDynamicsApplicationByName(anyString(), anyString(), anyString(), anyString());
    when(appdynamicsService.getTier(anyString(), anyLong(), anyString(), anyString(), anyString(), any()))
        .thenReturn(null);
    doThrow(new WingsException("Can not find tier by name"))
        .when(appdynamicsService)
        .getTier(anyString(), anyLong(), anyString(), anyString(), anyString(), any());

    doReturn(asList(TemplateExpression.builder()
                        .fieldName("analysisServerConfigId")
                        .expression("${AppDynamics_Server}")
                        .metadata(ImmutableMap.of("entityType", "APPDYNAMICS_CONFIGID"))
                        .build(),
                 TemplateExpression.builder()
                     .fieldName("applicationId")
                     .expression("${AppDynamics_App}")
                     .metadata(ImmutableMap.of("entityType", "APPDYNAMICS_APPID"))
                     .build(),
                 TemplateExpression.builder()
                     .fieldName("tierId")
                     .expression("${AppDynamics_Tier}")
                     .metadata(ImmutableMap.of("entityType", "APPDYNAMICS_TIERID"))
                     .build()))
        .when(spyAppDynamicsState)
        .getTemplateExpressions();

    doReturn(CVInstanceApiResponse.builder()
                 .controlNodes(Collections.singleton("control"))
                 .testNodes(Collections.singleton("test"))
                 .newNodesTrafficShiftPercent(Optional.empty())
                 .build())
        .when(spyAppDynamicsState)
        .getCVInstanceAPIResponse(any());
    doReturn(workflowId).when(spyAppDynamicsState).getWorkflowId(executionContext);
    doReturn(serviceId).when(spyAppDynamicsState).getPhaseServiceId(executionContext);

    when(metricAnalysisService.getLastSuccessfulWorkflowExecutionIdWithData(
             DelegateStateType.APP_DYNAMICS, appId, workflowId, serviceId, infraMappingId, environment.getUuid()))
        .thenReturn(workflowExecutionId);
    when(executionContext.renderExpression("${workflow.variables.AppDynamics_Server}"))
        .thenReturn(settingAttribute.getUuid());
    when(executionContext.renderExpression("${workflow.variables.AppDynamics_App}")).thenReturn("test_app");
    when(executionContext.renderExpression("${workflow.variables.AppDynamics_Tier}")).thenReturn("test_tier");
    doReturn(Environment.Builder.anEnvironment().uuid(UUID.randomUUID().toString()).build())
        .when(workflowStandardParamsExtensionService)
        .getEnv(workflowStandardParams);
    when(executionContext.getContextElement(ContextElementType.STANDARD)).thenReturn(workflowStandardParams);

    doReturn(CVInstanceApiResponse.builder()
                 .controlNodes(Collections.emptySet())
                 .testNodes(Collections.singleton("node"))
                 .newNodesTrafficShiftPercent(Optional.empty())
                 .build())
        .when(spyAppDynamicsState)
        .getCVInstanceAPIResponse(any());

    ExecutionResponse executionResponse = spyAppDynamicsState.execute(executionContext);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ERROR);
    assertThat(executionResponse.getErrorMessage()).isEqualTo("Can not find application by name");

    when(executionContext.renderExpression("${workflow.variables.AppDynamics_App}")).thenReturn("7689");

    doReturn(NewRelicApplication.builder().build())
        .when(appdynamicsService)
        .getAppDynamicsApplication(anyString(), anyString());
    executionResponse = spyAppDynamicsState.execute(executionContext);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ERROR);
    assertThat(executionResponse.getErrorMessage())
        .isEqualTo(
            "IllegalStateException: Not able to resolve  tier ID for tier name test_tier. Please check your expression or tier name");

    when(executionContext.renderExpression("${workflow.variables.AppDynamics_Tier}")).thenReturn("30889");

    long tierId = 30889;
    doReturn(AppdynamicsTier.builder().name(generateUuid()).id(tierId).build())
        .when(appdynamicsService)
        .getTier(anyString(), anyLong(), anyString(), anyString(), anyString(), any());
    doReturn(Sets.newHashSet(AppdynamicsTier.builder().name(generateUuid()).id(tierId).build()))
        .when(appdynamicsService)
        .getTiers(anyString(), anyLong(), anyString(), anyString(), any());
    executionResponse = spyAppDynamicsState.execute(executionContext);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(RUNNING);

    when(executionContext.renderExpression("${workflow.variables.AppDynamics_Tier}")).thenReturn("test_tier");

    when(appdynamicsService.getTier(anyString(), anyLong(), anyString(), anyString(), anyString(), any()))
        .thenReturn(null);
    doThrow(new WingsException("No tier found"))
        .when(appdynamicsService)
        .getTierByName(anyString(), anyString(), anyString(), anyString(), anyString(), any());

    executionResponse = spyAppDynamicsState.execute(executionContext);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ERROR);
    assertThat(executionResponse.getErrorMessage()).isEqualTo("No tier found");

    when(executionContext.renderExpression("${workflow.variables.AppDynamics_App}")).thenReturn("test_app");
    when(appdynamicsService.getAppDynamicsApplication(anyString(), anyString())).thenReturn(null);
    doThrow(new WingsException("No app found"))
        .when(appdynamicsService)
        .getAppDynamicsApplicationByName(anyString(), anyString(), anyString(), anyString());

    executionResponse = spyAppDynamicsState.execute(executionContext);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ERROR);
    assertThat(executionResponse.getErrorMessage()).isEqualTo("No app found");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testTriggerAnalysisDataCollectionExpression_whenOnlyConnectorTemplatized() {
    AppDynamicsConfig appDynamicsConfig = AppDynamicsConfig.builder()
                                              .accountId(accountId)
                                              .controllerUrl("appd-url")
                                              .username("appd-user")
                                              .password("appd-pwd".toCharArray())
                                              .build();
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(accountId)
                                            .withName("appd-config")
                                            .withValue(appDynamicsConfig)
                                            .build();
    final String appdConfigId = wingsPersistence.save(settingAttribute);
    appDynamicsState.setApplicationId("${appd.application}");
    appDynamicsState.setTierId("${appd.tier}");

    when(executionContext.renderExpression("${appd.application}")).thenReturn("appd-application");
    when(executionContext.renderExpression("${appd.tier}")).thenReturn("appd-tier");
    AppDynamicsState spyAppDynamicsState = spy(appDynamicsState);
    when(appdynamicsService.getAppDynamicsApplicationByName(
             appdConfigId, "appd-application", appId, workflowExecutionId))
        .thenReturn(applicationId);
    when(appdynamicsService.getTierByName(eq(appdConfigId), eq(applicationId), eq("appd-tier"), anyString(),
             anyString(), any(ThirdPartyApiCallLog.class)))
        .thenReturn(tierId);
    when(appdynamicsService.getTiers(eq(appdConfigId), eq(Long.parseLong(applicationId)), anyString(), anyString(),
             any(ThirdPartyApiCallLog.class)))
        .thenReturn(Sets.newHashSet(AppdynamicsTier.builder().name(generateUuid()).id(Long.parseLong(tierId)).build()));

    doReturn(asList(TemplateExpression.builder()
                        .fieldName("analysisServerConfigId")
                        .expression("${AppDynamics_Server}")
                        .metadata(ImmutableMap.of("entityType", "APPDYNAMICS_CONFIGID"))
                        .build()))
        .when(spyAppDynamicsState)
        .getTemplateExpressions();

    when(executionContext.renderExpression("${workflow.variables.AppDynamics_Server}"))
        .thenReturn(settingAttribute.getUuid());

    spyAppDynamicsState.triggerAnalysisDataCollection(executionContext, AnalysisContext.builder().build(),
        VerificationStateAnalysisExecutionData.builder().build(), Collections.singletonMap("host", "groupName"));
    ArgumentCaptor<DelegateTask> argument = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(argument.capture());
    TaskData taskData = argument.getValue().getData();
    Object parameters[] = taskData.getParameters();
    assertThat(1).isEqualTo(parameters.length);
    assertThat(TaskType.APPDYNAMICS_COLLECT_METRIC_DATA.name()).isEqualTo(taskData.getTaskType());
    AppdynamicsDataCollectionInfo appdynamicsDataCollectionInfo = (AppdynamicsDataCollectionInfo) parameters[0];
    assertThat(appdynamicsDataCollectionInfo.getAppDynamicsConfig().getUsername())
        .isEqualTo(appDynamicsConfig.getUsername());
    assertThat(appdynamicsDataCollectionInfo.getAppId()).isEqualTo(Long.parseLong(applicationId));
    assertThat(appdynamicsDataCollectionInfo.getTierId()).isEqualTo(Long.parseLong(tierId));
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testTriggerAnalysisDataCollectionExpression_whenConnectorAndApplicationTemplatized() throws IOException {
    AppDynamicsConfig appDynamicsConfig = AppDynamicsConfig.builder()
                                              .accountId(accountId)
                                              .controllerUrl("appd-url")
                                              .username("appd-user")
                                              .password("appd-pwd".toCharArray())
                                              .build();
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(accountId)
                                            .withName("appd-config")
                                            .withValue(appDynamicsConfig)
                                            .build();
    final String appdConfigId = wingsPersistence.save(settingAttribute);
    appDynamicsState.setTierId("${appd.tier}");

    when(executionContext.renderExpression("${appd.tier}")).thenReturn("appd-tier");
    AppDynamicsState spyAppDynamicsState = spy(appDynamicsState);
    when(appdynamicsService.getAppDynamicsApplicationByName(
             appdConfigId, "appd-application", appId, workflowExecutionId))
        .thenReturn(applicationId);
    when(appdynamicsService.getTierByName(eq(appdConfigId), eq(applicationId), eq("appd-tier"), anyString(),
             anyString(), any(ThirdPartyApiCallLog.class)))
        .thenReturn(tierId);
    when(appdynamicsService.getTiers(eq(appdConfigId), eq(Long.parseLong(applicationId)), anyString(), anyString(),
             any(ThirdPartyApiCallLog.class)))
        .thenReturn(Sets.newHashSet(AppdynamicsTier.builder().name(generateUuid()).id(Long.parseLong(tierId)).build()));

    doReturn(asList(TemplateExpression.builder()
                        .fieldName("analysisServerConfigId")
                        .expression("${AppDynamics_Server}")
                        .metadata(ImmutableMap.of("entityType", "APPDYNAMICS_CONFIGID"))
                        .build(),
                 TemplateExpression.builder()
                     .fieldName("applicationId")
                     .expression("${AppDynamics_App}")
                     .metadata(ImmutableMap.of("entityType", "APPDYNAMICS_APPID"))
                     .build()))
        .when(spyAppDynamicsState)
        .getTemplateExpressions();

    when(executionContext.renderExpression("${workflow.variables.AppDynamics_Server}"))
        .thenReturn(settingAttribute.getUuid());
    when(executionContext.renderExpression("${workflow.variables.AppDynamics_App}")).thenReturn("appd-application");

    spyAppDynamicsState.triggerAnalysisDataCollection(executionContext, AnalysisContext.builder().build(),
        VerificationStateAnalysisExecutionData.builder().build(), Collections.singletonMap("host", "groupName"));
    ArgumentCaptor<DelegateTask> argument = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(argument.capture());
    TaskData taskData = argument.getValue().getData();
    Object parameters[] = taskData.getParameters();
    assertThat(1).isEqualTo(parameters.length);
    assertThat(TaskType.APPDYNAMICS_COLLECT_METRIC_DATA.name()).isEqualTo(taskData.getTaskType());
    AppdynamicsDataCollectionInfo appdynamicsDataCollectionInfo = (AppdynamicsDataCollectionInfo) parameters[0];
    assertThat(appdynamicsDataCollectionInfo.getAppDynamicsConfig().getUsername())
        .isEqualTo(appDynamicsConfig.getUsername());
    assertThat(appdynamicsDataCollectionInfo.getAppId()).isEqualTo(Long.parseLong(applicationId));
    assertThat(appdynamicsDataCollectionInfo.getTierId()).isEqualTo(Long.parseLong(tierId));
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testGetResolvedConnectorId_whenNoTemplatizationOrExpression() {
    AppDynamicsState appDynamicsState = new AppDynamicsState("dummy");
    appDynamicsState.setAnalysisServerConfigId(generateUuid());
    appDynamicsState.setApplicationId("123");
    appDynamicsState.setTierId("456");
    final String resolvedConnectorId = appDynamicsState.getResolvedConnectorId(
        executionContext, AppDynamicsStateKeys.analysisServerConfigId, appDynamicsState.getAnalysisServerConfigId());

    assertThat(resolvedConnectorId).isEqualTo(appDynamicsState.getAnalysisServerConfigId());
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testGetResolvedConnectorId_whenTemplatizedWithInValidValue() {
    appDynamicsState.setTemplateExpressions(asList(TemplateExpression.builder()
                                                       .fieldName(AppDynamicsStateKeys.analysisServerConfigId)
                                                       .expression("${AppDynamics_Server}")
                                                       .metadata(ImmutableMap.of("entityType", "APPDYNAMICS_CONFIGID"))
                                                       .build()));

    assertThatThrownBy(
        ()
            -> appDynamicsState.getResolvedConnectorId(executionContext, AppDynamicsStateKeys.analysisServerConfigId,
                appDynamicsState.getAnalysisServerConfigId()))
        .isInstanceOf(WingsException.class)
        .hasMessage("No value provided for template expression  [${AppDynamics_Server}]");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testGetResolvedConnectorId_whenTemplatizedWithValidValue() {
    AppDynamicsConfig appDynamicsConfig = AppDynamicsConfig.builder()
                                              .accountId(accountId)
                                              .controllerUrl("appd-url")
                                              .username("appd-user")
                                              .password("appd-pwd".toCharArray())
                                              .build();
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(accountId)
                                            .withName("appd-config")
                                            .withValue(appDynamicsConfig)
                                            .build();
    wingsPersistence.save(settingAttribute);
    appDynamicsState.setTemplateExpressions(asList(TemplateExpression.builder()
                                                       .fieldName(AppDynamicsStateKeys.analysisServerConfigId)
                                                       .expression("${AppDynamics_Server}")
                                                       .metadata(ImmutableMap.of("entityType", "APPDYNAMICS_CONFIGID"))
                                                       .build()));
    when(executionContext.renderExpression("${workflow.variables.AppDynamics_Server}"))
        .thenReturn(settingAttribute.getUuid());
    final String resolvedConnectorId =
        appDynamicsState.getResolvedConnectorId(executionContext, AppDynamicsStateKeys.analysisServerConfigId, null);
    assertThat(resolvedConnectorId).isEqualTo(settingAttribute.getUuid());
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testGetResolvedConnectorId_whenExpressionInvalidValue() {
    appDynamicsState.setAnalysisServerConfigId("${connector.name}");
    assertThatThrownBy(
        ()
            -> appDynamicsState.getResolvedConnectorId(executionContext, AppDynamicsStateKeys.analysisServerConfigId,
                appDynamicsState.getAnalysisServerConfigId()))
        .isInstanceOf(DataCollectionException.class)
        .hasMessage("Expression " + appDynamicsState.getAnalysisServerConfigId() + " resolved to "
            + appDynamicsState.getAnalysisServerConfigId() + ". There was no connector found with this name.");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testGetResolvedConnectorId_whenExpressionValidValue() {
    AppDynamicsConfig appDynamicsConfig = AppDynamicsConfig.builder()
                                              .accountId(accountId)
                                              .controllerUrl("appd-url")
                                              .username("appd-user")
                                              .password("appd-pwd".toCharArray())
                                              .build();
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(accountId)
                                            .withName("appd-config")
                                            .withValue(appDynamicsConfig)
                                            .build();
    wingsPersistence.save(settingAttribute);
    appDynamicsState.setAnalysisServerConfigId("${connector.name}");
    when(executionContext.renderExpression("${connector.name}")).thenReturn(settingAttribute.getName());
    when(executionContext.getAccountId()).thenReturn(accountId);
    final String resolvedConnectorId = appDynamicsState.getResolvedConnectorId(
        executionContext, AppDynamicsStateKeys.analysisServerConfigId, appDynamicsState.getAnalysisServerConfigId());
    assertThat(resolvedConnectorId).isEqualTo(settingAttribute.getUuid());
  }
}
