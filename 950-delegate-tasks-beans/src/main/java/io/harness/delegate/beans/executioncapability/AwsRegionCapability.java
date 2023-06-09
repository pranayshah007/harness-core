/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.executioncapability;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import java.time.Duration;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Value;

@Value
@Builder
public class AwsRegionCapability implements ExecutionCapability {
  private String region;
  @Default private final CapabilityType capabilityType = CapabilityType.AWS_REGION;

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
    return region;
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
    // Following delegate(s) not running in same region : %s : [h1,h2]
    return isNotEmpty(fetchCapabilityBasis())
        ? String.format("Following delegate(s) not running in same region as %s", fetchCapabilityBasis())
        : ExecutionCapability.super.getCapabilityValidationError();
  }
}
