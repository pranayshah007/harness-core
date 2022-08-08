/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.usage.impl;

import io.harness.timescaledb.tables.pojos.ServiceInfraInfo;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.FieldNameConstants;

@Getter
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants(innerTypeName = "AggregateServiceUsageInfoKeys")
public class AggregateServiceUsageInfo extends ServiceInfraInfo {
  private long serviceInstanceCount;

  public AggregateServiceUsageInfo(
      String orgIdentifier, String projectId, String serviceId, long serviceInstanceCount) {
    super(null, null, serviceId, null, null, null, null, null, null, null, null, null, null, orgIdentifier, projectId,
        null);
    this.serviceInstanceCount = serviceInstanceCount;
  }
}
