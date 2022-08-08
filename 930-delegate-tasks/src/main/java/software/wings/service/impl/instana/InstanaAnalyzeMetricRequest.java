/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instana;

import java.util.List;
import lombok.Builder;
import lombok.Data;

// this request class can be used for both trace and call metrics.
// https://instana.github.io/openapi/#operation/getCallGroup
// https://instana.github.io/openapi/#operation/getTraceGroups
@Data
@Builder
public class InstanaAnalyzeMetricRequest {
  private InstanaTimeFrame timeFrame;
  private Group group;
  private List<InstanaTagFilter> tagFilters;
  private List<Metric> metrics;
  @Data
  @Builder
  public static class Group {
    private String groupByTag;
    private String groupbyTagSecondLevelKey;
  }
  @Data
  @Builder
  public static class Metric {
    private String metric;
    private String aggregation;
    private long granularity;
  }
}
