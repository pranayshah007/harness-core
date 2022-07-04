/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.executioncapability;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;

import java.time.Duration;
import java.util.Set;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@TargetModule(HarnessModule._957_CG_BEANS)
public class SelectorCapability implements ExecutionCapability {
  private Set<String> selectors;
  @Builder.Default private String selectorOrigin = "default";

  @Builder.Default private final CapabilityType capabilityType = CapabilityType.SELECTORS;

  @Override
  public EvaluationMode evaluationMode() {
    return EvaluationMode.MANAGER;
  }

  @Override
  public CapabilityType getCapabilityType() {
    return capabilityType;
  }

  @Override
  public String fetchCapabilityBasis() {
    return String.join(", ", selectors);
  }

  @Override
  public Duration getMaxValidityPeriod() {
    return null;
  }

  @Override
  public Duration getPeriodUntilNextValidation() {
    return null;
  }
}
