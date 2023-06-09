/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.executioncapability;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.executioncapability.CapabilityType.KUSTOMIZE;

import io.harness.data.structure.HarnessStringUtils;

import java.time.Duration;
import lombok.Data;

@Data
public class KustomizeCapability implements ExecutionCapability {
  private final String pluginRootDir;

  @Override
  public EvaluationMode evaluationMode() {
    return EvaluationMode.AGENT;
  }

  @Override
  public CapabilityType getCapabilityType() {
    return KUSTOMIZE;
  }

  @Override
  public String fetchCapabilityBasis() {
    return HarnessStringUtils.join(":", "kustomizePluginDir", pluginRootDir);
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
    return ExecutionCapability.super.getCapabilityToString();
  }

  /**
   * Error message to show mostly in delegate selection log if none of the delegates passed the validation check
   */
  @Override
  public String getCapabilityValidationError() {
    // Following Delegate(s) doesn't have Kustomize plugin directory at %s : [h1,h2]
    return isNotEmpty(fetchCapabilityBasis())
        ? String.format("Following Delegate(s) doesn't have Kustomize plugin directory at %s", fetchCapabilityBasis())
        : ExecutionCapability.super.getCapabilityValidationError();
  }
}
