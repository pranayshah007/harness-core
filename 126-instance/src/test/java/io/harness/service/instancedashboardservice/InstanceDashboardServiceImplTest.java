/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.instancedashboardservice;

import static io.harness.entities.RollbackStatus.UNAVAILABLE;
import static io.harness.rule.OwnerRule.ABHISHEK;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.InstancesTestBase;
import io.harness.category.element.UnitTests;
import io.harness.dtos.InstanceDTO;
import io.harness.entities.ArtifactDetails;
import io.harness.entities.Instance;
import io.harness.entities.Instance.InstanceBuilder;
import io.harness.entities.InstanceType;
import io.harness.entities.RollbackStatus;
import io.harness.entities.instanceinfo.GitopsInstanceInfo;
import io.harness.entities.instanceinfo.K8sInstanceInfo;
import io.harness.mappers.InstanceDetailsMapper;
import io.harness.models.ActiveServiceInstanceInfoV2;
import io.harness.models.ActiveServiceInstanceInfoWithEnvType;
import io.harness.models.ArtifactDeploymentDetailModel;
import io.harness.models.BuildsByEnvironment;
import io.harness.models.CountByServiceIdAndEnvType;
import io.harness.models.EnvBuildInstanceCount;
import io.harness.models.EnvironmentInstanceCountModel;
import io.harness.models.InstanceDetailGroupedByPipelineExecutionList;
import io.harness.models.InstanceDetailsByBuildId;
import io.harness.models.InstanceDetailsDTO;
import io.harness.models.InstanceGroupedByPipelineExecution;
import io.harness.models.InstancesByBuildId;
import io.harness.models.constants.InstanceSyncConstants;
import io.harness.models.dashboard.InstanceCountDetails;
import io.harness.models.dashboard.InstanceCountDetailsByEnvTypeAndServiceId;
import io.harness.models.dashboard.InstanceCountDetailsByEnvTypeBase;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.pms.contracts.execution.Status;
import io.harness.repositories.instance.InstanceRepository;
import io.harness.rule.Owner;
import io.harness.service.instance.InstanceService;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
public class InstanceDashboardServiceImplTest extends InstancesTestBase {
  private static final String ACCOUNT_IDENTIFIER = "acc";
  private static final String PROJECT_IDENTIFIER = "proj";
  private static final String ORG_IDENTIFIER = "org";
  private static final String SERVICE_IDENTIFIER = "serv";
  private static final String ENV_IDENTIFIER = "env";
  private static final String DISPLAY_NAME = "displayName";
  private static final String ENV_1 = "env1";
  private static final String ENV_2 = "env2";
  private static final String PIPELINE_1 = "pipeline1";
  private static final String PIPELINE_2 = "pipeline2";
  private static final String PIPELINE_EXECUTION_1 = "pipelineExecution1";
  private static final String PIPELINE_EXECUTION_2 = "pipelineExecution2";
  private static final String INFRASTRUCTURE_ID = "infraId";
  private static final String CLUSTER_ID = "clusterId";
  private static final String AGENT_ID = "agentId";
  private static final String instanceKey = "instanceKey";
  private static final String infraMappingId = "infraMappingId";
  private static final String lastPipelineExecutionName = "lastPipelineExecutionName";
  private static final String lastPipelineExecutionId = "lastPipelineExecutionId";
  private static final String stageNodeExecutionId = "stageNodeExecutionId";
  private static final Status stageStatus = Status.SUCCEEDED;
  private static final String stageSetupId = "stageSetupId";
  private static final RollbackStatus rollbackStatus = RollbackStatus.NOT_STARTED;
  private static final List<String> BUILD_IDS = Arrays.asList("id1", "id2");
  private static final List<ArtifactDeploymentDetailModel> artifactDeploymentDetailModels =
      Arrays.asList(new ArtifactDeploymentDetailModel(ENV_1, DISPLAY_NAME, 2l, null, null),
          new ArtifactDeploymentDetailModel(ENV_2, DISPLAY_NAME, 1l, null, null));
  private static final List<EnvironmentInstanceCountModel> environmentInstanceCountModels =
      Arrays.asList(new EnvironmentInstanceCountModel(ENV_1, 2), new EnvironmentInstanceCountModel(ENV_2, 1));
  private final List<Instance> instanceList = Arrays.asList(Instance.builder()
                                                                .id("1")
                                                                .createdAt(1l)
                                                                .lastModifiedAt(1l)
                                                                .instanceInfo(K8sInstanceInfo.builder().build())
                                                                .build(),
      Instance.builder()
          .id("2")
          .createdAt(2l)
          .lastModifiedAt(2l)
          .instanceInfo(K8sInstanceInfo.builder().build())
          .build());

  private static final List<ActiveServiceInstanceInfoWithEnvType> activeServiceInstanceInfoWithEnvTypeList =
      Arrays.asList(new ActiveServiceInstanceInfoWithEnvType(instanceKey, infraMappingId, ENV_IDENTIFIER,
          ENV_IDENTIFIER, EnvironmentType.PreProduction, INFRASTRUCTURE_ID, INFRASTRUCTURE_ID, CLUSTER_ID, AGENT_ID, 1l,
          DISPLAY_NAME, 1, lastPipelineExecutionName, lastPipelineExecutionId, stageNodeExecutionId, stageStatus,
          stageSetupId, rollbackStatus));
  private AggregationResults<ArtifactDeploymentDetailModel> artifactDeploymentDetailModelAggregationResults;
  private AggregationResults<EnvironmentInstanceCountModel> environmentInstanceCountModelAggregationResults;
  private AggregationResults<InstanceGroupedByPipelineExecution>
      instanceDetailGroupedByPipelineExecutionAggregationResults;

