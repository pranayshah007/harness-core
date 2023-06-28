/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.utils;

import io.harness.beans.DelegateTaskRequest;
import io.harness.callback.DelegateCallbackToken;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.function.Supplier;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class TaskExecutionUtils {
  private final DelegateServiceGrpcClient delegateServiceGrpcClient;
  private final Supplier<DelegateCallbackToken> delegateCallbackTokenSupplier;

  public ResponseData executeSyncTask(DelegateTaskRequest taskRequest) {
    return delegateServiceGrpcClient
        .executeSyncTaskReturningResponseDataV2(taskRequest, delegateCallbackTokenSupplier.get())
        .getValue();
  }
}
