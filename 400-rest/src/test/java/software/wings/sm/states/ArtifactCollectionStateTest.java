/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.ExecutionStatus.FAILED;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.beans.FeatureName.ARTIFACT_COLLECTION_CONFIGURABLE;
import static io.harness.beans.FeatureName.SAVE_ARTIFACT_TO_DB;
import static io.harness.beans.FeatureName.SPG_FETCH_ARTIFACT_FROM_DB;
import static io.harness.beans.OrchestrationWorkflowType.BUILD;
import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.FERNANDOD;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.LUCAS_SALES;
import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.rule.OwnerRule.PUNEET;
import static io.harness.rule.OwnerRule.SRINIVAS;

import static software.wings.helpers.ext.jenkins.BuildDetails.Builder.aBuildDetails;
import static software.wings.persistence.artifact.Artifact.Builder.anArtifact;
import static software.wings.sm.StateExecutionInstance.Builder.aStateExecutionInstance;
import static software.wings.sm.StateType.ARTIFACT_COLLECTION;
import static software.wings.sm.states.ArtifactCollectionState.DEFAULT_ARTIFACT_COLLECTION_STATE_TIMEOUT_MILLIS;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_SOURCE_NAME;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.DELEGATE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SETTING_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ArtifactMetadata;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.delay.DelayEventHelper;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import software.wings.api.ArtifactCollectionExecutionData;
import software.wings.api.ContextElementParamMapperFactory;
import software.wings.api.WorkflowElement;
import software.wings.app.MainConfiguration;
import software.wings.app.PortalConfig;
import software.wings.beans.Application;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TemplateExpression;
import software.wings.beans.artifact.ArtifactMetadataKeys;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.CustomArtifactStream;
import software.wings.beans.artifact.JenkinsArtifactStream;
import software.wings.beans.artifact.NexusArtifactStream;
import software.wings.common.TemplateExpressionProcessor;
import software.wings.common.VariableProcessor;
import software.wings.delegatetasks.buildsource.BuildSourceExecutionResponse;
import software.wings.delegatetasks.buildsource.BuildSourceParameters;
import software.wings.delegatetasks.buildsource.BuildSourceResponse;
import software.wings.expression.ManagerExpressionEvaluator;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.helpers.ext.url.SubdomainUrlHelperIntfc;
import software.wings.persistence.artifact.Artifact;
import software.wings.persistence.artifact.Artifact.Status;
import software.wings.service.ArtifactStreamHelper;
import software.wings.service.impl.artifact.ArtifactCollectionUtils;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.BuildSourceService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.StateType;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParams.Builder;
import software.wings.sm.WorkflowStandardParamsExtensionService;
import software.wings.sm.states.ArtifactCollectionState.ArtifactCollectionStateKeys;