  private AggregationResults<ActiveServiceInstanceInfoWithEnvType>
      activeServiceInstanceInfoWithEnvTypeAggregationResults;
  @Mock InstanceService instanceService;
  @Mock InstanceDetailsMapper instanceDetailsMapper;
  @InjectMocks InstanceDashboardServiceImpl instanceDashboardService;
  @Inject InstanceDashboardServiceImpl instanceDashboardService1;
  @Inject InstanceRepository instanceRepository;

  @Before
  public void setup() {
    artifactDeploymentDetailModelAggregationResults =
        new AggregationResults<>(artifactDeploymentDetailModels, new Document());
    environmentInstanceCountModelAggregationResults =
        new AggregationResults<>(environmentInstanceCountModels, new Document());
    InstanceGroupedByPipelineExecution instanceDetailGroupedByPipelineExecution1 =
        new InstanceGroupedByPipelineExecution(
            PIPELINE_1, PIPELINE_EXECUTION_1, 1l, null, null, null, UNAVAILABLE, instanceList);
    InstanceGroupedByPipelineExecution instanceDetailGroupedByPipelineExecution2 =
        new InstanceGroupedByPipelineExecution(
            PIPELINE_2, PIPELINE_EXECUTION_2, 2l, null, null, null, UNAVAILABLE, instanceList);
    instanceDetailGroupedByPipelineExecutionAggregationResults = new AggregationResults<>(
        Arrays.asList(instanceDetailGroupedByPipelineExecution1, instanceDetailGroupedByPipelineExecution2),
        new Document());
    activeServiceInstanceInfoWithEnvTypeAggregationResults =
        new AggregationResults<>(activeServiceInstanceInfoWithEnvTypeList, new Document());
  }

  public static List<Instance> getInstanceList() {
    List<Instance> instances = new ArrayList<>();
    InstanceBuilder instanceBuilder =
        Instance.builder()
            .accountIdentifier("accountId")
            .orgIdentifier("orgId")
            .projectIdentifier("projectId")
            .createdAt(1l)
            .lastModifiedAt(1l)
            .serviceIdentifier("svc1")
            .serviceName("svcN1")
            .envIdentifier("env1")
            .envName("env1")
            .infraIdentifier("infra1")
            .infraName("infra1")
            .instanceInfo(K8sInstanceInfo.builder().podName("infra1").namespace("infra1").build())
            .primaryArtifact(ArtifactDetails.builder().tag("1").displayName("artifact1:1").build());
    instances.add(
        instanceBuilder.lastPipelineExecutionId("1").lastPipelineExecutionName("a").lastDeployedAt(1l).build());
    instances.add(
        instanceBuilder.lastPipelineExecutionId("1").lastPipelineExecutionName("a").lastDeployedAt(2l).build());
    instances.add(
        instanceBuilder.lastPipelineExecutionId("1").lastPipelineExecutionName("a").lastDeployedAt(2l).build());
    instances.add(
        instanceBuilder.lastPipelineExecutionId("2").lastPipelineExecutionName("b").lastDeployedAt(1l).build());

    instanceBuilder.infraIdentifier("infra2").infraName("infra2").instanceInfo(
        K8sInstanceInfo.builder().podName("infra2").namespace("infra2").build());
    instances.add(
        instanceBuilder.lastPipelineExecutionId("1").lastPipelineExecutionName("a").lastDeployedAt(1l).build());
    instanceBuilder.envIdentifier("env2").envName("env2").primaryArtifact(
        ArtifactDetails.builder().tag("2").displayName("artifact2:2").build());
    instances.add(
        instanceBuilder.lastPipelineExecutionId("2").lastPipelineExecutionName("b").lastDeployedAt(1l).build());
    instanceBuilder.serviceIdentifier("svc2")
        .serviceName("svcN2")
        .envIdentifier("env1")
        .envName("env1")
        .infraIdentifier("infra1")
        .infraName("infra1")
        .instanceInfo(K8sInstanceInfo.builder().podName("infra1").namespace("infra1").build())
        .primaryArtifact(ArtifactDetails.builder().tag("1").displayName("artifact1:1").build());
    instances.add(
        instanceBuilder.lastPipelineExecutionId("1").lastPipelineExecutionName("a").lastDeployedAt(1l).build());
    instances.add(
        instanceBuilder.lastPipelineExecutionId("1").lastPipelineExecutionName("a").lastDeployedAt(2l).build());
    instances.add(
        instanceBuilder.lastPipelineExecutionId("1").lastPipelineExecutionName("a").lastDeployedAt(2l).build());
    instances.add(
        instanceBuilder.lastPipelineExecutionId("2").lastPipelineExecutionName("b").lastDeployedAt(1l).build());
    instanceBuilder.infraIdentifier("infra2").infraName("infra2").instanceInfo(
        K8sInstanceInfo.builder().podName("infra2").namespace("infra2").build());
    instances.add(
        instanceBuilder.lastPipelineExecutionId("1").lastPipelineExecutionName("a").lastDeployedAt(1l).build());
    instanceBuilder.envIdentifier("env2").envName("env2").primaryArtifact(
        ArtifactDetails.builder().tag("2").displayName("artifact2:2").build());
    instances.add(
        instanceBuilder.lastPipelineExecutionId("2").lastPipelineExecutionName("b").lastDeployedAt(1l).build());

    instanceBuilder.serviceIdentifier("svc1")
        .serviceName("svcN1")
        .envIdentifier("env1")
        .envName("env1")
        .infraIdentifier(null)
        .infraName(null)
        .instanceInfo(GitopsInstanceInfo.builder()
                          .clusterIdentifier("infra1")
                          .agentIdentifier("infra1")
                          .podName("infra1")
                          .build())
        .primaryArtifact(ArtifactDetails.builder().tag("1").displayName("artifact1:1").build());
    instances.add(
        instanceBuilder.lastPipelineExecutionId("1").lastPipelineExecutionName("a").lastDeployedAt(1l).build());
    instances.add(
        instanceBuilder.lastPipelineExecutionId("1").lastPipelineExecutionName("a").lastDeployedAt(2l).build());
    instances.add(
        instanceBuilder.lastPipelineExecutionId("1").lastPipelineExecutionName("a").lastDeployedAt(2l).build());
    instances.add(
        instanceBuilder.lastPipelineExecutionId("2").lastPipelineExecutionName("b").lastDeployedAt(1l).build());

    instanceBuilder.instanceInfo(
        GitopsInstanceInfo.builder().clusterIdentifier("infra2").agentIdentifier("infra2").podName("infra2").build());
    instances.add(
        instanceBuilder.lastPipelineExecutionId("1").lastPipelineExecutionName("a").lastDeployedAt(1l).build());
    instanceBuilder.envIdentifier("env2").envName("env2").primaryArtifact(
        ArtifactDetails.builder().tag("2").displayName("artifact2:2").build());
    instances.add(
        instanceBuilder.lastPipelineExecutionId("2").lastPipelineExecutionName("b").lastDeployedAt(1l).build());
    instanceBuilder.serviceIdentifier("svc2")
        .serviceName("svcN2")
        .envIdentifier("env1")
        .envName("env1")
        .primaryArtifact(ArtifactDetails.builder().tag("1").displayName("artifact1:1").build())
        .instanceInfo(GitopsInstanceInfo.builder()
                          .clusterIdentifier("infra1")
                          .agentIdentifier("infra1")
                          .podName("infra1")
                          .build());
    instances.add(
        instanceBuilder.lastPipelineExecutionId("1").lastPipelineExecutionName("a").lastDeployedAt(1l).build());
    instances.add(
        instanceBuilder.lastPipelineExecutionId("1").lastPipelineExecutionName("a").lastDeployedAt(2l).build());
    instances.add(
        instanceBuilder.lastPipelineExecutionId("1").lastPipelineExecutionName("a").lastDeployedAt(2l).build());
    instances.add(
        instanceBuilder.lastPipelineExecutionId("2").lastPipelineExecutionName("b").lastDeployedAt(1l).build());
    instanceBuilder.instanceInfo(
        GitopsInstanceInfo.builder().clusterIdentifier("infra2").agentIdentifier("infra2").podName("infra2").build());
    instances.add(
        instanceBuilder.lastPipelineExecutionId("1").lastPipelineExecutionName("a").lastDeployedAt(1l).build());
    instanceBuilder.envIdentifier("env2").envName("env2").primaryArtifact(
        ArtifactDetails.builder().tag("2").displayName("artifact2:2").build());
    instances.add(
        instanceBuilder.lastPipelineExecutionId("2").lastPipelineExecutionName("b").lastDeployedAt(1l).build());
    return instances;
  }
  public void activateInstances() {
    for (Instance instance : getInstanceList()) {
      instanceRepository.save(instance);
    }
  }

