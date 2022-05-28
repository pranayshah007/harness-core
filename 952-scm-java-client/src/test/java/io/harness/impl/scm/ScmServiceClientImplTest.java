/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.impl.scm;

import static io.harness.constants.Constants.SCM_CONFLICT_ERROR_CODE;
import static io.harness.constants.Constants.SCM_CONFLICT_ERROR_MESSAGE;
import static io.harness.constants.Constants.SCM_INTERNAL_SERVER_ERROR_CODE;
import static io.harness.constants.Constants.SCM_INTERNAL_SERVER_ERROR_MESSAGE;
import static io.harness.delegate.beans.connector.ConnectorType.BITBUCKET;
import static io.harness.delegate.beans.connector.ConnectorType.GITHUB;
import static io.harness.rule.OwnerRule.BHAVYA;
import static io.harness.rule.OwnerRule.DEEPAK;
import static io.harness.rule.OwnerRule.MEET;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.spy;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.PageRequestDTO;
import io.harness.beans.gitsync.GitFileDetails;
import io.harness.beans.gitsync.GitWebhookDetails;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.exception.UnexpectedException;
import io.harness.product.ci.scm.proto.Commit;
import io.harness.product.ci.scm.proto.CreateBranchResponse;
import io.harness.product.ci.scm.proto.CreateFileResponse;
import io.harness.product.ci.scm.proto.CreatePRResponse;
import io.harness.product.ci.scm.proto.CreateWebhookRequest;
import io.harness.product.ci.scm.proto.CreateWebhookResponse;
import io.harness.product.ci.scm.proto.GetLatestCommitOnFileResponse;
import io.harness.product.ci.scm.proto.GetLatestCommitResponse;
import io.harness.product.ci.scm.proto.GetUserRepoResponse;
import io.harness.product.ci.scm.proto.ListBranchesWithDefaultRequest;
import io.harness.product.ci.scm.proto.ListBranchesWithDefaultResponse;
import io.harness.product.ci.scm.proto.Provider;
import io.harness.product.ci.scm.proto.Repository;
import io.harness.product.ci.scm.proto.SCMGrpc;
import io.harness.product.ci.scm.proto.UpdateFileResponse;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.util.Arrays;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@OwnedBy(HarnessTeam.PL)
@RunWith(PowerMockRunner.class)
@PrepareForTest({SCMGrpc.SCMBlockingStub.class, Provider.class, ScmGitWebhookHelper.class})
public class ScmServiceClientImplTest extends CategoryTest {
  @InjectMocks ScmServiceClientImpl scmServiceClient;
  @Mock ScmGitProviderHelper scmGitProviderHelper;
  @Mock ScmGitProviderMapper scmGitProviderMapper;
  @Mock SCMGrpc.SCMBlockingStub scmBlockingStub;
  @Mock Provider gitProvider;
  @Mock ScmConnector scmConnector;
  Commit commit;
  GetLatestCommitResponse getLatestCommitResponse;
  CreateBranchResponse createBranchResponse;
  String slug = "slug";
  String error = "error";
  String branch = "branch";
  String filepath = "filepath";
  String commitId = "commitId";
  String commitMessage = "commitMessage";
  String baseBranchName = "baseBranchName";
  String sha = "sha";
  String fileContent = "fileContent";
  String userEmail = "userEmail";
  String userName = "userName";
  String target = "target";

  String repoUrl = "https://github.com/harness/platform";

