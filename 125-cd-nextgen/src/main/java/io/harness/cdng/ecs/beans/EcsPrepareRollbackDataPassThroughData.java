package io.harness.cdng.ecs.beans;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.pms.sdk.core.steps.io.PassThroughData;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDP)
@Value
@Builder
@TypeAlias("ecsPrepareRollbackDataPassThroughData")
@RecasterAlias("io.harness.cdng.ecs.beans.EcsPrepareRollbackDataPassThroughData")
public class EcsPrepareRollbackDataPassThroughData implements PassThroughData {
  String ecsTaskDefinitionManifestContent;
  String ecsServiceDefinitionManifestContent;
  List<String> ecsScalableTargetManifestContentList;
  List<String> ecsScalingPolicyManifestContentList;
  InfrastructureOutcome infrastructureOutcome;
}
