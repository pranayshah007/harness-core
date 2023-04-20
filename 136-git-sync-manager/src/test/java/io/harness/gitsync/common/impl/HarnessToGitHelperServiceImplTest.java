/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.impl;

import static io.harness.gitsync.common.scmerrorhandling.ScmErrorCodeToHttpStatusCodeMapping.HTTP_200;
import static io.harness.logging.LoggingInitializer.initializeLogging;
import static io.harness.rule.OwnerRule.MEET;
import static io.harness.rule.OwnerRule.MOHIT_GARG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.ScopeIdentifiers;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.git.YamlGitConfigDTO;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.ScmUnauthorizedException;
import io.harness.exception.WingsException;
import io.harness.gitsync.BranchDetails;
import io.harness.gitsync.ChangeType;
import io.harness.gitsync.CreateFileRequest;
import io.harness.gitsync.CreatePRRequest;
import io.harness.gitsync.CreatePRResponse;
import io.harness.gitsync.ErrorDetails;
import io.harness.gitsync.FileInfo;
import io.harness.gitsync.GetFileRequest;
import io.harness.gitsync.GetFileResponse;
import io.harness.gitsync.GetRepoUrlRequest;
import io.harness.gitsync.GitMetaData;
import io.harness.gitsync.RepoDetails;
import io.harness.gitsync.UpdateFileRequest;
import io.harness.gitsync.common.dtos.GitSyncEntityDTO;
import io.harness.gitsync.common.dtos.ScmCommitFileResponseDTO;
import io.harness.gitsync.common.dtos.ScmCreatePRResponseDTO;
import io.harness.gitsync.common.dtos.ScmGetFileResponseDTO;
import io.harness.gitsync.common.helper.GitFilePathHelper;
import io.harness.gitsync.common.service.GitEntityService;
import io.harness.gitsync.common.service.ScmFacilitatorService;
import io.harness.gitsync.common.service.YamlGitConfigService;
import io.harness.ng.core.EntityDetail;
import io.harness.rule.Owner;

import com.google.protobuf.StringValue;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.PL)
@RunWith(MockitoJUnitRunner.class)
public class HarnessToGitHelperServiceImplTest extends CategoryTest {
  @InjectMocks HarnessToGitHelperServiceImpl harnessToGitHelperService;
  @Mock GitEntityService gitEntityService;
  @Mock YamlGitConfigService yamlGitConfigService;
  @Mock ScmFacilitatorService scmFacilitatorService;
  @Mock GitFilePathHelper gitFilePathHelper;

  String baseBranch = "baseBranch";
  String branch = "branch";
  String commitId = "commitId";
  String errorMessage = "errorMessage";
  String invalidRequestErrorMessage = "Invalid request: errorMessage";
  String accountId = "accountId";
  String orgId = "orgId";
  String projectId = "projectId";
  String projectId2 = "projectId2";
  String connectorRef = "connectorRef";
  String identifier = "identifier";
  String fileContent = "fileContent";
  String blobId = "blobId";
  String filePath = ".harness/filePath.yaml";
  String repoName = "repoName";
  String hintMessage = "hintMessage";
  String explanationMessage = "explanationMessage";
  String repoUrl = "repoUrl";
  ScopeIdentifiers scopeIdentifiers;
  int prNumber = 0;

