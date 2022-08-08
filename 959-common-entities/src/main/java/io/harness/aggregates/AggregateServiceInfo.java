/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aggregates;

import io.harness.timescaledb.tables.pojos.ServiceInfraInfo;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = true)
public class AggregateServiceInfo extends ServiceInfraInfo {
  private long count;

  public AggregateServiceInfo(String orgIdentifier, String projectId, String serviceId, long count) {
    super(null, null, serviceId, null, null, null, null, null, null, null, null, null, null, orgIdentifier, projectId,
        null);
    this.count = count;
  }

  public AggregateServiceInfo(
      String orgIdentifier, String projectId, String serviceId, String serviceStatus, long count) {
    super(null, null, serviceId, null, null, null, null, null, null, serviceStatus, null, null, null, orgIdentifier,
        projectId, null);
    this.count = count;
  }
}
