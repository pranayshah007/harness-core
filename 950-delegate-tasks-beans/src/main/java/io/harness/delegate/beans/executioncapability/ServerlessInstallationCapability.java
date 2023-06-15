/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.executioncapability;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import java.time.Duration;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class ServerlessInstallationCapability implements ExecutionCapability {
  String criteria;
  CapabilityType capabilityType = CapabilityType.SERVERLESS_INSTALL;

  @Override
  public EvaluationMode evaluationMode() {
    return EvaluationMode.AGENT;
  }

  @Override
  public String fetchCapabilityBasis() {
    return criteria;
  }

  @Override
  public Duration getMaxValidityPeriod() {
    // todo review value later
    return Duration.ofHours(6);
  }

  @Override
  public Duration getPeriodUntilNextValidation() {
    // todo: review value later
    return Duration.ofHours(4);
  }

  /**
   * Error message to show mostly in delegate selection log if none of the delegates passed the validation check
   */
  @Override
  public String getCapabilityValidationError() {
    return "Make sure following delegate(s) have Serverless installed";
  }
}
