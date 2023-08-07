/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.advisers.retry;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.advisers.rollback.RollbackStrategy;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.commons.RepairActionCode;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.sdk.core.adviser.WithFailureTypes;
import io.harness.yaml.core.failurestrategy.FailureStrategyActionConfig;

import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDC)
@Value
@Builder
@TypeAlias("retryAdviserRollbackParameters")
public class RetryAdviserRollbackParameters implements WithFailureTypes {
  List<Integer> waitIntervalList;
  int retryCount;
  RepairActionCode repairActionCodeAfterRetry;
  FailureStrategyActionConfig retryActionConfig;
  Set<FailureType> applicableFailureTypes;
  // Only applicable if the repair action code after retry is set to IGNORE
  String nextNodeId;
  // Only applicable for finding
  @Singular("strategyToUuid") Map<RollbackStrategy, String> strategyToUuid;
}
