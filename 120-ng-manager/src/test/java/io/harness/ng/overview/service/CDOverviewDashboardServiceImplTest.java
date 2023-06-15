/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.overview.service;

import static io.harness.ng.core.template.TemplateListType.STABLE_TEMPLATE_TYPE;
import static io.harness.rule.OwnerRule.ABHISHEK;
import static io.harness.rule.OwnerRule.SOURABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.NgManagerTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity;
import io.harness.cdng.envGroup.services.EnvironmentGroupServiceImpl;
import io.harness.exception.InvalidRequestException;
import io.harness.models.ActiveServiceInstanceInfoV2;
import io.harness.models.ActiveServiceInstanceInfoWithEnvType;
import io.harness.models.ArtifactDeploymentDetailModel;
import io.harness.models.EnvironmentInstanceCountModel;
import io.harness.models.InstanceDetailGroupedByPipelineExecutionList;
import io.harness.models.InstanceDetailsDTO;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.environment.services.impl.EnvironmentServiceImpl;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.entity.ServiceSequence;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.service.services.ServiceSequenceService;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.ng.core.template.TemplateMetadataSummaryResponseDTO;
import io.harness.ng.overview.dto.ActiveServiceDeploymentsInfo;
import io.harness.ng.overview.dto.ActiveServiceDeploymentsInfo.ActiveServiceDeploymentsInfoBuilder;
import io.harness.ng.overview.dto.ArtifactDeploymentDetail;
import io.harness.ng.overview.dto.ArtifactInstanceDetails;
import io.harness.ng.overview.dto.EnvironmentGroupInstanceDetails;
import io.harness.ng.overview.dto.IconDTO;
import io.harness.ng.overview.dto.InstanceGroupedByEnvironmentList;
import io.harness.ng.overview.dto.InstanceGroupedByServiceList;
import io.harness.ng.overview.dto.InstanceGroupedOnArtifactList;
import io.harness.ng.overview.dto.OpenTaskDetails;
import io.harness.ng.overview.dto.PipelineExecutionCountInfo;
import io.harness.ng.overview.dto.ServiceArtifactExecutionDetail;
import io.harness.ng.overview.dto.ServiceArtifactExecutionDetail.ServiceArtifactExecutionDetailBuilder;
import io.harness.ng.overview.dto.ServicePipelineInfo;
import io.harness.ng.overview.dto.ServicePipelineWithRevertInfo;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.rule.Owner;
import io.harness.service.instancedashboardservice.InstanceDashboardServiceImpl;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.template.resources.beans.TemplateFilterPropertiesDTO;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.springframework.data.domain.Page;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.CDC)
public class CDOverviewDashboardServiceImplTest extends NgManagerTestBase {
  @InjectMocks private CDOverviewDashboardServiceImpl cdOverviewDashboardService;
  @Mock private InstanceDashboardServiceImpl instanceDashboardService;
  @Mock private ServiceEntityService serviceEntityServiceImpl;
  @Mock private EnvironmentServiceImpl environmentService;
  @Mock private TemplateResourceClient templateResourceClient;
  @Mock private EnvironmentGroupServiceImpl environmentGroupService;
  @Mock private ServiceSequenceService serviceSequenceService;

  private final String ENVIRONMENT_1 = "env1";
  private final String ENVIRONMENT_2 = "env2";
  private final String ENVIRONMENT_3 = "env3";
  private final String ENVIRONMENT_GROUP_1 = "group1";
  private final String ENVIRONMENT_GROUP_2 = "group2";
  private final String ENVIRONMENT_NAME_1 = "envN1";
  private final String ENVIRONMENT_NAME_2 = "envN2";
  private final String ENVIRONMENT_GROUP_NAME_1 = "envgroupN1";
  private final String ENVIRONMENT_GROUP_NAME_2 = "envgroupN2";
  private final String INFRASTRUCTURE_1 = "infra1";
  private final String DISPLAY_NAME_1 = "display1:1";
  private final String DISPLAY_NAME_2 = "display2:2";
  private final String PLAN_EXECUTION_1 = "planexec:1";
  private final String PIPELINE_EXECUTION_SUMMARY_CD_ID_1 = "sumarryid1";
  private final String PIPELINE_EXECUTION_SUMMARY_CD_ID_2 = "sumarryid2";
  private final String PLAN_EXECUTION_2 = "planexec:2";
  private final String ACCOUNT_ID = "accountID";
  private final String ORG_ID = "orgId";
  private final String PROJECT_ID = "projectId";
  private final String SERVICE_ID = "serviceId";
  private final String SERVICE_NAME = "serviceName";
  private final String SERVICE_ID_2 = "org.serviceId2";
  private final String SERVICE_NAME_2 = "serviceName2";
  private final String TAG_1 = "1";
  private final String TAG_2 = "2";
  private final String ARTIFACT_PATH_1 = "display1";
  private final String ARTIFACT_PATH_2 = "display2";
  private final String SUCCESS = "SUCCESS";
  private final String FAILED = "FAILED";
  private static final String PIPELINE_1 = "pipeline1";
  private static final String PIPELINE_2 = "pipeline2";
  private static final String PIPELINE_EXECUTION_1 = "pipelineExecution1";
  private static final String PIPELINE_EXECUTION_2 = "pipelineExecution2";
  private static final String FAILURE_MESSAGE_1 = "fail1";
  private static final String FAILURE_MESSAGE_2 = "fail2";

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
    infraPipelineExecutionMap2.get("infra2").add(getSampleInstanceGroupedByPipelineExecution("2", 0l, 1, "b"));

    clusterPipelineExecutionMap1.put("infra1", new ArrayList<>());
    clusterPipelineExecutionMap1.get("infra1").add(getSampleInstanceGroupedByPipelineExecution("1", 1l, 1, "a"));
    clusterPipelineExecutionMap1.get("infra1").add(getSampleInstanceGroupedByPipelineExecution("1", 2l, 2, "a"));
    clusterPipelineExecutionMap1.get("infra1").add(getSampleInstanceGroupedByPipelineExecution("2", 1l, 1, "b"));
    clusterPipelineExecutionMap1.put("infra2", new ArrayList<>());
    clusterPipelineExecutionMap1.get("infra2").add(getSampleInstanceGroupedByPipelineExecution("1", 1l, 1, "a"));
    clusterPipelineExecutionMap2.put("infra2", new ArrayList<>());
    clusterPipelineExecutionMap2.get("infra2").add(getSampleInstanceGroupedByPipelineExecution("2", 0l, 1, "b"));

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
    buildEnvInfraMap.put(null, envInfraMap4);

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
            .lastDeployedAt(0l)
            .instanceGroupedByPipelineExecutionList(
                Arrays.asList(getSampleInstanceGroupedByPipelineExecution("2", 0l, 1, "b")))
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
            .lastDeployedAt(0l)
            .instanceGroupedByPipelineExecutionList(
                Arrays.asList(getSampleInstanceGroupedByPipelineExecution("2", 0l, 1, "b")))
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
            .lastDeployedAt(0l)
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
            .lastDeployedAt(0l)
            .artifactPath("artifact2")
            .instanceGroupedByEnvironmentList(Arrays.asList(instanceGroupedByEnvironment2))
            .build();

    InstanceGroupedByServiceList.InstanceGroupedByArtifactV2 instanceGroupedByArtifact3 =
        InstanceGroupedByServiceList.InstanceGroupedByArtifactV2.builder()
            .artifactVersion(null)
            .latest(false)
            .lastDeployedAt(1l)
            .artifactPath(null)
            .instanceGroupedByEnvironmentList(Arrays.asList(instanceGroupedByEnvironment3))
            .build();

