/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.scm;

import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.MEET;
import static io.harness.rule.OwnerRule.MOHIT_GARG;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.EntityType;
import io.harness.GitSdkTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EntityReference;
import io.harness.beans.IdentifierRef;
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.eraro.ErrorCode;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.SCMExceptionHints;
import io.harness.exception.ScmException;
import io.harness.exception.ScmInternalServerErrorException;
import io.harness.exception.WingsException;
import io.harness.exception.ngexception.beans.ScmErrorMetadataDTO;
import io.harness.git.model.ChangeType;
import io.harness.gitsync.CreateFileRequest;
import io.harness.gitsync.CreateFileResponse;
import io.harness.gitsync.CreatePRRequest;
import io.harness.gitsync.CreatePRResponse;
import io.harness.gitsync.ErrorDetails;
import io.harness.gitsync.FileInfo;
import io.harness.gitsync.GetBatchFilesRequest;
import io.harness.gitsync.GetBatchFilesResponse;
import io.harness.gitsync.GetFileRequest;
import io.harness.gitsync.GetFileResponse;
import io.harness.gitsync.GetRepoUrlRequest;
import io.harness.gitsync.GetRepoUrlResponse;
import io.harness.gitsync.GitMetaData;
import io.harness.gitsync.HarnessToGitPushInfoServiceGrpc;
import io.harness.gitsync.PushFileResponse;
import io.harness.gitsync.UpdateFileRequest;
import io.harness.gitsync.UpdateFileResponse;
import io.harness.gitsync.common.helper.GitSyncGrpcClientUtils;
import io.harness.gitsync.exceptions.GitSyncException;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.gitsync.scm.beans.ScmCreateFileGitRequest;
import io.harness.gitsync.scm.beans.ScmCreateFileGitResponse;
import io.harness.gitsync.scm.beans.ScmCreatePRResponse;
import io.harness.gitsync.scm.beans.ScmGetBatchFileRequest;
import io.harness.gitsync.scm.beans.ScmGetBatchFilesResponse;
import io.harness.gitsync.scm.beans.ScmGetFileRequest;
import io.harness.gitsync.scm.beans.ScmGetFileResponse;
import io.harness.gitsync.scm.beans.ScmGetRepoUrlResponse;
import io.harness.gitsync.scm.beans.ScmPushResponse;
import io.harness.gitsync.scm.beans.ScmUpdateFileGitRequest;
import io.harness.gitsync.scm.beans.ScmUpdateFileGitResponse;
import io.harness.gitsync.scm.errorhandling.ScmErrorHandler;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.entitydetail.EntityDetailRestToProtoMapper;
import io.harness.rule.Owner;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.UserPrincipal;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.jooq.tools.reflect.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.MDC;

@OwnedBy(HarnessTeam.PL)
public class SCMGitSyncHelperTest extends GitSdkTestBase {
  private final String accountId = "accountId";
  private final String orgId = "orgId";
  private final String projectId = "projectId";
  private final String commitId = "commitId";
  private final String commitMessage = "message";
  private final String branch = "branch";
  private final String repo = "repo";
  private final String baseBranch = "baseBranch";
  private final String filePath = "filePath";
  private final String filePath2 = "filePath2";
  private final String folderPath = "folderPath";
  private final String lastObjectId = "lastObjectId";
  private final String yamlGitConfigId = "yamlGitConfigId";
  private final String error = "Error";
  private final String name = "name";
  private final String yaml = "yaml";
  private final String fileContent = "fileContent";
  private final String fileContent2 = "fileContent2";
  private final String connectorRef = "connectorRef";
  private final String sourceBranch = "sourceBranch";
  private final String targetBranch = "targetBranch";
  private final String title = "title";
  private final int prNumber = 0;
  private final String repoUrl = "repoUrl";
  private final Map<String, String> contextMap = new HashMap<>();

  @InjectMocks SCMGitSyncHelper scmGitSyncHelper;
  @Mock EntityDetailRestToProtoMapper entityDetailRestToProtoMapper;
  @Mock GitSyncSdkService gitSyncSdkService;
  @Mock EntityDetailProtoDTO entityDetailProtoDTO;
  @Mock HarnessToGitPushInfoServiceGrpc.HarnessToGitPushInfoServiceBlockingStub harnessToGitPushInfoServiceBlockingStub;
  @Inject ScmErrorHandler scmErrorHandler;

