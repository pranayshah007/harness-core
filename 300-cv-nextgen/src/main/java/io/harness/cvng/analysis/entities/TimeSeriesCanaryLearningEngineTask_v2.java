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
public class TimeSeriesCanaryLearningEngineTask_v2 extends LearningEngineTask {
  private String preDeploymentDataUrl;
  private String postDeploymentDataUrl;
  private String metricTemplateUrl;
  private TimeSeriesCanaryLearningEngineTask.DeploymentVerificationTaskInfo deploymentVerificationTaskInfo;
  private int dataLength;
  private int tolerance;
  private LearningEngineTask.LearningEngineTaskType learningEngineTaskType;
  Set<String> controlHosts;
  Set<String> testHosts;

  @Override
  public LearningEngineTaskType getType() {
    return learningEngineTaskType;
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
