/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.gcp;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.task.gcp.GcpValidationTaskHandler;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.gcp.request.GcpRequest;
import io.harness.delegate.task.gcp.request.GcpTaskParameters;
import io.harness.delegate.task.gcp.response.GcpValidationTaskResponse;
import io.harness.delegate.task.gcp.taskHandlers.TaskHandler;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;

import com.google.inject.Inject;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

@OwnedBy(CDP)
public class GcpTask extends AbstractDelegateRunnableTask {
  @Inject private Map<GcpTaskType, TaskHandler> gcpTaskTypeToTaskHandlerMap;
  @Inject private GcpValidationTaskHandler gcpValidationTaskHandler;

  public GcpTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new UnsupportedOperationException("Object Array parameters not supported");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    if (!(parameters instanceof GcpTaskParameters)) {
      throw new InvalidRequestException("Task Params are not of expected type: GcpTaskParameters");
    }
    final GcpTaskParameters gcpTaskParameters = (GcpTaskParameters) parameters;
    final GcpRequest gcpRequest = gcpTaskParameters.getGcpRequest();

    switch (gcpTaskParameters.getGcpTaskType()) {
      case VALIDATE:
        ConnectorValidationResult connectorValidationResult = gcpValidationTaskHandler.validate(gcpRequest);
        connectorValidationResult.setDelegateId(getDelegateId());
        return GcpValidationTaskResponse.builder().connectorValidationResult(connectorValidationResult).build();
      case LIST_CLUSTERS:
      case LIST_BUCKETS:
        TaskHandler taskHandler = gcpTaskTypeToTaskHandlerMap.get(gcpTaskParameters.getGcpTaskType());
        return taskHandler.executeRequest(gcpRequest);

      default:
        throw new InvalidRequestException(
            "Invalid request type [" + gcpTaskParameters.getGcpTaskType() + "]", WingsException.USER);
    }
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }
}
