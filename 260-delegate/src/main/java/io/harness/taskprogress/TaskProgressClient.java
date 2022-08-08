/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.taskprogress;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.callback.DelegateCallbackToken;
import io.harness.delegate.AccountId;
import io.harness.delegate.DelegateServiceAgentClient;
import io.harness.delegate.TaskId;
import io.harness.delegate.beans.DelegateProgressData;
import io.harness.delegate.beans.taskprogress.ITaskProgressClient;
import io.harness.serializer.KryoSerializer;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Builder
@Slf4j
@TargetModule(HarnessModule._420_DELEGATE_AGENT)
public class TaskProgressClient implements ITaskProgressClient {
  private final DelegateServiceAgentClient delegateServiceAgentClient;
  private final KryoSerializer kryoSerializer;

  private final String accountId;
  private final String taskId;
  private final String delegateCallbackToken;

  @Override
  public boolean sendTaskProgressUpdate(DelegateProgressData delegateProgressData) {
    byte[] progressData = kryoSerializer.asDeflatedBytes(delegateProgressData);

    return delegateServiceAgentClient.sendTaskProgressUpdate(AccountId.newBuilder().setId(accountId).build(),
        TaskId.newBuilder().setId(taskId).build(),
        DelegateCallbackToken.newBuilder().setToken(delegateCallbackToken).build(), progressData);
  }
}
