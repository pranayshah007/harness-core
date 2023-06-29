/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.executioncapability;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.k8s.model.HelmVersion;

import java.time.Duration;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class HelmInstallationCapability implements ExecutionCapability {
  HelmVersion version;
  String criteria;
  CapabilityType capabilityType = CapabilityType.HELM_INSTALL;

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
    // Delegate(s) missing the commandRequest.getHelmVersion().name(), make sure to include
    // commandRequest.getHelmVersion().name()) with the following delegates : [h1,h2]
    return isNotEmpty(fetchCapabilityBasis()) && version != null
        ? String.format("Delegate(s) missing the %s , make sure to include version %s with following delegates",
            fetchCapabilityBasis(), version.toString())
        : ExecutionCapability.super.getCapabilityValidationError();
  }
}
