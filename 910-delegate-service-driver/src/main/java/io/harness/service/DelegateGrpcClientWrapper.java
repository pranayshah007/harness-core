/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service;

import static io.harness.annotations.dev.HarnessTeam.DEL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.callback.DelegateCallbackToken;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.BinaryResponseData;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.function.Supplier;

@OwnedBy(DEL)
@Singleton
public class DelegateGrpcClientWrapper {
  @Inject private DelegateServiceGrpcClient delegateServiceGrpcClient;
  @Inject private Supplier<DelegateCallbackToken> delegateCallbackTokenSupplier;
  @Inject @Named("disableDeserialization") private boolean disableDeserialization;
  @Inject private KryoSerializer kryoSerializer;

  public DelegateResponseData executeSyncTask(DelegateTaskRequest delegateTaskRequest) {
    final ResponseData responseData = delegateServiceGrpcClient.executeSyncTaskReturningResponseData(
        delegateTaskRequest, delegateCallbackTokenSupplier.get());
    DelegateResponseData delegateResponseData;
    if (disableDeserialization) {
      delegateResponseData =
          (DelegateResponseData) kryoSerializer.asInflatedObject(((BinaryResponseData) responseData).getData());
      if (delegateResponseData instanceof ErrorNotifyResponseData) {
        WingsException exception = ((ErrorNotifyResponseData) delegateResponseData).getException();
        // if task registered to error handling framework on delegate, then exception won't be null
        if (exception != null) {
          throw exception;
        }
      } else if (delegateResponseData instanceof RemoteMethodReturnValueData) {
        Throwable throwable = ((RemoteMethodReturnValueData) delegateResponseData).getException();
        if (throwable != null) {
          throw new InvalidRequestException(ExceptionUtils.getMessage(throwable), throwable);
        }
      }
    } else {
      delegateResponseData = (DelegateResponseData) responseData;
    }
    return delegateResponseData;
  }

  public String submitAsyncTask(DelegateTaskRequest delegateTaskRequest, Duration holdFor) {
    return delegateServiceGrpcClient.submitAsyncTask(delegateTaskRequest, delegateCallbackTokenSupplier.get(), holdFor);
  }
}
