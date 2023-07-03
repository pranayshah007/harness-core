/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops.steps;

import static io.harness.cdng.gitops.constants.GitopsConstants.GITOPS_SWEEPING_OUTPUT;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EnvironmentType;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.gitops.beans.FetchLinkedAppsStepParams;
import io.harness.cdng.gitops.beans.GitOpsLinkedAppsOutcome;
import io.harness.cdng.manifest.yaml.DeploymentRepoManifestOutcome;
import io.harness.cdng.manifest.yaml.GithubStore;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.task.git.TaskStatus;
import io.harness.delegate.task.gitops.GitOpsFetchAppTaskResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.gitops.models.Application;
import io.harness.gitops.remote.GitopsResourceClient;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.ng.BaseUrls;
import io.harness.ng.beans.PageResponse;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.steps.StepHelper;
import io.harness.steps.StepUtils;
import io.harness.steps.TaskRequestsUtils;
import io.harness.supplier.ThrowingSupplier;

import software.wings.beans.TaskType;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import retrofit2.Call;
import retrofit2.Response;

@RunWith(MockitoJUnitRunner.class)
@PrepareForTest({StepUtils.class})
@OwnedBy(HarnessTeam.GITOPS)
public class FetchLinkedAppsStepTest extends CategoryTest {
  @Mock LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Mock ILogStreamingStepClient logStreamingStepClient;
  @Mock GitOpsStepHelper gitOpsStepHelper;
  @Mock CDStepHelper cdStepHelper;
  @Mock StepHelper stepHelper;
  @Mock ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock GitopsResourceClient gitopsResourceClient;
  @Mock BaseUrls baseUrls;
  @InjectMocks FetchLinkedAppsStep fetchLinkedAppsStep;
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldObtainTask() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    FetchLinkedAppsStepParams stepParams = FetchLinkedAppsStepParams.infoBuilder().build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(stepParams).build();
    doReturn(logStreamingStepClient).when(logStreamingStepClientFactory).getLogStreamingStepClient(ambiance);
    GithubStore githubStore = GithubStore.builder()
                                  .connectorRef(ParameterField.createValueField("connectorRef"))
                                  .branch(ParameterField.createValueField("branch"))
                                  .paths(ParameterField.createValueField(Collections.singletonList("path1")))
                                  .build();
    DeploymentRepoManifestOutcome manifestOutcome =
        DeploymentRepoManifestOutcome.builder().identifier("id").store(githubStore).build();
    doReturn(manifestOutcome).when(gitOpsStepHelper).getDeploymentRepoOutcome(ambiance);
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder().build();
    doReturn(connectorInfoDTO).when(cdStepHelper).getConnector("connectorRef", ambiance);
    GitStoreDelegateConfig gitStoreDelegateConfig = GitStoreDelegateConfig.builder().build();
    doReturn(gitStoreDelegateConfig)
        .when(cdStepHelper)
        .getGitStoreDelegateConfig(
            githubStore, connectorInfoDTO, manifestOutcome, Collections.singletonList("path"), ambiance);
    doReturn(EnvironmentType.PROD).when(stepHelper).getEnvironmentType(ambiance);
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);
    Mockito.mockStatic(TaskRequestsUtils.class);
    PowerMockito
        .when(TaskRequestsUtils.prepareCDTaskRequest(
            eq(ambiance), taskDataArgumentCaptor.capture(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());

    TaskRequest taskRequest = fetchLinkedAppsStep.obtainTaskAfterRbac(ambiance, stepElementParameters, null);
    assertThat(taskRequest).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getTaskType()).isEqualTo(TaskType.GITOPS_FETCH_APP_TASK.name());
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldThrowErrorIfDeploymentRepoOutcomeNotFound() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    FetchLinkedAppsStepParams stepParams = FetchLinkedAppsStepParams.infoBuilder().build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(stepParams).build();
    doReturn(logStreamingStepClient).when(logStreamingStepClientFactory).getLogStreamingStepClient(ambiance);
    doThrow(new InvalidRequestException("Not found")).when(gitOpsStepHelper).getDeploymentRepoOutcome(ambiance);
    Assertions.assertThatThrownBy(() -> fetchLinkedAppsStep.obtainTaskAfterRbac(ambiance, stepElementParameters, null))
        .hasMessageContaining("Not found");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldThrowErrorWhenTaskResponseStatusIsFail() throws Exception {
    Ambiance ambiance = Ambiance.newBuilder().build();
    FetchLinkedAppsStepParams stepParams = FetchLinkedAppsStepParams.infoBuilder().build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(stepParams).build();
    doReturn(logStreamingStepClient).when(logStreamingStepClientFactory).getLogStreamingStepClient(ambiance);
    GitOpsFetchAppTaskResponse taskResponse =
        GitOpsFetchAppTaskResponse.builder().taskStatus(TaskStatus.FAILURE).errorMessage("Task Failed").build();
    ThrowingSupplier<GitOpsFetchAppTaskResponse> throwingSupplier = () -> taskResponse;

    StepResponse stepResponse =
        fetchLinkedAppsStep.handleTaskResultWithSecurityContext(ambiance, stepElementParameters, throwingSupplier);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getFailureInfo()).isNotNull();
    assertThat(stepResponse.getFailureInfo().getFailureData(0)).isNotNull();
    assertThat(stepResponse.getFailureInfo().getFailureData(0).getMessage()).isEqualTo("Task Failed");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldThrowErrorWhenClusterOutcomeNotFound() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    FetchLinkedAppsStepParams stepParams = FetchLinkedAppsStepParams.infoBuilder().build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(stepParams).build();
    doReturn(logStreamingStepClient).when(logStreamingStepClientFactory).getLogStreamingStepClient(ambiance);
    GitOpsFetchAppTaskResponse taskResponse =
        GitOpsFetchAppTaskResponse.builder().taskStatus(TaskStatus.SUCCESS).build();
    ThrowingSupplier<GitOpsFetchAppTaskResponse> throwingSupplier = () -> taskResponse;
    doReturn(null)
        .when(executionSweepingOutputService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(GITOPS_SWEEPING_OUTPUT));

    Assertions
        .assertThatThrownBy(()
                                -> fetchLinkedAppsStep.handleTaskResultWithSecurityContext(
                                    ambiance, stepElementParameters, throwingSupplier))
        .hasMessageContaining("GitOps Clusters Outcome Not Found.");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldThrowErrorWhenGitOpsServiceThrowsError() throws IOException {
    Ambiance ambiance = Ambiance.newBuilder().build();
    FetchLinkedAppsStepParams stepParams = FetchLinkedAppsStepParams.infoBuilder().build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(stepParams).build();
    doReturn(logStreamingStepClient).when(logStreamingStepClientFactory).getLogStreamingStepClient(ambiance);
    GitOpsFetchAppTaskResponse taskResponse =
        GitOpsFetchAppTaskResponse.builder().taskStatus(TaskStatus.SUCCESS).build();
    ThrowingSupplier<GitOpsFetchAppTaskResponse> throwingSupplier = () -> taskResponse;
    GitopsClustersOutcome gitopsClustersOutcome = new GitopsClustersOutcome(Collections.singletonList(
        GitopsClustersOutcome.ClusterData.builder().clusterId("c1").scope("project").build()));
    doReturn(OptionalSweepingOutput.builder().found(true).output(gitopsClustersOutcome).build())
        .when(executionSweepingOutputService)
        .resolveOptional(any(), any());
    Call call = mock(Call.class);
    doReturn(call).when(gitopsResourceClient).listApps(any());
    doThrow(new IOException("Not Available")).when(call).execute();

    Assertions
        .assertThatThrownBy(()
                                -> fetchLinkedAppsStep.handleTaskResultWithSecurityContext(
                                    ambiance, stepElementParameters, throwingSupplier))
        .hasMessageContaining("Failed to retrieve Linked Apps from Gitops Service");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldSetOutcome() throws Exception {
    Ambiance ambiance = getAmbiance();
    FetchLinkedAppsStepParams stepParams = FetchLinkedAppsStepParams.infoBuilder().build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(stepParams).build();
    doReturn(logStreamingStepClient).when(logStreamingStepClientFactory).getLogStreamingStepClient(ambiance);
    GitOpsFetchAppTaskResponse taskResponse =
        GitOpsFetchAppTaskResponse.builder().taskStatus(TaskStatus.SUCCESS).build();
    ThrowingSupplier<GitOpsFetchAppTaskResponse> throwingSupplier = () -> taskResponse;
    GitopsClustersOutcome gitopsClustersOutcome = new GitopsClustersOutcome(Collections.singletonList(
        GitopsClustersOutcome.ClusterData.builder().clusterId("c1").scope("project").build()));
    doReturn(OptionalSweepingOutput.builder().found(true).output(gitopsClustersOutcome).build())
        .when(executionSweepingOutputService)
        .resolveOptional(any(), any());
    Call call = mock(Call.class);
    PageResponse pageResponse =
        PageResponse.builder()
            .content(Collections.singletonList(Application.builder().name("APP1").agentIdentifier("AGENT_ID").build()))
            .build();
    doReturn(call).when(gitopsResourceClient).listApps(any());
    doReturn(Response.success(pageResponse)).when(call).execute();
    doReturn("https://app.harness.io/ng#/").when(baseUrls).getNextGenUiUrl();

    StepResponse stepResponse =
        fetchLinkedAppsStep.handleTaskResultWithSecurityContext(ambiance, stepElementParameters, throwingSupplier);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    StepResponse.StepOutcome stepOutcome = (StepResponse.StepOutcome) ((List) stepResponse.getStepOutcomes()).get(0);
    assertThat(((GitOpsLinkedAppsOutcome) stepOutcome.getOutcome()).getApps()).hasSize(1);
    assertThat(((GitOpsLinkedAppsOutcome) stepOutcome.getOutcome()).getApps().get(0).getUrl())
        .isEqualTo(
            "https://app.harness.io/ng#/account/ACC_ID/cd/orgs/ORG_ID/projects/PROJ_ID/gitops/applications/APP1?agentId=AGENT_ID");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldNotSetOutcomeWhenNoAppsFound() throws Exception {
    Ambiance ambiance = Ambiance.newBuilder().build();
    FetchLinkedAppsStepParams stepParams = FetchLinkedAppsStepParams.infoBuilder().build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(stepParams).build();
    doReturn(logStreamingStepClient).when(logStreamingStepClientFactory).getLogStreamingStepClient(ambiance);
    GitOpsFetchAppTaskResponse taskResponse =
        GitOpsFetchAppTaskResponse.builder().taskStatus(TaskStatus.SUCCESS).build();
    ThrowingSupplier<GitOpsFetchAppTaskResponse> throwingSupplier = () -> taskResponse;
    GitopsClustersOutcome gitopsClustersOutcome = new GitopsClustersOutcome(Collections.singletonList(
        GitopsClustersOutcome.ClusterData.builder().clusterId("c1").scope("project").build()));
    doReturn(OptionalSweepingOutput.builder().found(true).output(gitopsClustersOutcome).build())
        .when(executionSweepingOutputService)
        .resolveOptional(any(), any());
    Call call = mock(Call.class);
    PageResponse pageResponse = PageResponse.builder().content(Collections.emptyList()).build();
    doReturn(call).when(gitopsResourceClient).listApps(any());
    doReturn(Response.success(pageResponse)).when(call).execute();

    StepResponse stepResponse =
        fetchLinkedAppsStep.handleTaskResultWithSecurityContext(ambiance, stepElementParameters, throwingSupplier);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes()).isEmpty();
  }

  private Ambiance getAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions("accountId", "ACC_ID")
        .putSetupAbstractions("orgIdentifier", "ORG_ID")
        .putSetupAbstractions("projectIdentifier", "PROJ_ID")
        .build();
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldReturnScopedClusterIds() {
    assertThat(fetchLinkedAppsStep.getScopedClusterIds(null)).isEmpty();
    assertThat(fetchLinkedAppsStep.getScopedClusterIds(new GitopsClustersOutcome(null))).isEmpty();

    GitopsClustersOutcome.ClusterData cluster1 =
        GitopsClustersOutcome.ClusterData.builder().clusterId("cid1").scope("project").build();
    GitopsClustersOutcome.ClusterData cluster2 =
        GitopsClustersOutcome.ClusterData.builder().clusterId("cid2").scope("account").build();
    GitopsClustersOutcome.ClusterData cluster3 =
        GitopsClustersOutcome.ClusterData.builder().clusterId("cid3").scope("ACCOUNT").build();
    GitopsClustersOutcome.ClusterData cluster4 =
        GitopsClustersOutcome.ClusterData.builder().clusterId("cid4").scope("ORGANIZATION").build();
    GitopsClustersOutcome.ClusterData cluster5 =
        GitopsClustersOutcome.ClusterData.builder().clusterId("cid5").scope("ORG").build();

    assertThat(fetchLinkedAppsStep.getScopedClusterIds(
                   new GitopsClustersOutcome(Arrays.asList(cluster1, cluster2, cluster3, cluster4, cluster5))))
        .isEqualTo(Arrays.asList("cid1", "account.cid2", "account.cid3", "org.cid4", "org.cid5"));
  }
}
