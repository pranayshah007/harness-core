package io.harness.dtos.gitops;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.instanceinfo.InstanceInfoDTO;
import io.harness.dtos.instanceinfo.K8sInstanceInfoDTO;
import io.harness.entities.ArtifactDetails;
import io.harness.entities.instanceinfo.K8sBasicInfo;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import io.harness.entities.InstanceType;

import io.harness.entities.instanceinfo.K8sBasicInfo;
import io.harness.k8s.model.K8sContainer;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.util.InstanceSyncKey;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Setter;
import lombok.Value;

import java.util.stream.Collectors;

import static io.harness.data.structure.CollectionUtils.emptyIfNull;

@OwnedBy(HarnessTeam.CDP)
@Value
@Builder
@EqualsAndHashCode(callSuper = true)
public class GitOpsInstanceRequestDTO extends InstanceInfoDTO {
  @NotEmpty private String orgIdentifier;
  @NotEmpty private String projectIdentifier;
  @NotEmpty private String envIdentifierServiceIdentifier;
  @NotEmpty private String serviceIdentifier;
  @NotEmpty private String lastDeployedById;
  @NotEmpty private String lastDeployedByName;
  @Setter ArtifactDetails artifactDetails;
  @NotNull  private long lastDeployedAt;
  @NotNull  private K8sBasicInfo instanceInfo; // private K8sBasicInfo instanceInfo

  @Override
  public String prepareInstanceKey() {
    return InstanceSyncKey.builder()
            .clazz(K8sInstanceInfoDTO.class)
            .part(instanceInfo.getPodName())
            .part(instanceInfo.getNamespace())
            .part(getImageInStringFormat())
            .build()
            .toString();
  }

  @Override
  public String prepareInstanceSyncHandlerKey() {
    return InstanceSyncKey.builder().part(getPodName()).build().toString();
  }

  @Override
  public String getPodName() {
    return instanceInfo.getPodName();
  }

  private String getImageInStringFormat() {
    return emptyIfNull(instanceInfo.getContainerList()).stream().map(K8sContainer::getImage).collect(Collectors.joining());
  }

  @Override
  public String getType() {
    return "K8s";
  }
}
