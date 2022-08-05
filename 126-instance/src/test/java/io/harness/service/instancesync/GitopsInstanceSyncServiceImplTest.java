/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.instancesync;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.InstancesTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cdng.gitops.beans.GitOpsInstanceRequest;
import io.harness.data.structure.UUIDGenerator;
import io.harness.dtos.InstanceDTO;
import io.harness.dtos.instanceinfo.GitOpsInstanceInfoDTO;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.service.instance.InstanceService;
import io.harness.service.instancesynchandlerfactory.InstanceSyncHandlerFactoryService;
import io.harness.util.InstanceSyncKey;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class GitopsInstanceSyncServiceImplTest extends InstancesTestBase {
  @Inject private InstanceSyncServiceUtils utils;

  @Inject private InstanceSyncHandlerFactoryService instanceSyncHandlerFactoryService;

  @Inject private InstanceService instanceService;

  @Inject private GitopsInstanceSyncServiceImpl gitopsInstanceSyncService;

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testProcessInstanceSync_1() {
    String accountId = "accountId";
    String orgId = "orgId";
    String projId = UUIDGenerator.generateUuid();

    List<InstanceDTO> instanceList =
        buildInstanceList(accountId, orgId, projId, "s1", "e1", "app1", "agent1", "default", "test-1", 3);

    gitopsInstanceSyncService.processInstanceSync(accountId, orgId, projId, instanceList);

    List<InstanceDTO> instancesSaved = instanceService.getActiveInstancesByServiceId(accountId, orgId, projId, "s1");

    assertThat(instancesSaved).hasSize(3);
    assertThat(instancesSaved.stream().map(InstanceDTO::getInstanceKey).collect(Collectors.toSet()))
        .containsExactlyInAnyOrder("GitOpsInstanceRequest_default_test-12", "GitOpsInstanceRequest_default_test-11",
            "GitOpsInstanceRequest_default_test-10");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testProcessInstanceSync_2() {
    String accountId = "accountId";
    String orgId = "orgId";
    String projId = UUIDGenerator.generateUuid();

    List<InstanceDTO> instanceList_1 =
        buildInstanceList(accountId, orgId, projId, "s1", "e1", "app1", "agent1", "default", "test-2", 3);
    List<InstanceDTO> instanceList_2 =
        buildInstanceList(accountId, orgId, projId, "s2", "e1", "app1", "agent2", "harness", "test-2", 3);

    gitopsInstanceSyncService.processInstanceSync(accountId, orgId, projId, instanceList_1);
    gitopsInstanceSyncService.processInstanceSync(accountId, orgId, projId, instanceList_2);

    List<InstanceDTO> instancesSavedForS1 =
        instanceService.getActiveInstancesByServiceId(accountId, orgId, projId, "s1");

    assertThat(instancesSavedForS1).hasSize(3);
    assertThat(instancesSavedForS1.stream().map(InstanceDTO::getInstanceKey).collect(Collectors.toSet()))
        .containsExactlyInAnyOrder("GitOpsInstanceRequest_default_test-20", "GitOpsInstanceRequest_default_test-21",
            "GitOpsInstanceRequest_default_test-22");

    List<InstanceDTO> instancesSavedForS2 =
        instanceService.getActiveInstancesByServiceId(accountId, orgId, projId, "s2");

    assertThat(instancesSavedForS2).hasSize(3);
    assertThat(instancesSavedForS2.stream().map(InstanceDTO::getInstanceKey).collect(Collectors.toSet()))
        .containsExactlyInAnyOrder("GitOpsInstanceRequest_harness_test-20", "GitOpsInstanceRequest_harness_test-21",
            "GitOpsInstanceRequest_harness_test-22");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testProcessInstanceSync_idempotency() {
    String accountId = "accountId";
    String orgId = "orgId";
    String projId = UUIDGenerator.generateUuid();

    List<InstanceDTO> instanceList =
        buildInstanceList(accountId, orgId, projId, "s1", "e1", "app1", "agent1", "gitops", "test-2", 3);

    gitopsInstanceSyncService.processInstanceSync(accountId, orgId, projId, instanceList);
    gitopsInstanceSyncService.processInstanceSync(accountId, orgId, projId, instanceList);
    gitopsInstanceSyncService.processInstanceSync(accountId, orgId, projId, instanceList);

    List<InstanceDTO> instancesSaved = instanceService.getActiveInstancesByServiceId(accountId, orgId, projId, "s1");

    assertThat(instancesSaved).hasSize(3);
    assertThat(instancesSaved.stream().map(InstanceDTO::getInstanceKey).collect(Collectors.toSet()))
        .containsExactlyInAnyOrder("GitOpsInstanceRequest_gitops_test-22", "GitOpsInstanceRequest_gitops_test-21",
            "GitOpsInstanceRequest_gitops_test-20");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testProcessInstanceSync_deletion() {
    String accountId = "accountId";
    String orgId = "orgId";
    String projId = UUIDGenerator.generateUuid();

    List<InstanceDTO> instanceList =
        buildInstanceList(accountId, orgId, projId, "s1", "e1", "app1", "agent1", "default", "test-4", 3);

    gitopsInstanceSyncService.processInstanceSync(accountId, orgId, projId, instanceList);
    gitopsInstanceSyncService.processInstanceSync(
        accountId, orgId, projId, Collections.singletonList(instanceList.get(0)));

    List<InstanceDTO> instancesSaved = instanceService.getActiveInstancesByServiceId(accountId, orgId, projId, "s1");

    assertThat(instancesSaved).hasSize(1);
    assertThat(instancesSaved.stream().map(InstanceDTO::getInstanceKey).collect(Collectors.toSet()))
        .containsExactlyInAnyOrder("GitOpsInstanceRequest_default_test-40");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testProcessInstanceSync_add() {
    String accountId = "accountId";
    String orgId = "orgId";
    String projId = UUIDGenerator.generateUuid();

    List<InstanceDTO> instanceList_1 =
        buildInstanceList(accountId, orgId, projId, "s1", "e1", "app1", "agent1", "default", "test-4", 3);

    gitopsInstanceSyncService.processInstanceSync(accountId, orgId, projId, instanceList_1);

    instanceList_1.addAll(
        buildInstanceList(accountId, orgId, projId, "s1", "e1", "app1", "agent1", "harness", "test-4", 2));

    gitopsInstanceSyncService.processInstanceSync(accountId, orgId, projId, instanceList_1);

    List<InstanceDTO> instancesSaved = instanceService.getActiveInstancesByServiceId(accountId, orgId, projId, "s1");

    assertThat(instancesSaved).hasSize(5);
    assertThat(instancesSaved.stream().map(InstanceDTO::getInstanceKey).collect(Collectors.toSet()))
        .containsExactlyInAnyOrder("GitOpsInstanceRequest_harness_test-40", "GitOpsInstanceRequest_harness_test-41",
            "GitOpsInstanceRequest_default_test-42", "GitOpsInstanceRequest_default_test-41",
            "GitOpsInstanceRequest_default_test-40");
  }

  List<InstanceDTO> buildInstanceList(String accountId, String orgId, String projectId, String serviceId, String envId,
      String appId, String agentId, String namespace, String prefix, int n) {
    List<InstanceDTO> ans = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      ans.add(InstanceDTO.builder()
                  .accountIdentifier(accountId)
                  .orgIdentifier(orgId)
                  .projectIdentifier(projectId)
                  .serviceIdentifier(serviceId)
                  .envIdentifier(envId)
                  .instanceKey(InstanceSyncKey.builder()
                                   .clazz(GitOpsInstanceRequest.class)
                                   .part(namespace)
                                   .part(prefix + i)
                                   .build()
                                   .toString())
                  .instanceInfoDTO(GitOpsInstanceInfoDTO.builder()
                                       .appIdentifier(appId)
                                       .agentIdentifier(agentId)
                                       .podName(prefix + i)
                                       .podId(prefix + i)
                                       .namespace(namespace)
                                       .build())
                  .build());
    }
    return ans;
  }
}
