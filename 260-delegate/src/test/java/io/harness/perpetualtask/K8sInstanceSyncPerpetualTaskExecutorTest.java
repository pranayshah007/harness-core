/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import io.harness.DelegateTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterDetailsDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialDTO;
import io.harness.delegate.beans.instancesync.K8sInstanceSyncPerpetualTaskResponse;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.K8sServerInstanceInfo;
import io.harness.delegate.task.k8s.ContainerDeploymentDelegateBaseHelper;
import io.harness.delegate.task.k8s.DirectK8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.k8s.model.HarnessLabels;
import io.harness.k8s.model.K8sPod;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.logging.CommandExecutionStatus;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.instancesync.K8sDeploymentRelease;
import io.harness.perpetualtask.instancesync.K8sInstanceSyncPerpetualTaskParams;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(CDP)
public class K8sInstanceSyncPerpetualTaskExecutorTest extends DelegateTestBase {
  private static final String RELEASE_NAME_1 = "releaseName1";
  private static final String RELEASE_NAME_2 = "releaseName2";
  private static final String PERPETUAL_TASK_ID = "perpetualTaskId";
  private static final String ACCOUNT_ID = "accountId";

  @Inject private KryoSerializer kryoSerializer;
  @Mock private ContainerDeploymentDelegateBaseHelper containerBaseHelper;
  @Mock private K8sTaskHelperBase k8sTaskHelperBase;
  @Mock private DelegateAgentManagerClient delegateAgentManagerClient;
  @Mock private Call<RestResponse<Boolean>> call;

  @Captor private ArgumentCaptor<K8sInstanceSyncPerpetualTaskResponse> perpetualTaskResponseCaptor;

  @InjectMocks K8sInstanceSyncPerpetualTaskExecutor k8sInstanceSyncPerpetualTaskExecutor;

