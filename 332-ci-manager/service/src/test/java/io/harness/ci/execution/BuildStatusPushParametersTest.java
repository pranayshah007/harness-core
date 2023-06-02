/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.delegate.beans.connector.ConnectorType.AZURE_REPO;
import static io.harness.delegate.beans.connector.ConnectorType.GITHUB;
import static io.harness.delegate.beans.connector.scm.GitConnectionType.ACCOUNT;
import static io.harness.delegate.beans.connector.scm.GitConnectionType.REPO;
import static io.harness.rule.OwnerRule.DEV_MITTAL;
import static io.harness.rule.OwnerRule.JAMIE;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.PipelineUtils;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.build.BuildStatusUpdateParameter;
import io.harness.category.element.UnitTests;
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.ci.executionplan.CIExecutionTestBase;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectionTypeDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.task.ci.CIBuildStatusPushParameters;
import io.harness.delegate.task.ci.GitSCMType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.steps.StepType;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import java.io.IOException;
import org.apache.groovy.util.Maps;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(CI)
public class BuildStatusPushParametersTest extends CIExecutionTestBase {
  private static final String SOME_URL = "https://url.com/owner/repo.git";
  private static final String VANITY_URL = "https://vanity.harness.io";

  @Mock AccountClient accountClient;
  @Mock private ConnectorUtils connectorUtils;
  @Mock GithubConnectorDTO gitConfigDTO;
  @Mock AzureRepoConnectorDTO azureGitConfigDTO;
  @Mock private PipelineUtils pipelineUtils;
  @Mock private ConnectorDetails connectorDetails;
  @InjectMocks private GitBuildStatusUtility gitBuildStatusUtility;
  private Ambiance ambiance = Ambiance.newBuilder()
                                  .putAllSetupAbstractions(Maps.of("accountId", "accountId", "projectIdentifier",
                                      "projectIdentfier", "orgIdentifier", "orgIdentifier"))
                                  .addLevels(Level.newBuilder()
                                                 .setSetupId("setupId")
                                                 .setStepType(StepType.newBuilder().setStepCategoryValue(2).build())
                                                 .build())
                                  .setStageExecutionId("stageExecId")
                                  .build();

  @Before
  public void setup() {
    on(gitBuildStatusUtility).set("ngBaseUrl", "https://app.harness.io/ng/#");
  }

  @Test
  @Owner(developers = JAMIE)
  @Category(UnitTests.class)
  public void testIdentifierGeneration() throws IOException {
    prepareRepoLevelConnector(SOME_URL, null);
    ExecutionMetadata executionMetadata = ExecutionMetadata.newBuilder()
                                              .setExecutionUuid("executionuuid")
                                              .setPipelineIdentifier("shortPipelineId")
                                              .build();
    BuildStatusUpdateParameter buildStatusUpdateParameter =
        getBuildStatusUpdateParameter("shortIdentifier", "shortname");

    CIBuildStatusPushParameters pushParameters = gitBuildStatusUtility.getCIBuildStatusPushParams(
        Ambiance.newBuilder(ambiance).setMetadata(executionMetadata).build(), buildStatusUpdateParameter,
        Status.SUCCEEDED, "sha");

    assertThat(pushParameters.getDesc())
        .isEqualTo("Execution status of Pipeline - shortPipelineId (executionuuid) Stage - shortname was SUCCEEDED");

    assertThat(pushParameters.getIdentifier()).isEqualTo("shortPipelineId-shortIdentifier");
  }

  @Test
  @Owner(developers = JAMIE)
  @Category(UnitTests.class)
  public void testRepoNameGeneration() throws IOException {
    prepareAccountLevelConnector("https://github.com/", null);
    ExecutionMetadata executionMetadata =
        ExecutionMetadata.newBuilder().setExecutionUuid("executionuuid").setPipelineIdentifier("pipelineId").build();
    BuildStatusUpdateParameter buildStatusUpdateParameter =
        getBuildStatusUpdateParameter("shortIdentifier", "shortname", "wings-software/jhttp");

    CIBuildStatusPushParameters pushParameters = gitBuildStatusUtility.getCIBuildStatusPushParams(
        Ambiance.newBuilder(ambiance).setMetadata(executionMetadata).build(), buildStatusUpdateParameter,
        Status.SUCCEEDED, "sha");

    assertThat(pushParameters.getDesc())
        .isEqualTo("Execution status of Pipeline - pipelineId (executionuuid) Stage - shortname was SUCCEEDED");

    assertThat(pushParameters.getIdentifier()).isEqualTo("pipelineId-shortIdentifier");
    assertThat(pushParameters.getOwner()).isEqualTo("wings-software");
    assertThat(pushParameters.getRepo()).isEqualTo("jhttp");
  }

