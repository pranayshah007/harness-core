/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.advisers.rollback;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@TypeAlias("onFailRollbackParameters")
@OwnedBy(CDC)
@RecasterAlias("io.harness.advisers.rollback.OnFailRollbackParameters")
public class OnFailRollbackParameters implements StepParameters {
  RollbackStrategy strategy;
  @Singular("strategyToUuid") Map<RollbackStrategy, String> strategyToUuid;
  Set<FailureType> applicableFailureTypes;
}
