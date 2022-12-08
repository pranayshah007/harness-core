/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.delegate.task.pcf;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.task.tas.TasValidationHandler;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.connector.tasconnector.TasTaskParams;
import io.harness.delegate.beans.connector.tasconnector.TasTaskType;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.pcf.response.TasValidateTaskResponse;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.exception.InvalidRequestException;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.apache.commons.lang3.NotImplementedException;

@OwnedBy(HarnessTeam.CDP)
public class TasConnectorValidationTask extends AbstractDelegateRunnableTask {
  @Inject private TasValidationHandler tasValidationHandler;

  public TasConnectorValidationTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("Not implemented");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    final TasTaskParams tasTaskParams = (TasTaskParams) parameters;
    final TasTaskType tasTaskType = tasTaskParams.getTasTaskType();
    if (Objects.isNull(tasTaskType)) {
      throw new InvalidRequestException("Task type not provided");
    }

    final List<EncryptedDataDetail> encryptionDetails = tasTaskParams.getEncryptionDetails();
    if (tasTaskType == TasTaskType.VALIDATE) {
      return handleValidateTask(tasTaskParams, encryptionDetails);
    } else {
      throw new InvalidRequestException("Task type not identified");
    }
  }

  public DelegateResponseData handleValidateTask(
      TasTaskParams tasTaskParams, List<EncryptedDataDetail> encryptionDetails) {
    ConnectorValidationResult connectorValidationResult =
        tasValidationHandler.validate(tasTaskParams, encryptionDetails);
    connectorValidationResult.setDelegateId(getDelegateId());
    return TasValidateTaskResponse.builder().connectorValidationResult(connectorValidationResult).build();
  }
}
