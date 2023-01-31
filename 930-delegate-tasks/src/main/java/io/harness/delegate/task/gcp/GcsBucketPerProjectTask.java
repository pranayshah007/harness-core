/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.gcp;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.task.gcp.GcpValidationTaskHandler;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.artifacts.googlecloudstorage.GcsListBucketsTaskHandler;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.delegate.task.gcp.request.GcpTaskParameters;
import io.harness.delegate.task.gcp.request.GcsListBucketsRequest;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Inject;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

@OwnedBy(CDP)
public class GcsBucketPerProjectTask extends AbstractDelegateRunnableTask {
  @Inject private GcsListBucketsTaskHandler gcsListBucketsTaskHandler;
  @Inject private GcpValidationTaskHandler gcpValidationTaskHandler;

  public GcsBucketPerProjectTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
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
    final GcsListBucketsRequest gcsListBucketsRequest = (GcsListBucketsRequest) gcpTaskParameters.getGcpRequest();
    return gcsListBucketsTaskHandler.executeRequest(gcsListBucketsRequest);
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }
}