  @Before
  public void setup() {
    GithubConnectorDTO githubConnector = GithubConnectorDTO.builder()
                                             .apiAccess(GithubApiAccessDTO.builder().build())
                                             .url(repoUrl)
                                             .connectionType(GitConnectionType.REPO)
                                             .build();
    ConnectorInfoDTO connectorInfo = ConnectorInfoDTO.builder().connectorConfig(githubConnector).build();
    scmConnector = (ScmConnector) connectorInfo.getConnectorConfig();
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testCreateNewBranch() {
    commit = Commit.newBuilder().setSha(sha).build();
    getLatestCommitResponse = GetLatestCommitResponse.newBuilder().setCommit(commit).build();
    createBranchResponse = CreateBranchResponse.newBuilder().setStatus(422).setError(error).build();

    when(scmGitProviderHelper.getSlug(any())).thenReturn(slug);
    when(scmGitProviderMapper.mapToSCMGitProvider(any())).thenReturn(gitProvider);
    when(scmBlockingStub.getLatestCommit(any())).thenReturn(getLatestCommitResponse);
    when(scmBlockingStub.createBranch(any())).thenReturn(createBranchResponse);
    assertThatThrownBy(() -> scmServiceClient.createNewBranch(scmConnector, branch, baseBranchName, scmBlockingStub))
        .hasMessage(String.format("Action could not be completed. Possible reasons can be:\n"
                + "1. A branch with name %s already exists in the remote Git repository\n"
                + "2. The branch name %s is invalid\n",
            branch, branch));
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void testListBranchesWithDefault() {
    ListBranchesWithDefaultResponse listBranchesWithDefaultResponse = ListBranchesWithDefaultResponse.newBuilder()
                                                                          .addAllBranches(Arrays.asList("abc", "def"))
                                                                          .setDefaultBranch("main")
                                                                          .build();
    when(scmGitProviderHelper.getSlug(any())).thenReturn(slug);
    when(scmGitProviderMapper.mapToSCMGitProvider(any())).thenReturn(gitProvider);
    when(scmBlockingStub.listBranchesWithDefault(any())).thenReturn(listBranchesWithDefaultResponse);
    ArgumentCaptor<ListBranchesWithDefaultRequest> listBranchesRequestCaptor =
        ArgumentCaptor.forClass(ListBranchesWithDefaultRequest.class);
    ListBranchesWithDefaultResponse responseFromService = scmServiceClient.listBranchesWithDefault(
        scmConnector, PageRequestDTO.builder().pageIndex(0).build(), scmBlockingStub);
    verify(scmBlockingStub, times(1)).listBranchesWithDefault(listBranchesRequestCaptor.capture());
    assertThat(responseFromService).isEqualTo(listBranchesWithDefaultResponse);
    ListBranchesWithDefaultRequest listBranchRequest = listBranchesRequestCaptor.getValue();
    assertThat(listBranchRequest.getSlug()).isEqualTo("slug");
    assertThat(listBranchRequest.getPagination().getPage()).isEqualTo(1);
  }

  @Test
  @Owner(developers = OwnerRule.MOHIT_GARG)
  @Category(UnitTests.class)
  public void testUpdateFileOpsPreChecksGithub() {
    when(scmConnector.getConnectorType()).thenReturn(GITHUB);
    Optional<UpdateFileResponse> response =
        scmServiceClient.runUpdateFileOpsPreChecks(scmConnector, scmBlockingStub, GitFileDetails.builder().build());
    assertThat(response.equals(Optional.empty())).isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.MOHIT_GARG)
  @Category(UnitTests.class)
  public void testUpdateFileOpsPreChecksBitbucket() {
    String currentCommitId = "DUMMY_COMMIT_ID";
    String newCommitId = "NEW_COMMIT_ID";
    when(scmConnector.getConnectorType()).thenReturn(BITBUCKET);
    when(scmGitProviderMapper.mapToSCMGitProvider(any(), eq(true))).thenReturn(Provider.newBuilder().build());
    when(scmGitProviderHelper.getSlug(any())).thenReturn(slug);
    when(scmBlockingStub.getLatestCommitOnFile(any()))
        .thenReturn(GetLatestCommitOnFileResponse.newBuilder().setCommitId(newCommitId).build());
    // conflict commit id case
    Optional<UpdateFileResponse> response = scmServiceClient.runUpdateFileOpsPreChecks(scmConnector, scmBlockingStub,
        GitFileDetails.builder().commitId(currentCommitId).branch(branch).filePath(filepath).build());
    assertThat(response).isNotNull();
    UpdateFileResponse updateFileResponse = response.orElseGet(() -> UpdateFileResponse.newBuilder().build());
    assertThat(updateFileResponse.getStatus() == SCM_CONFLICT_ERROR_CODE).isTrue();
    assertThat(updateFileResponse.getError().equals(SCM_CONFLICT_ERROR_MESSAGE)).isTrue();
    assertThat(updateFileResponse.getCommitId().equals(newCommitId)).isTrue();

    // non-conflict commit id case
    when(scmBlockingStub.getLatestCommitOnFile(any()))
        .thenReturn(GetLatestCommitOnFileResponse.newBuilder().setCommitId(currentCommitId).build());
    response = scmServiceClient.runUpdateFileOpsPreChecks(scmConnector, scmBlockingStub,
        GitFileDetails.builder().commitId(currentCommitId).branch(branch).filePath(filepath).build());
    assertThat(response).isNotNull();
    assertThat(response.equals(Optional.empty())).isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.MOHIT_GARG)
  @Category(UnitTests.class)
  public void testEmptyCommitIdWhenCreateFile() {
    when(scmGitProviderMapper.mapToSCMGitProvider(any(), eq(true))).thenReturn(Provider.newBuilder().build());
    when(scmGitProviderHelper.getSlug(any())).thenReturn(slug);
    when(scmBlockingStub.createFile(any()))
        .thenReturn(CreateFileResponse.newBuilder().setStatus(200).setCommitId("").build());
    CreateFileResponse createFileResponse =
        scmServiceClient.createFile(scmConnector, getGitFileDetailsDefault(), scmBlockingStub);
    assertThat(createFileResponse).isNotNull();
    assertThat(createFileResponse.getStatus() == SCM_INTERNAL_SERVER_ERROR_CODE).isTrue();
    assertThat(createFileResponse.getError().equals(SCM_INTERNAL_SERVER_ERROR_MESSAGE)).isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.MOHIT_GARG)
  @Category(UnitTests.class)
  public void testEmptyCommitIdWhenUpdateFile() {
    when(scmGitProviderMapper.mapToSCMGitProvider(any(), eq(true))).thenReturn(Provider.newBuilder().build());
    when(scmGitProviderHelper.getSlug(any())).thenReturn(slug);
    when(scmBlockingStub.updateFile(any()))
        .thenReturn(UpdateFileResponse.newBuilder().setStatus(200).setCommitId("").build());
    UpdateFileResponse updateFileResponse =
        scmServiceClient.updateFile(scmConnector, getGitFileDetailsDefault(), scmBlockingStub);
    assertThat(updateFileResponse).isNotNull();
    assertThat(updateFileResponse.getStatus() == SCM_INTERNAL_SERVER_ERROR_CODE).isTrue();
    assertThat(updateFileResponse.getError().equals(SCM_INTERNAL_SERVER_ERROR_MESSAGE)).isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.MOHIT_GARG)
  @Category(UnitTests.class)
  public void testUpdateFileDuringConflict() {
    String conflictCommitId = "conflictCommitId";
    when(scmConnector.getConnectorType()).thenReturn(BITBUCKET);
    when(scmGitProviderMapper.mapToSCMGitProvider(any(), eq(true))).thenReturn(Provider.newBuilder().build());
    when(scmGitProviderHelper.getSlug(any())).thenReturn(slug);
    when(scmBlockingStub.getLatestCommitOnFile(any()))
        .thenReturn(GetLatestCommitOnFileResponse.newBuilder().setCommitId(conflictCommitId).build());
    UpdateFileResponse updateFileResponse =
        scmServiceClient.updateFile(scmConnector, getGitFileDetailsDefault(), scmBlockingStub);
    assertThat(updateFileResponse).isNotNull();
    assertThat(updateFileResponse.equals(getErrorUpdateFileResponse(conflictCommitId))).isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.MOHIT_GARG)
  @Category(UnitTests.class)
  public void testErrorForCreateWebhookAPI() {
    PowerMockito.mockStatic(ScmGitWebhookHelper.class);
    ScmServiceClientImpl scmService = spy(scmServiceClient);

    when(scmGitProviderHelper.getSlug(any())).thenReturn(slug);
    when(scmGitProviderMapper.mapToSCMGitProvider(any())).thenReturn(getGitProviderDefault());
    when(ScmGitWebhookHelper.getCreateWebhookRequest(any(), any(), any(), any()))
        .thenReturn(CreateWebhookRequest.newBuilder().build());
    when(scmBlockingStub.createWebhook(any())).thenReturn(CreateWebhookResponse.newBuilder().setStatus(300).build());
    assertThatThrownBy(() -> scmService.createWebhook(scmConnector, getGitWebhookDetails(), scmBlockingStub))
        .isInstanceOf(UnexpectedException.class);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testGetRepoDetails() {
    when(scmGitProviderHelper.getSlug(any())).thenReturn(slug);
    when(scmGitProviderMapper.mapToSCMGitProvider(any())).thenReturn(getGitProviderDefault());
    when(scmBlockingStub.getUserRepo(any()))
        .thenReturn(
            GetUserRepoResponse.newBuilder().setRepo(Repository.newBuilder().setBranch(branch).build()).build());
    GetUserRepoResponse getUserRepoResponse = scmServiceClient.getRepoDetails(scmConnector, scmBlockingStub);
    assertThat(getUserRepoResponse.getRepo().getBranch()).isEqualTo(branch);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testCreateNewBranchV2_WhenGetLatestCommitFails() {
    when(scmGitProviderHelper.getSlug(any())).thenReturn(slug);
    when(scmGitProviderMapper.mapToSCMGitProvider(any())).thenReturn(getGitProviderDefault());
    when(scmBlockingStub.getLatestCommit(any()))
        .thenReturn(GetLatestCommitResponse.newBuilder().setStatus(404).setError(error).build());
    CreateBranchResponse createBranchResponse =
        scmServiceClient.createNewBranchV2(scmConnector, branch, baseBranchName, scmBlockingStub);
    assertThat(createBranchResponse.getError()).isEqualTo(error);
    assertThat(createBranchResponse.getStatus()).isEqualTo(404);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testCreateNewBranchV2() {
    when(scmGitProviderHelper.getSlug(any())).thenReturn(slug);
    when(scmGitProviderMapper.mapToSCMGitProvider(any())).thenReturn(getGitProviderDefault());
    when(scmBlockingStub.getLatestCommit(any()))
        .thenReturn(GetLatestCommitResponse.newBuilder()
                        .setStatus(200)
                        .setCommit(Commit.newBuilder().setSha(sha).build())
                        .build());
    when(scmBlockingStub.createBranch(any())).thenReturn(CreateBranchResponse.newBuilder().setStatus(200).build());
    CreateBranchResponse createBranchResponse =
        scmServiceClient.createNewBranchV2(scmConnector, branch, baseBranchName, scmBlockingStub);

    assertThat(createBranchResponse.getStatus()).isEqualTo(200);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testCreatePullRequestV2() {
    when(scmGitProviderHelper.getSlug(any())).thenReturn(slug);
    when(scmGitProviderMapper.mapToSCMGitProvider(any())).thenReturn(getGitProviderDefault());
    when(scmBlockingStub.createPR(any())).thenReturn(CreatePRResponse.newBuilder().setStatus(200).setError("").build());

    CreatePRResponse createPRResponse =
        scmServiceClient.createPullRequestV2(scmConnector, baseBranchName, branch, "pr title", scmBlockingStub);

    assertThat(createPRResponse.getStatus()).isEqualTo(200);
  }

  private GitFileDetails getGitFileDetailsDefault() {
    return GitFileDetails.builder()
        .filePath(filepath)
        .branch(branch)
        .commitId(commitId)
        .commitMessage(commitMessage)
        .fileContent(fileContent)
        .oldFileSha(sha)
        .userEmail(userEmail)
        .userName(userName)
        .build();
  }

  private UpdateFileResponse getErrorUpdateFileResponse(String commitId) {
    return UpdateFileResponse.newBuilder()
        .setStatus(SCM_CONFLICT_ERROR_CODE)
        .setError(SCM_CONFLICT_ERROR_MESSAGE)
        .setCommitId(commitId)
        .build();
  }

  private GitWebhookDetails getGitWebhookDetails() {
    return GitWebhookDetails.builder().target(target).build();
  }

  private Provider getGitProviderDefault() {
    return Provider.newBuilder().build();
  }
}
