/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.beans.FeatureName.CHANGE_INSTANCE_QUERY_OPERATOR_TO_NE;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.rule.OwnerRule.ABHINAV;
import static io.harness.rule.OwnerRule.LUCAS_SALES;
import static io.harness.rule.OwnerRule.RAMA;
import static io.harness.rule.OwnerRule.TARUN_UBA;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter.Operator;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.AccountType;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.Instance.InstanceKeys;
import software.wings.beans.infrastructure.instance.InstanceType;
import software.wings.beans.infrastructure.instance.info.KubernetesContainerInfo;
import software.wings.beans.infrastructure.instance.key.ContainerInstanceKey;
import software.wings.beans.infrastructure.instance.key.PodInstanceKey;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.instance.InstanceService;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 *
 * @author rktummala
 */
@Slf4j
public class InstanceServiceTest extends WingsBaseTest {
  @Inject private AccountService accountService;
  @Mock private AppService appService;
  @Mock private Account account;
  @Mock private FeatureFlagService featureFlagService;
  @Inject private HPersistence persistence;
  @InjectMocks @Inject private InstanceService instanceService;

  private String instanceId = UUIDGenerator.generateUuid();
  private String containerId = "containerId";
  private String clusterName = "clusterName";
  private String controllerName = "controllerName";
  private String serviceName = "serviceName";
  private String podName = "podName";
  private String controllerType = "controllerType";

  /**
   * Sets mocks.
   */
  @Before
  public void setupMocks() {
    account = getAccount(AccountType.PAID);
    account.setAccountName("HARNESS_ACCOUNT_NAME");
    account.setCompanyName("HARNESS");
    accountService.save(account, false);
    when(appService.exist(anyString())).thenReturn(true);
  }

  /**
   * Should save and read.
   *
   */
  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testSaveAndRead() {
    Instance instance = buildInstance(instanceId, false, System.currentTimeMillis(), false);
    Instance savedInstance = instanceService.save(instance);
    compare(instance, savedInstance);

    Instance instanceFromGet = instanceService.get(instanceId, false);
    compare(savedInstance, instanceFromGet);
  }

  private Instance buildInstance(String uuid, boolean isDeleted, Long deletedAt, boolean needRetry) {
    return Instance.builder()
        .uuid(uuid)
        .instanceInfo(KubernetesContainerInfo.builder()
                          .clusterName(clusterName)
                          .controllerName(controllerName)
                          .controllerType(controllerType)
                          .podName(podName)
                          .serviceName(serviceName)
                          .build())
        .instanceType(InstanceType.KUBERNETES_CONTAINER_INSTANCE)
        .accountId(account.getUuid())
        .appId(GLOBAL_APP_ID)
        .infraMappingId(INFRA_MAPPING_ID)
        .containerInstanceKey(ContainerInstanceKey.builder().containerId(containerId).build())
        .deletedAt(deletedAt)
        .needRetry(needRetry)
        .isDeleted(isDeleted)
        .build();
  }

  private Instance buildPodInstance(
      String uuid, boolean isDeleted, Long deletedAt, boolean needRetry, String infraMappingId) {
    return Instance.builder()
        .uuid(uuid)
        .instanceInfo(KubernetesContainerInfo.builder()
                          .clusterName(clusterName)
                          .controllerName(controllerName)
                          .controllerType(controllerType)
                          .podName(podName)
                          .serviceName(serviceName)
                          .build())
        .instanceType(InstanceType.KUBERNETES_CONTAINER_INSTANCE)
        .accountId(account.getUuid())
        .appId(GLOBAL_APP_ID)
        .infraMappingId(infraMappingId)
        .podInstanceKey(PodInstanceKey.builder().podName("default-pod").namespace("default").build())
        .deletedAt(deletedAt)
        .needRetry(needRetry)
        .isDeleted(isDeleted)
        .build();
  }

