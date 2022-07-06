package io.harness.models;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ActiveServiceInstanceInfo {
  private String infraIdentifier;
  private String infraName;
  private String lastPipelineExecutionId;
  private String lastPipelineExecutionName;
  private String lastDeployedAt;
  private String envIdentifier;
  private String envName;
  private String tag;
  private int count;
}
