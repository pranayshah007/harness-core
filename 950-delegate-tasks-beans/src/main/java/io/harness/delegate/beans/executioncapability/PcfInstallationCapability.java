/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.executioncapability;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pcf.model.CfCliVersion;

import java.time.Duration;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class PcfInstallationCapability implements ExecutionCapability {
  CfCliVersion version;
  String criteria;
  CapabilityType capabilityType = CapabilityType.PCF_INSTALL;

  @Override
  public EvaluationMode evaluationMode() {
    return EvaluationMode.AGENT;
  }

  @Override
  public CapabilityType getCapabilityType() {
    return CapabilityType.PCF_INSTALL;
  }

  @Override
  public String fetchCapabilityBasis() {
    return criteria;
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
    // Make sure following delegate(s) have CF CLI {version} installed : [h1,h2]
    return version != null ? String.format("Make sure following delegate(s) have CF CLI %s installed", version)
                           : ExecutionCapability.super.getCapabilityValidationError();
  }
}
