/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.execution;

import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.PRABU;

import static software.wings.beans.EntityType.ENVIRONMENT;
import static software.wings.beans.EntityType.INFRASTRUCTURE_DEFINITION;
import static software.wings.beans.EntityType.SERVICE;
import static software.wings.beans.PipelineExecution.Builder.aPipelineExecution;
import static software.wings.beans.Variable.VariableBuilder.aVariable;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_MANIFEST_NAME;
import static software.wings.utils.WingsTestConstants.ARTIFACT_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_SOURCE_NAME;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.HELM_CHART_ID;
import static software.wings.utils.WingsTestConstants.INFRA_DEFINITION_ID;
import static software.wings.utils.WingsTestConstants.INFRA_NAME;
import static software.wings.utils.WingsTestConstants.PIPELINE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_NAME;
import static software.wings.utils.WingsTestConstants.WORKFLOW_EXECUTION_ID;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Arrays.asList;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.CreatedByType;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.Pipeline;
import software.wings.beans.PipelineStage;
import software.wings.beans.PipelineStage.PipelineStageElement;
import software.wings.beans.RuntimeInputsConfig;
import software.wings.beans.Service;
import software.wings.beans.Variable;
import software.wings.beans.VariableType;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.HelmChart;
import software.wings.beans.appmanifest.StoreType;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.CustomArtifactStream;
import software.wings.beans.deployment.DeploymentMetadata;
import software.wings.beans.deployment.DeploymentMetadata.DeploymentMetadataKeys;
import software.wings.beans.deployment.WorkflowVariablesMetadata;
import software.wings.graphql.datafetcher.MutationContext;
import software.wings.graphql.datafetcher.user.UserController;
import software.wings.graphql.schema.mutation.execution.input.QLArtifactIdInput;
import software.wings.graphql.schema.mutation.execution.input.QLArtifactInputType;
import software.wings.graphql.schema.mutation.execution.input.QLArtifactValueInput;
import software.wings.graphql.schema.mutation.execution.input.QLBuildNumberInput;
import software.wings.graphql.schema.mutation.execution.input.QLExecutionType;
import software.wings.graphql.schema.mutation.execution.input.QLManifestInputType;
import software.wings.graphql.schema.mutation.execution.input.QLManifestValueInput;
import software.wings.graphql.schema.mutation.execution.input.QLServiceInput;
import software.wings.graphql.schema.mutation.execution.input.QLStartExecutionInput;
import software.wings.graphql.schema.mutation.execution.input.QLVariableInput;
import software.wings.graphql.schema.mutation.execution.input.QLVariableValue;
import software.wings.graphql.schema.mutation.execution.input.QLVariableValueType;
import software.wings.graphql.schema.mutation.execution.input.QLVersionNumberInput;
import software.wings.graphql.schema.mutation.execution.payload.QLStartExecutionPayload;
import software.wings.graphql.schema.query.QLServiceInputsForExecutionParams;
import software.wings.graphql.schema.type.QLExecuteOptions;
import software.wings.graphql.schema.type.QLExecutedByUser;
import software.wings.graphql.schema.type.QLExecutionStatus;
import software.wings.graphql.schema.type.QLPipelineExecution;
import software.wings.graphql.schema.type.QLPipelineExecution.QLPipelineExecutionBuilder;
import software.wings.infra.InfrastructureDefinition;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.impl.security.auth.AuthHandler;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.applicationmanifest.HelmChartService;
import software.wings.utils.JsonUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.api.client.util.Lists;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import graphql.schema.DataFetchingEnvironment;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

@OwnedBy(HarnessTeam.CDC)
public class PipelineExecutionControllerTest extends WingsBaseTest {
  private static final String APP_ID = "APP_ID";
  @Mock AuthHandler authHandler;
  @Mock AuthService authService;
  @Mock PipelineService pipelineService;
  @Mock WorkflowExecutionService workflowExecutionService;
  @Mock EnvironmentService environmentService;
  @Mock ServiceResourceService serviceResourceService;
  @Mock InfrastructureDefinitionService infrastructureDefinitionService;
  @Inject @InjectMocks ExecutionController executionController;
  @Mock FeatureFlagService featureFlagService;
  @Mock ArtifactService artifactService;
  @Mock ArtifactStreamService artifactStreamService;
  @Mock HelmChartService helmChartService;
  @Mock ApplicationManifestService applicationManifestService;
  @Inject @InjectMocks PipelineExecutionController pipelineExecutionController = new PipelineExecutionController();

