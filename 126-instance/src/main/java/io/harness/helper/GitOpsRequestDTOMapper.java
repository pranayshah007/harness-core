package io.harness.helper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.InstanceDTO;
import io.harness.dtos.gitops.GitOpsInstanceRequestDTO;
import io.harness.dtos.instanceinfo.K8sInstanceBasicInfoDTO;
import io.harness.entities.InstanceType;
import io.harness.entities.instanceinfo.K8sBasicInfo;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityService;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;

@OwnedBy(HarnessTeam.CDP)
public class GitOpsRequestDTOMapper {
  @Inject private EnvironmentService environmentService;
  @Inject private ServiceEntityService serviceEntityService;
  public InstanceDTO toInstanceDTO(
      GitOpsInstanceRequestDTO gitOpsInstanceRequestDTO, String accountId, K8sInstanceBasicInfoDTO k8sInstanceInfoDTO) {
    String orgId = gitOpsInstanceRequestDTO.getOrgIdentifier();
    String projId = gitOpsInstanceRequestDTO.getProjectIdentifier();
    String envId = gitOpsInstanceRequestDTO.getEnvIdentifier();
    String svcId = gitOpsInstanceRequestDTO.getServiceIdentifier();

    Environment env =
        environmentService.get(accountId, orgId, projId, envId, false).orElseGet(() -> Environment.builder().build());

    ServiceEntity service = serviceEntityService.get(accountId, orgId, projId, svcId, false)
                                .orElseGet(() -> ServiceEntity.builder().build());

    return InstanceDTO.builder()
        .accountIdentifier(accountId)
        .orgIdentifier(orgId)
        .projectIdentifier(projId)
        .envIdentifier(envId)
        .envName(env.getName())
        .envType(env.getType())
        .serviceIdentifier(gitOpsInstanceRequestDTO.getServiceIdentifier())
        .serviceName(service.getName())
        .instanceKey(gitOpsInstanceRequestDTO.getInstanceKey())
        .instanceType(InstanceType.K8S_INSTANCE)
        .lastDeployedById(gitOpsInstanceRequestDTO.getLastDeployedById())
        .lastDeployedByName(gitOpsInstanceRequestDTO.getLastDeployedByName())
        .lastDeployedAt(gitOpsInstanceRequestDTO.getLastDeployedAt())
        .instanceInfoDTO(k8sInstanceInfoDTO)
        .build();
  }
  public List<InstanceDTO> instanceDTOList(List<GitOpsInstanceRequestDTO> gitOpsInstanceRequestDTOS, String accountId) {
    List<InstanceDTO> instanceDTOList = new ArrayList<>();
    for (GitOpsInstanceRequestDTO gitOpsInstanceRequestDTO : gitOpsInstanceRequestDTOS) {
      K8sBasicInfo k8sBasicInfo = gitOpsInstanceRequestDTO.getInstanceInfo();

      K8sInstanceBasicInfoDTO k8sInstanceInfoDTO = K8sInstanceBasicInfoDTO.builder()
                                                       .namespace(k8sBasicInfo.getNamespace())
                                                       .podName(k8sBasicInfo.getPodName())
                                                       .podIP(k8sBasicInfo.getPodIP())
                                                       .containerList(k8sBasicInfo.getContainerList())
                                                       .build();

      instanceDTOList.add(toInstanceDTO(gitOpsInstanceRequestDTO, accountId, k8sInstanceInfoDTO));
    }
    return instanceDTOList;
  }
}
