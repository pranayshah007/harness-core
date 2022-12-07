/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.instancesync.resources;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.NgManagerTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.gitops.beans.GitOpsInstance;
import io.harness.cdng.gitops.beans.GitOpsInstanceRequest;
import io.harness.helper.GitOpsRequestDTOMapper;
import io.harness.ng.overview.dto.ServicePipelineInfo;
import io.harness.ng.overview.service.CDOverviewDashboardService;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(GITOPS)
public class GitOpsInstanceSyncResourceTest extends NgManagerTestBase {
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
  String pipelineExecutionId = "pipelineExecutionId";
  String planExecutionId = "planExecutionId";
  String status = "SUCCESS";

  @Mock private CDOverviewDashboardService cdOverviewDashboardService;

  @Mock private GitOpsRequestDTOMapper gitOpsRequestDTOMapper;

  @InjectMocks GitOpsInstanceSyncResource gitOpsInstanceSyncResource;

  @Test
  @Owner(developers = OwnerRule.MEENA)
  @Category(UnitTests.class)
  public void testPrepareInstanceSync() {
    when(cdOverviewDashboardService.getLastPipeline(accountId, orgId, projId,
             Stream.of(serviceId).collect(Collectors.toSet()), Stream.of(envId).collect(Collectors.toSet())))
        .thenReturn(getLastPipeline());
    when(cdOverviewDashboardService.getPipelineExecutionDetails(Arrays.asList(pipelineExecutionId)))
        .thenReturn(getPipelineExecutionDetails());
    when(gitOpsRequestDTOMapper.toGitOpsInstanceList(
             accountId, orgId, projId, Arrays.asList(getGitOpsInstanceRequest())))
        .thenReturn(Arrays.asList(getGitOpsInstance()));
    GitOpsInstance gitOpsInstance =
        gitOpsInstanceSyncResource
            .prepareInstanceSync(accountId, orgId, projId, Arrays.asList(getGitOpsInstanceRequest()))
            .get(0);
    assertEquals(gitOpsInstance.getAccountIdentifier(), accountId);
    assertEquals(gitOpsInstance.getOrgIdentifier(), orgId);
    assertEquals(gitOpsInstance.getProjectIdentifier(), projId);
    assertEquals(gitOpsInstance.getServiceIdentifier(), serviceId);
    assertEquals(gitOpsInstance.getEnvIdentifier(), envId);
    assertEquals(gitOpsInstance.getClusterIdentifier(), clusterId);
    assertEquals(gitOpsInstance.getAgentIdentifier(), agentId);
    assertEquals(gitOpsInstance.getLastDeployedAt(), millis);
    assertEquals(gitOpsInstance.getPipelineExecutionId(), planExecutionId);
  }

  @Test
  @Owner(developers = OwnerRule.MEENA)
  @Category(UnitTests.class)
  public void testPrepareInstanceSyncWithoutPipelineDetails() {
    when(cdOverviewDashboardService.getLastPipeline(accountId, orgId, projId,
             Stream.of(serviceId).collect(Collectors.toSet()), Stream.of(envId).collect(Collectors.toSet())))
        .thenReturn(new HashMap<>());
    when(cdOverviewDashboardService.getPipelineExecutionDetails(new ArrayList<>())).thenReturn(new HashMap<>());
    when(gitOpsRequestDTOMapper.toGitOpsInstanceList(
             accountId, orgId, projId, Arrays.asList(getGitOpsInstanceRequest())))
        .thenReturn(Arrays.asList(getGitOpsInstance()));
    assertThat(gitOpsInstanceSyncResource.prepareInstanceSync(
                   accountId, orgId, projId, Arrays.asList(getGitOpsInstanceRequest())))
        .isEmpty();
  }

  private Map<String, String> getLastPipeline() {
    Map<String, String> pipeline = new HashMap<>();
    pipeline.put(serviceId + "-" + envId, pipelineExecutionId);
    return pipeline;
  }

  private Map<String, ServicePipelineInfo> getPipelineExecutionDetails() {
    Map<String, ServicePipelineInfo> pipeline = new HashMap<>();
    ServicePipelineInfo pipelineInfo = ServicePipelineInfo.builder()
                                           .pipelineExecutionId(pipelineExecutionId)
                                           .planExecutionId(planExecutionId)
                                           .status(status)
                                           .build();
    pipeline.put(pipelineExecutionId, pipelineInfo);
    return pipeline;
  }

  private GitOpsInstanceRequest getGitOpsInstanceRequest() {
    return GitOpsInstanceRequest.builder()
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

  private GitOpsInstance getGitOpsInstance() {
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
        .serviceEnvIdentifier(serviceId + "-" + envId)
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
