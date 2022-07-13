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

import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.InstancesTestBase;
import io.harness.category.element.UnitTests;
import io.harness.dtos.InstanceDTO;
import io.harness.dtos.instanceinfo.InstanceInfoDTO;
import io.harness.dtos.instanceinfo.K8sInstanceInfoDTO;
import io.harness.entities.Instance;
import io.harness.entities.instanceinfo.InstanceInfo;
import io.harness.entities.instanceinfo.K8sInstanceInfo;
import io.harness.models.CountByServiceIdAndEnvType;
import io.harness.models.EnvBuildInstanceCount;
import io.harness.models.InstancesByBuildId;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.repositories.instance.InstanceRepository;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

public class InstanceServiceImplTest extends InstancesTestBase {
  private final String INSTANCE_KEY = "instance_key";
  @Mock InstanceRepository instanceRepository;
  @InjectMocks InstanceServiceImpl instanceService;

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
    Optional<InstanceDTO> respone = instanceService.saveOrReturnEmptyIfAlreadyExists(instanceDTO);
    assertThat(respone.get().getLastModifiedAt()).isEqualTo(3245L);
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
    long timestamp = 123L;
    InstanceInfo instanceInfo = K8sInstanceInfo.builder().build();
    Instance instance =
        Instance.builder().instanceInfo(instanceInfo).deletedAt(234L).createdAt(123L).lastModifiedAt(3245L).build();
    when(instanceRepository.getActiveInstancesByAccount(accountIdentifier, timestamp))
        .thenReturn(Arrays.asList(instance));
    List<InstanceDTO> instanceDTOList = instanceService.getActiveInstancesByAccount(accountIdentifier, timestamp);
    assertThat(instanceDTOList.size()).isEqualTo(1);
    assertThat(instanceDTOList.get(0).getCreatedAt()).isEqualTo(123L);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getInstancesDeployedInIntervalTest() {
    String accountIdentifier = "Acc";
    long startTimestamp = 123L;
    long endTimestamp = 123L;
    InstanceInfo instanceInfo = K8sInstanceInfo.builder().build();
    Instance instance =
        Instance.builder().instanceInfo(instanceInfo).deletedAt(234L).createdAt(123L).lastModifiedAt(3245L).build();
    when(instanceRepository.getInstancesDeployedInInterval(accountIdentifier, startTimestamp, endTimestamp))
        .thenReturn(Arrays.asList(instance));
    List<InstanceDTO> instanceDTOList =
        instanceService.getInstancesDeployedInInterval(accountIdentifier, startTimestamp, endTimestamp);
    assertThat(instanceDTOList.size()).isEqualTo(1);
    assertThat(instanceDTOList.get(0).getCreatedAt()).isEqualTo(123L);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getInstancesTest() {
    String accountIdentifier = "Acc";
    String orgIdentifier = "org";
    String projectIdentifier = "pro";
    String infrastructureMappingId = "infraMappingId";
    InstanceInfo instanceInfo = K8sInstanceInfo.builder().build();
    Instance instance =
        Instance.builder().instanceInfo(instanceInfo).deletedAt(234L).createdAt(123L).lastModifiedAt(3245L).build();
    when(instanceRepository.getInstances(accountIdentifier, orgIdentifier, projectIdentifier, infrastructureMappingId))
        .thenReturn(Arrays.asList(instance));
    List<InstanceDTO> instanceDTOList =
        instanceService.getInstances(accountIdentifier, orgIdentifier, projectIdentifier, infrastructureMappingId);
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
    int limit = 1;
    List<String> buildIds = Arrays.asList();
    InstancesByBuildId instancesByBuildId = new InstancesByBuildId("buildId", Arrays.asList());
    AggregationResults<InstancesByBuildId> idAggregationResults =
        new AggregationResults<>(Arrays.asList(instancesByBuildId), new Document());
    when(instanceRepository.getActiveInstancesByServiceIdEnvIdAndBuildIds(
             accountIdentifier, orgIdentifier, projectIdentifier, serviceId, envId, buildIds, timestamp, limit))
        .thenReturn(idAggregationResults);
    assertThat(instanceService.getActiveInstancesByServiceIdEnvIdAndBuildIds(
                   accountIdentifier, orgIdentifier, projectIdentifier, serviceId, envId, buildIds, timestamp, limit))
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
}