  private static final String ENVIRONMENT_DEV_ID = "ENV_DEV_ID";

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void pipelineExecutionIsBuiltCorrectlyEvenWhenStageIsDeleted() {
    // Note: Deleted few fields from this due to issues with serialization
    WorkflowExecution workflowExecution =
        JsonUtils.readResourceFile("execution/workflow_execution.json", WorkflowExecution.class);

    when(workflowExecutionService.fetchWorkflowVariables(any(), any(), any(), any()))
        .thenThrow(new IllegalStateException());

    when(workflowExecutionService.fetchFailureDetails(any(), any())).thenReturn("failureDetails");

    QLPipelineExecutionBuilder builder = QLPipelineExecution.builder();
    pipelineExecutionController.populatePipelineExecution(workflowExecution, builder);
    JsonNode actual = JsonUtils.toJsonNode(builder.build());
    JsonNode expected =
        JsonUtils.readResourceFile("execution/qlPipeline_execution_expected_when_exception.json", JsonNode.class);
    assertEquals("QLPipeline execution should be equal", expected, actual);
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void pipelineExecutionIsBuiltCorrectly() {
    // Note: Deleted few fields from this due to issues with serialization
    WorkflowExecution workflowExecution =
        JsonUtils.readResourceFile("execution/workflow_execution.json", WorkflowExecution.class);

    WorkflowVariablesMetadata metadata = new WorkflowVariablesMetadata(Lists.newArrayList());
    when(workflowExecutionService.fetchWorkflowVariables(any(), any(), any(), any())).thenReturn(metadata);
    when(workflowExecutionService.fetchFailureDetails(any(), any())).thenReturn("failureDetails");

    QLPipelineExecutionBuilder builder = QLPipelineExecution.builder();
    pipelineExecutionController.populatePipelineExecution(workflowExecution, builder);
    JsonNode actual = JsonUtils.toJsonNode(builder.build());
    JsonNode expected = JsonUtils.readResourceFile("execution/qlPipeline_execution_expected.json", JsonNode.class);
    assertEquals("QLPipeline execution should be equal", expected, actual);
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void pipelineExecutionIsBuiltCorrectlyWithApprovalStageTriggerResumed() {
    // Note: Deleted few fields from this due to issues with serialization
    WorkflowExecution workflowExecution =
        JsonUtils.readResourceFile("execution/workflow_execution_resumed_after_approval.json", WorkflowExecution.class);

    WorkflowVariablesMetadata metadata = new WorkflowVariablesMetadata(Lists.newArrayList());
    when(workflowExecutionService.fetchWorkflowVariables(any(), any(), any(), any())).thenReturn(metadata);
    when(workflowExecutionService.fetchFailureDetails(any(), any())).thenReturn("failureDetails");

    QLPipelineExecutionBuilder builder = QLPipelineExecution.builder();
    pipelineExecutionController.populatePipelineExecution(workflowExecution, builder);
    JsonNode actual = JsonUtils.toJsonNode(builder.build());
    JsonNode expected = JsonUtils.readResourceFile(
        "execution/qlPipeline_execution_expected_with_resumed_pipeline.json", JsonNode.class);
    assertEquals("QLPipeline execution should be equal", expected, actual);
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void pipelineExecutionIsBuiltCorrectlyWhenPipelineStageElementIsNull() {
    // Note: Deleted few fields from this due to issues with serialization
    WorkflowExecution workflowExecution = JsonUtils.readResourceFile(
        "execution/workflow_execution_without_pipeline_stage_element_id.json", WorkflowExecution.class);

    WorkflowVariablesMetadata metadata = new WorkflowVariablesMetadata(Lists.newArrayList());
    when(workflowExecutionService.fetchWorkflowVariables(any(), any(), any(), any())).thenReturn(metadata);

    QLPipelineExecutionBuilder builder = QLPipelineExecution.builder();
    pipelineExecutionController.populatePipelineExecution(workflowExecution, builder);
    JsonNode actual = JsonUtils.toJsonNode(builder.build());
    JsonNode expected = JsonUtils.readResourceFile(
        "execution/qlPipeline_execution_expected_when_pipeline_stage_element_id_missing.json", JsonNode.class);
    assertEquals("QLPipeline execution should be equal", expected, actual);
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testRunningPipelineResolveNonRuntimeEnvVariable() throws Exception {
    // Test case for getting env variable for pipelines with non runtime env variable

    WorkflowExecution execution =
        WorkflowExecution.builder()
            .executionArgs(
                ExecutionArgs.builder()
                    .workflowVariables(ImmutableMap.<String, String>builder().put("env", ENVIRONMENT_DEV_ID).build())
                    .build())
            .build();

    Variable envNonRuntimeVar = buildVariable("env", ENVIRONMENT, false);
    Pipeline pipeline = buildPipeline(null, envNonRuntimeVar);

    String actual = pipelineExecutionController.resolveEnvId(execution, pipeline, null);
    assertThat(ENVIRONMENT_DEV_ID).isEqualTo(actual);

    envNonRuntimeVar = buildVariable("env", ENVIRONMENT, null);
    pipeline = buildPipeline(null, envNonRuntimeVar);

    actual = pipelineExecutionController.resolveEnvId(execution, pipeline, null);
    assertThat(ENVIRONMENT_DEV_ID).isEqualTo(actual);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testReturnNullWhenEnvVariableMadeNonRuntime() throws Exception {
    // Test case for getting env variable for pipelines with non runtime env variable

    WorkflowExecution execution = WorkflowExecution.builder().executionArgs(ExecutionArgs.builder().build()).build();

    Variable envNonRuntimeVar = buildVariable("env", ENVIRONMENT, false);
    Pipeline pipeline = buildPipeline(null, envNonRuntimeVar);
    when(environmentService.getEnvironmentByName(any(), eq("env")))
        .thenReturn(Environment.Builder.anEnvironment().build());

    assertThatThrownBy(
        ()
            -> pipelineExecutionController.resolveEnvId(execution, pipeline,
                asList(QLVariableInput.builder()
                           .name("env")
                           .variableValue(QLVariableValue.builder().value("env").type(QLVariableValueType.NAME).build())
                           .build())))
        .hasMessage("Pipeline [null] has environment parameterized. However, the value not supplied");
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testEnvVariableNonTemplatized() {
    Pipeline pipeline = buildPipeline(null, null);
    String actual = pipelineExecutionController.resolveEnvId(pipeline, new ArrayList<>());
    assertNull(actual);

    pipeline = buildPipeline(null);
    actual = pipelineExecutionController.resolveEnvId(pipeline, null);
    assertNull(actual);
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testEnvVarTemplatisedButRuntime() {
    Pipeline pipeline = buildPipeline(null, buildVariable("env", ENVIRONMENT, true));
    String actual = pipelineExecutionController.resolveEnvId(pipeline, new ArrayList<>(), true);
    assertNull(actual);
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testEnvTemplatizedButNotPresent() throws Exception {
    assertThatThrownBy(() -> {
      Pipeline pipeline = buildPipeline(null, buildVariable("env", ENVIRONMENT, false));
      pipelineExecutionController.resolveEnvId(pipeline, null, false);
    }).isInstanceOf(InvalidRequestException.class);

    assertThatThrownBy(() -> {
      Pipeline pipeline = buildPipeline(null, buildVariable("env", ENVIRONMENT, true));
      pipelineExecutionController.resolveEnvId(pipeline, null, false);
    }).isInstanceOf(InvalidRequestException.class);

    assertThatThrownBy(() -> {
      Pipeline pipeline = buildPipeline(null, buildVariable("env", ENVIRONMENT, true));
      List<QLVariableInput> variableInputs = newArrayList(QLVariableInput.builder().name("service").build());
      pipelineExecutionController.resolveEnvId(pipeline, variableInputs, false);
    }).isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testEnvPresentButWithExpression() {
    Pipeline pipeline = buildPipeline(null, buildVariable("env", ENVIRONMENT, false));
    assertThatThrownBy(
        ()
            -> pipelineExecutionController.resolveEnvId(pipeline,
                newArrayList(QLVariableInput.builder()
                                 .name("env")
                                 .variableValue(QLVariableValue.builder().type(QLVariableValueType.EXPRESSION).build())
                                 .build()),
                true))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testInvalidEnvName() {
    when(environmentService.getEnvironmentByName(any(), any())).thenReturn(null);
    Pipeline pipeline = buildPipeline(null, buildVariable("env", ENVIRONMENT, false));
    assertThatThrownBy(
        ()
            -> pipelineExecutionController.resolveEnvId(pipeline,
                newArrayList(
                    QLVariableInput.builder()
                        .name("env")
                        .variableValue(
                            QLVariableValue.builder().type(QLVariableValueType.NAME).value("does_not_exist").build())
                        .build()),
                true))
        .isInstanceOf(GeneralException.class);
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testEnvPresentCorrectly() {
    when(environmentService.getEnvironmentByName(eq("APP_ID"), eq("DEV")))
        .thenReturn(Environment.Builder.anEnvironment().uuid(ENVIRONMENT_DEV_ID).build());
    when(environmentService.get(eq("APP_ID"), eq(ENVIRONMENT_DEV_ID)))
        .thenReturn(Environment.Builder.anEnvironment().uuid(ENVIRONMENT_DEV_ID).build());
    Pipeline pipeline = buildPipeline("APP_ID", buildVariable("env", ENVIRONMENT, false));
    String actual = pipelineExecutionController.resolveEnvId(pipeline,
        newArrayList(
            QLVariableInput.builder()
                .name("env")
                .variableValue(QLVariableValue.builder().type(QLVariableValueType.ID).value(ENVIRONMENT_DEV_ID).build())
                .build()),
        true);
    assertThat(actual).isEqualTo(ENVIRONMENT_DEV_ID);

    actual = pipelineExecutionController.resolveEnvId(pipeline,
        newArrayList(QLVariableInput.builder()
                         .name("env")
                         .variableValue(QLVariableValue.builder().type(QLVariableValueType.NAME).value("DEV").build())
                         .build()),
        true);

    assertThat(actual).isEqualTo(ENVIRONMENT_DEV_ID);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void pipelineExecutionByTriggerIsBuiltCorrectly() {
    // Note: Deleted few fields from this due to issues with serialization
    WorkflowExecution workflowExecution =
        JsonUtils.readResourceFile("execution/workflow_execution.json", WorkflowExecution.class);

    workflowExecution.setDeploymentTriggerId("TRIGGER_ID");

    when(workflowExecutionService.fetchWorkflowVariables(any(), any(), any(), any()))
        .thenThrow(new IllegalStateException());
    when(workflowExecutionService.fetchFailureDetails(any(), any())).thenReturn("failureDetails");

    QLPipelineExecutionBuilder builder = QLPipelineExecution.builder();
    pipelineExecutionController.populatePipelineExecution(workflowExecution, builder);
    JsonNode actual = JsonUtils.toJsonNode(builder.build());
    JsonNode expected = JsonUtils.readResourceFile("execution/qlPipeline_execution_by_trigger.json", JsonNode.class);
    assertEquals("QLPipeline execution should be equal", expected, actual);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void pipelineExecutionByApiKeyIsBuiltCorrectly() {
    // Note: Deleted few fields from this due to issues with serialization
    WorkflowExecution workflowExecution =
        JsonUtils.readResourceFile("execution/workflow_execution.json", WorkflowExecution.class);

    workflowExecution.setCreatedByType(CreatedByType.API_KEY);
    workflowExecution.setCreatedBy(EmbeddedUser.builder().name("API_KEY").uuid("KEY_ID").build());

    when(workflowExecutionService.fetchWorkflowVariables(any(), any(), any(), any()))
        .thenThrow(new IllegalStateException());
    when(workflowExecutionService.fetchFailureDetails(any(), any())).thenReturn("failureDetails");

    QLPipelineExecutionBuilder builder = QLPipelineExecution.builder();
    pipelineExecutionController.populatePipelineExecution(workflowExecution, builder);
    JsonNode actual = JsonUtils.toJsonNode(builder.build());
    JsonNode expected = JsonUtils.readResourceFile("execution/qlPipeline_execution_by_apikey.json", JsonNode.class);
    assertEquals("QLPipeline execution should be equal", expected, actual);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldStartPipelineWithArtifact() {
    QLStartExecutionInput qlStartExecutionInput = getStartExecutionInputWithoutManifest();
    Pipeline pipeline = Pipeline.builder().uuid(PIPELINE_ID).appId(APP_ID).build();
    when(pipelineService.readPipeline(APP_ID, PIPELINE_ID, true)).thenReturn(pipeline);
    ArgumentCaptor<ExecutionArgs> captor = ArgumentCaptor.forClass(ExecutionArgs.class);

    when(workflowExecutionService.triggerEnvExecution(eq(APP_ID), eq(null), captor.capture(), eq(null)))
        .thenReturn(WorkflowExecution.builder().uuid(WORKFLOW_EXECUTION_ID).status(ExecutionStatus.RUNNING).build());
    when(pipelineService.fetchDeploymentMetadata(
             eq(APP_ID), eq(PIPELINE_ID), any(), eq(null), eq(null), eq(false), eq(null)))
        .thenReturn(
            DeploymentMetadata.builder().artifactRequiredServiceIds(asList(SERVICE_ID, SERVICE_ID + 2)).build());
    when(serviceResourceService.get(eq(APP_ID), any()))
        .thenAnswer(invocationOnMock
            -> Service.builder()
                   .appId(APP_ID)
                   .accountId(ACCOUNT_ID)
                   .name(invocationOnMock.getArgument(1, String.class))
                   .uuid(invocationOnMock.getArgument(1, String.class))
                   .build());

    ArtifactStream artifactStream = CustomArtifactStream.builder().build();
    when(artifactService.get(ACCOUNT_ID, ARTIFACT_ID + 2))
        .thenReturn(
            Artifact.Builder.anArtifact().withUuid(ARTIFACT_ID + 2).withServiceIds(asList(SERVICE_ID + 2)).build());
    when(artifactStreamService.getArtifactStreamByName(APP_ID, SERVICE_ID, ARTIFACT_SOURCE_NAME))
        .thenReturn(artifactStream);
    when(artifactService.getArtifactByBuildNumber(artifactStream, "1.0", false))
        .thenReturn(Artifact.Builder.anArtifact().withUuid(ARTIFACT_ID).build());

    QLStartExecutionPayload startExecutionPayload =
        pipelineExecutionController.startPipelineExecution(qlStartExecutionInput, MutationContext.builder().build());
    assertThat(startExecutionPayload).isNotNull();
    assertThat(startExecutionPayload.getExecution().getStatus()).isEqualTo(QLExecutionStatus.RUNNING);
    assertThat(captor.getValue().getArtifacts().stream().map(Artifact::getUuid).collect(Collectors.toList()))
        .containsExactlyInAnyOrder(ARTIFACT_ID, ARTIFACT_ID + 2);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldStartPipelineWithoutArtifact() {
    QLStartExecutionInput qlStartExecutionInput = getStartExecutionInputWithoutManifest();
    Pipeline pipeline = Pipeline.builder().uuid(PIPELINE_ID).appId(APP_ID).build();
    when(pipelineService.readPipeline(APP_ID, PIPELINE_ID, true)).thenReturn(pipeline);
    ArgumentCaptor<ExecutionArgs> captor = ArgumentCaptor.forClass(ExecutionArgs.class);

    when(workflowExecutionService.triggerEnvExecution(eq(APP_ID), eq(null), captor.capture(), eq(null)))
        .thenReturn(WorkflowExecution.builder().uuid(WORKFLOW_EXECUTION_ID).status(ExecutionStatus.RUNNING).build());
    when(pipelineService.fetchDeploymentMetadata(
             eq(APP_ID), eq(PIPELINE_ID), any(), eq(null), eq(null), eq(false), eq(null)))
        .thenReturn(DeploymentMetadata.builder().build());

    QLStartExecutionPayload startExecutionPayload =
        pipelineExecutionController.startPipelineExecution(qlStartExecutionInput, MutationContext.builder().build());
    assertThat(startExecutionPayload).isNotNull();
    assertThat(startExecutionPayload.getExecution().getStatus()).isEqualTo(QLExecutionStatus.RUNNING);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldThrowErrorForArtifactMissing() {
    QLStartExecutionInput qlStartExecutionInput = getStartExecutionInputWithoutManifest();
    Pipeline pipeline = Pipeline.builder().uuid(PIPELINE_ID).appId(APP_ID).build();
    when(pipelineService.readPipeline(APP_ID, PIPELINE_ID, true)).thenReturn(pipeline);
    ArgumentCaptor<ExecutionArgs> captor = ArgumentCaptor.forClass(ExecutionArgs.class);

    when(workflowExecutionService.triggerEnvExecution(eq(APP_ID), eq(null), captor.capture(), eq(null)))
        .thenReturn(WorkflowExecution.builder().uuid(WORKFLOW_EXECUTION_ID).status(ExecutionStatus.RUNNING).build());
    when(pipelineService.fetchDeploymentMetadata(
             eq(APP_ID), eq(PIPELINE_ID), any(), eq(null), eq(null), eq(false), eq(null)))
        .thenReturn(
            DeploymentMetadata.builder().artifactRequiredServiceIds(asList(SERVICE_ID + 3, SERVICE_ID + 2)).build());
    when(serviceResourceService.get(eq(APP_ID), any()))
        .thenAnswer(invocationOnMock
            -> Service.builder()
                   .appId(APP_ID)
                   .name(invocationOnMock.getArgument(1, String.class))
                   .uuid(invocationOnMock.getArgument(1, String.class))
                   .build());
    assertThatThrownBy(()
                           -> pipelineExecutionController.startPipelineExecution(
                               qlStartExecutionInput, MutationContext.builder().build()))
        .isInstanceOf(GeneralException.class)
        .hasMessage("ServiceInput required for service: SERVICE_ID3");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldThrowErrorForInvalidArtifact() {
    QLStartExecutionInput qlStartExecutionInput = getStartExecutionInputWithoutManifest();
    Pipeline pipeline = Pipeline.builder().uuid(PIPELINE_ID).appId(APP_ID).build();
    when(pipelineService.readPipeline(APP_ID, PIPELINE_ID, true)).thenReturn(pipeline);
    ArgumentCaptor<ExecutionArgs> captor = ArgumentCaptor.forClass(ExecutionArgs.class);

    when(workflowExecutionService.triggerEnvExecution(eq(APP_ID), eq(null), captor.capture(), eq(null)))
        .thenReturn(WorkflowExecution.builder().uuid(WORKFLOW_EXECUTION_ID).status(ExecutionStatus.RUNNING).build());
    when(pipelineService.fetchDeploymentMetadata(
             eq(APP_ID), eq(PIPELINE_ID), any(), eq(null), eq(null), eq(false), eq(null)))
        .thenReturn(
            DeploymentMetadata.builder().artifactRequiredServiceIds(asList(SERVICE_ID, SERVICE_ID + 2)).build());
    when(serviceResourceService.get(eq(APP_ID), any()))
        .thenAnswer(invocationOnMock
            -> Service.builder()
                   .appId(APP_ID)
                   .name(invocationOnMock.getArgument(1, String.class))
                   .uuid(invocationOnMock.getArgument(1, String.class))
                   .build());

    ArtifactStream artifactStream = CustomArtifactStream.builder().build();
    when(artifactStreamService.getArtifactStreamByName(APP_ID, SERVICE_ID, ARTIFACT_SOURCE_NAME))
        .thenReturn(artifactStream);
    when(artifactService.get(ACCOUNT_ID, ARTIFACT_ID + 2))
        .thenReturn(
            Artifact.Builder.anArtifact().withUuid(ARTIFACT_ID + 2).withServiceIds(asList(SERVICE_ID + 2)).build());

    assertThatThrownBy(()
                           -> pipelineExecutionController.startPipelineExecution(
                               qlStartExecutionInput, MutationContext.builder().build()))
        .isInstanceOf(GeneralException.class)
        .hasMessage("Artifact Stream");

    when(artifactService.getArtifactByBuildNumber(artifactStream, "1.0", false))
        .thenReturn(Artifact.Builder.anArtifact().withUuid(ARTIFACT_ID).build());
    when(artifactService.get(ACCOUNT_ID, ARTIFACT_ID + 2)).thenReturn(null);
    assertThatThrownBy(()
                           -> pipelineExecutionController.startPipelineExecution(
                               qlStartExecutionInput, MutationContext.builder().build()))
        .isInstanceOf(GeneralException.class)
        .hasMessage("Cannot find artifact for specified Id: ARTIFACT_ID2. Might be deleted");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldStartPipelineWithPipelineVariables() {
    QLVariableInput environmentVariable =
        QLVariableInput.builder()
            .name("${env}")
            .variableValue(QLVariableValue.builder().type(QLVariableValueType.ID).value(ENV_ID).build())
            .build();

    QLVariableInput infraVariable =
        QLVariableInput.builder()
            .name("${infra}")
            .variableValue(QLVariableValue.builder().type(QLVariableValueType.NAME).value(INFRA_NAME).build())
            .build();

    QLVariableInput serviceVariable =
        QLVariableInput.builder()
            .name("${service}")
            .variableValue(QLVariableValue.builder().type(QLVariableValueType.NAME).value(SERVICE_NAME).build())
            .build();

    QLStartExecutionInput qlStartExecutionInput =
        QLStartExecutionInput.builder()
            .executionType(QLExecutionType.PIPELINE)
            .applicationId(APP_ID)
            .entityId(PIPELINE_ID)
            .variableInputs(asList(environmentVariable, infraVariable, serviceVariable))
            .build();
    Variable envVariable = aVariable().name("${env}").type(VariableType.ENTITY).entityType(ENVIRONMENT).build();
    Variable serVariable = aVariable().name("${service}").type(VariableType.ENTITY).entityType(SERVICE).build();
    Variable infVariable =
        aVariable().name("${infra}").type(VariableType.ENTITY).entityType(INFRASTRUCTURE_DEFINITION).build();

    Pipeline pipeline = Pipeline.builder().uuid(PIPELINE_ID).appId(APP_ID).build();
    pipeline.getPipelineVariables().addAll(asList(envVariable, infVariable, serVariable));
    when(pipelineService.readPipeline(APP_ID, PIPELINE_ID, true)).thenReturn(pipeline);
    when(environmentService.get(APP_ID, ENV_ID)).thenReturn(Environment.Builder.anEnvironment().uuid(ENV_ID).build());
    when(serviceResourceService.getServiceByName(APP_ID, SERVICE_NAME))
        .thenReturn(Service.builder().uuid(SERVICE_ID).build());
    when(infrastructureDefinitionService.getInfraDefByName(APP_ID, ENV_ID, INFRA_NAME))
        .thenReturn(InfrastructureDefinition.builder().uuid(INFRA_DEFINITION_ID).build());

    ArgumentCaptor<ExecutionArgs> captor = ArgumentCaptor.forClass(ExecutionArgs.class);

    when(workflowExecutionService.triggerEnvExecution(eq(APP_ID), eq(ENV_ID), captor.capture(), eq(null)))
        .thenReturn(WorkflowExecution.builder().uuid(WORKFLOW_EXECUTION_ID).status(ExecutionStatus.RUNNING).build());
    when(pipelineService.fetchDeploymentMetadata(
             eq(APP_ID), eq(PIPELINE_ID), any(), eq(null), eq(null), eq(false), eq(null)))
        .thenReturn(DeploymentMetadata.builder().build());

    QLStartExecutionPayload startExecutionPayload =
        pipelineExecutionController.startPipelineExecution(qlStartExecutionInput, MutationContext.builder().build());
    assertThat(startExecutionPayload).isNotNull();
    assertThat(startExecutionPayload.getExecution().getStatus()).isEqualTo(QLExecutionStatus.RUNNING);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldStartPipelineWithPipelineVariableMissing() {
    QLVariableInput environmentVariable =
        QLVariableInput.builder()
            .name("${env}")
            .variableValue(QLVariableValue.builder().type(QLVariableValueType.ID).value(ENV_ID).build())
            .build();

    QLStartExecutionInput qlStartExecutionInput = QLStartExecutionInput.builder()
                                                      .executionType(QLExecutionType.PIPELINE)
                                                      .applicationId(APP_ID)
                                                      .entityId(PIPELINE_ID)
                                                      .variableInputs(asList(environmentVariable))
                                                      .build();
    Variable envVariable =
        aVariable().name("${env}").type(VariableType.ENTITY).mandatory(true).entityType(ENVIRONMENT).build();
    Variable serVariable =
        aVariable().name("${service}").type(VariableType.ENTITY).mandatory(true).entityType(SERVICE).build();
    Variable infVariable = aVariable()
                               .name("${infra}")
                               .type(VariableType.ENTITY)
                               .mandatory(true)
                               .entityType(INFRASTRUCTURE_DEFINITION)
                               .build();

    Pipeline pipeline = Pipeline.builder().uuid(PIPELINE_ID).appId(APP_ID).build();
    pipeline.getPipelineVariables().addAll(asList(envVariable, infVariable, serVariable));
    when(pipelineService.readPipeline(APP_ID, PIPELINE_ID, true)).thenReturn(pipeline);
    when(environmentService.get(APP_ID, ENV_ID)).thenReturn(Environment.Builder.anEnvironment().uuid(ENV_ID).build());

    ArgumentCaptor<ExecutionArgs> captor = ArgumentCaptor.forClass(ExecutionArgs.class);

    when(workflowExecutionService.triggerEnvExecution(eq(APP_ID), eq(ENV_ID), captor.capture(), eq(null)))
        .thenReturn(WorkflowExecution.builder().uuid(WORKFLOW_EXECUTION_ID).status(ExecutionStatus.RUNNING).build());
    when(pipelineService.fetchDeploymentMetadata(
             eq(APP_ID), eq(PIPELINE_ID), any(), eq(null), eq(null), eq(false), eq(null)))
        .thenReturn(DeploymentMetadata.builder().build());

    assertThatThrownBy(()
                           -> pipelineExecutionController.startPipelineExecution(
                               qlStartExecutionInput, MutationContext.builder().build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Value not provided for required variable: [${infra},${service}]");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldThrowExceptionForPassingDisallowedValues() {
    Pipeline pipeline = Pipeline.builder().build();
    pipeline.getPipelineVariables().add(aVariable().name("var").allowedList(asList("1", "2")).build());
    doReturn(pipeline).when(pipelineService).readPipeline(any(), any(), eq(true));

    doNothing().when(authService).checkIfUserAllowedToDeployPipelineToEnv(any(), any());

    QLStartExecutionInput startExecutionInput =
        QLStartExecutionInput.builder()
            .variableInputs(
                asList(QLVariableInput.builder()
                           .name("var")
                           .variableValue(QLVariableValue.builder().type(QLVariableValueType.ID).value("3").build())
                           .build()))
            .build();
    MutationContext mutationContext = MutationContext.builder()
                                          .accountId("accountId")
                                          .dataFetchingEnvironment(Mockito.mock(DataFetchingEnvironment.class))
                                          .build();
    assertThatThrownBy(() -> pipelineExecutionController.startPipelineExecution(startExecutionInput, mutationContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Variable var can only take values [1, 2]");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldNotStartPipelineWithExpression() {
    QLVariableInput environmentVariable =
        QLVariableInput.builder()
            .name("${env}")
            .variableValue(QLVariableValue.builder().type(QLVariableValueType.EXPRESSION).value("${env}").build())
            .build();

    QLStartExecutionInput qlStartExecutionInput = QLStartExecutionInput.builder()
                                                      .executionType(QLExecutionType.PIPELINE)
                                                      .applicationId(APP_ID)
                                                      .entityId(PIPELINE_ID)
                                                      .variableInputs(asList(environmentVariable))
                                                      .build();
    Variable envVariable =
        aVariable().name("${env}").type(VariableType.ENTITY).mandatory(true).entityType(ENVIRONMENT).build();

    Pipeline pipeline = Pipeline.builder().uuid(PIPELINE_ID).appId(APP_ID).build();
    pipeline.getPipelineVariables().addAll(asList(envVariable));
    when(pipelineService.readPipeline(APP_ID, PIPELINE_ID, true)).thenReturn(pipeline);
    when(environmentService.get(APP_ID, ENV_ID)).thenReturn(Environment.Builder.anEnvironment().uuid(ENV_ID).build());

    ArgumentCaptor<ExecutionArgs> captor = ArgumentCaptor.forClass(ExecutionArgs.class);

    when(workflowExecutionService.triggerEnvExecution(eq(APP_ID), eq(ENV_ID), captor.capture(), eq(null)))
        .thenReturn(WorkflowExecution.builder().uuid(WORKFLOW_EXECUTION_ID).status(ExecutionStatus.RUNNING).build());
    when(pipelineService.fetchDeploymentMetadata(
             eq(APP_ID), eq(PIPELINE_ID), any(), eq(null), eq(null), eq(false), eq(null)))
        .thenReturn(DeploymentMetadata.builder().build());

    assertThatThrownBy(()
                           -> pipelineExecutionController.startPipelineExecution(
                               qlStartExecutionInput, MutationContext.builder().build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Value Type EXPRESSION Not supported");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldNotStartPipelineWithInvalidEntity() {
    QLVariableInput environmentVariable =
        QLVariableInput.builder()
            .name("${env}")
            .variableValue(QLVariableValue.builder().type(QLVariableValueType.ID).value(ENV_ID + 2).build())
            .build();

    QLStartExecutionInput qlStartExecutionInput = QLStartExecutionInput.builder()
                                                      .executionType(QLExecutionType.PIPELINE)
                                                      .applicationId(APP_ID)
                                                      .entityId(PIPELINE_ID)
                                                      .variableInputs(asList(environmentVariable))
                                                      .build();
    Variable envVariable =
        aVariable().name("${env}").type(VariableType.ENTITY).mandatory(true).entityType(ENVIRONMENT).build();

    Pipeline pipeline = Pipeline.builder().uuid(PIPELINE_ID).appId(APP_ID).build();
    pipeline.getPipelineVariables().addAll(asList(envVariable));
    when(pipelineService.readPipeline(APP_ID, PIPELINE_ID, true)).thenReturn(pipeline);
    when(environmentService.get(APP_ID, ENV_ID)).thenReturn(Environment.Builder.anEnvironment().uuid(ENV_ID).build());

    ArgumentCaptor<ExecutionArgs> captor = ArgumentCaptor.forClass(ExecutionArgs.class);

    when(workflowExecutionService.triggerEnvExecution(eq(APP_ID), eq(ENV_ID), captor.capture(), eq(null)))
        .thenReturn(WorkflowExecution.builder().uuid(WORKFLOW_EXECUTION_ID).status(ExecutionStatus.RUNNING).build());
    when(pipelineService.fetchDeploymentMetadata(
             eq(APP_ID), eq(PIPELINE_ID), any(), eq(null), eq(null), eq(false), eq(null)))
        .thenReturn(DeploymentMetadata.builder().build());

    assertThatThrownBy(()
                           -> pipelineExecutionController.startPipelineExecution(
                               qlStartExecutionInput, MutationContext.builder().build()))
        .isInstanceOf(GeneralException.class)
        .hasMessage("Environment [ENV_ID2] doesn't exist in specified application APP_ID");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldDisplayWarningForExtraVariables() {
    QLVariableInput environmentVariable =
        QLVariableInput.builder()
            .name("${env}")
            .variableValue(QLVariableValue.builder().type(QLVariableValueType.ID).value(ENV_ID).build())
            .build();

    QLVariableInput infraVariable =
        QLVariableInput.builder()
            .name("${infra}")
            .variableValue(QLVariableValue.builder().type(QLVariableValueType.NAME).value(INFRA_NAME).build())
            .build();

    QLStartExecutionInput qlStartExecutionInput = QLStartExecutionInput.builder()
                                                      .executionType(QLExecutionType.PIPELINE)
                                                      .applicationId(APP_ID)
                                                      .entityId(PIPELINE_ID)
                                                      .variableInputs(asList(environmentVariable, infraVariable))
                                                      .build();

    Variable envVariable =
        aVariable().name("${env}").type(VariableType.ENTITY).mandatory(true).entityType(ENVIRONMENT).build();

    Pipeline pipeline = Pipeline.builder().uuid(PIPELINE_ID).appId(APP_ID).build();
    pipeline.getPipelineVariables().addAll(asList(envVariable));
    when(pipelineService.readPipeline(APP_ID, PIPELINE_ID, true)).thenReturn(pipeline);
    when(environmentService.get(APP_ID, ENV_ID)).thenReturn(Environment.Builder.anEnvironment().uuid(ENV_ID).build());

    ArgumentCaptor<ExecutionArgs> captor = ArgumentCaptor.forClass(ExecutionArgs.class);

    when(workflowExecutionService.triggerEnvExecution(eq(APP_ID), eq(ENV_ID), captor.capture(), eq(null)))
        .thenReturn(WorkflowExecution.builder().uuid(WORKFLOW_EXECUTION_ID).status(ExecutionStatus.RUNNING).build());
    when(pipelineService.fetchDeploymentMetadata(
             eq(APP_ID), eq(PIPELINE_ID), any(), eq(null), eq(null), eq(false), eq(null)))
        .thenReturn(DeploymentMetadata.builder().build());

    QLStartExecutionPayload startExecutionPayload =
        pipelineExecutionController.startPipelineExecution(qlStartExecutionInput, MutationContext.builder().build());
    assertThat(startExecutionPayload).isNotNull();
    assertThat(startExecutionPayload.getExecution().getStatus()).isEqualTo(QLExecutionStatus.RUNNING);
    assertThat(startExecutionPayload.getWarningMessage()).isNotBlank();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldStartPipelineWithMultiInfra() {
    QLVariableInput environmentVariable =
        QLVariableInput.builder()
            .name("${env}")
            .variableValue(QLVariableValue.builder().type(QLVariableValueType.ID).value(ENV_ID).build())
            .build();

    QLVariableInput infraVariable = QLVariableInput.builder()
                                        .name("${infra}")
                                        .variableValue(QLVariableValue.builder()
                                                           .type(QLVariableValueType.NAME)
                                                           .value(INFRA_NAME + "," + INFRA_NAME + 2)
                                                           .build())
                                        .build();

    QLStartExecutionInput qlStartExecutionInput = QLStartExecutionInput.builder()
                                                      .executionType(QLExecutionType.PIPELINE)
                                                      .applicationId(APP_ID)
                                                      .entityId(PIPELINE_ID)
                                                      .variableInputs(asList(environmentVariable, infraVariable))
                                                      .build();
    Variable envVariable =
        aVariable().name("${env}").type(VariableType.ENTITY).mandatory(true).entityType(ENVIRONMENT).build();
    Variable infVariable = aVariable()
                               .name("${infra}")
                               .type(VariableType.ENTITY)
                               .mandatory(true)
                               .allowMultipleValues(true)
                               .entityType(INFRASTRUCTURE_DEFINITION)
                               .build();

    Pipeline pipeline = Pipeline.builder().uuid(PIPELINE_ID).appId(APP_ID).accountId(ACCOUNT_ID).build();
    pipeline.getPipelineVariables().addAll(asList(envVariable, infVariable));
    when(pipelineService.readPipeline(APP_ID, PIPELINE_ID, true)).thenReturn(pipeline);
    when(environmentService.get(APP_ID, ENV_ID)).thenReturn(Environment.Builder.anEnvironment().uuid(ENV_ID).build());
    when(infrastructureDefinitionService.getInfraDefByName(APP_ID, ENV_ID, INFRA_NAME))
        .thenReturn(InfrastructureDefinition.builder().uuid(INFRA_DEFINITION_ID).build());
    when(infrastructureDefinitionService.getInfraDefByName(APP_ID, ENV_ID, INFRA_NAME + 2))
        .thenReturn(InfrastructureDefinition.builder().uuid(INFRA_DEFINITION_ID + 2).build());

    ArgumentCaptor<ExecutionArgs> captor = ArgumentCaptor.forClass(ExecutionArgs.class);

    when(workflowExecutionService.triggerEnvExecution(eq(APP_ID), eq(ENV_ID), captor.capture(), eq(null)))
        .thenReturn(WorkflowExecution.builder().uuid(WORKFLOW_EXECUTION_ID).status(ExecutionStatus.RUNNING).build());
    when(pipelineService.fetchDeploymentMetadata(
             eq(APP_ID), eq(PIPELINE_ID), any(), eq(null), eq(null), eq(false), eq(null)))
        .thenReturn(DeploymentMetadata.builder().build());

    QLStartExecutionPayload startExecutionPayload =
        pipelineExecutionController.startPipelineExecution(qlStartExecutionInput, MutationContext.builder().build());
    assertThat(startExecutionPayload).isNotNull();
    assertThat(startExecutionPayload.getExecution().getStatus()).isEqualTo(QLExecutionStatus.RUNNING);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldThrowExceptionForPassingDisallowedValuesInRuntime() {
    Pipeline pipeline =
        Pipeline.builder()
            .pipelineStages(
                asList(PipelineStage.builder()
                           .pipelineStageElements(asList(
                               PipelineStageElement.builder()
                                   .uuid("PIPELINE_STAGE_ELEMENT_ID")
                                   .workflowVariables(Collections.singletonMap("var", "${var}"))
                                   .runtimeInputsConfig(
                                       RuntimeInputsConfig.builder().runtimeInputVariables(asList("${var}")).build())
                                   .build()))
                           .build()))
            .build();

    pipeline.getPipelineVariables().add(aVariable().name("var").allowedList(asList("1", "2")).build());

    List<QLVariableInput> variableInputs =
        asList(QLVariableInput.builder()
                   .name("var")
                   .variableValue(QLVariableValue.builder().type(QLVariableValueType.ID).value("3").build())
                   .build());

    assertThatThrownBy(()
                           -> pipelineExecutionController.validateAndResolveRuntimePipelineStageVars(
                               pipeline, variableInputs, null, new ArrayList<>(), "PIPELINE_STAGE_ELEMENT_ID", false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Variable var can only take values [1, 2]");
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldNotSetPipelineExecutionDetailsIfMissing() {
    long createdAt = Instant.now().getEpochSecond();
    long startedAt = Instant.now().plusMillis(5000).getEpochSecond();
    WorkflowExecution execution =
        WorkflowExecution.builder()
            .uuid("EXEC_ID")
            .workflowId("WF_ID")
            .appId(APP_ID)
            .createdAt(createdAt)
            .startTs(startedAt)
            .status(ExecutionStatus.RUNNING)
            .triggeredBy(EmbeddedUser.builder().uuid("uuid").name("username").email("example@acme.com").build())
            .pipelineExecution(aPipelineExecution().build())
            .build();
    QLPipelineExecutionBuilder builder = QLPipelineExecution.builder();
    pipelineExecutionController.populatePipelineExecution(execution, builder);
    QLPipelineExecution actual = builder.build();
    assertThat(actual.getId()).isEqualTo("EXEC_ID");
    assertThat(actual.getPipelineId()).isEqualTo("WF_ID");
    assertThat(actual.getAppId()).isEqualTo(APP_ID);
    assertThat(actual.getCreatedAt()).isEqualTo(createdAt);
    assertThat(actual.getStartedAt()).isEqualTo(startedAt);
    assertThat(actual.getEndedAt()).isNull();
    assertThat(actual.getStatus()).isEqualTo(QLExecutionStatus.RUNNING);
    assertThat(actual.getCause())
        .isEqualTo(QLExecutedByUser.builder()
                       .user(UserController.populateUser(
                           EmbeddedUser.builder().uuid("uuid").name("username").email("example@acme.com").build()))
                       .using(QLExecuteOptions.WEB_UI)
                       .build());
    assertThat(actual.getNotes()).isNull();
    assertThat(actual.getTags()).isEmpty();
  }

  private QLStartExecutionInput getStartExecutionInputWithoutManifest() {
    return QLStartExecutionInput.builder()
        .executionType(QLExecutionType.PIPELINE)
        .applicationId(APP_ID)
        .entityId(PIPELINE_ID)
        .serviceInputs(asList(QLServiceInput.builder()
                                  .name(SERVICE_ID)
                                  .artifactValueInput(QLArtifactValueInput.builder()
                                                          .valueType(QLArtifactInputType.BUILD_NUMBER)
                                                          .buildNumber(QLBuildNumberInput.builder()
                                                                           .artifactSourceName(ARTIFACT_SOURCE_NAME)
                                                                           .buildNumber("1.0")
                                                                           .build())
                                                          .build())
                                  .build(),
            QLServiceInput.builder()
                .name(SERVICE_ID + 2)
                .artifactValueInput(QLArtifactValueInput.builder()
                                        .valueType(QLArtifactInputType.ARTIFACT_ID)
                                        .artifactId(QLArtifactIdInput.builder().artifactId(ARTIFACT_ID + 2).build())
                                        .build())
                .build()))
        .build();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldAllowMultiSelectFromAllowedValues() {
    Pipeline pipeline = Pipeline.builder().build();
    pipeline.getPipelineVariables().add(aVariable().name("var").allowedList(asList("1", "2", "3")).build());
    doReturn(pipeline).when(pipelineService).readPipeline(any(), any(), eq(true));
    doNothing().when(authService).checkIfUserAllowedToDeployPipelineToEnv(any(), any());

    QLStartExecutionInput startExecutionInput =
        QLStartExecutionInput.builder()
            .applicationId(APP_ID)
            .variableInputs(
                asList(QLVariableInput.builder()
                           .name("var")
                           .variableValue(QLVariableValue.builder().type(QLVariableValueType.ID).value("2, 1").build())
                           .build()))
            .build();
    MutationContext mutationContext = MutationContext.builder()
                                          .accountId("accountId")
                                          .dataFetchingEnvironment(Mockito.mock(DataFetchingEnvironment.class))
                                          .build();
    when(workflowExecutionService.triggerEnvExecution(eq(APP_ID), any(), any(), eq(null)))
        .thenReturn(WorkflowExecution.builder().uuid(WORKFLOW_EXECUTION_ID).status(ExecutionStatus.RUNNING).build());
    when(pipelineService.fetchDeploymentMetadata(
             eq(APP_ID), eq(PIPELINE_ID), any(), eq(null), eq(null), eq(false), eq(null)))
        .thenReturn(DeploymentMetadata.builder().build());

    QLStartExecutionPayload startExecutionPayload =
        pipelineExecutionController.startPipelineExecution(startExecutionInput, mutationContext);
    assertThat(startExecutionPayload).isNotNull();
    assertThat(startExecutionPayload.getExecution().getStatus()).isEqualTo(QLExecutionStatus.RUNNING);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldPopulateArtifactAndManifestNeededServiceIds() {
    QLServiceInputsForExecutionParams params = QLServiceInputsForExecutionParams.builder()
                                                   .executionType(QLExecutionType.PIPELINE)
                                                   .applicationId(APP_ID)
                                                   .entityId(PIPELINE_ID)
                                                   .build();
    Pipeline pipeline = Pipeline.builder().uuid(PIPELINE_ID).appId(APP_ID).build();
    when(pipelineService.readPipeline(APP_ID, PIPELINE_ID, true)).thenReturn(pipeline);
    when(pipelineService.fetchDeploymentMetadata(eq(APP_ID), eq(pipeline), any()))
        .thenReturn(DeploymentMetadata.builder()
                        .artifactRequiredServiceIds(asList(SERVICE_ID, SERVICE_ID + 2))
                        .manifestRequiredServiceIds(asList(SERVICE_ID, SERVICE_ID + 3))
                        .build());
    Map<String, List<String>> requiredServiceMap =
        pipelineExecutionController.getArtifactAndManifestNeededServices(params);
    assertThat(requiredServiceMap).isNotNull();
    assertThat(requiredServiceMap.get(DeploymentMetadataKeys.artifactRequiredServices))
        .containsExactlyInAnyOrder(SERVICE_ID, SERVICE_ID + 2);
    assertThat(requiredServiceMap.get(DeploymentMetadataKeys.manifestRequiredServiceIds))
        .containsExactlyInAnyOrder(SERVICE_ID, SERVICE_ID + 3);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldStartPipelineWithManifest() {
    QLStartExecutionInput qlStartExecutionInput = getStartExecutionInput();
    Pipeline pipeline = Pipeline.builder().uuid(PIPELINE_ID).appId(APP_ID).build();
    when(pipelineService.readPipeline(APP_ID, PIPELINE_ID, true)).thenReturn(pipeline);
    ArgumentCaptor<ExecutionArgs> captor = ArgumentCaptor.forClass(ExecutionArgs.class);

    when(workflowExecutionService.triggerEnvExecution(eq(APP_ID), eq(null), captor.capture(), eq(null)))
        .thenReturn(WorkflowExecution.builder().uuid(WORKFLOW_EXECUTION_ID).status(ExecutionStatus.RUNNING).build());
    when(pipelineService.fetchDeploymentMetadata(
             eq(APP_ID), eq(PIPELINE_ID), any(), eq(null), eq(null), eq(false), eq(null)))
        .thenReturn(
            DeploymentMetadata.builder().manifestRequiredServiceIds(asList(SERVICE_ID, SERVICE_ID + 2)).build());
    when(serviceResourceService.get(eq(APP_ID), any()))
        .thenAnswer(invocationOnMock
            -> Service.builder()
                   .appId(APP_ID)
                   .name(invocationOnMock.getArgument(1, String.class))
                   .uuid(invocationOnMock.getArgument(1, String.class))
                   .build());
    when(helmChartService.getByChartVersion(APP_ID, SERVICE_ID, APP_MANIFEST_NAME, "1.0"))
        .thenReturn(HelmChart.builder().uuid(HELM_CHART_ID).build());
    when(helmChartService.get(APP_ID, HELM_CHART_ID + 2))
        .thenReturn(HelmChart.builder().uuid(HELM_CHART_ID + 2).build());
    when(applicationManifestService.getAppManifestByName(APP_ID, null, SERVICE_ID, APP_MANIFEST_NAME))
        .thenReturn(ApplicationManifest.builder().storeType(StoreType.HelmChartRepo).build());

    QLStartExecutionPayload startExecutionPayload =
        pipelineExecutionController.startPipelineExecution(qlStartExecutionInput, MutationContext.builder().build());
    assertThat(startExecutionPayload).isNotNull();
    assertThat(startExecutionPayload.getExecution().getStatus()).isEqualTo(QLExecutionStatus.RUNNING);
    assertThat(captor.getValue().getHelmCharts().stream().map(HelmChart::getUuid).collect(Collectors.toList()))
        .containsExactlyInAnyOrder(HELM_CHART_ID, HELM_CHART_ID + 2);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldStartPipelineWithoutManifest() {
    QLStartExecutionInput qlStartExecutionInput = getStartExecutionInput();
    Pipeline pipeline = Pipeline.builder().uuid(PIPELINE_ID).appId(APP_ID).build();
    when(pipelineService.readPipeline(APP_ID, PIPELINE_ID, true)).thenReturn(pipeline);
    ArgumentCaptor<ExecutionArgs> captor = ArgumentCaptor.forClass(ExecutionArgs.class);

    when(workflowExecutionService.triggerEnvExecution(eq(APP_ID), eq(null), captor.capture(), eq(null)))
        .thenReturn(WorkflowExecution.builder().uuid(WORKFLOW_EXECUTION_ID).status(ExecutionStatus.RUNNING).build());
    when(pipelineService.fetchDeploymentMetadata(
             eq(APP_ID), eq(PIPELINE_ID), any(), eq(null), eq(null), eq(false), eq(null)))
        .thenReturn(DeploymentMetadata.builder().build());

    QLStartExecutionPayload startExecutionPayload =
        pipelineExecutionController.startPipelineExecution(qlStartExecutionInput, MutationContext.builder().build());
    assertThat(startExecutionPayload).isNotNull();
    assertThat(startExecutionPayload.getExecution().getStatus()).isEqualTo(QLExecutionStatus.RUNNING);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldThrowErrorForManifestMissing() {
    QLStartExecutionInput qlStartExecutionInput = getStartExecutionInput();
    Pipeline pipeline = Pipeline.builder().uuid(PIPELINE_ID).appId(APP_ID).build();
    when(pipelineService.readPipeline(APP_ID, PIPELINE_ID, true)).thenReturn(pipeline);
    ArgumentCaptor<ExecutionArgs> captor = ArgumentCaptor.forClass(ExecutionArgs.class);

    when(workflowExecutionService.triggerEnvExecution(eq(APP_ID), eq(null), captor.capture(), eq(null)))
        .thenReturn(WorkflowExecution.builder().uuid(WORKFLOW_EXECUTION_ID).status(ExecutionStatus.RUNNING).build());
    when(pipelineService.fetchDeploymentMetadata(
             eq(APP_ID), eq(PIPELINE_ID), any(), eq(null), eq(null), eq(false), eq(null)))
        .thenReturn(
            DeploymentMetadata.builder().manifestRequiredServiceIds(asList(SERVICE_ID + 3, SERVICE_ID + 2)).build());
    when(serviceResourceService.get(eq(APP_ID), any()))
        .thenAnswer(invocationOnMock
            -> Service.builder()
                   .appId(APP_ID)
                   .name(invocationOnMock.getArgument(1, String.class))
                   .uuid(invocationOnMock.getArgument(1, String.class))
                   .build());
    assertThatThrownBy(()
                           -> pipelineExecutionController.startPipelineExecution(
                               qlStartExecutionInput, MutationContext.builder().build()))
        .isInstanceOf(GeneralException.class)
        .hasMessage("ServiceInput required for service: SERVICE_ID3");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldThrowErrorForInvalidHelmChart() {
    QLStartExecutionInput qlStartExecutionInput = getStartExecutionInput();
    Pipeline pipeline = Pipeline.builder().uuid(PIPELINE_ID).appId(APP_ID).build();
    when(pipelineService.readPipeline(APP_ID, PIPELINE_ID, true)).thenReturn(pipeline);
    ArgumentCaptor<ExecutionArgs> captor = ArgumentCaptor.forClass(ExecutionArgs.class);

    when(workflowExecutionService.triggerEnvExecution(eq(APP_ID), eq(null), captor.capture(), eq(null)))
        .thenReturn(WorkflowExecution.builder().uuid(WORKFLOW_EXECUTION_ID).status(ExecutionStatus.RUNNING).build());
    when(pipelineService.fetchDeploymentMetadata(
             eq(APP_ID), eq(PIPELINE_ID), any(), eq(null), eq(null), eq(false), eq(null)))
        .thenReturn(
            DeploymentMetadata.builder().manifestRequiredServiceIds(asList(SERVICE_ID, SERVICE_ID + 2)).build());
    when(serviceResourceService.get(eq(APP_ID), any()))
        .thenAnswer(invocationOnMock
            -> Service.builder()
                   .appId(APP_ID)
                   .name(invocationOnMock.getArgument(1, String.class))
                   .uuid(invocationOnMock.getArgument(1, String.class))
                   .build());
    when(helmChartService.getByChartVersion(APP_ID, SERVICE_ID, APP_MANIFEST_NAME, "2.0"))
        .thenReturn(HelmChart.builder().uuid(HELM_CHART_ID).build());
    when(helmChartService.get(APP_ID, HELM_CHART_ID + 2))
        .thenReturn(HelmChart.builder().uuid(HELM_CHART_ID + 2).build());
    when(applicationManifestService.getAppManifestByName(APP_ID, null, SERVICE_ID, APP_MANIFEST_NAME))
        .thenReturn(ApplicationManifest.builder().storeType(StoreType.HelmChartRepo).build());

    assertThatThrownBy(()
                           -> pipelineExecutionController.startPipelineExecution(
                               qlStartExecutionInput, MutationContext.builder().build()))
        .isInstanceOf(GeneralException.class)
        .hasMessage("Cannot find helm chart for specified version number: 1.0");

    when(helmChartService.getByChartVersion(APP_ID, SERVICE_ID, APP_MANIFEST_NAME, "1.0"))
        .thenReturn(HelmChart.builder().uuid(HELM_CHART_ID).build());
    when(helmChartService.get(APP_ID, HELM_CHART_ID + 2)).thenReturn(null);
    assertThatThrownBy(()
                           -> pipelineExecutionController.startPipelineExecution(
                               qlStartExecutionInput, MutationContext.builder().build()))
        .isInstanceOf(GeneralException.class)
        .hasMessage("Cannot find helm chart for specified Id: HELM_CHART_ID2. Might be deleted");
  }

  private QLStartExecutionInput getStartExecutionInput() {
    return QLStartExecutionInput.builder()
        .executionType(QLExecutionType.PIPELINE)
        .applicationId(APP_ID)
        .entityId(PIPELINE_ID)
        .serviceInputs(asList(QLServiceInput.builder()
                                  .name(SERVICE_ID)
                                  .manifestValueInput(QLManifestValueInput.builder()
                                                          .valueType(QLManifestInputType.VERSION_NUMBER)
                                                          .versionNumber(QLVersionNumberInput.builder()
                                                                             .versionNumber("1.0")
                                                                             .appManifestName(APP_MANIFEST_NAME)
                                                                             .build())
                                                          .build())
                                  .build(),
            QLServiceInput.builder()
                .name(SERVICE_ID + 2)
                .manifestValueInput(QLManifestValueInput.builder()
                                        .valueType(QLManifestInputType.HELM_CHART_ID)
                                        .helmChartId(HELM_CHART_ID + 2)
                                        .build())
                .build()))
        .build();
  }

  private Variable buildVariable(String name, EntityType entityType, Boolean isRuntime) {
    Variable variable = aVariable().entityType(entityType).name(name).build();
    variable.setRuntimeInput(isRuntime);
    return variable;
  }

  private Pipeline buildPipeline(String appId, Variable... variables) {
    Pipeline pipeline = Pipeline.builder().appId(appId).build();
    if (variables != null) {
      pipeline.setPipelineVariables(newArrayList(variables));
    }
    return pipeline;
  }
}
