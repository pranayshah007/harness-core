/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states.codebase;

import static io.harness.rule.OwnerRule.ALEKSANDAR;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.VINICIUS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.execution.BranchWebhookEvent;
import io.harness.beans.execution.CommitDetails;
import io.harness.beans.execution.ManualExecutionSource;
import io.harness.beans.execution.PRWebhookEvent;
import io.harness.beans.execution.ReleaseWebhookEvent;
import io.harness.beans.execution.Repository;
import io.harness.beans.execution.WebhookBaseAttributes;
import io.harness.beans.execution.WebhookExecutionSource;
import io.harness.beans.sweepingoutputs.CodebaseSweepingOutput;
import io.harness.category.element.UnitTests;
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.task.scm.GitRefType;
import io.harness.delegate.task.scm.ScmGitRefTaskParams;
import io.harness.delegate.task.scm.ScmGitRefTaskResponseData;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.yaml.ParameterField;
import io.harness.product.ci.scm.proto.Commit;
import io.harness.product.ci.scm.proto.FindPRResponse;
import io.harness.product.ci.scm.proto.GetLatestCommitResponse;
import io.harness.product.ci.scm.proto.ListCommitsInPRResponse;
import io.harness.product.ci.scm.proto.PullRequest;
import io.harness.product.ci.scm.proto.Reference;
import io.harness.product.ci.scm.proto.Signature;
import io.harness.product.ci.scm.proto.User;
import io.harness.rule.Owner;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;
import java.util.Arrays;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CI)
public class CodeBaseTaskStepTest extends CategoryTest {
  @Mock private ConnectorUtils connectorUtils;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @InjectMocks CodeBaseTaskStep codeBaseTaskStep;
  private Ambiance ambiance;
  private StepInputPackage stepInputPackage;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    ambiance = Ambiance.newBuilder()
                   .putSetupAbstractions(SetupAbstractionKeys.accountId, "accountId")
                   .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "orgIdentifier")
                   .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "projectIdentifier")
                   .build();
    stepInputPackage = StepInputPackage.builder().build();
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldObtainTaskForBranchBuilds() {
    ManualExecutionSource executionSource = ManualExecutionSource.builder().branch("main").build();
    ConnectorDetails connectorDetails = ConnectorDetails.builder()
                                            .connectorType(ConnectorType.GITHUB)
                                            .connectorConfig(GithubConnectorDTO.builder()
                                                                 .url("http://github.com/octocat/")
                                                                 .connectionType(GitConnectionType.ACCOUNT)
                                                                 .build())
                                            .build();

    ScmGitRefTaskParams taskParams =
        codeBaseTaskStep.obtainTaskParameters(executionSource, connectorDetails, "hello-world");
    assertThat(taskParams).isNotNull();
    assertThat(taskParams.getBranch()).isEqualTo("main");
    assertThat(taskParams.getScmConnector().getUrl()).isEqualTo("http://github.com/octocat/hello-world");
    assertThat(taskParams.getGitRefType()).isEqualTo(GitRefType.LATEST_COMMIT_ID);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldObtainTaskForPRBuilds() {
    ManualExecutionSource executionSource = ManualExecutionSource.builder().prNumber("1").build();
    ConnectorDetails connectorDetails = ConnectorDetails.builder()
                                            .connectorType(ConnectorType.GITHUB)
                                            .connectorConfig(GithubConnectorDTO.builder()
                                                                 .connectionType(GitConnectionType.ACCOUNT)
                                                                 .url("http://github.com/octocat/hello-world")
                                                                 .build())
                                            .build();

    ScmGitRefTaskParams taskParams = codeBaseTaskStep.obtainTaskParameters(executionSource, connectorDetails, null);
    assertThat(taskParams).isNotNull();
    assertThat(taskParams.getPrNumber()).isEqualTo(1);
    assertThat(taskParams.getScmConnector().getUrl()).isEqualTo("http://github.com/octocat/hello-world");
    assertThat(taskParams.getGitRefType()).isEqualTo(GitRefType.PULL_REQUEST_WITH_COMMITS);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldBuildCommitShaCodebaseSweepingOutput() throws InvalidProtocolBufferException {
    ScmGitRefTaskResponseData scmGitRefTaskResponseData =
        ScmGitRefTaskResponseData.builder()
            .branch("main")
            .repoUrl("http://github.com/octocat/hello-world")
            .getLatestCommitResponse(GetLatestCommitResponse.newBuilder()
                                         .setCommit(Commit.newBuilder()
                                                        .setSha("commitId")
                                                        .setAuthor(Signature.newBuilder()
                                                                       .setLogin("login")
                                                                       .setAvatar("avatar")
                                                                       .setName("name")
                                                                       .setEmail("email")
                                                                       .build())
                                                        .build())
                                         .setCommitId("commitId")
                                         .build()
                                         .toByteArray())
            .build();
    CodebaseSweepingOutput codebaseSweepingOutput =
        codeBaseTaskStep.buildCommitShaCodebaseSweepingOutput(scmGitRefTaskResponseData, null);
    assertThat(codebaseSweepingOutput.getCommitSha()).isEqualTo("commitId");
    assertThat(codebaseSweepingOutput.getShortCommitSha()).isEqualTo("commitI");
    assertThat(codebaseSweepingOutput.getBranch()).isEqualTo("main");
    assertThat(codebaseSweepingOutput.getRepoUrl()).isEqualTo("http://github.com/octocat/hello-world");
    assertThat(codebaseSweepingOutput.getGitUserId()).isEqualTo("login");
    assertThat(codebaseSweepingOutput.getGitUser()).isEqualTo("name");
    assertThat(codebaseSweepingOutput.getGitUserEmail()).isEqualTo("email");
    assertThat(codebaseSweepingOutput.getGitUserAvatar()).isEqualTo("avatar");
    assertThat(codebaseSweepingOutput.getCommitRef()).isEqualTo("refs/heads/main");
    assertThat(codebaseSweepingOutput.getBuild().getType()).isEqualTo("branch");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void shouldBuildCommitShaCodebaseSweepingOutputFromTag() throws InvalidProtocolBufferException {
    ScmGitRefTaskResponseData scmGitRefTaskResponseData =
        ScmGitRefTaskResponseData.builder()
            .repoUrl("http://github.com/octocat/hello-world")
            .getLatestCommitResponse(GetLatestCommitResponse.newBuilder()
                                         .setCommit(Commit.newBuilder()
                                                        .setSha("commitId")
                                                        .setAuthor(Signature.newBuilder()
                                                                       .setLogin("login")
                                                                       .setAvatar("avatar")
                                                                       .setName("name")
                                                                       .setEmail("email")
                                                                       .build())
                                                        .build())
                                         .setCommitId("commitId")
                                         .build()
                                         .toByteArray())
            .build();
    CodebaseSweepingOutput codebaseSweepingOutput =
        codeBaseTaskStep.buildCommitShaCodebaseSweepingOutput(scmGitRefTaskResponseData, "tag");
    assertThat(codebaseSweepingOutput.getCommitSha()).isEqualTo("commitId");
    assertThat(codebaseSweepingOutput.getShortCommitSha()).isEqualTo("commitI");
    assertThat(codebaseSweepingOutput.getRepoUrl()).isEqualTo("http://github.com/octocat/hello-world");
    assertThat(codebaseSweepingOutput.getGitUserId()).isEqualTo("login");
    assertThat(codebaseSweepingOutput.getGitUser()).isEqualTo("name");
    assertThat(codebaseSweepingOutput.getGitUserEmail()).isEqualTo("email");
    assertThat(codebaseSweepingOutput.getGitUserAvatar()).isEqualTo("avatar");
    assertThat(codebaseSweepingOutput.getCommitRef()).isEqualTo("refs/tags/tag");
    assertThat(codebaseSweepingOutput.getBuild().getType()).isEqualTo("tag");
    assertThat(codebaseSweepingOutput.getTag()).isEqualTo("tag");
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldBuildPRCodebaseSweepingOutput() throws InvalidProtocolBufferException {
    ScmGitRefTaskResponseData scmGitRefTaskResponseData =
        ScmGitRefTaskResponseData.builder()
            .branch("main")
            .repoUrl("http://github.com/octocat/hello-world")
            .findPRResponse(FindPRResponse.newBuilder()
                                .setPr(PullRequest.newBuilder()
                                           .setTarget("main")
                                           .setSource("feature/abc")
                                           .setNumber(1)
                                           .setTitle("Title")
                                           .setSha("commitId")
                                           .setRef("ref")
                                           .setBase(Reference.newBuilder().setSha("commitIdBase").build())
                                           .setAuthor(User.newBuilder()
                                                          .setName("First Last")
                                                          .setEmail("first.last@email.com")
                                                          .setAvatar("http://...")
                                                          .setLogin("firstLast")
                                                          .build())
                                           .setLink("http://github.com/octocat/hello-world/pull/1")
                                           .setClosed(false)
                                           .setMerged(false)
                                           .setMergeSha("mergeSha")
                                           .build())
                                .build()
                                .toByteArray())
            .listCommitsInPRResponse(
                ListCommitsInPRResponse.newBuilder()
                    .addCommits(Commit.newBuilder()
                                    .setSha("commitId")
                                    .setMessage("message")
                                    .setLink("http://github.com/octocat/hello-world/pull/1/commits/commitId")
                                    .setCommitter(Signature.newBuilder()
                                                      .setDate(Timestamp.newBuilder().setSeconds(123123123).build())
                                                      .build())
                                    .setAuthor(Signature.newBuilder()
                                                   .setName("First Last")
                                                   .setEmail("first.last@email.com")
                                                   .setAvatar("http://...")
                                                   .setLogin("firstLast")
                                                   .build())
                                    .build())
                    .build()
                    .toByteArray())
            .build();
    CodebaseSweepingOutput codebaseSweepingOutput =
        codeBaseTaskStep.buildPRCodebaseSweepingOutput(scmGitRefTaskResponseData);
    assertThat(codebaseSweepingOutput.getBranch()).isEqualTo("main");
    assertThat(codebaseSweepingOutput.getTargetBranch()).isEqualTo("main");
    assertThat(codebaseSweepingOutput.getSourceBranch()).isEqualTo("feature/abc");
    assertThat(codebaseSweepingOutput.getPrNumber()).isEqualTo("1");
    assertThat(codebaseSweepingOutput.getPrTitle()).isEqualTo("Title");
    assertThat(codebaseSweepingOutput.getCommitSha()).isEqualTo("commitId");
    assertThat(codebaseSweepingOutput.getShortCommitSha()).isEqualTo("commitI");
    assertThat(codebaseSweepingOutput.getBaseCommitSha()).isEqualTo("commitIdBase");
    assertThat(codebaseSweepingOutput.getCommitRef()).isEqualTo("ref");
    assertThat(codebaseSweepingOutput.getRepoUrl()).isEqualTo("http://github.com/octocat/hello-world");
    assertThat(codebaseSweepingOutput.getGitUser()).isEqualTo("First Last");
    assertThat(codebaseSweepingOutput.getGitUserEmail()).isEqualTo("first.last@email.com");
    assertThat(codebaseSweepingOutput.getGitUserAvatar()).isEqualTo("http://...");
    assertThat(codebaseSweepingOutput.getGitUserId()).isEqualTo("firstLast");
    assertThat(codebaseSweepingOutput.getPullRequestLink()).isEqualTo("http://github.com/octocat/hello-world/pull/1");
    assertThat(codebaseSweepingOutput.getCommits().get(0))
        .isEqualTo(CodebaseSweepingOutput.CodeBaseCommit.builder()
                       .link("http://github.com/octocat/hello-world/pull/1/commits/commitId")
                       .id("commitId")
                       .message("message")
                       .timeStamp(123123123)
                       .ownerName("First Last")
                       .ownerEmail("first.last@email.com")
                       .ownerId("firstLast")
                       .build());
    assertThat(codebaseSweepingOutput.getState()).isEqualTo("open");
    assertThat(codebaseSweepingOutput.getMergeSha()).isEqualTo("mergeSha");

    FindPRResponse prResponse = FindPRResponse.newBuilder()
                                    .setPr(PullRequest.newBuilder()
                                               .setTarget("main")
                                               .setSource("feature/abc")
                                               .setNumber(1)
                                               .setTitle("Title")
                                               .setSha("commitId")
                                               .setRef("ref")
                                               .setBase(Reference.newBuilder().setSha("commitIdBase").build())
                                               .setAuthor(User.newBuilder().setAvatar("http://...").build())
                                               .setLink("http://github.com/octocat/hello-world/pull/1")
                                               .setClosed(false)
                                               .setMerged(false)
                                               .setMergeSha("mergeSha")
                                               .build())
                                    .build();

    scmGitRefTaskResponseData.setFindPRResponse(prResponse.toByteArray());
    codebaseSweepingOutput = codeBaseTaskStep.buildPRCodebaseSweepingOutput(scmGitRefTaskResponseData);
    assertThat(codebaseSweepingOutput.getGitUser()).isEqualTo("First Last");
    assertThat(codebaseSweepingOutput.getGitUserEmail()).isEqualTo("first.last@email.com");
    assertThat(codebaseSweepingOutput.getGitUserAvatar()).isEqualTo("http://...");
    assertThat(codebaseSweepingOutput.getGitUserId()).isEqualTo("firstLast");
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldBuildManualCodebaseSweepingOutput() throws InvalidProtocolBufferException {
    ManualExecutionSource manualExecutionSource = ManualExecutionSource.builder().branch("main").build();
    CodebaseSweepingOutput codebaseSweepingOutput =
        codeBaseTaskStep.buildManualCodebaseSweepingOutput(manualExecutionSource, "url");
    assertThat(codebaseSweepingOutput.getBranch()).isEqualTo("main");
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldBuildPRWebhookCodebaseSweepingOutput() {
    WebhookExecutionSource webhookExecutionSource =
        WebhookExecutionSource.builder()
            .webhookEvent(PRWebhookEvent.builder()
                              .sourceBranch("feature/abc")
                              .targetBranch("main")
                              .pullRequestId(1L)
                              .title("Title")
                              .pullRequestLink("http://github.com/octocat/hello-world/pull/1")
                              .baseAttributes(WebhookBaseAttributes.builder()
                                                  .after("commitId")
                                                  .before("commitIdBase")
                                                  .authorName("First Last")
                                                  .authorEmail("first.last@email.com")
                                                  .authorAvatar("http://...")
                                                  .authorLogin("firstLast")
                                                  .mergeSha("mergeSha")
                                                  .build())
                              .commitDetailsList(Arrays.asList(
                                  CommitDetails.builder().message("First commit message").timeStamp(110).build(),
                                  CommitDetails.builder().message("Last commit message").timeStamp(120).build()))
                              .repository(Repository.builder().link("http://github.com/octocat/hello-world").build())
                              .build())
            .build();
    CodebaseSweepingOutput codebaseSweepingOutput =
        codeBaseTaskStep.buildWebhookCodebaseSweepingOutput(webhookExecutionSource);
    assertThat(codebaseSweepingOutput.getBranch()).isEqualTo("main");
    assertThat(codebaseSweepingOutput.getTargetBranch()).isEqualTo("main");
    assertThat(codebaseSweepingOutput.getSourceBranch()).isEqualTo("feature/abc");
    assertThat(codebaseSweepingOutput.getPrNumber()).isEqualTo("1");
    assertThat(codebaseSweepingOutput.getPrTitle()).isEqualTo("Title");
    assertThat(codebaseSweepingOutput.getCommitSha()).isEqualTo("commitId");
    assertThat(codebaseSweepingOutput.getShortCommitSha()).isEqualTo("commitI");
    assertThat(codebaseSweepingOutput.getBaseCommitSha()).isEqualTo("commitIdBase");
    assertThat(codebaseSweepingOutput.getRepoUrl()).isEqualTo("http://github.com/octocat/hello-world");
    assertThat(codebaseSweepingOutput.getGitUser()).isEqualTo("First Last");
    assertThat(codebaseSweepingOutput.getGitUserEmail()).isEqualTo("first.last@email.com");
    assertThat(codebaseSweepingOutput.getGitUserAvatar()).isEqualTo("http://...");
    assertThat(codebaseSweepingOutput.getGitUserId()).isEqualTo("firstLast");
    assertThat(codebaseSweepingOutput.getPullRequestLink()).isEqualTo("http://github.com/octocat/hello-world/pull/1");
    assertThat(codebaseSweepingOutput.getMergeSha()).isEqualTo("mergeSha");
    assertThat(codebaseSweepingOutput.getCommitMessage()).isEqualTo("Last commit message");

    PRWebhookEvent prWebhookEvent =
        PRWebhookEvent.builder()
            .sourceBranch("feature/abc")
            .targetBranch("main")
            .pullRequestId(1L)
            .title("Title")
            .pullRequestLink("http://github.com/octocat/hello-world/pull/1")
            .baseAttributes(WebhookBaseAttributes.builder()
                                .after("commitId")
                                .before("commitIdBase")
                                .authorAvatar("http://...")
                                .mergeSha("mergeSha")
                                .build())
            .commitDetailsList(
                Arrays.asList(CommitDetails.builder().message("First commit message").timeStamp(110).build(),
                    CommitDetails.builder()
                        .message("Last commit message")
                        .ownerId("firstLast")
                        .ownerEmail("first.last@email.com")
                        .ownerName("First Last")
                        .timeStamp(120)
                        .build()))
            .repository(Repository.builder().link("http://github.com/octocat/hello-world").build())
            .build();
    webhookExecutionSource = WebhookExecutionSource.builder().webhookEvent(prWebhookEvent).build();
    codebaseSweepingOutput = codeBaseTaskStep.buildWebhookCodebaseSweepingOutput(webhookExecutionSource);
    assertThat(codebaseSweepingOutput.getGitUser()).isEqualTo("First Last");
    assertThat(codebaseSweepingOutput.getGitUserEmail()).isEqualTo("first.last@email.com");
    assertThat(codebaseSweepingOutput.getGitUserAvatar()).isEqualTo("http://...");
    assertThat(codebaseSweepingOutput.getGitUserId()).isEqualTo("firstLast");
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldBuildPushWebhookCodebaseSweepingOutput() {
    WebhookExecutionSource webhookExecutionSource =
        WebhookExecutionSource.builder()
            .webhookEvent(
                BranchWebhookEvent.builder()
                    .branchName("main")
                    .baseAttributes(WebhookBaseAttributes.builder()
                                        .after("commitId")
                                        .before("commitIdBase")
                                        .authorName("First Last")
                                        .authorEmail("first.last@email.com")
                                        .authorAvatar("http://...")
                                        .authorLogin("firstLast")
                                        .ref("refs/heads/main")
                                        .build())
                    .commitDetailsList(Arrays.asList(CommitDetails.builder().message("Last commit message").build()))
                    .repository(Repository.builder().link("http://github.com/octocat/hello-world").build())
                    .build())
            .build();
    CodebaseSweepingOutput codebaseSweepingOutput =
        codeBaseTaskStep.buildWebhookCodebaseSweepingOutput(webhookExecutionSource);
    assertThat(codebaseSweepingOutput.getBranch()).isEqualTo("main");
    assertThat(codebaseSweepingOutput.getTargetBranch()).isEqualTo("main");
    assertThat(codebaseSweepingOutput.getCommitSha()).isEqualTo("commitId");
    assertThat(codebaseSweepingOutput.getShortCommitSha()).isEqualTo("commitI");
    assertThat(codebaseSweepingOutput.getRepoUrl()).isEqualTo("http://github.com/octocat/hello-world");
    assertThat(codebaseSweepingOutput.getGitUser()).isEqualTo("First Last");
    assertThat(codebaseSweepingOutput.getGitUserEmail()).isEqualTo("first.last@email.com");
    assertThat(codebaseSweepingOutput.getGitUserAvatar()).isEqualTo("http://...");
    assertThat(codebaseSweepingOutput.getGitUserId()).isEqualTo("firstLast");
    assertThat(codebaseSweepingOutput.getCommitMessage()).isEqualTo("Last commit message");
    assertThat(codebaseSweepingOutput.getCommitRef()).isEqualTo("refs/heads/main");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void shouldBuildReleaseWebhookCodebaseSweepingOutput() {
    WebhookExecutionSource webhookExecutionSource =
        WebhookExecutionSource.builder()
            .webhookEvent(ReleaseWebhookEvent.builder()
                              .releaseTag("1.1")
                              .releaseBody("releaseBody")
                              .releaseLink("LinkToRelease")
                              .title("releaseTitle")
                              .baseAttributes(WebhookBaseAttributes.builder()
                                                  .after("commitId")
                                                  .before("commitIdBase")
                                                  .authorName("First Last")
                                                  .authorEmail("first.last@email.com")
                                                  .authorAvatar("http://...")
                                                  .authorLogin("firstLast")
                                                  .build())
                              .repository(Repository.builder().link("http://github.com/octocat/hello-world").build())
                              .build())
            .build();
    CodebaseSweepingOutput codebaseSweepingOutput =
        codeBaseTaskStep.buildWebhookCodebaseSweepingOutput(webhookExecutionSource);
    assertThat(codebaseSweepingOutput.getReleaseTag()).isEqualTo("1.1");
    assertThat(codebaseSweepingOutput.getReleaseBody()).isEqualTo("releaseBody");
    assertThat(codebaseSweepingOutput.getReleaseLink()).isEqualTo("LinkToRelease");
    assertThat(codebaseSweepingOutput.getReleaseTitle()).isEqualTo("releaseTitle");
    assertThat(codebaseSweepingOutput.getRepoUrl()).isEqualTo("http://github.com/octocat/hello-world");
    assertThat(codebaseSweepingOutput.getGitUser()).isEqualTo("First Last");
    assertThat(codebaseSweepingOutput.getGitUserEmail()).isEqualTo("first.last@email.com");
    assertThat(codebaseSweepingOutput.getGitUserAvatar()).isEqualTo("http://...");
    assertThat(codebaseSweepingOutput.getGitUserId()).isEqualTo("firstLast");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void shouldFailPRBuildWhenAPIAccessDisabled() {
    ManualExecutionSource executionSource = ManualExecutionSource.builder().prNumber("12").build();
    ConnectorDetails connectorDetails = ConnectorDetails.builder()
                                            .connectorType(ConnectorType.GITHUB)
                                            .connectorConfig(GithubConnectorDTO.builder()
                                                                 .url("http://github.com/octocat/")
                                                                 .connectionType(GitConnectionType.ACCOUNT)
                                                                 .build())
                                            .build();
    when(connectorUtils.getConnectorDetails(any(), any())).thenReturn(connectorDetails);
    when(connectorUtils.hasApiAccess(connectorDetails)).thenReturn(false);
    CodeBaseTaskStepParameters codeBaseTaskStepParameters =
        CodeBaseTaskStepParameters.builder()
            .connectorRef(ParameterField.createValueField("connectorRef"))
            .repoName(ParameterField.createValueField("repoName"))
            .executionSource(executionSource)
            .build();
    assertThatThrownBy(() -> codeBaseTaskStep.executeSync(ambiance, codeBaseTaskStepParameters, stepInputPackage, null))
        .isInstanceOf(CIStageExecutionException.class);
  }
}
