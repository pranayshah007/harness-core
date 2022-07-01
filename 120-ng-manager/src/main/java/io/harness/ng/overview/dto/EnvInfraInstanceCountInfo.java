package io.harness.ng.overview.dto;

import java.util.List;
import lombok.Builder;

@Builder
public class EnvInfraInstanceCountInfo {
  private String envId;
  private String envName;
  private List<InfraInstanceCount> infraInstanceCountList;
}