  EntityReference entityReference;
  GitEntityInfo gitEntityInfo1;
  GitEntityInfo gitEntityInfo2;
  PushFileResponse pushFileResponse1;
  PushFileResponse pushFileResponse2;
  PushFileResponse pushFileResponse3;
  PushFileResponse pushFileResponse4;
  EntityDetail entityDetail;

  EntityType entityType;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    Reflect.on(scmGitSyncHelper).set("scmErrorHandler", scmErrorHandler);

    gitEntityInfo1 = buildGitEntityInfo(branch, baseBranch, commitId, commitMessage, filePath, folderPath, false, false,
        true, true, lastObjectId, yamlGitConfigId);
    gitEntityInfo2 = buildGitEntityInfo(branch, baseBranch, commitId, commitMessage, filePath, folderPath, false, false,
        true, false, lastObjectId, yamlGitConfigId);
    entityReference =
        IdentifierRef.builder().accountIdentifier(accountId).orgIdentifier(orgId).projectIdentifier(projectId).build();
    entityDetail = EntityDetail.builder().entityRef(entityReference).name(name).type(EntityType.CONNECTORS).build();

    pushFileResponse1 = buildPushFileResponse(1, 404, error);
    pushFileResponse2 = buildPushFileResponse(1, 304, error);
    pushFileResponse3 = buildPushFileResponse(1, 400, error);
    pushFileResponse4 = buildPushFileResponse(1, 409, error);

