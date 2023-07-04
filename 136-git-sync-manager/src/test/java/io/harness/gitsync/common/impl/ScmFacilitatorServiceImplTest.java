/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.BHAVYA;
import static io.harness.rule.OwnerRule.DEV_MITTAL;
import static io.harness.rule.OwnerRule.MOHIT_GARG;
import static io.harness.rule.OwnerRule.SHALINI;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.GetBatchFileRequestIdentifier;
import io.harness.beans.Scope;
import io.harness.beans.request.GitFileRequestV2;
import io.harness.beans.response.GitFileBatchResponse;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.encryption.SecretRefData;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ScmBadRequestException;
import io.harness.exception.ScmUnexpectedException;
import io.harness.exception.WingsException;
import io.harness.gitsync.GitSyncTestBase;
import io.harness.gitsync.caching.service.GitFileCacheService;
import io.harness.gitsync.common.dtos.GitBranchDetailsDTO;
import io.harness.gitsync.common.dtos.GitBranchesResponseDTO;
import io.harness.gitsync.common.dtos.GitRepositoryResponseDTO;
import io.harness.gitsync.common.dtos.ScmCommitFileResponseDTO;
import io.harness.gitsync.common.dtos.ScmCreateFileRequestDTO;
import io.harness.gitsync.common.dtos.ScmCreatePRRequestDTO;
import io.harness.gitsync.common.dtos.ScmCreatePRResponseDTO;
import io.harness.gitsync.common.dtos.ScmGetBatchFileRequestIdentifier;
import io.harness.gitsync.common.dtos.ScmGetBatchFilesByBranchRequestDTO;
import io.harness.gitsync.common.dtos.ScmGetFileByBranchRequestDTO;
import io.harness.gitsync.common.dtos.ScmGetFileByCommitIdRequestDTO;
import io.harness.gitsync.common.dtos.ScmGetFileResponseDTO;
import io.harness.gitsync.common.dtos.ScmGetFileUrlRequestDTO;
import io.harness.gitsync.common.dtos.ScmGetFileUrlResponseDTO;
import io.harness.gitsync.common.dtos.ScmUpdateFileRequestDTO;
import io.harness.gitsync.common.dtos.UserDetailsRequestDTO;
import io.harness.gitsync.common.dtos.UserDetailsResponseDTO;
import io.harness.gitsync.common.dtos.gitAccess.GithubAccessTokenDTO;
import io.harness.gitsync.common.helper.GitClientEnabledHelper;
import io.harness.gitsync.common.helper.GitDefaultBranchCacheHelper;
import io.harness.gitsync.common.helper.GitFilePathHelper;
import io.harness.gitsync.common.helper.GitRepoAllowlistHelper;
import io.harness.gitsync.common.helper.GitRepoHelper;
import io.harness.gitsync.common.helper.GitSyncConnectorHelper;
import io.harness.gitsync.common.service.ScmOrchestratorService;
import io.harness.gitsync.core.runnable.GitBackgroundCacheRefreshHelper;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.ng.beans.PageRequest;
import io.harness.product.ci.scm.proto.CreateBranchResponse;
import io.harness.product.ci.scm.proto.CreateFileResponse;
import io.harness.product.ci.scm.proto.CreatePRResponse;
import io.harness.product.ci.scm.proto.FileContent;
import io.harness.product.ci.scm.proto.GetLatestCommitOnFileResponse;
import io.harness.product.ci.scm.proto.GetUserRepoResponse;
import io.harness.product.ci.scm.proto.GetUserReposResponse;
import io.harness.product.ci.scm.proto.ListBranchesWithDefaultResponse;
import io.harness.product.ci.scm.proto.Repository;
import io.harness.product.ci.scm.proto.UpdateFileResponse;
import io.harness.rule.Owner;
import io.harness.utils.NGFeatureFlagHelperService;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PL)
public class ScmFacilitatorServiceImplTest extends GitSyncTestBase {
  @Mock GitSyncConnectorHelper gitSyncConnectorHelper;
  @Mock ScmOrchestratorService scmOrchestratorService;
  @Mock NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @Mock GitClientEnabledHelper gitClientEnabledHelper;
  @Mock ConnectorService connectorService;
  ScmFacilitatorServiceImpl scmFacilitatorService;
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
  String error = "error";
  ConnectorInfoDTO connectorInfo;
  PageRequest pageRequest;
  Scope scope;
  ScmConnector scmConnector;
  @Mock GitFileCacheService gitFileCacheService;
  DelegateServiceGrpcClient delegateServiceGrpcClient;

