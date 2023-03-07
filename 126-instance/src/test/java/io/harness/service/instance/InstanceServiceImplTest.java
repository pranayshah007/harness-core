/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.instance;

import static io.harness.rule.OwnerRule.ABHISHEK;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;
import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.InstancesTestBase;
import io.harness.category.element.UnitTests;
import io.harness.dtos.InstanceDTO;
import io.harness.dtos.instanceinfo.InstanceInfoDTO;
import io.harness.dtos.instanceinfo.K8sInstanceInfoDTO;
import io.harness.entities.Instance;
import io.harness.entities.Instance.InstanceKeys;
import io.harness.entities.instanceinfo.GitopsInstanceInfo;
import io.harness.entities.instanceinfo.InstanceInfo;
import io.harness.entities.instanceinfo.K8sInstanceInfo;
import io.harness.models.ActiveServiceInstanceInfoWithEnvType;
import io.harness.models.CountByServiceIdAndEnvType;
import io.harness.models.EnvBuildInstanceCount;
import io.harness.models.InstancesByBuildId;
import io.harness.models.constants.InstanceSyncConstants;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.repositories.instance.InstanceRepository;
import io.harness.rule.Owner;
import io.harness.service.instancedashboardservice.InstanceDashboardServiceImplTest;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;

public class InstanceServiceImplTest extends InstancesTestBase {
  private final String INSTANCE_KEY = "instance_key";
  @Mock InstanceRepository instanceRepository;
  @InjectMocks InstanceServiceImpl instanceService;
  @Captor ArgumentCaptor<String> instanceIdCapture;
  @Inject InstanceRepository instanceRepository1;
  @Inject InstanceServiceImpl instanceService1;

  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String SERVICE_ID = "serviceId";
  private static final String ENVIRONMENT_ID = "environmentId";
  private static final String DISPLAY_NAME = "artifact:tag";
  private static final String INFRASTRUCTURE_ID = "infraId";
  private static final String CLUSTER_ID = "clusterId";
  private static final String AGENT_ID = "agentId";

  private AggregationResults<ActiveServiceInstanceInfoWithEnvType> aggregationResults;

  @Before
  public void setup() {
    aggregationResults = new AggregationResults<>(Arrays.asList(new ActiveServiceInstanceInfoWithEnvType(ENVIRONMENT_ID,
                                                      ENVIRONMENT_ID, EnvironmentType.PreProduction, INFRASTRUCTURE_ID,
                                                      INFRASTRUCTURE_ID, CLUSTER_ID, AGENT_ID, 1l, DISPLAY_NAME, 1)),
        new Document());
  }

  public void activateInstances() {
    for (Instance instance : InstanceDashboardServiceImplTest.getInstanceList()) {
      instanceRepository1.save(instance);
    }
  }

