/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRAVEEN;
import static io.harness.rule.OwnerRule.SOWMYA;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.common.VerificationConstants.URL_BODY_APPENDER;
import static software.wings.service.impl.analysis.AnalysisComparisonStrategy.COMPARE_WITH_CURRENT;
import static software.wings.sm.StateExecutionInstance.Builder.aStateExecutionInstance;
import static software.wings.sm.states.CustomLogVerificationState.constructLogDefinitions;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.ENV_NAME;
import static software.wings.utils.WingsTestConstants.STATE_EXECUTION_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.DelegateTask;
import io.harness.beans.EnvironmentType;
import io.harness.category.element.UnitTests;
import io.harness.context.ContextElementType;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;
import io.harness.serializer.YamlUtils;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.WingsBaseTest;
import software.wings.api.ContextElementParamMapperFactory;
import software.wings.api.HostElement;
import software.wings.beans.APMVerificationConfig;
import software.wings.beans.Application;
import software.wings.beans.LogCollectionInfo;
import software.wings.beans.SettingAttribute;
import software.wings.delegatetasks.cv.beans.CustomLogResponseMapper;
import software.wings.helpers.ext.url.SubdomainUrlHelperIntfc;
import software.wings.service.impl.analysis.CustomLogDataCollectionInfo;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.BuildSourceService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;
import software.wings.verification.VerificationStateAnalysisExecutionData;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class CustomLogVerificationStateTest extends WingsBaseTest {
  @Inject private Injector injector;
  @Mock private WorkflowStandardParams workflowStandardParams;

  @Mock private SettingsService settingsService;
  @Mock private AppService appService;
  @Mock private WaitNotifyEngine waitNotifyEngine;
  @Mock private DelegateService delegateService;
  @Mock private SecretManager secretManager;
  @Mock private WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;

  private CustomLogVerificationState customLogVerificationState;
  private APMVerificationConfig apmVerificationConfig;
  private String configId;
  private VerificationStateAnalysisExecutionData executionData;
  private Set<String> hosts;
  private String workflowId;
  private String serviceId;

  private ExecutionContextImpl context;

  /**
   * Sets context.
   */
  @Before
  public void setupContext() throws IllegalAccessException {
    MockitoAnnotations.initMocks(this);

    StateExecutionInstance stateExecutionInstance =
        aStateExecutionInstance().displayName("healthCheck1").uuid(STATE_EXECUTION_ID).build();
    when(workflowStandardParamsExtensionService.getApp(workflowStandardParams))
        .thenReturn(anApplication().uuid(APP_ID).name(APP_NAME).build());
    when(workflowStandardParamsExtensionService.getEnv(workflowStandardParams))
        .thenReturn(anEnvironment().uuid(ENV_ID).name(ENV_NAME).environmentType(EnvironmentType.NON_PROD).build());
    when(workflowStandardParams.getAppId()).thenReturn(APP_ID);
    when(workflowStandardParams.getEnvId()).thenReturn(ENV_ID);

    when(workflowStandardParams.getElementType()).thenReturn(ContextElementType.STANDARD);
    context = new ExecutionContextImpl(stateExecutionInstance, null, injector);
    context.pushContextElement(workflowStandardParams);
    context.pushContextElement(HostElement.builder().hostName("localhost").build());
    FieldUtils.writeField(
        context, "workflowStandardParamsExtensionService", workflowStandardParamsExtensionService, true);

    ContextElementParamMapperFactory contextElementParamMapperFactory = new ContextElementParamMapperFactory(
        injector.getInstance(SubdomainUrlHelperIntfc.class), injector.getInstance(WorkflowExecutionService.class),
        injector.getInstance(ArtifactService.class), injector.getInstance(ArtifactStreamService.class),
        injector.getInstance(ApplicationManifestService.class), injector.getInstance(FeatureFlagService.class),
        injector.getInstance(BuildSourceService.class), workflowStandardParamsExtensionService);
    FieldUtils.writeField(context, "contextElementParamMapperFactory", contextElementParamMapperFactory, true);

    String accountId = generateUuid();
    String appId = generateUuid();
    configId = generateUuid();
    workflowId = generateUuid();
    serviceId = generateUuid();
    customLogVerificationState = Mockito.spy(new CustomLogVerificationState("customState"));
    apmVerificationConfig = new APMVerificationConfig();
    apmVerificationConfig.setAccountId(accountId);
    apmVerificationConfig.setUrl("http://baseUrl.com");
    apmVerificationConfig.setValidationUrl("http://validationUrl.com");
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(accountId)
                                            .withAppId(appId)
                                            .withValue(apmVerificationConfig)
                                            .build();
    executionData = VerificationStateAnalysisExecutionData.builder().build();
    hosts = new HashSet<>();
    hosts.add("host1");

    Application app = new Application();
    app.setName("name");

    FieldUtils.writeField(customLogVerificationState, "settingsService", settingsService, true);
    FieldUtils.writeField(customLogVerificationState, "appService", appService, true);
    FieldUtils.writeField(customLogVerificationState, "waitNotifyEngine", waitNotifyEngine, true);
    FieldUtils.writeField(customLogVerificationState, "delegateService", delegateService, true);
    FieldUtils.writeField(customLogVerificationState, "secretManager", secretManager, true);
    FieldUtils.writeField(customLogVerificationState, "workflowStandardParamsExtensionService",
        workflowStandardParamsExtensionService, true);

    when(settingsService.get(configId)).thenReturn(settingAttribute);
    when(appService.get(any())).thenReturn(app);

    doReturn(configId).when(customLogVerificationState).getResolvedConnectorId(any(), any(), any());
    doReturn(workflowId).when(customLogVerificationState).getWorkflowId(any());
    doReturn(serviceId).when(customLogVerificationState).getPhaseServiceId(any());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testConstructLogDefinitions() throws IOException {
    CustomLogVerificationState state = new CustomLogVerificationState("dummy");
    YamlUtils yamlUtils = new YamlUtils();
    String yamlStr =
        Resources.toString(CustomLogVerificationStateTest.class.getResource("/apm/log_config.yml"), Charsets.UTF_8);
    List<LogCollectionInfo> collectionInfos = yamlUtils.read(yamlStr, new TypeReference<List<LogCollectionInfo>>() {});
    state.setLogCollectionInfos(collectionInfos);

    Map<String, Map<String, CustomLogResponseMapper>> logDefinitions =
        constructLogDefinitions(context, collectionInfos);
    assertThat(logDefinitions).isNotNull();
    assertThat(logDefinitions.containsKey("customLogVerificationQuery")).isTrue();
    Map<String, CustomLogResponseMapper> mapping = logDefinitions.get("customLogVerificationQuery");
    assertThat("hits.hits[*]._source.kubernetes.pod.name").isEqualTo(mapping.get("host").getJsonPath().get(0));
    assertThat("hits.hits[*]._source.@timestamp").isEqualTo(mapping.get("timestamp").getJsonPath().get(0));
    assertThat(mapping.get("timestamp").getTimestampFormat()).isEqualTo("hh:mm a");
    assertThat("hits.hits[*]._source.log").isEqualTo(mapping.get("logMessage").getJsonPath().get(0));
    assertThat(state.shouldInspectHostsForLogAnalysis()).isTrue();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testConstructLogDefinitions_postWithBody() throws IOException {
    CustomLogVerificationState state = new CustomLogVerificationState("dummy");
    YamlUtils yamlUtils = new YamlUtils();
    String yamlStr = Resources.toString(
        CustomLogVerificationStateTest.class.getResource("/apm/log_config_post.yml"), Charsets.UTF_8);
    List<LogCollectionInfo> collectionInfos = yamlUtils.read(yamlStr, new TypeReference<List<LogCollectionInfo>>() {});
    state.setLogCollectionInfos(collectionInfos);
    ExecutionContextImpl executionContext = mock(ExecutionContextImpl.class);
    when(executionContext.renderExpression(anyString()))
        .thenAnswer(invocation -> invocation.getArgument(0, String.class));
    Map<String, Map<String, CustomLogResponseMapper>> logDefinitions =
        constructLogDefinitions(executionContext, collectionInfos);
    assertThat(logDefinitions).isNotNull();
    assertThat(logDefinitions.size()).isEqualTo(1);
    logDefinitions.forEach((url, mapping) -> {
      assertThat(url.contains(URL_BODY_APPENDER)).isTrue();
      String actualUrl = url.split(URL_BODY_APPENDER)[0];
      String body = url.split(URL_BODY_APPENDER)[1];
      assertThat(actualUrl).isEqualTo("customLogVerificationQuery");
      assertThat(body).isEqualTo("body${host}");
      assertThat("hits.hits[*]._source.kubernetes.pod.name").isEqualTo(mapping.get("host").getJsonPath().get(0));
      assertThat("hits.hits[*]._source.@timestamp").isEqualTo(mapping.get("timestamp").getJsonPath().get(0));
      assertThat(mapping.get("timestamp").getTimestampFormat()).isEqualTo("hh:mm a");
      assertThat("hits.hits[*]._source.log").isEqualTo(mapping.get("logMessage").getJsonPath().get(0));
      assertThat(state.shouldInspectHostsForLogAnalysis()).isTrue();
    });
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testConstructLogDefinitions_postWithBodyContainingExpressions() throws IOException {
    CustomLogVerificationState state = new CustomLogVerificationState("dummy");
    YamlUtils yamlUtils = new YamlUtils();
    String yamlStr = Resources.toString(
        CustomLogVerificationStateTest.class.getResource("/apm/log_config_post_expressions.yml"), Charsets.UTF_8);
    List<LogCollectionInfo> collectionInfos = yamlUtils.read(yamlStr, new TypeReference<List<LogCollectionInfo>>() {});
    state.setLogCollectionInfos(collectionInfos);
    ExecutionContextImpl executionContext = mock(ExecutionContextImpl.class);
    when(executionContext.renderExpression(anyString()))
        .thenAnswer(invocation -> invocation.getArgument(0, String.class));
    when(executionContext.renderExpression("body${workflow.variable.bodyDetails}")).thenReturn("renderedBodyDetails");
    Map<String, Map<String, CustomLogResponseMapper>> logDefinitions =
        constructLogDefinitions(executionContext, collectionInfos);
    assertThat(logDefinitions).isNotNull();
    assertThat(logDefinitions.size()).isEqualTo(1);
    logDefinitions.forEach((url, mapping) -> {
      assertThat(url.contains(URL_BODY_APPENDER)).isTrue();
      String actualUrl = url.split(URL_BODY_APPENDER)[0];
      String body = url.split(URL_BODY_APPENDER)[1];
      assertThat(actualUrl).isEqualTo("customLogVerificationQuery${host}");
      assertThat(body).isEqualTo("renderedBodyDetails");
      assertThat("hits.hits[*]._source.kubernetes.pod.name").isEqualTo(mapping.get("host").getJsonPath().get(0));
      assertThat("hits.hits[*]._source.@timestamp").isEqualTo(mapping.get("timestamp").getJsonPath().get(0));
      assertThat(mapping.get("timestamp").getTimestampFormat()).isEqualTo("hh:mm a");
      assertThat("hits.hits[*]._source.log").isEqualTo(mapping.get("logMessage").getJsonPath().get(0));
      assertThat(state.shouldInspectHostsForLogAnalysis()).isTrue();
    });
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testShouldDoHostFiltering() throws IOException {
    CustomLogVerificationState state = new CustomLogVerificationState("dummy");
    YamlUtils yamlUtils = new YamlUtils();
    String yamlStr = Resources.toString(
        CustomLogVerificationStateTest.class.getResource("/apm/log_config_noHost.yml"), Charsets.UTF_8);
    List<LogCollectionInfo> collectionInfos = yamlUtils.read(yamlStr, new TypeReference<List<LogCollectionInfo>>() {});
    state.setLogCollectionInfos(collectionInfos);

    boolean shouldDOHostFiltering = state.shouldInspectHostsForLogAnalysis();
    assertThat(shouldDOHostFiltering).isFalse();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testShouldDoHostFiltering_true() throws IOException {
    CustomLogVerificationState state = new CustomLogVerificationState("dummy");
    YamlUtils yamlUtils = new YamlUtils();
    String yamlStr =
        Resources.toString(CustomLogVerificationStateTest.class.getResource("/apm/log_config.yml"), Charsets.UTF_8);
    List<LogCollectionInfo> collectionInfos = yamlUtils.read(yamlStr, new TypeReference<List<LogCollectionInfo>>() {});
    state.setLogCollectionInfos(collectionInfos);

    boolean shouldDOHostFiltering = state.shouldInspectHostsForLogAnalysis();
    assertThat(shouldDOHostFiltering).isTrue();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testTriggerAnalysisDataCollection_whenConnectorIdIsValid() {
    doReturn(COMPARE_WITH_CURRENT).when(customLogVerificationState).getComparisonStrategy();
    customLogVerificationState.setLogCollectionInfos(new ArrayList<>());
    customLogVerificationState.triggerAnalysisDataCollection(context, executionData, hosts);

    ArgumentCaptor<DelegateTask> delegateTaskArgumentCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService, times(1)).queueTaskV2(delegateTaskArgumentCaptor.capture());

    DelegateTask task = delegateTaskArgumentCaptor.getValue();
    CustomLogDataCollectionInfo dataCollectionInfo = (CustomLogDataCollectionInfo) task.getData().getParameters()[0];

    assertThat(dataCollectionInfo.getBaseUrl()).isEqualTo(apmVerificationConfig.getUrl());
    assertThat(dataCollectionInfo.getValidationUrl()).isEqualTo(apmVerificationConfig.getValidationUrl());
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testTriggerAnalysisDataCollection_whenConnectorIdIsInValid() {
    customLogVerificationState.setAnalysisServerConfigId(configId);
    when(settingsService.get(configId)).thenReturn(null);
    assertThatThrownBy(() -> customLogVerificationState.triggerAnalysisDataCollection(context, executionData, hosts))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("No connector found with id " + configId);
  }
}
