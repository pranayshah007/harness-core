/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.instancesync;

import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;
import static io.harness.service.instancesync.OperationsOnInstances.ADD;
import static io.harness.service.instancesync.OperationsOnInstances.DELETE;
import static io.harness.service.instancesync.OperationsOnInstances.UPDATE;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.dtos.InstanceDTO;
import io.harness.dtos.instanceinfo.InstanceInfoDTO;
import io.harness.dtos.instanceinfo.K8sInstanceInfoDTO;
import io.harness.rule.Owner;
import io.harness.service.instance.InstanceService;
import io.harness.service.instancesynchandler.K8sInstanceSyncHandler;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class InstanceSyncServiceUtilsTest {
  private static final String ACCOUNT_ID = "BgiA4-xETamKNVAz-wQRjw";
  private static final String ORG_ID = "kmpySmUISimoRrJL6NL73w";
  private static final String PROJECT_ID = "instancesynctest";
  private static final String INFRA_MAPPING_ID = "62b9a965c202de6a4a1a7728";
  AutoCloseable openMocks;
  @Mock private InstanceService instanceService;
  private InstanceSyncServiceUtils instanceSyncServiceUtils;
  private K8sInstanceSyncHandler k8sInstanceSyncHandler;
  private static final String INSTANCESYNC_KEY1 =
      "K8sInstanceInfoDTO_rollingdeploy-deploy-64dfd95958-v4h6s_default_harness/todolist-sample:10";
  private static final String INSTANCESYNC_KEY2 =
      "K8sInstanceInfoDTO_rollingdeploy-deploy-64dfd95958-ah5gf_default_harness/todolist-sample:10";
  private static final String INSTANCESYNC_KEY3 =
      "K8sInstanceInfoDTO_rollingdeploy-deploy-64dfd95958-h39kj_default_harness/todolist-sample:10";
  private static final String RELEASE_NAME = "release-9a854787f0afbb105cf115d533f7a54624e1ba57";

  @Before
  public void setup() {
    openMocks = MockitoAnnotations.openMocks(this);
    k8sInstanceSyncHandler = new K8sInstanceSyncHandler();
    instanceSyncServiceUtils = new InstanceSyncServiceUtils(instanceService);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testProcessInstances() {
    Map<OperationsOnInstances, List<InstanceDTO>> instancesToBeModified = new HashMap<>();
    InstanceDTO instanceToUpdate = mockInstanceDTO(INSTANCESYNC_KEY3);
    InstanceDTO instanceToAdd = mockInstanceDTO(INSTANCESYNC_KEY2);
    instancesToBeModified.put(DELETE, Collections.singletonList(mockInstanceDTO(INSTANCESYNC_KEY1)));
    instancesToBeModified.put(ADD, Collections.singletonList(instanceToAdd));
    instancesToBeModified.put(UPDATE, Collections.singletonList(instanceToUpdate));

    instanceSyncServiceUtils.processInstances(instancesToBeModified);

    verify(instanceService).delete(eq(INSTANCESYNC_KEY1), anyString(), anyString(), anyString(), anyString());
    verify(instanceService).findAndReplace(eq(instanceToUpdate));
    verify(instanceService).saveOrReturnEmptyIfAlreadyExists(eq(instanceToAdd));
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testGetSyncKeyToInstances() {
    InstanceDTO instanceDTO = mockInstanceDTO(INSTANCESYNC_KEY1);
    Map<String, List<InstanceDTO>> syncKeyToInstances =
        instanceSyncServiceUtils.getSyncKeyToInstances(k8sInstanceSyncHandler, Collections.singletonList(instanceDTO));
    assertTrue(syncKeyToInstances.containsKey(RELEASE_NAME));
    assertEquals(1, syncKeyToInstances.get(RELEASE_NAME).size());
    assertEquals(instanceDTO, syncKeyToInstances.get(RELEASE_NAME).get(0));
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void getSyncKeyToInstancesFromServerMap() {
    InstanceInfoDTO instanceInfoDTO = mockInstanceInfoDTO();
    Map<String, List<InstanceInfoDTO>> syncKeyToInstances = instanceSyncServiceUtils.getSyncKeyToInstancesFromServerMap(
        k8sInstanceSyncHandler, Collections.singletonList(instanceInfoDTO));
    assertTrue(syncKeyToInstances.containsKey(RELEASE_NAME));
    assertEquals(1, syncKeyToInstances.get(RELEASE_NAME).size());
    assertEquals(instanceInfoDTO, syncKeyToInstances.get(RELEASE_NAME).get(0));
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testInitMapForTrackingFinalListOfInstances() {
    Map<OperationsOnInstances, List<InstanceDTO>> instanceMap =
        instanceSyncServiceUtils.initMapForTrackingFinalListOfInstances();
    assertTrue(instanceMap.containsKey(ADD));
    assertTrue(instanceMap.containsKey(UPDATE));
    assertTrue(instanceMap.containsKey(DELETE));
  }

  private InstanceInfoDTO mockInstanceInfoDTO() {
    return K8sInstanceInfoDTO.builder().releaseName(RELEASE_NAME).build();
  }

  private InstanceDTO mockInstanceDTO(String instanceKey) {
    InstanceInfoDTO instanceInfoDTO = K8sInstanceInfoDTO.builder().releaseName(RELEASE_NAME).build();
    return InstanceDTO.builder()
        .instanceKey(instanceKey)
        .accountIdentifier(ACCOUNT_ID)
        .orgIdentifier(ORG_ID)
        .projectIdentifier(PROJECT_ID)
        .infrastructureMappingId(INFRA_MAPPING_ID)
        .instanceInfoDTO(instanceInfoDTO)
        .build();
  }
}
