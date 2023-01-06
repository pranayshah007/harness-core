/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.overview.service;

import static io.harness.rule.OwnerRule.ABHISHEK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.NgManagerTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.models.ActiveServiceInstanceInfoV2;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.overview.dto.ActiveServiceDeploymentsInfo;
import io.harness.ng.overview.dto.ActiveServiceDeploymentsInfo.ActiveServiceDeploymentsInfoBuilder;
import io.harness.ng.overview.dto.InstanceGroupedByServiceList;
import io.harness.ng.overview.dto.ServicePipelineInfo;
import io.harness.rule.Owner;
import io.harness.service.instancedashboardservice.InstanceDashboardServiceImpl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

@OwnedBy(HarnessTeam.CDC)
public class CDOverviewDashboardServiceImplTest extends NgManagerTestBase {
  @InjectMocks private CDOverviewDashboardServiceImpl cdOverviewDashboardService;
  @Mock private InstanceDashboardServiceImpl instanceDashboardService;
  @Mock private ServiceEntityService serviceEntityServiceImpl;

  InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution getSampleInstanceGroupedByPipelineExecution(
      String id, Long lastDeployedAt, int count, String name) {
    return new InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution(count, id, name, lastDeployedAt);
  }

  Map<String,
      Map<String,
          Map<String,
              Pair<Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>,
                  Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>>>>>
  getSampleServiceBuildEnvInfraMap() {
    Map<String,
        Map<String,
            Map<String,
                Pair<Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>,
                    Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>>>>>
        serviceBuildEnvInfraMap = new HashMap<>();

    Map<String,
        Map<String,
            Pair<Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>,
                Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>>>> buildEnvInfraMap =
        new HashMap<>();

    Map<String,
        Pair<Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>,
            Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>>> envInfraMap1 =
        new HashMap<>();
    Map<String,
        Pair<Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>,
            Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>>> envInfraMap2 =
        new HashMap<>();

    Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>> infraPipelineExecutionMap1 =
        new HashMap<>();
    Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>> infraPipelineExecutionMap2 =
        new HashMap<>();
    Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>> clusterPipelineExecutionMap1 =
        new HashMap<>();
    Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>> clusterPipelineExecutionMap2 =
        new HashMap<>();

    infraPipelineExecutionMap1.put("infra1", new ArrayList<>());
    infraPipelineExecutionMap1.get("infra1").add(getSampleInstanceGroupedByPipelineExecution("1", 1l, 1, "a"));
    infraPipelineExecutionMap1.get("infra1").add(getSampleInstanceGroupedByPipelineExecution("1", 2l, 2, "a"));
    infraPipelineExecutionMap1.get("infra1").add(getSampleInstanceGroupedByPipelineExecution("2", 1l, 1, "b"));
    infraPipelineExecutionMap1.put("infra2", new ArrayList<>());
    infraPipelineExecutionMap1.get("infra2").add(getSampleInstanceGroupedByPipelineExecution("1", 1l, 1, "a"));
    infraPipelineExecutionMap2.put("infra2", new ArrayList<>());
    infraPipelineExecutionMap2.get("infra2").add(getSampleInstanceGroupedByPipelineExecution("2", 1l, 1, "b"));

    clusterPipelineExecutionMap1.put("infra1", new ArrayList<>());
    clusterPipelineExecutionMap1.get("infra1").add(getSampleInstanceGroupedByPipelineExecution("1", 1l, 1, "a"));
    clusterPipelineExecutionMap1.get("infra1").add(getSampleInstanceGroupedByPipelineExecution("1", 2l, 2, "a"));
    clusterPipelineExecutionMap1.get("infra1").add(getSampleInstanceGroupedByPipelineExecution("2", 1l, 1, "b"));
    clusterPipelineExecutionMap1.put("infra2", new ArrayList<>());
    clusterPipelineExecutionMap1.get("infra2").add(getSampleInstanceGroupedByPipelineExecution("1", 1l, 1, "a"));
    clusterPipelineExecutionMap2.put("infra2", new ArrayList<>());
    clusterPipelineExecutionMap2.get("infra2").add(getSampleInstanceGroupedByPipelineExecution("2", 1l, 1, "b"));

    envInfraMap1.put("env1", new MutablePair<>(infraPipelineExecutionMap1, clusterPipelineExecutionMap1));
    envInfraMap2.put("env2", new MutablePair<>(infraPipelineExecutionMap2, clusterPipelineExecutionMap2));

    Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>> infraPipelineExecutionMap4 =
        new HashMap<>();
    infraPipelineExecutionMap4.put(
        "infra1", Arrays.asList(getSampleInstanceGroupedByPipelineExecution("1", 1l, 1, "a")));

    Map<String,
        Pair<Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>,
            Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>>> envInfraMap4 =
        new HashMap<>();
    envInfraMap4.put("env1", new MutablePair<>(infraPipelineExecutionMap4, new HashMap<>()));

    buildEnvInfraMap.put("artifact1:1", envInfraMap1);
    buildEnvInfraMap.put("artifact2:2", envInfraMap2);
    buildEnvInfraMap.put("artifact3:1", envInfraMap4);

    serviceBuildEnvInfraMap.put("svc1", buildEnvInfraMap);

    Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>> infraPipelineExecutionMap3 =
        new HashMap<>();

    infraPipelineExecutionMap3.put(
        "infra1", Arrays.asList(getSampleInstanceGroupedByPipelineExecution("1", 1l, 1, "a")));
    Map<String,
        Pair<Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>,
            Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>>> envInfraMap3 =
        new HashMap<>();
    envInfraMap3.put("env1", new MutablePair<>(infraPipelineExecutionMap3, new HashMap<>()));
    Map<String,
        Map<String,
            Pair<Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>,
                Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>>>>
        buildEnvInfraMap2 = new HashMap<>();
    buildEnvInfraMap2.put("artifact11:1", envInfraMap3);

    serviceBuildEnvInfraMap.put("svc2", buildEnvInfraMap2);

    return serviceBuildEnvInfraMap;
  }

