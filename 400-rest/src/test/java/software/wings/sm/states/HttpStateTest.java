/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.FeatureName.TIMEOUT_FAILURE_SUPPORT;
import static io.harness.rule.OwnerRule.DINESH;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.LUCAS_SALES;
import static io.harness.rule.OwnerRule.MARKO;
import static io.harness.rule.OwnerRule.MILOS;
import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.rule.OwnerRule.SRINIVAS;

import static software.wings.api.InstanceElement.Builder.anInstanceElement;
import static software.wings.api.ServiceTemplateElement.Builder.aServiceTemplateElement;
import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.Variable.VariableBuilder.aVariable;
import static software.wings.persistence.artifact.Artifact.Builder.anArtifact;
import static software.wings.sm.ContextElement.ARTIFACT;
import static software.wings.sm.StateExecutionInstance.Builder.aStateExecutionInstance;
import static software.wings.sm.states.HttpState.Builder.aHttpState;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.DELEGATE_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.ENV_NAME;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_INSTANCE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_NAME;
import static software.wings.utils.WingsTestConstants.STATE_EXECUTION_ID;
import static software.wings.utils.WingsTestConstants.TEMPLATE_ID;
import static software.wings.utils.WingsTestConstants.TEMPLATE_NAME;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ArtifactMetadata;
import io.harness.beans.DelegateTask;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.EnvironmentType;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.KeyValuePair;
import io.harness.beans.TriggeredBy;
import io.harness.category.element.UnitTests;
import io.harness.context.ContextElementType;
import io.harness.delegate.beans.DelegateTaskDetails;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.task.common.DelegateRunnableTask;
import io.harness.delegate.utils.DelegateTaskMigrationHelper;
import io.harness.exception.FailureType;
import io.harness.ff.FeatureFlagService;
import io.harness.http.HttpServiceImpl;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.ContextElementParamMapper;
import software.wings.api.ContextElementParamMapperFactory;
import software.wings.api.HostElement;
import software.wings.api.HttpStateExecutionData;
import software.wings.api.ServiceElement;
import software.wings.beans.Activity;
import software.wings.beans.Activity.Type;
import software.wings.beans.Application;
import software.wings.beans.Variable;
import software.wings.beans.artifact.ArtifactMetadataKeys;
import software.wings.beans.template.TemplateUtils;
import software.wings.delegatetasks.HttpTask;
import software.wings.service.impl.AccountServiceImpl;
import software.wings.service.impl.ActivityHelperService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.StateExecutionData;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.StateType;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;
import software.wings.sm.WorkflowStandardParamsParamMapper;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

/**
 * The Class HttpStateTest.
 */
@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
public class HttpStateTest extends WingsBaseTest {
  private static final HttpState.Builder httpStateBuilder =
      aHttpState()
          .withName("healthCheck1")
          .withMethod("GET")
          .withUrl("http://${host.hostName}:8088/health/status")
          .withHeaders(Lists.newArrayList(KeyValuePair.builder().key("Content-Type").value("application/xml").build(),
              KeyValuePair.builder().key("Content-Type").value("application/xml").build(),
              KeyValuePair.builder().key("Accept").value("*/*").build()))
          .withAssertion(
              "(${httpResponseCode}==200 || ${httpResponseCode}==201) && ${xmlFormat()} && ${xpath('//health/status/text()')}.equals('Enabled')");

  private static final Activity activity =
      Activity.builder()
          .applicationName(APP_NAME)
          .environmentId(ENV_ID)
          .environmentName(ENV_NAME)
          .environmentType(EnvironmentType.NON_PROD)
          .commandName("healthCheck1")
          .type(Type.Verification)
          .stateExecutionInstanceId(STATE_EXECUTION_ID)
          .stateExecutionInstanceName("healthCheck1")
          .commandType(StateType.HTTP.name())
          .status(ExecutionStatus.RUNNING)
          .commandUnits(Collections.emptyList())
          .triggeredBy(TriggeredBy.builder().name("test").email("test@harness.io").build())
          .build();

  static {
    activity.setUuid(ACTIVITY_ID);
    activity.setAppId(APP_ID);
    activity.setValidUntil(null);
  }
  /**
   * The Wire mock rule.
   */
  @Rule public WireMockRule wireMockRule = new WireMockRule(8088);

  @Inject private HttpServiceImpl httpService;
  @Inject private Injector injector;

  @Mock private WorkflowStandardParams workflowStandardParams;
  @Mock private WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;
  @Mock private ActivityHelperService activityHelperService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private DelegateService delegateService;
  @Mock private ExecutionContextImpl executionContext;
  @Mock private TemplateUtils templateUtils;
  @Mock private StateExecutionService stateExecutionService;
  @Mock private AccountServiceImpl accountService;
  @Mock private InfrastructureMappingService infrastructureMappingService;
  @Mock private DelegateTaskMigrationHelper delegateTaskMigrationHelper;

