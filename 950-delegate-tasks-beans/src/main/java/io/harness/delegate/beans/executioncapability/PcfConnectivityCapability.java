/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.executioncapability;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import java.time.Duration;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PcfConnectivityCapability implements ExecutionCapability {
  @NotNull private String endpointUrl;

  private final CapabilityType capabilityType = CapabilityType.PCF_CONNECTIVITY;

  @Override
  public EvaluationMode evaluationMode() {
    return EvaluationMode.AGENT;
  }

  @Override
  public String fetchCapabilityBasis() {
    return "Pcf:" + endpointUrl;
  }

  @Override
  public Duration getMaxValidityPeriod() {
    return Duration.ofHours(6);
  }

  @Override
  public Duration getPeriodUntilNextValidation() {
    return Duration.ofHours(4);
  }

  @Override
  public String getCapabilityToString() {
    return isNotEmpty(endpointUrl) ? String.format("Capability reach url: %s ", endpointUrl) : null;
  }

  /**
   * Error message to show mostly in delegate selection log if none of the delegates passed the validation check
   */
  @Override
  public String getCapabilityValidationError() {
    // Following delegate(s) unable to connect to {pcf: url} : [h1,h2]
    return isNotEmpty(fetchCapabilityBasis())
        ? String.format("Following delegate(s) unable to connect to  %s,", fetchCapabilityBasis())
        : ExecutionCapability.super.getCapabilityValidationError();
  }
}
