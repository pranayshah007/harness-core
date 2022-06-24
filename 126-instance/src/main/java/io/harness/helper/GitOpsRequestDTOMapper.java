package io.harness.helper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.InstanceDTO;
import io.harness.dtos.gitops.GitOpsInstanceRequestDTO;
import io.harness.dtos.instanceinfo.K8sInstanceInfoDTO;
import io.harness.entities.InstanceType;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class GitOpsRequestDTOMapper {
  public InstanceDTO toInstanceDTO(
      GitOpsInstanceRequestDTO gitOpsInstanceRequestDTO, String accountId, K8sInstanceInfoDTO k8sInstanceInfoDTO) {
    return InstanceDTO.builder()
        .accountIdentifier(accountId)
        .orgIdentifier(gitOpsInstanceRequestDTO.getOrgIdentifier())
        .projectIdentifier(gitOpsInstanceRequestDTO.getProjectIdentifier())
        .envIdentifier(gitOpsInstanceRequestDTO.getEnvIdentifier())
        .serviceIdentifier(gitOpsInstanceRequestDTO.getServiceIdentifier())
        .instanceKey(gitOpsInstanceRequestDTO.getInstanceKey())
        .instanceType(InstanceType.K8S_INSTANCE)
        .lastDeployedById(gitOpsInstanceRequestDTO.getLastDeployedById())
        .lastDeployedByName(gitOpsInstanceRequestDTO.getLastDeployedByName())
        .lastDeployedAt(gitOpsInstanceRequestDTO.getLastDeployedAt())
        .instanceInfoDTO(k8sInstanceInfoDTO)
        // TODO: Achyuth -- extract and set other fields
        .build();
  }
  public List<InstanceDTO> instanceDTOList(List<GitOpsInstanceRequestDTO> gitOpsInstanceRequestDTOS, String accountId) {
    List<InstanceDTO> instanceDTOList = new ArrayList<>();
    for (GitOpsInstanceRequestDTO gitOpsInstanceRequestDTO : gitOpsInstanceRequestDTOS) {
      K8sInstanceInfoDTO k8sInstanceInfoDTO = null; // TODO: Achyuth -- initialize correctly from InstanceInfo
      instanceDTOList.add(toInstanceDTO(gitOpsInstanceRequestDTO, accountId, k8sInstanceInfoDTO));
    }
    return instanceDTOList;
  }
}
