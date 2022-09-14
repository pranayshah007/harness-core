package io.harness.delegate.beans.ecs;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@OwnedBy(HarnessTeam.CDP)
public class EcsBlueGreenPrepareRollbackDataResult {
    String serviceName;
    String createServiceRequestBuilderString;
    List<String> registerScalableTargetRequestBuilderStrings;
    List<String> registerScalingPolicyRequestBuilderStrings;
    boolean isFirstDeployment;
    String loadBalancer;
    String listenerArn;
    String listenerRuleArn;
    String targetGroupArn;
}
