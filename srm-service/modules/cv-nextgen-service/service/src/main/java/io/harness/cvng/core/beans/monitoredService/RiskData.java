/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService;

import io.harness.cvng.analysis.beans.Risk;
import io.harness.cvng.core.beans.params.TimeRangeParams;

import lombok.Builder;
import lombok.Data;
import org.jetbrains.annotations.NotNull;

@Data
@Builder
public class RiskData implements Comparable<RiskData> {
  Integer healthScore;
  Risk riskStatus;
  long startTime;
  long endTime;

  @Deprecated TimeRangeParams timeRangeParams;

  @Override
  public int compareTo(@NotNull RiskData o) {
    if (healthScore == null && o.getHealthScore() == null) {
      return riskStatus.compareTo(o.getRiskStatus());
    } else if (healthScore == null) {
      return -1;
    } else if (o.getHealthScore() == null) {
      return 1;
    } else if (healthScore < o.getHealthScore()) {
      return 1;
    } else if (healthScore > o.getHealthScore()) {
      return -1;
    }
    return 0;
  }
}