  @Test
  @Owner(developers = JAMIE)
  @Category(UnitTests.class)
  public void testIdentifierGenerationLongName() throws IOException {
    prepareRepoLevelConnector(SOME_URL, null);
    ExecutionMetadata executionMetadata =
        ExecutionMetadata.newBuilder()
            .setExecutionUuid("executionuuid")
            .setPipelineIdentifier(
                "longlonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglongPipline")
            .build();
    BuildStatusUpdateParameter buildStatusUpdateParameter = getBuildStatusUpdateParameter(
        "longlonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglongId",
        "longlonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglongName");

    CIBuildStatusPushParameters pushParameters = gitBuildStatusUtility.getCIBuildStatusPushParams(
        Ambiance.newBuilder(ambiance).setMetadata(executionMetadata).build(), buildStatusUpdateParameter,
        Status.SUCCEEDED, "sha");

    assertThat(pushParameters.getDesc())
        .isEqualTo(
            "Execution status of Pipeline - longlonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglongPipline (executionuuid) Stage - longlonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglongName was SUCCEEDED");

    assertThat(pushParameters.getIdentifier())
        .isEqualTo("longlonglonglonglonglonglon...-longlonglonglonglonglonglon...");
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testIdentifierGenerationBBSaas() throws IOException {
    prepareRepoLevelConnector("https://bitbucket.org/invastsecjp/sumo-report-batch-worker", null);
    ExecutionMetadata executionMetadata =
        ExecutionMetadata.newBuilder()
            .setExecutionUuid("executionuuid")
            .setPipelineIdentifier(
                "longlonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglongPipline")
            .build();
    BuildStatusUpdateParameter buildStatusUpdateParameter = getBuildStatusUpdateParameter(
        "longlonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglongId",
        "longlonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglongName");

    CIBuildStatusPushParameters pushParameters = gitBuildStatusUtility.getCIBuildStatusPushParams(
        Ambiance.newBuilder(ambiance).setMetadata(executionMetadata).build(), buildStatusUpdateParameter,
        Status.SUCCEEDED, "sha");

    assertThat(pushParameters.getDesc())
        .isEqualTo(
            "Execution status of Pipeline - longlonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglongPipline (executionuuid) Stage - longlonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglongName was SUCCEEDED");

    assertThat(pushParameters.getIdentifier()).isEqualTo("longlonglonglong...-longlonglonglong...");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetBuildDetailsUrlWithoutVanityUrl() throws IOException {
    prepareRepoLevelConnector(SOME_URL, null);
    when(pipelineUtils.getBuildDetailsUrl(any(), any(), any(), any(), any(), any())).thenCallRealMethod();

    ExecutionMetadata executionMetadata = ExecutionMetadata.newBuilder()
                                              .setExecutionUuid("executionuuid")
                                              .setPipelineIdentifier("shortPipelineId")
                                              .build();
    BuildStatusUpdateParameter buildStatusUpdateParameter =
        getBuildStatusUpdateParameter("shortIdentifier", "shortname");

    CIBuildStatusPushParameters pushParameters = gitBuildStatusUtility.getCIBuildStatusPushParams(
        Ambiance.newBuilder(ambiance).setMetadata(executionMetadata).build(), buildStatusUpdateParameter,
        Status.SUCCEEDED, "sha");

    assertThat(pushParameters.getDetailsUrl())
        .isEqualTo(
            "https://app.harness.io/ng/#/account/accountId/ci/orgs/orgIdentifier/projects/projectIdentfier/pipelines/shortPipelineId/executions/executionuuid/pipeline?stage=setupId&stageExecId=stageExecId");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetBuildDetailsUrlWithVanityUrl() throws IOException {
    prepareRepoLevelConnector(SOME_URL, VANITY_URL);
    when(pipelineUtils.getBuildDetailsUrl(any(), any(), any(), any(), any(), any())).thenCallRealMethod();

    ExecutionMetadata executionMetadata = ExecutionMetadata.newBuilder()
                                              .setExecutionUuid("executionuuid")
                                              .setPipelineIdentifier("shortPipelineId")
                                              .build();
    BuildStatusUpdateParameter buildStatusUpdateParameter =
        getBuildStatusUpdateParameter("shortIdentifier", "shortname");

    CIBuildStatusPushParameters pushParameters = gitBuildStatusUtility.getCIBuildStatusPushParams(
        Ambiance.newBuilder(ambiance).setMetadata(executionMetadata).build(), buildStatusUpdateParameter,
        Status.SUCCEEDED, "sha");

    assertThat(pushParameters.getDetailsUrl())
        .isEqualTo(
            "https://vanity.harness.io/ng/#/account/accountId/ci/orgs/orgIdentifier/projects/projectIdentfier/pipelines/shortPipelineId/executions/executionuuid/pipeline?stage=setupId&stageExecId=stageExecId");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testOwnerRepoNameForProjectLevelHttpAzureRepoConnector() throws IOException {
    String url = "https://dev.azure.com/org/project/";
    prepareAccountLevelConnector(url, null);

    when(connectorDetails.getConnectorType()).thenReturn(AZURE_REPO);
    when(connectorDetails.getConnectorConfig()).thenReturn(azureGitConfigDTO);
    when(azureGitConfigDTO.getUrl()).thenReturn(url);
    when(azureGitConfigDTO.getConnectorType()).thenReturn(AZURE_REPO);
    when(azureGitConfigDTO.getConnectionType()).thenReturn(AzureRepoConnectionTypeDTO.PROJECT);

    ExecutionMetadata executionMetadata =
        ExecutionMetadata.newBuilder().setExecutionUuid("executionuuid").setPipelineIdentifier("pipelineId").build();
    BuildStatusUpdateParameter buildStatusUpdateParameter =
        getBuildStatusUpdateParameter("shortIdentifier", "shortname", "repo");

    CIBuildStatusPushParameters pushParameters = gitBuildStatusUtility.getCIBuildStatusPushParams(
        Ambiance.newBuilder(ambiance).setMetadata(executionMetadata).build(), buildStatusUpdateParameter,
        Status.SUCCEEDED, "sha");

    assertThat(pushParameters.getOwner()).isEqualTo("org");
    assertThat(pushParameters.getRepo()).isEqualTo("project/_git/repo");
    assertThat(pushParameters.getGitSCMType()).isEqualTo(GitSCMType.AZURE_REPO);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testOwnerRepoNameForRepoLevelHttpAzureRepoConnector() throws IOException {
    String url = "https://dev.azure.com/org/project/_git/repo";
    prepareAccountLevelConnector(url, null);

    when(connectorDetails.getConnectorType()).thenReturn(AZURE_REPO);
    when(connectorDetails.getConnectorConfig()).thenReturn(azureGitConfigDTO);
    when(azureGitConfigDTO.getUrl()).thenReturn(url);
    when(azureGitConfigDTO.getConnectionType()).thenReturn(AzureRepoConnectionTypeDTO.REPO);

    ExecutionMetadata executionMetadata =
        ExecutionMetadata.newBuilder().setExecutionUuid("executionuuid").setPipelineIdentifier("pipelineId").build();
    BuildStatusUpdateParameter buildStatusUpdateParameter =
        getBuildStatusUpdateParameter("shortIdentifier", "shortname", null);

    CIBuildStatusPushParameters pushParameters = gitBuildStatusUtility.getCIBuildStatusPushParams(
        Ambiance.newBuilder(ambiance).setMetadata(executionMetadata).build(), buildStatusUpdateParameter,
        Status.SUCCEEDED, "sha");

    assertThat(pushParameters.getOwner()).isEqualTo("org");
    assertThat(pushParameters.getRepo()).isEqualTo("project/_git/repo");
    assertThat(pushParameters.getGitSCMType()).isEqualTo(GitSCMType.AZURE_REPO);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testOwnerRepoNameForProjectLevelSSHAzureRepoConnector() throws IOException {
    String url = "git@ssh.dev.azure.com:v3/org/project/";
    prepareAccountLevelConnector(url, null);

    when(connectorDetails.getConnectorType()).thenReturn(AZURE_REPO);
    when(connectorDetails.getConnectorConfig()).thenReturn(azureGitConfigDTO);
    when(azureGitConfigDTO.getUrl()).thenReturn(url);
    when(azureGitConfigDTO.getConnectorType()).thenReturn(AZURE_REPO);
    when(azureGitConfigDTO.getConnectionType()).thenReturn(AzureRepoConnectionTypeDTO.PROJECT);

    ExecutionMetadata executionMetadata =
        ExecutionMetadata.newBuilder().setExecutionUuid("executionuuid").setPipelineIdentifier("pipelineId").build();
    BuildStatusUpdateParameter buildStatusUpdateParameter =
        getBuildStatusUpdateParameter("shortIdentifier", "shortname", "repo");

    CIBuildStatusPushParameters pushParameters = gitBuildStatusUtility.getCIBuildStatusPushParams(
        Ambiance.newBuilder(ambiance).setMetadata(executionMetadata).build(), buildStatusUpdateParameter,
        Status.SUCCEEDED, "sha");

    assertThat(pushParameters.getOwner()).isEqualTo("org");
    assertThat(pushParameters.getRepo()).isEqualTo("project/repo");
    assertThat(pushParameters.getGitSCMType()).isEqualTo(GitSCMType.AZURE_REPO);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testOwnerRepoNameForRepoLevelSSHAzureRepoConnector() throws IOException {
    String url = "git@ssh.dev.azure.com:v3/org/project/repo";
    prepareAccountLevelConnector(url, null);

    when(connectorDetails.getConnectorType()).thenReturn(AZURE_REPO);
    when(connectorDetails.getConnectorConfig()).thenReturn(azureGitConfigDTO);
    when(azureGitConfigDTO.getUrl()).thenReturn(url);
    when(azureGitConfigDTO.getConnectionType()).thenReturn(AzureRepoConnectionTypeDTO.REPO);

    ExecutionMetadata executionMetadata =
        ExecutionMetadata.newBuilder().setExecutionUuid("executionuuid").setPipelineIdentifier("pipelineId").build();
    BuildStatusUpdateParameter buildStatusUpdateParameter =
        getBuildStatusUpdateParameter("shortIdentifier", "shortname", null);

    CIBuildStatusPushParameters pushParameters = gitBuildStatusUtility.getCIBuildStatusPushParams(
        Ambiance.newBuilder(ambiance).setMetadata(executionMetadata).build(), buildStatusUpdateParameter,
        Status.SUCCEEDED, "sha");

    assertThat(pushParameters.getOwner()).isEqualTo("org");
    assertThat(pushParameters.getRepo()).isEqualTo("project/repo");
    assertThat(pushParameters.getGitSCMType()).isEqualTo(GitSCMType.AZURE_REPO);
  }

  private BuildStatusUpdateParameter getBuildStatusUpdateParameter(String identifier, String name) {
    return BuildStatusUpdateParameter.builder().identifier(identifier).buildNumber("0").desc("desc").name(name).build();
  }

  private BuildStatusUpdateParameter getBuildStatusUpdateParameter(String identifier, String name, String repo) {
    return BuildStatusUpdateParameter.builder()
        .connectorIdentifier("testConnector")
        .repoName(repo)
        .identifier(identifier)
        .buildNumber("0")
        .desc("desc")
        .name(name)
        .build();
  }

  private void prepareAccountLevelConnector(String url, String vanityUrl) throws IOException {
    when(connectorUtils.getConnectorDetails(any(), any(), eq(true))).thenReturn(connectorDetails);
    when(connectorDetails.getConnectorType()).thenReturn(GITHUB);
    when(connectorDetails.getConnectorConfig()).thenReturn(gitConfigDTO);
    when(gitConfigDTO.getUrl()).thenReturn(url);
    when(gitConfigDTO.getConnectionType()).thenReturn(ACCOUNT);
    when(pipelineUtils.getBuildDetailsUrl(any(), any(), any(), any(), any(), any())).thenReturn(SOME_URL);

    Call vanityUrlCall = mock(Call.class);
    when(vanityUrlCall.execute()).thenReturn(Response.success(new RestResponse<>(vanityUrl)));
    when(accountClient.getVanityUrl(anyString())).thenReturn(vanityUrlCall);
  }

  private void prepareRepoLevelConnector(String url, String vanityUrl) throws IOException {
    when(connectorUtils.getConnectorDetails(any(), any(), eq(true))).thenReturn(connectorDetails);
    when(connectorDetails.getConnectorType()).thenReturn(GITHUB);
    when(connectorDetails.getConnectorConfig()).thenReturn(gitConfigDTO);
    when(gitConfigDTO.getUrl()).thenReturn(url);
    when(gitConfigDTO.getConnectionType()).thenReturn(REPO);
    when(pipelineUtils.getBuildDetailsUrl(any(), any(), any(), any(), any(), any())).thenReturn(SOME_URL);

    Call vanityUrlCall = mock(Call.class);
    when(vanityUrlCall.execute()).thenReturn(Response.success(new RestResponse<>(vanityUrl)));
    when(accountClient.getVanityUrl(anyString())).thenReturn(vanityUrlCall);
  }
}