  private Instance buildInstanceWithLastWorkflowExecutionId(
      String uuid, boolean isDeleted, Long deletedAt, boolean needRetry) {
    Instance instance = buildInstance(uuid, isDeleted, deletedAt, needRetry);
    instance.setLastWorkflowExecutionId("someId");
    return instance;
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testList() {
    Instance instance1 = buildInstance(instanceId, false, System.currentTimeMillis(), false);

    Instance savedInstance1 = instanceService.save(instance1);

    Instance instance2 = Instance.builder()
                             .uuid(UUIDGenerator.generateUuid())
                             .instanceInfo(KubernetesContainerInfo.builder()
                                               .clusterName(clusterName)
                                               .controllerName(controllerName)
                                               .controllerType(controllerType)
                                               .podName(podName)
                                               .serviceName(serviceName)
                                               .build())
                             .instanceType(InstanceType.KUBERNETES_CONTAINER_INSTANCE)
                             .accountId(account.getUuid())
                             .appId(GLOBAL_APP_ID)
                             .infraMappingId(INFRA_MAPPING_ID)
                             .containerInstanceKey(ContainerInstanceKey.builder().containerId(containerId).build())
                             .build();
    Instance savedInstance2 = instanceService.save(instance2);

    PageResponse<Instance> pageResponse =
        instanceService.list(aPageRequest().addFilter("accountId", Operator.EQ, account.getUuid()).build());
    assertThat(pageResponse).isNotNull();
    List<Instance> instanceList = pageResponse.getResponse();
    assertThat(instanceList).isNotNull();
    assertThat(instanceList).hasSize(2);

    instanceList = instanceService.getInstancesForAppAndInframapping(GLOBAL_APP_ID, INFRA_MAPPING_ID);
    assertThat(instanceList).hasSize(2);

    assertEquals(2, instanceService.getInstanceCount(GLOBAL_APP_ID, INFRA_MAPPING_ID));
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testHugeList() {
    for (int i = 0; i < 2000; i++) {
      Instance instance1 = buildInstance(UUIDGenerator.generateUuid(), false, System.currentTimeMillis(), false);
      instanceService.save(instance1);
    }
    List<Instance> instanceList = instanceService.getInstancesForAppAndInframapping(GLOBAL_APP_ID, INFRA_MAPPING_ID);
    assertThat(instanceList).hasSize(2000);
    assertEquals(2000, instanceService.getInstanceCount(GLOBAL_APP_ID, INFRA_MAPPING_ID));
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testUpdateAndRead() {
    Instance instance = buildInstance(instanceId, false, System.currentTimeMillis(), false);
    Instance savedInstance = instanceService.save(instance);
    compare(instance, savedInstance);

    Instance instanceFromGet = instanceService.get(instanceId, false);
    compare(instance, instanceFromGet);

    instanceFromGet.setInfraMappingId("inframappingId1");

    Instance updatedInstance = instanceService.update(instanceFromGet, savedInstance.getUuid());
    compare(instanceFromGet, updatedInstance);

    instanceFromGet = instanceService.get(instanceId, false);
    compare(updatedInstance, instanceFromGet);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testDelete() {
    Instance instance = buildInstance(instanceId, false, System.currentTimeMillis(), false);
    instanceService.save(instance);

    instanceService.delete(Sets.newHashSet(instanceId));

    Instance instanceAfterDelete = instanceService.get(instanceId, false);
    assertThat(instanceAfterDelete).isNull();

    instanceAfterDelete = instanceService.get(instanceId, true);
    assertThat(instanceAfterDelete).isNotNull();
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testManyDelete() {
    List<Instance> instances = new ArrayList<>();
    final long currentTimeMillis = System.currentTimeMillis();
    final Instance instanceDeleted1 =
        instanceService.save(buildInstance(UUIDGenerator.generateUuid(), true, currentTimeMillis, false));
    final Instance instanceDeleted2 =
        instanceService.save(buildInstance(UUIDGenerator.generateUuid(), true, currentTimeMillis, true));
    int total = 0;
    while (total < 500) {
      instances.add(instanceService.save(buildInstance(UUIDGenerator.generateUuid(), false, 0L, false)));
      instances.add(instanceService.save(buildInstance(UUIDGenerator.generateUuid(), false, 0L, true)));
      total += 2;
    }
    final List<String> idList = instances.stream().map(Instance::getUuid).collect(Collectors.toList());
    Set<String> idSet = new HashSet<>(idList);
    idSet.add(instanceDeleted1.getUuid());
    idSet.add(instanceDeleted2.getUuid());
    instanceService.delete(idSet);

    final long deletedAt = instanceService.get(idSet.iterator().next(), true).getDeletedAt();

    for (Instance instance : instances) {
      Instance instanceAfterDelete = instanceService.get(instance.getUuid(), true);
      assertThat(instanceAfterDelete.getDeletedAt()).isEqualTo(deletedAt);
      assertThat(instanceAfterDelete.isDeleted()).isEqualTo(true);
    }
    Instance instanceAfterDelete1 = instanceService.get(instanceDeleted1.getUuid(), true);
    assertThat(instanceAfterDelete1.getDeletedAt()).isEqualTo(currentTimeMillis);
    assertThat(instanceAfterDelete1.isDeleted()).isEqualTo(true);

    Instance instanceAfterDelete2 = instanceService.get(instanceDeleted2.getUuid(), true);
    assertThat(instanceAfterDelete2.getDeletedAt()).isEqualTo(currentTimeMillis);
    assertThat(instanceAfterDelete2.isDeleted()).isEqualTo(true);
  }

  private void compare(Instance lhs, Instance rhs) {
    //    assertThat( rhs.getUuid()).isEqualTo(lhs.getUuid());
    assertThat(rhs.getContainerInstanceKey().getContainerId())
        .isEqualTo(lhs.getContainerInstanceKey().getContainerId());
    assertThat(rhs.getInfraMappingId()).isEqualTo(lhs.getInfraMappingId());
    assertThat(rhs.getAccountId()).isEqualTo(lhs.getAccountId());
    assertThat(rhs.getAppId()).isEqualTo(lhs.getAppId());
    assertThat(rhs.getInstanceType()).isEqualTo(lhs.getInstanceType());
    assertThat(rhs.getInstanceType()).isEqualTo(lhs.getInstanceType());
  }

  private void comparePodInstance(Instance lhs, Instance rhs) {
    assertThat(rhs.getPodInstanceKey().getPodName()).isEqualTo(lhs.getPodInstanceKey().getPodName());
    assertThat(rhs.getPodInstanceKey().getNamespace()).isEqualTo(lhs.getPodInstanceKey().getNamespace());
    assertThat(rhs.getInfraMappingId()).isEqualTo(lhs.getInfraMappingId());
    assertThat(rhs.getAccountId()).isEqualTo(lhs.getAccountId());
    assertThat(rhs.getAppId()).isEqualTo(lhs.getAppId());
    assertThat(rhs.getInstanceType()).isEqualTo(lhs.getInstanceType());
    assertThat(rhs.getInstanceType()).isEqualTo(lhs.getInstanceType());
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testListInstancesNotRemovedFully() {
    List<Instance> instances = new ArrayList<>();
    int a = 0;
    while (a < 600) {
      instances.add(
          instanceService.save(buildInstance(UUIDGenerator.generateUuid(), false, System.currentTimeMillis(), false)));
      instances.add(
          instanceService.save(buildInstance(UUIDGenerator.generateUuid(), false, System.currentTimeMillis(), true)));
      instances.add(
          instanceService.save(buildInstance(UUIDGenerator.generateUuid(), true, System.currentTimeMillis(), false)));
      instances.add(
          instanceService.save(buildInstance(UUIDGenerator.generateUuid(), true, System.currentTimeMillis(), true)));
      a += 4;
    }
    instanceService.save(
        buildInstance(instanceId, true, System.currentTimeMillis() - TimeUnit.DAYS.toMillis(10), false));
    instanceService.save(buildInstance(instanceId, true, 0L, false));

    Query<Instance> query = persistence.createQuery(Instance.class).filter(InstanceKeys.accountId, account.getUuid());
    final List<Instance> instances1 = instanceService.listInstancesNotRemovedFully(query);
    final List<String> uuidsInResponse =
        instances1.stream().map(Instance::getUuid).sorted().collect(Collectors.toList());
    final List<String> uuidsExpected = instances.stream().map(Instance::getUuid).sorted().collect(Collectors.toList());
    assertThat(uuidsInResponse.size()).isEqualTo(600);
    assertThat(uuidsInResponse).isEqualTo(uuidsExpected);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testPurgeForInstances() {
    List<Instance> instancesToBeDeleted = new ArrayList<>();
    List<Instance> instancesNotToBeDeleted = new ArrayList<>();
    int a = 0;
    while (a < 1000) {
      instancesToBeDeleted.add(
          instanceService.save(buildInstance(UUIDGenerator.generateUuid(), true, (long) (12345 + a), false)));

      instancesNotToBeDeleted.add(
          instanceService.save(buildInstance(UUIDGenerator.generateUuid(), true, 9999999L, true)));
      a += 2;
    }
    final boolean b = instanceService.purgeDeletedUpTo(Instant.ofEpochMilli(12346 + a));
    assertThat(b).isTrue();
    final Instance instance = instanceService.get(instancesToBeDeleted.get(0).getUuid(), true);
    assertThat(instance).isNull();
    final Instance instance_1 = instanceService.get(instancesNotToBeDeleted.get(0).getUuid(), true);
    assertThat(instance_1).isNotNull();
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testGetLastDiscoveredInstance() {
    doReturn(true).when(featureFlagService).isGlobalEnabled(CHANGE_INSTANCE_QUERY_OPERATOR_TO_NE);
    List<Instance> instances = new ArrayList<>();
    int a = 0;
    while (a < 600) {
      instances.add(
          instanceService.save(buildInstance(UUIDGenerator.generateUuid(), false, System.currentTimeMillis(), false)));
      instances.add(
          instanceService.save(buildInstance(UUIDGenerator.generateUuid(), false, System.currentTimeMillis(), true)));
      instances.add(
          instanceService.save(buildInstance(UUIDGenerator.generateUuid(), true, System.currentTimeMillis(), false)));
      instances.add(
          instanceService.save(buildInstance(UUIDGenerator.generateUuid(), true, System.currentTimeMillis(), true)));
      a += 4;
    }
    Instance instanceToBeFound = buildInstanceWithLastWorkflowExecutionId(
        instanceId, true, System.currentTimeMillis() - TimeUnit.DAYS.toMillis(10), false);
    instanceService.save(instanceToBeFound);

    final Instance instanceFound = instanceService.getLastDiscoveredInstance(GLOBAL_APP_ID, INFRA_MAPPING_ID);

    assertThat(instanceFound.getUuid()).isEqualTo(instanceToBeFound.getUuid());
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testGetLastDiscoveredInstance_shouldReturn0() {
    doReturn(true).when(featureFlagService).isGlobalEnabled(CHANGE_INSTANCE_QUERY_OPERATOR_TO_NE);
    List<Instance> instances = new ArrayList<>();
    int a = 0;
    while (a < 600) {
      instances.add(
          instanceService.save(buildInstance(UUIDGenerator.generateUuid(), false, System.currentTimeMillis(), false)));
      instances.add(
          instanceService.save(buildInstance(UUIDGenerator.generateUuid(), false, System.currentTimeMillis(), true)));
      instances.add(
          instanceService.save(buildInstance(UUIDGenerator.generateUuid(), true, System.currentTimeMillis(), false)));
      instances.add(
          instanceService.save(buildInstance(UUIDGenerator.generateUuid(), true, System.currentTimeMillis(), true)));
      a += 4;
    }

    final Instance instanceFound = instanceService.getLastDiscoveredInstance(GLOBAL_APP_ID, INFRA_MAPPING_ID);

    assertThat(instanceFound).isNull();
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testSaveAndNotUpdate() {
    List<Instance> instances = new ArrayList<>();
    instances.add(instanceService.save(
        buildPodInstance(UUIDGenerator.generateUuid(), false, System.currentTimeMillis(), false, "infra1")));
    instances.add(instanceService.save(
        buildPodInstance(UUIDGenerator.generateUuid(), false, System.currentTimeMillis(), false, "infra2")));
    Instance instance = buildPodInstance(instanceId, false, System.currentTimeMillis(), false, "infra1");
    Instance savedInstance = instanceService.saveOrUpdate(instance);
    comparePodInstance(instance, savedInstance);

    final Instance instanceFound = instanceService.get(instances.get(0).getUuid(), true);
    assertThat(instanceFound.getUuid()).isEqualTo(instances.get(0).getUuid());
  }
}
