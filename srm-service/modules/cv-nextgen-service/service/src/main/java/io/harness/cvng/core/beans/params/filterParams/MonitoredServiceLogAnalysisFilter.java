/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.params.filterParams;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.cvng.analysis.entities.LogAnalysisResult;

import java.util.List;
import javax.validation.constraints.NotNull;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@FieldDefaults(level = AccessLevel.PRIVATE)
@SuperBuilder
@Value
@NoArgsConstructor
public class MonitoredServiceLogAnalysisFilter extends LogAnalysisFilter {
  @QueryParam("clusterTypes") List<LogAnalysisResult.RadarChartTag> clusterTypes;
  @NotNull @QueryParam("startTime") Long startTimeMillis;
  @NotNull @QueryParam("endTime") Long endTimeMillis;
  @QueryParam("minAngle") Double minAngle;
  @QueryParam("maxAngle") Double maxAngle;
  @QueryParam("clusterId") String clusterId;

  public boolean filterByClusterType() {
    return isNotEmpty(clusterTypes);
  }

  public boolean filterByAngle() {
    if (minAngle != null && maxAngle != null) {
      return true;
    }
    return false;
  }

  public boolean hasClusterIdFilter() {
    return clusterId != null;
  }
}
