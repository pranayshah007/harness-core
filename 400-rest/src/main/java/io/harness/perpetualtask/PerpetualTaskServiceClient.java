/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.perpetualtask;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.DelegateTask;

import com.google.protobuf.Message;

/**
 * Used on the manager side to handle CRUD of a specific type of perpetual tasks.
 */
@OwnedBy(HarnessTeam.DEL)
@TargetModule(HarnessModule._920_DELEGATE_SERVICE_BEANS)
@Deprecated
public interface PerpetualTaskServiceClient {
  Message getTaskParams(PerpetualTaskClientContext clientContext, boolean referenceFalseKryoSerializer);
  DelegateTask getValidationTask(PerpetualTaskClientContext clientContext, String accountId);
}
