/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.internal.OrchestrationAdjacencyListInternal;
import io.harness.cache.Distributable;
import io.harness.cache.Nominal;
import io.harness.pms.contracts.execution.Status;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.Wither;

@OwnedBy(CDC)
@Value
@Builder
public class OrchestrationGraph implements Distributable, Nominal {
  // previous value was - ObjectStreamClass.lookup(OrchestrationGraph.class).getSerialVersionUID()
  public static final long STRUCTURE_HASH = 5750747935866324077L;
  public static final long ALGORITHM_ID = 3;

  // cache variables
  long cacheContextOrder;
  String cacheKey;
  List<String> cacheParams;
  @Wither @Builder.Default long lastUpdatedAt = System.currentTimeMillis();

  String planExecutionId;
  Long startTs;
  @Wither Long endTs;
  @Wither Status status;

  List<String> rootNodeIds;
  OrchestrationAdjacencyListInternal adjacencyList;

  @Override
  public long structureHash() {
    return STRUCTURE_HASH;
  }

  @Override
  public long algorithmId() {
    return ALGORITHM_ID;
  }

  @Override
  public String key() {
    return cacheKey;
  }

  @Override
  public List<String> parameters() {
    return cacheParams;
  }

  @Override
  public long contextHash() {
    return cacheContextOrder;
  }
}