import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class ArtifactCollectionStateTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock private ArtifactStreamService artifactStreamService;
  @Mock private ArtifactService artifactService;
  @Mock private WorkflowExecutionService workflowExecutionService;
  @Mock private AppService appService;
  @Mock MainConfiguration configuration;
  @Mock PortalConfig portalConfig;
  @Mock VariableProcessor variableProcessor;
  @Mock DelayEventHelper delayEventHelper;
  @Mock AccountService accountService;
  @Mock FeatureFlagService featureFlagService;
  @Mock private SubdomainUrlHelperIntfc subdomainUrlHelper;
  @Mock private ArtifactStreamHelper artifactStreamHelper;
  @Mock private BuildSourceService buildSourceService;
  @Mock private ArtifactCollectionUtils artifactCollectionUtils;
  @Mock private TemplateExpressionProcessor templateExpressionProcessor;
  @Mock private SettingsService settingsService;
  @Mock private DelegateService delegateService;

  private ManagerExpressionEvaluator expressionEvaluator = new ManagerExpressionEvaluator();

  @InjectMocks
  private ArtifactCollectionState artifactCollectionState = new ArtifactCollectionState("Collect Artifact");

  private JenkinsArtifactStream jenkinsArtifactStream = JenkinsArtifactStream.builder()
                                                            .appId(APP_ID)
                                                            .uuid(ARTIFACT_STREAM_ID)
                                                            .sourceName(ARTIFACT_SOURCE_NAME)
                                                            .settingId(SETTING_ID)
                                                            .jobname("JOB")
                                                            .serviceId(SERVICE_ID)
                                                            .artifactPaths(asList("*WAR"))
                                                            .build();
  private CustomArtifactStream customArtifactStream =
      CustomArtifactStream.builder()
          .appId(APP_ID)
          .uuid(ARTIFACT_STREAM_ID)
          .scripts(asList(CustomArtifactStream.Script.builder().scriptString("echo hi").build()))
          .sourceName(ARTIFACT_SOURCE_NAME)
          .settingId(SETTING_ID)
          .serviceId(SERVICE_ID)
          .build();
  private NexusArtifactStream nexusArtifactStream = NexusArtifactStream.builder()
                                                        .accountId(ACCOUNT_ID)
                                                        .appId(APP_ID)
                                                        .settingId(SETTING_ID)
                                                        .jobname("${repo}")
                                                        .groupId("${groupId}")
                                                        .artifactPaths(asList("${path}"))
                                                        .autoPopulate(false)
                                                        .serviceId(SERVICE_ID)
                                                        .name("testNexus")
                                                        .uuid(ARTIFACT_STREAM_ID)
                                                        .sourceName(ARTIFACT_SOURCE_NAME)
                                                        .build();

  private WorkflowStandardParams workflowStandardParams =
      Builder.aWorkflowStandardParams()
          .withAppId(APP_ID)
          .withWorkflowElement(WorkflowElement.builder()
                                   .variables(ImmutableMap.of("sourceCommitHash",
                                       "0fcb2caa537745f8228fb081aac2af55765d8e62", "buildNo", "1.1"))
                                   .build())
          .build();
  private StateExecutionInstance stateExecutionInstance = aStateExecutionInstance()
                                                              .displayName(StateType.ARTIFACT_COLLECTION.name())
                                                              .orchestrationWorkflowType(BUILD)
                                                              .addContextElement(workflowStandardParams)
                                                              .build();

  private ExecutionContextImpl executionContext = new ExecutionContextImpl(stateExecutionInstance);

  @Before
  public void setUp() throws Exception {
    artifactCollectionState.setArtifactStreamId(ARTIFACT_STREAM_ID);
    FieldUtils.writeField(executionContext, "variableProcessor", variableProcessor, true);
    FieldUtils.writeField(executionContext, "evaluator", expressionEvaluator, true);
    FieldUtils.writeField(executionContext, "featureFlagService", featureFlagService, true);

    WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService =
        new WorkflowStandardParamsExtensionService(
            appService, accountService, artifactService, null, null, null, featureFlagService);
    ContextElementParamMapperFactory contextElementParamMapperFactory = new ContextElementParamMapperFactory(
        subdomainUrlHelper, workflowExecutionService, artifactService, artifactStreamService, null, featureFlagService,
        buildSourceService, workflowStandardParamsExtensionService);
    on(executionContext).set("workflowStandardParamsExtensionService", workflowStandardParamsExtensionService);
    on(executionContext).set("contextElementParamMapperFactory", contextElementParamMapperFactory);

    when(configuration.getPortal()).thenReturn(portalConfig);
    when(portalConfig.getUrl()).thenReturn("http://portalUrl");
    when(appService.get(APP_ID))
        .thenReturn(Application.Builder.anApplication().appId(APP_ID).uuid(APP_ID).accountId(ACCOUNT_ID).build());
    when(appService.getApplicationWithDefaults(APP_ID))
        .thenReturn(Application.Builder.anApplication().appId(APP_ID).uuid(APP_ID).accountId(ACCOUNT_ID).build());
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(jenkinsArtifactStream);
    when(artifactService.fetchLastCollectedApprovedArtifactForArtifactStream(jenkinsArtifactStream))
        .thenReturn(anArtifact().withAppId(APP_ID).withStatus(Status.APPROVED).build());
    when(delayEventHelper.delay(anyInt(), any())).thenReturn("anyGUID");
    when(subdomainUrlHelper.getPortalBaseUrl(any())).thenReturn("baseUrl");
    nexusArtifactStream.setArtifactStreamParameterized(true);
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void shouldFetchArtifacSourcetBySourceVariableValue() {
    doReturn(null).when(artifactStreamService).get(ARTIFACT_STREAM_ID);
    doReturn(jenkinsArtifactStream)
        .when(artifactStreamService)
        .fetchByArtifactSourceVariableValue(APP_ID, ARTIFACT_STREAM_ID);
    ExecutionResponse executionResponse = artifactCollectionState.execute(executionContext);
    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(SUCCESS);
    verify(artifactStreamService).get(ARTIFACT_STREAM_ID);
    verify(artifactStreamService).fetchByArtifactSourceVariableValue(APP_ID, ARTIFACT_STREAM_ID);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldFailOnNoArtifactStream() {
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(null);
    ExecutionResponse executionResponse = artifactCollectionState.execute(executionContext);
    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
    verify(artifactStreamService).get(ARTIFACT_STREAM_ID);
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void shouldExecuteWithDelayQueue() {
    when(artifactService.fetchLastCollectedApprovedArtifactForArtifactStream(jenkinsArtifactStream)).thenReturn(null);
    ExecutionResponse executionResponse = artifactCollectionState.execute(executionContext);
    assertThat(executionResponse).isNotNull().hasFieldOrPropertyWithValue("async", true);
    verify(artifactStreamService).get(ARTIFACT_STREAM_ID);
    verify(delayEventHelper).delay(anyLong(), any());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldHandleAsyncResponse() {
    artifactCollectionState.handleAsyncResponse(executionContext,
        ImmutableMap.of(
            ACTIVITY_ID, ArtifactCollectionExecutionData.builder().artifactStreamId(ARTIFACT_STREAM_ID).build()));
    verify(workflowExecutionService).refreshBuildExecutionSummary(any(), any());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldArtifactCollectionEvaluateBuildNo() {
    artifactCollectionState.setBuildNo("${regex.extract('...', ${workflow.variables.sourceCommitHash})}");
    when(artifactService.getArtifactByBuildNumber(jenkinsArtifactStream, "0fc", false))
        .thenReturn(anArtifact().withAppId(APP_ID).withStatus(Status.APPROVED).build());
    artifactCollectionState.handleAsyncResponse(executionContext,
        ImmutableMap.of(
            ACTIVITY_ID, ArtifactCollectionExecutionData.builder().artifactStreamId(ARTIFACT_STREAM_ID).build()));
    verify(workflowExecutionService).refreshBuildExecutionSummary(any(), any());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  @Ignore("for srinivas to modify; need to simulate real Jenkins")
  public void shouldArtifactCollectionEvaluateBuildNoFromDescription() {
    artifactCollectionState.setBuildNo("${regex.replace('tag: ([\\w-]+)', '$1', ${Jenkins.description}}");
    when(artifactService.getArtifactByBuildNumber(jenkinsArtifactStream, "0fc", false))
        .thenReturn(anArtifact().withAppId(APP_ID).withStatus(Status.APPROVED).build());
    artifactCollectionState.handleAsyncResponse(executionContext,
        ImmutableMap.of(
            ACTIVITY_ID, ArtifactCollectionExecutionData.builder().artifactStreamId(ARTIFACT_STREAM_ID).build()));
    verify(workflowExecutionService).refreshBuildExecutionSummary(any(), any());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetTimeout() {
    Integer timeoutMillis = artifactCollectionState.getTimeoutMillis();
    assertThat(timeoutMillis).isEqualTo(Math.toIntExact(DEFAULT_ARTIFACT_COLLECTION_STATE_TIMEOUT_MILLIS));
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetSetTimeout() {
    artifactCollectionState.setTimeoutMillis((int) TimeUnit.HOURS.toMillis(1));
    Integer timeoutMillis = artifactCollectionState.getTimeoutMillis();
    assertThat(timeoutMillis).isEqualTo((int) TimeUnit.HOURS.toMillis(1));
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldHandleAbort() {
    executionContext.getStateExecutionInstance().setStateExecutionMap(
        ImmutableMap.of(ARTIFACT_COLLECTION.name(), ArtifactCollectionExecutionData.builder().build()));
    artifactCollectionState.handleAbortEvent(executionContext);
    assertThat(executionContext.getStateExecutionData()).isNotNull();

    // TODO: getErrorMsg returns null - is this expected
    // assertThat(executionContext.getStateExecutionData().getErrorMsg()).isNotBlank();
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldExecuteForParameterizedArtifactStreamWithLastCollectedArtifact() {
    Map<String, Object> runtimeValues = new HashMap<>();
    runtimeValues.put("repo", "harness-maven");
    runtimeValues.put("group", "mygroup");
    runtimeValues.put("artifactId", "todolist");
    artifactCollectionState.setRuntimeValues(runtimeValues);
    artifactCollectionState.setBuildNo("1.0");
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(nexusArtifactStream);
    when(artifactService.getArtifactByBuildNumberAndSourceName(
             nexusArtifactStream, "1.0", false, "${repo}/${groupId}/${path}"))
        .thenReturn(anArtifact().withAppId(APP_ID).withStatus(Status.APPROVED).build());
    artifactCollectionState.execute(executionContext);
    verify(artifactStreamService).get(ARTIFACT_STREAM_ID);
    verify(workflowExecutionService).refreshBuildExecutionSummary(any(), any());
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldExecuteForParameterizedArtifactStreamWithNewBuild() {
    Map<String, Object> runtimeValues = new HashMap<>();
    runtimeValues.put("repo", "harness-maven");
    runtimeValues.put("group", "mygroup");
    runtimeValues.put("artifactId", "todolist");
    artifactCollectionState.setRuntimeValues(runtimeValues);
    artifactCollectionState.setBuildNo("${workflow.variables.buildNo}");
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(nexusArtifactStream);
    when(artifactService.getArtifactByBuildNumberAndSourceName(
             nexusArtifactStream, "1.1", false, "${repo}/${groupId}/${path}"))
        .thenReturn(null);
    when(buildSourceService.getBuild(any(), any(), any(), any())).thenReturn(aBuildDetails().withNumber("1.1").build());
    Map<String, String> map = new HashMap<>();
    map.put("buildNo", "1.1");
    Artifact artifact =
        Artifact.Builder.anArtifact().withMetadata(new ArtifactMetadata(map)).withUuid(ARTIFACT_ID).build();
    when(artifactCollectionUtils.getArtifact(any(), any())).thenReturn(artifact);
    when(artifactService.create(artifact, nexusArtifactStream, false)).thenReturn(artifact);
    artifactCollectionState.execute(executionContext);
    verify(artifactStreamService).get(ARTIFACT_STREAM_ID);
    verify(workflowExecutionService).refreshBuildExecutionSummary(any(), any());
    assertThat(runtimeValues.get("buildNo")).isEqualTo("1.1");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldNotExecuteWithoutRuntimeValuesForParameterizedArtifactStream() {
    Map<String, Object> runtimeValues = new HashMap<>();
    artifactCollectionState.setRuntimeValues(runtimeValues);
    artifactCollectionState.setBuildNo("1.1");
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(nexusArtifactStream);
    ExecutionResponse executionResponse = artifactCollectionState.execute(executionContext);
    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldNotExecuteWithoutBuildNumberForParameterizedArtifactStream() {
    Map<String, Object> runtimeValues = new HashMap<>();
    runtimeValues.put("repo", "harness-maven");
    runtimeValues.put("group", "mygroup");
    runtimeValues.put("artifactId", "todolist");
    artifactCollectionState.setRuntimeValues(runtimeValues);
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(nexusArtifactStream);
    ExecutionResponse executionResponse = artifactCollectionState.execute(executionContext);
    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldNotExecuteIfArtifactCollectionFailsForParameterizedArtifactStream() {
    Map<String, Object> runtimeValues = new HashMap<>();
    runtimeValues.put("repo", "harness-maven");
    runtimeValues.put("group", "mygroup");
    runtimeValues.put("artifactId", "todolist");
    artifactCollectionState.setRuntimeValues(runtimeValues);
    artifactCollectionState.setBuildNo("1.1");
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(nexusArtifactStream);
    when(artifactService.getArtifactByBuildNumberAndSourceName(
             nexusArtifactStream, "1.1", false, "${repo}/${groupId}/${path}"))
        .thenReturn(null);
    when(buildSourceService.getBuild(any(), any(), any(), any())).thenReturn(null);
    ExecutionResponse executionResponse = artifactCollectionState.execute(executionContext);
    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldExecuteForTemplatizedArtifactStream() {
    List<TemplateExpression> templateExpressions =
        Collections.singletonList(TemplateExpression.builder()
                                      .fieldName(ArtifactCollectionStateKeys.artifactStreamId)
                                      .expression("${ArtifactStream}")
                                      .build());
    artifactCollectionState.setTemplateExpressions(templateExpressions);
    artifactCollectionState.setBuildNo("${workflow.variables.buildNo}");
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(nexusArtifactStream);

    nexusArtifactStream.setArtifactStreamParameterized(false);
    when(templateExpressionProcessor.getTemplateExpression(
             templateExpressions, ArtifactCollectionStateKeys.artifactStreamId))
        .thenReturn(templateExpressions.get(0));
    when(templateExpressionProcessor.resolveTemplateExpression(executionContext, templateExpressions.get(0)))
        .thenReturn(ARTIFACT_STREAM_ID);
    when(artifactService.fetchLastCollectedApprovedArtifactForArtifactStream(nexusArtifactStream))
        .thenReturn(anArtifact().build());
    Map<String, String> map = new HashMap<>();
    map.put("buildNo", "1.1");
    Artifact artifact =
        Artifact.Builder.anArtifact().withMetadata(new ArtifactMetadata(map)).withUuid(ARTIFACT_ID).build();
    when(artifactCollectionUtils.getArtifact(any(), any())).thenReturn(artifact);

    ExecutionResponse executionResponse = artifactCollectionState.execute(executionContext);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldExecuteForTemplatizedArtifactStreamByName() {
    List<TemplateExpression> templateExpressions =
        Collections.singletonList(TemplateExpression.builder()
                                      .fieldName(ArtifactCollectionStateKeys.artifactStreamId)
                                      .expression("${ArtifactStream}")
                                      .build());
    artifactCollectionState.setTemplateExpressions(templateExpressions);
    artifactCollectionState.setBuildNo("${workflow.variables.buildNo}");
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(nexusArtifactStream);

    when(templateExpressionProcessor.getTemplateExpression(
             templateExpressions, ArtifactCollectionStateKeys.artifactStreamId))
        .thenReturn(templateExpressions.get(0));
    when(templateExpressionProcessor.resolveTemplateExpression(executionContext, templateExpressions.get(0)))
        .thenReturn(ARTIFACT_SOURCE_NAME);
    when(artifactStreamService.fetchByArtifactSourceVariableValue(APP_ID, ARTIFACT_SOURCE_NAME))
        .thenReturn(nexusArtifactStream);

    when(artifactService.fetchLastCollectedApprovedArtifactForArtifactStream(nexusArtifactStream))
        .thenReturn(anArtifact().build());
    Map<String, String> map = new HashMap<>();
    map.put("buildNo", "1.1");
    Artifact artifact =
        Artifact.Builder.anArtifact().withMetadata(new ArtifactMetadata(map)).withUuid(ARTIFACT_ID).build();
    when(artifactCollectionUtils.getArtifact(any(), any())).thenReturn(artifact);

    ExecutionResponse executionResponse = artifactCollectionState.execute(executionContext);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
    assertThat(executionResponse.getErrorMessage())
        .isEqualTo(
            "Parameterized Artifact Source testNexus cannot be used as a value for templatized artifact variable");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testCollectJenkinsArtifactWithBuildNumFFEnabled() {
    String delegateTaskId = "delegateTaskId";
    when(featureFlagService.isEnabled(eq(ARTIFACT_COLLECTION_CONFIGURABLE), anyString())).thenReturn(true);
    artifactCollectionState.setBuildNo("1.0");
    when(settingsService.get(anyString())).thenReturn(SettingAttribute.Builder.aSettingAttribute().build());
    when(artifactCollectionUtils.getBuildSourceParameters(any(), any(), eq(false), eq(false)))
        .thenReturn(BuildSourceParameters.builder().build());
    when(delegateService.queueTaskV2(any())).thenReturn(delegateTaskId);

    ExecutionResponse executionResponse = artifactCollectionState.execute(executionContext);
    verify(settingsService).get(anyString());
    verify(artifactCollectionUtils).getBuildSourceParameters(any(), any(), eq(false), eq(false));
    verify(delegateService).queueTaskV2(any());
    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.isAsync()).isEqualTo(true);
    assertThat(executionResponse.getDelegateTaskId()).isEqualTo(delegateTaskId);
    assertThat(executionResponse.getStateExecutionData())
        .isNotNull()
        .isInstanceOf(ArtifactCollectionExecutionData.class);
    ArtifactCollectionExecutionData executionData =
        (ArtifactCollectionExecutionData) executionResponse.getStateExecutionData();
    assertThat(executionData.getBuildNo()).isEqualTo("1.0");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testCollectJenkinsArtifactWithEmptyBuildNumFFEnabled() {
    String delegateTaskId = "delegateTaskId";
    when(featureFlagService.isEnabled(eq(ARTIFACT_COLLECTION_CONFIGURABLE), anyString())).thenReturn(true);
    when(settingsService.get(anyString())).thenReturn(SettingAttribute.Builder.aSettingAttribute().build());
    when(artifactCollectionUtils.getBuildSourceParameters(any(), any(), eq(false), eq(false)))
        .thenReturn(BuildSourceParameters.builder().build());
    when(delegateService.queueTaskV2(any())).thenReturn(delegateTaskId);
    Artifact lastDeployedArtifact = anArtifact()
                                        .withAppId(APP_ID)
                                        .withStatus(Status.APPROVED)
                                        .withMetadata(new ArtifactMetadata(new HashMap<String, String>() {
                                          { put("buildNo", "1.0"); }
                                        }))
                                        .withArtifactSourceName(ARTIFACT_SOURCE_NAME)
                                        .build();
    when(artifactService.fetchLastCollectedApprovedArtifactForArtifactStream(jenkinsArtifactStream))
        .thenReturn(lastDeployedArtifact);

    ExecutionResponse executionResponse = artifactCollectionState.execute(executionContext);
    verify(settingsService, never()).get(anyString());
    verify(artifactCollectionUtils, never()).getBuildSourceParameters(any(), any(), eq(false), eq(false));
    verify(delegateService, never()).queueTaskV2(any());
    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.isAsync()).isEqualTo(false);
    assertThat(executionResponse.getErrorMessage()).isNotNull();
    assertThat(executionResponse.getErrorMessage())
        .isEqualTo("Collected artifact [" + lastDeployedArtifact.getBuildNo() + "] for artifact source ["
            + lastDeployedArtifact.getArtifactSourceName() + "]");
    assertThat(executionResponse.getStateExecutionData())
        .isNotNull()
        .isInstanceOf(ArtifactCollectionExecutionData.class);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testCollectCustomArtifactWithBuildNumFFEnabled() {
    String delegateTaskId = "delegateTaskId";
    when(featureFlagService.isEnabled(eq(ARTIFACT_COLLECTION_CONFIGURABLE), anyString())).thenReturn(true);
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(customArtifactStream);
    artifactCollectionState.setBuildNo("1.0");
    when(settingsService.get(anyString())).thenReturn(SettingAttribute.Builder.aSettingAttribute().build());
    when(artifactCollectionUtils.renderCustomArtifactScriptString(customArtifactStream))
        .thenReturn(ArtifactStreamAttributes.builder().build());
    when(artifactCollectionUtils.fetchCustomDelegateTask(
             anyString(), any(), any(), eq(false), eq(BuildSourceParameters.BuildSourceRequestType.GET_BUILD), any()))
        .thenReturn(DelegateTask.builder());
    when(delegateService.queueTaskV2(any())).thenReturn(delegateTaskId);

    ExecutionResponse executionResponse = artifactCollectionState.execute(executionContext);
    verify(settingsService, never()).get(anyString());
    verify(artifactCollectionUtils).renderCustomArtifactScriptString(customArtifactStream);
    verify(delegateService).queueTaskV2(any());
    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.isAsync()).isEqualTo(true);
    assertThat(executionResponse.getDelegateTaskId()).isEqualTo(delegateTaskId);
    assertThat(executionResponse.getStateExecutionData())
        .isNotNull()
        .isInstanceOf(ArtifactCollectionExecutionData.class);
    ArtifactCollectionExecutionData executionData =
        (ArtifactCollectionExecutionData) executionResponse.getStateExecutionData();
    assertThat(executionData.getBuildNo()).isEqualTo("1.0");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testCollectNexusArtifactWithBuildNumFFEnabled() {
    String delegateTaskId = "delegateTaskId";
    Map<String, Object> runtimeValues = new HashMap<>();
    runtimeValues.put("repo", "harness-maven");
    runtimeValues.put("group", "mygroup");
    runtimeValues.put("artifactId", "todolist");
    when(featureFlagService.isEnabled(eq(ARTIFACT_COLLECTION_CONFIGURABLE), anyString())).thenReturn(true);
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(nexusArtifactStream);
    artifactCollectionState.setBuildNo("1.0");
    artifactCollectionState.setRuntimeValues(runtimeValues);
    when(settingsService.get(anyString())).thenReturn(SettingAttribute.Builder.aSettingAttribute().build());
    when(artifactCollectionUtils.getBuildSourceParameters(any(), any(), eq(false), eq(false)))
        .thenReturn(BuildSourceParameters.builder().build());
    when(delegateService.queueTaskV2(any())).thenReturn(delegateTaskId);

    ExecutionResponse executionResponse = artifactCollectionState.execute(executionContext);
    verify(settingsService).get(anyString());
    verify(artifactCollectionUtils).getBuildSourceParameters(any(), any(), eq(false), eq(false));
    verify(delegateService).queueTaskV2(any());
    verify(artifactStreamHelper).resolveArtifactStreamRuntimeValues(any(), anyMap());
    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.isAsync()).isEqualTo(true);
    assertThat(executionResponse.getDelegateTaskId()).isEqualTo(delegateTaskId);
    assertThat(executionResponse.getStateExecutionData())
        .isNotNull()
        .isInstanceOf(ArtifactCollectionExecutionData.class);
    ArtifactCollectionExecutionData executionData =
        (ArtifactCollectionExecutionData) executionResponse.getStateExecutionData();
    assertThat(executionData.getBuildNo()).isEqualTo("1.0");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseWithSuccessfulJenkinsArtifactCollectionFFEnabled() {
    when(featureFlagService.isEnabled(eq(ARTIFACT_COLLECTION_CONFIGURABLE), anyString())).thenReturn(true);
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(jenkinsArtifactStream);
    artifactCollectionState.setBuildNo("1.0");
    BuildDetails buildDetails = aBuildDetails().build();
    BuildSourceExecutionResponse buildSourceExecutionResponse =
        BuildSourceExecutionResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .buildSourceResponse(BuildSourceResponse.builder().buildDetails(asList(buildDetails)).build())
            .build();
    Artifact artifact = anArtifact().build();
    when(artifactCollectionUtils.getArtifact(jenkinsArtifactStream, buildDetails)).thenReturn(artifact);
    when(artifactService.create(artifact, jenkinsArtifactStream, false)).thenReturn(artifact);

    ExecutionResponse executionResponse = artifactCollectionState.handleAsyncResponse(
        executionContext, Collections.singletonMap("response", buildSourceExecutionResponse));
    verify(artifactService).create(artifact, jenkinsArtifactStream, false);
    verify(workflowExecutionService).refreshBuildExecutionSummary(any(), any());
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(executionResponse.getStateExecutionData()).isNotNull();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseWithFailJenkinsArtifactCollectionFFEnabled() {
    when(featureFlagService.isEnabled(eq(ARTIFACT_COLLECTION_CONFIGURABLE), anyString())).thenReturn(true);
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(jenkinsArtifactStream);
    artifactCollectionState.setBuildNo("1.0");
    BuildDetails buildDetails = aBuildDetails().build();
    String errorMessage = "Artifact collection failed for xyz reason.";
    BuildSourceExecutionResponse buildSourceExecutionResponse =
        BuildSourceExecutionResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.FAILURE)
            .errorMessage(errorMessage)
            .build();
    Artifact artifact = anArtifact().build();
    when(artifactCollectionUtils.getArtifact(jenkinsArtifactStream, buildDetails)).thenReturn(artifact);
    when(artifactService.create(artifact, jenkinsArtifactStream, false)).thenReturn(artifact);

    ExecutionResponse executionResponse = artifactCollectionState.handleAsyncResponse(
        executionContext, Collections.singletonMap("response", buildSourceExecutionResponse));
    verify(artifactService, never()).create(artifact, jenkinsArtifactStream, false);
    verify(workflowExecutionService, never()).refreshBuildExecutionSummary(anyString(), any());
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(FAILED);
    assertThat(executionResponse.getErrorMessage()).isEqualTo(errorMessage);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseWithSuccessfulParameterisedArtifactCollectionFFEnabled() {
    when(featureFlagService.isEnabled(eq(ARTIFACT_COLLECTION_CONFIGURABLE), anyString())).thenReturn(true);
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(nexusArtifactStream);
    Map<String, Object> runtimeValues = new HashMap<>();
    runtimeValues.put("repo", "harness-maven");
    runtimeValues.put("group", "mygroup");
    runtimeValues.put("artifactId", "todolist");
    artifactCollectionState.setBuildNo("1.0");
    artifactCollectionState.setRuntimeValues(runtimeValues);
    BuildDetails buildDetails = aBuildDetails().build();
    BuildSourceExecutionResponse buildSourceExecutionResponse =
        BuildSourceExecutionResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .buildSourceResponse(BuildSourceResponse.builder().buildDetails(asList(buildDetails)).build())
            .build();
    Artifact artifact = anArtifact().build();
    when(artifactCollectionUtils.getArtifact(nexusArtifactStream, buildDetails)).thenReturn(artifact);
    when(artifactService.create(artifact, nexusArtifactStream, false)).thenReturn(artifact);

    ExecutionResponse executionResponse = artifactCollectionState.handleAsyncResponse(
        executionContext, Collections.singletonMap("response", buildSourceExecutionResponse));
    verify(artifactService).create(artifact, nexusArtifactStream, false);
    verify(workflowExecutionService).refreshBuildExecutionSummary(any(), any());
    verify(artifactStreamHelper).resolveArtifactStreamRuntimeValues(any(), anyMap());
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(executionResponse.getStateExecutionData()).isNotNull();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseWithSuccessfulDuplicateJenkinsArtifactCollectionFFEnabled() {
    when(featureFlagService.isEnabled(eq(ARTIFACT_COLLECTION_CONFIGURABLE), anyString())).thenReturn(true);
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(jenkinsArtifactStream);
    artifactCollectionState.setBuildNo("1.0");
    BuildDetails buildDetails = aBuildDetails().build();
    BuildSourceExecutionResponse buildSourceExecutionResponse =
        BuildSourceExecutionResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .buildSourceResponse(BuildSourceResponse.builder().buildDetails(asList(buildDetails)).build())
            .build();
    Artifact artifact = anArtifact().withRevision("1.0").build();
    Artifact savedArtifact = anArtifact().withRevision("2.0").build();
    savedArtifact.setDuplicate(true);
    when(artifactCollectionUtils.getArtifact(jenkinsArtifactStream, buildDetails)).thenReturn(artifact);
    when(artifactService.create(artifact, jenkinsArtifactStream, false)).thenReturn(savedArtifact);

    ExecutionResponse executionResponse = artifactCollectionState.handleAsyncResponse(
        executionContext, Collections.singletonMap("response", buildSourceExecutionResponse));
    verify(artifactService).create(artifact, jenkinsArtifactStream, false);
    verify(workflowExecutionService).refreshBuildExecutionSummary(any(), any());
    verify(artifactService).updateMetadataAndRevision(any(), any(), any(), any());
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(executionResponse.getStateExecutionData()).isNotNull();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldExecuteForCustomArtifactStream() {
    customArtifactStream.setArtifactStreamParameterized(false);
    customArtifactStream.setCollectionEnabled(false);
    customArtifactStream.setScripts(Collections.singletonList(CustomArtifactStream.Script.builder().build()));

    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(customArtifactStream);
    when(artifactService.fetchLastCollectedApprovedArtifactForArtifactStream(customArtifactStream))
        .thenReturn(anArtifact().build());

    when(featureFlagService.isEnabled(ARTIFACT_COLLECTION_CONFIGURABLE, ACCOUNT_ID)).thenReturn(true);
    Map<String, String> map = new HashMap<>();
    map.put("buildNo", "1.1");
    Artifact artifact =
        Artifact.Builder.anArtifact().withMetadata(new ArtifactMetadata(map)).withUuid(ARTIFACT_ID).build();
    when(artifactCollectionUtils.getArtifact(any(), any())).thenReturn(artifact);
    when(artifactService.create(artifact)).thenReturn(artifact);

    ExecutionResponse executionResponse = artifactCollectionState.execute(executionContext);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldExecuteForCustomArtifactWithScript() {
    customArtifactStream.setArtifactStreamParameterized(false);
    customArtifactStream.setCollectionEnabled(false);
    customArtifactStream.setScripts(
        Collections.singletonList(CustomArtifactStream.Script.builder().scriptString("script").build()));

    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(customArtifactStream);
    when(artifactService.fetchLastCollectedApprovedArtifactForArtifactStream(customArtifactStream))
        .thenReturn(anArtifact().build());

    when(featureFlagService.isEnabled(ARTIFACT_COLLECTION_CONFIGURABLE, ACCOUNT_ID)).thenReturn(true);
    Map<String, String> map = new HashMap<>();
    map.put("buildNo", "1.1");
    Artifact artifact =
        Artifact.Builder.anArtifact().withMetadata(new ArtifactMetadata(map)).withUuid(ARTIFACT_ID).build();
    when(artifactCollectionUtils.getArtifact(any(), any())).thenReturn(artifact);
    when(artifactService.create(artifact)).thenReturn(artifact);

    artifactCollectionState.setTimeoutMillis(10000);
    when(delegateService.queueTaskV2(any())).thenReturn(DELEGATE_ID);
    when(artifactCollectionUtils.renderCustomArtifactScriptString(customArtifactStream))
        .thenReturn(ArtifactStreamAttributes.builder().build());
    when(artifactCollectionUtils.fetchCustomDelegateTask(
             anyString(), any(), any(), eq(false), eq(BuildSourceParameters.BuildSourceRequestType.GET_BUILD), any()))
        .thenReturn(DelegateTask.builder());
    ExecutionResponse executionResponse = artifactCollectionState.execute(executionContext);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseDelegateTaskFailure() {
    when(featureFlagService.isEnabled(eq(ARTIFACT_COLLECTION_CONFIGURABLE), anyString())).thenReturn(true);
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(jenkinsArtifactStream);
    artifactCollectionState.setBuildNo("1.0");
    BuildDetails buildDetails = aBuildDetails().build();
    BuildSourceExecutionResponse buildSourceExecutionResponse =
        BuildSourceExecutionResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.FAILURE)
            .buildSourceResponse(BuildSourceResponse.builder().buildDetails(asList(buildDetails)).build())
            .build();
    Artifact artifact =
        anArtifact()
            .withMetadata(new ArtifactMetadata(Collections.singletonMap(ArtifactMetadataKeys.buildNo, "1.0")))
            .build();
    when(artifactService.getArtifactByBuildNumber(jenkinsArtifactStream, "1.0", false)).thenReturn(artifact);
    when(artifactCollectionUtils.getArtifact(jenkinsArtifactStream, buildDetails)).thenReturn(artifact);
    when(artifactService.create(artifact, jenkinsArtifactStream, false)).thenReturn(artifact);

    ExecutionResponse executionResponse = artifactCollectionState.handleAsyncResponse(
        executionContext, Collections.singletonMap("response", buildSourceExecutionResponse));
    verify(workflowExecutionService).refreshBuildExecutionSummary(any(), any());
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(executionResponse.getStateExecutionData()).isNotNull();
    assertThat(((ArtifactCollectionExecutionData) executionResponse.getStateExecutionData()).getBuildNo()).isNotNull();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseDelegateTaskFailureWithFeatureFlag() {
    when(featureFlagService.isEnabled(eq(ARTIFACT_COLLECTION_CONFIGURABLE), anyString())).thenReturn(true);
    when(featureFlagService.isEnabled(eq(SAVE_ARTIFACT_TO_DB), anyString())).thenReturn(true);
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(jenkinsArtifactStream);
    artifactCollectionState.setBuildNo("1.0");
    BuildDetails buildDetails = aBuildDetails().build();
    BuildSourceExecutionResponse buildSourceExecutionResponse =
        BuildSourceExecutionResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.FAILURE)
            .buildSourceResponse(BuildSourceResponse.builder().buildDetails(asList(buildDetails)).build())
            .build();
    Artifact artifact =
        anArtifact()
            .withMetadata(new ArtifactMetadata(Collections.singletonMap(ArtifactMetadataKeys.buildNo, "1.0")))
            .build();
    when(artifactService.getArtifactByBuildNumber(jenkinsArtifactStream, "1.0", false)).thenReturn(null);
    when(artifactService.create(any())).thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0, Artifact.class));
    when(artifactCollectionUtils.getArtifact(any(), any())).thenReturn(artifact);
    when(artifactService.create(artifact, jenkinsArtifactStream, false)).thenReturn(artifact);

    ExecutionResponse executionResponse = artifactCollectionState.handleAsyncResponse(
        executionContext, Collections.singletonMap("response", buildSourceExecutionResponse));
    verify(workflowExecutionService).refreshBuildExecutionSummary(any(), any());
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(executionResponse.getStateExecutionData()).isNotNull();
    assertThat(((ArtifactCollectionExecutionData) executionResponse.getStateExecutionData()).getBuildNo()).isNotNull();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldFetchArtifactFromDatabaseWhenArtifactStreamNotParameterizedAndFFisON() {
    CustomArtifactStream artifactStream =
        CustomArtifactStream.builder()
            .appId(APP_ID)
            .uuid(ARTIFACT_STREAM_ID)
            .scripts(asList(CustomArtifactStream.Script.builder().scriptString("echo hi").build()))
            .sourceName(ARTIFACT_SOURCE_NAME)
            .settingId(SETTING_ID)
            .serviceId(SERVICE_ID)
            .build();
    artifactStream.setArtifactStreamParameterized(false);

    Artifact lastCollectedArtifact = Artifact.Builder.anArtifact().build();
    lastCollectedArtifact.getMetadata().put(ArtifactMetadataKeys.buildNo, "1.1");
    when(artifactService.getArtifactByBuildNumber(artifactStream, "1.1", false)).thenReturn(lastCollectedArtifact);

    when(featureFlagService.isEnabled(eq(ARTIFACT_COLLECTION_CONFIGURABLE), anyString())).thenReturn(true);
    when(featureFlagService.isEnabled(eq(SPG_FETCH_ARTIFACT_FROM_DB), anyString())).thenReturn(true);

    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(artifactStream);
    artifactCollectionState.setBuildNo("1.1");
    when(artifactCollectionUtils.renderCustomArtifactScriptString(artifactStream))
        .thenReturn(ArtifactStreamAttributes.builder().build());
    when(artifactCollectionUtils.fetchCustomDelegateTask(
             anyString(), any(), any(), eq(false), eq(BuildSourceParameters.BuildSourceRequestType.GET_BUILD), any()))
        .thenReturn(DelegateTask.builder());

    ExecutionResponse executionResponse = artifactCollectionState.execute(executionContext);
    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.getDelegateTaskId()).isNull();
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(executionResponse.getStateExecutionData())
        .isNotNull()
        .isInstanceOf(ArtifactCollectionExecutionData.class);
    ArtifactCollectionExecutionData executionData =
        (ArtifactCollectionExecutionData) executionResponse.getStateExecutionData();
    assertThat(executionData.getBuildNo()).isEqualTo("1.1");
  }
}