  List<InstanceGroupedByServiceList.InstanceGroupedByService> getSampleListInstanceGroupedByService() {
    InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2 instanceGroupedByInfrastructure1 =
        InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2.builder()
            .infraIdentifier("infra1")
            .infraName("infra1")
            .lastDeployedAt(2l)
            .instanceGroupedByPipelineExecutionList(
                Arrays.asList(getSampleInstanceGroupedByPipelineExecution("1", 2l, 3, "a"),
                    getSampleInstanceGroupedByPipelineExecution("2", 1l, 1, "b")))
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2 instanceGroupedByInfrastructure2 =
        InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2.builder()
            .infraIdentifier("infra2")
            .infraName("infra2")
            .lastDeployedAt(1l)
            .instanceGroupedByPipelineExecutionList(
                Arrays.asList(getSampleInstanceGroupedByPipelineExecution("1", 1l, 1, "a")))
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2 instanceGroupedByInfrastructure3 =
        InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2.builder()
            .infraIdentifier("infra2")
            .infraName("infra2")
            .lastDeployedAt(1l)
            .instanceGroupedByPipelineExecutionList(
                Arrays.asList(getSampleInstanceGroupedByPipelineExecution("2", 1l, 1, "b")))
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2 instanceGroupedByInfrastructure4 =
        InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2.builder()
            .infraIdentifier("infra1")
            .infraName("infra1")
            .lastDeployedAt(1l)
            .instanceGroupedByPipelineExecutionList(
                Arrays.asList(getSampleInstanceGroupedByPipelineExecution("1", 1l, 1, "a")))
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2 instanceGroupedByCluster1 =
        InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2.builder()
            .clusterIdentifier("infra1")
            .agentIdentifier("infra1")
            .lastDeployedAt(2l)
            .instanceGroupedByPipelineExecutionList(
                Arrays.asList(getSampleInstanceGroupedByPipelineExecution("1", 2l, 3, "a"),
                    getSampleInstanceGroupedByPipelineExecution("2", 1l, 1, "b")))
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2 instanceGroupedByCluster2 =
        InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2.builder()
            .clusterIdentifier("infra2")
            .agentIdentifier("infra2")
            .lastDeployedAt(1l)
            .instanceGroupedByPipelineExecutionList(
                Arrays.asList(getSampleInstanceGroupedByPipelineExecution("1", 1l, 1, "a")))
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2 instanceGroupedByCluster3 =
        InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2.builder()
            .clusterIdentifier("infra2")
            .agentIdentifier("infra2")
            .lastDeployedAt(1l)
            .instanceGroupedByPipelineExecutionList(
                Arrays.asList(getSampleInstanceGroupedByPipelineExecution("2", 1l, 1, "b")))
            .build();

    InstanceGroupedByServiceList.InstanceGroupedByEnvironmentV2 instanceGroupedByEnvironment1 =
        InstanceGroupedByServiceList.InstanceGroupedByEnvironmentV2.builder()
            .envId("env1")
            .envName("env1")
            .lastDeployedAt(2l)
            .instanceGroupedByInfraList(
                Arrays.asList(instanceGroupedByInfrastructure1, instanceGroupedByInfrastructure2))
            .instanceGroupedByClusterList(Arrays.asList(instanceGroupedByCluster1, instanceGroupedByCluster2))
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByEnvironmentV2 instanceGroupedByEnvironment2 =
        InstanceGroupedByServiceList.InstanceGroupedByEnvironmentV2.builder()
            .envId("env2")
            .envName("env2")
            .lastDeployedAt(1l)
            .instanceGroupedByInfraList(Arrays.asList(instanceGroupedByInfrastructure3))
            .instanceGroupedByClusterList(Arrays.asList(instanceGroupedByCluster3))
            .build();

    InstanceGroupedByServiceList.InstanceGroupedByEnvironmentV2 instanceGroupedByEnvironment3 =
        InstanceGroupedByServiceList.InstanceGroupedByEnvironmentV2.builder()
            .envId("env1")
            .envName("env1")
            .lastDeployedAt(1l)
            .instanceGroupedByInfraList(Arrays.asList(instanceGroupedByInfrastructure4))
            .instanceGroupedByClusterList(new ArrayList<>())
            .build();

    InstanceGroupedByServiceList.InstanceGroupedByArtifactV2 instanceGroupedByArtifact1 =
        InstanceGroupedByServiceList.InstanceGroupedByArtifactV2.builder()
            .artifactVersion("1")
            .artifactPath("artifact1")
            .latest(true)
            .lastDeployedAt(2l)
            .instanceGroupedByEnvironmentList(Arrays.asList(instanceGroupedByEnvironment1))
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByArtifactV2 instanceGroupedByArtifact2 =
        InstanceGroupedByServiceList.InstanceGroupedByArtifactV2.builder()
            .artifactVersion("2")
            .latest(false)
            .lastDeployedAt(1l)
            .artifactPath("artifact2")
            .instanceGroupedByEnvironmentList(Arrays.asList(instanceGroupedByEnvironment2))
            .build();

    InstanceGroupedByServiceList.InstanceGroupedByArtifactV2 instanceGroupedByArtifact3 =
        InstanceGroupedByServiceList.InstanceGroupedByArtifactV2.builder()
            .artifactVersion("1")
            .latest(false)
            .lastDeployedAt(1l)
            .artifactPath("artifact3")
            .instanceGroupedByEnvironmentList(Arrays.asList(instanceGroupedByEnvironment3))
            .build();

    InstanceGroupedByServiceList.InstanceGroupedByService instanceGroupedByService1 =
        InstanceGroupedByServiceList.InstanceGroupedByService.builder()
            .serviceName("svcN1")
            .serviceId("svc1")
            .lastDeployedAt(2l)
            .instanceGroupedByArtifactList(
                Arrays.asList(instanceGroupedByArtifact1, instanceGroupedByArtifact2, instanceGroupedByArtifact3))
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2 instanceGroupedByInfrastructureV2 =
        InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2.builder()
            .infraName("infra1")
            .infraIdentifier("infra1")
            .lastDeployedAt(1l)
            .instanceGroupedByPipelineExecutionList(
                Arrays.asList(getSampleInstanceGroupedByPipelineExecution("1", 1l, 1, "a")))
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByEnvironmentV2 instanceGroupedByEnvironmentV2 =
        InstanceGroupedByServiceList.InstanceGroupedByEnvironmentV2.builder()
            .envId("env1")
            .envName("env1")
            .lastDeployedAt(1l)
            .instanceGroupedByInfraList(Arrays.asList(instanceGroupedByInfrastructureV2))
            .instanceGroupedByClusterList(new ArrayList<>())
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByArtifactV2 instanceGroupedByArtifactV2 =
        InstanceGroupedByServiceList.InstanceGroupedByArtifactV2.builder()
            .artifactPath("artifact11")
            .artifactVersion("1")
            .lastDeployedAt(1l)
            .latest(true)
            .instanceGroupedByEnvironmentList(Arrays.asList(instanceGroupedByEnvironmentV2))
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByService instanceGroupedByService =
        InstanceGroupedByServiceList.InstanceGroupedByService.builder()
            .serviceId("svc2")
            .serviceName("svcN2")
            .lastDeployedAt(1l)
            .instanceGroupedByArtifactList(Arrays.asList(instanceGroupedByArtifactV2))
            .build();
    return Arrays.asList(instanceGroupedByService1, instanceGroupedByService);
  }

