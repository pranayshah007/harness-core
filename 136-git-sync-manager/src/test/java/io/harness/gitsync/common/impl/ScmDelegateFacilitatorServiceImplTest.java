/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.impl;

import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.BHAVYA;
import static io.harness.rule.OwnerRule.DEEPAK;
import static io.harness.rule.OwnerRule.HARI;
import static io.harness.rule.OwnerRule.MOHIT_GARG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.GetBatchFileRequestIdentifier;
import io.harness.beans.PageRequestDTO;
import io.harness.beans.Scope;
import io.harness.beans.gitsync.GitFilePathDetails;
import io.harness.beans.gitsync.GitPRCreateRequest;
import io.harness.beans.request.GitFileBatchRequest;
import io.harness.beans.request.GitFileRequest;
import io.harness.beans.request.GitFileRequestV2;
import io.harness.beans.response.GitFileResponse;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.git.YamlGitConfigDTO;
import io.harness.delegate.task.scm.GitFileTaskResponseData;
import io.harness.delegate.task.scm.ScmBatchGetFileTaskParams;
import io.harness.delegate.task.scm.ScmGitFileTaskParams;
import io.harness.delegate.task.scm.ScmGitRefTaskResponseData;
import io.harness.delegate.task.scm.ScmPRTaskResponseData;
import io.harness.delegate.task.scm.ScmPushTaskResponseData;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.GitSyncTestBase;
import io.harness.gitsync.common.dtos.CreateGitFileRequestDTO;
import io.harness.gitsync.common.dtos.GetLatestCommitOnFileRequestDTO;
import io.harness.gitsync.common.dtos.GitFileContent;
import io.harness.gitsync.common.dtos.UpdateGitFileRequestDTO;
import io.harness.gitsync.common.helper.GitSyncConnectorHelper;
import io.harness.gitsync.common.service.YamlGitConfigService;
import io.harness.ng.beans.PageRequest;
import io.harness.product.ci.scm.proto.CreateBranchResponse;
import io.harness.product.ci.scm.proto.CreateFileResponse;
import io.harness.product.ci.scm.proto.CreatePRResponse;
import io.harness.product.ci.scm.proto.FileContent;
import io.harness.product.ci.scm.proto.GetLatestCommitOnFileResponse;
import io.harness.product.ci.scm.proto.GetUserRepoResponse;
import io.harness.product.ci.scm.proto.GetUserReposResponse;
import io.harness.product.ci.scm.proto.ListBranchesResponse;
import io.harness.product.ci.scm.proto.ListBranchesWithDefaultResponse;
import io.harness.product.ci.scm.proto.Repository;
import io.harness.product.ci.scm.proto.UpdateFileResponse;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.service.DelegateGrpcClientWrapper;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.DX)
public class ScmDelegateFacilitatorServiceImplTest extends GitSyncTestBase {
  @Mock SecretManagerClientService secretManagerClientService;
  @Mock ConnectorService connectorService;
  @Mock DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock YamlGitConfigService yamlGitConfigService;
  @Mock GitSyncConnectorHelper gitSyncConnectorHelper;
  ScmDelegateFacilitatorServiceImpl scmDelegateFacilitatorService;
  FileContent fileContent = FileContent.newBuilder().build();
  String accountIdentifier = "accountIdentifier";
  String projectIdentifier = "projectIdentifier";
  String orgIdentifier = "orgIdentifier";
  String connectorIdentifierRef = "connectorIdentifierRef";
  String repoURL = "repoURL";
  String yamlGitConfigIdentifier = "yamlGitConfigIdentifier";
  String filePath = "filePath";
  String branch = "branch";
  String connectorRef = "connectorRef";
  String repoName = "repoName";
  String commitId = "commitId";
  String defaultBranch = "default";
  String sourceBranch = "sourceBranch";
  String targetBranch = "targetBranch";
  String content = "content";
  String title = "title";
  int prNumber = 0;
  GithubConnectorDTO githubConnector;
  ConnectorInfoDTO connectorInfo;
  Scope scope;
  final ListBranchesResponse listBranchesResponse =
      ListBranchesResponse.newBuilder().addBranches("master").addBranches("feature").build();

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
    scmDelegateFacilitatorService = new ScmDelegateFacilitatorServiceImpl(connectorService, null, yamlGitConfigService,
        secretManagerClientService, delegateGrpcClientWrapper, null, gitSyncConnectorHelper);
    when(secretManagerClientService.getEncryptionDetails(any(), any())).thenReturn(Collections.emptyList());
    githubConnector = GithubConnectorDTO.builder().apiAccess(GithubApiAccessDTO.builder().build()).build();
    connectorInfo = ConnectorInfoDTO.builder().connectorConfig(githubConnector).build();
    scope = Scope.of(accountIdentifier, orgIdentifier, projectIdentifier);
    doReturn(Optional.of(ConnectorResponseDTO.builder().connector(connectorInfo).build()))
        .when(connectorService)
        .get(any(), any(), any(), any());
    doReturn((ScmConnector) connectorInfo.getConnectorConfig())
        .when(gitSyncConnectorHelper)
        .getScmConnector(any(), any(), any(), any());
    doNothing().when(gitSyncConnectorHelper).setUserGitCredsInConnectorIfPresent(anyString(), any());
    when(yamlGitConfigService.get(any(), any(), any(), any()))
        .thenReturn(YamlGitConfigDTO.builder()
                        .accountIdentifier(accountIdentifier)
                        .projectIdentifier(projectIdentifier)
                        .organizationIdentifier(orgIdentifier)
                        .gitConnectorRef(connectorIdentifierRef)
                        .build());
    when(gitSyncConnectorHelper.getScmConnector(any(), any(), any(), any(), any(), any()))
        .thenReturn((ScmConnector) connectorInfo.getConnectorConfig());
    doReturn(githubConnector)
        .when(gitSyncConnectorHelper)
        .getScmConnectorForGivenRepo(any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = HARI)
  @Category(UnitTests.class)
  public void listBranchesForRepoByConnectorTest() {
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(
            ScmGitRefTaskResponseData.builder().listBranchesResponse(listBranchesResponse.toByteArray()).build());
    final List<String> branches = scmDelegateFacilitatorService.listBranchesForRepoByConnector(accountIdentifier,
        orgIdentifier, projectIdentifier, connectorIdentifierRef, repoURL,
        PageRequest.builder().pageIndex(0).pageSize(10).build(), "");
    assertThat(branches).isEqualTo(listBranchesResponse.getBranchesList());
  }

