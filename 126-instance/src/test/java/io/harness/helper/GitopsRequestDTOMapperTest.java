/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.helper;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.when;

import io.harness.InstancesTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.gitops.beans.GitOpsInstance;
import io.harness.cdng.gitops.beans.GitOpsInstanceRequest;
import io.harness.dtos.InstanceDTO;
import io.harness.dtos.instanceinfo.GitOpsInstanceInfoDTO;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.GITOPS)
public class GitopsRequestDTOMapperTest extends InstancesTestBase {
  String accountId = "accountId";
  String orgId = "orgId";
  String projId = "projectId";
  String clusterId = "clusterId";
  String agentId = "agentId";
  String serviceId = "serviceId";
  String envId = "envId";
  String buildId = "buildId";
  long millis = System.currentTimeMillis();
  String commonId = "-12345";
  String pipelineId = "pipelineId";
  String podId = "podId";
  String containerId = "containerId";
  String namespace = "namespace";

  @Mock private EnvironmentService environmentService;

  @Mock private ServiceEntityService serviceEntityService;

  @InjectMocks GitOpsRequestDTOMapper gitOpsRequestDTOMapper;

  @Test
  @Owner(developers = OwnerRule.MEENA)
  @Category(UnitTests.class)
  public void testToInstanceDTOList() {
    List<GitOpsInstance> gitOpsInstanceRequests = Arrays.asList(getGitopsInstanceList());
    ServiceEntity serviceEntity = ServiceEntity.builder().name(serviceId).id(serviceId).build();
    Environment env = Environment.builder().name(envId).id(envId).build();
    when(serviceEntityService.get(accountId, orgId, projId, serviceId, false)).thenReturn(Optional.of(serviceEntity));
    when(environmentService.get(accountId, orgId, projId, envId, false)).thenReturn(Optional.of(env));
    InstanceDTO instanceObj =
        gitOpsRequestDTOMapper.toInstanceDTOList(accountId, orgId, projId, gitOpsInstanceRequests).get(0);
    GitOpsInstanceInfoDTO gitOpsInstanceInfoDTO = (GitOpsInstanceInfoDTO) instanceObj.getInstanceInfoDTO();
    assertEquals(instanceObj.getAccountIdentifier(), accountId);
    assertEquals(instanceObj.getOrgIdentifier(), orgId);
    assertEquals(gitOpsInstanceInfoDTO.getAgentIdentifier(), agentId);
    assertEquals(gitOpsInstanceInfoDTO.getClusterIdentifier(), clusterId);
  }

  private GitOpsInstance getGitopsInstanceList() {
    return GitOpsInstance.builder()
        .accountIdentifier(accountId)
        .orgIdentifier(orgId)
        .projectIdentifier(projId)
        .clusterIdentifier(clusterId)
        .agentIdentifier(agentId)
        .serviceIdentifier(serviceId)
        .envIdentifier(envId)
        .buildId(buildId)
        .lastDeployedAt(millis)
        .pipelineName(pipelineId)
        .pipelineExecutionId(pipelineId + commonId)
        .instanceInfo(getInstanceInfo())
        .build();
  }

  private GitOpsInstanceRequest.K8sBasicInfo getInstanceInfo() {
    return GitOpsInstanceRequest.K8sBasicInfo.builder()
        .agentIdentifier(agentId)
        .clusterIdentifier(clusterId)
        .podId(podId + commonId)
        .podName(podId)
        .namespace(namespace)
        .containerList(Arrays.asList(getK8sContainers()))
        .build();
  }

  private GitOpsInstanceRequest.K8sContainer getK8sContainers() {
    return GitOpsInstanceRequest.K8sContainer.builder()
        .containerId(containerId + commonId)
        .name(containerId)
        .image(null)
        .build();
  }
}
