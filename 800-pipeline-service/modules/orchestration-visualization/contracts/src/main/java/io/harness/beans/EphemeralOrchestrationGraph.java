/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.internal.OrchestrationAdjacencyListInternal;
import io.harness.pms.contracts.execution.Status;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@OwnedBy(CDC)
@Value
@Builder
public class EphemeralOrchestrationGraph {
  String planExecutionId;
  Long startTs;
  Long endTs;
  Status status;

  List<String> rootNodeIds;
  OrchestrationAdjacencyListInternal adjacencyList;
}