    SourcePrincipalContextBuilder.setSourcePrincipal(
        new UserPrincipal("userName", "DUMMY_USER_EMAIL", "userName", accountId));
    entityType = EntityType.PIPELINES;
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testPushToGit() throws IOException {
    when(gitSyncSdkService.isDefaultBranch(entityDetail.getEntityRef().getAccountIdentifier(),
             entityDetail.getEntityRef().getOrgIdentifier(), entityDetail.getEntityRef().getProjectIdentifier()))
        .thenReturn(true);
    ScmPushResponse scmPushResponse1 = scmGitSyncHelper.pushToGit(gitEntityInfo1, yaml, ChangeType.ADD, entityDetail);
    assertThat(scmPushResponse1.isPushToDefaultBranch()).isEqualTo(true);

    Principal principal = new UserPrincipal(name, "DUMMY_USER_EMAIL", name, accountId);
    SourcePrincipalContextBuilder.setSourcePrincipal(principal);
    when(entityDetailRestToProtoMapper.createEntityDetailDTO(entityDetail)).thenReturn(entityDetailProtoDTO);
    MDC.setContextMap(contextMap);

    when(GitSyncGrpcClientUtils.retryAndProcessException(
             harnessToGitPushInfoServiceBlockingStub::pushFile, any(FileInfo.class)))
        .thenReturn(pushFileResponse1);
    assertThatThrownBy(() -> scmGitSyncHelper.pushToGit(gitEntityInfo2, yaml, ChangeType.ADD, entityDetail))
        .hasMessage("Invalid Repository or wrong credentials.");
    when(GitSyncGrpcClientUtils.retryAndProcessException(
             harnessToGitPushInfoServiceBlockingStub::pushFile, any(FileInfo.class)))
        .thenReturn(pushFileResponse2);
    assertThatThrownBy(() -> scmGitSyncHelper.pushToGit(gitEntityInfo2, yaml, ChangeType.ADD, entityDetail))
        .hasMessage(error);
    when(GitSyncGrpcClientUtils.retryAndProcessException(
             harnessToGitPushInfoServiceBlockingStub::pushFile, any(FileInfo.class)))
        .thenReturn(pushFileResponse3);
    assertThatThrownBy(() -> scmGitSyncHelper.pushToGit(gitEntityInfo2, yaml, ChangeType.ADD, entityDetail))
        .hasMessage("Unexpected error occurred while doing scm operation");
    when(GitSyncGrpcClientUtils.retryAndProcessException(
             harnessToGitPushInfoServiceBlockingStub::pushFile, any(FileInfo.class)))
        .thenReturn(pushFileResponse4);
    assertThatThrownBy(() -> scmGitSyncHelper.pushToGit(gitEntityInfo2, yaml, ChangeType.ADD, entityDetail))
        .hasMessage("A file with name filePath already exists in the remote Git repository");

    MDC.clear();
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testCheckForError() {
    String errorMessage = "errorMessage";
    scmGitSyncHelper.checkForError(buildPushFileResponse(1, 200, errorMessage));

    assertThatThrownBy(() -> scmGitSyncHelper.checkForError(buildPushFileResponse(0, 0, errorMessage)))
        .hasMessage(errorMessage)
        .isInstanceOf(GitSyncException.class);

    assertThatThrownBy(() -> scmGitSyncHelper.checkForError(buildPushFileResponse(1, 304, errorMessage)))
        .isInstanceOf(ExplanationException.class)
        .hasMessage(errorMessage);

    assertThatThrownBy(() -> scmGitSyncHelper.checkForError(buildPushFileResponse(1, 304, "")))
        .isInstanceOf(ScmException.class)
        .hasMessage("");

    assertThatThrownBy(() -> scmGitSyncHelper.checkForError(buildPushFileResponse(1, 404, errorMessage)))
        .isInstanceOf(HintException.class)
        .hasMessage(SCMExceptionHints.INVALID_CREDENTIALS);

    try {
      scmGitSyncHelper.checkForError(buildPushFileResponse(1, 304, errorMessage));
    } catch (WingsException ex) {
      assertThat(ex.getMetadata()).isNotNull();
      assertThat(ex.getMetadata() instanceof ScmErrorMetadataDTO).isTrue();
      assertThat(((ScmErrorMetadataDTO) ex.getMetadata()).getConflictCommitId().equals(commitId)).isTrue();
    }
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testThrowDifferentExceptionInCaseOfChangeTypeAddMethod() {
    WingsException exception = new WingsException("dummy");
    assertThatThrownBy(()
                           -> scmGitSyncHelper.throwDifferentExceptionInCaseOfChangeTypeAdd(
                               GitEntityInfo.builder().build(), ChangeType.DELETE, exception))
        .isEqualTo(exception);

    assertThatThrownBy(()
                           -> scmGitSyncHelper.throwDifferentExceptionInCaseOfChangeTypeAdd(
                               GitEntityInfo.builder().build(), ChangeType.ADD, exception))
        .isEqualTo(exception);

    WingsException exception2 = new ScmException(ErrorCode.SCM_CONFLICT_ERROR);
    assertThatThrownBy(()
                           -> scmGitSyncHelper.throwDifferentExceptionInCaseOfChangeTypeAdd(
                               GitEntityInfo.builder().build(), ChangeType.ADD, exception2))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testGetFile() {
    GetFileResponse successfulGetFileResponse = GetFileResponse.newBuilder()
                                                    .setStatusCode(200)
                                                    .setFileContent(fileContent)
                                                    .setGitMetaData(getDefaultGitMetaData())
                                                    .build();
    when(GitSyncGrpcClientUtils.retryAndProcessException(
             harnessToGitPushInfoServiceBlockingStub::getFile, any(GetFileRequest.class)))
        .thenReturn(successfulGetFileResponse);

    ScmGetFileResponse scmGetFileResponse = scmGitSyncHelper.getFileByBranch(
        getDefaultScope(), repo, branch, commitId, filePath, connectorRef, false, entityType, contextMap, false);
    assertThat(scmGetFileResponse).isNotNull();
    assertThat(scmGetFileResponse.getFileContent()).isEqualTo(fileContent);
    assertThat(scmGetFileResponse.getGitMetaData().getRepoName()).isEqualTo(repo);
    assertThat(scmGetFileResponse.getGitMetaData().getBranchName()).isEqualTo(branch);
    assertThat(scmGetFileResponse.getGitMetaData().getFilePath()).isEqualTo(filePath);
    assertThat(scmGetFileResponse.getGitMetaData().getCommitId()).isEqualTo(commitId);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testGetFileInCaseOfError() {
    GetFileResponse failureGetFileResponse =
        GetFileResponse.newBuilder().setStatusCode(500).setError(getDefaultErrorDetails()).build();
    when(GitSyncGrpcClientUtils.retryAndProcessException(
             harnessToGitPushInfoServiceBlockingStub::getFile, any(GetFileRequest.class)))
        .thenReturn(failureGetFileResponse);

    assertThatThrownBy(()
                           -> scmGitSyncHelper.getFileByBranch(getDefaultScope(), repo, branch, commitId, filePath,
                               connectorRef, false, entityType, contextMap, false))
        .isInstanceOf(ScmInternalServerErrorException.class)
        .hasMessage(error);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testCreateFile() {
    CreateFileResponse createFileResponse =
        CreateFileResponse.newBuilder().setStatusCode(200).setGitMetaData(getDefaultGitMetaData()).build();
    when(GitSyncGrpcClientUtils.retryAndProcessException(
             harnessToGitPushInfoServiceBlockingStub::createFile, any(CreateFileRequest.class)))
        .thenReturn(createFileResponse);

    ScmCreateFileGitResponse response =
        scmGitSyncHelper.createFile(getDefaultScope(), createFileRequestDefault(), contextMap);
    assertThat(response).isNotNull();
    assertThat(response.getGitMetaData().getRepoName()).isEqualTo(repo);
    assertThat(response.getGitMetaData().getBranchName()).isEqualTo(branch);
    assertThat(response.getGitMetaData().getFilePath()).isEqualTo(filePath);
    assertThat(response.getGitMetaData().getCommitId()).isEqualTo(commitId);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testCreateFileInCaseOfError() {
    CreateFileResponse createFileResponse =
        CreateFileResponse.newBuilder().setStatusCode(500).setError(getDefaultErrorDetails()).build();
    when(GitSyncGrpcClientUtils.retryAndProcessException(
             harnessToGitPushInfoServiceBlockingStub::createFile, any(CreateFileRequest.class)))
        .thenReturn(createFileResponse);

    assertThatThrownBy(() -> scmGitSyncHelper.createFile(getDefaultScope(), createFileRequestDefault(), contextMap))
        .isInstanceOf(ScmInternalServerErrorException.class)
        .hasMessage(error);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testUpdateFile() {
    UpdateFileResponse updateFileResponse =
        UpdateFileResponse.newBuilder().setStatusCode(200).setGitMetaData(getDefaultGitMetaData()).build();
    when(GitSyncGrpcClientUtils.retryAndProcessException(
             harnessToGitPushInfoServiceBlockingStub::updateFile, any(UpdateFileRequest.class)))
        .thenReturn(updateFileResponse);

    ScmUpdateFileGitResponse response =
        scmGitSyncHelper.updateFile(getDefaultScope(), updateFileGitRequestDefault(), contextMap);
    assertThat(response).isNotNull();
    assertThat(response.getGitMetaData().getRepoName()).isEqualTo(repo);
    assertThat(response.getGitMetaData().getBranchName()).isEqualTo(branch);
    assertThat(response.getGitMetaData().getFilePath()).isEqualTo(filePath);
    assertThat(response.getGitMetaData().getCommitId()).isEqualTo(commitId);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testUpdateFileInCaseOfError() {
    UpdateFileResponse updateFileResponse =
        UpdateFileResponse.newBuilder().setStatusCode(500).setError(getDefaultErrorDetails()).build();
    when(GitSyncGrpcClientUtils.retryAndProcessException(
             harnessToGitPushInfoServiceBlockingStub::updateFile, any(UpdateFileRequest.class)))
        .thenReturn(updateFileResponse);

    assertThatThrownBy(() -> scmGitSyncHelper.updateFile(getDefaultScope(), updateFileGitRequestDefault(), contextMap))
        .isInstanceOf(ScmInternalServerErrorException.class)
        .hasMessage(error);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testCreatePullRequest() {
    CreatePRResponse createPRResponse = CreatePRResponse.newBuilder().setStatusCode(200).setPrNumber(prNumber).build();
    when(GitSyncGrpcClientUtils.retryAndProcessException(
             harnessToGitPushInfoServiceBlockingStub::createPullRequest, any(CreatePRRequest.class)))
        .thenReturn(createPRResponse);

    ScmCreatePRResponse response = scmGitSyncHelper.createPullRequest(
        getDefaultScope(), repo, connectorRef, sourceBranch, targetBranch, title, contextMap);
    assertThat(response).isNotNull();
    assertThat(response.getPrNumber()).isEqualTo(prNumber);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testCreatePullRequestInCaseOfError() {
    CreatePRResponse createPRResponse =
        CreatePRResponse.newBuilder().setStatusCode(500).setError(getDefaultErrorDetails()).build();
    when(GitSyncGrpcClientUtils.retryAndProcessException(
             harnessToGitPushInfoServiceBlockingStub::createPullRequest, any(CreatePRRequest.class)))
        .thenReturn(createPRResponse);

    assertThatThrownBy(()
                           -> scmGitSyncHelper.createPullRequest(
                               getDefaultScope(), repo, connectorRef, sourceBranch, targetBranch, title, contextMap))
        .isInstanceOf(ScmInternalServerErrorException.class)
        .hasMessage(error);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testGetRepoUrlRequest() {
    GetRepoUrlResponse getRepoUrlResponse =
        GetRepoUrlResponse.newBuilder().setStatusCode(200).setRepoUrl(repoUrl).build();
    when(GitSyncGrpcClientUtils.retryAndProcessException(
             harnessToGitPushInfoServiceBlockingStub::getRepoUrl, any(GetRepoUrlRequest.class)))
        .thenReturn(getRepoUrlResponse);

    ScmGetRepoUrlResponse response = scmGitSyncHelper.getRepoUrl(getDefaultScope(), repo, connectorRef, contextMap);
    assertThat(response).isNotNull();
    assertThat(response.getRepoUrl()).isEqualTo(repoUrl);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testGetRepoUrlRequestInCaseOfError() {
    GetRepoUrlResponse getRepoUrlResponse =
        GetRepoUrlResponse.newBuilder().setStatusCode(500).setError(getDefaultErrorDetails()).build();
    when(GitSyncGrpcClientUtils.retryAndProcessException(
             harnessToGitPushInfoServiceBlockingStub::getRepoUrl, any(GetRepoUrlRequest.class)))
        .thenReturn(getRepoUrlResponse);

    assertThatThrownBy(() -> scmGitSyncHelper.getRepoUrl(getDefaultScope(), repo, connectorRef, contextMap))
        .isInstanceOf(ScmInternalServerErrorException.class)
        .hasMessage(error);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetBatchFilesByBranch() {
    String uniqueKey1 = "uniqueKey1";
    String uniqueKey2 = "uniqueKey2";

    Map<String, ScmGetFileRequest> scmGetBatchFilesRequestMap = new HashMap<>();
    ScmGetFileRequest scmGetFileRequest1 = ScmGetFileRequest.builder()
                                               .scope(getDefaultScope())
                                               .filePath(filePath)
                                               .repoName(repo)
                                               .branchName(branch)
                                               .connectorRef(connectorRef)
                                               .entityType(entityType)
                                               .contextMap(contextMap)
                                               .loadFromCache(false)
                                               .build();
    ScmGetFileRequest scmGetFileRequest2 = ScmGetFileRequest.builder()
                                               .scope(getDefaultScope())
                                               .filePath(filePath2)
                                               .repoName(repo)
                                               .branchName(branch)
                                               .connectorRef(connectorRef)
                                               .entityType(entityType)
                                               .contextMap(contextMap)
                                               .loadFromCache(false)
                                               .build();

    scmGetBatchFilesRequestMap.put(uniqueKey1, scmGetFileRequest1);
    scmGetBatchFilesRequestMap.put(uniqueKey2, scmGetFileRequest2);

    ScmGetBatchFileRequest scmGetBatchFileRequest =
        ScmGetBatchFileRequest.builder().scmGetBatchFilesRequestMap(scmGetBatchFilesRequestMap).build();

    GetFileResponse successfulGetFileResponse1 = GetFileResponse.newBuilder()
                                                     .setStatusCode(200)
                                                     .setFileContent(fileContent)
                                                     .setGitMetaData(getDefaultGitMetaData())
                                                     .build();

    GetFileResponse successfulGetFileResponse2 = GetFileResponse.newBuilder()
                                                     .setStatusCode(200)
                                                     .setFileContent(fileContent2)
                                                     .setGitMetaData(getDefaultGitMetaData())
                                                     .build();

    Map<String, GetFileResponse> getFileResponseMap = new HashMap<>();
    getFileResponseMap.put(uniqueKey1, successfulGetFileResponse1);
    getFileResponseMap.put(uniqueKey2, successfulGetFileResponse2);

    GetBatchFilesResponse getBatchFilesResponse =
        GetBatchFilesResponse.newBuilder().putAllGetFileResponseMap(getFileResponseMap).build();
    when(GitSyncGrpcClientUtils.retryAndProcessException(
             harnessToGitPushInfoServiceBlockingStub::getBatchFiles, any(GetBatchFilesRequest.class)))
        .thenReturn(getBatchFilesResponse);

    ScmGetBatchFilesResponse scmGetBatchFilesResponse =
        scmGitSyncHelper.getBatchFilesByBranch(accountId, scmGetBatchFileRequest);
    Map<String, ScmGetFileResponse> batchFilesResponse = scmGetBatchFilesResponse.getBatchFilesResponse();
    assertTrue(batchFilesResponse.containsKey(uniqueKey1));
    ScmGetFileResponse scmGetFileResponse1 = batchFilesResponse.get(uniqueKey1);
    assertEquals(scmGetFileResponse1.getFileContent(), fileContent);

    assertTrue(batchFilesResponse.containsKey(uniqueKey2));
    ScmGetFileResponse scmGetFileResponse2 = batchFilesResponse.get(uniqueKey2);
    assertEquals(scmGetFileResponse2.getFileContent(), fileContent2);
  }

  private GitEntityInfo buildGitEntityInfo(String branch, String baseBranch, String commitId, String commitMsg,
      String filePath, String folderPath, Boolean isFullSyncFlow, boolean findDefaultFromOtherRepos,
      Boolean isNewBranch, Boolean isSyncFromGit, String lastObjectId, String yamlGitConfigId) {
    return GitEntityInfo.builder()
        .branch(branch)
        .baseBranch(baseBranch)
        .commitId(commitId)
        .commitMsg(commitMsg)
        .filePath(filePath)
        .findDefaultFromOtherRepos(findDefaultFromOtherRepos)
        .folderPath(folderPath)
        .isFullSyncFlow(isFullSyncFlow)
        .isNewBranch(isNewBranch)
        .isSyncFromGit(isSyncFromGit)
        .lastObjectId(lastObjectId)
        .yamlGitConfigId(yamlGitConfigId)
        .build();
  }

  private PushFileResponse buildPushFileResponse(int status, int scmResponseCode, String errorMsg) {
    return PushFileResponse.newBuilder()
        .setStatus(status)
        .setScmResponseCode(scmResponseCode)
        .setError(errorMsg)
        .setCommitId(commitId)
        .build();
  }

  private GitMetaData getDefaultGitMetaData() {
    return GitMetaData.newBuilder()
        .setCommitId(commitId)
        .setBlobId(lastObjectId)
        .setBranchName(branch)
        .setRepoName(repo)
        .setFilePath(filePath)
        .build();
  }

  private ScmCreateFileGitRequest createFileRequestDefault() {
    return ScmCreateFileGitRequest.builder()
        .repoName(repo)
        .baseBranch(baseBranch)
        .branchName(branch)
        .fileContent(fileContent)
        .commitMessage(commitMessage)
        .connectorRef(connectorRef)
        .filePath(filePath)
        .isCommitToNewBranch(false)
        .build();
  }

  private ScmUpdateFileGitRequest updateFileGitRequestDefault() {
    return ScmUpdateFileGitRequest.builder()
        .repoName(repo)
        .baseBranch(baseBranch)
        .branchName(branch)
        .fileContent(fileContent)
        .commitMessage(commitMessage)
        .connectorRef(connectorRef)
        .filePath(filePath)
        .isCommitToNewBranch(false)
        .oldFileSha(commitId)
        .build();
  }

  private Scope getDefaultScope() {
    return Scope.builder().accountIdentifier(accountId).orgIdentifier(orgId).projectIdentifier(projectId).build();
  }

  private ErrorDetails getDefaultErrorDetails() {
    return ErrorDetails.newBuilder().setErrorMessage(error).build();
  }
}