  List<ActiveServiceInstanceInfoV2> getSampleListActiveServiceInstanceInfo(String serviceId, String serviceName) {
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfo = new ArrayList<>();
    ActiveServiceInstanceInfoV2 instance1 = new ActiveServiceInstanceInfoV2(
        serviceId, serviceName, "env1", "env1", "infra1", "infra1", null, null, "1", "a", 1l, "1", "artifact1:1", 1);
    activeServiceInstanceInfo.add(instance1);
    instance1 = new ActiveServiceInstanceInfoV2(
        serviceId, serviceName, "env1", "env1", "infra1", "infra1", null, null, "1", "a", 2l, "1", "artifact1:1", 2);
    activeServiceInstanceInfo.add(instance1);
    instance1 = new ActiveServiceInstanceInfoV2(
        serviceId, serviceName, "env1", "env1", "infra1", "infra1", null, null, "2", "b", 1l, "1", "artifact1:1", 1);
    activeServiceInstanceInfo.add(instance1);
    instance1 = new ActiveServiceInstanceInfoV2(
        serviceId, serviceName, "env1", "env1", "infra2", "infra2", null, null, "1", "a", 1l, "1", "artifact1:1", 1);
    activeServiceInstanceInfo.add(instance1);
    return activeServiceInstanceInfo;
  }

  List<ActiveServiceInstanceInfoV2> getSampleListActiveServiceInstanceInfoEnv2(String serviceId, String serviceName) {
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfo = new ArrayList<>();
    ActiveServiceInstanceInfoV2 instance1 = new ActiveServiceInstanceInfoV2(
        serviceId, serviceName, "env2", "env2", "infra2", "infra2", null, null, "2", "b", 1l, "2", "artifact2:2", 1);
    activeServiceInstanceInfo.add(instance1);
    return activeServiceInstanceInfo;
  }

