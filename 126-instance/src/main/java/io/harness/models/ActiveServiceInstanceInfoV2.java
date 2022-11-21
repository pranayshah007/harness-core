package io.harness.models;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ActiveServiceInstanceInfoV2 {
  private String serviceIdentifier;
  private String serviceName;
  private String envIdentifier;
  private String envName;
  private String infraIdentifier;
  private String infraName;
  private String clusterIdentifier;
  private String agentIdentifier;
  private String lastPipelineExecutionId;
  private String lastPipelineExecutionName;
  private Long lastDeployedAt;
  private String tag;
  private String displayName;
  private int count;
}