  @Before
  public void before() {
    initializeLogging();
    scopeIdentifiers = ScopeIdentifiers.newBuilder()
                           .setOrgIdentifier(orgId)
                           .setAccountIdentifier(accountId)
                           .setProjectIdentifier(projectId)
                           .build();
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  @Ignore(value = "TODO")
  public void testIfConflictCommitIdPresent() {
    String lastCommitId = harnessToGitHelperService.fetchLastCommitIdForFile(
        getFileInfoDefault(commitId, ChangeType.MODIFY), getEntityDetailDefault());
    assertThat(lastCommitId).isEqualTo(commitId);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  @Ignore(value = "TODO")
  public void testFetchLastCommitIdForFileForAddChangeType() {
    String lastCommitId = harnessToGitHelperService.fetchLastCommitIdForFile(
        getFileInfoDefault(commitId, ChangeType.ADD), getEntityDetailDefault());
    assertThat(lastCommitId).isEqualTo(commitId);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  @Ignore(value = "TODO")
  public void testFetchLastCommitIdForFileUpdateCase() {
    ArgumentCaptor<String> branchArgumentCaptor = ArgumentCaptor.forClass(String.class);
    when(gitEntityService.get(any(), any(), any())).thenReturn(getGitSyncEntityDTODefault());
    String lastCommitId = harnessToGitHelperService.fetchLastCommitIdForFile(
        getFileInfoDefault("", ChangeType.MODIFY), getEntityDetailDefault());
    verify(gitEntityService, times(1)).get(any(), any(), branchArgumentCaptor.capture());
    assertThat(branchArgumentCaptor.getValue()).isEqualTo(branch);
    assertThat(lastCommitId).isEqualTo(commitId);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  @Ignore(value = "TODO")
  public void testFetchLastCommitIdForFileUpdateToNewBranchCase() {
    ArgumentCaptor<String> branchArgumentCaptor = ArgumentCaptor.forClass(String.class);
    when(gitEntityService.get(any(), any(), any())).thenReturn(getGitSyncEntityDTODefault());
    String lastCommitId = harnessToGitHelperService.fetchLastCommitIdForFile(
        getFileInfoDefault("", ChangeType.MODIFY, true), getEntityDetailDefault());
    verify(gitEntityService, times(1)).get(any(), any(), branchArgumentCaptor.capture());
    assertThat(branchArgumentCaptor.getValue()).isEqualTo(baseBranch);
    assertThat(lastCommitId).isEqualTo(commitId);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  @Ignore(value = "TODO")
  public void testGetBranchDetails() {
    RepoDetails repoDetails = RepoDetails.newBuilder()
                                  .setAccountId(accountId)
                                  .setOrgIdentifier(StringValue.of(orgId))
                                  .setProjectIdentifier(StringValue.of(projectId))
                                  .setYamlGitConfigId(identifier)
                                  .build();
    RepoDetails repoDetails2 = RepoDetails.newBuilder()
                                   .setAccountId(accountId)
                                   .setOrgIdentifier(StringValue.of(orgId))
                                   .setProjectIdentifier(StringValue.of(projectId2))
                                   .setYamlGitConfigId(identifier)
                                   .build();
    YamlGitConfigDTO yamlGitConfigDTO = YamlGitConfigDTO.builder().branch(branch).build();
    BranchDetails branchDetails = BranchDetails.newBuilder().setDefaultBranch(branch).build();
    when(yamlGitConfigService.get(projectId, orgId, accountId, identifier)).thenReturn(yamlGitConfigDTO);
    assertThat(harnessToGitHelperService.getBranchDetails(repoDetails)).isEqualTo(branchDetails);

    BranchDetails branchDetails2 = BranchDetails.newBuilder().setError(invalidRequestErrorMessage).build();
    when(yamlGitConfigService.get(projectId2, orgId, accountId, identifier))
        .thenThrow(new InvalidRequestException(errorMessage));
    assertThat(harnessToGitHelperService.getBranchDetails(repoDetails2)).isEqualTo(branchDetails2);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  @Ignore("Not working after jdk upgrade") // todo: @Mohit
  public void testGetFileByBranchWhenSCMOpsIsSuccess() {
    GetFileRequest getFileRequest = getGetFileRequestDefault();
    when(scmFacilitatorService.getFileByBranch(any()))
        .thenReturn(ScmGetFileResponseDTO.builder()
                        .fileContent(fileContent)
                        .commitId(commitId)
                        .blobId(blobId)
                        .branchName(branch)
                        .build());
    GetFileResponse getFileResponse = harnessToGitHelperService.getFileByBranch(getFileRequest);

    assertThat(getFileResponse.getFileContent()).isEqualTo(fileContent);
    assertThat(getFileResponse.getStatusCode()).isEqualTo(HTTP_200);
    assertGitMetaData(getFileResponse.getGitMetaData(),
        GitMetaData.newBuilder()
            .setBlobId(blobId)
            .setFilePath(filePath)
            .setCommitId(commitId)
            .setRepoName(repoName)
            .setBranchName(branch)
            .build());
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testGetFileByBranchWhenSCMExceptionOccurs() {
    GetFileRequest getFileRequest = getGetFileRequestDefault();
    when(scmFacilitatorService.getFileByBranch(any())).thenThrow(getInvalidCredsDefaultException());
    GetFileResponse getFileResponse = harnessToGitHelperService.getFileByBranch(getFileRequest);

    assertThat(getFileResponse.getStatusCode()).isEqualTo(401);
    assertGitErrorDetails(getFileResponse.getError(),
        ErrorDetails.newBuilder()
            .setErrorMessage(errorMessage)
            .setHintMessage(hintMessage)
            .setExplanationMessage(explanationMessage)
            .build());
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testGetFileByBranchWhenWingsExceptionOccurs() {
    GetFileRequest getFileRequest = getGetFileRequestDefault();
    when(scmFacilitatorService.getFileByBranch(any())).thenThrow(getDefaultWingsException());
    GetFileResponse getFileResponse = harnessToGitHelperService.getFileByBranch(getFileRequest);

    assertThat(getFileResponse.getStatusCode()).isEqualTo(400);
    assertThat(getFileResponse.getError().getErrorMessage())
        .isEqualTo(ExceptionUtils.getMessage(getDefaultWingsException()));
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  @Ignore("Not working after jdk upgrade") // todo: @Mohit
  public void testCreateFileWhenSCMOpsIsSuccess() {
    CreateFileRequest createFileRequest = getCreateFileRequestDefault();
    when(scmFacilitatorService.createFile(any()))
        .thenReturn(ScmCommitFileResponseDTO.builder().commitId(commitId).blobId(blobId).build());
    io.harness.gitsync.CreateFileResponse createFileResponse = harnessToGitHelperService.createFile(createFileRequest);

    assertThat(createFileResponse.getStatusCode()).isEqualTo(HTTP_200);
    assertGitMetaData(createFileResponse.getGitMetaData(),
        GitMetaData.newBuilder()
            .setBlobId(blobId)
            .setFilePath(filePath)
            .setCommitId(commitId)
            .setRepoName(repoName)
            .setBranchName(branch)
            .build());
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testCreateFileWhenSCMExceptionOccurs() {
    CreateFileRequest createFileRequest = getCreateFileRequestDefault();
    when(scmFacilitatorService.createFile(any())).thenThrow(getInvalidCredsDefaultException());
    io.harness.gitsync.CreateFileResponse createFileResponse = harnessToGitHelperService.createFile(createFileRequest);

    assertThat(createFileResponse.getStatusCode()).isEqualTo(401);
    assertGitErrorDetails(createFileResponse.getError(),
        ErrorDetails.newBuilder()
            .setErrorMessage(errorMessage)
            .setHintMessage(hintMessage)
            .setExplanationMessage(explanationMessage)
            .build());
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testCreateFileWhenWingsExceptionOccurs() {
    CreateFileRequest createFileRequest = getCreateFileRequestDefault();
    when(scmFacilitatorService.createFile(any())).thenThrow(getDefaultWingsException());
    io.harness.gitsync.CreateFileResponse createFileResponse = harnessToGitHelperService.createFile(createFileRequest);

    assertThat(createFileResponse.getStatusCode()).isEqualTo(400);
    assertThat(createFileResponse.getError().getErrorMessage())
        .isEqualTo(ExceptionUtils.getMessage(getDefaultWingsException()));
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  @Ignore("Not working after jdk upgrade") // todo: @Mohit
  public void testUpdateFileWhenSCMOpsIsSuccess() {
    UpdateFileRequest updateFileRequest = getUpdateFileRequestDefault();
    when(scmFacilitatorService.updateFile(any()))
        .thenReturn(ScmCommitFileResponseDTO.builder().commitId(commitId).blobId(blobId).build());
    io.harness.gitsync.UpdateFileResponse updateFileResponse = harnessToGitHelperService.updateFile(updateFileRequest);

    assertThat(updateFileResponse.getStatusCode()).isEqualTo(HTTP_200);
    assertGitMetaData(updateFileResponse.getGitMetaData(),
        GitMetaData.newBuilder()
            .setBlobId(blobId)
            .setFilePath(filePath)
            .setCommitId(commitId)
            .setRepoName(repoName)
            .setBranchName(branch)
            .build());
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testUpdateFileWhenSCMExceptionOccurs() {
    UpdateFileRequest updateFileRequest = getUpdateFileRequestDefault();
    when(scmFacilitatorService.updateFile(any())).thenThrow(getInvalidCredsDefaultException());
    io.harness.gitsync.UpdateFileResponse updateFileResponse = harnessToGitHelperService.updateFile(updateFileRequest);

    assertThat(updateFileResponse.getStatusCode()).isEqualTo(401);
    assertGitErrorDetails(updateFileResponse.getError(),
        ErrorDetails.newBuilder()
            .setErrorMessage(errorMessage)
            .setHintMessage(hintMessage)
            .setExplanationMessage(explanationMessage)
            .build());
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testUpdateFileWhenWingsExceptionOccurs() {
    UpdateFileRequest updateFileRequest = getUpdateFileRequestDefault();
    when(scmFacilitatorService.updateFile(any())).thenThrow(getDefaultWingsException());
    io.harness.gitsync.UpdateFileResponse updateFileResponse = harnessToGitHelperService.updateFile(updateFileRequest);

    assertThat(updateFileResponse.getStatusCode()).isEqualTo(400);
    assertThat(updateFileResponse.getError().getErrorMessage())
        .isEqualTo(ExceptionUtils.getMessage(getDefaultWingsException()));
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testCreatePullRequestWhenSCMOpsIsSuccess() {
    CreatePRRequest createPRRequest = getCreatePRRequestDefault();
    when(scmFacilitatorService.createPR(any())).thenReturn(ScmCreatePRResponseDTO.builder().prNumber(prNumber).build());
    CreatePRResponse createPRResponse = harnessToGitHelperService.createPullRequest(createPRRequest);

    assertThat(createPRResponse.getStatusCode()).isEqualTo(HTTP_200);
    assertThat(createPRResponse.getPrNumber()).isEqualTo(prNumber);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testGetRepoUrlWhenSuccess() {
    when(scmFacilitatorService.getRepoUrl(any(), any(), any())).thenReturn(repoUrl);
    io.harness.gitsync.GetRepoUrlResponse response =
        harnessToGitHelperService.getRepoUrl(GetRepoUrlRequest.newBuilder()
                                                 .setConnectorRef(connectorRef)
                                                 .setRepoName(repoName)
                                                 .setScopeIdentifiers(scopeIdentifiers)
                                                 .build());

    assertThat(response.getStatusCode()).isEqualTo(HTTP_200);
    assertThat(response.getRepoUrl()).isEqualTo(repoUrl);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testGetRepoUrlWhenExceptionOccurs() {
    when(scmFacilitatorService.getRepoUrl(any(), any(), any())).thenThrow(getInvalidCredsDefaultException());
    io.harness.gitsync.GetRepoUrlResponse response =
        harnessToGitHelperService.getRepoUrl(getGetRepoUrlRequestDefault());

    assertThat(response.getStatusCode()).isEqualTo(401);
    assertGitErrorDetails(response.getError(),
        ErrorDetails.newBuilder()
            .setErrorMessage(errorMessage)
            .setHintMessage(hintMessage)
            .setExplanationMessage(explanationMessage)
            .build());
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testGetRepoUrlWhenWingsExceptionOccurs() {
    when(scmFacilitatorService.getRepoUrl(any(), any(), any())).thenThrow(getDefaultWingsException());
    io.harness.gitsync.GetRepoUrlResponse response =
        harnessToGitHelperService.getRepoUrl(getGetRepoUrlRequestDefault());

    assertThat(response.getStatusCode()).isEqualTo(400);
    assertThat(response.getError().getErrorMessage()).isEqualTo(ExceptionUtils.getMessage(getDefaultWingsException()));
  }

  private FileInfo getFileInfoDefault(String commitId, ChangeType changeType) {
    return getFileInfoDefault(commitId, changeType, false);
  }

  private WingsException getInvalidCredsDefaultException() {
    return NestedExceptionUtils.hintWithExplanationException(
        hintMessage, explanationMessage, new ScmUnauthorizedException(errorMessage));
  }

  private WingsException getDefaultWingsException() {
    return new InvalidRequestException(errorMessage);
  }

  private void assertGitMetaData(GitMetaData gitMetaDataActual, GitMetaData expectedGitMetadata) {
    assertThat(gitMetaDataActual).isNotNull();
    assertThat(gitMetaDataActual.getBranchName()).isEqualTo(expectedGitMetadata.getBranchName());
    assertThat(gitMetaDataActual.getFilePath()).isEqualTo(expectedGitMetadata.getFilePath());
    assertThat(gitMetaDataActual.getCommitId()).isEqualTo(expectedGitMetadata.getCommitId());
    assertThat(gitMetaDataActual.getBlobId()).isEqualTo(expectedGitMetadata.getBlobId());
    assertThat(gitMetaDataActual.getRepoName()).isEqualTo(expectedGitMetadata.getRepoName());
  }

  private void assertGitErrorDetails(ErrorDetails errorDetailsActual, ErrorDetails errorDetailsExpected) {
    assertThat(errorDetailsActual).isNotNull();
    assertThat(errorDetailsActual.getErrorMessage()).isEqualTo(errorDetailsExpected.getErrorMessage());
    assertThat(errorDetailsActual.getExplanationMessage()).isEqualTo(errorDetailsExpected.getExplanationMessage());
    assertThat(errorDetailsActual.getHintMessage()).isEqualTo(errorDetailsExpected.getHintMessage());
  }

  private FileInfo getFileInfoDefault(String commitId, ChangeType changeType, boolean isNewBranch) {
    return FileInfo.newBuilder()
        .setBaseBranch(StringValue.newBuilder().setValue(baseBranch).build())
        .setBranch(branch)
        .setCommitId(commitId)
        .setChangeType(changeType)
        .setIsNewBranch(isNewBranch)
        .build();
  }

  private CreatePRRequest getCreatePRRequestDefault() {
    return CreatePRRequest.newBuilder()
        .setSourceBranch(baseBranch)
        .setTargetBranch(branch)
        .setRepoName(repoName)
        .build();
  }

  private UpdateFileRequest getUpdateFileRequestDefault() {
    return UpdateFileRequest.newBuilder()
        .setBranchName(branch)
        .setFilePath(filePath)
        .setRepoName(repoName)
        .setScopeIdentifiers(scopeIdentifiers)
        .setConnectorRef(connectorRef)
        .build();
  }

  private CreateFileRequest getCreateFileRequestDefault() {
    return CreateFileRequest.newBuilder()
        .setBranchName(branch)
        .setFilePath(filePath)
        .setRepoName(repoName)
        .setScopeIdentifiers(scopeIdentifiers)
        .setConnectorRef(connectorRef)
        .build();
  }

  private GetFileRequest getGetFileRequestDefault() {
    return GetFileRequest.newBuilder()
        .setBranchName(branch)
        .setFilePath(filePath)
        .setRepoName(repoName)
        .setScopeIdentifiers(scopeIdentifiers)
        .setConnectorRef(connectorRef)
        .build();
  }

  private GetRepoUrlRequest getGetRepoUrlRequestDefault() {
    return GetRepoUrlRequest.newBuilder()
        .setConnectorRef(connectorRef)
        .setRepoName(repoName)
        .setScopeIdentifiers(scopeIdentifiers)
        .build();
  }

  private GitSyncEntityDTO getGitSyncEntityDTODefault() {
    return GitSyncEntityDTO.builder().lastCommitId(commitId).build();
  }

  private EntityDetail getEntityDetailDefault() {
    return EntityDetail.builder().build();
  }
}