  List<ActiveServiceInstanceInfoV2> getSampleListActiveServiceInstanceInfo() {
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfo = new ArrayList<>();
    ActiveServiceInstanceInfoV2 instance1 = new ActiveServiceInstanceInfoV2(
        "svc1", "svcN1", "env1", "env1", "infra1", "infra1", null, null, "1", "a", 1l, "1", "artifact1:1", 1);
    activeServiceInstanceInfo.add(instance1);
    instance1 = new ActiveServiceInstanceInfoV2(
        "svc1", "svcN1", "env1", "env1", "infra1", "infra1", null, null, "1", "a", 2l, "1", "artifact1:1", 2);
    activeServiceInstanceInfo.add(instance1);
    instance1 = new ActiveServiceInstanceInfoV2(
        "svc1", "svcN1", "env1", "env1", "infra1", "infra1", null, null, "2", "b", 1l, "1", "artifact1:1", 1);
    activeServiceInstanceInfo.add(instance1);
    instance1 = new ActiveServiceInstanceInfoV2(
        "svc1", "svcN1", "env1", "env1", "infra2", "infra2", null, null, "1", "a", 1l, "1", "artifact1:1", 1);
    activeServiceInstanceInfo.add(instance1);
    instance1 = new ActiveServiceInstanceInfoV2(
        "svc1", "svcN1", "env2", "env2", "infra2", "infra2", null, null, "2", "b", 1l, "2", "artifact2:2", 1);
    activeServiceInstanceInfo.add(instance1);
    instance1 = new ActiveServiceInstanceInfoV2(
        "svc2", "svcN2", "env1", "env1", "infra1", "infra1", null, null, "1", "a", 1l, "1", "artifact11:1", 1);
    activeServiceInstanceInfo.add(instance1);
    instance1 = new ActiveServiceInstanceInfoV2(
        "svc1", "svcN1", "env1", "env1", "infra1", "infra1", null, null, "1", "a", 1l, "1", "artifact3:1", 1);
    activeServiceInstanceInfo.add(instance1);
    return activeServiceInstanceInfo;
  }

