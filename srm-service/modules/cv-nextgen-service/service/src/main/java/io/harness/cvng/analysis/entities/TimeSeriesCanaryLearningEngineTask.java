/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.entities;

import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Value;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TimeSeriesCanaryLearningEngineTask extends LearningEngineTask {
  private String postDeploymentDataUrl;
  private String preDeploymentDataUrl;
  private String metricTemplateUrl;
  private DeploymentVerificationTaskInfo deploymentVerificationTaskInfo;
  private int dataLength;
  private int tolerance;
  @Override
  public LearningEngineTaskType getType() {
    return LearningEngineTaskType.TIME_SERIES_CANARY;
  }

  @Value
  @Builder
  public static class DeploymentVerificationTaskInfo {
    private long deploymentStartTime;
    private Set<String> oldVersionHosts;
    private Set<String> newVersionHosts;
    private Integer newHostsTrafficSplitPercentage;
  }
}
