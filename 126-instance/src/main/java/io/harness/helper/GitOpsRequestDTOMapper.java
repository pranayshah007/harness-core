/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.helper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.gitops.beans.GitOpsInstanceRequest;
import io.harness.dtos.InstanceDTO;
import io.harness.dtos.instanceinfo.GitOpsInstanceInfoDTO;
import io.harness.entities.ArtifactDetails;
import io.harness.entities.InstanceType;
import io.harness.k8s.model.K8sContainer;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.util.InstanceSyncKey;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.validation.constraints.NotEmpty;
import lombok.NonNull;

@OwnedBy(HarnessTeam.GITOPS)
public class GitOpsRequestDTOMapper {
  @Inject private EnvironmentService environmentService;
  @Inject private ServiceEntityService serviceEntityService;

  @NonNull
  public List<InstanceDTO> toInstanceDTOList(@NotEmpty String accountId, @NotEmpty String orgIdentifier,
      @NotEmpty String projectIdentifier, List<GitOpsInstanceRequest> gitOpsInstanceRequests) {
    final List<InstanceDTO> instanceDTOList = new ArrayList<>();
    final Map<String, ServiceEntity> serviceEntityMap = new HashMap<>();
    final Map<String, Environment> envEntityMap = new HashMap<>();

    for (GitOpsInstanceRequest gitOpsInstanceRequest : gitOpsInstanceRequests) {
      String svcId = gitOpsInstanceRequest.getServiceIdentifier();
      String envId = gitOpsInstanceRequest.getEnvIdentifier();

      final List<K8sContainer> k8sContainers = gitOpsInstanceRequest.getInstanceInfo()
                                                   .getContainerList()
                                                   .stream()
                                                   .map(x
                                                       -> K8sContainer.builder()
                                                              .containerId(x.getContainerId())
                                                              .image(x.getImage())
                                                              .name(x.getName())
                                                              .build())
                                                   .collect(Collectors.toList());

      final GitOpsInstanceInfoDTO k8sInstanceInfoDTO = toGitOpsInstanceInfoDTO(gitOpsInstanceRequest, k8sContainers);

      ServiceEntity service = serviceEntityMap.computeIfAbsent(svcId,
          k
          -> serviceEntityService.get(accountId, orgIdentifier, projectIdentifier, k, false)
                 .orElseGet(() -> ServiceEntity.builder().build()));

      Environment env = envEntityMap.computeIfAbsent(envId,
          k
          -> environmentService.get(accountId, orgIdentifier, projectIdentifier, k, false)
                 .orElseGet(() -> Environment.builder().build()));

      instanceDTOList.add(toInstanceDTO(accountId, gitOpsInstanceRequest, k8sInstanceInfoDTO, service, env));
    }
    return instanceDTOList;
  }

  public List<InstanceDTO> toInstanceDTOListForDeletion(
      @NotEmpty String accountId, List<GitOpsInstanceRequest> gitOpsInstanceRequests) {
    final List<InstanceDTO> instanceDTOList = new ArrayList<>();
    for (GitOpsInstanceRequest gitOpsInstanceRequest : gitOpsInstanceRequests) {
      GitOpsInstanceInfoDTO gitOpsInstanceInfoDTO = toGitOpsInstanceInfoDTO(gitOpsInstanceRequest, null);
      instanceDTOList.add(toInstanceDTO(accountId, gitOpsInstanceRequest, gitOpsInstanceInfoDTO, null, null));
    }
    return instanceDTOList;
  }
  public InstanceDTO toInstanceDTO(String accountId, GitOpsInstanceRequest gitOpsInstanceRequest,
      GitOpsInstanceInfoDTO instanceInfoDTO, ServiceEntity service, Environment env) {
    final String orgId = gitOpsInstanceRequest.getOrgIdentifier();
    final String projId = gitOpsInstanceRequest.getProjectIdentifier();
    final String envId = gitOpsInstanceRequest.getEnvIdentifier();
    final String svcId = gitOpsInstanceRequest.getServiceIdentifier();

    return InstanceDTO.builder()
        .accountIdentifier(accountId)
        .orgIdentifier(orgId)
        .projectIdentifier(projId)
        .envIdentifier(envId)
        .envName(env != null ? env.getName() : null)
        .envType(env != null ? env.getType() : null)
        .serviceIdentifier(svcId)
        .serviceName(service != null ? service.getName() : null)
        .infrastructureKind(InfrastructureKind.GITOPS)
        .primaryArtifact(ArtifactDetails.builder().tag(gitOpsInstanceRequest.getBuildId()).build())
        .instanceKey(InstanceSyncKey.builder()
                         .clazz(GitOpsInstanceRequest.class)
                         .part(gitOpsInstanceRequest.getInstanceInfo().getNamespace())
                         .part(gitOpsInstanceRequest.getInstanceInfo().getPodName())
                         .build()
                         .toString())
        .instanceType(InstanceType.K8S_INSTANCE)
        .lastDeployedAt(gitOpsInstanceRequest.getLastDeployedAt())
        .instanceInfoDTO(instanceInfoDTO)
        .build();
  }
  private GitOpsInstanceInfoDTO toGitOpsInstanceInfoDTO(
      GitOpsInstanceRequest gitOpsInstanceRequest, List<K8sContainer> k8sContainers) {
    return GitOpsInstanceInfoDTO.builder()
        .namespace(gitOpsInstanceRequest.getInstanceInfo().getNamespace())
        .appIdentifier(gitOpsInstanceRequest.getApplicationIdentifier())
        .agentIdentifier(gitOpsInstanceRequest.getAgentIdentifier())
        .clusterIdentifier(gitOpsInstanceRequest.getClusterIdentifier())
        .podName(gitOpsInstanceRequest.getInstanceInfo().getPodName())
        .podId(gitOpsInstanceRequest.getInstanceInfo().getPodId())
        .containerList(k8sContainers)
        .build();
  }
}
