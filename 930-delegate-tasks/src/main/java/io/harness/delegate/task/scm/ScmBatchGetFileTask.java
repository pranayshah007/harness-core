/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.scm;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.GetBatchFileRequestIdentifier;
import io.harness.beans.request.GitFileBatchRequest;
import io.harness.beans.request.GitFileRequestV2;
import io.harness.beans.response.GitFileBatchResponse;
import io.harness.connector.helper.GitApiAccessDecryptionHelper;
import io.harness.connector.service.scm.ScmDelegateClient;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.product.ci.scm.proto.SCMGrpc;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.service.ScmServiceClient;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.apache.commons.lang3.NotImplementedException;
import org.jose4j.lang.JoseException;

@OwnedBy(HarnessTeam.PIPELINE)
public class ScmBatchGetFileTask extends AbstractDelegateRunnableTask {
  @Inject ScmDelegateClient scmDelegateClient;
  @Inject ScmServiceClient scmServiceClient;
  @Inject SecretDecryptionService secretDecryptionService;

  public ScmBatchGetFileTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) throws IOException, JoseException {
    ScmBatchGetFileTaskParams scmBatchGetFileTaskParams = (ScmBatchGetFileTaskParams) parameters;

    Map<GetBatchFileRequestIdentifier, GitFileRequestV2> getBatchFileRequestIdentifierGitFileRequestV2Map =
        new HashMap<>();
    scmBatchGetFileTaskParams.getGetFileTaskParamsPerConnectorList().forEach(getFileTaskParamsPerConnector -> {
      ScmConnector scmConnector = getFileTaskParamsPerConnector.getConnectorDecryptionParams().getScmConnector();
      secretDecryptionService.decrypt(GitApiAccessDecryptionHelper.getAPIAccessDecryptableEntity(scmConnector),
          getFileTaskParamsPerConnector.getConnectorDecryptionParams().getEncryptedDataDetails());

      getFileTaskParamsPerConnector.getGitFileLocationDetailsMap().forEach((identifier, gitFileLocationDetails) -> {
        getBatchFileRequestIdentifierGitFileRequestV2Map.put(identifier,
            GitFileRequestV2.builder()
                .branch(gitFileLocationDetails.getBranch())
                .repo(gitFileLocationDetails.getRepo())
                .commitId(gitFileLocationDetails.getCommitId())
                .filepath(gitFileLocationDetails.getFilepath())
                .getOnlyFileContent(gitFileLocationDetails.isGetOnlyFileContent())
                .scmConnector(scmConnector)
                .build());
      });
    });
    GitFileBatchResponse gitFileBatchResponse = scmDelegateClient.processScmRequest(c
        -> scmServiceClient.getBatchFile(
            GitFileBatchRequest.builder()
                .getBatchFileRequestIdentifierGitFileRequestV2Map(getBatchFileRequestIdentifierGitFileRequestV2Map)
                .build(),
            SCMGrpc.newBlockingStub(c)));
    return ScmBatchGetFileTaskResponseData.builder()
        .getBatchFileRequestIdentifierGitFileResponseMap(
            gitFileBatchResponse.getGetBatchFileRequestIdentifierGitFileResponseMap())
        .build();
  }
}
