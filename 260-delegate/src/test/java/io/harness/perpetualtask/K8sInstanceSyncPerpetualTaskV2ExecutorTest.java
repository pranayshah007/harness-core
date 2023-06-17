/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialDTO;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.K8sServerInstanceInfo;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.instancesync.DeploymentReleaseDetails;
import io.harness.perpetualtask.instancesync.InstanceSyncV2Request;
import io.harness.perpetualtask.instancesync.K8sInstanceSyncPerpetualTaskParamsV2;
import io.harness.perpetualtask.instancesync.k8s.K8sDeploymentReleaseDetails;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.serializer.KryoSerializer;

import software.wings.WingsBaseTest;

import com.google.inject.Inject;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(CDP)
public class K8sInstanceSyncPerpetualTaskV2ExecutorTest extends WingsBaseTest {
  @InjectMocks private K8sInstanceSyncPerpetualTaskV2Executor executor;
  @Inject private KryoSerializer kryoSerializer;
  @Mock private DelegateAgentManagerClient delegateAgentManagerClient;
  @Mock private K8sInstanceSyncV2Helper k8sInstanceSyncV2Helper;

  private final String PERPETUAL_TASK = "perpetualTaskId";
  private final String ACCOUNT_IDENTIFIER = "acc";
  private final String PROJECT_IDENTIFIER = "proj";
  private final String ORG_IDENTIFIER = "org";

  @Before
  public void setUp() throws IOException {
    on(executor).set("kryoSerializer", kryoSerializer);
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void retrieveServiceInstancesTest() throws Exception {
    PerpetualTaskId taskId = PerpetualTaskId.newBuilder().setId(PERPETUAL_TASK).build();

    PerpetualTaskExecutionParams params =
        PerpetualTaskExecutionParams.newBuilder()
            .setCustomizedParams(Any.pack(K8sInstanceSyncPerpetualTaskParamsV2.newBuilder()
                                              .setAccountId(ACCOUNT_IDENTIFIER)
                                              .setOrgId(ORG_IDENTIFIER)
                                              .setProjectId(PROJECT_IDENTIFIER)
                                              .build()))
            .build();
    LinkedHashSet<String> namespaces = new LinkedHashSet<>();
    namespaces.add("namespace1");
    K8sDeploymentReleaseDetails k8sDeploymentReleaseDetails =
        K8sDeploymentReleaseDetails.builder().releaseName("releaseName").namespaces(namespaces).build();
    List<K8sDeploymentReleaseDetails> k8sDeploymentReleaseDetailsList = new ArrayList<>();
    k8sDeploymentReleaseDetailsList.add(k8sDeploymentReleaseDetails);
    DeploymentReleaseDetails deploymentReleaseDetails =
        DeploymentReleaseDetails.builder().deploymentDetails(new ArrayList<>(k8sDeploymentReleaseDetailsList)).build();
    InstanceSyncV2Request instanceSyncV2Request = InstanceSyncV2Request.builder()
                                                      .accountId(ACCOUNT_IDENTIFIER)
                                                      .orgId(ORG_IDENTIFIER)
                                                      .projectId(PROJECT_IDENTIFIER)
                                                      .connector(ConnectorInfoDTO.builder().build())
                                                      .perpetualTaskId(PERPETUAL_TASK)
                                                      .build();
    when(k8sInstanceSyncV2Helper.getServerInstanceInfoList(
             any(K8sInstanceSyncPerpetualTaskV2Executor.PodDetailsRequest.class)))
        .thenReturn(List.of(K8sServerInstanceInfo.builder()
                                .name("instance1")
                                .namespace("namespace1")
                                .releaseName("releaseName")
                                .build()));
    List<ServerInstanceInfo> serverInstanceInfoList =
        executor.retrieveServiceInstances(instanceSyncV2Request, deploymentReleaseDetails);

    assertThat(serverInstanceInfoList.size()).isEqualTo(1);
    assertThat(((K8sServerInstanceInfo) serverInstanceInfoList.get(0)).getName()).isEqualTo("instance1");
    assertThat(((K8sServerInstanceInfo) serverInstanceInfoList.get(0)).getNamespace()).isEqualTo("namespace1");
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testCreateRequest() {
    PerpetualTaskId taskId = PerpetualTaskId.newBuilder().setId(PERPETUAL_TASK).build();
    ByteString encryptionDetailsBytes = ByteString.copyFrom(kryoSerializer.asBytes(new ArrayList<>()));
    PerpetualTaskExecutionParams params =
        PerpetualTaskExecutionParams.newBuilder()
            .setCustomizedParams(
                Any.pack(K8sInstanceSyncPerpetualTaskParamsV2.newBuilder()
                             .setAccountId(ACCOUNT_IDENTIFIER)
                             .setOrgId(ORG_IDENTIFIER)
                             .setProjectId(PROJECT_IDENTIFIER)
                             .setEncryptedData(encryptionDetailsBytes)
                             .setConnectorInfoDto(ByteString.copyFrom(kryoSerializer.asBytes(
                                 ConnectorInfoDTO.builder()
                                     .connectorConfig(KubernetesClusterConfigDTO.builder()
                                                          .credential(KubernetesCredentialDTO.builder().build())
                                                          .build())
                                     .build())))
                             .build()))
            .build();

    InstanceSyncV2Request instanceSyncV2Request = executor.createRequest(taskId.getId(), params);
    assertThat(instanceSyncV2Request).isNotNull();
    assertThat(instanceSyncV2Request.getAccountId()).isEqualTo(ACCOUNT_IDENTIFIER);
    assertThat(instanceSyncV2Request.getPerpetualTaskId()).isEqualTo(PERPETUAL_TASK);
  }
}