  List<ActiveServiceInstanceInfoV2> getSampleListActiveServiceInstanceInfoGitOps() {
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfo = new ArrayList<>();
    ActiveServiceInstanceInfoV2 instance1 = new ActiveServiceInstanceInfoV2(
        "svc1", "svcN1", "env1", "env1", null, null, "infra1", "infra1", "1", "a", 1l, "1", "artifact1:1", 1);
    activeServiceInstanceInfo.add(instance1);
    instance1 = new ActiveServiceInstanceInfoV2(
        "svc1", "svcN1", "env1", "env1", null, null, "infra1", "infra1", "1", "a", 2l, "1", "artifact1:1", 2);
    activeServiceInstanceInfo.add(instance1);
    instance1 = new ActiveServiceInstanceInfoV2(
        "svc1", "svcN1", "env1", "env1", null, null, "infra1", "infra1", "2", "b", 1l, "1", "artifact1:1", 1);
    activeServiceInstanceInfo.add(instance1);
    instance1 = new ActiveServiceInstanceInfoV2(
        "svc1", "svcN1", "env1", "env1", null, null, "infra2", "infra2", "1", "a", 1l, "1", "artifact1:1", 1);
    activeServiceInstanceInfo.add(instance1);
    instance1 = new ActiveServiceInstanceInfoV2(
        "svc1", "svcN1", "env2", "env2", null, null, "infra2", "infra2", "2", "b", 1l, "2", "artifact2:2", 1);
    activeServiceInstanceInfo.add(instance1);
    return activeServiceInstanceInfo;
  }

