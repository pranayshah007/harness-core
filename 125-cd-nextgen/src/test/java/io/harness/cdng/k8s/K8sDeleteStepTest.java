/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.k8s;

import static io.harness.cdng.k8s.K8sDeleteStep.K8S_DELETE_COMMAND_NAME;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.ACHYUTH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.infra.beans.K8sDirectInfrastructureOutcome;
import io.harness.cdng.k8s.beans.GitFetchResponsePassThroughData;
import io.harness.cdng.k8s.beans.HelmValuesFetchResponsePassThroughData;
import io.harness.cdng.k8s.beans.K8sExecutionPassThroughData;
import io.harness.cdng.k8s.beans.StepExceptionPassThroughData;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.task.k8s.DeleteResourcesType;
import io.harness.delegate.task.k8s.K8sDeleteRequest;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.delegate.task.k8s.K8sTaskType;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.Arrays;
import lombok.SneakyThrows;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CDP)
public class K8sDeleteStepTest extends AbstractK8sStepExecutorTestBase {
  @Mock private OutcomeService outcomeService;
  @InjectMocks private K8sDeleteStep deleteStep;

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testObtainTaskWithResourceName() {
    DeleteResourceNameSpec spec = new DeleteResourceNameSpec();
    spec.setResourceNames(ParameterField.createValueField(Arrays.asList(
        "Deployment/test-delete-resource-name-deployment", "ConfigMap/test-delete-resource-name-config")));

    final K8sDeleteStepParameters stepParameters =
        K8sDeleteStepParameters.infoBuilder()
            .deleteResources(DeleteResourcesWrapper.builder()
                                 .spec(spec)
                                 .type(io.harness.delegate.task.k8s.DeleteResourcesType.ResourceName)
                                 .build())
            .build();
    final StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(stepParameters).timeout(ParameterField.createValueField("10m")).build();

    doReturn("test-delete-resource-name-release").when(cdStepHelper).getReleaseName(ambiance, infrastructureOutcome);
    K8sDeleteRequest deleteRequest = executeTask(stepElementParameters, K8sDeleteRequest.class);
    assertThat(deleteRequest).isNotNull();
    assertThat(deleteRequest.getCommandName()).isEqualTo(K8S_DELETE_COMMAND_NAME);
    assertThat(deleteRequest.getTaskType()).isEqualTo(K8sTaskType.DELETE);
    assertThat(deleteRequest.getReleaseName()).isEqualTo("test-delete-resource-name-release");
    assertThat(deleteRequest.getFilePaths()).isEmpty();
    assertThat(deleteRequest.getResources())
        .isEqualTo("Deployment/test-delete-resource-name-deployment,ConfigMap/test-delete-resource-name-config");
    assertThat(deleteRequest.isDeleteNamespacesForRelease()).isEqualTo(false);
    assertThat(deleteRequest.getTimeoutIntervalInMin()).isEqualTo(10);
    assertThat(deleteRequest.getK8sInfraDelegateConfig()).isEqualTo(infraDelegateConfig);

    ArgumentCaptor<String> releaseNameCaptor = ArgumentCaptor.forClass(String.class);
    verify(k8sStepHelper, times(1)).publishReleaseNameStepDetails(eq(ambiance), releaseNameCaptor.capture());
    assertThat(releaseNameCaptor.getValue()).isEqualTo("test-delete-resource-name-release");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testObtainTaskWithManifestPath() {
    DeleteManifestPathSpec spec = new DeleteManifestPathSpec();
    spec.setManifestPaths(ParameterField.createValueField(Arrays.asList("deployment.yaml", "config.yaml")));
    spec.setAllManifestPaths(ParameterField.createValueField(false));

    final K8sDeleteStepParameters stepParameters =
        K8sDeleteStepParameters.infoBuilder()
            .deleteResources(DeleteResourcesWrapper.builder()
                                 .spec(spec)
                                 .type(io.harness.delegate.task.k8s.DeleteResourcesType.ManifestPath)
                                 .build())
            .build();
    final StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(stepParameters).timeout(ParameterField.createValueField("10m")).build();

    doReturn("test-delete-manifest-file-release").when(cdStepHelper).getReleaseName(ambiance, infrastructureOutcome);

    K8sDeleteRequest deleteRequest = executeTask(stepElementParameters, K8sDeleteRequest.class);
    assertThat(deleteRequest).isNotNull();
    assertThat(deleteRequest.getCommandName()).isEqualTo(K8S_DELETE_COMMAND_NAME);
    assertThat(deleteRequest.getTaskType()).isEqualTo(K8sTaskType.DELETE);
    assertThat(deleteRequest.getReleaseName()).isEqualTo("test-delete-manifest-file-release");
    assertThat(deleteRequest.getFilePaths()).isEqualTo("deployment.yaml,config.yaml");
    assertThat(deleteRequest.getResources()).isEmpty();
    assertThat(deleteRequest.isDeleteNamespacesForRelease()).isEqualTo(false);
    assertThat(deleteRequest.getTimeoutIntervalInMin()).isEqualTo(10);
    assertThat(deleteRequest.getK8sInfraDelegateConfig()).isEqualTo(infraDelegateConfig);

    ArgumentCaptor<String> releaseNameCaptor = ArgumentCaptor.forClass(String.class);
    verify(k8sStepHelper, times(1)).publishReleaseNameStepDetails(eq(ambiance), releaseNameCaptor.capture());
    assertThat(releaseNameCaptor.getValue()).isEqualTo("test-delete-manifest-file-release");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testObtainTaskWithReleaseName() {
    DeleteReleaseNameSpec spec = new DeleteReleaseNameSpec();
    spec.setDeleteNamespace(ParameterField.createValueField(true));

    final K8sDeleteStepParameters stepParameters =
        K8sDeleteStepParameters.infoBuilder()
            .deleteResources(DeleteResourcesWrapper.builder().spec(spec).type(DeleteResourcesType.ReleaseName).build())
            .build();
    final StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(stepParameters).timeout(ParameterField.createValueField("10m")).build();

    doReturn("test-delete-release-name-release").when(cdStepHelper).getReleaseName(ambiance, infrastructureOutcome);

    K8sDeleteRequest deleteRequest = executeTask(stepElementParameters, K8sDeleteRequest.class);
    assertThat(deleteRequest).isNotNull();
    assertThat(deleteRequest.getCommandName()).isEqualTo(K8S_DELETE_COMMAND_NAME);
    assertThat(deleteRequest.getTaskType()).isEqualTo(K8sTaskType.DELETE);
    assertThat(deleteRequest.getReleaseName()).isEqualTo("test-delete-release-name-release");
    assertThat(deleteRequest.getFilePaths()).isEmpty();
    assertThat(deleteRequest.getResources()).isEmpty();
    assertThat(deleteRequest.isDeleteNamespacesForRelease()).isEqualTo(true);
    assertThat(deleteRequest.getTimeoutIntervalInMin()).isEqualTo(10);
    assertThat(deleteRequest.getK8sInfraDelegateConfig()).isEqualTo(infraDelegateConfig);

    ArgumentCaptor<String> releaseNameCaptor = ArgumentCaptor.forClass(String.class);
    verify(k8sStepHelper, times(1)).publishReleaseNameStepDetails(eq(ambiance), releaseNameCaptor.capture());
    assertThat(releaseNameCaptor.getValue()).isEqualTo("test-delete-release-name-release");
  }

  @SneakyThrows
  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testHandleTaskResultSucceeded() {
    K8sDeleteStepParameters stepParameters = K8sDeleteStepParameters.infoBuilder().build();
    final StepElementParameters stepElementParameters = StepElementParameters.builder().spec(stepParameters).build();

    K8sDeployResponse k8sDeployResponse = K8sDeployResponse.builder()
                                              .commandExecutionStatus(SUCCESS)
                                              .commandUnitsProgress(UnitProgressData.builder().build())
                                              .build();

    StepResponse response =
        deleteStep.finalizeExecutionWithSecurityContext(ambiance, stepElementParameters, null, () -> k8sDeployResponse);
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @SneakyThrows
  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testHandleTaskResultFailed() {
    K8sDeleteStepParameters stepParameters = K8sDeleteStepParameters.infoBuilder().build();
    final StepElementParameters stepElementParameters = StepElementParameters.builder().spec(stepParameters).build();

    K8sDeployResponse k8sDeployResponse = K8sDeployResponse.builder()
                                              .errorMessage("Execution failed.")
                                              .commandExecutionStatus(FAILURE)
                                              .commandUnitsProgress(UnitProgressData.builder().build())
                                              .build();

    StepResponse response =
        deleteStep.finalizeExecutionWithSecurityContext(ambiance, stepElementParameters, null, () -> k8sDeployResponse);
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
    assertThat(response.getFailureInfo().getErrorMessage()).isEqualTo("Execution failed.");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHandleTaskResultPassThroughFailed() throws Exception {
    final K8sDeleteStepParameters stepParameters = K8sDeleteStepParameters.infoBuilder().build();
    final StepElementParameters stepElementParameters = StepElementParameters.builder().spec(stepParameters).build();
    final GitFetchResponsePassThroughData gitFetchPassThroughData = GitFetchResponsePassThroughData.builder().build();
    final HelmValuesFetchResponsePassThroughData helmValuesPassThroughData =
        HelmValuesFetchResponsePassThroughData.builder().build();
    final StepExceptionPassThroughData stepExceptionPassThroughData = StepExceptionPassThroughData.builder().build();
    final StepResponse gitFetchValuesFailed = StepResponse.builder().status(Status.FAILED).build();
    final StepResponse helmFetchValuesFailed = StepResponse.builder().status(Status.FAILED).build();
    final StepResponse stepException = StepResponse.builder().status(Status.FAILED).build();

    doReturn(gitFetchValuesFailed).when(cdStepHelper).handleGitTaskFailure(gitFetchPassThroughData);
    doReturn(helmFetchValuesFailed).when(k8sStepHelper).handleHelmValuesFetchFailure(helmValuesPassThroughData);
    doReturn(stepException).when(cdStepHelper).handleStepExceptionFailure(stepExceptionPassThroughData);

    assertThat(deleteStep.finalizeExecutionWithSecurityContext(
                   ambiance, stepElementParameters, gitFetchPassThroughData, () -> null))
        .isSameAs(gitFetchValuesFailed);

    assertThat(deleteStep.finalizeExecutionWithSecurityContext(
                   ambiance, stepElementParameters, helmValuesPassThroughData, () -> null))
        .isSameAs(helmFetchValuesFailed);

    assertThat(deleteStep.finalizeExecutionWithSecurityContext(
                   ambiance, stepElementParameters, stepExceptionPassThroughData, () -> null))
        .isSameAs(stepException);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testValidateK8sDeleteStepParams() {
    K8sDeleteStepParameters deleteStepParameters = K8sDeleteStepParameters.infoBuilder().build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(deleteStepParameters).build();

    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    assertThatThrownBy(() -> deleteStep.startChainLink(ambiance, stepElementParameters, stepInputPackage))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("DeleteResources is mandatory");

    deleteStepParameters.setDeleteResources(DeleteResourcesWrapper.builder().build());
    assertThatThrownBy(() -> deleteStep.startChainLink(ambiance, stepElementParameters, stepInputPackage))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("DeleteResources type is mandatory");

    deleteStepParameters.setDeleteResources(
        DeleteResourcesWrapper.builder().type(io.harness.delegate.task.k8s.DeleteResourcesType.ManifestPath).build());
    assertThatThrownBy(() -> deleteStep.startChainLink(ambiance, stepElementParameters, stepInputPackage))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("DeleteResources spec is mandatory");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testGetK8sDeleteStepParameter() {
    assertThat(deleteStep.getStepParametersClass()).isEqualTo(StepElementParameters.class);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFinalizeExecutionException() throws Exception {
    final StepElementParameters stepElementParameters = StepElementParameters.builder().build();
    final Exception thrownException = new GeneralException("Something went wrong");
    final K8sExecutionPassThroughData executionPassThroughData = K8sExecutionPassThroughData.builder().build();
    final StepResponse stepResponse = StepResponse.builder().status(Status.FAILED).build();

    doReturn(stepResponse).when(k8sStepHelper).handleTaskException(ambiance, executionPassThroughData, thrownException);

    StepResponse response = deleteStep.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, executionPassThroughData, () -> { throw thrownException; });

    assertThat(response).isEqualTo(stepResponse);

    verify(k8sStepHelper, times(1)).handleTaskException(ambiance, executionPassThroughData, thrownException);
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testStartChainLinkAfterRbac() {
    final StepElementParameters stepElementParameters =
        StepElementParameters.builder()
            .type("Release Name")
            .spec(K8sDeleteStepParameters.infoBuilder()
                      .deleteResources(DeleteResourcesWrapper.builder()
                                           .type(DeleteResourcesType.ReleaseName)
                                           .spec(new DeleteReleaseNameSpec())
                                           .build())
                      .build())
            .build();
    when(outcomeService.resolve(any(), any())).thenReturn(K8sDirectInfrastructureOutcome.builder().build());
    assertThatCode(
        () -> deleteStep.startChainLinkAfterRbac(ambiance, stepElementParameters, StepInputPackage.builder().build()));
    verify(outcomeService, times(1)).resolve(any(), any());
  }

  @Override
  protected K8sStepExecutor getK8sStepExecutor() {
    return deleteStep;
  }
}