  public static boolean checkInstanceEquality(Instance instance1, Instance instance2) {
    if (((instance1.getInfraIdentifier() == null && instance2.getInfraIdentifier() == null)
            || instance1.getInfraIdentifier().equals(instance2.getInfraIdentifier()))
        && instance1.getEnvIdentifier().equals(instance2.getEnvIdentifier())
        && instance1.getServiceIdentifier().equals(instance2.getServiceIdentifier())
        && instance1.getPrimaryArtifact().getTag().equals(instance2.getPrimaryArtifact().getTag())
        && instance1.getLastPipelineExecutionId().equals(instance2.getLastPipelineExecutionId())) {
      if (((instance1.getInstanceInfo() instanceof GitopsInstanceInfo)
              && (instance1.getInstanceInfo() instanceof GitopsInstanceInfo))
          || (!(instance1.getInstanceInfo() instanceof GitopsInstanceInfo)
              && !(instance1.getInstanceInfo() instanceof GitopsInstanceInfo))) {
        if (instance1.getInstanceInfo() instanceof GitopsInstanceInfo) {
          GitopsInstanceInfo instanceInfo1 = (GitopsInstanceInfo) instance1.getInstanceInfo();
          GitopsInstanceInfo instanceInfo2 = (GitopsInstanceInfo) instance2.getInstanceInfo();
          if (!instanceInfo1.getClusterIdentifier().equals(instanceInfo2.getClusterIdentifier())
              || !instanceInfo1.getAgentIdentifier().equals(instanceInfo2.getAgentIdentifier())) {
            return false;
          }
        }
        return true;
      } else {
        return false;
      }
    }
    return false;
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void saveTest() {
    InstanceInfoDTO instanceInfoDTO = K8sInstanceInfoDTO.builder().build();
    InstanceDTO instanceDTO = InstanceDTO.builder().instanceInfoDTO(instanceInfoDTO).build();
    InstanceInfo instanceInfo = K8sInstanceInfo.builder().build();
    Instance instance =
        Instance.builder().instanceInfo(instanceInfo).deletedAt(234L).createdAt(123L).lastModifiedAt(3245L).build();
    when(instanceRepository.save(any())).thenReturn(instance);
    InstanceDTO actualInstanceDTO = instanceService.save(instanceDTO);
    assertThat(actualInstanceDTO.getCreatedAt()).isEqualTo(123L);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void saveAllTest() {
    InstanceInfo instanceInfo = K8sInstanceInfo.builder().build();
    Instance instance =
        Instance.builder().instanceInfo(instanceInfo).deletedAt(234L).createdAt(123L).lastModifiedAt(3245L).build();
    InstanceInfoDTO instanceInfoDTO = K8sInstanceInfoDTO.builder().build();
    InstanceDTO instanceDTO = InstanceDTO.builder().instanceInfoDTO(instanceInfoDTO).build();
    List<InstanceDTO> instanceDTOList = Arrays.asList(instanceDTO);
    when(instanceRepository.saveAll(anyList())).thenReturn(Arrays.asList(instance));
    List<InstanceDTO> actualInstanceDTOList = instanceService.saveAll(instanceDTOList);
    assertThat(actualInstanceDTOList.size()).isEqualTo(1);
    assertThat(actualInstanceDTOList.get(0).getCreatedAt()).isEqualTo(123L);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void saveOrReturnEmptyIfAlreadyExists() {
    InstanceInfo instanceInfo = K8sInstanceInfo.builder().build();
    Instance instance =
        Instance.builder().instanceInfo(instanceInfo).deletedAt(234L).createdAt(123L).lastModifiedAt(3245L).build();
    InstanceInfoDTO instanceInfoDTO = K8sInstanceInfoDTO.builder().build();
    InstanceDTO instanceDTO = InstanceDTO.builder().instanceInfoDTO(instanceInfoDTO).build();
    when(instanceRepository.save(any())).thenReturn(instance);
    Optional<InstanceDTO> response = instanceService.saveOrReturnEmptyIfAlreadyExists(instanceDTO);
    assertTrue(response.isPresent());
    assertThat(response.get().getLastModifiedAt()).isEqualTo(3245L);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void saveOrReturnEmptyIfAlreadyExistsDuplicateKeyException() {
    InstanceInfoDTO instanceInfoDTO = K8sInstanceInfoDTO.builder().build();
    InstanceDTO instanceDTO = InstanceDTO.builder().instanceInfoDTO(instanceInfoDTO).build();
    when(instanceRepository.save(any())).thenThrow(new DuplicateKeyException("duplicate"));
    Optional<InstanceDTO> response = instanceService.saveOrReturnEmptyIfAlreadyExists(instanceDTO);
    assertFalse(response.isPresent());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void saveOrReturnEmptyIfAlreadyExistsDuplicateKeyExceptionUndeleteInstance() {
    InstanceInfoDTO instanceInfoDTO = K8sInstanceInfoDTO.builder().build();
    InstanceDTO instanceDTO = InstanceDTO.builder().instanceInfoDTO(instanceInfoDTO).build();
    when(instanceRepository.save(any())).thenThrow(new DuplicateKeyException("duplicate"));
    Instance instanceInDeletedState = Instance.builder().build();
    when(instanceRepository.findAndReplace(any(), any())).thenReturn(instanceInDeletedState);
    Optional<InstanceDTO> response = instanceService.saveOrReturnEmptyIfAlreadyExists(instanceDTO);
    assertFalse(response.isPresent());
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void deleteByIdTest() {
    String id = "id";
    ArgumentCaptor<String> idCaptor = ArgumentCaptor.forClass(String.class);
    instanceService.deleteById(id);
    verify(instanceRepository, times(1)).deleteById(idCaptor.capture());
    assertThat(idCaptor.getValue()).isEqualTo(id);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void deleteAllTest() {
    InstanceInfoDTO instanceInfoDTO = K8sInstanceInfoDTO.builder().build();
    InstanceDTO instanceDTO = InstanceDTO.builder().instanceInfoDTO(instanceInfoDTO).instanceKey(INSTANCE_KEY).build();
    List<InstanceDTO> instanceDTOList = Arrays.asList(instanceDTO);
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    instanceService.deleteAll(instanceDTOList);
    verify(instanceRepository, times(1)).deleteByInstanceKey(captor.capture());
    assertThat(captor.getValue()).isEqualTo(INSTANCE_KEY);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getActiveInstancesByAccountTest() {
    String accountIdentifier = "Acc";
    String orgIdentifier = "Org";
    String projectIdentifier = "Proj";
    String serviceIdentifier = "Svc";
    long timestamp = 123L;
    InstanceInfo instanceInfo = K8sInstanceInfo.builder().build();
    Instance instance =
        Instance.builder().instanceInfo(instanceInfo).deletedAt(234L).createdAt(123L).lastModifiedAt(3245L).build();
    when(instanceRepository.getActiveInstancesByAccountOrgProjectAndService(
             accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier, timestamp))
        .thenReturn(Arrays.asList(instance));
    List<InstanceDTO> instanceDTOList = instanceService.getActiveInstancesByAccountOrgProjectAndService(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier, timestamp);
    assertThat(instanceDTOList.size()).isEqualTo(1);
    assertThat(instanceDTOList.get(0).getCreatedAt()).isEqualTo(123L);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getActiveInstancesTest() {
    String accountIdentifier = "Acc";
    String orgIdentifier = "org";
    long timestamp = 123L;
    String projectIdentifier = "pro";
    InstanceInfo instanceInfo = K8sInstanceInfo.builder().build();
    Instance instance =
        Instance.builder().instanceInfo(instanceInfo).deletedAt(234L).createdAt(123L).lastModifiedAt(3245L).build();
    when(instanceRepository.getActiveInstances(accountIdentifier, orgIdentifier, projectIdentifier, timestamp))
        .thenReturn(Arrays.asList(instance));
    List<InstanceDTO> instanceDTOList =
        instanceService.getActiveInstances(accountIdentifier, orgIdentifier, projectIdentifier, timestamp);
    assertThat(instanceDTOList.size()).isEqualTo(1);
    assertThat(instanceDTOList.get(0).getCreatedAt()).isEqualTo(123L);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getActiveInstancesByServiceIdTest() {
    String accountIdentifier = "Acc";
    String orgIdentifier = "org";
    long timestamp = 123L;
    String projectIdentifier = "pro";
    String serviceId = "serviceId";
    InstanceInfo instanceInfo = K8sInstanceInfo.builder().build();
    Instance instance =
        Instance.builder().instanceInfo(instanceInfo).deletedAt(234L).createdAt(123L).lastModifiedAt(3245L).build();
    when(instanceRepository.getActiveInstancesByServiceId(
             accountIdentifier, orgIdentifier, projectIdentifier, serviceId, timestamp))
        .thenReturn(Arrays.asList(instance));
    List<InstanceDTO> instanceDTOList = instanceService.getActiveInstancesByServiceId(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceId, timestamp);
    assertThat(instanceDTOList.size()).isEqualTo(1);
    assertThat(instanceDTOList.get(0).getCreatedAt()).isEqualTo(123L);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getActiveInstancesByInfrastructureMappingIdTest() {
    String accountIdentifier = "Acc";
    String orgIdentifier = "org";
    String projectIdentifier = "pro";
    String infrastructureMappingId = "infraMappingId";
    InstanceInfo instanceInfo = K8sInstanceInfo.builder().build();
    Instance instance =
        Instance.builder().instanceInfo(instanceInfo).deletedAt(234L).createdAt(123L).lastModifiedAt(3245L).build();
    when(instanceRepository.getActiveInstancesByInfrastructureMappingId(
             accountIdentifier, orgIdentifier, projectIdentifier, infrastructureMappingId))
        .thenReturn(Arrays.asList(instance));
    List<InstanceDTO> instanceDTOList = instanceService.getActiveInstancesByInfrastructureMappingId(
        accountIdentifier, orgIdentifier, projectIdentifier, infrastructureMappingId);
    assertThat(instanceDTOList.size()).isEqualTo(1);
    assertThat(instanceDTOList.get(0).getCreatedAt()).isEqualTo(123L);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getActiveInstancesByInstanceInfoTest() {
    String accountIdentifier = "Acc";
    String instanceInfoNamespace = "instanceInfoNamespace";
    String instanceInfoPodName = "instanceInfoPodName";
    InstanceInfo instanceInfo = K8sInstanceInfo.builder().build();
    Instance instance =
        Instance.builder().instanceInfo(instanceInfo).deletedAt(234L).createdAt(123L).lastModifiedAt(3245L).build();
    when(instanceRepository.getActiveInstancesByInstanceInfo(
             accountIdentifier, instanceInfoNamespace, instanceInfoPodName))
        .thenReturn(Arrays.asList(instance));
    List<InstanceDTO> instanceDTOList =
        instanceService.getActiveInstancesByInstanceInfo(accountIdentifier, instanceInfoNamespace, instanceInfoPodName);
    assertThat(instanceDTOList.size()).isEqualTo(1);
    assertThat(instanceDTOList.get(0).getCreatedAt()).isEqualTo(123L);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getEnvBuildInstanceCountByServiceIdTest() {
    String accountIdentifier = "Acc";
    String orgIdentifier = "org";
    long timestamp = 123L;
    String projectIdentifier = "pro";
    String serviceId = "serviceId";
    EnvBuildInstanceCount envBuildInstanceCount = new EnvBuildInstanceCount("envIden", "envName", "tag", 1);
    AggregationResults<EnvBuildInstanceCount> idAggregationResults =
        new AggregationResults<>(Arrays.asList(envBuildInstanceCount), new Document());
    when(instanceRepository.getEnvBuildInstanceCountByServiceId(
             accountIdentifier, orgIdentifier, projectIdentifier, serviceId, timestamp))
        .thenReturn(idAggregationResults);
    assertThat(instanceService.getEnvBuildInstanceCountByServiceId(
                   accountIdentifier, orgIdentifier, projectIdentifier, serviceId, timestamp))
        .isEqualTo(idAggregationResults);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getActiveInstancesByServiceIdEnvIdAndBuildIdsTest() {
    String accountIdentifier = "Acc";
    String orgIdentifier = "org";
    long timestamp = 123L;
    String projectIdentifier = "pro";
    String serviceId = "serviceId";
    String envId = "envId";
    String clusterId = "clusterId";
    String pipelineExecutionId = "pipelineExecutionId";
    int limit = 1;
    List<String> buildIds = Arrays.asList();
    InstancesByBuildId instancesByBuildId = new InstancesByBuildId("buildId", Arrays.asList());
    AggregationResults<InstancesByBuildId> idAggregationResults =
        new AggregationResults<>(Arrays.asList(instancesByBuildId), new Document());
    when(instanceRepository.getActiveInstancesByServiceIdEnvIdAndBuildIds(accountIdentifier, orgIdentifier,
             projectIdentifier, serviceId, envId, buildIds, timestamp, limit, null, clusterId, pipelineExecutionId))
        .thenReturn(idAggregationResults);
    assertThat(
        instanceService.getActiveInstancesByServiceIdEnvIdAndBuildIds(accountIdentifier, orgIdentifier,
            projectIdentifier, serviceId, envId, buildIds, timestamp, limit, null, clusterId, pipelineExecutionId))
        .isEqualTo(idAggregationResults);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getActiveServiceInstanceCountBreakdownTest() {
    String accountIdentifier = "Acc";
    String orgIdentifier = "org";
    long timestamp = 123L;
    String projectIdentifier = "pro";
    String serviceId = "serviceId";
    List<String> serviceIdsList = Arrays.asList(serviceId);
    CountByServiceIdAndEnvType countByServiceIdAndEnvType =
        new CountByServiceIdAndEnvType(serviceId, EnvironmentType.Production, 1);
    AggregationResults<CountByServiceIdAndEnvType> idAggregationResults =
        new AggregationResults<>(Arrays.asList(countByServiceIdAndEnvType), new Document());
    when(instanceRepository.getActiveServiceInstanceCountBreakdown(
             accountIdentifier, orgIdentifier, projectIdentifier, serviceIdsList, timestamp))
        .thenReturn(idAggregationResults);
    assertThat(instanceService.getActiveServiceInstanceCountBreakdown(
                   accountIdentifier, orgIdentifier, projectIdentifier, serviceIdsList, timestamp))
        .isEqualTo(idAggregationResults);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testFindAndReplace() {
    InstanceDTO instanceDTO = InstanceDTO.builder().instanceInfoDTO(K8sInstanceInfoDTO.builder().build()).build();
    Instance instance = Instance.builder()
                            .instanceInfo(K8sInstanceInfo.builder().build())
                            .deletedAt(234L)
                            .createdAt(123L)
                            .lastModifiedAt(3245L)
                            .build();
    when(instanceRepository.findAndReplace(any(), any())).thenReturn(instance);
    Optional<InstanceDTO> responseDTO = instanceService.findAndReplace(instanceDTO);
    assertTrue(responseDTO.isPresent());
    assertThat(responseDTO.get().getLastModifiedAt()).isEqualTo(3245L);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testFindAndReplaceFail() {
    InstanceInfoDTO instanceInfoDTO = K8sInstanceInfoDTO.builder().build();
    InstanceDTO instanceDTO = InstanceDTO.builder().instanceInfoDTO(instanceInfoDTO).build();
    when(instanceRepository.findAndReplace(any(), any())).thenReturn(null);
    Optional<InstanceDTO> responseDTO = instanceService.findAndReplace(instanceDTO);
    assertFalse(responseDTO.isPresent());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testUpdateInfrastructureMapping() {
    List<String> instanceIds = Arrays.asList("1", "2", "3");
    String infrastructureMappingId = "2";
    instanceService.updateInfrastructureMapping(instanceIds, infrastructureMappingId);
    verify(instanceRepository, times(3))
        .updateInfrastructureMapping(instanceIdCapture.capture(), eq(infrastructureMappingId));
    assertThat(instanceIdCapture.getAllValues()).isEqualTo(instanceIds);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testUpdateInfrastructureMappingWhenDuplicateKeyExceptionSoftDeleteInstance() {
    List<String> instanceIds = Arrays.asList("1", "2");
    String infrastructureMappingId = "2";
    doThrow(DuplicateKeyException.class)
        .when(instanceRepository)
        .updateInfrastructureMapping(eq("1"), eq(infrastructureMappingId));

    instanceService.updateInfrastructureMapping(instanceIds, infrastructureMappingId);

    Criteria criteria = Criteria.where(InstanceKeys.id).is("1");
    verify(instanceRepository).findAndModify(eq(criteria), any());
    verify(instanceRepository, times(2))
        .updateInfrastructureMapping(instanceIdCapture.capture(), eq(infrastructureMappingId));
    assertThat(instanceIdCapture.getAllValues()).isEqualTo(instanceIds);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_getActiveInstanceDetails_infra() {
    activateInstances();

    List<Instance> instances =
        InstanceDashboardServiceImplTest.getInstanceList()
            .stream()
            .filter(e
                -> e.getInfraIdentifier() != null && e.getInfraIdentifier().equals("infra1")
                    && e.getServiceIdentifier().equals("svc1") && e.getEnvIdentifier().equals("env1")
                    && e.getPrimaryArtifact().getTag().equals("1") && e.getLastPipelineExecutionId().equals("1"))
            .collect(Collectors.toList());

    List<Instance> instances1 = instanceService1.getActiveInstanceDetails("accountId", "orgId", "projectId", "svc1",
        "env1", "infra1", null, "1", "1", InstanceSyncConstants.INSTANCE_LIMIT);

    assertThat(instances.size()).isEqualTo(instances1.size());

    for (int i = 0; i < instances.size(); i++) {
      assertThat(checkInstanceEquality(instances.get(i), instances1.get(i))).isEqualTo(true);
    }
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_getActiveInstanceDetails_cluster() {
    activateInstances();

    List<Instance> instances =
        InstanceDashboardServiceImplTest.getInstanceList()
            .stream()
            .filter(e
                -> (e.getInstanceInfo() instanceof GitopsInstanceInfo)
                    && (((GitopsInstanceInfo) e.getInstanceInfo()).getClusterIdentifier() != null)
                    && (((GitopsInstanceInfo) e.getInstanceInfo()).getClusterIdentifier().equals("infra1"))
                    && e.getServiceIdentifier().equals("svc1") && e.getEnvIdentifier().equals("env1")
                    && e.getPrimaryArtifact().getTag().equals("1") && e.getLastPipelineExecutionId().equals("1"))
            .collect(Collectors.toList());

    List<Instance> instances1 = instanceService1.getActiveInstanceDetails("accountId", "orgId", "projectId", "svc1",
        "env1", null, "infra1", "1", "1", InstanceSyncConstants.INSTANCE_LIMIT);

    assertThat(instances.size()).isEqualTo(instances1.size());

    for (int i = 0; i < instances.size(); i++) {
      assertThat(checkInstanceEquality(instances.get(i), instances1.get(i))).isEqualTo(true);
    }
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_getActiveServiceInstanceInfoWithEnvType_NonGitOps() {
    when(instanceRepository.getActiveServiceInstanceInfoWithEnvType(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, ENVIRONMENT_ID, SERVICE_ID, DISPLAY_NAME, false, true))
        .thenReturn(aggregationResults);
    AggregationResults<ActiveServiceInstanceInfoWithEnvType> aggregationResults1 =
        instanceRepository.getActiveServiceInstanceInfoWithEnvType(
            ACCOUNT_ID, ORG_ID, PROJECT_ID, ENVIRONMENT_ID, SERVICE_ID, DISPLAY_NAME, false, true);
    assertThat(aggregationResults1).isEqualTo(aggregationResults);
    verify(instanceRepository)
        .getActiveServiceInstanceInfoWithEnvType(
            ACCOUNT_ID, ORG_ID, PROJECT_ID, ENVIRONMENT_ID, SERVICE_ID, DISPLAY_NAME, false, true);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_getActiveServiceInstanceInfoWithEnvType_GitOps() {
    when(instanceRepository.getActiveServiceInstanceInfoWithEnvType(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, ENVIRONMENT_ID, SERVICE_ID, DISPLAY_NAME, true, true))
        .thenReturn(aggregationResults);
    AggregationResults<ActiveServiceInstanceInfoWithEnvType> aggregationResults1 =
        instanceRepository.getActiveServiceInstanceInfoWithEnvType(
            ACCOUNT_ID, ORG_ID, PROJECT_ID, ENVIRONMENT_ID, SERVICE_ID, DISPLAY_NAME, true, true);
    assertThat(aggregationResults1).isEqualTo(aggregationResults);
    verify(instanceRepository)
        .getActiveServiceInstanceInfoWithEnvType(
            ACCOUNT_ID, ORG_ID, PROJECT_ID, ENVIRONMENT_ID, SERVICE_ID, DISPLAY_NAME, true, true);
  }
}
