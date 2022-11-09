/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.spot;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.task.spot.SpotValidationHandler;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.connector.spotconnector.SpotTaskParams;
import io.harness.delegate.beans.connector.spotconnector.SpotTaskType;
import io.harness.delegate.beans.connector.spotconnector.SpotValidateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.exception.InvalidRequestException;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class SpotDelegateTask extends AbstractDelegateRunnableTask {
  @Inject private SpotValidationHandler spotValidationHandler;

  public SpotDelegateTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("Not implemented");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    final SpotTaskParams spotTaskParams = (SpotTaskParams) parameters;
    final SpotTaskType spotTaskType = spotTaskParams.getSpotTaskType();
    if (Objects.isNull(spotTaskType)) {
      throw new InvalidRequestException("Task type not provided");
    }

    final List<EncryptedDataDetail> encryptionDetails = spotTaskParams.getEncryptionDetails();
    if (spotTaskType == SpotTaskType.VALIDATE) {
      return handleValidateTask(spotTaskParams, encryptionDetails);
    } else {
      throw new InvalidRequestException("Task type not identified");
    }
  }

  public DelegateResponseData handleValidateTask(
      SpotTaskParams spotTaskParams, List<EncryptedDataDetail> encryptionDetails) {
    ConnectorValidationResult connectorValidationResult =
        spotValidationHandler.validate(spotTaskParams, encryptionDetails);
    connectorValidationResult.setDelegateId(getDelegateId());
    return SpotValidateTaskResponse.builder().connectorValidationResult(connectorValidationResult).build();
  }
}
