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
import io.harness.delegate.AccountId;
import io.harness.delegate.TaskType;
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
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(DEL)
@Singleton
public class DelegateGrpcClientWrapper {
  @Inject private DelegateServiceGrpcClient delegateServiceGrpcClient;
  @Inject private Supplier<DelegateCallbackToken> delegateCallbackTokenSupplier;
  @Inject @Named("disableDeserialization") private boolean disableDeserialization;

  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;

  public DelegateResponseData executeSyncTaskV2(DelegateTaskRequest delegateTaskRequest) {
    return executeSyncTaskV2ReturnTaskId(delegateTaskRequest).getValue();
  }

  public Pair<String, DelegateResponseData> executeSyncTaskV2ReturnTaskId(DelegateTaskRequest delegateTaskRequest) {
    var responseEntry = delegateServiceGrpcClient.executeSyncTaskReturningResponseDataV2(
        delegateTaskRequest, delegateCallbackTokenSupplier.get());
    final ResponseData responseData = responseEntry.getValue();
    final String taskId = responseEntry.getKey();
    DelegateResponseData delegateResponseData;
    if (disableDeserialization) {
      BinaryResponseData binaryResponseData = (BinaryResponseData) responseData;
      delegateResponseData =
          (DelegateResponseData) referenceFalseKryoSerializer.asInflatedObject(binaryResponseData.getData());
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
    return Pair.of(taskId, delegateResponseData);
  }

  public String submitAsyncTaskV2(DelegateTaskRequest delegateTaskRequest, Duration holdFor) {
    return delegateServiceGrpcClient.submitAsyncTaskV2(
        delegateTaskRequest, delegateCallbackTokenSupplier.get(), holdFor, false);
  }

  public boolean isTaskTypeSupported(AccountId accountId, TaskType taskType) {
    return delegateServiceGrpcClient.isTaskTypeSupported(accountId, taskType);
  }
}
