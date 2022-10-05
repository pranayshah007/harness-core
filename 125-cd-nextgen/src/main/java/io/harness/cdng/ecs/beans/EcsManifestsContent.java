package io.harness.cdng.ecs.beans;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EcsManifestsContent {
  private String ecsTaskDefinitionFileContent;
  private String ecsServiceDefinitionFileContent;
  private List<String> ecsScalableTargetManifestContentList;
  private List<String> ecsScalingPolicyManifestContentList;
}
