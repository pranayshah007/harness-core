package io.harness.entities.instanceinfo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.k8s.model.K8sContainer;

import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@OwnedBy(HarnessTeam.CDP)
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
public class GitopsInstanceInfo extends InstanceInfo {
  @NotNull private String namespace;
  @NotNull private String podName;
  @NotNull private String podId;
  @NotNull private String appIdentifier;
  @NotNull private String agentIdentifier;
  @NotNull private String clusterIdentifier;
  private List<K8sContainer> containerList;
}