  List<ActiveServiceInstanceInfoV2> getSampleListActiveServiceInstanceInfoGitOps(String serviceId, String serviceName) {
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfo = new ArrayList<>();
    ActiveServiceInstanceInfoV2 instance1 = new ActiveServiceInstanceInfoV2(
        serviceId, serviceName, "env1", "env1", null, null, "infra1", "infra1", "1", "a", 1l, "1", "artifact1:1", 1);
    activeServiceInstanceInfo.add(instance1);
    instance1 = new ActiveServiceInstanceInfoV2(
        serviceId, serviceName, "env1", "env1", null, null, "infra1", "infra1", "1", "a", 2l, "1", "artifact1:1", 2);
    activeServiceInstanceInfo.add(instance1);
    instance1 = new ActiveServiceInstanceInfoV2(
        serviceId, serviceName, "env1", "env1", null, null, "infra1", "infra1", "2", "b", 1l, "1", "artifact1:1", 1);
    activeServiceInstanceInfo.add(instance1);
    instance1 = new ActiveServiceInstanceInfoV2(
        serviceId, serviceName, "env1", "env1", null, null, "infra2", "infra2", "1", "a", 1l, "1", "artifact1:1", 1);
    activeServiceInstanceInfo.add(instance1);
    return activeServiceInstanceInfo;
  }

  List<ActiveServiceInstanceInfoV2> getSampleListActiveServiceInstanceInfoGitOpsEnv2(
      String serviceId, String serviceName) {
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfo = new ArrayList<>();
    ActiveServiceInstanceInfoV2 instance1 = new ActiveServiceInstanceInfoV2(
        serviceId, serviceName, "env2", "env2", null, null, "infra2", "infra2", "2", "b", 1l, "2", "artifact2:2", 1);
    activeServiceInstanceInfo.add(instance1);
    return activeServiceInstanceInfo;
  }

