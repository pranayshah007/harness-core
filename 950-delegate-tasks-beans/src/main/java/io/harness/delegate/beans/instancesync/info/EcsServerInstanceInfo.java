package io.harness.delegate.beans.instancesync.info;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.ecs.EcsContainer;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;

@JsonTypeName("EcsServerInstanceInfo")
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@OwnedBy(HarnessTeam.CDP)
public class EcsServerInstanceInfo extends ServerInstanceInfo {
    private String region;
    private String clusterArn;
    private String taskArn;
    private String taskDefinitionArn;
    private String launchType;
    private String serviceName;
    private List<EcsContainer> containers; // list of containers
    private long startedAt;
    private String startedBy;
    private Long version;
    private String infraStructureKey; // harness concept

}