  @Test
  @Owner(developers = HARI)
  @Category(UnitTests.class)
  public void getFileContentTest() {
    final ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestArgumentCaptor =
        ArgumentCaptor.forClass(DelegateTaskRequest.class);

    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(GitFileTaskResponseData.builder().fileContent(fileContent.toByteArray()).build());
    FileContent gitFileContent = scmDelegateFacilitatorService.getFile(
        accountIdentifier, orgIdentifier, projectIdentifier, connectorRef, repoName, branch, filePath, null);
    assertThat(gitFileContent).isEqualTo(fileContent);

    gitFileContent = scmDelegateFacilitatorService.getFile(
        accountIdentifier, orgIdentifier, projectIdentifier, connectorRef, repoName, branch, filePath, commitId);
    assertThat(gitFileContent).isEqualTo(fileContent);

    verify(delegateGrpcClientWrapper, times(2)).executeSyncTaskV2(delegateTaskRequestArgumentCaptor.capture());

    List<DelegateTaskRequest> delegateTaskRequestList = delegateTaskRequestArgumentCaptor.getAllValues();

    ScmGitFileTaskParams scmGitFileTaskParams =
        (ScmGitFileTaskParams) delegateTaskRequestList.get(0).getTaskParameters();
    assertThat(scmGitFileTaskParams.getBranch()).isEqualTo(branch);
    assertThat(scmGitFileTaskParams.getScmConnector()).isEqualTo(githubConnector);
    assertThat(scmGitFileTaskParams.getGitFilePathDetails())
        .isEqualTo(GitFilePathDetails.builder().filePath(filePath).branch(branch).ref(null).build());

    scmGitFileTaskParams = (ScmGitFileTaskParams) delegateTaskRequestList.get(1).getTaskParameters();
    assertThat(scmGitFileTaskParams.getBranch()).isEqualTo(branch);
    assertThat(scmGitFileTaskParams.getScmConnector()).isEqualTo(githubConnector);
    assertThat(scmGitFileTaskParams.getGitFilePathDetails())
        .isEqualTo(GitFilePathDetails.builder().filePath(filePath).branch(null).ref(commitId).build());
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void getFileTest() {
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(GitFileTaskResponseData.builder().fileContent(fileContent.toByteArray()).build());
    final GitFileContent gitFileContent = scmDelegateFacilitatorService.getFileContent(
        yamlGitConfigIdentifier, accountIdentifier, orgIdentifier, projectIdentifier, filePath, branch, null);
    assertThat(gitFileContent)
        .isEqualTo(
            GitFileContent.builder().content(fileContent.getContent()).objectId(fileContent.getBlobId()).build());
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void testCreatePRWithSameSourceTargetBranch() {
    GitPRCreateRequest createPRRequest =
        GitPRCreateRequest.builder().sourceBranch("branch").targetBranch("branch").build();
    assertThatThrownBy(() -> scmDelegateFacilitatorService.createPullRequest(createPRRequest))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void getListUserRepos() {
    GetUserReposResponse getUserReposResponse =
        GetUserReposResponse.newBuilder().addRepos(Repository.newBuilder().setName(repoName).build()).build();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(
            ScmGitRefTaskResponseData.builder().getUserReposResponse(getUserReposResponse.toByteArray()).build());
    getUserReposResponse = scmDelegateFacilitatorService.listUserRepos(accountIdentifier, orgIdentifier,
        projectIdentifier, (ScmConnector) connectorInfo.getConnectorConfig(), PageRequestDTO.builder().build());
    assertThat(getUserReposResponse.getReposCount()).isEqualTo(1);
    assertThat(getUserReposResponse.getRepos(0).getName()).isEqualTo(repoName);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testListBranches() {
    ListBranchesWithDefaultResponse listBranchesWithDefaultResponse = ListBranchesWithDefaultResponse.newBuilder()
                                                                          .addAllBranches(Arrays.asList(branch))
                                                                          .setDefaultBranch(defaultBranch)
                                                                          .build();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(ScmGitRefTaskResponseData.builder()
                        .getListBranchesWithDefaultResponse(listBranchesWithDefaultResponse.toByteArray())
                        .build());
    listBranchesWithDefaultResponse = scmDelegateFacilitatorService.listBranches(accountIdentifier, orgIdentifier,
        projectIdentifier, (ScmConnector) connectorInfo.getConnectorConfig(), PageRequestDTO.builder().build());
    assertThat(listBranchesWithDefaultResponse.getBranchesCount()).isEqualTo(1);
    assertThat(listBranchesWithDefaultResponse.getDefaultBranch()).isEqualTo(defaultBranch);
    assertThat(listBranchesWithDefaultResponse.getBranchesList().get(0)).isEqualTo(branch);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testGetRepoDetails() {
    GetUserRepoResponse getUserRepoResponse =
        GetUserRepoResponse.newBuilder()
            .setRepo(Repository.newBuilder().setName(repoName).setBranch(defaultBranch).build())
            .build();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(ScmGitRefTaskResponseData.builder().getUserRepoResponse(getUserRepoResponse.toByteArray()).build());
    getUserRepoResponse = scmDelegateFacilitatorService.getRepoDetails(
        accountIdentifier, orgIdentifier, projectIdentifier, (ScmConnector) connectorInfo.getConnectorConfig());
    assertThat(getUserRepoResponse.getRepo().getName()).isEqualTo(repoName);
    assertThat(getUserRepoResponse.getRepo().getBranch()).isEqualTo(defaultBranch);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testCreateNewBranch() {
    String errorMessage = "Repo not exist";
    CreateBranchResponse createBranchResponse =
        CreateBranchResponse.newBuilder().setStatus(404).setError(errorMessage).build();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(
            ScmGitRefTaskResponseData.builder().createBranchResponse(createBranchResponse.toByteArray()).build());
    createBranchResponse = scmDelegateFacilitatorService.createNewBranch(
        scope, (ScmConnector) connectorInfo.getConnectorConfig(), branch, defaultBranch);
    assertThat(createBranchResponse.getError()).isEqualTo(errorMessage);
    assertThat(createBranchResponse.getStatus()).isEqualTo(404);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testCreateFile() {
    CreateFileResponse createFileResponse =
        CreateFileResponse.newBuilder().setStatus(200).setCommitId(commitId).build();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(ScmPushTaskResponseData.builder().createFileResponse(createFileResponse.toByteArray()).build());
    CreateGitFileRequestDTO createGitFileRequestDTO =
        CreateGitFileRequestDTO.builder()
            .scope(scope)
            .branchName(branch)
            .filePath(filePath)
            .fileContent("content")
            .scmConnector((ScmConnector) connectorInfo.getConnectorConfig())
            .build();
    createFileResponse = scmDelegateFacilitatorService.createFile(createGitFileRequestDTO);
    assertThat(createFileResponse.getStatus()).isEqualTo(200);
    assertThat(createFileResponse.getCommitId()).isEqualTo(commitId);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testUpdateFile() {
    UpdateFileResponse updateFileResponse =
        UpdateFileResponse.newBuilder().setStatus(200).setCommitId(commitId).build();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(ScmPushTaskResponseData.builder().updateFileResponse(updateFileResponse.toByteArray()).build());
    UpdateGitFileRequestDTO updateGitFileRequestDTO =
        UpdateGitFileRequestDTO.builder()
            .scope(scope)
            .branchName(branch)
            .filePath(filePath)
            .fileContent("content")
            .oldCommitId("commit1")
            .scmConnector((ScmConnector) connectorInfo.getConnectorConfig())
            .build();
    updateFileResponse = scmDelegateFacilitatorService.updateFile(updateGitFileRequestDTO);
    assertThat(updateFileResponse.getStatus()).isEqualTo(200);
    assertThat(updateFileResponse.getCommitId()).isEqualTo(commitId);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testGetLatestCommitOnFile() {
    GetLatestCommitOnFileResponse getLatestCommitOnFileResponse =
        GetLatestCommitOnFileResponse.newBuilder().setCommitId(commitId).build();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(ScmGitRefTaskResponseData.builder()
                        .getLatestCommitOnFileResponse(getLatestCommitOnFileResponse.toByteArray())
                        .build());
    GetLatestCommitOnFileRequestDTO getLatestCommitOnFileRequestDTO =
        GetLatestCommitOnFileRequestDTO.builder()
            .branchName(branch)
            .filePath(filePath)
            .scmConnector((ScmConnector) connectorInfo.getConnectorConfig())
            .scope(Scope.builder()
                       .accountIdentifier(accountIdentifier)
                       .orgIdentifier(orgIdentifier)
                       .projectIdentifier(projectIdentifier)
                       .build())
            .build();
    getLatestCommitOnFileResponse =
        scmDelegateFacilitatorService.getLatestCommitOnFile(getLatestCommitOnFileRequestDTO);
    assertThat(getLatestCommitOnFileResponse.getCommitId()).isEqualTo(commitId);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testCreatePullRequest() {
    CreatePRResponse mockedCreatePRResponse = CreatePRResponse.newBuilder().setStatus(200).setNumber(prNumber).build();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(ScmPRTaskResponseData.builder().createPRResponse(mockedCreatePRResponse).build());
    CreatePRResponse createPRResponse = scmDelegateFacilitatorService.createPullRequest(
        getDefaultScope(), connectorRef, repoName, sourceBranch, targetBranch, title);
    assertThat(createPRResponse.getStatus()).isEqualTo(200);
    assertThat(createPRResponse.getNumber()).isEqualTo(prNumber);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testGetFile() {
    GitFileTaskResponseData gitFileTaskResponseData = GitFileTaskResponseData.builder()
                                                          .gitFileResponse(GitFileResponse.builder()
                                                                               .statusCode(200)
                                                                               .branch(branch)
                                                                               .commitId(commitId)
                                                                               .content(content)
                                                                               .filepath(filePath)
                                                                               .build())
                                                          .build();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(gitFileTaskResponseData);
    GitFileRequest gitFileRequest = GitFileRequest.builder().filepath(filePath).branch(branch).build();
    GitFileResponse gitFileResponse =
        scmDelegateFacilitatorService.getFile(scope, (ScmConnector) connectorInfo.getConnectorConfig(), gitFileRequest);
    assertThat(gitFileResponse.getBranch()).isEqualTo(branch);
    assertThat(gitFileResponse.getCommitId()).isEqualTo(commitId);
    assertThat(gitFileResponse.getFilepath()).isEqualTo(filePath);
    assertThat(gitFileResponse.getContent()).isEqualTo(content);
    assertThat(gitFileResponse.getError()).isEqualTo(null);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetFileForGetFileContentOnly() {
    GitFileTaskResponseData gitFileTaskResponseData = GitFileTaskResponseData.builder()
                                                          .gitFileResponse(GitFileResponse.builder()
                                                                               .statusCode(200)
                                                                               .branch(branch)
                                                                               .commitId(commitId)
                                                                               .content(content)
                                                                               .filepath(filePath)
                                                                               .build())
                                                          .build();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(gitFileTaskResponseData);
    GitFileRequest gitFileRequest =
        GitFileRequest.builder().filepath(filePath).branch(branch).getOnlyFileContent(true).build();
    GitFileResponse gitFileResponse =
        scmDelegateFacilitatorService.getFile(scope, (ScmConnector) connectorInfo.getConnectorConfig(), gitFileRequest);
    assertThat(gitFileResponse.getBranch()).isEqualTo(branch);
    assertThat(gitFileResponse.getCommitId()).isEqualTo(commitId);
    assertThat(gitFileResponse.getFilepath()).isEqualTo(filePath);
    assertThat(gitFileResponse.getContent()).isEqualTo(content);
    assertThat(gitFileResponse.getError()).isEqualTo(null);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testGetScmBatchGetFileTaskParams() {
    GitFileRequestV2 gitFileRequest1 = GitFileRequestV2.builder()
                                           .connectorRef("connector-1")
                                           .scope(getScope(accountIdentifier, orgIdentifier, projectIdentifier))
                                           .scmConnector(githubConnector)
                                           .build();
    GitFileRequestV2 gitFileRequest2 = GitFileRequestV2.builder()
                                           .connectorRef("connector-2")
                                           .scope(getScope(accountIdentifier, orgIdentifier, projectIdentifier))
                                           .scmConnector(githubConnector)
                                           .build();
    Map<GetBatchFileRequestIdentifier, GitFileRequestV2> getBatchFileRequestIdentifierGitFileRequestV2Map =
        new HashMap<>();
    GitFileBatchRequest gitFileBatchRequest =
        GitFileBatchRequest.builder()
            .accountIdentifier(accountIdentifier)
            .getBatchFileRequestIdentifierGitFileRequestV2Map(getBatchFileRequestIdentifierGitFileRequestV2Map)
            .build();
    getBatchFileRequestIdentifierGitFileRequestV2Map.put(getRandomRequestIdentifier(), gitFileRequest1);
    getBatchFileRequestIdentifierGitFileRequestV2Map.put(getRandomRequestIdentifier(), gitFileRequest2);
    ScmBatchGetFileTaskParams responseParams =
        scmDelegateFacilitatorService.getScmBatchGetFileTaskParams(gitFileBatchRequest);
    assertThat(responseParams.getGetFileTaskParamsPerConnectorList().size()).isEqualTo(2);

    GitFileRequestV2 gitFileRequest3 = GitFileRequestV2.builder()
                                           .connectorRef("connector-2")
                                           .scope(getScope(accountIdentifier, orgIdentifier, projectIdentifier))
                                           .scmConnector(githubConnector)
                                           .build();
    getBatchFileRequestIdentifierGitFileRequestV2Map.put(getRandomRequestIdentifier(), gitFileRequest3);
    responseParams = scmDelegateFacilitatorService.getScmBatchGetFileTaskParams(gitFileBatchRequest);
    assertThat(responseParams.getGetFileTaskParamsPerConnectorList().size()).isEqualTo(2);

    GitFileRequestV2 gitFileRequest4 = GitFileRequestV2.builder()
                                           .connectorRef("connector-2")
                                           .scope(getScope(null, orgIdentifier, projectIdentifier))
                                           .scmConnector(githubConnector)
                                           .build();
    getBatchFileRequestIdentifierGitFileRequestV2Map.put(getRandomRequestIdentifier(), gitFileRequest4);
    responseParams = scmDelegateFacilitatorService.getScmBatchGetFileTaskParams(gitFileBatchRequest);
    assertThat(responseParams.getGetFileTaskParamsPerConnectorList().size()).isEqualTo(3);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testGetEligibleScopeOfDelegates() {
    GitFileRequestV2 gitFileRequest1 =
        GitFileRequestV2.builder().scope(getScope(accountIdentifier, orgIdentifier, projectIdentifier)).build();
    GitFileRequestV2 gitFileRequest2 =
        GitFileRequestV2.builder().scope(getScope(accountIdentifier, orgIdentifier, projectIdentifier)).build();
    Map<GetBatchFileRequestIdentifier, GitFileRequestV2> getBatchFileRequestIdentifierGitFileRequestV2Map =
        new HashMap<>();
    GitFileBatchRequest gitFileBatchRequest =
        GitFileBatchRequest.builder()
            .accountIdentifier(accountIdentifier)
            .getBatchFileRequestIdentifierGitFileRequestV2Map(getBatchFileRequestIdentifierGitFileRequestV2Map)
            .build();
    getBatchFileRequestIdentifierGitFileRequestV2Map.put(getRandomRequestIdentifier(), gitFileRequest1);
    getBatchFileRequestIdentifierGitFileRequestV2Map.put(getRandomRequestIdentifier(), gitFileRequest2);
    Scope scope = scmDelegateFacilitatorService.getEligibleScopeOfDelegates(gitFileBatchRequest);
    assertThat(scope.getAccountIdentifier()).isEqualTo(accountIdentifier);
    assertThat(scope.getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(scope.getProjectIdentifier()).isEqualTo(projectIdentifier);

    GitFileRequestV2 gitFileRequest3 =
        GitFileRequestV2.builder().scope(getScope(accountIdentifier, orgIdentifier, null)).build();
    getBatchFileRequestIdentifierGitFileRequestV2Map.put(getRandomRequestIdentifier(), gitFileRequest3);
    scope = scmDelegateFacilitatorService.getEligibleScopeOfDelegates(gitFileBatchRequest);
    assertThat(scope.getAccountIdentifier()).isEqualTo(accountIdentifier);
    assertThat(scope.getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(scope.getProjectIdentifier()).isEqualTo(null);

    GitFileRequestV2 gitFileRequest4 =
        GitFileRequestV2.builder().scope(getScope(accountIdentifier, null, null)).build();
    getBatchFileRequestIdentifierGitFileRequestV2Map.put(getRandomRequestIdentifier(), gitFileRequest4);
    scope = scmDelegateFacilitatorService.getEligibleScopeOfDelegates(gitFileBatchRequest);
    assertThat(scope.getAccountIdentifier()).isEqualTo(accountIdentifier);
    assertThat(scope.getOrgIdentifier()).isEqualTo(null);
    assertThat(scope.getProjectIdentifier()).isEqualTo(null);
  }

  private Scope getDefaultScope() {
    return Scope.builder()
        .accountIdentifier(accountIdentifier)
        .projectIdentifier(projectIdentifier)
        .orgIdentifier(orgIdentifier)
        .build();
  }

  private Scope getScope(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return Scope.builder()
        .accountIdentifier(accountIdentifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .build();
  }

  private GetBatchFileRequestIdentifier getRandomRequestIdentifier() {
    return GetBatchFileRequestIdentifier.builder().identifier(UUID.randomUUID().toString()).build();
  }
}
