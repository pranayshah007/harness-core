/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.api;

import io.harness.cvng.core.beans.params.TimeRangeParams;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardWidget;
import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective;

import java.time.Instant;

public interface GraphDataService {
  SLODashboardWidget.SLOGraphData getGraphData(AbstractServiceLevelObjective serviceLevelObjective, Instant startTime,
      Instant endTime, int totalErrorBudgetMinutes, long numOfDataPointsInBetween);
  SLODashboardWidget.SLOGraphData getGraphData(AbstractServiceLevelObjective serviceLevelObjective, Instant startTime,
      Instant endTime, int totalErrorBudgetMinutes, TimeRangeParams timeRangeParams);

  SLODashboardWidget.SLOGraphData getGraphData(AbstractServiceLevelObjective serviceLevelObjective, Instant startTime,
      Instant endTime, int totalErrorBudgetMinutes, TimeRangeParams filter, long numOfDataPointsInBetween);
}