  @Before
  public void setUp() throws IOException {
    on(k8sInstanceSyncPerpetualTaskExecutor).set("kryoSerializer", kryoSerializer);
    doReturn(call)
        .when(delegateAgentManagerClient)
        .processInstanceSyncNGResult(anyString(), anyString(), perpetualTaskResponseCaptor.capture());
    doReturn(retrofit2.Response.success("success")).when(call).execute();

    doReturn(KubernetesConfig.builder().accountId(ACCOUNT_ID).build())
        .when(containerBaseHelper)
        .createKubernetesConfig(any(DirectK8sInfraDelegateConfig.class), any());
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void runOnceWithDifferentNamespaces() throws Exception {
    List<String> namespacesRN1 = Arrays.asList("ns1", "ns2");
    List<String> namespacesRN2 = Arrays.asList("ns3", "ns4");

    List<K8sDeploymentRelease> deploymentReleases =
        getK8sDeploymentReleases(namespacesRN1, RELEASE_NAME_1, namespacesRN2, RELEASE_NAME_2);

    K8sInstanceSyncPerpetualTaskParams message = K8sInstanceSyncPerpetualTaskParams.newBuilder()
                                                     .setAccountId(ACCOUNT_ID)
                                                     .addAllK8SDeploymentReleaseList(deploymentReleases)
                                                     .build();
    PerpetualTaskExecutionParams perpetualTaskExecutionParams =
        PerpetualTaskExecutionParams.newBuilder().setCustomizedParams(Any.pack(message)).build();

    k8sInstanceSyncPerpetualTaskExecutor.runOnce(
        PerpetualTaskId.newBuilder().setId(PERPETUAL_TASK_ID).build(), perpetualTaskExecutionParams, Instant.EPOCH);

    assertThat(perpetualTaskResponseCaptor.getValue()).isInstanceOf(K8sInstanceSyncPerpetualTaskResponse.class);
    K8sInstanceSyncPerpetualTaskResponse k8sInstanceSyncPerpetualTaskResponse = perpetualTaskResponseCaptor.getValue();
    assertThat(k8sInstanceSyncPerpetualTaskResponse.getCommandExecutionStatus())
        .isEqualTo(CommandExecutionStatus.SUCCESS);
    List<ServerInstanceInfo> serverInstanceDetails = k8sInstanceSyncPerpetualTaskResponse.getServerInstanceDetails();
    assertThat(serverInstanceDetails.size()).isEqualTo(4);

    serverInstanceDetails.forEach(serverInstanceInfo -> {
      K8sServerInstanceInfo k8sServerInstanceInfo = (K8sServerInstanceInfo) serverInstanceInfo;
      String namespace = k8sServerInstanceInfo.getNamespace();
      String releaseName = k8sServerInstanceInfo.getReleaseName();
      assertThat(Arrays.asList("ns1", "ns2", "ns3", "ns4").contains(namespace)).isTrue();
      assertThat(Arrays.asList(RELEASE_NAME_1, RELEASE_NAME_2).contains(releaseName)).isTrue();
    });
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void runOnceWithSameNamespaces() throws Exception {
    List<String> namespacesRN1 = Arrays.asList("ns1", "ns2");
    List<String> namespacesRN2 = Arrays.asList("ns1", "ns2");

    List<K8sDeploymentRelease> deploymentReleases =
        getK8sDeploymentReleases(namespacesRN1, RELEASE_NAME_1, namespacesRN2, RELEASE_NAME_2);

    K8sInstanceSyncPerpetualTaskParams message = K8sInstanceSyncPerpetualTaskParams.newBuilder()
                                                     .setAccountId(ACCOUNT_ID)
                                                     .addAllK8SDeploymentReleaseList(deploymentReleases)
                                                     .build();
    PerpetualTaskExecutionParams perpetualTaskExecutionParams =
        PerpetualTaskExecutionParams.newBuilder().setCustomizedParams(Any.pack(message)).build();

    k8sInstanceSyncPerpetualTaskExecutor.runOnce(
        PerpetualTaskId.newBuilder().setId(PERPETUAL_TASK_ID).build(), perpetualTaskExecutionParams, Instant.EPOCH);

    assertThat(perpetualTaskResponseCaptor.getValue()).isInstanceOf(K8sInstanceSyncPerpetualTaskResponse.class);
    K8sInstanceSyncPerpetualTaskResponse k8sInstanceSyncPerpetualTaskResponse = perpetualTaskResponseCaptor.getValue();
    assertThat(k8sInstanceSyncPerpetualTaskResponse.getCommandExecutionStatus())
        .isEqualTo(CommandExecutionStatus.SUCCESS);
    List<ServerInstanceInfo> serverInstanceDetails = k8sInstanceSyncPerpetualTaskResponse.getServerInstanceDetails();
    assertThat(serverInstanceDetails.size()).isEqualTo(4);

    serverInstanceDetails.forEach(serverInstanceInfo -> {
      K8sServerInstanceInfo k8sServerInstanceInfo = (K8sServerInstanceInfo) serverInstanceInfo;
      String namespace = k8sServerInstanceInfo.getNamespace();
      String releaseName = k8sServerInstanceInfo.getReleaseName();
      assertThat(Arrays.asList("ns1", "ns2").contains(namespace)).isTrue();
      assertThat(Arrays.asList(RELEASE_NAME_1, RELEASE_NAME_2).contains(releaseName)).isTrue();
    });
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void runOnceWithSameReleaseNameAndSameNamespaces() throws Exception {
    List<String> namespacesRN1 = Arrays.asList("ns1", "ns2");
    List<String> namespacesRN2 = Arrays.asList("ns1", "ns2");

    List<K8sDeploymentRelease> deploymentReleases =
        getK8sDeploymentReleases(namespacesRN1, RELEASE_NAME_1, namespacesRN2, RELEASE_NAME_1);

    K8sInstanceSyncPerpetualTaskParams message = K8sInstanceSyncPerpetualTaskParams.newBuilder()
                                                     .setAccountId(ACCOUNT_ID)
                                                     .addAllK8SDeploymentReleaseList(deploymentReleases)
                                                     .build();
    PerpetualTaskExecutionParams perpetualTaskExecutionParams =
        PerpetualTaskExecutionParams.newBuilder().setCustomizedParams(Any.pack(message)).build();

    k8sInstanceSyncPerpetualTaskExecutor.runOnce(
        PerpetualTaskId.newBuilder().setId(PERPETUAL_TASK_ID).build(), perpetualTaskExecutionParams, Instant.EPOCH);

    assertThat(perpetualTaskResponseCaptor.getValue()).isInstanceOf(K8sInstanceSyncPerpetualTaskResponse.class);
    K8sInstanceSyncPerpetualTaskResponse k8sInstanceSyncPerpetualTaskResponse = perpetualTaskResponseCaptor.getValue();
    assertThat(k8sInstanceSyncPerpetualTaskResponse.getCommandExecutionStatus())
        .isEqualTo(CommandExecutionStatus.SUCCESS);
    List<ServerInstanceInfo> serverInstanceDetails = k8sInstanceSyncPerpetualTaskResponse.getServerInstanceDetails();
    assertThat(serverInstanceDetails.size()).isEqualTo(2);

    serverInstanceDetails.forEach(serverInstanceInfo -> {
      K8sServerInstanceInfo k8sServerInstanceInfo = (K8sServerInstanceInfo) serverInstanceInfo;
      String namespace = k8sServerInstanceInfo.getNamespace();
      String releaseName = k8sServerInstanceInfo.getReleaseName();
      assertThat(Arrays.asList("ns1", "ns2").contains(namespace)).isTrue();
      assertThat(Objects.equals(RELEASE_NAME_1, releaseName)).isTrue();
    });
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void runOnceWithSameReleaseNameAndDifferentNamespaces() throws Exception {
    List<String> namespacesRN1 = Arrays.asList("ns1", "ns2");
    List<String> namespacesRN2 = Arrays.asList("ns3", "ns4");

    List<K8sDeploymentRelease> deploymentReleases =
        getK8sDeploymentReleases(namespacesRN1, RELEASE_NAME_1, namespacesRN2, RELEASE_NAME_1);

    K8sInstanceSyncPerpetualTaskParams message = K8sInstanceSyncPerpetualTaskParams.newBuilder()
                                                     .setAccountId(ACCOUNT_ID)
                                                     .addAllK8SDeploymentReleaseList(deploymentReleases)
                                                     .build();
    PerpetualTaskExecutionParams perpetualTaskExecutionParams =
        PerpetualTaskExecutionParams.newBuilder().setCustomizedParams(Any.pack(message)).build();

    k8sInstanceSyncPerpetualTaskExecutor.runOnce(
        PerpetualTaskId.newBuilder().setId(PERPETUAL_TASK_ID).build(), perpetualTaskExecutionParams, Instant.EPOCH);

    assertThat(perpetualTaskResponseCaptor.getValue()).isInstanceOf(K8sInstanceSyncPerpetualTaskResponse.class);
    K8sInstanceSyncPerpetualTaskResponse k8sInstanceSyncPerpetualTaskResponse = perpetualTaskResponseCaptor.getValue();
    assertThat(k8sInstanceSyncPerpetualTaskResponse.getCommandExecutionStatus())
        .isEqualTo(CommandExecutionStatus.SUCCESS);
    List<ServerInstanceInfo> serverInstanceDetails = k8sInstanceSyncPerpetualTaskResponse.getServerInstanceDetails();
    assertThat(serverInstanceDetails.size()).isEqualTo(4);

    serverInstanceDetails.forEach(serverInstanceInfo -> {
      K8sServerInstanceInfo k8sServerInstanceInfo = (K8sServerInstanceInfo) serverInstanceInfo;
      String namespace = k8sServerInstanceInfo.getNamespace();
      String releaseName = k8sServerInstanceInfo.getReleaseName();
      assertThat(Arrays.asList("ns1", "ns2", "ns3", "ns4").contains(namespace)).isTrue();
      assertThat(Objects.equals(RELEASE_NAME_1, releaseName)).isTrue();
    });
  }

  private List<K8sDeploymentRelease> getK8sDeploymentReleases(List<String> namespacesRN1, String releaseName1,
      List<String> namespacesRN2, String releaseName2) throws Exception {
    K8sDeploymentRelease k8sDeploymentReleaseOne = getK8sDeploymentRelease(namespacesRN1, releaseName1);
    K8sDeploymentRelease k8sDeploymentReleaseTwo = getK8sDeploymentRelease(namespacesRN2, releaseName2);

    List<K8sDeploymentRelease> deploymentReleases = new ArrayList<>();
    deploymentReleases.add(k8sDeploymentReleaseOne);
    deploymentReleases.add(k8sDeploymentReleaseTwo);
    mockGetPodDetails(k8sDeploymentReleaseOne.getNamespacesList(), k8sDeploymentReleaseOne.getReleaseName());
    mockGetPodDetails(k8sDeploymentReleaseTwo.getNamespacesList(), k8sDeploymentReleaseTwo.getReleaseName());
    return deploymentReleases;
  }

  private K8sDeploymentRelease getK8sDeploymentRelease(List<String> namespaces, String releaseName) {
    DirectK8sInfraDelegateConfig k8sInfraDelegateConfig =
        DirectK8sInfraDelegateConfig.builder()
            .namespace(namespaces.get(0))
            .kubernetesClusterConfigDTO(
                KubernetesClusterConfigDTO.builder()
                    .credential(
                        KubernetesCredentialDTO.builder().config(KubernetesClusterDetailsDTO.builder().build()).build())
                    .build())
            .build();

    return K8sDeploymentRelease.newBuilder()
        .setK8SInfraDelegateConfig(ByteString.copyFrom(kryoSerializer.asBytes(k8sInfraDelegateConfig)))
        .addAllNamespaces(namespaces)
        .setReleaseName(releaseName)
        .build();
  }

  private void mockGetPodDetails(List<String> namespaces, String releaseName) throws Exception {
    for (String namespace : namespaces) {
      List<K8sPod> k8sPodList = new ArrayList<>();
      Map<String, String> labels = new HashMap<>();
      labels.put(HarnessLabels.color, "blueGreenColor");
      k8sPodList.add(K8sPod.builder().namespace(namespace).releaseName(releaseName).labels(labels).build());
      doReturn(k8sPodList)
          .when(k8sTaskHelperBase)
          .getPodDetails(any(KubernetesConfig.class), eq(namespace), eq(releaseName), anyLong());
    }
  }
}
