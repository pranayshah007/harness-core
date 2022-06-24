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
public class K8sBasicInfo extends InstanceInfo {
  @NotNull private String namespace;
  @NotNull private String podName;
  private String podIP;
  @NotNull private List<K8sContainer> containerList;
}
