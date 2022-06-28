package io.harness.mappers.instanceinfo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.instanceinfo.K8sInstanceBasicInfoDTO;
import io.harness.entities.instanceinfo.K8sBasicInfo;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class K8sInstanceBasicInfoMapper {
  public K8sInstanceBasicInfoDTO toDTO(K8sBasicInfo k8sInstanceInfo) {
    return K8sInstanceBasicInfoDTO.builder()
        .containerList(k8sInstanceInfo.getContainerList())
        .namespace(k8sInstanceInfo.getNamespace())
        .podIP(k8sInstanceInfo.getPodIP())
        .podName(k8sInstanceInfo.getPodName())
        .build();
  }

  public K8sBasicInfo toEntity(K8sInstanceBasicInfoDTO k8sInstanceInfoDTO) {
    return K8sBasicInfo.builder()
        .containerList(k8sInstanceInfoDTO.getContainerList())
        .namespace(k8sInstanceInfoDTO.getNamespace())
        .podIP(k8sInstanceInfoDTO.getPodIP())
        .podName(k8sInstanceInfoDTO.getPodName())
        .build();
  }
}
