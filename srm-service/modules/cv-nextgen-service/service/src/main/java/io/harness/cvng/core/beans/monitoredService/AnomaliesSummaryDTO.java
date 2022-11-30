/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Value
public class AnomaliesSummaryDTO {
  long logsAnomalies;
  long timeSeriesAnomalies;
  long totalAnomalies;

  @Builder
  public AnomaliesSummaryDTO(long logsAnomalies, long timeSeriesAnomalies) {
    this.logsAnomalies = logsAnomalies;
    this.timeSeriesAnomalies = timeSeriesAnomalies;
    this.totalAnomalies = logsAnomalies + timeSeriesAnomalies;
  }
}
