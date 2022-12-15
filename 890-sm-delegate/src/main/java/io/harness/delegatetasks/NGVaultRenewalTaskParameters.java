/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegatetasks;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.TaskParameters;
import io.harness.expression.ExpressionEvaluator;

import software.wings.beans.BaseVaultConfig;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@OwnedBy(PL)
@Getter
@Builder
public class NGVaultRenewalTaskParameters implements TaskParameters, ExecutionCapabilityDemander {
  private final BaseVaultConfig encryptionConfig;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return encryptionConfig.fetchRequiredExecutionCapabilities(maskingEvaluator);
  }
}
