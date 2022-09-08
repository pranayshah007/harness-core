package io.harness.delegate.beans.ecs;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(HarnessTeam.CDP)
public class EcsPrepareRollbackDataResult {
  String serviceName;
  String createServiceRequestBuilderString;
  List<String> registerScalableTargetRequestBuilderStrings;
  List<String> registerScalingPolicyRequestBuilderStrings;
  private boolean isFirstDeployment;
}
