/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.rancher;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.task.rancher.RancherValidationHandler;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.connector.rancher.RancherTaskParams;
import io.harness.delegate.beans.connector.rancher.RancherTestConnectionTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class RancherTestConnectionDelegateTask extends AbstractDelegateRunnableTask {
  @Inject private RancherValidationHandler rancherValidationHandler;
  public RancherTestConnectionDelegateTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    final RancherTaskParams rancherTaskParams = (RancherTaskParams) parameters;
    final List<EncryptedDataDetail> encryptionDetails = rancherTaskParams.getEncryptionDetails();
    return handleValidateTask(rancherTaskParams, encryptionDetails);
  }

  public DelegateResponseData handleValidateTask(
      RancherTaskParams rancherTaskParams, List<EncryptedDataDetail> encryptionDetails) {
    ConnectorValidationResult connectorValidationResult =
        rancherValidationHandler.validate(rancherTaskParams, encryptionDetails);
    connectorValidationResult.setDelegateId(getDelegateId());
    return RancherTestConnectionTaskResponse.builder().connectorValidationResult(connectorValidationResult).build();
  }
}