    InstanceGroupedByServiceList.InstanceGroupedByService instanceGroupedByService1 =
        InstanceGroupedByServiceList.InstanceGroupedByService.builder()
            .serviceName("svcN1")
            .serviceId("svc1")
            .lastDeployedAt(2l)
            .instanceGroupedByArtifactList(
                Arrays.asList(instanceGroupedByArtifact1, instanceGroupedByArtifact3, instanceGroupedByArtifact2))
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
        "svc1", "svcN1", "env2", "env2", "infra2", "infra2", null, null, "2", "b", 0l, "2", "artifact2:2", 1);
    activeServiceInstanceInfo.add(instance1);
    instance1 = new ActiveServiceInstanceInfoV2(
        "svc2", "svcN2", "env1", "env1", "infra1", "infra1", null, null, "1", "a", 1l, "1", "artifact11:1", 1);
    activeServiceInstanceInfo.add(instance1);
    instance1 = new ActiveServiceInstanceInfoV2(
        "svc1", "svcN1", "env1", "env1", "infra1", "infra1", null, null, "1", "a", 1l, null, null, 1);
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
        "svc1", "svcN1", "env2", "env2", null, null, "infra2", "infra2", "2", "b", 0l, "2", "artifact2:2", 1);
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

  private List<EnvironmentInstanceCountModel> getEnvironmentInstanceCountModelList() {
    List<EnvironmentInstanceCountModel> environmentInstanceCountModelList = new ArrayList<>();
    environmentInstanceCountModelList.add(new EnvironmentInstanceCountModel(ENVIRONMENT_1, 2));
    environmentInstanceCountModelList.add(new EnvironmentInstanceCountModel(ENVIRONMENT_2, 1));
    return environmentInstanceCountModelList;
  }

  private List<Environment> getEnvironmentList() {
    List<Environment> environmentList = new ArrayList<>();
    environmentList.add(Environment.builder()
                            .name(ENVIRONMENT_NAME_1)
                            .accountId(ACCOUNT_ID)
                            .orgIdentifier(ORG_ID)
                            .projectIdentifier(PROJECT_ID)
                            .type(EnvironmentType.PreProduction)
                            .identifier(ENVIRONMENT_1)
                            .build());
    environmentList.add(Environment.builder()
                            .name(ENVIRONMENT_NAME_2)
                            .accountId(ACCOUNT_ID)
                            .orgIdentifier(ORG_ID)
                            .projectIdentifier(PROJECT_ID)
                            .type(EnvironmentType.Production)

                            .identifier(ENVIRONMENT_2)
                            .build());
    return environmentList;
  }

  private List<ArtifactDeploymentDetailModel> getArtifactDeploymentDetailModelList() {
    List<ArtifactDeploymentDetailModel> artifactDeploymentDetailModels = new ArrayList<>();
    artifactDeploymentDetailModels.add(
        new ArtifactDeploymentDetailModel(ENVIRONMENT_1, DISPLAY_NAME_1, 1l, PLAN_EXECUTION_1, null));
    artifactDeploymentDetailModels.add(
        new ArtifactDeploymentDetailModel(ENVIRONMENT_2, DISPLAY_NAME_2, 2l, PLAN_EXECUTION_2, null));
    return artifactDeploymentDetailModels;
  }

  private List<ArtifactDeploymentDetailModel> getArtifactDeploymentDetailModelList_ArtifactCard() {
    List<ArtifactDeploymentDetailModel> artifactDeploymentDetailModels = new ArrayList<>();
    artifactDeploymentDetailModels.add(
        new ArtifactDeploymentDetailModel(ENVIRONMENT_1, DISPLAY_NAME_1, 1l, PLAN_EXECUTION_1, null));
    artifactDeploymentDetailModels.add(
        new ArtifactDeploymentDetailModel(ENVIRONMENT_2, DISPLAY_NAME_2, 2l, PLAN_EXECUTION_2, null));
    return artifactDeploymentDetailModels;
  }

  private List<EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail> getEnvironmentGroupInstanceDetailList() {
    List<EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail> environmentInstanceDetails = new ArrayList<>();
    environmentInstanceDetails.add(
        EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail.builder()
            .id(ENVIRONMENT_GROUP_1)
            .name(ENVIRONMENT_GROUP_NAME_1)
            .isEnvGroup(true)
            .isDrift(false)
            .isRollback(true)
            .isRevert(false)
            .environmentTypes(Arrays.asList(EnvironmentType.PreProduction))
            .count(2)
            .artifactDeploymentDetails(Collections.singletonList(ArtifactDeploymentDetail.builder()
                                                                     .envName(ENVIRONMENT_NAME_1)
                                                                     .envId(ENVIRONMENT_1)
                                                                     .lastPipelineExecutionId(PLAN_EXECUTION_1)
                                                                     .artifact(DISPLAY_NAME_1)
                                                                     .lastDeployedAt(1l)
                                                                     .build()))
            .build());
    environmentInstanceDetails.add(
        EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail.builder()
            .id(ENVIRONMENT_GROUP_2)
            .name(ENVIRONMENT_GROUP_NAME_2)
            .isEnvGroup(true)
            .isDrift(true)
            .isRollback(false)
            .isRevert(true)
            .environmentTypes(Arrays.asList(EnvironmentType.Production, EnvironmentType.PreProduction))
            .count(3)
            .artifactDeploymentDetails(Arrays.asList(ArtifactDeploymentDetail.builder()
                                                         .envId(ENVIRONMENT_2)
                                                         .envName(ENVIRONMENT_NAME_2)
                                                         .lastPipelineExecutionId(PLAN_EXECUTION_2)
                                                         .artifact(DISPLAY_NAME_2)
                                                         .lastDeployedAt(2l)
                                                         .build(),
                ArtifactDeploymentDetail.builder()
                    .envName(ENVIRONMENT_NAME_1)
                    .envId(ENVIRONMENT_1)
                    .lastPipelineExecutionId(PLAN_EXECUTION_1)
                    .artifact(DISPLAY_NAME_1)
                    .lastDeployedAt(1l)
                    .build()))
            .build());
    return environmentInstanceDetails;
  }

  private List<ArtifactInstanceDetails.ArtifactInstanceDetail> getArtifactInstanceDetailList() {
    List<ArtifactInstanceDetails.ArtifactInstanceDetail> artifactInstanceDetails = new ArrayList<>();
    List<EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail> environmentInstanceDetails1 =
        new ArrayList<>();
    environmentInstanceDetails1.add(
        EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail.builder()
            .id(ENVIRONMENT_GROUP_1)
            .name(ENVIRONMENT_GROUP_NAME_1)
            .isEnvGroup(true)
            .isDrift(false)
            .environmentTypes(Arrays.asList(EnvironmentType.PreProduction))
            .artifactDeploymentDetails(Collections.singletonList(ArtifactDeploymentDetail.builder()
                                                                     .envName(ENVIRONMENT_NAME_1)
                                                                     .envId(ENVIRONMENT_1)
                                                                     .lastPipelineExecutionId(PLAN_EXECUTION_1)
                                                                     .artifact(DISPLAY_NAME_1)
                                                                     .lastDeployedAt(1l)
                                                                     .build()))
            .build());
    environmentInstanceDetails1.add(
        EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail.builder()
            .id(ENVIRONMENT_GROUP_2)
            .name(ENVIRONMENT_GROUP_NAME_2)
            .isEnvGroup(true)
            .isDrift(true)
            .environmentTypes(Arrays.asList(EnvironmentType.PreProduction, EnvironmentType.Production))
            .artifactDeploymentDetails(Arrays.asList(ArtifactDeploymentDetail.builder()
                                                         .envId(ENVIRONMENT_2)
                                                         .envName(ENVIRONMENT_NAME_2)
                                                         .lastPipelineExecutionId(PLAN_EXECUTION_2)
                                                         .artifact(DISPLAY_NAME_2)
                                                         .lastDeployedAt(2l)
                                                         .build(),
                ArtifactDeploymentDetail.builder()
                    .envName(ENVIRONMENT_NAME_1)
                    .envId(ENVIRONMENT_1)
                    .lastPipelineExecutionId(PLAN_EXECUTION_1)
                    .artifact(DISPLAY_NAME_1)
                    .lastDeployedAt(1l)
                    .build()))
            .build());

    List<EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail> environmentInstanceDetails2 =
        new ArrayList<>();
    environmentInstanceDetails2.add(
        EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail.builder()
            .id(ENVIRONMENT_GROUP_2)
            .name(ENVIRONMENT_GROUP_NAME_2)
            .isEnvGroup(true)
            .isDrift(true)
            .environmentTypes(Arrays.asList(EnvironmentType.PreProduction, EnvironmentType.Production))
            .artifactDeploymentDetails(Arrays.asList(ArtifactDeploymentDetail.builder()
                                                         .envId(ENVIRONMENT_2)
                                                         .envName(ENVIRONMENT_NAME_2)
                                                         .lastPipelineExecutionId(PLAN_EXECUTION_2)
                                                         .artifact(DISPLAY_NAME_2)
                                                         .lastDeployedAt(2l)
                                                         .build(),
                ArtifactDeploymentDetail.builder()
                    .envName(ENVIRONMENT_NAME_1)
                    .envId(ENVIRONMENT_1)
                    .lastPipelineExecutionId(PLAN_EXECUTION_1)
                    .artifact(DISPLAY_NAME_1)
                    .lastDeployedAt(1l)
                    .build()))
            .build());

    artifactInstanceDetails.add(
        ArtifactInstanceDetails.ArtifactInstanceDetail.builder()
            .artifact(DISPLAY_NAME_1)
            .environmentGroupInstanceDetails(EnvironmentGroupInstanceDetails.builder()
                                                 .environmentGroupInstanceDetails(environmentInstanceDetails1)
                                                 .build())
            .build());
    artifactInstanceDetails.add(
        ArtifactInstanceDetails.ArtifactInstanceDetail.builder()
            .artifact(DISPLAY_NAME_2)
            .environmentGroupInstanceDetails(EnvironmentGroupInstanceDetails.builder()
                                                 .environmentGroupInstanceDetails(environmentInstanceDetails2)
                                                 .build())
            .build());
    return artifactInstanceDetails;
  }

  private void mockServiceEntityForNonGitOps() {
    when(serviceEntityServiceImpl.getService(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID))
        .thenReturn(Optional.of(ServiceEntity.builder().gitOpsEnabled(false).build()));
  }

  private void mockServiceEntityForGitOps() {
    when(serviceEntityServiceImpl.getService(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID))
        .thenReturn(Optional.of(ServiceEntity.builder().gitOpsEnabled(true).build()));
  }

  private void verifyServiceEntityCall() {
    verify(serviceEntityServiceImpl).getService(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID);
  }

  private List<ServiceArtifactExecutionDetail> getServiceArtifactExecutionDetailList() {
    List<ServiceArtifactExecutionDetail> serviceArtifactExecutionDetailList = new ArrayList<>();
    ServiceArtifactExecutionDetailBuilder serviceArtifactExecutionDetailBuilder =
        ServiceArtifactExecutionDetail.builder()
            .accountId(ACCOUNT_ID)
            .orgId(ORG_ID)
            .projectId(PROJECT_ID)
            .serviceRef(SERVICE_ID)
            .serviceName(SERVICE_NAME)
            .artifactTag(TAG_1)
            .artifactPath(ARTIFACT_PATH_1)
            .artifactDisplayName(DISPLAY_NAME_1)
            .serviceStartTime(7l)
            .pipelineExecutionSummaryCDId("7");
    serviceArtifactExecutionDetailList.add(serviceArtifactExecutionDetailBuilder.build());
    serviceArtifactExecutionDetailList.add(serviceArtifactExecutionDetailBuilder.artifactDisplayName(null)
                                               .serviceStartTime(6l)
                                               .pipelineExecutionSummaryCDId("6")
                                               .build());
    serviceArtifactExecutionDetailList.add(
        serviceArtifactExecutionDetailBuilder.serviceStartTime(5l).pipelineExecutionSummaryCDId("6").build());
    serviceArtifactExecutionDetailList.add(serviceArtifactExecutionDetailBuilder.artifactDisplayName(DISPLAY_NAME_2)
                                               .artifactTag(TAG_2)
                                               .artifactPath(ARTIFACT_PATH_2)
                                               .serviceStartTime(4l)
                                               .pipelineExecutionSummaryCDId("6")
                                               .build());
    serviceArtifactExecutionDetailList.add(serviceArtifactExecutionDetailBuilder.serviceRef(SERVICE_ID_2)
                                               .serviceName(SERVICE_NAME_2)
                                               .artifactDisplayName(DISPLAY_NAME_2)
                                               .artifactTag(TAG_2)
                                               .artifactPath(ARTIFACT_PATH_2)
                                               .serviceStartTime(3l)
                                               .pipelineExecutionSummaryCDId("4")
                                               .build());
    return serviceArtifactExecutionDetailList;
  }

  private Map<String, String> getExecutionStatusMap() {
    Map<String, String> statusMap = new HashMap<>();
    statusMap.put("7", SUCCESS);
    statusMap.put("6", FAILED);
    statusMap.put("4", FAILED);
    return statusMap;
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
            ACCOUNT_ID, ORG_ID, PROJECT_ID, null, null, null, false))
        .thenReturn(getSampleListActiveServiceInstanceInfo());
    Mockito
        .when(instanceDashboardService.getActiveServiceInstanceInfo(
            ACCOUNT_ID, ORG_ID, PROJECT_ID, null, null, null, true))
        .thenReturn(getSampleListActiveServiceInstanceInfoGitOps());
    InstanceGroupedByServiceList instanceGroupedByServiceList =
        InstanceGroupedByServiceList.builder()
            .instanceGroupedByServiceList(getSampleListInstanceGroupedByService())
            .build();
    assertThat(instanceGroupedByServiceList)
        .isEqualTo(cdOverviewDashboardService.getInstanceGroupedByServiceList(
            ACCOUNT_ID, ORG_ID, PROJECT_ID, null, null, null));
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
            ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, "build", "env");
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
        .getActiveServiceDeploymentsListHelper(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, null, null);

    InstanceGroupedByServiceList.InstanceGroupedByService instanceGroupedByService1 =
        cdOverviewDashboardService1.getActiveServiceDeploymentsList(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID);

    assertThat(instanceGroupedByService).isEqualTo(instanceGroupedByService1);
    verify(cdOverviewDashboardService1)
        .getActiveServiceDeploymentsListHelper(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, null, null);
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
        .getActiveServiceDeploymentsListHelper(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, null, null);

    InstanceGroupedByServiceList.InstanceGroupedByService instanceGroupedByService1 =
        cdOverviewDashboardService1.getActiveServiceDeploymentsList(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID);

    assertThat(instanceGroupedByService).isEqualTo(instanceGroupedByService1);
    verify(cdOverviewDashboardService1)
        .getActiveServiceDeploymentsListHelper(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, null, null);
  }
  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_getInstanceGroupedByArtifactList_NonGitOps() {
    CDOverviewDashboardServiceImpl cdOverviewDashboardService1 = spy(cdOverviewDashboardService);
    InstanceGroupedByServiceList.InstanceGroupedByService instanceGroupedByService =
        getSampleListInstanceGroupedByServiceForActiveDeployments().get(0);
    mockServiceEntityForNonGitOps();
    doReturn(InstanceGroupedByServiceList.builder()
                 .instanceGroupedByServiceList(Arrays.asList(instanceGroupedByService))
                 .build())
        .when(cdOverviewDashboardService1)
        .getInstanceGroupedByServiceListHelper(anyList());
    assertThat(instanceGroupedByService)
        .isEqualTo(
            cdOverviewDashboardService1.getInstanceGroupedByArtifactList(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID));
    verify(instanceDashboardService)
        .getActiveServiceInstanceInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID, null, SERVICE_ID, null, false);
    verifyServiceEntityCall();
    verify(cdOverviewDashboardService1).getInstanceGroupedByServiceListHelper(anyList());
  }
  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_getInstanceGroupedByArtifactList_GitOps() {
    CDOverviewDashboardServiceImpl cdOverviewDashboardService1 = spy(cdOverviewDashboardService);
    InstanceGroupedByServiceList.InstanceGroupedByService instanceGroupedByService =
        getSampleListInstanceGroupedByServiceForActiveDeployments().get(0);
    mockServiceEntityForGitOps();
    doReturn(InstanceGroupedByServiceList.builder()
                 .instanceGroupedByServiceList(Arrays.asList(instanceGroupedByService))
                 .build())
        .when(cdOverviewDashboardService1)
        .getInstanceGroupedByServiceListHelper(anyList());
    assertThat(instanceGroupedByService)
        .isEqualTo(
            cdOverviewDashboardService1.getInstanceGroupedByArtifactList(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID));
    verify(instanceDashboardService)
        .getActiveServiceInstanceInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID, null, SERVICE_ID, null, true);
    verifyServiceEntityCall();
    verify(cdOverviewDashboardService1).getInstanceGroupedByServiceListHelper(anyList());
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_constructEnvironmentCountMap() {
    List<EnvironmentInstanceCountModel> environmentInstanceCountModels = getEnvironmentInstanceCountModelList();
    List<String> envIds = Arrays.asList(ENVIRONMENT_1, ENVIRONMENT_2);
    Set<String> envIdResult = new HashSet<>();
    Map<String, Integer> envIdToCountMap = new HashMap<>();
    envIdToCountMap.put(ENVIRONMENT_1, 2);
    envIdToCountMap.put(ENVIRONMENT_2, 1);
    Map<String, Integer> envIdToCountMapResult = new HashMap<>();
    DashboardServiceHelper.constructEnvironmentCountMap(
        environmentInstanceCountModels, envIdToCountMapResult, envIdResult);
    assertThat(envIds.size()).isEqualTo(envIdResult.size());
    assertThat(envIdToCountMap).isEqualTo(envIdToCountMapResult);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_constructEnvironmentNameAndTypeMap() {
    List<Environment> environments = getEnvironmentList();
    Map<String, String> envIdToEnvNameMap = new HashMap<>();
    envIdToEnvNameMap.put(ENVIRONMENT_1, ENVIRONMENT_NAME_1);
    envIdToEnvNameMap.put(ENVIRONMENT_2, ENVIRONMENT_NAME_2);
    Map<String, EnvironmentType> envIdToEnvTypeMap = new HashMap<>();
    envIdToEnvTypeMap.put(ENVIRONMENT_1, EnvironmentType.PreProduction);
    envIdToEnvTypeMap.put(ENVIRONMENT_2, EnvironmentType.Production);
    Map<String, String> envIdToEnvNameMapResult = new HashMap<>();
    Map<String, EnvironmentType> envIdToEnvTypeMapResult = new HashMap<>();
    DashboardServiceHelper.constructEnvironmentNameAndTypeMap(
        environments, envIdToEnvNameMapResult, envIdToEnvTypeMapResult);
    assertThat(envIdToEnvNameMap).isEqualTo(envIdToEnvNameMapResult);
    assertThat(envIdToEnvTypeMap).isEqualTo(envIdToEnvTypeMapResult);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_constructEnvironmentToArtifactDeploymentMap() {
    List<ArtifactDeploymentDetailModel> artifactDeploymentDetailModels = getArtifactDeploymentDetailModelList();
    Map<String, ArtifactDeploymentDetail> artifactDeploymentDetailMap = new HashMap<>();
    artifactDeploymentDetailMap.put(ENVIRONMENT_1,
        ArtifactDeploymentDetail.builder()
            .envId(ENVIRONMENT_1)
            .envName(ENVIRONMENT_NAME_1)
            .lastPipelineExecutionId(PLAN_EXECUTION_1)
            .artifact(DISPLAY_NAME_1)
            .lastDeployedAt(1l)
            .build());
    artifactDeploymentDetailMap.put(ENVIRONMENT_2,
        ArtifactDeploymentDetail.builder()
            .envId(ENVIRONMENT_2)
            .envName(ENVIRONMENT_NAME_2)
            .lastPipelineExecutionId(PLAN_EXECUTION_2)
            .artifact(DISPLAY_NAME_2)
            .lastDeployedAt(2l)
            .build());

    Map<String, String> envIdToEnvNameMap = new HashMap<>();
    envIdToEnvNameMap.put(ENVIRONMENT_1, ENVIRONMENT_NAME_1);
    envIdToEnvNameMap.put(ENVIRONMENT_2, ENVIRONMENT_NAME_2);
    Map<String, ArtifactDeploymentDetail> artifactDeploymentDetailMapResult =
        DashboardServiceHelper.constructEnvironmentToArtifactDeploymentMap(
            artifactDeploymentDetailModels, envIdToEnvNameMap);
    assertArtifactDeploymentDetail(
        artifactDeploymentDetailMap.get(ENVIRONMENT_1), artifactDeploymentDetailMapResult.get(ENVIRONMENT_1));
  }

  private void assertArtifactDeploymentDetail(
      ArtifactDeploymentDetail artifactDeploymentDetail, ArtifactDeploymentDetail artifactDeploymentDetail1) {
    assertThat(artifactDeploymentDetail.getArtifact()).isEqualTo(artifactDeploymentDetail1.getArtifact());
    assertThat(artifactDeploymentDetail.getEnvId()).isEqualTo(artifactDeploymentDetail1.getEnvId());
    assertThat(artifactDeploymentDetail.getLastDeployedAt()).isEqualTo(artifactDeploymentDetail1.getLastDeployedAt());
    assertThat(artifactDeploymentDetail.getEnvName()).isEqualTo(artifactDeploymentDetail1.getEnvName());
    assertThat(artifactDeploymentDetail.getLastPipelineExecutionId())
        .isEqualTo(artifactDeploymentDetail1.getLastPipelineExecutionId());
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_getEnvironmentInstanceDetails() {
    CDOverviewDashboardServiceImpl cdOverviewDashboardService1 = spy(cdOverviewDashboardService);
    List<ArtifactDeploymentDetailModel> artifactDeploymentDetailModels = getArtifactDeploymentDetailModelList();
    List<EnvironmentInstanceCountModel> environmentInstanceCountModels = getEnvironmentInstanceCountModelList();
    List<Environment> environments = getEnvironmentList();
    List<String> envIds = Arrays.asList(ENVIRONMENT_2, ENVIRONMENT_1);
    Optional<ServiceSequence> serviceSequence = Optional.of(ServiceSequence.builder().build());
    Page<EnvironmentGroupEntity> page = mock(Page.class);
    when(environmentGroupService.list(any(), any(), any(), any(), any())).thenReturn(page);
    when(serviceSequenceService.get(any(), any(), any(), any())).thenReturn(serviceSequence);
    EnvironmentGroupEntity environmentGroupEntity1 = EnvironmentGroupEntity.builder()
                                                         .accountId(ACCOUNT_ID)
                                                         .orgIdentifier(ORG_ID)
                                                         .projectIdentifier(PROJECT_ID)
                                                         .identifier(ENVIRONMENT_GROUP_1)
                                                         .name(ENVIRONMENT_GROUP_NAME_1)
                                                         .envIdentifiers(Collections.singletonList(ENVIRONMENT_1))
                                                         .build();
    EnvironmentGroupEntity environmentGroupEntity2 = EnvironmentGroupEntity.builder()
                                                         .accountId(ACCOUNT_ID)
                                                         .orgIdentifier(ORG_ID)
                                                         .projectIdentifier(PROJECT_ID)
                                                         .identifier(ENVIRONMENT_GROUP_2)
                                                         .name(ENVIRONMENT_GROUP_NAME_2)
                                                         .envIdentifiers(Arrays.asList(ENVIRONMENT_1, ENVIRONMENT_2))
                                                         .build();
    when(page.getContent()).thenReturn(Arrays.asList(environmentGroupEntity1, environmentGroupEntity2));
    Map<String, ServicePipelineWithRevertInfo> servicePipelineInfoMap = new HashMap<>();
    servicePipelineInfoMap.put(PLAN_EXECUTION_1,
        ServicePipelineWithRevertInfo.builder()
            .isRevertExecution(false)
            .identifier(PIPELINE_EXECUTION_SUMMARY_CD_ID_1)
            .planExecutionId(PLAN_EXECUTION_1)
            .pipelineExecutionId(PIPELINE_EXECUTION_1)
            .build());
    servicePipelineInfoMap.put(PLAN_EXECUTION_2,
        ServicePipelineWithRevertInfo.builder()
            .isRevertExecution(true)
            .identifier(PIPELINE_EXECUTION_SUMMARY_CD_ID_2)
            .planExecutionId(PLAN_EXECUTION_2)
            .pipelineExecutionId(PIPELINE_EXECUTION_2)
            .build());
    doReturn(servicePipelineInfoMap)
        .when(cdOverviewDashboardService1)
        .getPipelineExecutionDetailsWithRevertInfo(anyList());
    doReturn(Arrays.asList(PIPELINE_EXECUTION_SUMMARY_CD_ID_1))
        .when(cdOverviewDashboardService1)
        .getPipelineExecutionsWhereRollbackOccurred(anyList());
    when(instanceDashboardService.getInstanceCountForEnvironmentFilteredByService(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, false))
        .thenReturn(environmentInstanceCountModels);
    when(instanceDashboardService.getLastDeployedInstance(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, true, false))
        .thenReturn(artifactDeploymentDetailModels);
    when(environmentService.fetchesNonDeletedEnvIdentifiersFromList(any(), any(), any(), any()))
        .thenReturn(Arrays.asList(ENVIRONMENT_2, ENVIRONMENT_1));
    when(environmentService.fetchesNonDeletedEnvironmentFromListOfRefs(any(), any(), any(), any()))
        .thenReturn(environments);
    mockServiceEntityForNonGitOps();

    EnvironmentGroupInstanceDetails environmentInstanceDetails =
        EnvironmentGroupInstanceDetails.builder()
            .environmentGroupInstanceDetails(getEnvironmentGroupInstanceDetailList())
            .build();
    EnvironmentGroupInstanceDetails environmentInstanceDetailResult =
        cdOverviewDashboardService1.getEnvironmentInstanceDetails(
            ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, null, false);
    assertThat(environmentInstanceDetails.getEnvironmentGroupInstanceDetails().size())
        .isEqualTo(environmentInstanceDetailResult.getEnvironmentGroupInstanceDetails().size());
    verify(instanceDashboardService)
        .getInstanceCountForEnvironmentFilteredByService(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, false);
    verify(instanceDashboardService).getLastDeployedInstance(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, true, false);
    verify(environmentService).fetchesNonDeletedEnvironmentFromListOfRefs(ACCOUNT_ID, ORG_ID, PROJECT_ID, envIds);
    verifyServiceEntityCall();
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_getInstanceDetailGroupedByPipelineExecution() {
    InstanceDetailGroupedByPipelineExecutionList
        .InstanceDetailGroupedByPipelineExecution instanceDetailGroupedByPipelineExecution1 =
        InstanceDetailGroupedByPipelineExecutionList.InstanceDetailGroupedByPipelineExecution.builder()
            .pipelineId(PIPELINE_1)
            .planExecutionId(PIPELINE_EXECUTION_1)
            .lastDeployedAt(1l)
            .instances(Arrays.asList(
                InstanceDetailsDTO.builder().podName("1").build(), InstanceDetailsDTO.builder().podName("2").build()))
            .build();
    InstanceDetailGroupedByPipelineExecutionList
        .InstanceDetailGroupedByPipelineExecution instanceDetailGroupedByPipelineExecution2 =
        InstanceDetailGroupedByPipelineExecutionList.InstanceDetailGroupedByPipelineExecution.builder()
            .pipelineId(PIPELINE_2)
            .planExecutionId(PIPELINE_EXECUTION_2)
            .lastDeployedAt(2l)
            .instances(Arrays.asList(
                InstanceDetailsDTO.builder().podName("3").build(), InstanceDetailsDTO.builder().podName("4").build()))
            .build();
    List<InstanceDetailGroupedByPipelineExecutionList.InstanceDetailGroupedByPipelineExecution>
        instanceDetailGroupedByPipelineExecutionList =
            Arrays.asList(instanceDetailGroupedByPipelineExecution1, instanceDetailGroupedByPipelineExecution2);
    List<InstanceDetailGroupedByPipelineExecutionList.InstanceDetailGroupedByPipelineExecution>
        instanceDetailGroupedByPipelineExecutionListSorted =
            Arrays.asList(instanceDetailGroupedByPipelineExecution2, instanceDetailGroupedByPipelineExecution1);

    when(instanceDashboardService.getActiveInstanceDetailGroupedByPipelineExecution(ACCOUNT_ID, ORG_ID, PROJECT_ID,
             SERVICE_ID, ENVIRONMENT_1, EnvironmentType.Production, INFRASTRUCTURE_1, null, DISPLAY_NAME_1, false))
        .thenReturn(instanceDetailGroupedByPipelineExecutionList);
    mockServiceEntityForNonGitOps();

    InstanceDetailGroupedByPipelineExecutionList instanceDetailGroupedByPipelineExecutionList1 =
        InstanceDetailGroupedByPipelineExecutionList.builder()
            .instanceDetailGroupedByPipelineExecutionList(instanceDetailGroupedByPipelineExecutionListSorted)
            .build();
    InstanceDetailGroupedByPipelineExecutionList instanceDetailGroupedByPipelineExecutionList2 =
        cdOverviewDashboardService.getInstanceDetailGroupedByPipelineExecution(ACCOUNT_ID, ORG_ID, PROJECT_ID,
            SERVICE_ID, ENVIRONMENT_1, EnvironmentType.Production, INFRASTRUCTURE_1, null, DISPLAY_NAME_1);

    assertThat(instanceDetailGroupedByPipelineExecutionList1).isEqualTo(instanceDetailGroupedByPipelineExecutionList2);
    verifyServiceEntityCall();
    verify(instanceDashboardService)
        .getActiveInstanceDetailGroupedByPipelineExecution(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, ENVIRONMENT_1,
            EnvironmentType.Production, INFRASTRUCTURE_1, null, DISPLAY_NAME_1, false);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_getInstanceGroupedByEnvironmentList() {
    List<ActiveServiceInstanceInfoWithEnvType> activeServiceInstanceInfoWithEnvTypeList = new ArrayList<>();
    InstanceGroupedByEnvironmentList instanceGroupedByEnvironmentList =
        InstanceGroupedByEnvironmentList.builder().build();
    when(instanceDashboardService.getActiveServiceInstanceInfoWithEnvType(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, ENVIRONMENT_1, SERVICE_ID, null, false, false))
        .thenReturn(activeServiceInstanceInfoWithEnvTypeList);
    when(serviceEntityServiceImpl.getService(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID))
        .thenReturn(Optional.of(ServiceEntity.builder().gitOpsEnabled(false).build()));
    mockStatic(DashboardServiceHelper.class);
    when(DashboardServiceHelper.getInstanceGroupedByEnvironmentListHelper(
             null, activeServiceInstanceInfoWithEnvTypeList, false, null))
        .thenReturn(instanceGroupedByEnvironmentList);

    InstanceGroupedByEnvironmentList instanceGroupedByEnvironmentList1 =
        cdOverviewDashboardService.getInstanceGroupedByEnvironmentList(
            ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, ENVIRONMENT_1, null);

    assertThat(instanceGroupedByEnvironmentList1).isEqualTo(instanceGroupedByEnvironmentList);
    verify(serviceEntityServiceImpl).getService(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID);
    verify(instanceDashboardService)
        .getActiveServiceInstanceInfoWithEnvType(
            ACCOUNT_ID, ORG_ID, PROJECT_ID, ENVIRONMENT_1, SERVICE_ID, null, false, false);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_getArtifactInstanceDetails() {
    List<ArtifactDeploymentDetailModel> artifactDeploymentDetailModels =
        getArtifactDeploymentDetailModelList_ArtifactCard();
    List<Environment> environments = getEnvironmentList();
    List<String> envIds = Arrays.asList(ENVIRONMENT_2, ENVIRONMENT_1);

    when(instanceDashboardService.getLastDeployedInstance(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, false, false))
        .thenReturn(artifactDeploymentDetailModels);
    when(environmentService.fetchesNonDeletedEnvIdentifiersFromList(any(), any(), any(), any()))
        .thenReturn(Arrays.asList(ENVIRONMENT_2, ENVIRONMENT_1));
    when(environmentService.fetchesNonDeletedEnvironmentFromListOfRefs(ACCOUNT_ID, ORG_ID, PROJECT_ID, envIds))
        .thenReturn(environments);
    mockServiceEntityForNonGitOps();

    Page<EnvironmentGroupEntity> page = mock(Page.class);
    when(environmentGroupService.list(any(), any(), any(), any(), any())).thenReturn(page);
    EnvironmentGroupEntity environmentGroupEntity1 = EnvironmentGroupEntity.builder()
                                                         .accountId(ACCOUNT_ID)
                                                         .orgIdentifier(ORG_ID)
                                                         .projectIdentifier(PROJECT_ID)
                                                         .identifier(ENVIRONMENT_GROUP_1)
                                                         .name(ENVIRONMENT_GROUP_NAME_1)
                                                         .envIdentifiers(Collections.singletonList(ENVIRONMENT_1))
                                                         .build();
    EnvironmentGroupEntity environmentGroupEntity2 = EnvironmentGroupEntity.builder()
                                                         .accountId(ACCOUNT_ID)
                                                         .orgIdentifier(ORG_ID)
                                                         .projectIdentifier(PROJECT_ID)
                                                         .identifier(ENVIRONMENT_GROUP_2)
                                                         .name(ENVIRONMENT_GROUP_NAME_2)
                                                         .envIdentifiers(Arrays.asList(ENVIRONMENT_1, ENVIRONMENT_2))
                                                         .build();
    when(page.getContent()).thenReturn(Arrays.asList(environmentGroupEntity1, environmentGroupEntity2));

    ArtifactInstanceDetails artifactInstanceDetails =
        ArtifactInstanceDetails.builder().artifactInstanceDetails(getArtifactInstanceDetailList()).build();
    ArtifactInstanceDetails artifactInstanceDetailsResult =
        cdOverviewDashboardService.getArtifactInstanceDetails(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID);
    assertThat(artifactInstanceDetails.getArtifactInstanceDetails().size())
        .isEqualTo(artifactInstanceDetailsResult.getArtifactInstanceDetails().size());
    verify(instanceDashboardService).getLastDeployedInstance(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, false, false);
    verify(environmentService).fetchesNonDeletedEnvironmentFromListOfRefs(ACCOUNT_ID, ORG_ID, PROJECT_ID, envIds);
    verifyServiceEntityCall();
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_getInstanceGroupedOnArtifactList() {
    List<ActiveServiceInstanceInfoWithEnvType> activeServiceInstanceInfoWithEnvTypeList = new ArrayList<>();
    InstanceGroupedOnArtifactList instanceGroupedOnArtifactList = InstanceGroupedOnArtifactList.builder().build();
    when(instanceDashboardService.getActiveServiceInstanceInfoWithEnvType(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, ENVIRONMENT_1, SERVICE_ID, DISPLAY_NAME_1, false, true))
        .thenReturn(activeServiceInstanceInfoWithEnvTypeList);
    when(serviceEntityServiceImpl.getService(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID))
        .thenReturn(Optional.of(ServiceEntity.builder().gitOpsEnabled(false).build()));
    mockStatic(DashboardServiceHelper.class);
    when(DashboardServiceHelper.getInstanceGroupedByArtifactListHelper(
             activeServiceInstanceInfoWithEnvTypeList, false, null, null))
        .thenReturn(instanceGroupedOnArtifactList);

    InstanceGroupedOnArtifactList instanceGroupedOnArtifactList1 =
        cdOverviewDashboardService.getInstanceGroupedOnArtifactList(
            ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, ENVIRONMENT_1, null, DISPLAY_NAME_1, true);

    assertThat(instanceGroupedOnArtifactList1).isEqualTo(instanceGroupedOnArtifactList);
    verify(serviceEntityServiceImpl).getService(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID);
    verify(instanceDashboardService)
        .getActiveServiceInstanceInfoWithEnvType(
            ACCOUNT_ID, ORG_ID, PROJECT_ID, ENVIRONMENT_1, SERVICE_ID, DISPLAY_NAME_1, false, true);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_OpenTasks() {
    CDOverviewDashboardServiceImpl cdOverviewDashboardService1 = spy(cdOverviewDashboardService);
    String query = DashboardServiceHelper.buildOpenTaskQuery(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, 1000l);
    List<String> STATUS_LIST = Arrays
                                   .asList(ExecutionStatus.ABORTED, ExecutionStatus.ABORTEDBYFREEZE,
                                       ExecutionStatus.FAILED, ExecutionStatus.EXPIRED, ExecutionStatus.APPROVALWAITING)
                                   .stream()
                                   .map(ExecutionStatus::name)
                                   .collect(Collectors.toList());
    Map<String, String> pipelineExecutionToFailureMessageMap = new HashMap<>();
    pipelineExecutionToFailureMessageMap.put(PIPELINE_EXECUTION_1, FAILURE_MESSAGE_1);
    pipelineExecutionToFailureMessageMap.put(PIPELINE_EXECUTION_2, FAILURE_MESSAGE_2);

    List<ServicePipelineWithRevertInfo> servicePipelineRevertInfoList =
        Arrays.asList(ServicePipelineWithRevertInfo.builder()
                          .pipelineExecutionId(PIPELINE_EXECUTION_2)
                          .lastExecutedAt(1l)
                          .failureDetail(FAILURE_MESSAGE_2)
                          .build(),
            ServicePipelineWithRevertInfo.builder()
                .pipelineExecutionId(PIPELINE_EXECUTION_1)
                .lastExecutedAt(2l)
                .failureDetail(FAILURE_MESSAGE_1)
                .build());
    List<ServicePipelineInfo> servicePipelineInfoList = Arrays.asList(
        ServicePipelineInfo.builder().pipelineExecutionId(PIPELINE_EXECUTION_2).lastExecutedAt(1l).build(),
        ServicePipelineInfo.builder().pipelineExecutionId(PIPELINE_EXECUTION_1).lastExecutedAt(2l).build());
    List<ServicePipelineWithRevertInfo> servicePipelineInfoListSorted =
        Arrays.asList(servicePipelineRevertInfoList.get(1), servicePipelineRevertInfoList.get(0));
    Map<String, ServicePipelineInfo> servicePipelineInfoMap = new HashMap<>();
    servicePipelineInfoMap.put(PIPELINE_EXECUTION_1, servicePipelineInfoList.get(0));
    servicePipelineInfoMap.put(PIPELINE_EXECUTION_2, servicePipelineInfoList.get(1));
    doReturn(pipelineExecutionToFailureMessageMap)
        .when(cdOverviewDashboardService1)
        .getPipelineExecutionIdAndFailureDetailsFromServiceInfraInfo(query);
    doReturn(servicePipelineInfoMap).when(cdOverviewDashboardService1).getPipelineExecutionDetails(any(), any());
    OpenTaskDetails openTaskDetailsResult =
        cdOverviewDashboardService1.getOpenTasks(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, 1000l);
    OpenTaskDetails openTaskDetails =
        OpenTaskDetails.builder().pipelineDeploymentDetails(servicePipelineInfoListSorted).build();
    assertThat(openTaskDetails).isEqualTo(openTaskDetailsResult);
    verify(cdOverviewDashboardService1).getPipelineExecutionIdAndFailureDetailsFromServiceInfraInfo(query);
    verify(cdOverviewDashboardService1).getPipelineExecutionDetails(any(), any());
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_getPipelineExecutionCountInfo() throws InvalidRequestException {
    CDOverviewDashboardServiceImpl cdOverviewDashboardService1 = spy(cdOverviewDashboardService);
    List<ServiceArtifactExecutionDetail> serviceArtifactExecutionDetailList = getServiceArtifactExecutionDetailList();
    Map<String, String> statusMap = getExecutionStatusMap();
    String queryExecutionIdAndArtifactDetails =
        "select accountid, orgidentifier, projectidentifier, service_id, service_name, artifact_display_name, artifact_image, tag, pipeline_execution_summary_cd_id, service_startts from service_infra_info where accountid = 'accountID' and orgidentifier = 'orgId' and projectidentifier = 'projectId' and service_id is not null and service_startts >= 1 and service_startts <= 3 and service_id = 'serviceId' and artifact_display_name = 'display1:1' and artifact_image = 'display1' and tag = '1'";
    String queryGetPipelineExecutionStatusMap =
        "select id, status from pipeline_execution_summary_cd where accountid = 'accountID' and orgidentifier = 'orgId' and projectidentifier = 'projectId' and id = any (?) and status = 'SUCCESS'";
    doReturn(serviceArtifactExecutionDetailList)
        .when(cdOverviewDashboardService1)
        .getExecutionIdAndArtifactDetails(queryExecutionIdAndArtifactDetails);
    doReturn(statusMap)
        .when(cdOverviewDashboardService1)
        .getPipelineExecutionStatusMap(
            statusMap.keySet().stream().collect(Collectors.toList()), queryGetPipelineExecutionStatusMap);
    PipelineExecutionCountInfo pipelineExecutionCountInfoResult =
        cdOverviewDashboardService1.getPipelineExecutionCountInfo(
            ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, 1l, 3l, ARTIFACT_PATH_1, TAG_1, DISPLAY_NAME_1, SUCCESS);
    PipelineExecutionCountInfo.CountGroupedOnStatus countGroupedOnStatus1 =
        PipelineExecutionCountInfo.CountGroupedOnStatus.builder().count(1l).status(SUCCESS).build();
    PipelineExecutionCountInfo.CountGroupedOnStatus countGroupedOnStatus2 =
        PipelineExecutionCountInfo.CountGroupedOnStatus.builder().count(1l).status(FAILED).build();
    PipelineExecutionCountInfo.CountGroupedOnArtifact countGroupedOnArtifact1 =
        PipelineExecutionCountInfo.CountGroupedOnArtifact.builder()
            .count(2l)
            .artifact(DISPLAY_NAME_1)
            .artifactVersion(TAG_1)
            .artifactPath(ARTIFACT_PATH_1)
            .executionCountGroupedOnStatusList(Arrays.asList(countGroupedOnStatus1, countGroupedOnStatus2))
            .build();
    PipelineExecutionCountInfo.CountGroupedOnArtifact countGroupedOnArtifact2 =
        PipelineExecutionCountInfo.CountGroupedOnArtifact.builder()
            .count(1l)
            .artifact(DISPLAY_NAME_2)
            .artifactVersion(TAG_2)
            .artifactPath(ARTIFACT_PATH_2)
            .executionCountGroupedOnStatusList(Arrays.asList(countGroupedOnStatus2))
            .build();
    PipelineExecutionCountInfo.CountGroupedOnService countGroupedOnService1 =
        PipelineExecutionCountInfo.CountGroupedOnService.builder()
            .count(2l)
            .executionCountGroupedOnStatusList(Arrays.asList(countGroupedOnStatus1, countGroupedOnStatus2))
            .serviceReference("accountID/orgId/projectId/serviceId")
            .serviceName("serviceName")
            .executionCountGroupedOnArtifactList(Arrays.asList(countGroupedOnArtifact2, countGroupedOnArtifact1))
            .build();
    PipelineExecutionCountInfo.CountGroupedOnService countGroupedOnService2 =
        PipelineExecutionCountInfo.CountGroupedOnService.builder()
            .count(1l)
            .executionCountGroupedOnStatusList(Arrays.asList(countGroupedOnStatus2))
            .serviceReference("accountID/orgId/serviceId2")
            .serviceName("serviceName2")
            .executionCountGroupedOnArtifactList(Arrays.asList(countGroupedOnArtifact2))
            .build();
    PipelineExecutionCountInfo pipelineExecutionCountInfo =
        PipelineExecutionCountInfo.builder()
            .executionCountGroupedOnServiceList(Arrays.asList(countGroupedOnService1, countGroupedOnService2))
            .build();
    assertThat(pipelineExecutionCountInfo).isEqualTo(pipelineExecutionCountInfoResult);
    verify(cdOverviewDashboardService1)
        .getPipelineExecutionStatusMap(
            statusMap.keySet().stream().collect(Collectors.toList()), queryGetPipelineExecutionStatusMap);
    verify(cdOverviewDashboardService1).getExecutionIdAndArtifactDetails(queryExecutionIdAndArtifactDetails);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void test_getDeploymentIconMap() throws InvalidRequestException, IOException {
    String yamlForServiceWithTemplate =
        "service:\n  name: s-2\n  identifier: s1\n  serviceDefinition:\n    type: CustomDeployment\n    spec:\n      customDeploymentRef:\n        templateRef: temp1\n        versionLabel: \"1\"\n  gitOpsEnabled: false\n";

    ServiceEntity serviceEntity = ServiceEntity.builder()
                                      .yaml(yamlForServiceWithTemplate)
                                      .identifier("s1")
                                      .accountId(ACCOUNT_ID)
                                      .orgIdentifier(ORG_ID)
                                      .projectIdentifier(PROJECT_ID)
                                      .build();
    Map<String, Set<String>> serviceIdToDeploymentTypeMap = new HashMap<>();
    serviceIdToDeploymentTypeMap.put("s1", new HashSet<>(Arrays.asList("CustomDeployment")));

    Call<ResponseDTO<PageResponse<TemplateMetadataSummaryResponseDTO>>> callRequest = mock(Call.class);

    TemplateFilterPropertiesDTO templateFilterPropertiesDTO =
        TemplateFilterPropertiesDTO.builder()
            .templateEntityTypes(Collections.singletonList(TemplateEntityType.CUSTOM_DEPLOYMENT_TEMPLATE))
            .templateIdentifiers(Arrays.asList("temp1"))
            .build();

    Mockito
        .when(templateResourceClient.listTemplateMetadata(
            ACCOUNT_ID, ORG_ID, PROJECT_ID, STABLE_TEMPLATE_TYPE, 0, 1, templateFilterPropertiesDTO))
        .thenReturn(callRequest);

    PageResponse<TemplateMetadataSummaryResponseDTO> pageResponse =
        PageResponse.<TemplateMetadataSummaryResponseDTO>builder()
            .content(Collections.singletonList(TemplateMetadataSummaryResponseDTO.builder()
                                                   .templateEntityType(TemplateEntityType.CUSTOM_DEPLOYMENT_TEMPLATE)
                                                   .icon("IconString")
                                                   .identifier("temp1")
                                                   .build()))
            .totalPages(1)
            .pageIndex(0)
            .pageSize(1)
            .build();

    when(callRequest.execute()).thenReturn(Response.success(ResponseDTO.newResponse(pageResponse)));

    Map<String, Set<IconDTO>> resultMap = cdOverviewDashboardService.getDeploymentIconMap(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, Arrays.asList(serviceEntity), serviceIdToDeploymentTypeMap);

    List<IconDTO> iconDTO = new ArrayList<>(resultMap.get("s1"));
    assertThat(iconDTO.get(0).getIcon()).isEqualTo("IconString");
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void test_getDeploymentIconMapWithMultipleServices() throws InvalidRequestException, IOException {
    String yamlForServiceWithTemplate =
        "service:\n  name: s-2\n  identifier: s2\n  serviceDefinition:\n    type: CustomDeployment\n    spec:\n      customDeploymentRef:\n        templateRef: temp1\n        versionLabel: \"1\"\n  gitOpsEnabled: false\n";

    ServiceEntity serviceEntity1 = ServiceEntity.builder()
                                       .yaml(yamlForServiceWithTemplate)
                                       .identifier("s1")
                                       .accountId(ACCOUNT_ID)
                                       .orgIdentifier(ORG_ID)
                                       .projectIdentifier(PROJECT_ID)
                                       .build();
    ServiceEntity serviceEntity2 = ServiceEntity.builder()
                                       .yaml(yamlForServiceWithTemplate)
                                       .identifier("s2")
                                       .accountId(ACCOUNT_ID)
                                       .orgIdentifier(ORG_ID)
                                       .projectIdentifier(PROJECT_ID)
                                       .build();

    Map<String, Set<String>> serviceIdToDeploymentTypeMap = new HashMap<>();
    serviceIdToDeploymentTypeMap.put("s1", new HashSet<>(Arrays.asList("CustomDeployment")));
    serviceIdToDeploymentTypeMap.put("s2", new HashSet<>(Arrays.asList("K8s")));

    Call<ResponseDTO<PageResponse<TemplateMetadataSummaryResponseDTO>>> callRequest = mock(Call.class);

    TemplateFilterPropertiesDTO templateFilterPropertiesDTO =
        TemplateFilterPropertiesDTO.builder()
            .templateEntityTypes(Collections.singletonList(TemplateEntityType.CUSTOM_DEPLOYMENT_TEMPLATE))
            .templateIdentifiers(Arrays.asList("temp1"))
            .build();

    Mockito
        .when(templateResourceClient.listTemplateMetadata(
            ACCOUNT_ID, ORG_ID, PROJECT_ID, STABLE_TEMPLATE_TYPE, 0, 1, templateFilterPropertiesDTO))
        .thenReturn(callRequest);

    PageResponse<TemplateMetadataSummaryResponseDTO> pageResponse =
        PageResponse.<TemplateMetadataSummaryResponseDTO>builder()
            .content(Collections.singletonList(TemplateMetadataSummaryResponseDTO.builder()
                                                   .templateEntityType(TemplateEntityType.CUSTOM_DEPLOYMENT_TEMPLATE)
                                                   .icon("IconString")
                                                   .identifier("temp1")
                                                   .build()))
            .totalPages(1)
            .pageIndex(0)
            .pageSize(1)
            .build();

    when(callRequest.execute()).thenReturn(Response.success(ResponseDTO.newResponse(pageResponse)));

    Map<String, Set<IconDTO>> resultMap = cdOverviewDashboardService.getDeploymentIconMap(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, Arrays.asList(serviceEntity1, serviceEntity2), serviceIdToDeploymentTypeMap);

    List<IconDTO> iconDTO = new ArrayList<>(resultMap.get("s1"));
    assertThat(iconDTO.get(0).getIcon()).isEqualTo("IconString");
    iconDTO = new ArrayList<>(resultMap.get("s2"));
    assertThat(iconDTO.get(0).getIcon()).isEqualTo("");
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void test_getInstanceGroupedByEnvironmentListRevamp() {
    List<ActiveServiceInstanceInfoWithEnvType> activeServiceInstanceInfoWithEnvTypeList =
        Arrays.asList(getActiveServiceInstanceInfoWithEnvType(ENVIRONMENT_1, ARTIFACT_PATH_1),
            getActiveServiceInstanceInfoWithEnvType(ENVIRONMENT_2, ARTIFACT_PATH_1),
            getActiveServiceInstanceInfoWithEnvType(ENVIRONMENT_3, ARTIFACT_PATH_1));
    InstanceGroupedByEnvironmentList instanceGroupedByEnvironmentList =
        InstanceGroupedByEnvironmentList.builder().build();
    when(instanceDashboardService.getActiveServiceInstanceInfoWithEnvType(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, null, SERVICE_ID, null, false, false))
        .thenReturn(activeServiceInstanceInfoWithEnvTypeList);
    when(serviceEntityServiceImpl.getService(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID))
        .thenReturn(Optional.of(ServiceEntity.builder().gitOpsEnabled(false).build()));

    when(environmentService.fetchesNonDeletedEnvironmentFromListOfRefs(any(), any(), any(), any()))
        .thenReturn(getEnvironmentList());
    Page<EnvironmentGroupEntity> page = mock(Page.class);
    when(environmentGroupService.list(any(), any(), any(), any(), any())).thenReturn(page);

    when(page.getContent()).thenReturn(Collections.emptyList());
    ArgumentCaptor<List<ActiveServiceInstanceInfoWithEnvType>> argumentCaptor = ArgumentCaptor.forClass(List.class);
    Mockito.mockStatic(DashboardServiceHelper.class);
    PowerMockito
        .when(DashboardServiceHelper.getInstanceGroupedByEnvironmentListHelper(any(), any(), anyBoolean(), any()))
        .thenReturn(instanceGroupedByEnvironmentList);

    InstanceGroupedByEnvironmentList instanceGroupedByEnvironmentList1 =
        cdOverviewDashboardService.getInstanceGroupedByEnvironmentList(
            ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, null, null);

    PowerMockito.verifyStatic(DashboardServiceHelper.class, times(1));
    DashboardServiceHelper.getInstanceGroupedByEnvironmentListHelper(
        any(), argumentCaptor.capture(), anyBoolean(), any());
    List<ActiveServiceInstanceInfoWithEnvType> activeServiceInstanceInfoWithEnvTypes = argumentCaptor.getValue();
    assertThat(activeServiceInstanceInfoWithEnvTypes.size()).isEqualTo(2);
    assertThat(activeServiceInstanceInfoWithEnvTypes.get(0).getEnvIdentifier()).isEqualTo(ENVIRONMENT_1);
    assertThat(activeServiceInstanceInfoWithEnvTypes.get(1).getEnvIdentifier()).isEqualTo(ENVIRONMENT_2);

    assertThat(instanceGroupedByEnvironmentList1).isEqualTo(instanceGroupedByEnvironmentList);
    verify(serviceEntityServiceImpl).getService(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID);
    verify(instanceDashboardService)
        .getActiveServiceInstanceInfoWithEnvType(ACCOUNT_ID, ORG_ID, PROJECT_ID, null, SERVICE_ID, null, false, false);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void test_getInstanceGroupedByArtifactListRevamp() {
    List<ActiveServiceInstanceInfoWithEnvType> activeServiceInstanceInfoWithEnvTypeList =
        Arrays.asList(getActiveServiceInstanceInfoWithEnvType(ENVIRONMENT_1, ARTIFACT_PATH_1),
            getActiveServiceInstanceInfoWithEnvType(ENVIRONMENT_2, ARTIFACT_PATH_1),
            getActiveServiceInstanceInfoWithEnvType(ENVIRONMENT_3, ARTIFACT_PATH_1));

    InstanceGroupedOnArtifactList instanceGroupedOnArtifactList = InstanceGroupedOnArtifactList.builder().build();
    when(instanceDashboardService.getActiveServiceInstanceInfoWithEnvType(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, ENVIRONMENT_1, SERVICE_ID, DISPLAY_NAME_1, false, true))
        .thenReturn(activeServiceInstanceInfoWithEnvTypeList);
    when(serviceEntityServiceImpl.getService(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID))
        .thenReturn(Optional.of(ServiceEntity.builder().gitOpsEnabled(false).build()));

    Page<EnvironmentGroupEntity> page = mock(Page.class);
    when(environmentGroupService.list(any(), any(), any(), any(), any())).thenReturn(page);

    when(environmentService.fetchesNonDeletedEnvironmentFromListOfRefs(any(), any(), any(), any()))
        .thenReturn(getEnvironmentList());
    mockStatic(DashboardServiceHelper.class);

    when(DashboardServiceHelper.getInstanceGroupedByArtifactListHelper(
             activeServiceInstanceInfoWithEnvTypeList, false, null, null))
        .thenReturn(instanceGroupedOnArtifactList);

    cdOverviewDashboardService.getInstanceGroupedOnArtifactList(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, ENVIRONMENT_1, null, DISPLAY_NAME_1, false);

    verify(instanceDashboardService, times(1))
        .getActiveServiceInstanceInfoWithEnvType(any(), any(), any(), any(), any(), any(), anyBoolean(), anyBoolean());
    verify(serviceEntityServiceImpl).getService(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID);
    verify(instanceDashboardService)
        .getActiveServiceInstanceInfoWithEnvType(
            ACCOUNT_ID, ORG_ID, PROJECT_ID, ENVIRONMENT_1, SERVICE_ID, DISPLAY_NAME_1, false, false);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void test_getInstanceGroupedByArtifactListRevampForArtifactFilter() {
    List<ActiveServiceInstanceInfoWithEnvType> activeServiceInstanceInfoWithEnvTypeList =
        Arrays.asList(getActiveServiceInstanceInfoWithEnvType(ENVIRONMENT_1, null),
            getActiveServiceInstanceInfoWithEnvType(ENVIRONMENT_2, null),
            getActiveServiceInstanceInfoWithEnvType(ENVIRONMENT_3, null));

    InstanceGroupedOnArtifactList instanceGroupedOnArtifactList = InstanceGroupedOnArtifactList.builder().build();
    when(instanceDashboardService.getActiveServiceInstanceInfoWithEnvType(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, ENVIRONMENT_1, SERVICE_ID, DISPLAY_NAME_1, false, true))
        .thenReturn(activeServiceInstanceInfoWithEnvTypeList);
    when(serviceEntityServiceImpl.getService(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID))
        .thenReturn(Optional.of(ServiceEntity.builder().gitOpsEnabled(false).build()));

    Page<EnvironmentGroupEntity> page = mock(Page.class);
    when(environmentGroupService.list(any(), any(), any(), any(), any())).thenReturn(page);

    when(environmentService.fetchesNonDeletedEnvironmentFromListOfRefs(any(), any(), any(), any()))
        .thenReturn(getEnvironmentList());
    mockStatic(DashboardServiceHelper.class);

    when(DashboardServiceHelper.getInstanceGroupedByArtifactListHelper(
             activeServiceInstanceInfoWithEnvTypeList, false, null, null))
        .thenReturn(instanceGroupedOnArtifactList);

    cdOverviewDashboardService.getInstanceGroupedOnArtifactList(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID, ENVIRONMENT_1, null, "", true);

    verify(instanceDashboardService, times(2))
        .getActiveServiceInstanceInfoWithEnvType(any(), any(), any(), any(), any(), any(), anyBoolean(), anyBoolean());
    verify(serviceEntityServiceImpl).getService(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID);
    verify(instanceDashboardService)
        .getActiveServiceInstanceInfoWithEnvType(
            ACCOUNT_ID, ORG_ID, PROJECT_ID, ENVIRONMENT_1, SERVICE_ID, null, false, true);
  }

  private ActiveServiceInstanceInfoWithEnvType getActiveServiceInstanceInfoWithEnvType(
      String envRef, String displayName) {
    return ActiveServiceInstanceInfoWithEnvType.builder()
        .envType(EnvironmentType.PreProduction)
        .envIdentifier(envRef)
        .displayName(displayName)
        .build();
  }
}