/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.executioncapability;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import java.time.Duration;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Value;

@Value
@Builder
public class SystemEnvCheckerCapability implements ExecutionCapability {
  @NotNull private String comparate;
  @NotNull private String systemPropertyName;

  @Default private final CapabilityType capabilityType = CapabilityType.SYSTEM_ENV;

  @Override
  public EvaluationMode evaluationMode() {
    return EvaluationMode.AGENT;
  }

  @Override
  public CapabilityType getCapabilityType() {
    return capabilityType;
  }

  @Override
  public String fetchCapabilityBasis() {
    return systemPropertyName + ":" + comparate;
  }

  @Override
  public Duration getMaxValidityPeriod() {
    return Duration.ofHours(6);
  }

  @Override
  public Duration getPeriodUntilNextValidation() {
    return Duration.ofHours(4);
  }

  /**
   * Error message to show mostly in delegate selection log if none of the delegates passed the validation check
   */
  @Override
  public String getCapabilityValidationError() {
    return isNotEmpty(systemPropertyName) && isNotEmpty(comparate)
        ? String.format(
            "Following delegate(s) unable to compare system property %s with value %s", systemPropertyName, comparate)
        : ExecutionCapability.super.getCapabilityValidationError();
  }
}
