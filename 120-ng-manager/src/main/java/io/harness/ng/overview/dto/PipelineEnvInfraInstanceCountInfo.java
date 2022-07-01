package io.harness.ng.overview.dto;

import java.util.List;
import lombok.Builder;

@Builder
public class PipelineEnvInfraInstanceCountInfo {
  private String lastPipelineExecutionId;
  private String lastPipelineExecutionName;
  List<EnvInfraInstanceCountInfo> envInfraInstanceCountInfoList;
}
