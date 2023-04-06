/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.scm;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FileContentBatchResponse;
import io.harness.beans.gitsync.GitFilePathDetails;
import io.harness.beans.request.GitFileRequest;
import io.harness.beans.request.ListFilesInCommitRequest;
import io.harness.beans.response.GitFileResponse;
import io.harness.beans.response.ListFilesInCommitResponse;
import io.harness.connector.helper.GitApiAccessDecryptionHelper;
import io.harness.connector.service.scm.ScmDelegateClient;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.exception.UnknownEnumTypeException;
import io.harness.product.ci.scm.proto.FileContent;
import io.harness.product.ci.scm.proto.SCMGrpc;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.service.ScmServiceClient;

import com.google.inject.Inject;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.apache.commons.lang3.NotImplementedException;

@OwnedBy(HarnessTeam.DX)
public class ScmGitFileTask extends AbstractDelegateRunnableTask {
  @Inject ScmDelegateClient scmDelegateClient;
  @Inject ScmServiceClient scmServiceClient;
  @Inject SecretDecryptionService secretDecryptionService;

  public ScmGitFileTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    ScmGitFileTaskParams scmGitFileTaskParams = (ScmGitFileTaskParams) parameters;
    secretDecryptionService.decrypt(
        GitApiAccessDecryptionHelper.getAPIAccessDecryptableEntity(scmGitFileTaskParams.getScmConnector()),
        scmGitFileTaskParams.getEncryptedDataDetails());
    switch (scmGitFileTaskParams.getGitFileTaskType()) {
      case GET_FILE_CONTENT_BATCH:
        FileContentBatchResponse fileBatchContentResponse = scmDelegateClient.processScmRequest(c
            -> scmServiceClient.listFiles(scmGitFileTaskParams.getScmConnector(), scmGitFileTaskParams.getFoldersList(),
                scmGitFileTaskParams.getBranch(), SCMGrpc.newBlockingStub(c)));
        return GitFileTaskResponseData.builder()
            .gitFileTaskType(scmGitFileTaskParams.getGitFileTaskType())
            .fileBatchContentResponse(fileBatchContentResponse.getFileBatchContentResponse().toByteArray())
            .commitId(fileBatchContentResponse.getCommitId())
            .build();
      case GET_FILE_CONTENT:
        final FileContent fileContent = scmDelegateClient.processScmRequest(c
            -> scmServiceClient.getFileContent(scmGitFileTaskParams.getScmConnector(),
                scmGitFileTaskParams.getGitFilePathDetails(), SCMGrpc.newBlockingStub(c)));
        return GitFileTaskResponseData.builder()
            .gitFileTaskType(scmGitFileTaskParams.getGitFileTaskType())
            .fileContent(fileContent.toByteArray())
            .build();
      case GET_FILE_CONTENT_BATCH_BY_FILE_PATHS:
        fileBatchContentResponse = scmDelegateClient.processScmRequest(c
            -> scmServiceClient.listFilesByFilePaths(scmGitFileTaskParams.getScmConnector(),
                scmGitFileTaskParams.getFilePathsList(), scmGitFileTaskParams.getBranch(), SCMGrpc.newBlockingStub(c)));
        return GitFileTaskResponseData.builder()
            .gitFileTaskType(scmGitFileTaskParams.getGitFileTaskType())
            .fileBatchContentResponse(fileBatchContentResponse.getFileBatchContentResponse().toByteArray())
            .commitId(fileBatchContentResponse.getCommitId())
            .build();
      case GET_FILE_CONTENT_BATCH_BY_REF:
        fileBatchContentResponse = scmDelegateClient.processScmRequest(c
            -> scmServiceClient.listFilesByCommitId(scmGitFileTaskParams.getScmConnector(),
                scmGitFileTaskParams.getFilePathsList(), scmGitFileTaskParams.getRef(), SCMGrpc.newBlockingStub(c)));
        return GitFileTaskResponseData.builder()
            .gitFileTaskType(scmGitFileTaskParams.getGitFileTaskType())
            .fileBatchContentResponse(fileBatchContentResponse.getFileBatchContentResponse().toByteArray())
            .commitId(fileBatchContentResponse.getCommitId())
            .build();
      case GET_FILE:
        GitFilePathDetails gitFilePathDetails = scmGitFileTaskParams.getGitFilePathDetails();
        GitFileResponse gitFileResponse = scmDelegateClient.processScmRequest(c
            -> scmServiceClient.getFile(scmGitFileTaskParams.getScmConnector(),
                GitFileRequest.builder()
                    .filepath(gitFilePathDetails.getFilePath())
                    .branch(gitFilePathDetails.getBranch())
                    .commitId(gitFilePathDetails.getRef())
                    .getOnlyFileContent(gitFilePathDetails.isGetOnlyFileContent())
                    .build(),
                SCMGrpc.newBlockingStub(c)));
        return GitFileTaskResponseData.builder()
            .gitFileTaskType(scmGitFileTaskParams.getGitFileTaskType())
            .gitFileResponse(gitFileResponse)
            .build();
      case GET_FILE_GIT_DETAILS_LIST_IN_COMMIT:
        ListFilesInCommitResponse listFilesInCommitResponse = scmDelegateClient.processScmRequest(c
            -> scmServiceClient.listFilesInCommit(scmGitFileTaskParams.getScmConnector(),
                ListFilesInCommitRequest.builder()
                    .ref(scmGitFileTaskParams.getRef())
                    .fileDirectoryPath(scmGitFileTaskParams.getFilePathsList().get(0))
                    .build(),
                SCMGrpc.newBlockingStub(c)));
        return GitFileTaskResponseData.builder()
            .gitFileTaskType(scmGitFileTaskParams.getGitFileTaskType())
            .listFilesInCommitResponse(listFilesInCommitResponse)
            .build();
      default:
        throw new UnknownEnumTypeException("GitFileTaskType", scmGitFileTaskParams.getGitFileTaskType().toString());
    }
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }
}
