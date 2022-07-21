/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.adviser.manualintervention;

import io.harness.pms.contracts.commons.RepairActionCode;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.sdk.core.adviser.WithFailureTypes;
import io.harness.pms.sdk.core.adviser.retry.RetryAdviserParameters;

import java.util.EnumSet;
import java.util.Set;
import lombok.Builder;
import lombok.Value;
@Value
@Builder
public class ManualInterventionAdviserParameters implements WithFailureTypes {
  @Builder.Default Set<FailureType> applicableFailureTypes = EnumSet.noneOf(FailureType.class);
  Integer timeout;
  RepairActionCode timeoutAction;

  // Config only used when timeoutAction is Retry.
  RetryAdviserParameters retryAdviserParameters;
}