  List<ActiveServiceDeploymentsInfo> getSampleActiveServiceDeployments() {
    ActiveServiceDeploymentsInfoBuilder activeServiceDeploymentsInfoBuilder =
        ActiveServiceDeploymentsInfo.builder().serviceId("svc1").serviceName("svcN1");

    List<ActiveServiceDeploymentsInfo> activeServiceDeploymentsInfoList = new ArrayList<>();

    activeServiceDeploymentsInfoList.add(activeServiceDeploymentsInfoBuilder.envId("env1")
                                             .envName("envN1")
                                             .infrastructureIdentifier("infra1")
                                             .infrastructureName("infraN1")
                                             .artifactPath("artifact1")
                                             .tag("1")
                                             .pipelineExecutionId("pipelineExecution1")
                                             .build());
    activeServiceDeploymentsInfoList.add(activeServiceDeploymentsInfoBuilder.envId("env1")
                                             .envName("envN1")
                                             .infrastructureIdentifier("infra2")
                                             .infrastructureName("infraN2")
                                             .artifactPath("artifact1")
                                             .tag("1")
                                             .pipelineExecutionId("pipelineExecution2")
                                             .build());
    activeServiceDeploymentsInfoList.add(activeServiceDeploymentsInfoBuilder.envId("env2")
                                             .envName("envN2")
                                             .infrastructureIdentifier("infra1")
                                             .infrastructureName("infraN1")
                                             .artifactPath("artifact1")
                                             .tag("1")
                                             .pipelineExecutionId("pipelineExecution3")
                                             .build());
    activeServiceDeploymentsInfoList.add(activeServiceDeploymentsInfoBuilder.envId("env2")
                                             .envName("envN2")
                                             .infrastructureIdentifier("infra2")
                                             .infrastructureName("infraN2")
                                             .artifactPath("artifact1")
                                             .tag("2")
                                             .pipelineExecutionId("pipelineExecution4")
                                             .build());
    activeServiceDeploymentsInfoBuilder.serviceId("svc2").serviceName("svcN2");
    activeServiceDeploymentsInfoList.add(activeServiceDeploymentsInfoBuilder.envId("env3")
                                             .envName("envN3")
                                             .infrastructureIdentifier("infra1")
                                             .infrastructureName("infraN1")
                                             .artifactPath("artifact2")
                                             .tag("1")
                                             .pipelineExecutionId("pipelineExecution5")
                                             .build());

    return activeServiceDeploymentsInfoList;
  }

  Map<String, ServicePipelineInfo> getSampleServicePipelineInfo() {
    Map<String, ServicePipelineInfo> servicePipelineInfoMap = new HashMap<>();

    servicePipelineInfoMap.put("pipelineExecution1",
        ServicePipelineInfo.builder()
            .planExecutionId("1")
            .identifier("pipeline1")
            .lastExecutedAt(1l)
            .pipelineExecutionId("pipelineExecution1")
            .build());
    servicePipelineInfoMap.put("pipelineExecution2",
        ServicePipelineInfo.builder()
            .planExecutionId("2")
            .identifier("pipeline2")
            .lastExecutedAt(2l)
            .pipelineExecutionId("pipelineExecution2")
            .build());
    servicePipelineInfoMap.put("pipelineExecution3",
        ServicePipelineInfo.builder()
            .planExecutionId("3")
            .identifier("pipeline3")
            .lastExecutedAt(3l)
            .pipelineExecutionId("pipelineExecution3")
            .build());
    servicePipelineInfoMap.put("pipelineExecution4",
        ServicePipelineInfo.builder()
            .planExecutionId("4")
            .identifier("pipeline4")
            .lastExecutedAt(4l)
            .pipelineExecutionId("pipelineExecution4")
            .build());
    servicePipelineInfoMap.put("pipelineExecution5",
        ServicePipelineInfo.builder()
            .planExecutionId("5")
            .identifier("pipeline5")
            .lastExecutedAt(5l)
            .pipelineExecutionId("pipelineExecution5")
            .build());

    return servicePipelineInfoMap;
  }

