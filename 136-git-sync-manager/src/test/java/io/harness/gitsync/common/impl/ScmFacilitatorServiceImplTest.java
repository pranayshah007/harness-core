/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.BHAVYA;
import static io.harness.rule.OwnerRule.MOHIT_GARG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.ScmBadRequestException;
import io.harness.exception.WingsException;
import io.harness.gitsync.GitSyncTestBase;
import io.harness.gitsync.common.dtos.GitBranchesResponseDTO;
import io.harness.gitsync.common.dtos.GitRepositoryResponseDTO;
import io.harness.gitsync.common.dtos.ScmCommitFileResponseDTO;
import io.harness.gitsync.common.dtos.ScmCreateFileRequestDTO;
import io.harness.gitsync.common.dtos.ScmCreatePRRequestDTO;
import io.harness.gitsync.common.dtos.ScmCreatePRResponseDTO;
import io.harness.gitsync.common.dtos.ScmGetFileByBranchRequestDTO;
import io.harness.gitsync.common.dtos.ScmGetFileByCommitIdRequestDTO;
import io.harness.gitsync.common.dtos.ScmGetFileResponseDTO;
import io.harness.gitsync.common.dtos.ScmUpdateFileRequestDTO;
import io.harness.gitsync.common.helper.GitSyncConnectorHelper;
import io.harness.gitsync.common.service.ScmOrchestratorService;
import io.harness.ng.beans.PageRequest;
import io.harness.product.ci.scm.proto.CreateBranchResponse;
import io.harness.product.ci.scm.proto.CreateFileResponse;
import io.harness.product.ci.scm.proto.CreatePRResponse;
import io.harness.product.ci.scm.proto.FileContent;
import io.harness.product.ci.scm.proto.GetUserRepoResponse;
import io.harness.product.ci.scm.proto.GetUserReposResponse;
import io.harness.product.ci.scm.proto.ListBranchesWithDefaultResponse;
import io.harness.product.ci.scm.proto.Repository;
import io.harness.product.ci.scm.proto.UpdateFileResponse;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PL)
public class ScmFacilitatorServiceImplTest extends GitSyncTestBase {
  @Mock GitSyncConnectorHelper gitSyncConnectorHelper;
  @Mock ScmOrchestratorService scmOrchestratorService;
  ScmFacilitatorServiceImpl scmFacilitatorService;
  FileContent fileContent = FileContent.newBuilder().build();
  String accountIdentifier = "accountIdentifier";
  String projectIdentifier = "projectIdentifier";
  String orgIdentifier = "orgIdentifier";
  String repoURL = "https://github.com/harness";
  String yamlGitConfigIdentifier = "yamlGitConfigIdentifier";
  String filePath = "filePath";
  String repoName = "repoName";
  String branch = "branch";
  String defaultBranch = "default";
  String connectorRef = "connectorRef";
  String commitId = "commitId";
  String blobId = "blobId";
  String content = "content";
  ConnectorInfoDTO connectorInfo;
  PageRequest pageRequest;
  Scope scope;
  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
    scmFacilitatorService = new ScmFacilitatorServiceImpl(gitSyncConnectorHelper, scmOrchestratorService);
    pageRequest = PageRequest.builder().build();
    GithubConnectorDTO githubConnector = GithubConnectorDTO.builder()
                                             .connectionType(GitConnectionType.ACCOUNT)
                                             .apiAccess(GithubApiAccessDTO.builder().build())
                                             .url(repoURL)
                                             .build();
    connectorInfo = ConnectorInfoDTO.builder().connectorConfig(githubConnector).build();
    scope = getDefaultScope();
    when(gitSyncConnectorHelper.getScmConnector(any(), any(), any(), any()))
        .thenReturn((ScmConnector) connectorInfo.getConnectorConfig());
    when(gitSyncConnectorHelper.getScmConnectorForGivenRepo(any(), any(), any(), any(), any()))
        .thenReturn((ScmConnector) connectorInfo.getConnectorConfig());
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testListReposByRefConnector() {
    List<Repository> repositories =
        Arrays.asList(Repository.newBuilder().setName("repo1").setNamespace("harness").build(),
            Repository.newBuilder().setName("repo2").setNamespace("harness").build(),
            Repository.newBuilder().setName("repo3").setNamespace("harnessxy").build());
    GetUserReposResponse getUserReposResponse = GetUserReposResponse.newBuilder().addAllRepos(repositories).build();
    when(scmOrchestratorService.processScmRequestUsingConnectorSettings(any(), any())).thenReturn(getUserReposResponse);
    List<GitRepositoryResponseDTO> repositoryResponseDTOList = scmFacilitatorService.listReposByRefConnector(
        accountIdentifier, orgIdentifier, projectIdentifier, connectorRef, pageRequest, "");
    assertThat(repositoryResponseDTOList.size()).isEqualTo(2);
    assertThat(repositoryResponseDTOList.get(0).getName()).isEqualTo("repo1");
    assertThat(repositoryResponseDTOList.get(1).getName()).isEqualTo("repo2");
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testListBranchesV2() {
    ListBranchesWithDefaultResponse listBranchesWithDefaultResponse = ListBranchesWithDefaultResponse.newBuilder()
                                                                          .setDefaultBranch(defaultBranch)
                                                                          .addAllBranches(Arrays.asList(branch))
                                                                          .build();
    when(scmOrchestratorService.processScmRequestUsingConnectorSettings(any(), any()))
        .thenReturn(listBranchesWithDefaultResponse);
    GitBranchesResponseDTO gitBranchesResponseDTO = scmFacilitatorService.listBranchesV2(
        accountIdentifier, orgIdentifier, projectIdentifier, connectorRef, repoName, pageRequest, "");
    assertThat(gitBranchesResponseDTO.getDefaultBranch().getName()).isEqualTo(defaultBranch);
    assertThat(gitBranchesResponseDTO.getBranches().size()).isEqualTo(1);
    assertThat(gitBranchesResponseDTO.getBranches().get(0).getName()).isEqualTo(branch);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testGetDefaultBranch() {
    GetUserRepoResponse getUserRepoResponse =
        GetUserRepoResponse.newBuilder()
            .setRepo(Repository.newBuilder().setName(repoName).setBranch(defaultBranch).build())
            .build();
    when(scmOrchestratorService.processScmRequestUsingConnectorSettings(any(), any())).thenReturn(getUserRepoResponse);
    String branchName = scmFacilitatorService.getDefaultBranch(
        accountIdentifier, orgIdentifier, projectIdentifier, connectorRef, repoName);
    assertThat(branchName).isEqualTo(defaultBranch);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testCreateNewBranch() {
    String errorMessage = "Repo not exist";
    CreateBranchResponse createBranchResponse =
        CreateBranchResponse.newBuilder().setStatus(404).setError(errorMessage).build();
    when(scmOrchestratorService.processScmRequestUsingConnectorSettings(any(), any())).thenReturn(createBranchResponse);
    try {
      scmFacilitatorService.createNewBranch(
          scope, (ScmConnector) connectorInfo.getConnectorConfig(), branch, defaultBranch);
    } catch (Exception ex) {
      WingsException exception = ExceptionUtils.cause(ScmBadRequestException.class, ex);
      assertThat(exception).isNotNull();
      assertThat(exception.getMessage()).isEqualTo(errorMessage);
    }
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testCreateFileWhenSCMAPIsucceeds() {
    CreateFileResponse createFileResponse =
        CreateFileResponse.newBuilder().setBlobId(blobId).setCommitId(commitId).build();
    when(scmOrchestratorService.processScmRequestUsingConnectorSettings(any(), any())).thenReturn(createFileResponse);
    ScmCommitFileResponseDTO scmCommitFileResponseDTO =
        scmFacilitatorService.createFile(ScmCreateFileRequestDTO.builder().scope(Scope.builder().build()).build());
    assertThat(scmCommitFileResponseDTO.getCommitId()).isEqualTo(commitId);
    assertThat(scmCommitFileResponseDTO.getBlobId()).isEqualTo(blobId);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testCreateFileWhenSCMAPIfails() {
    CreateFileResponse createFileResponse = CreateFileResponse.newBuilder().setStatus(400).build();
    when(scmOrchestratorService.processScmRequestUsingConnectorSettings(any(), any())).thenReturn(createFileResponse);
    assertThatThrownBy(()
                           -> scmFacilitatorService.createFile(
                               ScmCreateFileRequestDTO.builder().scope(Scope.builder().build()).build()))
        .isInstanceOf(WingsException.class);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testUpdateFileWhenSCMAPIsucceeds() {
    UpdateFileResponse updateFileResponse =
        UpdateFileResponse.newBuilder().setBlobId(blobId).setCommitId(commitId).build();
    when(scmOrchestratorService.processScmRequestUsingConnectorSettings(any(), any())).thenReturn(updateFileResponse);
    ScmCommitFileResponseDTO scmCommitFileResponseDTO =
        scmFacilitatorService.updateFile(ScmUpdateFileRequestDTO.builder().scope(getDefaultScope()).build());
    assertThat(scmCommitFileResponseDTO.getCommitId()).isEqualTo(commitId);
    assertThat(scmCommitFileResponseDTO.getBlobId()).isEqualTo(blobId);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testUpdateFileWhenSCMAPIfails() {
    UpdateFileResponse updateFileResponse = UpdateFileResponse.newBuilder().setStatus(400).build();
    when(scmOrchestratorService.processScmRequestUsingConnectorSettings(any(), any())).thenReturn(updateFileResponse);
    assertThatThrownBy(
        () -> scmFacilitatorService.updateFile(ScmUpdateFileRequestDTO.builder().scope(getDefaultScope()).build()))
        .isInstanceOf(WingsException.class);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testCreatePRWhenSCMAPIsucceeds() {
    CreatePRResponse createPRResponse = CreatePRResponse.newBuilder().setNumber(0).build();
    when(scmOrchestratorService.processScmRequestUsingConnectorSettings(any(), any())).thenReturn(createPRResponse);
    ScmCreatePRResponseDTO scmCreatePRResponseDTO =
        scmFacilitatorService.createPR(ScmCreatePRRequestDTO.builder().scope(getDefaultScope()).build());
    assertThat(scmCreatePRResponseDTO.getPrNumber()).isEqualTo(0);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testCreatePRWhenSCMAPIfails() {
    CreatePRResponse createPRResponse = CreatePRResponse.newBuilder().setStatus(400).build();
    when(scmOrchestratorService.processScmRequestUsingConnectorSettings(any(), any())).thenReturn(createPRResponse);
    assertThatThrownBy(
        () -> scmFacilitatorService.createPR(ScmCreatePRRequestDTO.builder().scope(getDefaultScope()).build()))
        .isInstanceOf(WingsException.class);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testGetFileByBranchWhenSCMAPIsucceeds() {
    FileContent fileContent =
        FileContent.newBuilder().setContent(content).setBlobId(blobId).setCommitId(commitId).setPath(filePath).build();
    when(scmOrchestratorService.processScmRequestUsingConnectorSettings(any(), any())).thenReturn(fileContent);
    ScmGetFileResponseDTO scmGetFileResponseDTO = scmFacilitatorService.getFileByBranch(
        ScmGetFileByBranchRequestDTO.builder().scope(getDefaultScope()).branchName(branch).build());
    assertThat(scmGetFileResponseDTO.getBlobId()).isEqualTo(blobId);
    assertThat(scmGetFileResponseDTO.getCommitId()).isEqualTo(commitId);
    assertThat(scmGetFileResponseDTO.getFileContent()).isEqualTo(content);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testGetFileByBranchWhenSCMAPIfails() {
    FileContent fileContent = FileContent.newBuilder().setStatus(400).build();
    when(scmOrchestratorService.processScmRequestUsingConnectorSettings(any(), any())).thenReturn(fileContent);
    assertThatThrownBy(
        ()
            -> scmFacilitatorService.getFileByBranch(
                ScmGetFileByBranchRequestDTO.builder().scope(getDefaultScope()).branchName(branch).build()))
        .isInstanceOf(WingsException.class);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testGetFileByCommitIdWhenSCMAPIsucceeds() {
    FileContent fileContent =
        FileContent.newBuilder().setContent(content).setBlobId(blobId).setCommitId(commitId).setPath(filePath).build();
    when(scmOrchestratorService.processScmRequestUsingConnectorSettings(any(), any())).thenReturn(fileContent);
    ScmGetFileResponseDTO scmGetFileResponseDTO = scmFacilitatorService.getFileByCommitId(
        ScmGetFileByCommitIdRequestDTO.builder().scope(getDefaultScope()).commitId(commitId).build());
    assertThat(scmGetFileResponseDTO.getBlobId()).isEqualTo(blobId);
    assertThat(scmGetFileResponseDTO.getCommitId()).isEqualTo(commitId);
    assertThat(scmGetFileResponseDTO.getFileContent()).isEqualTo(content);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testGetFileByCommitIdWhenSCMAPIfails() {
    FileContent fileContent = FileContent.newBuilder().setStatus(400).build();
    when(scmOrchestratorService.processScmRequestUsingConnectorSettings(any(), any())).thenReturn(fileContent);
    assertThatThrownBy(
        ()
            -> scmFacilitatorService.getFileByCommitId(
                ScmGetFileByCommitIdRequestDTO.builder().scope(getDefaultScope()).commitId(commitId).build()))
        .isInstanceOf(WingsException.class);
  }

  private Scope getDefaultScope() {
    return Scope.builder()
        .accountIdentifier(accountIdentifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .build();
  }
}
