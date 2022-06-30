package io.harness.dtos.instanceinfo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.k8s.model.K8sContainer;

import java.util.List;
import javax.validation.constraints.NotNull;

import io.harness.util.InstanceSyncKey;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
@OwnedBy(HarnessTeam.CDP)
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
public class K8sInstanceBasicInfoDTO extends InstanceInfoDTO {
  @NotNull private String namespace;
  @NotNull private String podName;
  private String podIP;
  @NotNull private List<K8sContainer> containerList;

  @Override
  public String prepareInstanceKey() {
    return InstanceSyncKey.builder()
            .clazz(K8sInstanceBasicInfoDTO.class)
            .part(podName)
            .part(namespace)
            .build()
            .toString();  }

  @Override
  public String prepareInstanceSyncHandlerKey() {
    return InstanceSyncKey.builder().part(podName).build().toString();
  }

  @Override
  public String getType() {
    return "K8s";
  }
}
