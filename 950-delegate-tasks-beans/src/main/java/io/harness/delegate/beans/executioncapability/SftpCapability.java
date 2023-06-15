/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.executioncapability;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;

import java.time.Duration;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@OwnedBy(CDC)
@Value
@Builder
public class SftpCapability implements ExecutionCapability {
  @NotNull String sftpUrl;
  @Builder.Default private final CapabilityType capabilityType = CapabilityType.SFTP;

  @Override
  public EvaluationMode evaluationMode() {
    return EvaluationMode.AGENT;
  }

  @Override
  public String fetchCapabilityBasis() {
    return sftpUrl;
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
    return isNotEmpty(fetchCapabilityBasis())
        ? String.format(
            "Delegate(s) unable to connect to  %s, make sure to provide the connectivity with the following delegates",
            fetchCapabilityBasis())
        : ExecutionCapability.super.getCapabilityValidationError();
  }
}
