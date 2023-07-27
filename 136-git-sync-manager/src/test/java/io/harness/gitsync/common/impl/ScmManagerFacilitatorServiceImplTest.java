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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.BranchFilterParamsDTO;
import io.harness.beans.IdentifierRef;
import io.harness.beans.PageRequestDTO;
import io.harness.beans.RepoFilterParamsDTO;
import io.harness.beans.Scope;
import io.harness.beans.gitsync.GitFilePathDetails;
import io.harness.beans.request.GitFileRequest;
import io.harness.beans.response.GitFileResponse;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.git.YamlGitConfigDTO;
import io.harness.gitsync.GitSyncTestBase;
import io.harness.gitsync.common.beans.ConnectorDetails;
import io.harness.gitsync.common.dtos.CreateGitFileRequestDTO;
import io.harness.gitsync.common.dtos.GetLatestCommitOnFileRequestDTO;
import io.harness.gitsync.common.dtos.GitFileContent;
import io.harness.gitsync.common.dtos.UpdateGitFileRequestDTO;
import io.harness.gitsync.common.helper.GitSyncConnectorHelper;
import io.harness.gitsync.common.service.YamlGitConfigService;
import io.harness.ng.beans.PageRequest;
import io.harness.product.ci.scm.proto.Commit;
import io.harness.product.ci.scm.proto.CreateBranchResponse;
import io.harness.product.ci.scm.proto.CreateFileResponse;
import io.harness.product.ci.scm.proto.FileContent;
import io.harness.product.ci.scm.proto.GetLatestCommitOnFileResponse;
import io.harness.product.ci.scm.proto.GetLatestCommitResponse;
import io.harness.product.ci.scm.proto.GetUserRepoResponse;
import io.harness.product.ci.scm.proto.GetUserReposResponse;
import io.harness.product.ci.scm.proto.ListBranchesResponse;
import io.harness.product.ci.scm.proto.ListBranchesWithDefaultResponse;
import io.harness.product.ci.scm.proto.Repository;
import io.harness.product.ci.scm.proto.UpdateFileResponse;
import io.harness.rule.Owner;
import io.harness.service.ScmClient;
import io.harness.tasks.DecryptGitApiAccessHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(HarnessTeam.DX)
public class ScmManagerFacilitatorServiceImplTest extends GitSyncTestBase {
  @Mock ScmClient scmClient;
  @Mock DecryptGitApiAccessHelper decryptGitApiAccessHelper;
  @Mock ConnectorService connectorService;
  @Mock AbstractScmClientFacilitatorServiceImpl abstractScmClientFacilitatorService;
  @Mock YamlGitConfigService yamlGitConfigService;
  @Mock GitSyncConnectorHelper gitSyncConnectorHelper;
  @Spy @InjectMocks ScmManagerFacilitatorServiceImpl scmManagerFacilitatorService;
  String accountIdentifier = "accountIdentifier";
  String projectIdentifier = "projectIdentifier";
  String orgIdentifier = "orgIdentifier";
  String connectorIdentifierRef = "connectorIdentifierRef";
  String repoURL = "repoURL";
  String yamlGitConfigIdentifier = "yamlGitConfigIdentifier";
  String filePath = "filePath";
  String connectorRef = "connectorRef";
  final String branch = "branch";
  String repoName = "repoName";
  String commitId = "commitId";
  String defaultBranch = "default";
  FileContent fileContent = FileContent.newBuilder().build();
  String content = "content";
  GithubConnectorDTO githubConnector;
  ConnectorInfoDTO connectorInfo;
  Scope scope;
  final ListBranchesResponse listBranchesResponse =
      ListBranchesResponse.newBuilder().addBranches("master").addBranches("feature").build();

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
    when(decryptGitApiAccessHelper.decryptScmApiAccess(any(), any(), any(), any()))
        .thenReturn(GithubConnectorDTO.builder().build());
    when(scmClient.getFileContent(any(), any())).thenReturn(fileContent);
    when(scmClient.listBranches(any())).thenReturn(listBranchesResponse);
    githubConnector = GithubConnectorDTO.builder().apiAccess(GithubApiAccessDTO.builder().build()).build();
    connectorInfo = ConnectorInfoDTO.builder().connectorConfig(githubConnector).build();
    scope = Scope.of(accountIdentifier, orgIdentifier, projectIdentifier);
    doReturn(Optional.of(ConnectorResponseDTO.builder().connector(connectorInfo).build()))
        .when(connectorService)
        .get(anyString(), anyString(), anyString(), anyString());
    doReturn(githubConnector).when(gitSyncConnectorHelper).getDecryptedConnector(any(), any());
    doReturn(githubConnector)
        .when(gitSyncConnectorHelper)
        .getDecryptedConnector(anyString(), anyString(), anyString(), anyString());
    doReturn(githubConnector)
        .when(gitSyncConnectorHelper)
        .getDecryptedConnectorByRef(anyString(), anyString(), anyString(), anyString());
    doReturn(githubConnector)
        .when(gitSyncConnectorHelper)
        .getDecryptedConnectorForGivenRepo(anyString(), anyString(), anyString(), anyString(), anyString());
    when(abstractScmClientFacilitatorService.getYamlGitConfigDTO(
             accountIdentifier, orgIdentifier, projectIdentifier, yamlGitConfigIdentifier))
        .thenReturn(YamlGitConfigDTO.builder().build());
    when(abstractScmClientFacilitatorService.getConnectorIdentifierRef(any(), anyString(), anyString(), anyString()))
        .thenReturn(IdentifierRef.builder().build());
    when(yamlGitConfigService.get(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(YamlGitConfigDTO.builder()
                        .accountIdentifier(accountIdentifier)
                        .projectIdentifier(projectIdentifier)
                        .organizationIdentifier(orgIdentifier)
                        .gitConnectorRef(connectorIdentifierRef)
                        .build());
  }

  @Test
  @Owner(developers = HARI)
  @Category(UnitTests.class)
  public void listBranchesForRepoByConnectorTest() {
    final List<String> branches =
        scmManagerFacilitatorService.listBranchesForRepoByConnector(accountIdentifier, orgIdentifier, projectIdentifier,
            connectorIdentifierRef, repoURL, PageRequest.builder().pageIndex(0).pageSize(10).build(), "");
    assertThat(branches).isEqualTo(listBranchesResponse.getBranchesList());
  }

  @Test
  @Owner(developers = HARI)
  @Category(UnitTests.class)
  public void getFileContentTest() {
    final GitFileContent gitFileContent = scmManagerFacilitatorService.getFileContent(
        yamlGitConfigIdentifier, accountIdentifier, orgIdentifier, projectIdentifier, filePath, branch, null);
    assertThat(gitFileContent)
        .isEqualTo(
            GitFileContent.builder().content(fileContent.getContent()).objectId(fileContent.getBlobId()).build());
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void getFileTest() {
    final ArgumentCaptor<ScmConnector> scmConnectorArgumentCaptor = ArgumentCaptor.forClass(ScmConnector.class);
    final ArgumentCaptor<GitFilePathDetails> gitFilePathDetailsArgumentCaptor =
        ArgumentCaptor.forClass(GitFilePathDetails.class);
    FileContent gitFileContent = scmManagerFacilitatorService.getFile(
        accountIdentifier, orgIdentifier, projectIdentifier, connectorRef, repoName, branch, filePath, null);
    assertThat(gitFileContent).isEqualTo(fileContent);

    gitFileContent = scmManagerFacilitatorService.getFile(
        accountIdentifier, orgIdentifier, projectIdentifier, connectorRef, repoName, branch, filePath, commitId);
    verify(scmClient, times(2))
        .getFileContent(scmConnectorArgumentCaptor.capture(), gitFilePathDetailsArgumentCaptor.capture());

    List<ScmConnector> scmConnectors = scmConnectorArgumentCaptor.getAllValues();
    List<GitFilePathDetails> gitFilePathDetails = gitFilePathDetailsArgumentCaptor.getAllValues();

    assertThat(scmConnectors.get(0)).isEqualTo(githubConnector);
    assertThat(scmConnectors.get(1)).isEqualTo(githubConnector);
    assertThat(gitFilePathDetails.get(0))
        .isEqualTo(GitFilePathDetails.builder().filePath(filePath).branch(branch).ref(null).build());
    assertThat(gitFilePathDetails.get(1))
        .isEqualTo(GitFilePathDetails.builder().filePath(filePath).branch(null).ref(commitId).build());
    assertThat(gitFileContent).isEqualTo(fileContent);
  }

  @Test
  @Owner(developers = HARI)
  @Category(UnitTests.class)
  public void isSaasGitTest() {
    List<String> repoURLs = new ArrayList<>(Arrays.asList(
        "www.github.com", "http://www.gitlab.com", "www.github.harness.com", "harness.github.com", "github.com"));
    List<Boolean> expected = new ArrayList<>(Arrays.asList(true, true, false, false, true));
    List<Boolean> actual = new ArrayList<>();
    repoURLs.forEach(repoURL -> actual.add(GitUtils.isSaasGit(repoURL).isSaasGit()));
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void testGetLatestCommit() {
    String commitId = "commitId";
    ArgumentCaptor<String> branchNameCapture = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> ref = ArgumentCaptor.forClass(String.class);
    when(scmClient.getLatestCommit(any(), branchNameCapture.capture(), ref.capture()))
        .thenReturn(
            GetLatestCommitResponse.newBuilder().setCommit(Commit.newBuilder().setSha(commitId).build()).build());
    YamlGitConfigDTO yamlGitConfigDTO = YamlGitConfigDTO.builder().branch("default").build();
    final Commit returnedCommit = scmManagerFacilitatorService.getLatestCommit(yamlGitConfigDTO, "branch1");
    assertThat(returnedCommit.getSha()).isEqualTo(commitId);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testListUserRepos() {
    Repository repoDetails = Repository.newBuilder().setName(repoName).build();
    when(scmClient.getUserRepos(any(), any(), any()))
        .thenReturn(GetUserReposResponse.newBuilder().addRepos(repoDetails).build());
    final GetUserReposResponse userReposResponse = scmManagerFacilitatorService.listUserRepos(accountIdentifier,
        orgIdentifier, projectIdentifier, (ScmConnector) connectorInfo.getConnectorConfig(),
        PageRequestDTO.builder().build(), RepoFilterParamsDTO.builder().build());
    assertThat(userReposResponse.getReposCount()).isEqualTo(1);
    assertThat(userReposResponse.getRepos(0).getName()).isEqualTo(repoName);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testListUserReposWithRepoFilters() {
    Repository repoDetails = Repository.newBuilder().setName(repoName).build();
    when(scmClient.getUserRepos(any(), any(), any()))
        .thenReturn(GetUserReposResponse.newBuilder().addRepos(repoDetails).build());
    final GetUserReposResponse userReposResponse = scmManagerFacilitatorService.listUserRepos(accountIdentifier,
        orgIdentifier, projectIdentifier, (ScmConnector) connectorInfo.getConnectorConfig(),
        PageRequestDTO.builder().build(), RepoFilterParamsDTO.builder().repoName("repo").build());
    assertThat(userReposResponse.getReposCount()).isEqualTo(1);
    assertThat(userReposResponse.getRepos(0).getName()).isEqualTo(repoName);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testListBranches() {
    when(scmClient.listBranchesWithDefault(any(), any(), any()))
        .thenReturn(ListBranchesWithDefaultResponse.newBuilder()
                        .addAllBranches(Arrays.asList(branch))
                        .setDefaultBranch(defaultBranch)
                        .build());
    final ListBranchesWithDefaultResponse listBranchesWithDefaultResponse = scmManagerFacilitatorService.listBranches(
        accountIdentifier, orgIdentifier, projectIdentifier, (ScmConnector) connectorInfo.getConnectorConfig(),
        PageRequestDTO.builder().build(), BranchFilterParamsDTO.builder().build());
    assertThat(listBranchesWithDefaultResponse.getBranchesCount()).isEqualTo(1);
    assertThat(listBranchesWithDefaultResponse.getDefaultBranch()).isEqualTo(defaultBranch);
    assertThat(listBranchesWithDefaultResponse.getBranchesList().get(0)).isEqualTo(branch);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testListBranchesWithBranchFilters() {
    when(scmClient.listBranchesWithDefault(any(), any(), any()))
        .thenReturn(ListBranchesWithDefaultResponse.newBuilder().addAllBranches(Arrays.asList(branch)).build());
    final ListBranchesWithDefaultResponse listBranchesWithDefaultResponse = scmManagerFacilitatorService.listBranches(
        accountIdentifier, orgIdentifier, projectIdentifier, (ScmConnector) connectorInfo.getConnectorConfig(),
        PageRequestDTO.builder().build(), BranchFilterParamsDTO.builder().branchName(branch).build());
    assertThat(listBranchesWithDefaultResponse.getBranchesCount()).isEqualTo(1);
    assertThat(listBranchesWithDefaultResponse.getBranchesList().get(0)).isEqualTo(branch);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testGetRepoDetails() {
    when(scmClient.getRepoDetails(any()))
        .thenReturn(GetUserRepoResponse.newBuilder()
                        .setRepo(Repository.newBuilder().setBranch(defaultBranch).setName(repoName).build())
                        .build());
    final GetUserRepoResponse getUserRepoResponse = scmManagerFacilitatorService.getRepoDetails(
        accountIdentifier, orgIdentifier, projectIdentifier, (ScmConnector) connectorInfo.getConnectorConfig());
    assertThat(getUserRepoResponse.getRepo().getName()).isEqualTo(repoName);
    assertThat(getUserRepoResponse.getRepo().getBranch()).isEqualTo(defaultBranch);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testCreateNewBranch() {
    when(scmClient.createNewBranchV2(any(), any(), any()))
        .thenReturn(CreateBranchResponse.newBuilder().setStatus(200).setError("").build());
    final CreateBranchResponse createBranchResponse = scmManagerFacilitatorService.createNewBranch(
        scope, (ScmConnector) connectorInfo.getConnectorConfig(), branch, defaultBranch);
    assertThat(createBranchResponse.getStatus()).isEqualTo(200);
    assertThat(createBranchResponse.getError()).isEmpty();
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testCreateFile() {
    when(scmClient.createFile(any(), any(), anyBoolean()))
        .thenReturn(CreateFileResponse.newBuilder().setStatus(200).setCommitId(commitId).build());
    CreateGitFileRequestDTO createGitFileRequestDTO =
        CreateGitFileRequestDTO.builder()
            .scope(scope)
            .branchName(branch)
            .filePath(filePath)
            .fileContent("content")
            .useGitClient(false)
            .scmConnector((ScmConnector) connectorInfo.getConnectorConfig())
            .build();
    final CreateFileResponse createFileResponse = scmManagerFacilitatorService.createFile(createGitFileRequestDTO);
    assertThat(createFileResponse.getStatus()).isEqualTo(200);
    assertThat(createFileResponse.getCommitId()).isEqualTo(commitId);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testUpdateFile() {
    when(scmClient.updateFile(any(), any(), anyBoolean()))
        .thenReturn(UpdateFileResponse.newBuilder().setStatus(200).setCommitId(commitId).build());
    UpdateGitFileRequestDTO updateGitFileRequestDTO =
        UpdateGitFileRequestDTO.builder()
            .scope(scope)
            .branchName(branch)
            .filePath(filePath)
            .fileContent("content")
            .oldCommitId("commit1")
            .useGitClient(false)
            .scmConnector((ScmConnector) connectorInfo.getConnectorConfig())
            .build();
    final UpdateFileResponse updateFileResponse = scmManagerFacilitatorService.updateFile(updateGitFileRequestDTO);
    assertThat(updateFileResponse.getStatus()).isEqualTo(200);
    assertThat(updateFileResponse.getCommitId()).isEqualTo(commitId);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testGetFile() {
    when(scmClient.getFile(any(), any()))
        .thenReturn(GitFileResponse.builder()
                        .statusCode(200)
                        .branch(branch)
                        .commitId(commitId)
                        .content(content)
                        .filepath(filePath)
                        .build());
    GitFileRequest gitFileRequest = GitFileRequest.builder().filepath(filePath).branch(branch).build();
    final GitFileResponse gitFileResponse =
        scmManagerFacilitatorService.getFile(scope, (ScmConnector) connectorInfo.getConnectorConfig(), gitFileRequest);
    assertThat(gitFileResponse.getBranch()).isEqualTo(branch);
    assertThat(gitFileResponse.getCommitId()).isEqualTo(commitId);
    assertThat(gitFileResponse.getFilepath()).isEqualTo(filePath);
    assertThat(gitFileResponse.getContent()).isEqualTo(content);
    assertThat(gitFileResponse.getError()).isEqualTo(null);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testGetLatestCommitOnFile() {
    when(scmClient.getLatestCommitOnFile(any(), anyString(), anyString()))
        .thenReturn(GetLatestCommitOnFileResponse.newBuilder().setCommitId(commitId).build());
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
    final GetLatestCommitOnFileResponse getLatestCommitOnFileResponse =
        scmManagerFacilitatorService.getLatestCommitOnFile(getLatestCommitOnFileRequestDTO);
    assertThat(getLatestCommitOnFileResponse.getCommitId()).isEqualTo(commitId);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testGetDecryptedScmConnector() {
    Map<ConnectorDetails, ScmConnector> decryptedConnectorMap = new HashMap<>();
    scmManagerFacilitatorService.getDecryptedScmConnector(decryptedConnectorMap,
        getScope(accountIdentifier, orgIdentifier, projectIdentifier), "connectorRef-1", githubConnector, repoName);
    assertThat(decryptedConnectorMap.size()).isEqualTo(1);
    scmManagerFacilitatorService.getDecryptedScmConnector(decryptedConnectorMap,
        getScope(null, orgIdentifier, projectIdentifier), "connectorRef-1", githubConnector, repoName);
    assertThat(decryptedConnectorMap.size()).isEqualTo(2);
    scmManagerFacilitatorService.getDecryptedScmConnector(
        decryptedConnectorMap, getScope(null, null, projectIdentifier), "connectorRef-1", githubConnector, repoName);
    assertThat(decryptedConnectorMap.size()).isEqualTo(3);
    scmManagerFacilitatorService.getDecryptedScmConnector(decryptedConnectorMap,
        getScope(null, orgIdentifier, projectIdentifier), "connectorRef-1", githubConnector, repoName);
    assertThat(decryptedConnectorMap.size()).isEqualTo(3);
    scmManagerFacilitatorService.getDecryptedScmConnector(decryptedConnectorMap,
        getScope(null, orgIdentifier, projectIdentifier), "connectorRef-2", githubConnector, repoName);
    assertThat(decryptedConnectorMap.size()).isEqualTo(4);
  }

  private Scope getScope(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return Scope.builder()
        .accountIdentifier(accountIdentifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .build();
  }
}
