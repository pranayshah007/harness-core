package io.harness.ng.overview.dto;

import java.util.List;
import lombok.Builder;

@Builder
public class BuildIdEnvInfraInstanceCountInfo {
  private String BuildId;
  private List<PipelineEnvInfraInstanceCountInfo> pipelineEnvInfraInstanceCountInfoList;
}
