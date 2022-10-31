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
import io.harness.impl.ScmResponseStatusUtils;
import io.harness.product.ci.scm.proto.CreateWebhookResponse;
import io.harness.product.ci.scm.proto.SCMGrpc;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.service.ScmServiceClient;

import com.google.inject.Inject;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.apache.commons.lang3.NotImplementedException;

@OwnedBy(HarnessTeam.DX)
public class ScmGitWebhookTask extends AbstractDelegateRunnableTask {
  @Inject SecretDecryptionService secretDecryptionService;
  @Inject ScmDelegateClient scmDelegateClient;
  @Inject ScmServiceClient scmServiceClient;

  public ScmGitWebhookTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    ScmGitWebhookTaskParams scmGitWebhookTaskParams = (ScmGitWebhookTaskParams) parameters;
    final DecryptableEntity apiAccessDecryptableEntity =
        GitApiAccessDecryptionHelper.getAPIAccessDecryptableEntity(scmGitWebhookTaskParams.getScmConnector());
    secretDecryptionService.decrypt(apiAccessDecryptableEntity, scmGitWebhookTaskParams.getEncryptedDataDetails());
    switch (scmGitWebhookTaskParams.getGitWebhookTaskType()) {
      case UPSERT:
        final CreateWebhookResponse upsertWebhookResponse = scmDelegateClient.processScmRequest(c
            -> scmServiceClient.upsertWebhook(scmGitWebhookTaskParams.getScmConnector(),
                scmGitWebhookTaskParams.getGitWebhookDetails(), SCMGrpc.newBlockingStub(c)));
        return ScmGitWebhookTaskResponseData.builder()
            .gitWebhookTaskType(scmGitWebhookTaskParams.getGitWebhookTaskType())
            .createWebhookResponse(upsertWebhookResponse.toByteArray())
            .build();
      case CREATE:
        CreateWebhookResponse createWebhookResponse = scmDelegateClient.processScmRequest(c
            -> scmServiceClient.createWebhook(scmGitWebhookTaskParams.getScmConnector(),
                scmGitWebhookTaskParams.getGitWebhookDetails(), SCMGrpc.newBlockingStub(c)));
        ScmResponseStatusUtils.checkScmResponseStatusAndThrowException(createWebhookResponse.getStatus(), null);
        return ScmGitWebhookTaskResponseData.builder()
            .gitWebhookTaskType(scmGitWebhookTaskParams.getGitWebhookTaskType())
            .createWebhookResponse(createWebhookResponse.toByteArray())
            .build();
      default:
        throw new UnknownEnumTypeException(
            "GitWebhookTaskType", scmGitWebhookTaskParams.getGitWebhookTaskType().toString());
    }
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }
}
