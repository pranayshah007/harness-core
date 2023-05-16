/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.execution;

import static io.harness.rule.OwnerRule.PRABU;

import static software.wings.beans.CanaryOrchestrationWorkflow.CanaryOrchestrationWorkflowBuilder.aCanaryOrchestrationWorkflow;
import static software.wings.beans.EntityType.ENVIRONMENT;
import static software.wings.beans.EntityType.INFRASTRUCTURE_DEFINITION;
import static software.wings.beans.EntityType.SERVICE;
import static software.wings.beans.Variable.VariableBuilder.aVariable;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_MANIFEST_NAME;
import static software.wings.utils.WingsTestConstants.ARTIFACT_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_SOURCE_NAME;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.ENV_NAME;
import static software.wings.utils.WingsTestConstants.FREEZE_WINDOW_ID;
import static software.wings.utils.WingsTestConstants.HELM_CHART_ID;
import static software.wings.utils.WingsTestConstants.INFRA_DEFINITION_ID;
import static software.wings.utils.WingsTestConstants.INFRA_NAME;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_NAME;
import static software.wings.utils.WingsTestConstants.WORKFLOW_EXECUTION_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.exception.DeploymentFreezeException;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.Environment;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.Service;
import software.wings.beans.TemplateExpression;
import software.wings.beans.Variable;
import software.wings.beans.VariableType;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.HelmChart;
import software.wings.beans.appmanifest.StoreType;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.CustomArtifactStream;
import software.wings.beans.deployment.DeploymentMetadata;
import software.wings.beans.deployment.DeploymentMetadata.DeploymentMetadataKeys;
import software.wings.graphql.datafetcher.MutationContext;
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
import software.wings.graphql.schema.type.QLExecutionStatus;
import software.wings.infra.InfrastructureDefinition;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.impl.security.auth.AuthHandler;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.WorkflowService;
import software.wings.service.intfc.applicationmanifest.HelmChartService;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

