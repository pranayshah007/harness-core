package io.harness.beans.environment;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ServiceDefinitionInfo {
  private String identifier;
  private String name;
  private String image;
  private String containerName;
}