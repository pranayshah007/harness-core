package io.harness.models;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ActiveServiceInstanceInfoWithoutEnvWithServiceDetails {
  private String serviceIdentifier;
  private String serviceName;
  private String infraIdentifier;
  private String infraName;
  private String lastPipelineExecutionId;
  private String lastPipelineExecutionName;
  private String lastDeployedAt;
  private String tag;
  private String displayName;
  private int count;
}