  List<InstanceGroupedByServiceList.InstanceGroupedByService>
  getSampleListInstanceGroupedByServiceForActiveDeployments() {
    InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2 instanceGroupedByInfrastructure1 =
        InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2.builder()
            .infraIdentifier("infra1")
            .infraName("infraN1")
            .lastDeployedAt(1l)
            .instanceGroupedByPipelineExecutionList(Arrays.asList(
                new InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution(null, "1", "pipeline1", 1l)))
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2 instanceGroupedByInfrastructure2 =
        InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2.builder()
            .infraIdentifier("infra2")
            .infraName("infraN2")
            .lastDeployedAt(2l)
            .instanceGroupedByPipelineExecutionList(Arrays.asList(
                new InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution(null, "2", "pipeline2", 2l)))
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2 instanceGroupedByInfrastructure3 =
        InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2.builder()
            .infraIdentifier("infra1")
            .infraName("infraN1")
            .lastDeployedAt(3l)
            .instanceGroupedByPipelineExecutionList(Arrays.asList(
                new InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution(null, "3", "pipeline3", 3l)))
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2 instanceGroupedByInfrastructure4 =
        InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2.builder()
            .infraIdentifier("infra2")
            .infraName("infraN2")
            .lastDeployedAt(4l)
            .instanceGroupedByPipelineExecutionList(Arrays.asList(
                new InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution(null, "4", "pipeline4", 4l)))
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2 instanceGroupedByInfrastructure5 =
        InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2.builder()
            .infraIdentifier("infra1")
            .infraName("infraN1")
            .lastDeployedAt(5l)
            .instanceGroupedByPipelineExecutionList(Arrays.asList(
                new InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution(null, "5", "pipeline5", 5l)))
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByEnvironmentV2 instanceGroupedByEnvironment1 =
        InstanceGroupedByServiceList.InstanceGroupedByEnvironmentV2.builder()
            .envId("env1")
            .envName("envN1")
            .lastDeployedAt(2l)
            .instanceGroupedByInfraList(
                Arrays.asList(instanceGroupedByInfrastructure2, instanceGroupedByInfrastructure1))
            .instanceGroupedByClusterList(new ArrayList<>())
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByEnvironmentV2 instanceGroupedByEnvironment2 =
        InstanceGroupedByServiceList.InstanceGroupedByEnvironmentV2.builder()
            .envId("env2")
            .envName("envN2")
            .lastDeployedAt(3l)
            .instanceGroupedByInfraList(Arrays.asList(instanceGroupedByInfrastructure3))
            .instanceGroupedByClusterList(new ArrayList<>())
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByEnvironmentV2 instanceGroupedByEnvironment3 =
        InstanceGroupedByServiceList.InstanceGroupedByEnvironmentV2.builder()
            .envId("env2")
            .envName("envN2")
            .lastDeployedAt(4l)
            .instanceGroupedByInfraList(Arrays.asList(instanceGroupedByInfrastructure4))
            .instanceGroupedByClusterList(new ArrayList<>())
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByEnvironmentV2 instanceGroupedByEnvironment4 =
        InstanceGroupedByServiceList.InstanceGroupedByEnvironmentV2.builder()
            .envId("env3")
            .envName("envN3")
            .lastDeployedAt(5l)
            .instanceGroupedByInfraList(Arrays.asList(instanceGroupedByInfrastructure5))
            .instanceGroupedByClusterList(new ArrayList<>())
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByArtifactV2 instanceGroupedByArtifact1 =
        InstanceGroupedByServiceList.InstanceGroupedByArtifactV2.builder()
            .artifactVersion("1")
            .artifactPath("artifact1")
            .lastDeployedAt(3l)
            .instanceGroupedByEnvironmentList(
                Arrays.asList(instanceGroupedByEnvironment2, instanceGroupedByEnvironment1))
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByArtifactV2 instanceGroupedByArtifact2 =
        InstanceGroupedByServiceList.InstanceGroupedByArtifactV2.builder()
            .artifactVersion("2")
            .artifactPath("artifact1")
            .latest(true)
            .lastDeployedAt(4l)
            .instanceGroupedByEnvironmentList(Arrays.asList(instanceGroupedByEnvironment3))
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByArtifactV2 instanceGroupedByArtifact3 =
        InstanceGroupedByServiceList.InstanceGroupedByArtifactV2.builder()
            .artifactVersion("1")
            .artifactPath("artifact2")
            .latest(true)
            .lastDeployedAt(5l)
            .instanceGroupedByEnvironmentList(Arrays.asList(instanceGroupedByEnvironment4))
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByService instanceGroupedByService1 =
        InstanceGroupedByServiceList.InstanceGroupedByService.builder()
            .serviceId("svc1")
            .serviceName("svcN1")
            .lastDeployedAt(4l)
            .instanceGroupedByArtifactList(Arrays.asList(instanceGroupedByArtifact2, instanceGroupedByArtifact1))
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByService instanceGroupedByService2 =
        InstanceGroupedByServiceList.InstanceGroupedByService.builder()
            .serviceId("svc2")
            .serviceName("svcN2")
            .lastDeployedAt(5l)
            .instanceGroupedByArtifactList(Arrays.asList(instanceGroupedByArtifact3))
            .build();
    return Arrays.asList(instanceGroupedByService2, instanceGroupedByService1);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_groupByServices() {
    Map<String,
        Map<String,
            Map<String,
                Pair<Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>,
                    Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>>>>>
        serviceBuildEnvInfraMap = getSampleServiceBuildEnvInfraMap();
    Map<String, String> serviceIdToServiceNameMap = new HashMap<>();
    Map<String, String> envIdToEnvNameMap = new HashMap<>();
    Map<String, String> infraIdToInfraNameMap = new HashMap<>();
    Map<String, String> serviceIdToLatestBuildMap = new HashMap<>();

    serviceIdToLatestBuildMap.put("svc1", "artifact1:1");
    serviceIdToLatestBuildMap.put("svc2", "artifact11:1");

    serviceIdToServiceNameMap.put("svc1", "svcN1");
    serviceIdToServiceNameMap.put("svc2", "svcN2");

    envIdToEnvNameMap.put("env1", "env1");
    envIdToEnvNameMap.put("env2", "env2");

    infraIdToInfraNameMap.put("infra1", "infra1");
    infraIdToInfraNameMap.put("infra2", "infra2");

    List<InstanceGroupedByServiceList.InstanceGroupedByService> instanceGroupedByServices =
        getSampleListInstanceGroupedByService();

    List<InstanceGroupedByServiceList.InstanceGroupedByService> instanceGroupedByServices1 =
        cdOverviewDashboardService.groupedByServices(serviceBuildEnvInfraMap, envIdToEnvNameMap, infraIdToInfraNameMap,
            serviceIdToServiceNameMap, infraIdToInfraNameMap, serviceIdToLatestBuildMap);

    assertThat(instanceGroupedByServices1).isEqualTo(instanceGroupedByServices);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_getInstanceGroupedByServiceListHelper() {
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfoV2List = getSampleListActiveServiceInstanceInfo();
    activeServiceInstanceInfoV2List.addAll(getSampleListActiveServiceInstanceInfoGitOps());
    InstanceGroupedByServiceList instanceGroupedByServiceList =
        cdOverviewDashboardService.getInstanceGroupedByServiceListHelper(activeServiceInstanceInfoV2List);
    assertThat(instanceGroupedByServiceList)
        .isEqualTo(InstanceGroupedByServiceList.builder()
                       .instanceGroupedByServiceList(getSampleListInstanceGroupedByService())
                       .build());
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_getInstanceGroupedByServiceList() {
    Mockito
        .when(instanceDashboardService.getActiveServiceInstanceInfo(
            "accountId", "orgId", "projectId", null, null, null, false))
        .thenReturn(getSampleListActiveServiceInstanceInfo());
    Mockito
        .when(instanceDashboardService.getActiveServiceInstanceInfo(
            "accountId", "orgId", "projectId", null, null, null, true))
        .thenReturn(getSampleListActiveServiceInstanceInfoGitOps());
    InstanceGroupedByServiceList instanceGroupedByServiceList =
        InstanceGroupedByServiceList.builder()
            .instanceGroupedByServiceList(getSampleListInstanceGroupedByService())
            .build();
    assertThat(instanceGroupedByServiceList)
        .isEqualTo(cdOverviewDashboardService.getInstanceGroupedByServiceList(
            "accountId", "orgId", "projectId", null, null, null));
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_getActiveServiceDeploymentsListHelper() {
    List<ActiveServiceDeploymentsInfo> activeServiceDeploymentsInfoList = getSampleActiveServiceDeployments();
    CDOverviewDashboardServiceImpl cdOverviewDashboardService1 = spy(cdOverviewDashboardService);
    doReturn(activeServiceDeploymentsInfoList)
        .when(cdOverviewDashboardService1)
        .getActiveServiceDeploymentsInfo(anyString());
    doReturn(getSampleServicePipelineInfo()).when(cdOverviewDashboardService1).getPipelineExecutionDetails(anyList());
    InstanceGroupedByServiceList instanceGroupedByServiceList1 =
        InstanceGroupedByServiceList.builder()
            .instanceGroupedByServiceList(getSampleListInstanceGroupedByServiceForActiveDeployments())
            .build();
    InstanceGroupedByServiceList instanceGroupedByServiceList2 =
        cdOverviewDashboardService1.getActiveServiceDeploymentsListHelper(
            "account", "org", "project", "service", "build", "env");
    assertThat(instanceGroupedByServiceList1).isEqualTo(instanceGroupedByServiceList2);
    verify(cdOverviewDashboardService1).getActiveServiceDeploymentsInfo(anyString());
    verify(cdOverviewDashboardService1).getPipelineExecutionDetails(anyList());
    verify(cdOverviewDashboardService1).getInstanceGroupedByServiceListHelper(anyList());
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_getActiveServiceDeploymentsList() {
    CDOverviewDashboardServiceImpl cdOverviewDashboardService1 = spy(cdOverviewDashboardService);
    InstanceGroupedByServiceList.InstanceGroupedByService instanceGroupedByService =
        getSampleListInstanceGroupedByServiceForActiveDeployments().get(0);

    doReturn(InstanceGroupedByServiceList.builder()
                 .instanceGroupedByServiceList(Arrays.asList(instanceGroupedByService))
                 .build())
        .when(cdOverviewDashboardService1)
        .getActiveServiceDeploymentsListHelper("account", "org", "project", "service", null, null);

    InstanceGroupedByServiceList.InstanceGroupedByService instanceGroupedByService1 =
        cdOverviewDashboardService1.getActiveServiceDeploymentsList("account", "org", "project", "service");

    assertThat(instanceGroupedByService).isEqualTo(instanceGroupedByService1);
    verify(cdOverviewDashboardService1)
        .getActiveServiceDeploymentsListHelper("account", "org", "project", "service", null, null);
  }
  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_getActiveServiceDeploymentsList_EmptyCase() {
    CDOverviewDashboardServiceImpl cdOverviewDashboardService1 = spy(cdOverviewDashboardService);
    InstanceGroupedByServiceList.InstanceGroupedByService instanceGroupedByService =
        InstanceGroupedByServiceList.InstanceGroupedByService.builder()
            .instanceGroupedByArtifactList(new ArrayList<>())
            .build();

    doReturn(InstanceGroupedByServiceList.builder().instanceGroupedByServiceList(new ArrayList<>()).build())
        .when(cdOverviewDashboardService1)
        .getActiveServiceDeploymentsListHelper("account", "org", "project", "service", null, null);

    InstanceGroupedByServiceList.InstanceGroupedByService instanceGroupedByService1 =
        cdOverviewDashboardService1.getActiveServiceDeploymentsList("account", "org", "project", "service");

    assertThat(instanceGroupedByService).isEqualTo(instanceGroupedByService1);
    verify(cdOverviewDashboardService1)
        .getActiveServiceDeploymentsListHelper("account", "org", "project", "service", null, null);
  }
  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_getInstanceGroupedByArtifactList_NonGitOps() {
    CDOverviewDashboardServiceImpl cdOverviewDashboardService1 = spy(cdOverviewDashboardService);
    InstanceGroupedByServiceList.InstanceGroupedByService instanceGroupedByService =
        getSampleListInstanceGroupedByServiceForActiveDeployments().get(0);
    when(serviceEntityServiceImpl.getService("account", "org", "project", "service"))
        .thenReturn(Optional.of(ServiceEntity.builder().gitOpsEnabled(false).build()));
    doReturn(InstanceGroupedByServiceList.builder()
                 .instanceGroupedByServiceList(Arrays.asList(instanceGroupedByService))
                 .build())
        .when(cdOverviewDashboardService1)
        .getInstanceGroupedByServiceListHelper(anyList());
    assertThat(instanceGroupedByService)
        .isEqualTo(
            cdOverviewDashboardService1.getInstanceGroupedByArtifactList("account", "org", "project", "service"));
    verify(instanceDashboardService)
        .getActiveServiceInstanceInfo("account", "org", "project", null, "service", null, false);
    verify(cdOverviewDashboardService1).getInstanceGroupedByServiceListHelper(anyList());
  }
  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_getInstanceGroupedByArtifactList_GitOps() {
    CDOverviewDashboardServiceImpl cdOverviewDashboardService1 = spy(cdOverviewDashboardService);
    InstanceGroupedByServiceList.InstanceGroupedByService instanceGroupedByService =
        getSampleListInstanceGroupedByServiceForActiveDeployments().get(0);
    when(serviceEntityServiceImpl.getService("account", "org", "project", "service"))
        .thenReturn(Optional.of(ServiceEntity.builder().gitOpsEnabled(true).build()));
    doReturn(InstanceGroupedByServiceList.builder()
                 .instanceGroupedByServiceList(Arrays.asList(instanceGroupedByService))
                 .build())
        .when(cdOverviewDashboardService1)
        .getInstanceGroupedByServiceListHelper(anyList());
    assertThat(instanceGroupedByService)
        .isEqualTo(
            cdOverviewDashboardService1.getInstanceGroupedByArtifactList("account", "org", "project", "service"));
    verify(instanceDashboardService)
        .getActiveServiceInstanceInfo("account", "org", "project", null, "service", null, true);
    verify(cdOverviewDashboardService1).getInstanceGroupedByServiceListHelper(anyList());
  }
}
