package io.harness.delegate.beans.ecs;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@OwnedBy(HarnessTeam.CDP)
public class EcsBlueGreenCreateServiceResult {
    private String region;
    private List<EcsTask> ecsTasks;
    private boolean isNewServiceCreated;
    private String serviceName;
    private String loadBalancer;
    private String listenerArn;
    private String listenerRuleArn;
    private String targetGroupArn;

}