  List<InstanceDetailGroupedByPipelineExecutionList.InstanceDetailGroupedByPipelineExecution>
  getSampleInstanceDetailGroupedByPipelineExecutionList() {
    List<InstanceDetailsDTO> instanceDetailsDTOList = Arrays.asList(
        InstanceDetailsDTO.builder().podName("1").build(), InstanceDetailsDTO.builder().podName("2").build());
    InstanceDetailGroupedByPipelineExecutionList
        .InstanceDetailGroupedByPipelineExecution instanceDetailGroupedByPipelineExecution1 =
        InstanceDetailGroupedByPipelineExecutionList.InstanceDetailGroupedByPipelineExecution.builder()
            .pipelineId(PIPELINE_1)
            .planExecutionId(PIPELINE_EXECUTION_1)
            .lastDeployedAt(1l)
            .instances(instanceDetailsDTOList)
            .build();
    InstanceDetailGroupedByPipelineExecutionList
        .InstanceDetailGroupedByPipelineExecution instanceDetailGroupedByPipelineExecution2 =
        InstanceDetailGroupedByPipelineExecutionList.InstanceDetailGroupedByPipelineExecution.builder()
            .pipelineId(PIPELINE_2)
            .planExecutionId(PIPELINE_EXECUTION_2)
            .lastDeployedAt(2l)
            .instances(instanceDetailsDTOList)
            .build();
    return Arrays.asList(instanceDetailGroupedByPipelineExecution1, instanceDetailGroupedByPipelineExecution2);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getActiveInstanceCountDetailsByEnvTypeTest() {
    InstanceDTO instanceDTO = InstanceDTO.builder()
                                  .serviceIdentifier(SERVICE_IDENTIFIER)
                                  .envIdentifier(ENV_IDENTIFIER)
                                  .envType(EnvironmentType.Production)
                                  .build();
    InstanceDTO instanceDTO1 = InstanceDTO.builder()
                                   .serviceIdentifier(SERVICE_IDENTIFIER)
                                   .envIdentifier(ENV_IDENTIFIER)
                                   .envType(EnvironmentType.PreProduction)
                                   .build();
    InstanceDTO instanceDTO2 = InstanceDTO.builder()
                                   .serviceIdentifier(SERVICE_IDENTIFIER + "2")
                                   .envIdentifier(ENV_IDENTIFIER)
                                   .envType(EnvironmentType.Production)
                                   .build();
    InstanceDTO instanceDTO3 = InstanceDTO.builder()
                                   .serviceIdentifier(SERVICE_IDENTIFIER + "2")
                                   .envIdentifier(ENV_IDENTIFIER)
                                   .envType(EnvironmentType.PreProduction)
                                   .build();
    InstanceDTO instanceDTO4 = InstanceDTO.builder()
                                   .serviceIdentifier(SERVICE_IDENTIFIER + "2")
                                   .envIdentifier(ENV_IDENTIFIER + "2")
                                   .envType(EnvironmentType.Production)
                                   .build();
    when(instanceService.getActiveInstances(anyString(), anyString(), anyString(), anyLong()))
        .thenReturn(Arrays.asList(instanceDTO, instanceDTO1, instanceDTO2, instanceDTO3, instanceDTO4));

    InstanceCountDetails instanceCountDetails = instanceDashboardService.getActiveInstanceCountDetailsByEnvType(
        ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    assertThat(instanceCountDetails.getProdInstances()).isEqualTo(3);
    assertThat(instanceCountDetails.getNonProdInstances()).isEqualTo(2);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getActiveInstancesByServiceIdGroupedByEnvironmentAndBuildTest() {
    InstanceDTO instanceDTO = InstanceDTO.builder()
                                  .serviceIdentifier(SERVICE_IDENTIFIER)
                                  .envIdentifier(ENV_IDENTIFIER)
                                  .primaryArtifact(ArtifactDetails.builder().tag("tag1").build())
                                  .envType(EnvironmentType.Production)
                                  .build();
    InstanceDTO instanceDTO1 = InstanceDTO.builder()
                                   .serviceIdentifier(SERVICE_IDENTIFIER)
                                   .envIdentifier(ENV_IDENTIFIER)
                                   .primaryArtifact(ArtifactDetails.builder().tag("tag1").build())
                                   .envType(EnvironmentType.PreProduction)
                                   .build();
    InstanceDTO instanceDTO2 = InstanceDTO.builder()
                                   .serviceIdentifier(SERVICE_IDENTIFIER + "2")
                                   .envIdentifier(ENV_IDENTIFIER)
                                   .primaryArtifact(ArtifactDetails.builder().tag("tag2").build())
                                   .envType(EnvironmentType.Production)
                                   .build();
    InstanceDTO instanceDTO3 = InstanceDTO.builder()
                                   .serviceIdentifier(SERVICE_IDENTIFIER + "2")
                                   .envIdentifier(ENV_IDENTIFIER)
                                   .primaryArtifact(ArtifactDetails.builder().tag("tag2").build())
                                   .envType(EnvironmentType.PreProduction)
                                   .build();
    InstanceDTO instanceDTO4 = InstanceDTO.builder()
                                   .serviceIdentifier(SERVICE_IDENTIFIER + "2")
                                   .envIdentifier(ENV_IDENTIFIER + "2")
                                   .primaryArtifact(ArtifactDetails.builder().tag("tag3").build())
                                   .envType(EnvironmentType.Production)
                                   .build();
    when(instanceService.getActiveInstancesByServiceId(anyString(), anyString(), anyString(), anyString(), anyLong()))
        .thenReturn(Arrays.asList(instanceDTO, instanceDTO1, instanceDTO2, instanceDTO3, instanceDTO4));

    List<BuildsByEnvironment> buildsByEnvironmentList =
        instanceDashboardService.getActiveInstancesByServiceIdGroupedByEnvironmentAndBuild(
            ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SERVICE_IDENTIFIER, 10);
    assertThat(buildsByEnvironmentList.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getEnvBuildInstanceCountByServiceIdTest() {
    EnvBuildInstanceCount envBuildInstanceCount = new EnvBuildInstanceCount(ENV_IDENTIFIER, "ENV", "TAG", 3);
    AggregationResults<EnvBuildInstanceCount> envBuildInstanceCountAggregationResults =
        new AggregationResults<>(Arrays.asList(envBuildInstanceCount), new Document());
    when(instanceService.getEnvBuildInstanceCountByServiceId(
             anyString(), anyString(), anyString(), anyString(), anyLong()))
        .thenReturn(envBuildInstanceCountAggregationResults);
    assertThat(instanceDashboardService
                   .getEnvBuildInstanceCountByServiceId(
                       ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SERVICE_IDENTIFIER, 10)
                   .size())
        .isEqualTo(1);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getActiveInstancesByServiceIdEnvIdAndBuildIdsTest() {
    String infraId = "infraId";
    String clusterId = "clusterId";
    String pipelineExecutionId = "pipelineExecutionId";
    long lastDeployedAt = System.currentTimeMillis();
    Instance instance = Instance.builder()
                            .accountIdentifier(ACCOUNT_IDENTIFIER)
                            .projectIdentifier(PROJECT_IDENTIFIER)
                            .orgIdentifier(ORG_IDENTIFIER)
                            .serviceIdentifier(SERVICE_IDENTIFIER)
                            .envIdentifier(ENV_IDENTIFIER)
                            .envName("env1")
                            .envType(EnvironmentType.Production)
                            .instanceKey("key")
                            .connectorRef("connector")
                            .id("id")
                            .createdAt(5L)
                            .deletedAt(10)
                            .instanceType(InstanceType.K8S_INSTANCE)
                            .infrastructureMappingId("mappingId")
                            .lastDeployedAt(3)
                            .lastDeployedByName("asdf")
                            .lastPipelineExecutionId("sdf")
                            .lastPipelineExecutionName("sdfasd")
                            .serviceName("serv")
                            .isDeleted(false)
                            .instanceInfo(K8sInstanceInfo.builder().build())
                            .lastModifiedAt(10L)
                            .build();
    InstancesByBuildId instanceDetailsByBuildId = new InstancesByBuildId("build1", Arrays.asList(instance));
    AggregationResults<InstancesByBuildId> instanceDetailsByBuildIdAggregationResults =
        new AggregationResults<>(Arrays.asList(instanceDetailsByBuildId), new Document());

    when(instanceService.getActiveInstancesByServiceIdEnvIdAndBuildIds(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER,
             PROJECT_IDENTIFIER, SERVICE_IDENTIFIER, ENV_IDENTIFIER, BUILD_IDS, 10,
             InstanceSyncConstants.INSTANCE_LIMIT, infraId, clusterId, pipelineExecutionId))
        .thenReturn(instanceDetailsByBuildIdAggregationResults);
    List<InstanceDetailsByBuildId> instanceDetailsByBuildIdList =
        instanceDashboardService.getActiveInstancesByServiceIdEnvIdAndBuildIds(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER,
            PROJECT_IDENTIFIER, SERVICE_IDENTIFIER, ENV_IDENTIFIER, BUILD_IDS, 10, infraId, clusterId,
            pipelineExecutionId, false);
    assertThat(instanceDetailsByBuildIdList.size()).isEqualTo(1);
    assertThat(instanceDetailsByBuildIdList.get(0).getBuildId()).isEqualTo("build1");
    assertThat(instanceDetailsByBuildIdList.get(0).getInstances().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getActiveServiceInstanceCountBreakdownTest() {
    CountByServiceIdAndEnvType countByServiceIdAndEnvType =
        new CountByServiceIdAndEnvType(SERVICE_IDENTIFIER, EnvironmentType.Production, 2);
    AggregationResults<CountByServiceIdAndEnvType> countByServiceIdAndEnvTypeAggregationResults =
        new AggregationResults<>(Arrays.asList(countByServiceIdAndEnvType), new Document());
    when(instanceService.getActiveServiceInstanceCountBreakdown(
             ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, Arrays.asList(SERVICE_IDENTIFIER), 10))
        .thenReturn(countByServiceIdAndEnvTypeAggregationResults);
    Map<EnvironmentType, Integer> envTypeVsInstanceCountMap = new HashMap<>();
    envTypeVsInstanceCountMap.put(EnvironmentType.Production, 2);
    Map<String, InstanceCountDetailsByEnvTypeBase> instanceCountDetailsByEnvTypeBaseMap = new HashMap<>();
    instanceCountDetailsByEnvTypeBaseMap.put(SERVICE_IDENTIFIER,
        InstanceCountDetailsByEnvTypeBase.builder().envTypeVsInstanceCountMap(envTypeVsInstanceCountMap).build());
    InstanceCountDetailsByEnvTypeAndServiceId instanceCountDetailsByEnvTypeAndServiceId =
        instanceDashboardService.getActiveServiceInstanceCountBreakdown(
            ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, Arrays.asList(SERVICE_IDENTIFIER), 10);
    assertThat(instanceCountDetailsByEnvTypeAndServiceId.getInstanceCountDetailsByEnvTypeBaseMap().containsKey(
                   SERVICE_IDENTIFIER))
        .isTrue();
    assertThat(instanceCountDetailsByEnvTypeAndServiceId.getInstanceCountDetailsByEnvTypeBaseMap()
                   .get(SERVICE_IDENTIFIER)
                   .getProdInstances())
        .isEqualTo(2);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void getActiveServiceInstanceInfo_filterServiceId() {
    activateInstances();
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfoV2List1 =
        getSampleListActiveServiceInstanceInfo("svc1", "svcN1");
    activeServiceInstanceInfoV2List1.addAll(getSampleListActiveServiceInstanceInfoEnv2("svc1", "svcN1"));
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfoV2List =
        instanceDashboardService1.getActiveServiceInstanceInfo(
            "accountId", "orgId", "projectId", null, "svc1", null, false);
    assertThat(activeServiceInstanceInfoV2List1).isEqualTo(activeServiceInstanceInfoV2List);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void getActiveServiceInstanceInfo_filterServiceIdEnv() {
    activateInstances();
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfoV2List1 =
        getSampleListActiveServiceInstanceInfo("svc1", "svcN1");
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfoV2List =
        instanceDashboardService1.getActiveServiceInstanceInfo(
            "accountId", "orgId", "projectId", "env1", "svc1", null, false);
    assertThat(activeServiceInstanceInfoV2List1).isEqualTo(activeServiceInstanceInfoV2List);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void getActiveServiceInstanceInfo_filterServiceId_GitOps() {
    activateInstances();
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfoV2List1 =
        getSampleListActiveServiceInstanceInfoGitOps("svc1", "svcN1");
    activeServiceInstanceInfoV2List1.addAll(getSampleListActiveServiceInstanceInfoGitOpsEnv2("svc1", "svcN1"));
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfoV2List =
        instanceDashboardService1.getActiveServiceInstanceInfo(
            "accountId", "orgId", "projectId", null, "svc1", null, true);
    assertThat(activeServiceInstanceInfoV2List1).isEqualTo(activeServiceInstanceInfoV2List);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void getActiveServiceInstanceInfo_filterServiceIdEnv_GitOps() {
    activateInstances();
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfoV2List1 =
        getSampleListActiveServiceInstanceInfoGitOps("svc1", "svcN1");
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfoV2List =
        instanceDashboardService1.getActiveServiceInstanceInfo(
            "accountId", "orgId", "projectId", "env1", "svc1", null, true);
    assertThat(activeServiceInstanceInfoV2List1).isEqualTo(activeServiceInstanceInfoV2List);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void getActiveServiceInstanceInfo() {
    activateInstances();
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfoV2List1 =
        getSampleListActiveServiceInstanceInfo("svc1", "svcN1");
    activeServiceInstanceInfoV2List1.addAll(getSampleListActiveServiceInstanceInfoEnv2("svc1", "svcN1"));
    activeServiceInstanceInfoV2List1.addAll(getSampleListActiveServiceInstanceInfo("svc2", "svcN2"));
    activeServiceInstanceInfoV2List1.addAll(getSampleListActiveServiceInstanceInfoEnv2("svc2", "svcN2"));
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfoV2List =
        instanceDashboardService1.getActiveServiceInstanceInfo(
            "accountId", "orgId", "projectId", null, null, null, false);
    assertThat(activeServiceInstanceInfoV2List1).isEqualTo(activeServiceInstanceInfoV2List);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void getActiveServiceInstanceInfo_filterEnv() {
    activateInstances();
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfoV2List1 =
        getSampleListActiveServiceInstanceInfo("svc1", "svcN1");
    activeServiceInstanceInfoV2List1.addAll(getSampleListActiveServiceInstanceInfo("svc2", "svcN2"));
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfoV2List =
        instanceDashboardService1.getActiveServiceInstanceInfo(
            "accountId", "orgId", "projectId", "env1", null, null, false);
    assertThat(activeServiceInstanceInfoV2List1).isEqualTo(activeServiceInstanceInfoV2List);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void getActiveServiceInstanceInfo_GitOps() {
    activateInstances();
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfoV2List1 =
        getSampleListActiveServiceInstanceInfoGitOps("svc1", "svcN1");
    activeServiceInstanceInfoV2List1.addAll(getSampleListActiveServiceInstanceInfoGitOpsEnv2("svc1", "svcN1"));
    activeServiceInstanceInfoV2List1.addAll(getSampleListActiveServiceInstanceInfoGitOps("svc2", "svcN2"));
    activeServiceInstanceInfoV2List1.addAll(getSampleListActiveServiceInstanceInfoGitOpsEnv2("svc2", "svcN2"));
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfoV2List =
        instanceDashboardService1.getActiveServiceInstanceInfo(
            "accountId", "orgId", "projectId", null, null, null, true);
    assertThat(activeServiceInstanceInfoV2List1).isEqualTo(activeServiceInstanceInfoV2List);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void getActiveServiceInstanceInfo_GitOps_filterEnv() {
    activateInstances();
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfoV2List1 =
        getSampleListActiveServiceInstanceInfoGitOps("svc1", "svcN1");
    activeServiceInstanceInfoV2List1.addAll(getSampleListActiveServiceInstanceInfoGitOps("svc2", "svcN2"));
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfoV2List =
        instanceDashboardService1.getActiveServiceInstanceInfo(
            "accountId", "orgId", "projectId", "env1", null, null, true);
    assertThat(activeServiceInstanceInfoV2List1).isEqualTo(activeServiceInstanceInfoV2List);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_getActiveInstanceDetails_infra() {
    InstanceDetailsDTO instanceDetailsDTO1 = InstanceDetailsDTO.builder().build();
    InstanceDetailsDTO instanceDetailsDTO2 = InstanceDetailsDTO.builder().build();
    when(instanceDetailsMapper.toInstanceDetailsDTOList(anyList(), anyBoolean()))
        .thenReturn(Arrays.asList(instanceDetailsDTO1, instanceDetailsDTO2));

    InstanceDetailsByBuildId instanceDetailsByBuildId = instanceDashboardService.getActiveInstanceDetails(
        "accountId", "orgId", "projectId", "svc1", "env1", "infra1", null, "1", "1", false);

    verify(instanceService)
        .getActiveInstanceDetails("accountId", "orgId", "projectId", "svc1", "env1", "infra1", null, "1", "1",
            InstanceSyncConstants.INSTANCE_LIMIT);
    verify(instanceDetailsMapper).toInstanceDetailsDTOList(anyList(), anyBoolean());

    assertThat(instanceDetailsByBuildId.getInstances().get(0)).isEqualTo(instanceDetailsDTO1);
    assertThat(instanceDetailsByBuildId.getInstances().get(1)).isEqualTo(instanceDetailsDTO2);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_getActiveInstanceDetails_cluster() {
    InstanceDetailsDTO instanceDetailsDTO1 = InstanceDetailsDTO.builder().build();
    InstanceDetailsDTO instanceDetailsDTO2 = InstanceDetailsDTO.builder().build();
    when(instanceDetailsMapper.toInstanceDetailsDTOList(anyList(), anyBoolean()))
        .thenReturn(Arrays.asList(instanceDetailsDTO1, instanceDetailsDTO2));

    InstanceDetailsByBuildId instanceDetailsByBuildId = instanceDashboardService.getActiveInstanceDetails(
        "accountId", "orgId", "projectId", "svc1", "env1", null, "cluster1", "1", "1", false);

    verify(instanceService)
        .getActiveInstanceDetails("accountId", "orgId", "projectId", "svc1", "env1", null, "cluster1", "1", "1",
            InstanceSyncConstants.INSTANCE_LIMIT);
    verify(instanceDetailsMapper).toInstanceDetailsDTOList(anyList(), anyBoolean());

    assertThat(instanceDetailsByBuildId.getInstances().get(0)).isEqualTo(instanceDetailsDTO1);
    assertThat(instanceDetailsByBuildId.getInstances().get(1)).isEqualTo(instanceDetailsDTO2);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_getLastDeployedInstance_environmentCard_nonGitOps() {
    when(instanceService.getLastDeployedInstance(
             ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SERVICE_IDENTIFIER, true, false))
        .thenReturn(artifactDeploymentDetailModelAggregationResults);

    List<ArtifactDeploymentDetailModel> artifactDeploymentDetailModels1 =
        instanceDashboardService.getLastDeployedInstance(
            ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SERVICE_IDENTIFIER, true, false);

    verify(instanceService)
        .getLastDeployedInstance(
            ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SERVICE_IDENTIFIER, true, false);
    assertThat(artifactDeploymentDetailModels).isEqualTo(artifactDeploymentDetailModels1);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_getLastDeployedInstance_environmentCard_gitOps() {
    when(instanceService.getLastDeployedInstance(
             ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SERVICE_IDENTIFIER, true, true))
        .thenReturn(artifactDeploymentDetailModelAggregationResults);

    List<ArtifactDeploymentDetailModel> artifactDeploymentDetailModels1 =
        instanceDashboardService.getLastDeployedInstance(
            ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SERVICE_IDENTIFIER, true, true);

    verify(instanceService)
        .getLastDeployedInstance(
            ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SERVICE_IDENTIFIER, true, true);
    assertThat(artifactDeploymentDetailModels).isEqualTo(artifactDeploymentDetailModels1);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_getLastDeployedInstance_notEnvironmentCard_nonGitOps() {
    when(instanceService.getLastDeployedInstance(
             ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SERVICE_IDENTIFIER, false, false))
        .thenReturn(artifactDeploymentDetailModelAggregationResults);

    List<ArtifactDeploymentDetailModel> artifactDeploymentDetailModels1 =
        instanceDashboardService.getLastDeployedInstance(
            ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SERVICE_IDENTIFIER, false, false);

    verify(instanceService)
        .getLastDeployedInstance(
            ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SERVICE_IDENTIFIER, false, false);
    assertThat(artifactDeploymentDetailModels).isEqualTo(artifactDeploymentDetailModels1);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_getLastDeployedInstance_notEnvironmentCard_gitOps() {
    when(instanceService.getLastDeployedInstance(
             ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SERVICE_IDENTIFIER, false, true))
        .thenReturn(artifactDeploymentDetailModelAggregationResults);

    List<ArtifactDeploymentDetailModel> artifactDeploymentDetailModels1 =
        instanceDashboardService.getLastDeployedInstance(
            ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SERVICE_IDENTIFIER, false, true);

    verify(instanceService)
        .getLastDeployedInstance(
            ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SERVICE_IDENTIFIER, false, true);
    assertThat(artifactDeploymentDetailModels).isEqualTo(artifactDeploymentDetailModels1);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_getInstanceCountForEnvironmentFilteredByService_nonGitOps() {
    when(instanceService.getInstanceCountForEnvironmentFilteredByService(
             ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SERVICE_IDENTIFIER, false))
        .thenReturn(environmentInstanceCountModelAggregationResults);

    List<EnvironmentInstanceCountModel> environmentInstanceCountModels1 =
        instanceDashboardService.getInstanceCountForEnvironmentFilteredByService(
            ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SERVICE_IDENTIFIER, false);

    verify(instanceService)
        .getInstanceCountForEnvironmentFilteredByService(
            ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SERVICE_IDENTIFIER, false);
    assertThat(environmentInstanceCountModels).isEqualTo(environmentInstanceCountModels1);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_getInstanceCountForEnvironmentFilteredByService_gitOps() {
    when(instanceService.getInstanceCountForEnvironmentFilteredByService(
             ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SERVICE_IDENTIFIER, true))
        .thenReturn(environmentInstanceCountModelAggregationResults);

    List<EnvironmentInstanceCountModel> environmentInstanceCountModels1 =
        instanceDashboardService.getInstanceCountForEnvironmentFilteredByService(
            ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SERVICE_IDENTIFIER, true);

    verify(instanceService)
        .getInstanceCountForEnvironmentFilteredByService(
            ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SERVICE_IDENTIFIER, true);
    assertThat(environmentInstanceCountModels).isEqualTo(environmentInstanceCountModels1);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_getActiveServiceInstanceInfoWithEnvType_NonGitOps() {
    when(instanceService.getActiveServiceInstanceInfoWithEnvType(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER,
             ENV_IDENTIFIER, SERVICE_IDENTIFIER, DISPLAY_NAME, false, true))
        .thenReturn(activeServiceInstanceInfoWithEnvTypeAggregationResults);

    List<ActiveServiceInstanceInfoWithEnvType> activeServiceInstanceInfoWithEnvTypeList1 =
        instanceDashboardService.getActiveServiceInstanceInfoWithEnvType(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER,
            PROJECT_IDENTIFIER, ENV_IDENTIFIER, SERVICE_IDENTIFIER, DISPLAY_NAME, false, true);

    verify(instanceService)
        .getActiveServiceInstanceInfoWithEnvType(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, ENV_IDENTIFIER,
            SERVICE_IDENTIFIER, DISPLAY_NAME, false, true);
    assertThat(activeServiceInstanceInfoWithEnvTypeList).isEqualTo(activeServiceInstanceInfoWithEnvTypeList1);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_getActiveServiceInstanceInfoWithEnvType_GitOps() {
    when(instanceService.getActiveServiceInstanceInfoWithEnvType(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER,
             ENV_IDENTIFIER, SERVICE_IDENTIFIER, DISPLAY_NAME, true, true))
        .thenReturn(activeServiceInstanceInfoWithEnvTypeAggregationResults);

    List<ActiveServiceInstanceInfoWithEnvType> activeServiceInstanceInfoWithEnvTypeList1 =
        instanceDashboardService.getActiveServiceInstanceInfoWithEnvType(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER,
            PROJECT_IDENTIFIER, ENV_IDENTIFIER, SERVICE_IDENTIFIER, DISPLAY_NAME, true, true);

    verify(instanceService)
        .getActiveServiceInstanceInfoWithEnvType(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, ENV_IDENTIFIER,
            SERVICE_IDENTIFIER, DISPLAY_NAME, true, true);
    assertThat(activeServiceInstanceInfoWithEnvTypeList).isEqualTo(activeServiceInstanceInfoWithEnvTypeList1);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_getActiveInstanceDetailGroupedByPipelineExecution_NonGitOps() {
    List<InstanceDetailGroupedByPipelineExecutionList.InstanceDetailGroupedByPipelineExecution>
        instanceDetailGroupedByPipelineExecutionList = getSampleInstanceDetailGroupedByPipelineExecutionList();
    when(instanceService.getActiveInstanceGroupedByPipelineExecution(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER,
             PROJECT_IDENTIFIER, SERVICE_IDENTIFIER, ENV_IDENTIFIER, EnvironmentType.Production, INFRASTRUCTURE_ID,
             null, DISPLAY_NAME))
        .thenReturn(instanceDetailGroupedByPipelineExecutionAggregationResults);
    when(instanceDetailsMapper.toInstanceDetailsDTOList(anyList(), anyBoolean()))
        .thenReturn(instanceDetailGroupedByPipelineExecutionList.get(0).getInstances());
    List<InstanceDetailGroupedByPipelineExecutionList.InstanceDetailGroupedByPipelineExecution>
        instanceDetailGroupedByPipelineExecutionListResult =
            instanceDashboardService.getActiveInstanceDetailGroupedByPipelineExecution(ACCOUNT_IDENTIFIER,
                ORG_IDENTIFIER, PROJECT_IDENTIFIER, SERVICE_IDENTIFIER, ENV_IDENTIFIER, EnvironmentType.Production,
                INFRASTRUCTURE_ID, null, DISPLAY_NAME, false);
    assertThat(instanceDetailGroupedByPipelineExecutionList)
        .isEqualTo(instanceDetailGroupedByPipelineExecutionListResult);
  }
}
