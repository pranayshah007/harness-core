/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.helper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.gitops.beans.GitOpsInstance;
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
      @NotEmpty String projectIdentifier, List<GitOpsInstance> gitOpsInstanceRequests) {
    final List<InstanceDTO> instanceDTOList = new ArrayList<>();
    final Map<String, ServiceEntity> serviceEntityMap = new HashMap<>();
    final Map<String, Environment> envEntityMap = new HashMap<>();

    for (GitOpsInstance gitOpsInstance : gitOpsInstanceRequests) {
      String svcId = gitOpsInstance.getServiceIdentifier();
      String envId = gitOpsInstance.getEnvIdentifier();

      final List<K8sContainer> k8sContainers = gitOpsInstance.getInstanceInfo()
                                                   .getContainerList()
                                                   .stream()
                                                   .map(x
                                                       -> K8sContainer.builder()
                                                              .containerId(x.getContainerId())
                                                              .image(x.getImage())
                                                              .name(x.getName())
                                                              .build())
                                                   .collect(Collectors.toList());

      final GitOpsInstanceInfoDTO k8sInstanceInfoDTO = toGitOpsInstanceInfoDTO(gitOpsInstance, k8sContainers);

      ServiceEntity service = serviceEntityMap.computeIfAbsent(svcId,
          k
          -> serviceEntityService.getMetadata(accountId, orgIdentifier, projectIdentifier, k, false)
                 .orElseGet(() -> ServiceEntity.builder().build()));

      Environment env = envEntityMap.computeIfAbsent(envId,
          k
          -> environmentService.getMetadata(accountId, orgIdentifier, projectIdentifier, k, false)
                 .orElseGet(() -> Environment.builder().build()));

      instanceDTOList.add(toInstanceDTO(accountId, gitOpsInstance, k8sInstanceInfoDTO, service, env));
    }
    return instanceDTOList;
  }

  @NonNull
  public List<GitOpsInstance> toGitOpsInstanceList(@NotEmpty String accountId, @NotEmpty String orgIdentifier,
      @NotEmpty String projectIdentifier, List<GitOpsInstanceRequest> gitOpsInstance) {
    final List<GitOpsInstance> gitOpsInstanceDTOList = new ArrayList<>();
    for (GitOpsInstanceRequest gitOpsInstanceRequest : gitOpsInstance) {
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
      gitOpsInstanceDTOList.add(toGitOpsInstance(accountId, gitOpsInstanceRequest));
    }
    return gitOpsInstanceDTOList;
  }

  public List<InstanceDTO> toInstanceDTOListForDeletion(
      @NotEmpty String accountId, List<GitOpsInstanceRequest> gitOpsInstanceRequests) {
    final List<InstanceDTO> instanceDTOList = new ArrayList<>();
    for (GitOpsInstanceRequest gitOpsInstanceRequest : gitOpsInstanceRequests) {
      GitOpsInstance gitOpsInstance = toGitOpsInstance(accountId, gitOpsInstanceRequest);
      GitOpsInstanceInfoDTO gitOpsInstanceInfoDTO = toGitOpsInstanceInfoDTO(gitOpsInstance, null);
      instanceDTOList.add(toInstanceDTO(accountId, gitOpsInstance, gitOpsInstanceInfoDTO, null, null));
    }
    return instanceDTOList;
  }

  public InstanceDTO toInstanceDTO(String accountId, GitOpsInstance gitOpsInstance,
      GitOpsInstanceInfoDTO instanceInfoDTO, ServiceEntity service, Environment env) {
    final String orgId = gitOpsInstance.getOrgIdentifier();
    final String projId = gitOpsInstance.getProjectIdentifier();
    final String envId = gitOpsInstance.getEnvIdentifier();
    final String svcId = gitOpsInstance.getServiceIdentifier();

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
        .primaryArtifact(
            ArtifactDetails.builder().tag(gitOpsInstance.getBuildId()).displayName(gitOpsInstance.getBuildId()).build())
        .instanceKey(InstanceSyncKey.builder()
                         .clazz(GitOpsInstanceRequest.class)
                         .part(gitOpsInstance.getInstanceInfo().getNamespace())
                         .part(gitOpsInstance.getInstanceInfo().getPodName())
                         .build()
                         .toString())
        .instanceType(InstanceType.K8S_INSTANCE)
        .lastDeployedAt(gitOpsInstance.getLastExecutedAt())
        .podCreatedAt(gitOpsInstance.getLastDeployedAt())
        .instanceInfoDTO(instanceInfoDTO)
        .lastPipelineExecutionName(gitOpsInstance.getPipelineName())
        .lastPipelineExecutionId(gitOpsInstance.getPipelineExecutionId())
        .lastDeployedByName(gitOpsInstance.getLastDeployedByName())
        .lastDeployedById(gitOpsInstance.getLastDeployedById())
        .build();
  }

  public GitOpsInstance toGitOpsInstance(String accountId, GitOpsInstanceRequest gitOpsInstanceRequest) {
    final String orgId = gitOpsInstanceRequest.getOrgIdentifier();
    final String projId = gitOpsInstanceRequest.getProjectIdentifier();
    final String envId = gitOpsInstanceRequest.getEnvIdentifier();
    final String svcId = gitOpsInstanceRequest.getServiceIdentifier();

    return GitOpsInstance.builder()
        .accountIdentifier(accountId)
        .orgIdentifier(orgId)
        .projectIdentifier(projId)
        .applicationIdentifier(gitOpsInstanceRequest.getApplicationIdentifier())
        .envIdentifier(envId)
        .clusterIdentifier(gitOpsInstanceRequest.getClusterIdentifier())
        .agentIdentifier(gitOpsInstanceRequest.getAgentIdentifier())
        .serviceIdentifier(svcId)
        .serviceEnvIdentifier(svcId + '-' + envId)
        .buildId(gitOpsInstanceRequest.getBuildId())
        .creationTimestamp(gitOpsInstanceRequest.getCreationTimestamp())
        .lastDeployedAt(gitOpsInstanceRequest.getLastDeployedAt())
        .instanceInfo(gitOpsInstanceRequest.getInstanceInfo())
        .build();
  }

  private GitOpsInstanceInfoDTO toGitOpsInstanceInfoDTO(
      GitOpsInstance gitOpsInstance, List<K8sContainer> k8sContainers) {
    return GitOpsInstanceInfoDTO.builder()
        .namespace(gitOpsInstance.getInstanceInfo().getNamespace())
        .appIdentifier(gitOpsInstance.getApplicationIdentifier())
        .agentIdentifier(gitOpsInstance.getAgentIdentifier())
        .clusterIdentifier(gitOpsInstance.getClusterIdentifier())
        .podName(gitOpsInstance.getInstanceInfo().getPodName())
        .podId(gitOpsInstance.getInstanceInfo().getPodId())
        .containerList(k8sContainers)
        .build();
  }
}
