/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.vmss.handler;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.azure.vmss.ng.request.AzureVMSSTaskRequest;
import io.harness.delegate.task.azure.vmss.ng.response.AzureVMSSRequestResponse;
import io.harness.exception.InvalidArgumentsException;

import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(CDP)
public abstract class AzureVMSSRequestHandler<T extends AzureVMSSTaskRequest> {
  public final AzureVMSSRequestResponse handleRequest(AzureVMSSTaskRequest azureVMSSTaskRequest) {
    if (!getRequestType().isAssignableFrom(azureVMSSTaskRequest.getClass())) {
      throw new InvalidArgumentsException(Pair.of("azureVMSSTaskRequest",
          format("Unexpected type of task request [%s], expected [%s]", azureVMSSTaskRequest.getClass().getSimpleName(),
              getRequestType().getSimpleName())));
    }

    return execute((T) azureVMSSTaskRequest);
  }

  protected abstract AzureVMSSRequestResponse execute(T azureVMSSTaskRequest);

  protected abstract Class<T> getRequestType();
}