  @InjectMocks GitFilePathHelper gitFilePathHelper;
  @Mock GitFilePathHelper gitFilePathHelperMock;
  @Mock GitBackgroundCacheRefreshHelper gitBackgroundCacheRefreshHelper;

  @Mock GitDefaultBranchCacheHelper gitDefaultBranchCacheHelper;
  @Mock GitRepoAllowlistHelper gitRepoAllowlistHelper;

  String fileUrl = "https://github.com/harness/repoName/blob/branch/filePath";

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
    GitRepoHelper gitRepoHelper = new GitRepoHelper();
    scmFacilitatorService = new ScmFacilitatorServiceImpl(gitSyncConnectorHelper, connectorService,
        scmOrchestratorService, ngFeatureFlagHelperService, gitClientEnabledHelper, gitFileCacheService,
        gitFilePathHelper, delegateServiceGrpcClient, gitBackgroundCacheRefreshHelper, gitDefaultBranchCacheHelper,
        gitRepoHelper, gitRepoAllowlistHelper);
    pageRequest = PageRequest.builder().build();
    GithubConnectorDTO githubConnector = GithubConnectorDTO.builder()
                                             .connectionType(GitConnectionType.ACCOUNT)
                                             .apiAccess(GithubApiAccessDTO.builder().build())
                                             .url(repoURL)
                                             .build();
    connectorInfo = ConnectorInfoDTO.builder().connectorConfig(githubConnector).build();
    scope = getDefaultScope();
    scmConnector = (ScmConnector) connectorInfo.getConnectorConfig();
    when(gitSyncConnectorHelper.getScmConnector(any(), any(), any(), any())).thenReturn(scmConnector);
    when(gitSyncConnectorHelper.getScmConnectorForGivenRepo(any(), any(), any(), any(), any()))
        .thenReturn(scmConnector);
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
        accountIdentifier, orgIdentifier, projectIdentifier, connectorRef, pageRequest, "", false);
    assertThat(repositoryResponseDTOList.size()).isEqualTo(2);
    assertThat(repositoryResponseDTOList.get(0).getName()).isEqualTo("repo1");
    assertThat(repositoryResponseDTOList.get(1).getName()).isEqualTo("repo2");
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testListReposByRefConnectorNoOwner() {
    GithubConnectorDTO githubConnector = GithubConnectorDTO.builder()
                                             .connectionType(GitConnectionType.ACCOUNT)
                                             .apiAccess(GithubApiAccessDTO.builder().build())
                                             .url("https://github.com/")
                                             .build();
    connectorInfo = ConnectorInfoDTO.builder().connectorConfig(githubConnector).build();
    scmConnector = (ScmConnector) connectorInfo.getConnectorConfig();
    when(gitSyncConnectorHelper.getScmConnector(any(), any(), any(), any())).thenReturn(scmConnector);

    List<Repository> repositories =
        Arrays.asList(Repository.newBuilder().setName("repo1").setNamespace("harness").build(),
            Repository.newBuilder().setName("repo2").setNamespace("harness").build(),
            Repository.newBuilder().setName("repo3").setNamespace("harnessxy").build());
    GetUserReposResponse getUserReposResponse = GetUserReposResponse.newBuilder().addAllRepos(repositories).build();
    when(scmOrchestratorService.processScmRequestUsingConnectorSettings(any(), any())).thenReturn(getUserReposResponse);
    List<GitRepositoryResponseDTO> repositoryResponseDTOList = scmFacilitatorService.listReposByRefConnector(
        accountIdentifier, orgIdentifier, projectIdentifier, connectorRef, pageRequest, "", false);
    assertThat(repositoryResponseDTOList.size()).isEqualTo(3);
    assertThat(repositoryResponseDTOList.get(0).getName()).isEqualTo("harness/repo1");
    assertThat(repositoryResponseDTOList.get(1).getName()).isEqualTo("harness/repo2");
    assertThat(repositoryResponseDTOList.get(2).getName()).isEqualTo("harnessxy/repo3");
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
    assertThat(gitBranchesResponseDTO.getBranches().get(0).getName()).isEqualTo(defaultBranch);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testListBranchesV2_WithDuplicates() {
    ListBranchesWithDefaultResponse listBranchesWithDefaultResponse =
        ListBranchesWithDefaultResponse.newBuilder()
            .setDefaultBranch(defaultBranch)
            .addAllBranches(Arrays.asList(branch, branch, "branch1", "branch1"))
            .build();
    when(scmOrchestratorService.processScmRequestUsingConnectorSettings(any(), any()))
        .thenReturn(listBranchesWithDefaultResponse);
    GitBranchesResponseDTO gitBranchesResponseDTO = scmFacilitatorService.listBranchesV2(
        accountIdentifier, orgIdentifier, projectIdentifier, connectorRef, repoName, pageRequest, "");
    assertThat(gitBranchesResponseDTO.getDefaultBranch().getName()).isEqualTo(defaultBranch);
    assertThat(gitBranchesResponseDTO.getBranches().size()).isEqualTo(3);
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
    when(gitClientEnabledHelper.isGitClientEnabledInSettings(scope.getAccountIdentifier())).thenReturn(false);
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
    when(gitClientEnabledHelper.isGitClientEnabledInSettings(scope.getAccountIdentifier())).thenReturn(false);
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
    when(gitClientEnabledHelper.isGitClientEnabledInSettings(scope.getAccountIdentifier())).thenReturn(false);
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
    when(gitClientEnabledHelper.isGitClientEnabledInSettings(scope.getAccountIdentifier())).thenReturn(false);
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
    when(ngFeatureFlagHelperService.isEnabled(any(), any())).thenReturn(true);
    FileContent fileContent = FileContent.newBuilder()
                                  .setContent(content)
                                  .setBlobId(blobId)
                                  .setCommitId("commitIdOfHead")
                                  .setPath(filePath)
                                  .build();
    GetLatestCommitOnFileResponse getLatestCommitOnFileResponse =
        GetLatestCommitOnFileResponse.newBuilder().setCommitId(commitId).build();
    when(scmOrchestratorService.processScmRequestUsingConnectorSettings(any(), any()))
        .thenReturn(fileContent)
        .thenReturn(getLatestCommitOnFileResponse);
    when(ngFeatureFlagHelperService.isEnabled(any(), any())).thenReturn(false);
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
    when(ngFeatureFlagHelperService.isEnabled(any(), any())).thenReturn(false);
    FileContent fileContent = FileContent.newBuilder().setStatus(400).build();
    GetLatestCommitOnFileResponse getLatestCommitOnFileResponse = GetLatestCommitOnFileResponse.newBuilder().build();
    when(scmOrchestratorService.processScmRequestUsingConnectorSettings(any(), any()))
        .thenReturn(fileContent)
        .thenReturn(getLatestCommitOnFileResponse);
    assertThatThrownBy(
        ()
            -> scmFacilitatorService.getFileByBranch(
                ScmGetFileByBranchRequestDTO.builder().scope(getDefaultScope()).branchName(branch).build()))
        .isInstanceOf(WingsException.class);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testGetFileByBranchWhenGetLatestCommitOnFileSCMAPIfails() {
    when(ngFeatureFlagHelperService.isEnabled(any(), any())).thenReturn(false);
    FileContent fileContent = FileContent.newBuilder().setStatus(200).build();
    GetLatestCommitOnFileResponse getLatestCommitOnFileResponse =
        GetLatestCommitOnFileResponse.newBuilder().setError(error).build();
    when(scmOrchestratorService.processScmRequestUsingConnectorSettings(any(), any()))
        .thenReturn(fileContent)
        .thenReturn(getLatestCommitOnFileResponse);
    try {
      scmFacilitatorService.getFileByBranch(
          ScmGetFileByBranchRequestDTO.builder().scope(getDefaultScope()).branchName(branch).build());
    } catch (Exception exception) {
      assertThat(exception).isInstanceOf(ScmUnexpectedException.class);
      assertThat(exception.getMessage()).isEqualTo(error);
    }
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testGetFileByCommitIdWhenSCMAPIsucceeds() {
    when(ngFeatureFlagHelperService.isEnabled(any(), any())).thenReturn(false);
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

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetFileUrl() {
    ScmGetFileUrlRequestDTO fileUrlRequestDTO = ScmGetFileUrlRequestDTO.builder()
                                                    .scope(scope)
                                                    .branch(branch)
                                                    .connectorRef(connectorRef)
                                                    .commitId(commitId)
                                                    .filePath(filePath)
                                                    .repoName(repoName)
                                                    .build();
    ScmGetFileUrlResponseDTO scmGetFileUrlResponseDTO = scmFacilitatorService.getFileUrl(fileUrlRequestDTO);
    assertEquals(fileUrl, scmGetFileUrlResponseDTO.getFileURL());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetFileUrlWhenConnectorRefISWrong() {
    ScmGetFileUrlRequestDTO fileUrlRequestDTO = ScmGetFileUrlRequestDTO.builder()
                                                    .scope(scope)
                                                    .connectorRef(connectorRef)
                                                    .commitId(commitId)
                                                    .filePath(filePath)
                                                    .repoName(repoName)
                                                    .build();
    on(gitFilePathHelper).set("gitSyncConnectorHelper", gitSyncConnectorHelper);
    when(gitSyncConnectorHelper.getScmConnectorForGivenRepo(any(), any(), any(), any(), any()))
        .thenThrow(InvalidRequestException.class);
    assertThatThrownBy(() -> scmFacilitatorService.getFileUrl(fileUrlRequestDTO));
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testGetBatchFiles_Validations() {
    Map<ScmGetBatchFileRequestIdentifier, ScmGetFileByBranchRequestDTO> scmGetFileByBranchRequestDTOMap =
        new HashMap<>();
    scmGetFileByBranchRequestDTOMap.put(getBatchFileRequestIdentifier(UUID.randomUUID().toString()),
        ScmGetFileByBranchRequestDTO.builder().scope(getDefaultScope()).build());
    assertThatThrownBy(()
                           -> scmFacilitatorService.getBatchFilesByBranch(
                               ScmGetBatchFilesByBranchRequestDTO.builder()
                                   .accountIdentifier(UUID.randomUUID().toString())
                                   .scmGetFileByBranchRequestDTOMap(scmGetFileByBranchRequestDTOMap)
                                   .build()))
        .isInstanceOf(InvalidRequestException.class);

    Map<ScmGetBatchFileRequestIdentifier, ScmGetFileByBranchRequestDTO> scmGetFileByBranchRequestDTOMap2 =
        new HashMap<>();
    scmGetFileByBranchRequestDTOMap2.put(getBatchFileRequestIdentifier(UUID.randomUUID().toString()),
        ScmGetFileByBranchRequestDTO.builder()
            .scope(Scope.builder().accountIdentifier(accountIdentifier).build())
            .build());
    scmGetFileByBranchRequestDTOMap2.put(getBatchFileRequestIdentifier(UUID.randomUUID().toString()),
        ScmGetFileByBranchRequestDTO.builder()
            .scope(Scope.builder().accountIdentifier(accountIdentifier).orgIdentifier(orgIdentifier).build())
            .build());
    scmGetFileByBranchRequestDTOMap2.put(getBatchFileRequestIdentifier(UUID.randomUUID().toString()),
        ScmGetFileByBranchRequestDTO.builder()
            .scope(Scope.builder().accountIdentifier(accountIdentifier).projectIdentifier(projectIdentifier).build())
            .build());

    ScmGetBatchFilesByBranchRequestDTO scmGetBatchFilesByBranchRequestDTO =
        ScmGetBatchFilesByBranchRequestDTO.builder()
            .accountIdentifier(accountIdentifier)
            .scmGetFileByBranchRequestDTOMap(scmGetFileByBranchRequestDTOMap2)
            .build();
    ScmGetBatchFilesByBranchRequestDTO finalScmGetBatchFilesByBranchRequestDTO = scmGetBatchFilesByBranchRequestDTO;
    assertThatThrownBy(() -> finalScmGetBatchFilesByBranchRequestDTO.validate())
        .isInstanceOf(InvalidRequestException.class);

    Map<ScmGetBatchFileRequestIdentifier, ScmGetFileByBranchRequestDTO> scmGetFileByBranchRequestDTOMap3 =
        new HashMap<>();
    scmGetFileByBranchRequestDTOMap3.put(getBatchFileRequestIdentifier(UUID.randomUUID().toString()),
        ScmGetFileByBranchRequestDTO.builder()
            .scope(Scope.builder().accountIdentifier(accountIdentifier).build())
            .build());
    scmGetFileByBranchRequestDTOMap3.put(getBatchFileRequestIdentifier(UUID.randomUUID().toString()),
        ScmGetFileByBranchRequestDTO.builder()
            .scope(Scope.builder().accountIdentifier(accountIdentifier).orgIdentifier(orgIdentifier).build())
            .build());
    scmGetFileByBranchRequestDTOMap3.put(getBatchFileRequestIdentifier(UUID.randomUUID().toString()),
        ScmGetFileByBranchRequestDTO.builder()
            .scope(Scope.builder()
                       .accountIdentifier(accountIdentifier)
                       .orgIdentifier(orgIdentifier)
                       .projectIdentifier(projectIdentifier)
                       .build())
            .build());
    scmGetBatchFilesByBranchRequestDTO = ScmGetBatchFilesByBranchRequestDTO.builder()
                                             .accountIdentifier(accountIdentifier)
                                             .scmGetFileByBranchRequestDTOMap(scmGetFileByBranchRequestDTOMap3)
                                             .build();
    scmGetBatchFilesByBranchRequestDTO.validate();

    Map<ScmGetBatchFileRequestIdentifier, ScmGetFileByBranchRequestDTO> scmGetFileByBranchRequestDTOMap4 =
        new HashMap<>();
    scmGetFileByBranchRequestDTOMap4.put(getBatchFileRequestIdentifier(UUID.randomUUID().toString()),
        ScmGetFileByBranchRequestDTO.builder()
            .scope(Scope.builder().accountIdentifier(accountIdentifier).build())
            .build());
    scmGetFileByBranchRequestDTOMap4.put(getBatchFileRequestIdentifier(UUID.randomUUID().toString()),
        ScmGetFileByBranchRequestDTO.builder()
            .scope(Scope.builder().accountIdentifier(accountIdentifier).orgIdentifier(orgIdentifier).build())
            .build());
    scmGetFileByBranchRequestDTOMap4.put(getBatchFileRequestIdentifier(UUID.randomUUID().toString()),
        ScmGetFileByBranchRequestDTO.builder()
            .scope(Scope.builder()
                       .accountIdentifier(accountIdentifier)
                       .orgIdentifier("orgIdentifier2")
                       .projectIdentifier(projectIdentifier)
                       .build())
            .build());
    scmGetBatchFilesByBranchRequestDTO = ScmGetBatchFilesByBranchRequestDTO.builder()
                                             .accountIdentifier(accountIdentifier)
                                             .scmGetFileByBranchRequestDTOMap(scmGetFileByBranchRequestDTOMap4)
                                             .build();
    ScmGetBatchFilesByBranchRequestDTO finalScmGetBatchFilesByBranchRequestDTO2 = scmGetBatchFilesByBranchRequestDTO;
    assertThatThrownBy(() -> finalScmGetBatchFilesByBranchRequestDTO2.validate())
        .isInstanceOf(InvalidRequestException.class);

    Map<ScmGetBatchFileRequestIdentifier, ScmGetFileByBranchRequestDTO> scmGetFileByBranchRequestDTOMap5 =
        new HashMap<>();
    scmGetFileByBranchRequestDTOMap5.put(getBatchFileRequestIdentifier(UUID.randomUUID().toString()),
        ScmGetFileByBranchRequestDTO.builder()
            .scope(Scope.builder().accountIdentifier(accountIdentifier).build())
            .build());
    scmGetFileByBranchRequestDTOMap5.put(getBatchFileRequestIdentifier(UUID.randomUUID().toString()),
        ScmGetFileByBranchRequestDTO.builder()
            .scope(Scope.builder()
                       .accountIdentifier(accountIdentifier)
                       .orgIdentifier(orgIdentifier)
                       .projectIdentifier(projectIdentifier)
                       .build())
            .build());
    scmGetFileByBranchRequestDTOMap5.put(getBatchFileRequestIdentifier(UUID.randomUUID().toString()),
        ScmGetFileByBranchRequestDTO.builder()
            .scope(Scope.builder()
                       .accountIdentifier(accountIdentifier)
                       .orgIdentifier(orgIdentifier)
                       .projectIdentifier("projectIdentifier2")
                       .build())
            .build());
    scmGetBatchFilesByBranchRequestDTO = ScmGetBatchFilesByBranchRequestDTO.builder()
                                             .accountIdentifier(accountIdentifier)
                                             .scmGetFileByBranchRequestDTOMap(scmGetFileByBranchRequestDTOMap5)
                                             .build();
    ScmGetBatchFilesByBranchRequestDTO finalScmGetBatchFilesByBranchRequestDTO3 = scmGetBatchFilesByBranchRequestDTO;
    assertThatThrownBy(() -> finalScmGetBatchFilesByBranchRequestDTO3.validate())
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testProcessGitFileBatchRequest() {
    GitFileBatchResponse gitFileBatchResponse =
        scmFacilitatorService.processGitFileBatchRequest(null, new HashMap<>(), true);
    assertThat(gitFileBatchResponse.getGetBatchFileRequestIdentifierGitFileResponseMap()).isEmpty();

    Map<GetBatchFileRequestIdentifier, GitFileRequestV2> gitFileRequestMap = new HashMap<>();
    gitFileRequestMap.put(GetBatchFileRequestIdentifier.builder().identifier(UUID.randomUUID().toString()).build(),
        GitFileRequestV2.builder().build());
    scmFacilitatorService.processGitFileBatchRequest(null, gitFileRequestMap, true);
    verify(scmOrchestratorService, times(1)).processScmRequestUsingManager(any());
    verify(scmOrchestratorService, times(0)).processScmRequestUsingDelegate(any());

    scmFacilitatorService.processGitFileBatchRequest(null, gitFileRequestMap, false);
    verify(scmOrchestratorService, times(1)).processScmRequestUsingManager(any());
    verify(scmOrchestratorService, times(1)).processScmRequestUsingDelegate(any());
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetUserDetails() {
    UserDetailsRequestDTO userDetailsRequestDTO =
        UserDetailsRequestDTO.builder()
            .gitAccessDTO(GithubAccessTokenDTO.builder()
                              .tokenRef(SecretRefData.builder()
                                            .identifier("tokenRef")
                                            .scope(io.harness.encryption.Scope.ACCOUNT)
                                            .build())
                              .build())
            .build();
    UserDetailsResponseDTO userDetailsResponseDTO =
        UserDetailsResponseDTO.builder().userEmail("email").userName("userName").build();
    doReturn(userDetailsResponseDTO).when(scmOrchestratorService).processScmRequestUsingManager(any());
    assertEquals(userDetailsResponseDTO, scmFacilitatorService.getUserDetails(userDetailsRequestDTO));
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testPrepareGitBranchListWhenDefaultBranchIsPresentInList() {
    ListBranchesWithDefaultResponse listBranchesWithDefaultResponse =
        ListBranchesWithDefaultResponse.newBuilder()
            .setDefaultBranch(defaultBranch)
            .addAllBranches(Arrays.asList(branch, "branch1", defaultBranch))
            .build();
    List<GitBranchDetailsDTO> gitBranches = scmFacilitatorService.prepareGitBranchList(listBranchesWithDefaultResponse);
    assertEquals(3, gitBranches.size());
    assertEquals(defaultBranch, gitBranches.get(2).getName());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testPrepareGitBranchList() {
    ListBranchesWithDefaultResponse listBranchesWithDefaultResponse =
        ListBranchesWithDefaultResponse.newBuilder()
            .setDefaultBranch(defaultBranch)
            .addAllBranches(Arrays.asList(branch, "branch1"))
            .build();
    List<GitBranchDetailsDTO> gitBranches = scmFacilitatorService.prepareGitBranchList(listBranchesWithDefaultResponse);
    assertEquals(2, gitBranches.size());
    assertEquals(defaultBranch, gitBranches.get(1).getName());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testPrepareGitBranchListWhenBranchesAreEmpty() {
    ListBranchesWithDefaultResponse listBranchesWithDefaultResponse =
        ListBranchesWithDefaultResponse.newBuilder().setDefaultBranch(defaultBranch).build();
    List<GitBranchDetailsDTO> gitBranches = scmFacilitatorService.prepareGitBranchList(listBranchesWithDefaultResponse);
    assertEquals(0, gitBranches.size());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testListReposForConnectorOfRepoLevel() {
    GithubConnectorDTO githubConnector = GithubConnectorDTO.builder()
                                             .connectionType(GitConnectionType.REPO)
                                             .apiAccess(GithubApiAccessDTO.builder().build())
                                             .url("https://github.com/senjucanon2/test-repo")
                                             .build();
    connectorInfo = ConnectorInfoDTO.builder().connectorConfig(githubConnector).build();
    scmConnector = (ScmConnector) connectorInfo.getConnectorConfig();
    when(gitSyncConnectorHelper.getScmConnector(any(), any(), any(), any())).thenReturn(scmConnector);

    List<Repository> repositories =
        Arrays.asList(Repository.newBuilder().setName("repo1").setNamespace("harness").build(),
            Repository.newBuilder().setName("test-repo").setNamespace("harness").build());
    GetUserReposResponse getUserReposResponse = GetUserReposResponse.newBuilder().addAllRepos(repositories).build();
    when(scmOrchestratorService.processScmRequestUsingConnectorSettings(any(), any())).thenReturn(getUserReposResponse);
    List<GitRepositoryResponseDTO> repositoryResponseDTOList = scmFacilitatorService.listReposByRefConnector(
        accountIdentifier, orgIdentifier, projectIdentifier, connectorRef, pageRequest, "", false);
    assertThat(repositoryResponseDTOList.size()).isEqualTo(1);
    assertThat(repositoryResponseDTOList.get(0).getName()).isEqualTo("test-repo");
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testListReposForConnectorOfRepoLevelWithAccessDeniedToValidRepos() {
    GithubConnectorDTO githubConnector = GithubConnectorDTO.builder()
                                             .connectionType(GitConnectionType.REPO)
                                             .apiAccess(GithubApiAccessDTO.builder().build())
                                             .url("https://github.com/senjucanon2/test-repo")
                                             .build();
    connectorInfo = ConnectorInfoDTO.builder().connectorConfig(githubConnector).build();
    scmConnector = (ScmConnector) connectorInfo.getConnectorConfig();
    when(gitSyncConnectorHelper.getScmConnector(any(), any(), any(), any())).thenReturn(scmConnector);

    List<Repository> repositories =
        Arrays.asList(Repository.newBuilder().setName("repo1").setNamespace("harness").build(),
            Repository.newBuilder().setName("repo2").setNamespace("harness").build());
    GetUserReposResponse getUserReposResponse = GetUserReposResponse.newBuilder().addAllRepos(repositories).build();
    when(scmOrchestratorService.processScmRequestUsingConnectorSettings(any(), any())).thenReturn(getUserReposResponse);
    List<GitRepositoryResponseDTO> repositoryResponseDTOList = scmFacilitatorService.listReposByRefConnector(
        accountIdentifier, orgIdentifier, projectIdentifier, connectorRef, pageRequest, "", false);
    assertThat(repositoryResponseDTOList.get(0).getName()).isNull();
  }

  private Scope getDefaultScope() {
    return Scope.builder()
        .accountIdentifier(accountIdentifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .build();
  }

  private ScmGetBatchFileRequestIdentifier getBatchFileRequestIdentifier(String identifier) {
    return ScmGetBatchFileRequestIdentifier.builder().identifier(identifier).build();
  }
}
