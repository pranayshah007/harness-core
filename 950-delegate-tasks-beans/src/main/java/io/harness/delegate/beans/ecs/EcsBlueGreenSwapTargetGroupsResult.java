package io.harness.delegate.beans.ecs;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@OwnedBy(HarnessTeam.CDP)
public class EcsBlueGreenSwapTargetGroupsResult {
    private boolean trafficShifted;
    private String region;
    private List<EcsTask> ecsTasks;
    private String infrastructureKey;
    private String loadBalancer;
    private String prodListenerArn;
    private String prodListenerRuleArn;
    private String prodTargetGroupArn;
    private String stageListenerArn;
    private String stageListenerRuleArn;
    private String stageTargetGroupArn;
}
