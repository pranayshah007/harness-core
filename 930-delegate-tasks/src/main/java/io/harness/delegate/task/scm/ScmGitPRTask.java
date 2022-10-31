/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.scm;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.gitsync.GitPRCreateRequest;
import io.harness.connector.helper.GitApiAccessDecryptionHelper;
import io.harness.connector.service.scm.ScmDelegateClient;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.exception.ExplanationException;
import io.harness.exception.WingsException;
import io.harness.impl.ScmResponseStatusUtils;
import io.harness.product.ci.scm.proto.CreatePRResponse;
import io.harness.product.ci.scm.proto.SCMGrpc;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.service.ScmServiceClient;

import com.google.inject.Inject;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.apache.commons.lang3.NotImplementedException;

@OwnedBy(HarnessTeam.DX)
public class ScmGitPRTask extends AbstractDelegateRunnableTask {
  @Inject private SecretDecryptionService decryptionService;
  @Inject ScmDelegateClient scmDelegateClient;
  @Inject ScmServiceClient scmServiceClient;

  public ScmGitPRTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    ScmPRTaskParams scmPRTaskParams = (ScmPRTaskParams) parameters;
    decryptionService.decrypt(
        GitApiAccessDecryptionHelper.getAPIAccessDecryptableEntity(scmPRTaskParams.getScmConnector()),
        scmPRTaskParams.getEncryptedDataDetails());
    GitPRCreateRequest gitPRCreateRequest = scmPRTaskParams.getGitPRCreateRequest();
    switch (scmPRTaskParams.getGitPRTaskType()) {
      case CREATE_PR:
        CreatePRResponse createPRResponse = scmDelegateClient.processScmRequest(c
            -> scmServiceClient.createPullRequest(
                scmPRTaskParams.getScmConnector(), gitPRCreateRequest, SCMGrpc.newBlockingStub(c)));
        try {
          ScmResponseStatusUtils.checkScmResponseStatusAndThrowException(
              createPRResponse.getStatus(), createPRResponse.getError());
        } catch (WingsException e) {
          throw new ExplanationException(
              String.format("Could not create the pull request from %s to %s", gitPRCreateRequest.getSourceBranch(),
                  gitPRCreateRequest.getTargetBranch()),
              e);
        }
        return ScmPRTaskResponseData.builder()
            .prTaskType(scmPRTaskParams.getGitPRTaskType())
            .createPRResponse(createPRResponse)
            .build();
      case CREATE_PR_V2:
        createPRResponse = scmDelegateClient.processScmRequest(c
            -> scmServiceClient.createPullRequestV2(scmPRTaskParams.getScmConnector(),
                scmPRTaskParams.getSourceBranchName(), scmPRTaskParams.getTargetBranchName(),
                scmPRTaskParams.getPrTitle(), SCMGrpc.newBlockingStub(c)));
        return ScmPRTaskResponseData.builder()
            .prTaskType(scmPRTaskParams.getGitPRTaskType())
            .createPRResponse(createPRResponse)
            .build();
      default: {
        throw new NotImplementedException("Not Implemented");
      }
    }
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }
}
