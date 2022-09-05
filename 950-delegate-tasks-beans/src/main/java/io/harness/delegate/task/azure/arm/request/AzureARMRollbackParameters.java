/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.azure.arm.request;

import static io.harness.delegate.task.azure.arm.AzureARMTaskType.ARM_ROLLBACK;

import io.harness.delegate.task.azure.arm.AzureARMTaskParameters;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AzureARMRollbackParameters extends AzureARMTaskParameters {
  @Builder
  public AzureARMRollbackParameters(String appId, String accountId, String activityId, String subscriptionId,
      String commandName, Integer timeoutIntervalInMin) {
    super(appId, accountId, activityId, subscriptionId, commandName, timeoutIntervalInMin, ARM_ROLLBACK);
  }
}