@OwnedBy(HarnessTeam.CDC)
public class WorkflowExecutionControllerTest extends WingsBaseTest {
  @Mock AuthHandler authHandler;
  @Mock AuthService authService;
  @Mock WorkflowService workflowService;
  @Mock WorkflowExecutionService workflowExecutionService;
  @Mock EnvironmentService environmentService;
  @Mock ServiceResourceService serviceResourceService;
  @Mock InfrastructureDefinitionService infrastructureDefinitionService;
  @Mock ArtifactService artifactService;
  @Mock ArtifactStreamService artifactStreamService;
  @Inject @InjectMocks ExecutionController executionController;
  @Mock FeatureFlagService featureFlagService;
  @Inject @InjectMocks WorkflowExecutionController workflowExecutionController = new WorkflowExecutionController();
  @Mock HelmChartService helmChartService;
  @Mock ApplicationManifestService applicationManifestService;

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldStartWorkflowWithArtifact() {
    QLStartExecutionInput qlStartExecutionInput = getStartExecutionInputWithoutManifest();
    Workflow workflow = getWorkflow();
    when(workflowService.readWorkflow(APP_ID, WORKFLOW_ID)).thenReturn(workflow);
    ArgumentCaptor<ExecutionArgs> captor = ArgumentCaptor.forClass(ExecutionArgs.class);

    when(workflowExecutionService.triggerEnvExecution(eq(APP_ID), eq(null), captor.capture(), eq(null)))
        .thenReturn(WorkflowExecution.builder().uuid(WORKFLOW_EXECUTION_ID).status(ExecutionStatus.RUNNING).build());
    when(workflowService.fetchDeploymentMetadata(
             eq(APP_ID), eq(workflow), any(), eq(null), eq(null), eq(DeploymentMetadata.Include.ARTIFACT_SERVICE)))
        .thenReturn(
            DeploymentMetadata.builder().artifactRequiredServiceIds(Arrays.asList(SERVICE_ID, SERVICE_ID + 2)).build());
    when(serviceResourceService.get(eq(APP_ID), anyString()))
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
        workflowExecutionController.startWorkflowExecution(qlStartExecutionInput, MutationContext.builder().build());
    assertThat(startExecutionPayload).isNotNull();
    assertThat(startExecutionPayload.getExecution().getStatus()).isEqualTo(QLExecutionStatus.RUNNING);
    assertThat(captor.getValue().getArtifacts().stream().map(Artifact::getUuid).collect(Collectors.toList()))
        .containsExactlyInAnyOrder(ARTIFACT_ID, ARTIFACT_ID + 2);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldStartWorkflowWithoutArtifact() {
    QLStartExecutionInput qlStartExecutionInput = getStartExecutionInputWithoutManifest();
    Workflow workflow = getWorkflow();
    when(workflowService.readWorkflow(APP_ID, WORKFLOW_ID)).thenReturn(workflow);
    ArgumentCaptor<ExecutionArgs> captor = ArgumentCaptor.forClass(ExecutionArgs.class);

    when(workflowExecutionService.triggerEnvExecution(eq(APP_ID), eq(null), captor.capture(), eq(null)))
        .thenReturn(WorkflowExecution.builder().uuid(WORKFLOW_EXECUTION_ID).status(ExecutionStatus.RUNNING).build());
    when(workflowService.fetchDeploymentMetadata(
             eq(APP_ID), eq(workflow), any(), eq(null), eq(null), eq(DeploymentMetadata.Include.ARTIFACT_SERVICE)))
        .thenReturn(DeploymentMetadata.builder().build());

    QLStartExecutionPayload startExecutionPayload =
        workflowExecutionController.startWorkflowExecution(qlStartExecutionInput, MutationContext.builder().build());
    assertThat(startExecutionPayload).isNotNull();
    assertThat(startExecutionPayload.getExecution().getStatus()).isEqualTo(QLExecutionStatus.RUNNING);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldThrowDeploymentFreezeExceptionForFrozenEnv() {
    QLStartExecutionInput qlStartExecutionInput = getStartExecutionInputWithoutManifest();
    Workflow workflow = getWorkflow();
    workflow.setEnvId(ENV_ID);
    when(workflowService.readWorkflow(APP_ID, WORKFLOW_ID)).thenReturn(workflow);
    ArgumentCaptor<ExecutionArgs> captor = ArgumentCaptor.forClass(ExecutionArgs.class);

    when(workflowExecutionService.triggerEnvExecution(eq(APP_ID), eq(ENV_ID), captor.capture(), eq(null)))
        .thenThrow(new DeploymentFreezeException(ErrorCode.DEPLOYMENT_GOVERNANCE_ERROR, Level.INFO, WingsException.USER,
            ACCOUNT_ID, Collections.singletonList(FREEZE_WINDOW_ID), Collections.singletonList("FREEZE_NAME"),
            "FREEZE_NAME", false, false));
    when(workflowService.fetchDeploymentMetadata(
             eq(APP_ID), eq(workflow), any(), eq(null), any(), eq(DeploymentMetadata.Include.ARTIFACT_SERVICE)))
        .thenReturn(DeploymentMetadata.builder().build());

    assertThatThrownBy(()
                           -> workflowExecutionController.startWorkflowExecution(
                               qlStartExecutionInput, MutationContext.builder().build()))
        .isInstanceOf(DeploymentFreezeException.class)
        .hasMessage(
            "Deployment Freeze Window FREEZE_NAME is active for the environment. No deployments are allowed to proceed.");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldThrowErrorForArtifactMissing() {
    QLStartExecutionInput qlStartExecutionInput = getStartExecutionInputWithoutManifest();
    Workflow workflow = getWorkflow();
    when(workflowService.readWorkflow(APP_ID, WORKFLOW_ID)).thenReturn(workflow);
    ArgumentCaptor<ExecutionArgs> captor = ArgumentCaptor.forClass(ExecutionArgs.class);

    when(workflowExecutionService.triggerEnvExecution(eq(APP_ID), eq(null), captor.capture(), eq(null)))
        .thenReturn(WorkflowExecution.builder().uuid(WORKFLOW_EXECUTION_ID).status(ExecutionStatus.RUNNING).build());
    when(workflowService.fetchDeploymentMetadata(
             eq(APP_ID), eq(workflow), any(), eq(null), eq(null), eq(DeploymentMetadata.Include.ARTIFACT_SERVICE)))
        .thenReturn(DeploymentMetadata.builder()
                        .artifactRequiredServiceIds(Arrays.asList(SERVICE_ID + 3, SERVICE_ID + 2))
                        .build());

    when(serviceResourceService.get(eq(APP_ID), anyString()))
        .thenAnswer(invocationOnMock
            -> Service.builder()
                   .appId(APP_ID)
                   .name(invocationOnMock.getArgument(1, String.class))
                   .uuid(invocationOnMock.getArgument(1, String.class))
                   .build());
    assertThatThrownBy(()
                           -> workflowExecutionController.startWorkflowExecution(
                               qlStartExecutionInput, MutationContext.builder().build()))
        .isInstanceOf(GeneralException.class)
        .hasMessage("ServiceInput required for service: SERVICE_ID3");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldStartWorkflowWithVariables() {
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
            .executionType(QLExecutionType.WORKFLOW)
            .applicationId(APP_ID)
            .entityId(WORKFLOW_ID)
            .variableInputs(asList(environmentVariable, infraVariable, serviceVariable))
            .build();

    Workflow workflow = getWorkflow();
    Variable envVariable = aVariable().name("${env}").type(VariableType.ENTITY).entityType(ENVIRONMENT).build();
    Variable serVariable = aVariable().name("${service}").type(VariableType.ENTITY).entityType(SERVICE).build();
    Variable infVariable =
        aVariable().name("${infra}").type(VariableType.ENTITY).entityType(INFRASTRUCTURE_DEFINITION).build();
    workflow.setOrchestrationWorkflow(
        aCanaryOrchestrationWorkflow().withUserVariables(asList(envVariable, serVariable, infVariable)).build());
    workflow.setTemplateExpressions(asList(TemplateExpression.builder().fieldName("envId").build()));

    when(workflowService.readWorkflow(APP_ID, WORKFLOW_ID)).thenReturn(workflow);
    when(environmentService.get(APP_ID, ENV_ID)).thenReturn(Environment.Builder.anEnvironment().uuid(ENV_ID).build());
    when(serviceResourceService.getServiceByName(APP_ID, SERVICE_NAME))
        .thenReturn(Service.builder().uuid(SERVICE_ID).build());
    when(infrastructureDefinitionService.getInfraDefByName(APP_ID, ENV_ID, INFRA_NAME))
        .thenReturn(InfrastructureDefinition.builder().uuid(INFRA_DEFINITION_ID).build());
    ArgumentCaptor<ExecutionArgs> captor = ArgumentCaptor.forClass(ExecutionArgs.class);

    when(workflowExecutionService.triggerEnvExecution(eq(APP_ID), eq(ENV_ID), captor.capture(), eq(null)))
        .thenReturn(WorkflowExecution.builder().uuid(WORKFLOW_EXECUTION_ID).status(ExecutionStatus.RUNNING).build());
    when(workflowService.fetchDeploymentMetadata(
             eq(APP_ID), eq(workflow), any(), eq(null), eq(null), eq(DeploymentMetadata.Include.ARTIFACT_SERVICE)))
        .thenReturn(DeploymentMetadata.builder().build());

    QLStartExecutionPayload startExecutionPayload =
        workflowExecutionController.startWorkflowExecution(qlStartExecutionInput, MutationContext.builder().build());
    assertThat(startExecutionPayload).isNotNull();
    assertThat(startExecutionPayload.getExecution().getStatus()).isEqualTo(QLExecutionStatus.RUNNING);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldNotStartWorkflowWithMissingVariables() {
    QLVariableInput environmentVariable =
        QLVariableInput.builder()
            .name("${env}")
            .variableValue(QLVariableValue.builder().type(QLVariableValueType.NAME).value(ENV_NAME).build())
            .build();

    QLStartExecutionInput qlStartExecutionInput = QLStartExecutionInput.builder()
                                                      .executionType(QLExecutionType.WORKFLOW)
                                                      .applicationId(APP_ID)
                                                      .entityId(WORKFLOW_ID)
                                                      .variableInputs(asList(environmentVariable))
                                                      .build();

    Workflow workflow = getWorkflow();
    Variable envVariable = aVariable().name("${env}").type(VariableType.ENTITY).entityType(ENVIRONMENT).build();
    Variable serVariable =
        aVariable().name("${service}").type(VariableType.ENTITY).mandatory(true).entityType(SERVICE).build();
    Variable infVariable = aVariable()
                               .name("${infra}")
                               .type(VariableType.ENTITY)
                               .mandatory(true)
                               .entityType(INFRASTRUCTURE_DEFINITION)
                               .build();
    workflow.setOrchestrationWorkflow(
        aCanaryOrchestrationWorkflow().withUserVariables(asList(envVariable, serVariable, infVariable)).build());
    workflow.setTemplateExpressions(asList(TemplateExpression.builder().fieldName("envId").build()));

    when(workflowService.readWorkflow(APP_ID, WORKFLOW_ID)).thenReturn(workflow);
    when(environmentService.getEnvironmentByName(APP_ID, ENV_NAME))
        .thenReturn(Environment.Builder.anEnvironment().uuid(ENV_ID).build());

    assertThatThrownBy(()
                           -> workflowExecutionController.startWorkflowExecution(
                               qlStartExecutionInput, MutationContext.builder().build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Value not provided for required variable: [${service},${infra}]");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldNotStartWorkflowWithEnvExpression() {
    QLVariableInput environmentVariable =
        QLVariableInput.builder()
            .name("${env}")
            .variableValue(QLVariableValue.builder().type(QLVariableValueType.EXPRESSION).value("${env}").build())
            .build();

    QLStartExecutionInput qlStartExecutionInput = QLStartExecutionInput.builder()
                                                      .executionType(QLExecutionType.WORKFLOW)
                                                      .applicationId(APP_ID)
                                                      .entityId(WORKFLOW_ID)
                                                      .variableInputs(asList(environmentVariable))
                                                      .build();

    Workflow workflow = getWorkflow();
    Variable envVariable = aVariable().name("${env}").type(VariableType.ENTITY).entityType(ENVIRONMENT).build();
    workflow.setOrchestrationWorkflow(aCanaryOrchestrationWorkflow().withUserVariables(asList(envVariable)).build());
    workflow.setTemplateExpressions(asList(TemplateExpression.builder().fieldName("envId").build()));

    when(workflowService.readWorkflow(APP_ID, WORKFLOW_ID)).thenReturn(workflow);
    when(environmentService.getEnvironmentByName(APP_ID, ENV_NAME))
        .thenReturn(Environment.Builder.anEnvironment().uuid(ENV_ID).build());

    assertThatThrownBy(()
                           -> workflowExecutionController.startWorkflowExecution(
                               qlStartExecutionInput, MutationContext.builder().build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Value Type EXPRESSION Not supported");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldNotStartWorkflowWithInvalidEnv() {
    QLVariableInput environmentVariable =
        QLVariableInput.builder()
            .name("${env}")
            .variableValue(QLVariableValue.builder().type(QLVariableValueType.ID).value(ENV_ID).build())
            .build();

    QLStartExecutionInput qlStartExecutionInput = QLStartExecutionInput.builder()
                                                      .executionType(QLExecutionType.WORKFLOW)
                                                      .applicationId(APP_ID)
                                                      .entityId(WORKFLOW_ID)
                                                      .variableInputs(asList(environmentVariable))
                                                      .build();

    Workflow workflow = getWorkflow();
    Variable envVariable = aVariable().name("${env}").type(VariableType.ENTITY).entityType(ENVIRONMENT).build();
    workflow.setOrchestrationWorkflow(aCanaryOrchestrationWorkflow().withUserVariables(asList(envVariable)).build());
    workflow.setTemplateExpressions(asList(TemplateExpression.builder().fieldName("envId").build()));

    when(workflowService.readWorkflow(APP_ID, WORKFLOW_ID)).thenReturn(workflow);
    when(environmentService.getEnvironmentByName(APP_ID, ENV_NAME))
        .thenReturn(Environment.Builder.anEnvironment().uuid(ENV_ID).build());

    assertThatThrownBy(()
                           -> workflowExecutionController.startWorkflowExecution(
                               qlStartExecutionInput, MutationContext.builder().build()))
        .isInstanceOf(GeneralException.class)
        .hasMessage("Environment [ENV_ID] doesn't exist in specified application APP_ID");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldNotStartWorkflowWithFixedVariableChanged() {
    QLVariableInput environmentVariable =
        QLVariableInput.builder()
            .name("${var}")
            .variableValue(QLVariableValue.builder().type(QLVariableValueType.NAME).value("2").build())
            .build();

    QLStartExecutionInput qlStartExecutionInput = QLStartExecutionInput.builder()
                                                      .executionType(QLExecutionType.WORKFLOW)
                                                      .applicationId(APP_ID)
                                                      .entityId(WORKFLOW_ID)
                                                      .variableInputs(asList(environmentVariable))
                                                      .build();

    Workflow workflow = getWorkflow();
    Variable variable = aVariable().name("${var}").type(VariableType.TEXT).fixed(true).value("1").build();
    workflow.setOrchestrationWorkflow(aCanaryOrchestrationWorkflow().withUserVariables(asList(variable)).build());

    when(workflowService.readWorkflow(APP_ID, WORKFLOW_ID)).thenReturn(workflow);

    assertThatThrownBy(()
                           -> workflowExecutionController.startWorkflowExecution(
                               qlStartExecutionInput, MutationContext.builder().build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Cannot change value of a fixed variable in workflow: ${var}. Value set in workflow is: 1");
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
                                                      .executionType(QLExecutionType.WORKFLOW)
                                                      .applicationId(APP_ID)
                                                      .entityId(WORKFLOW_ID)
                                                      .variableInputs(asList(environmentVariable, infraVariable))
                                                      .build();

    Workflow workflow = getWorkflow();
    Variable envVariable = aVariable().name("${env}").type(VariableType.ENTITY).entityType(ENVIRONMENT).build();
    workflow.setOrchestrationWorkflow(aCanaryOrchestrationWorkflow().withUserVariables(asList(envVariable)).build());
    workflow.setTemplateExpressions(asList(TemplateExpression.builder().fieldName("envId").build()));

    when(workflowService.readWorkflow(APP_ID, WORKFLOW_ID)).thenReturn(workflow);
    when(environmentService.get(APP_ID, ENV_ID)).thenReturn(Environment.Builder.anEnvironment().uuid(ENV_ID).build());
    ArgumentCaptor<ExecutionArgs> captor = ArgumentCaptor.forClass(ExecutionArgs.class);

    when(workflowExecutionService.triggerEnvExecution(eq(APP_ID), eq(ENV_ID), captor.capture(), eq(null)))
        .thenReturn(WorkflowExecution.builder().uuid(WORKFLOW_EXECUTION_ID).status(ExecutionStatus.RUNNING).build());
    when(workflowService.fetchDeploymentMetadata(
             eq(APP_ID), eq(workflow), any(), eq(null), eq(null), eq(DeploymentMetadata.Include.ARTIFACT_SERVICE)))
        .thenReturn(DeploymentMetadata.builder().build());

    QLStartExecutionPayload startExecutionPayload =
        workflowExecutionController.startWorkflowExecution(qlStartExecutionInput, MutationContext.builder().build());
    assertThat(startExecutionPayload).isNotNull();
    assertThat(startExecutionPayload.getExecution().getStatus()).isEqualTo(QLExecutionStatus.RUNNING);
    assertThat(startExecutionPayload.getWarningMessage()).isNotBlank();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldPopulateArtifactAndManifestNeededServiceIds() {
    QLServiceInputsForExecutionParams params = QLServiceInputsForExecutionParams.builder()
                                                   .executionType(QLExecutionType.WORKFLOW)
                                                   .applicationId(APP_ID)
                                                   .entityId(WORKFLOW_ID)
                                                   .build();
    Workflow workflow = getWorkflow();
    when(workflowService.readWorkflow(APP_ID, WORKFLOW_ID)).thenReturn(workflow);
    when(workflowService.fetchDeploymentMetadata(eq(APP_ID), eq(workflow), any(), eq(null), eq(null), eq(false),
             eq(null), eq(DeploymentMetadata.Include.ARTIFACT_SERVICE)))
        .thenReturn(DeploymentMetadata.builder()
                        .artifactRequiredServiceIds(Arrays.asList(SERVICE_ID, SERVICE_ID + 2))
                        .manifestRequiredServiceIds(Arrays.asList(SERVICE_ID, SERVICE_ID + 3))
                        .build());
    Map<String, List<String>> requiredServiceMap =
        workflowExecutionController.getArtifactAndManifestNeededServices(params);
    assertThat(requiredServiceMap).isNotNull();
    assertThat(requiredServiceMap.get(DeploymentMetadataKeys.artifactRequiredServices))
        .containsExactlyInAnyOrder(SERVICE_ID, SERVICE_ID + 2);
    assertThat(requiredServiceMap.get(DeploymentMetadataKeys.manifestRequiredServiceIds))
        .containsExactlyInAnyOrder(SERVICE_ID, SERVICE_ID + 3);
  }

  @NotNull
  private Workflow getWorkflow() {
    return aWorkflow()
        .uuid(WORKFLOW_ID)
        .appId(APP_ID)
        .orchestrationWorkflow(Mockito.mock(CanaryOrchestrationWorkflow.class))
        .build();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldStartWorkflowWithManifest() {
    QLStartExecutionInput qlStartExecutionInput = getStartExecutionInputWithManifest();
    Workflow workflow = getWorkflow();
    when(workflowService.readWorkflow(APP_ID, WORKFLOW_ID)).thenReturn(workflow);
    ArgumentCaptor<ExecutionArgs> captor = ArgumentCaptor.forClass(ExecutionArgs.class);

    when(workflowExecutionService.triggerEnvExecution(eq(APP_ID), eq(null), captor.capture(), eq(null)))
        .thenReturn(WorkflowExecution.builder().uuid(WORKFLOW_EXECUTION_ID).status(ExecutionStatus.RUNNING).build());
    when(workflowService.fetchDeploymentMetadata(
             eq(APP_ID), eq(workflow), any(), eq(null), eq(null), eq(DeploymentMetadata.Include.ARTIFACT_SERVICE)))
        .thenReturn(
            DeploymentMetadata.builder().manifestRequiredServiceIds(Arrays.asList(SERVICE_ID, SERVICE_ID + 2)).build());
    when(serviceResourceService.get(eq(APP_ID), anyString()))
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
        workflowExecutionController.startWorkflowExecution(qlStartExecutionInput, MutationContext.builder().build());
    assertThat(startExecutionPayload).isNotNull();
    assertThat(startExecutionPayload.getExecution().getStatus()).isEqualTo(QLExecutionStatus.RUNNING);
    assertThat(captor.getValue().getHelmCharts().stream().map(HelmChart::getUuid).collect(Collectors.toList()))
        .containsExactlyInAnyOrder(HELM_CHART_ID, HELM_CHART_ID + 2);

    when(featureFlagService.isEnabled(FeatureName.BYPASS_HELM_FETCH, null)).thenReturn(true);
    when(helmChartService.getByChartVersion(APP_ID, SERVICE_ID, APP_MANIFEST_NAME, "1.0")).thenReturn(null);
    workflowExecutionController.startWorkflowExecution(qlStartExecutionInput, MutationContext.builder().build());
    verify(helmChartService).createHelmChartWithVersionForAppManifest(any(), eq("1.0"));
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldStartWorkflowWithoutManifest() {
    QLStartExecutionInput qlStartExecutionInput = getStartExecutionInputWithoutManifest();
    Workflow workflow = getWorkflow();
    when(workflowService.readWorkflow(APP_ID, WORKFLOW_ID)).thenReturn(workflow);
    ArgumentCaptor<ExecutionArgs> captor = ArgumentCaptor.forClass(ExecutionArgs.class);

    when(workflowExecutionService.triggerEnvExecution(eq(APP_ID), eq(null), captor.capture(), eq(null)))
        .thenReturn(WorkflowExecution.builder().uuid(WORKFLOW_EXECUTION_ID).status(ExecutionStatus.RUNNING).build());
    when(workflowService.fetchDeploymentMetadata(
             eq(APP_ID), eq(workflow), any(), eq(null), eq(null), eq(DeploymentMetadata.Include.ARTIFACT_SERVICE)))
        .thenReturn(DeploymentMetadata.builder().build());

    QLStartExecutionPayload startExecutionPayload =
        workflowExecutionController.startWorkflowExecution(qlStartExecutionInput, MutationContext.builder().build());
    assertThat(startExecutionPayload).isNotNull();
    assertThat(startExecutionPayload.getExecution().getStatus()).isEqualTo(QLExecutionStatus.RUNNING);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldThrowErrorForManifestMissing() {
    QLStartExecutionInput qlStartExecutionInput = getStartExecutionInputWithManifest();
    Workflow workflow = getWorkflow();
    when(workflowService.readWorkflow(APP_ID, WORKFLOW_ID)).thenReturn(workflow);
    ArgumentCaptor<ExecutionArgs> captor = ArgumentCaptor.forClass(ExecutionArgs.class);

    when(workflowExecutionService.triggerEnvExecution(eq(APP_ID), eq(null), captor.capture(), eq(null)))
        .thenReturn(WorkflowExecution.builder().uuid(WORKFLOW_EXECUTION_ID).status(ExecutionStatus.RUNNING).build());
    when(workflowService.fetchDeploymentMetadata(
             eq(APP_ID), eq(workflow), any(), eq(null), eq(null), eq(DeploymentMetadata.Include.ARTIFACT_SERVICE)))
        .thenReturn(DeploymentMetadata.builder()
                        .manifestRequiredServiceIds(Arrays.asList(SERVICE_ID + 3, SERVICE_ID + 2))
                        .build());

    when(serviceResourceService.get(eq(APP_ID), anyString()))
        .thenAnswer(invocationOnMock
            -> Service.builder()
                   .appId(APP_ID)
                   .name(invocationOnMock.getArgument(1, String.class))
                   .uuid(invocationOnMock.getArgument(1, String.class))
                   .build());
    assertThatThrownBy(()
                           -> workflowExecutionController.startWorkflowExecution(
                               qlStartExecutionInput, MutationContext.builder().build()))
        .isInstanceOf(GeneralException.class)
        .hasMessage("ServiceInput required for service: SERVICE_ID3");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldThrowErrorForInvalidHelmChart() {
    QLStartExecutionInput qlStartExecutionInput = getStartExecutionInputWithManifest();
    Workflow workflow = getWorkflow();
    when(workflowService.readWorkflow(APP_ID, WORKFLOW_ID)).thenReturn(workflow);
    ArgumentCaptor<ExecutionArgs> captor = ArgumentCaptor.forClass(ExecutionArgs.class);

    when(workflowExecutionService.triggerEnvExecution(eq(APP_ID), eq(null), captor.capture(), eq(null)))
        .thenReturn(WorkflowExecution.builder().uuid(WORKFLOW_EXECUTION_ID).status(ExecutionStatus.RUNNING).build());
    when(workflowService.fetchDeploymentMetadata(
             eq(APP_ID), eq(workflow), any(), eq(null), eq(null), eq(DeploymentMetadata.Include.ARTIFACT_SERVICE)))
        .thenReturn(
            DeploymentMetadata.builder().manifestRequiredServiceIds(Arrays.asList(SERVICE_ID, SERVICE_ID + 2)).build());

    when(serviceResourceService.get(eq(APP_ID), anyString()))
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
                           -> workflowExecutionController.startWorkflowExecution(
                               qlStartExecutionInput, MutationContext.builder().build()))
        .isInstanceOf(GeneralException.class)
        .hasMessage("Cannot find helm chart for specified version number: 1.0");

    when(helmChartService.get(APP_ID, HELM_CHART_ID + 2)).thenReturn(null);
    when(helmChartService.getByChartVersion(APP_ID, SERVICE_ID, APP_MANIFEST_NAME, "1.0"))
        .thenReturn(HelmChart.builder().uuid(HELM_CHART_ID).build());
    assertThatThrownBy(()
                           -> workflowExecutionController.startWorkflowExecution(
                               qlStartExecutionInput, MutationContext.builder().build()))
        .isInstanceOf(GeneralException.class)
        .hasMessage("Cannot find helm chart for specified Id: HELM_CHART_ID2. Might be deleted");
  }

  private QLStartExecutionInput getStartExecutionInputWithManifest() {
    return QLStartExecutionInput.builder()
        .executionType(QLExecutionType.WORKFLOW)
        .applicationId(APP_ID)
        .entityId(WORKFLOW_ID)
        .serviceInputs(Arrays.asList(QLServiceInput.builder()
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

  private QLStartExecutionInput getStartExecutionInputWithoutManifest() {
    return QLStartExecutionInput.builder()
        .executionType(QLExecutionType.WORKFLOW)
        .applicationId(APP_ID)
        .entityId(WORKFLOW_ID)
        .serviceInputs(
            Arrays.asList(QLServiceInput.builder()
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
}
