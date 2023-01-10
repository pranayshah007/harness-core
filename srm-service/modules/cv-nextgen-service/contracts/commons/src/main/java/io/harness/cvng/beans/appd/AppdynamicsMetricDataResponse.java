/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans.appd;

import io.harness.cvng.beans.ThirdPartyApiResponseStatus;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.Value;

@Value
@Builder
public class AppdynamicsMetricDataResponse {
  private Long startTime;
  private Long endTime;
  private ThirdPartyApiResponseStatus responseStatus;
  private List<DataPoint> dataPoints;

  @Data
  @Builder
  public static class DataPoint {
    long timestamp;
    double value;
  }
}
