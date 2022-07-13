package io.harness.ng.overview.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ActiveServiceDeploymentsInfo {
  private String envId;
  private String envName;
  private String tag;
  private String pipelineExecutionId;
}
