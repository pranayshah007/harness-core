/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.scm;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.helper.GitApiAccessDecryptionHelper;
import io.harness.connector.service.scm.ScmDelegateClient;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.exception.UnknownEnumTypeException;
import io.harness.product.ci.scm.proto.CreateFileResponse;
import io.harness.product.ci.scm.proto.DeleteFileResponse;
import io.harness.product.ci.scm.proto.SCMGrpc;
import io.harness.product.ci.scm.proto.UpdateFileResponse;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.service.ScmServiceClient;

import com.google.inject.Inject;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.apache.commons.lang3.NotImplementedException;

@OwnedBy(HarnessTeam.DX)
public class ScmPushTask extends AbstractDelegateRunnableTask {
  @Inject private SecretDecryptionService secretDecryptionService;
  @Inject ScmDelegateClient scmDelegateClient;
  @Inject ScmServiceClient scmServiceClient;

  public ScmPushTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    ScmPushTaskParams scmPushTaskParams = (ScmPushTaskParams) parameters;
    final DecryptableEntity apiAccessDecryptableEntity =
        GitApiAccessDecryptionHelper.getAPIAccessDecryptableEntity(scmPushTaskParams.getScmConnector());
    secretDecryptionService.decrypt(apiAccessDecryptableEntity, scmPushTaskParams.getEncryptedDataDetails());
    switch (scmPushTaskParams.getChangeType()) {
      case ADD: {
        CreateFileResponse createFileResponse = scmDelegateClient.processScmRequest(c -> {
          final SCMGrpc.SCMBlockingStub scmBlockingStub = SCMGrpc.newBlockingStub(c);
          if (scmPushTaskParams.isNewBranch()) {
            scmServiceClient.createNewBranch(scmPushTaskParams.getScmConnector(),
                scmPushTaskParams.getGitFileDetails().getBranch(), scmPushTaskParams.getBaseBranch(), scmBlockingStub);
          }
          return scmServiceClient.createFile(
              scmPushTaskParams.getScmConnector(), scmPushTaskParams.getGitFileDetails(), scmBlockingStub);
        });
        return ScmPushTaskResponseData.builder()
            .createFileResponse(createFileResponse.toByteArray())
            .changeType(scmPushTaskParams.getChangeType())
            .build();
      }
      case DELETE: {
        DeleteFileResponse deleteFileResponse = scmDelegateClient.processScmRequest(c
            -> scmServiceClient.deleteFile(scmPushTaskParams.getScmConnector(), scmPushTaskParams.getGitFileDetails(),
                SCMGrpc.newBlockingStub(c)));
        return ScmPushTaskResponseData.builder()
            .deleteFileResponse(deleteFileResponse.toByteArray())
            .changeType(scmPushTaskParams.getChangeType())
            .build();
      }
      case MODIFY: {
        UpdateFileResponse updateFileResponse = scmDelegateClient.processScmRequest(c -> {
          final SCMGrpc.SCMBlockingStub scmBlockingStub = SCMGrpc.newBlockingStub(c);
          if (scmPushTaskParams.isNewBranch()) {
            scmServiceClient.createNewBranch(scmPushTaskParams.getScmConnector(),
                scmPushTaskParams.getGitFileDetails().getBranch(), scmPushTaskParams.getBaseBranch(), scmBlockingStub);
          }
          return scmServiceClient.updateFile(
              scmPushTaskParams.getScmConnector(), scmPushTaskParams.getGitFileDetails(), scmBlockingStub);
        });
        return ScmPushTaskResponseData.builder()
            .updateFileResponse(updateFileResponse.toByteArray())
            .changeType(scmPushTaskParams.getChangeType())
            .build();
      }
      case ADD_V2:
        CreateFileResponse createFileResponse = scmDelegateClient.processScmRequest(c
            -> scmServiceClient.createFile(scmPushTaskParams.getScmConnector(), scmPushTaskParams.getGitFileDetails(),
                SCMGrpc.newBlockingStub(c)));
        return ScmPushTaskResponseData.builder()
            .createFileResponse(createFileResponse.toByteArray())
            .changeType(scmPushTaskParams.getChangeType())
            .build();
      case UPDATE_V2:
        UpdateFileResponse updateFileResponse = scmDelegateClient.processScmRequest(c
            -> scmServiceClient.updateFile(scmPushTaskParams.getScmConnector(), scmPushTaskParams.getGitFileDetails(),
                SCMGrpc.newBlockingStub(c)));
        return ScmPushTaskResponseData.builder()
            .updateFileResponse(updateFileResponse.toByteArray())
            .changeType(scmPushTaskParams.getChangeType())
            .build();
      case RENAME:
      case NONE:
        throw new NotImplementedException("Not Implemented");
      default: {
        throw new UnknownEnumTypeException("ChangeType", scmPushTaskParams.getChangeType().toString());
      }
    }
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }
}