  private ExecutionResponse asyncExecutionResponse;

  private ExecutionContextImpl context;

  /**
   * Sets context.
   */
  @Before
  public void setupContext() {
    StateExecutionInstance stateExecutionInstance =
        aStateExecutionInstance().displayName("healthCheck1").uuid(STATE_EXECUTION_ID).build();

    Application app = anApplication().accountId(ACCOUNT_ID).build();

    Map<String, StateExecutionData> stateExecutionMap = new HashMap<>();
    stateExecutionMap.put("healthCheck1", HttpStateExecutionData.builder().build());
    stateExecutionInstance.setStateExecutionMap(stateExecutionMap);

    when(accountService.isCertValidationRequired(any())).thenReturn(false);

    when(workflowStandardParamsExtensionService.fetchRequiredApp(workflowStandardParams))
        .thenReturn(anApplication().uuid(APP_ID).name(APP_NAME).build());
    when(workflowStandardParamsExtensionService.getEnv(workflowStandardParams))
        .thenReturn(anEnvironment().uuid(ENV_ID).name(ENV_NAME).environmentType(EnvironmentType.NON_PROD).build());
    when(workflowStandardParamsExtensionService.getApp(workflowStandardParams)).thenReturn(app);
    when(workflowStandardParams.getAppId()).thenReturn(APP_ID);
    when(workflowStandardParams.getEnvId()).thenReturn(ENV_ID);
    when(workflowStandardParams.getElementType()).thenReturn(ContextElementType.STANDARD);

    context = new ExecutionContextImpl(stateExecutionInstance, null, injector);
    context.pushContextElement(workflowStandardParams);
    context.pushContextElement(HostElement.builder().hostName("localhost").build());

    EmbeddedUser currentUser = EmbeddedUser.builder().name("test").email("test@harness.io").build();
    workflowStandardParams.setCurrentUser(currentUser);
    when(executionContext.getContextElement(ContextElementType.STANDARD)).thenReturn(workflowStandardParams);
    when(workflowStandardParams.getCurrentUser()).thenReturn(currentUser);

    when(activityHelperService.createAndSaveActivity(any(), any(), any(), any(), any())).thenReturn(activity);
    when(featureFlagService.isEnabled(TIMEOUT_FAILURE_SUPPORT, ACCOUNT_ID)).thenReturn(true);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetPatternsForRequiredContextElementType() {
    List<String> patternsForRequiredContextElementType =
        getHttpState(httpStateBuilder, context).getPatternsForRequiredContextElementType();
    assertThat(patternsForRequiredContextElementType).isNotEmpty();
    assertThat(patternsForRequiredContextElementType)
        .contains(
            "(${httpResponseCode}==200 || ${httpResponseCode}==201) && ${xmlFormat()} && ${xpath('//health/status/text()')}.equals('Enabled')");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetTemplatedHttpPatternsForRequiredContextElementType() {
    List<String> patternsForRequiredContextElementType =
        getHttpState(httpStateBuilder, context).getPatternsForRequiredContextElementType();
    assertThat(patternsForRequiredContextElementType).isNotEmpty();
    assertThat(patternsForRequiredContextElementType)
        .contains(
            "(${httpResponseCode}==200 || ${httpResponseCode}==201) && ${xmlFormat()} && ${xpath('//health/status/text()')}.equals('Enabled')");
  }

  /**
   * Should execute and evaluate JSON response.
   */
  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldExecuteAndEvaluateJsonResponse() throws IllegalAccessException {
    wireMockRule.stubFor(
        get(urlEqualTo("/health/status"))
            .withHeader("Content-Type", equalTo("application/json"))
            .withHeader("Accept", equalTo("*/*"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/json")
                    .withBody(
                        "{\"status\":{\"code\":\"SUCCESS\"},\"data\":{\"title\":\"Some server\",\"version\":\"2.31.0-MASTER-SNAPSHOT\",\"buildTimestamp\":1506086747259}}")));

    HttpState.Builder jsonHttpStateBuilder =
        aHttpState()
            .withName("healthCheck1")
            .withMethod("GET")
            .withUrl("http://${host.hostName}:8088/health/status")
            .withHeaders(
                Lists.newArrayList(KeyValuePair.builder().key("Content-Type").value("application/json").build(),
                    KeyValuePair.builder().key("Accept").value("*/*").build()))
            .withAssertion("${httpResponseCode}==200 && ${jsonpath(\"data.version\")}==${artifact.buildNo}");

    HttpState httpState = getHttpState(jsonHttpStateBuilder.but(), context);
    FieldUtils.writeField(httpState, "stateExecutionService", stateExecutionService, true);

    // overwrite mapper factory set in getHttpState
    ContextElementParamMapper workflowStandardParamsMapper = spy(ContextElementParamMapper.class);
    when(workflowStandardParamsMapper.paramMap(context))
        .thenReturn(ImmutableMap.of(ARTIFACT,
            anArtifact()
                .withMetadata(
                    new ArtifactMetadata(ImmutableMap.of(ArtifactMetadataKeys.buildNo, "2.31.0-MASTER-SNAPSHOT")))
                .build()));

    ContextElementParamMapperFactory contextElementParamMapperFactory =
        spy(injector.getInstance(ContextElementParamMapperFactory.class));
    when(contextElementParamMapperFactory.getParamMapper(workflowStandardParams))
        .thenReturn(workflowStandardParamsMapper);
    on(context).set("contextElementParamMapperFactory", contextElementParamMapperFactory);

    ExecutionResponse response = httpState.execute(context);

    assertThat(response).isNotNull().extracting(ExecutionResponse::isAsync).isEqualTo(true);

    response = asyncExecutionResponse;

    assertThat(response.getStateExecutionData())
        .isNotNull()
        .isInstanceOf(HttpStateExecutionData.class)
        .isEqualToComparingOnlyGivenFields(
            HttpStateExecutionData.builder()
                .assertionStatus("SUCCESS")
                .httpResponseCode(200)
                .httpResponseBody(
                    "{\"status\":{\"code\":\"SUCCESS\"},\"data\":{\"title\":\"Some server\",\"version\":\"2.31.0-MASTER-SNAPSHOT\",\"buildTimestamp\":1506086747259}}")
                .build(),
            "httpUrl", "assertionStatus", "httpResponseCode", "httpResponseBody");

    verify(activityHelperService).createAndSaveActivity(any(), any(), any(), any(), any());
    verify(activityHelperService).updateStatus(ACTIVITY_ID, APP_ID, ExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldExecuteAndEvaluateJsonResponseWithVariables() throws IllegalAccessException {
    wireMockRule.stubFor(
        get(urlEqualTo("/health/status"))
            .withHeader("Content-Type", equalTo("application/json"))
            .withHeader("Accept", equalTo("*/*"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/json")
                    .withBody(
                        "{\"status\":{\"code\":\"SUCCESS\"},\"data\":{\"title\":\"Some server\",\"version\":\"2.31.0-MASTER-SNAPSHOT\",\"buildTimestamp\":1506086747259}}")));

    Map<String, Object> map = ImmutableMap.of(ARTIFACT,
        anArtifact()
            .withMetadata(new ArtifactMetadata(ImmutableMap.of(ArtifactMetadataKeys.buildNo, "2.31.0-MASTER-SNAPSHOT")))
            .build());
    //    when(workflowStandardParams.paramMap(context)).thenReturn(map);
    List<Variable> templateVariables = asList(aVariable().name("url").value("localhost:8088/health/status").build(),
        aVariable().name("buildNo").value("2.31.0-MASTER-SNAPSHOT").build(),
        aVariable().name("contentType").value("application/json").build());
    Map<String, Object> variableMap = new HashMap<>();
    variableMap.put("url", "localhost:8088/health/status");
    variableMap.put("buildNo", "2.31.0-MASTER-SNAPSHOT");
    variableMap.put("contentType", "application/json");
    when(templateUtils.processTemplateVariables(context, templateVariables)).thenReturn(variableMap);
    HttpState.Builder jsonHttpStateBuilder =
        aHttpState()
            .withName("healthCheck1")
            .withMethod("GET")
            .withUrl("http://${url}")
            .withHeaders(Lists.newArrayList(KeyValuePair.builder().key("Content-Type").value("${contentType}").build(),
                KeyValuePair.builder().key("Accept").value("*/*").build()))
            .withAssertion("${httpResponseCode}==200 && ${jsonpath(\"data.version\")}==${buildNo}")
            .withTemplateVariables(asList(aVariable().name("url").value("localhost:8088/health/status").build(),
                aVariable().name("buildNo").value("2.31.0-MASTER-SNAPSHOT").build(),
                aVariable().name("contentType").value("application/json").build()));

    HttpState httpState = getHttpState(jsonHttpStateBuilder.but(), context);
    FieldUtils.writeField(httpState, "stateExecutionService", stateExecutionService, true);

    ExecutionResponse response = httpState.execute(context);

    assertThat(response).isNotNull().extracting(ExecutionResponse::isAsync).isEqualTo(true);

    response = asyncExecutionResponse;

    assertThat(response.getStateExecutionData())
        .isNotNull()
        .isInstanceOf(HttpStateExecutionData.class)
        .isEqualToComparingOnlyGivenFields(
            HttpStateExecutionData.builder()
                .assertionStatus("SUCCESS")
                .httpResponseCode(200)
                .httpResponseBody(
                    "{\"status\":{\"code\":\"SUCCESS\"},\"data\":{\"title\":\"Some server\",\"version\":\"2.31.0-MASTER-SNAPSHOT\",\"buildTimestamp\":1506086747259}}")
                .build(),
            "httpUrl", "assertionStatus", "httpResponseCode", "httpResponseBody");

    verify(activityHelperService).createAndSaveActivity(any(), any(), any(), any(), any());
    verify(activityHelperService).updateStatus(ACTIVITY_ID, APP_ID, ExecutionStatus.SUCCESS);
  }
  /**
   * Should execute and evaluate response.
   */
  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldExecuteAndEvaluateResponse() throws IllegalAccessException {
    wireMockRule.stubFor(get(urlEqualTo("/health/status"))
                             .withHeader("Content-Type", equalTo("application/xml"))
                             .withHeader("Accept", equalTo("*/*"))
                             .willReturn(aResponse()
                                             .withStatus(200)
                                             .withBody("<health><status>Enabled</status></health>")
                                             .withHeader("Content-Type", "text/xml")));

    HttpState httpState = getHttpState(httpStateBuilder.but(), context);
    FieldUtils.writeField(httpState, "stateExecutionService", stateExecutionService, true);
    ExecutionResponse response = httpState.execute(context);

    assertThat(response).isNotNull().extracting(ExecutionResponse::isAsync).isEqualTo(true);

    response = asyncExecutionResponse;

    assertThat(response.getStateExecutionData())
        .isNotNull()
        .isInstanceOf(HttpStateExecutionData.class)
        .isEqualToComparingOnlyGivenFields(HttpStateExecutionData.builder()
                                               .assertionStatus("SUCCESS")
                                               .httpResponseCode(200)
                                               .httpResponseBody("<health><status>Enabled</status></health>")
                                               .build(),
            "httpUrl", "assertionStatus", "httpResponseCode", "httpResponseBody");

    verify(activityHelperService).createAndSaveActivity(any(), any(), any(), any(), any());
    verify(activityHelperService).updateStatus(ACTIVITY_ID, APP_ID, ExecutionStatus.SUCCESS);
    verify(stateExecutionService).appendDelegateTaskDetails(anyString(), any(DelegateTaskDetails.class));
  }

  /**
   * Should execute and evaluate response with certificate validation.
   */
  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void shouldExecuteAndEvaluateResponseWithCertValidation() throws IllegalAccessException {
    when(accountService.isCertValidationRequired(any())).thenReturn(true);

    wireMockRule.stubFor(get(urlEqualTo("/health/status"))
                             .withHeader("Content-Type", equalTo("application/xml"))
                             .withHeader("Accept", equalTo("*/*"))
                             .willReturn(aResponse()
                                             .withStatus(200)
                                             .withBody("<health><status>Enabled</status></health>")
                                             .withHeader("Content-Type", "text/xml")));

    HttpState httpState = getHttpState(httpStateBuilder.but(), context);
    FieldUtils.writeField(httpState, "stateExecutionService", stateExecutionService, true);
    ExecutionResponse response = httpState.execute(context);

    assertThat(response).isNotNull().extracting(ExecutionResponse::isAsync).isEqualTo(true);

    response = asyncExecutionResponse;

    assertThat(response.getStateExecutionData())
        .isNotNull()
        .isInstanceOf(HttpStateExecutionData.class)
        .isEqualToComparingOnlyGivenFields(HttpStateExecutionData.builder()
                                               .assertionStatus("SUCCESS")
                                               .httpResponseCode(200)
                                               .httpResponseBody("<health><status>Enabled</status></health>")
                                               .build(),
            "httpUrl", "assertionStatus", "httpResponseCode", "httpResponseBody");

    verify(activityHelperService).createAndSaveActivity(any(), any(), any(), any(), any());
    verify(activityHelperService).updateStatus(ACTIVITY_ID, APP_ID, ExecutionStatus.SUCCESS);
    verify(stateExecutionService).appendDelegateTaskDetails(anyString(), any(DelegateTaskDetails.class));
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testSelectionLogsTrackingForTasksEnabled() {
    assertThat(httpStateBuilder.build().isSelectionLogsTrackingForTasksEnabled()).isTrue();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldExecuteAndEvaluateResponseWithProxy() throws IllegalAccessException {
    wireMockRule.stubFor(get(urlEqualTo("/health/status"))
                             .withHeader("Content-Type", equalTo("application/xml"))
                             .withHeader("Accept", equalTo("*/*"))
                             .willReturn(aResponse()
                                             .withStatus(200)
                                             .withBody("<health><status>Enabled</status></health>")
                                             .withHeader("Content-Type", "text/xml")));

    HttpState.Builder proxyHttpBuilder =
        aHttpState()
            .withName("healthCheck1")
            .withMethod("GET")
            .withUrl("http://${host.hostName}:8088/health/status")
            .withHeaders(Lists.newArrayList(KeyValuePair.builder().key("Content-Type").value("application/xml").build(),
                KeyValuePair.builder().key("Accept").value("*/*").build()))
            .usesProxy(true)
            .withAssertion(
                "(${httpResponseCode}==200 || ${httpResponseCode}==201) && ${xmlFormat()} && ${xpath('//health/status/text()')}.equals('Enabled')");

    HttpState httpState = getHttpState(proxyHttpBuilder.but(), context);
    FieldUtils.writeField(httpState, "stateExecutionService", stateExecutionService, true);

    ExecutionResponse response = httpState.execute(context);

    assertThat(response).isNotNull().extracting(ExecutionResponse::isAsync).isEqualTo(true);

    assertThat(((HttpStateExecutionData) response.getStateExecutionData()).isUseProxy()).isTrue();

    response = asyncExecutionResponse;

    assertThat(response.getStateExecutionData())
        .isNotNull()
        .isInstanceOf(HttpStateExecutionData.class)
        .isEqualToComparingOnlyGivenFields(HttpStateExecutionData.builder()
                                               .assertionStatus("SUCCESS")
                                               .httpResponseCode(200)
                                               .httpResponseBody("<health><status>Enabled</status></health>")
                                               .build(),
            "httpUrl", "assertionStatus", "httpResponseCode", "httpResponseBody");

    verify(activityHelperService).createAndSaveActivity(any(), any(), any(), any(), any());
    verify(activityHelperService).updateStatus(ACTIVITY_ID, APP_ID, ExecutionStatus.SUCCESS);
  }

  /**
   * Should execute and evaluate response.
   */
  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldExecuteAndEvaluateResponseWithVariables() throws IllegalAccessException {
    wireMockRule.stubFor(get(urlEqualTo("/health/status"))
                             .withHeader("Content-Type", equalTo("application/xml"))
                             .withHeader("Accept", equalTo("*/*"))
                             .willReturn(aResponse()
                                             .withStatus(200)
                                             .withBody("<health><status>Enabled</status></health>")
                                             .withHeader("Content-Type", "text/xml")));

    httpStateBuilder.withTemplateVariables(asList(aVariable().name("status").value("Enabled").build()));
    HttpState httpState = getHttpState(httpStateBuilder.but(), context);
    FieldUtils.writeField(httpState, "stateExecutionService", stateExecutionService, true);
    ExecutionResponse response = httpState.execute(context);

    assertThat(response).isNotNull().extracting(ExecutionResponse::isAsync).isEqualTo(true);

    response = asyncExecutionResponse;

    assertThat(response.getStateExecutionData())
        .isNotNull()
        .isInstanceOf(HttpStateExecutionData.class)
        .isEqualToComparingOnlyGivenFields(HttpStateExecutionData.builder()
                                               .assertionStatus("SUCCESS")
                                               .httpResponseCode(200)
                                               .httpResponseBody("<health><status>Enabled</status></health>")
                                               .build(),
            "httpUrl", "assertionStatus", "httpResponseCode", "httpResponseBody");

    verify(activityHelperService).createAndSaveActivity(any(), any(), any(), any(), any());
    verify(activityHelperService).updateStatus(ACTIVITY_ID, APP_ID, ExecutionStatus.SUCCESS);
  }

  /**
   * Should execute and evaluate response.
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldExecuteAndEvaluateResponseWithInstance() throws IllegalAccessException {
    wireMockRule.stubFor(get(urlEqualTo("/health/status"))
                             .withHeader("Content-Type", equalTo("application/xml"))
                             .withHeader("Accept", equalTo("*/*"))
                             .willReturn(aResponse()
                                             .withStatus(200)
                                             .withBody("<health><status>Enabled</status></health>")
                                             .withHeader("Content-Type", "text/xml")));

    context.pushContextElement(
        anInstanceElement()
            .host(HostElement.builder().hostName("localhost").build())
            .serviceTemplateElement(
                aServiceTemplateElement()
                    .withName(TEMPLATE_NAME)
                    .withUuid(TEMPLATE_ID)
                    .withServiceElement(ServiceElement.builder().name(SERVICE_NAME).uuid(SERVICE_ID).build())
                    .build())
            .uuid(SERVICE_INSTANCE_ID)
            .build());

    HttpState httpState = getHttpState(httpStateBuilder.withUrl("http://localhost:8088/health/status").but(), context);
    FieldUtils.writeField(httpState, "stateExecutionService", stateExecutionService, true);

    ExecutionResponse response = httpState.execute(context);

    assertThat(response).isNotNull().extracting(ExecutionResponse::isAsync).isEqualTo(true);

    response = asyncExecutionResponse;
    assertThat(response).isNotNull().extracting(ExecutionResponse::isAsync).isEqualTo(false);
    assertThat(response.getStateExecutionData())
        .isNotNull()
        .isInstanceOf(HttpStateExecutionData.class)
        .isEqualToComparingOnlyGivenFields(HttpStateExecutionData.builder()
                                               .assertionStatus("SUCCESS")
                                               .httpResponseCode(200)
                                               .httpResponseBody("<health><status>Enabled</status></health>")
                                               .build(),
            "httpUrl", "assertionStatus", "httpResponseCode", "httpResponseBody");

    verify(activityHelperService).createAndSaveActivity(any(), any(), any(), any(), any());
    verify(activityHelperService).updateStatus(ACTIVITY_ID, APP_ID, ExecutionStatus.SUCCESS);
  }

  /**
   * Should execute and get summary/details.
   */
  @Test
  @Owner(developers = DINESH)
  @Category(UnitTests.class)
  public void shouldGetExecutionDataSummaryDetails() throws IllegalAccessException {
    wireMockRule.stubFor(get(urlEqualTo("/health/status"))
                             .withHeader("Content-Type", equalTo("application/xml"))
                             .withHeader("Accept", equalTo("*/*"))
                             .willReturn(aResponse()
                                             .withStatus(200)
                                             .withBody("<health><status>Enabled</status></health>")
                                             .withHeader("Content-Type", "text/xml")));

    HttpState httpState = getHttpState(httpStateBuilder.but(), context);
    FieldUtils.writeField(httpState, "stateExecutionService", stateExecutionService, true);

    ExecutionResponse response = httpState.execute(context);

    assertThat(response).isNotNull().extracting(ExecutionResponse::isAsync).isEqualTo(true);

    response = asyncExecutionResponse;
    assertThat(response.getStateExecutionData()).isNotNull().isInstanceOf(HttpStateExecutionData.class);
    response.getStateExecutionData().setStatus(ExecutionStatus.SUCCESS);
    assertThat(response.getStateExecutionData().getExecutionSummary()).isNotNull();
    assertThat(response.getStateExecutionData().getExecutionDetails()).isNotNull();

    verify(activityHelperService).createAndSaveActivity(any(), any(), any(), any(), any());
    verify(activityHelperService).updateStatus(ACTIVITY_ID, APP_ID, ExecutionStatus.SUCCESS);
  }

  /**
   * Should fail on socket timeout.
   */
  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldFailOnSocketTimeout() throws IllegalAccessException {
    wireMockRule.stubFor(get(urlEqualTo("/health/status"))
                             .withHeader("Content-Type", equalTo("application/xml"))
                             .withHeader("Accept", equalTo("*/*"))
                             .willReturn(aResponse()
                                             .withStatus(200)
                                             .withBody("<health><status>Enabled</status></health>")
                                             .withHeader("Content-Type", "text/xml")
                                             .withFixedDelay(2000)));

    HttpState httpState = getHttpState(httpStateBuilder.but().withSocketTimeoutMillis(1000), context);
    FieldUtils.writeField(httpState, "stateExecutionService", stateExecutionService, true);

    ExecutionResponse response = httpState.execute(context);
    assertThat(response).isNotNull().extracting(ExecutionResponse::isAsync).isEqualTo(true);

    response = asyncExecutionResponse;
    assertThat(response.getStateExecutionData())
        .isNotNull()
        .isInstanceOf(HttpStateExecutionData.class)
        .isEqualToComparingOnlyGivenFields(HttpStateExecutionData.builder()
                                               .assertionStatus("FAILED")
                                               .httpResponseCode(500)
                                               .httpResponseBody("SocketTimeoutException: Read timed out")
                                               .build(),
            "httpUrl", "assertionStatus", "httpResponseCode", "httpResponseBody");
    assertThat(response.getFailureTypes()).containsOnly(FailureType.TIMEOUT_ERROR);
    verify(activityHelperService).createAndSaveActivity(any(), any(), any(), any(), any());
    verify(activityHelperService).updateStatus(ACTIVITY_ID, APP_ID, ExecutionStatus.FAILED);
  }

  /**
   * Should fail on empty response.
   */
  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldFailOnEmptyResponse() throws IllegalAccessException {
    wireMockRule.stubFor(get(urlEqualTo("/health/status"))
                             .withHeader("Content-Type", equalTo("application/xml"))
                             .withHeader("Accept", equalTo("*/*"))
                             .willReturn(aResponse().withStatus(200).withFault(Fault.EMPTY_RESPONSE)));

    HttpState httpState = getHttpState(httpStateBuilder.but(), context);
    FieldUtils.writeField(httpState, "stateExecutionService", stateExecutionService, true);

    ExecutionResponse response = httpState.execute(context);
    assertThat(response).isNotNull().extracting(ExecutionResponse::isAsync).isEqualTo(true);

    response = asyncExecutionResponse;
    assertThat(response.getStateExecutionData())
        .isNotNull()
        .isInstanceOf(HttpStateExecutionData.class)
        .isEqualToComparingOnlyGivenFields(
            HttpStateExecutionData.builder()
                .assertionStatus("FAILED")
                .httpResponseCode(500)
                .httpResponseBody("NoHttpResponseException: localhost:8088 failed to respond")
                .build(),
            "httpUrl", "assertionStatus", "httpResponseCode", "httpResponseBody");
    verify(activityHelperService).createAndSaveActivity(any(), any(), any(), any(), any());
    verify(activityHelperService).updateStatus(ACTIVITY_ID, APP_ID, ExecutionStatus.FAILED);
  }

  /**
   * Should fail on malformed response.
   */
  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldFailOnMalformedResponse() throws IllegalAccessException {
    wireMockRule.stubFor(get(urlEqualTo("/health/status"))
                             .withHeader("Content-Type", equalTo("application/xml"))
                             .withHeader("Accept", equalTo("*/*"))
                             .willReturn(aResponse().withStatus(200).withFault(Fault.MALFORMED_RESPONSE_CHUNK)));

    HttpState httpState = getHttpState(httpStateBuilder.but(), context);
    FieldUtils.writeField(httpState, "stateExecutionService", stateExecutionService, true);

    ExecutionResponse response = httpState.execute(context);
    assertThat(response).isNotNull().extracting(ExecutionResponse::isAsync).isEqualTo(true);

    response = asyncExecutionResponse;
    assertThat(response.getStateExecutionData())
        .isNotNull()
        .isInstanceOf(HttpStateExecutionData.class)
        .isEqualToComparingOnlyGivenFields(
            HttpStateExecutionData.builder()
                .assertionStatus("FAILED")
                .httpResponseCode(500)
                .httpResponseBody(
                    "MalformedChunkCodingException: Bad chunk header: lskdu018973t09sylgasjkfg1][]'./.sdlv")
                .build(),
            "httpUrl", "assertionStatus", "httpResponseCode", "httpResponseBody");
    verify(activityHelperService).createAndSaveActivity(any(), any(), any(), any(), any());
    verify(activityHelperService).updateStatus(ACTIVITY_ID, APP_ID, ExecutionStatus.FAILED);
  }

  /**
   * Should fail on random data.
   */
  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldFailOnRandomData() throws IllegalAccessException {
    wireMockRule.stubFor(get(urlEqualTo("/health/status"))
                             .withHeader("Content-Type", equalTo("application/xml"))
                             .withHeader("Accept", equalTo("*/*"))
                             .willReturn(aResponse().withStatus(200).withFault(Fault.RANDOM_DATA_THEN_CLOSE)));

    HttpState httpState = getHttpState(httpStateBuilder.but(), context);
    FieldUtils.writeField(httpState, "stateExecutionService", stateExecutionService, true);

    ExecutionResponse response = httpState.execute(context);
    assertThat(response).isNotNull().extracting(ExecutionResponse::isAsync).isEqualTo(true);

    response = asyncExecutionResponse;
    assertThat(response.getStateExecutionData())
        .isNotNull()
        .isInstanceOf(HttpStateExecutionData.class)
        .isEqualToComparingOnlyGivenFields(HttpStateExecutionData.builder()
                                               .assertionStatus("FAILED")
                                               .httpResponseCode(500)
                                               .httpResponseBody("ClientProtocolException: ")
                                               .build(),
            "httpUrl", "assertionStatus", "httpResponseCode", "httpResponseBody");
    verify(activityHelperService).createAndSaveActivity(any(), any(), any(), any(), any());
    verify(activityHelperService).updateStatus(ACTIVITY_ID, APP_ID, ExecutionStatus.FAILED);
  }

  /**
   * Should fail on connect timeout.
   */
  @Test
  @Owner(developers = DINESH)
  @Category(UnitTests.class)
  public void shouldFailOnConnectTimeout() throws IllegalAccessException {
    context.pushContextElement(HostElement.builder().hostName("www.google.com").build());

    HttpState httpState =
        getHttpState(httpStateBuilder.but().withUrl("http://${host.hostName}:81/health/status"), context);
    FieldUtils.writeField(httpState, "stateExecutionService", stateExecutionService, true);

    ExecutionResponse response = httpState.execute(context);
    assertThat(response).isNotNull().extracting(ExecutionResponse::isAsync).isEqualTo(true);

    response = asyncExecutionResponse;
    assertThat(response.getStateExecutionData())
        .isNotNull()
        .isInstanceOf(HttpStateExecutionData.class)
        .isEqualToComparingOnlyGivenFields(
            HttpStateExecutionData.builder().assertionStatus("FAILED").httpResponseCode(500).build(), "httpUrl",
            "assertionStatus", "httpResponseCode");
    assertThat(response.getFailureTypes()).containsOnly(FailureType.TIMEOUT_ERROR);
    assertThat(((HttpStateExecutionData) response.getStateExecutionData()).getHttpResponseBody())
        .contains("Connect to www.google.com:81 ");
    verify(activityHelperService).createAndSaveActivity(any(), any(), any(), any(), any());
    verify(activityHelperService).updateStatus(ACTIVITY_ID, APP_ID, ExecutionStatus.FAILED);
  }

  private HttpState getHttpState(HttpState.Builder builder, ExecutionContext context) {
    HttpState httpState = builder.build();
    on(httpState).set("activityHelperService", activityHelperService);
    on(httpState).set("delegateService", delegateService);
    on(httpState).set("templateUtils", templateUtils);
    on(httpState).set("accountService", accountService);
    on(httpState).set("infrastructureMappingService", infrastructureMappingService);
    on(httpState).set("featureFlagService", featureFlagService);
    on(httpState).set("workflowStandardParamsExtensionService", workflowStandardParamsExtensionService);
    on(httpState).set("delegateTaskMigrationHelper", delegateTaskMigrationHelper);

    WorkflowStandardParamsParamMapper workflowStandardParamsParamMapper =
        injector.getInstance(WorkflowStandardParamsParamMapper.class);
    on(workflowStandardParamsParamMapper)
        .set("workflowStandardParamsExtensionService", workflowStandardParamsExtensionService);
    on(workflowStandardParamsParamMapper).set("element", workflowStandardParams);
    ContextElementParamMapperFactory contextElementParamMapperFactory =
        spy(injector.getInstance(ContextElementParamMapperFactory.class));
    when(contextElementParamMapperFactory.getParamMapper(workflowStandardParams))
        .thenReturn(workflowStandardParamsParamMapper);

    on(context).set("workflowStandardParamsExtensionService", workflowStandardParamsExtensionService);
    on(context).set("contextElementParamMapperFactory", contextElementParamMapperFactory);

    doAnswer(invocation -> {
      DelegateTask task = invocation.getArgument(0, DelegateTask.class);

      DelegateRunnableTask delegateRunnableTask =
          new HttpTask(DelegateTaskPackage.builder().data(task.getData()).delegateId(DELEGATE_ID).build(), null,
              o
              -> asyncExecutionResponse =
                     httpState.handleAsyncResponse(context, ImmutableMap.of(task.getWaitId(), o.getResponse())),
              () -> true);
      on(delegateRunnableTask).set("httpService", httpService);
      delegateRunnableTask.run();

      return null;
    })
        .when(delegateService)
        .queueTaskV2(any(DelegateTask.class));

    return httpState;
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void shouldFailOnSocketTimeoutWithEmptyAssertion() throws IllegalAccessException {
    wireMockRule.stubFor(get(urlEqualTo("/health/status"))
                             .withHeader("Content-Type", equalTo("application/xml"))
                             .withHeader("Accept", equalTo("*/*"))
                             .willReturn(aResponse().withFixedDelay(2000)));

    HttpState httpState = getHttpState(httpStateBuilder.but().withSocketTimeoutMillis(1000), context);
    httpState.setAssertion("");
    FieldUtils.writeField(httpState, "stateExecutionService", stateExecutionService, true);

    ExecutionResponse response = httpState.execute(context);
    assertThat(response).isNotNull().extracting(ExecutionResponse::isAsync).isEqualTo(true);

    response = asyncExecutionResponse;
    assertThat(response.getStateExecutionData())
        .isNotNull()
        .isInstanceOf(HttpStateExecutionData.class)
        .isEqualToComparingOnlyGivenFields(HttpStateExecutionData.builder()
                                               .assertionStatus("FAILED")
                                               .httpResponseCode(500)
                                               .httpResponseBody("SocketTimeoutException: Read timed out")
                                               .build(),
            "httpUrl", "assertionStatus", "httpResponseCode", "httpResponseBody");
    assertThat(response.getFailureTypes()).containsOnly(FailureType.TIMEOUT_ERROR);
    verify(activityHelperService).createAndSaveActivity(any(), any(), any(), any(), any());
    verify(activityHelperService).updateStatus(ACTIVITY_ID, APP_ID, ExecutionStatus.FAILED);
  }
}
