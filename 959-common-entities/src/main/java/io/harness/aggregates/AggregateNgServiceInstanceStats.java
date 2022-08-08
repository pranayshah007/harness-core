/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aggregates;

import io.harness.timescaledb.tables.pojos.NgInstanceStats;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.FieldNameConstants;

@Getter
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants(innerTypeName = "AggregateNgServiceInstanceStatsKeys")
public class AggregateNgServiceInstanceStats extends NgInstanceStats {
  private long aggregateServiceInstanceCount;

  public AggregateNgServiceInstanceStats(String orgIdentifier, String projectId, String serviceId, long count) {
    super(null, null, orgIdentifier, projectId, serviceId, null, null, null, 0);
    this.aggregateServiceInstanceCount = count;
  }
}
