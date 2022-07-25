package io.harness.cdng.ecs.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@OwnedBy(HarnessTeam.CDP)
@Value
@Builder
public class EcsStepExecutorParams {
  boolean shouldOpenFetchFilesLogStream;

  String ecsTaskDefinitionManifestContent;
  String ecsServiceDefinitionManifestContent;
  List<String> ecsScalableTargetManifestContentList;
  List<String> ecsScalingPolicyManifestContentList;
}
