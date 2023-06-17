/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.stateutils.buildstate;

import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_BUILD_LINK;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_COMMIT_MESSAGE;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_COMMIT_REF;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_COMMIT_SHA;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_NETRC_MACHINE;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_PULL_REQUEST_TITLE;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_REMOTE_URL;
import static io.harness.rule.OwnerRule.JAMES_RICKS;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.beans.sweepingoutputs.CodebaseSweepingOutput;
import io.harness.category.element.UnitTests;
import io.harness.ci.buildstate.CodebaseUtils;
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.ci.execution.GitBuildStatusUtility;
import io.harness.ci.executionplan.CIExecutionPlanTestHelper;
import io.harness.ci.executionplan.CIExecutionTestBase;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectionTypeDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.yaml.extended.ci.codebase.CodeBase;

import com.google.inject.Inject;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;

public class CodebaseUtilsTest extends CIExecutionTestBase {
  @Inject private CIExecutionPlanTestHelper ciExecutionPlanTestHelper;
  @Inject public CodebaseUtils codebaseUtils;
  @Mock private ConnectorUtils connectorUtils;
  @Mock private GitBuildStatusUtility gitBuildStatusUtility;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  private Ambiance ambiance;

  @Before
  public void setUp() {
    on(codebaseUtils).set("connectorUtils", connectorUtils);
    on(codebaseUtils).set("executionSweepingOutputResolver", executionSweepingOutputService);
    on(codebaseUtils).set("gitBuildStatusUtility", gitBuildStatusUtility);
    ambiance = Ambiance.newBuilder()
                   .putSetupAbstractions("accountId", "accountId")
                   .putSetupAbstractions("projectIdentifier", "projectId")
                   .putSetupAbstractions("orgIdentifier", "orgIdentifier")
                   .build();
    when(gitBuildStatusUtility.getBuildDetailsUrl(any())).thenReturn("url");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetCompleteUrlForHttpRepoConnector() {
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .connectorType(ConnectorType.GITHUB)
            .connectorConfig(GithubConnectorDTO.builder()
                                 .connectionType(GitConnectionType.REPO)
                                 .url("https://github.com/test/repo")
                                 .authentication(GithubAuthenticationDTO.builder().authType(GitAuthType.HTTP).build())
                                 .build())
            .build();

    String completeURL = CodebaseUtils.getCompleteURLFromConnector(connectorDetails, null);
    assertThat(completeURL).isEqualTo("https://github.com/test/repo");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetCompleteUrlForSshRepoConnector() {
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .connectorType(ConnectorType.GITHUB)
            .connectorConfig(GithubConnectorDTO.builder()
                                 .connectionType(GitConnectionType.REPO)
                                 .url("git@github.com:test/test-repo.git")
                                 .authentication(GithubAuthenticationDTO.builder().authType(GitAuthType.SSH).build())
                                 .build())
            .build();

    String completeURL = CodebaseUtils.getCompleteURLFromConnector(connectorDetails, null);
    assertThat(completeURL).isEqualTo("git@github.com:test/test-repo.git");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetCompleteUrlForHttpAccountConnector() {
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .connectorType(ConnectorType.GITHUB)
            .connectorConfig(GithubConnectorDTO.builder()
                                 .connectionType(GitConnectionType.ACCOUNT)
                                 .url("https://github.com/test")
                                 .authentication(GithubAuthenticationDTO.builder().authType(GitAuthType.HTTP).build())
                                 .build())
            .build();

    String completeURL = CodebaseUtils.getCompleteURLFromConnector(connectorDetails, "repo");
    assertThat(completeURL).isEqualTo("https://github.com/test/repo");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetCompleteUrlForSshAccountConnector() {
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .connectorType(ConnectorType.GITHUB)
            .connectorConfig(GithubConnectorDTO.builder()
                                 .connectionType(GitConnectionType.ACCOUNT)
                                 .url("git@github.com:test")
                                 .authentication(GithubAuthenticationDTO.builder().authType(GitAuthType.SSH).build())
                                 .build())
            .build();

    String completeURL = CodebaseUtils.getCompleteURLFromConnector(connectorDetails, "test-repo");
    assertThat(completeURL).isEqualTo("git@github.com:test/test-repo");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetCompleteUrlForAzureHttpAccountConnector() {
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .connectorType(ConnectorType.AZURE_REPO)
            .connectorConfig(
                AzureRepoConnectorDTO.builder()
                    .connectionType(AzureRepoConnectionTypeDTO.PROJECT)
                    .url("https://dev.azure.com/org/project/")
                    .authentication(AzureRepoAuthenticationDTO.builder().authType(GitAuthType.HTTP).build())
                    .build())
            .build();

    String completeURL = CodebaseUtils.getCompleteURLFromConnector(connectorDetails, "repo");
    assertThat(completeURL).isEqualTo("https://dev.azure.com/org/project/_git/repo");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetCompleteUrlForAzureSshAccountConnector() {
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .connectorType(ConnectorType.AZURE_REPO)
            .connectorConfig(AzureRepoConnectorDTO.builder()
                                 .connectionType(AzureRepoConnectionTypeDTO.PROJECT)
                                 .url("git@ssh.dev.azure.com:v3/org/project/")
                                 .authentication(AzureRepoAuthenticationDTO.builder().authType(GitAuthType.SSH).build())
                                 .build())
            .build();

    String completeURL = CodebaseUtils.getCompleteURLFromConnector(connectorDetails, "repo");
    assertThat(completeURL).isEqualTo("git@ssh.dev.azure.com:v3/org/project/repo");
  }

  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void testGetGitConnectorSkipClone() {
    NGAccess ngAccess = Mockito.mock(NGAccess.class);
    CodeBase codeBase = CodeBase.builder().build();
    final ConnectorDetails gitConnector = codebaseUtils.getGitConnector(ngAccess, codeBase, true);
    assertThat(gitConnector).isNull();
  }

  @Test(expected = CIStageExecutionException.class)
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void testGetGitConnectorNullCodeBase() {
    codebaseUtils.getGitConnector(null, null, false);
  }

  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void testGetGitConnectorCodebase() {
    String connectorRefValue = "myConnectorRef";
    ConnectorDetails connectorDetails = ConnectorDetails.builder().connectorType(ConnectorType.GITHUB).build();
    when(connectorUtils.getConnectorDetails(any(), eq(connectorRefValue), eq(true))).thenReturn(connectorDetails);
    CodeBase codeBase = CodeBase.builder().connectorRef(ParameterField.createValueField(connectorRefValue)).build();
    final ConnectorDetails gitConnector = codebaseUtils.getGitConnector(null, codeBase, false);
    assertThat(gitConnector).isEqualTo(connectorDetails);
  }

  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void testGetGitConnector() {
    String connectorRefValue = "myConnectorRef";
    ConnectorDetails connectorDetails = ConnectorDetails.builder().connectorType(ConnectorType.GITHUB).build();
    when(connectorUtils.getConnectorDetails(any(), eq(connectorRefValue), eq(true))).thenReturn(connectorDetails);
    final ConnectorDetails gitConnector = codebaseUtils.getGitConnector(null, connectorRefValue);
    assertThat(gitConnector).isEqualTo(connectorDetails);
  }

  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void testGetGitEnvVariablesCodeBaseSkipGitClone() {
    final Map<String, String> gitEnvVariables = codebaseUtils.getGitEnvVariables(null, null, true);
    assertThat(gitEnvVariables).isEmpty();
  }

  @Test(expected = CIStageExecutionException.class)
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void testGetGitEnvVariablesNullCodebase() {
    codebaseUtils.getGitEnvVariables(null, null, false);
  }

  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void testGetGitEnvVariablesCodeBase() {
    String repoName = "myRepoName";
    String scmHostName = "github.com";
    String scmUrl = "git@" + scmHostName + ":org";
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .connectorType(ConnectorType.GITHUB)
            .connectorConfig(GithubConnectorDTO.builder()
                                 .connectionType(GitConnectionType.ACCOUNT)
                                 .url(scmUrl)
                                 .authentication(GithubAuthenticationDTO.builder().authType(GitAuthType.SSH).build())
                                 .build())
            .build();
    CodeBase codeBase = CodeBase.builder().repoName(ParameterField.createValueField(repoName)).build();
    final Map<String, String> gitEnvVariables = codebaseUtils.getGitEnvVariables(connectorDetails, codeBase, false);
    assertThat(gitEnvVariables.get(DRONE_NETRC_MACHINE)).isEqualTo(scmHostName);
    assertThat(gitEnvVariables.get(DRONE_REMOTE_URL)).isEqualTo(scmUrl + "/" + repoName + ".git");
  }

  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void testGetGitEnvVariables() {
    String repoName = "myRepoName";
    String scmHostName = "gitlab.com";
    String scmUrl = "git@" + scmHostName + ":org";
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .connectorType(ConnectorType.GITLAB)
            .connectorConfig(GitlabConnectorDTO.builder()
                                 .connectionType(GitConnectionType.ACCOUNT)
                                 .url(scmUrl)
                                 .authentication(GitlabAuthenticationDTO.builder().authType(GitAuthType.SSH).build())
                                 .build())
            .build();
    final Map<String, String> gitEnvVariables = codebaseUtils.getGitEnvVariables(connectorDetails, repoName);
    assertThat(gitEnvVariables.get(DRONE_NETRC_MACHINE)).isEqualTo(scmHostName);
    assertThat(gitEnvVariables.get(DRONE_REMOTE_URL)).isEqualTo(scmUrl + "/" + repoName + ".git");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetRuntimeCodebaseVarsForBitbucket() {
    ConnectorDetails connectorDetails = ciExecutionPlanTestHelper.getBitBucketConnector();
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(CodebaseSweepingOutput.builder().sourceBranch("source").build())
                        .build());
    final Map<String, String> runtimeCodebaseVars = codebaseUtils.getRuntimeCodebaseVars(ambiance, connectorDetails);
    assertThat(runtimeCodebaseVars).isNotEmpty();
    assertThat(runtimeCodebaseVars).containsKey(DRONE_COMMIT_REF);
    assertThat(runtimeCodebaseVars.get(DRONE_COMMIT_REF)).isEqualTo("+refs/heads/source");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetRuntimeCodebaseVarsForBitbucketMergedPR() {
    ConnectorDetails connectorDetails = ciExecutionPlanTestHelper.getBitBucketConnector();
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(CodebaseSweepingOutput.builder()
                                    .targetBranch("target")
                                    .sourceBranch("source")
                                    .state("merged")
                                    .commitSha("commitSha")
                                    .mergeSha("mergeSha")
                                    .build())
                        .build());
    final Map<String, String> runtimeCodebaseVars = codebaseUtils.getRuntimeCodebaseVars(ambiance, connectorDetails);
    assertThat(runtimeCodebaseVars).isNotEmpty();
    assertThat(runtimeCodebaseVars.get(DRONE_COMMIT_REF)).isEqualTo("+refs/heads/target");
    assertThat(runtimeCodebaseVars.get(DRONE_COMMIT_SHA)).isEqualTo("mergeSha");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetRuntimeCodebaseVarsForBitbucketUnMergedPR() {
    ConnectorDetails connectorDetails = ciExecutionPlanTestHelper.getBitBucketConnector();
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(CodebaseSweepingOutput.builder()
                                    .targetBranch("target")
                                    .sourceBranch("source")
                                    .state("open")
                                    .commitSha("commitSha")
                                    .prTitle("PR Title")
                                    .commitMessage("Commit Message")
                                    .build())
                        .build());
    final Map<String, String> runtimeCodebaseVars = codebaseUtils.getRuntimeCodebaseVars(ambiance, connectorDetails);
    assertThat(runtimeCodebaseVars).isNotEmpty();
    assertThat(runtimeCodebaseVars.get(DRONE_COMMIT_REF)).isEqualTo("+refs/heads/source");
    assertThat(runtimeCodebaseVars.get(DRONE_BUILD_LINK)).isEqualTo("url");
    assertThat(runtimeCodebaseVars.get(DRONE_PULL_REQUEST_TITLE)).isEqualTo("PR Title");
    assertThat(runtimeCodebaseVars.get(DRONE_COMMIT_MESSAGE)).isEqualTo("Commit Message");
    assertThat(runtimeCodebaseVars.get(DRONE_COMMIT_SHA)).isEqualTo("commitSha");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetRuntimeCodebaseVarsForBitbucketWithoutGitConnector() {
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(CodebaseSweepingOutput.builder().sourceBranch("source").build())
                        .build());
    final Map<String, String> runtimeCodebaseVars = codebaseUtils.getRuntimeCodebaseVars(ambiance, null);
    assertThat(runtimeCodebaseVars).doesNotContainKey(DRONE_COMMIT_REF);
  }
}
