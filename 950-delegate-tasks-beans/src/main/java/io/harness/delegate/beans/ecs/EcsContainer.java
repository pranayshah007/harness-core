package io.harness.delegate.beans.ecs;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EcsContainer {
  private String containerArn;
  private String name;
  private String image;
  private String runtimeId; // docker container id
}
