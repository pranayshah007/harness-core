/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.gitpolling.request;

import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.gitapi.GitApiTaskParams;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.gitpolling.GitPollingSourceDelegateRequest;
import io.harness.delegate.task.gitpolling.GitPollingTaskType;
import io.harness.expression.ExpressionEvaluator;

import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class GitPollingTaskParameters implements TaskParameters, ExecutionCapabilityDemander {
  @NotNull String accountId;
  @NotNull GitPollingSourceDelegateRequest attributes;
  @NotNull GitPollingTaskType gitPollingTaskType;
  private GitApiTaskParams gitApiTaskParams;
  private String webhookId;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return attributes.fetchRequiredExecutionCapabilities(maskingEvaluator);
  }
}
