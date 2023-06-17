/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.instancesyncv2.cg;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.DelegateTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.model.K8sPod;
import io.harness.logging.CommandExecutionStatus;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.PerpetualTaskExecutionParams;
import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.perpetualtask.instancesyncv2.CgDeploymentReleaseDetails;
import io.harness.perpetualtask.instancesyncv2.CgInstanceSyncResponse;
import io.harness.perpetualtask.instancesyncv2.CgInstanceSyncTaskParams;
import io.harness.perpetualtask.instancesyncv2.DirectK8sInstanceSyncTaskDetails;
import io.harness.perpetualtask.instancesyncv2.InstanceSyncData;
import io.harness.perpetualtask.instancesyncv2.InstanceSyncTrackedDeploymentDetails;
import io.harness.perpetualtask.instancesyncv2.ResponseBatchConfig;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.serializer.KryoSerializer;

import software.wings.helpers.ext.container.ContainerDeploymentDelegateHelper;
import software.wings.helpers.ext.k8s.response.K8sInstanceSyncResponse;
import software.wings.helpers.ext.k8s.response.K8sTaskExecutionResponse;

import com.google.inject.Inject;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(CDP)
public class CgInstanceSyncV2TaskExecutorTest extends DelegateTestBase {
  @Inject KryoSerializer kryoSerializer;
  @InjectMocks private CgInstanceSyncV2TaskExecutor executor;
  @Mock private ContainerDeploymentDelegateHelper containerDeploymentDelegateHelper;
  @Mock private Call<RestResponse<Boolean>> call;
  @Mock private Call<InstanceSyncTrackedDeploymentDetails> callFetch;

  @Mock private KubernetesContainerService kubernetesContainerService;

  @Mock private DelegateAgentManagerClient delegateAgentManagerClient;
  @Mock private InstanceDetailsFetcherFactory instanceDetailsFetcherFactory;
  @Mock private K8sTaskHelperBase k8sTaskHelperBase;
  @Mock
  InstanceDetailsFetcher instanceDetailsFetcher = new CgK8sInstancesDetailsFetcher(
      containerDeploymentDelegateHelper, kubernetesContainerService, kryoSerializer, k8sTaskHelperBase);

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void runOnceWithK8sCallSuccess() throws Exception {
    InstanceSyncTrackedDeploymentDetails instanceSyncTrackedDeploymentDetails =
        InstanceSyncTrackedDeploymentDetails.newBuilder()
            .setPerpetualTaskId("perpetualTaskId")
            .setAccountId("AccountId")
            .setResponseBatchConfig(ResponseBatchConfig.newBuilder().setReleaseCount(15).setInstanceCount(500).build())
            .addAllDeploymentDetails(
                Collections.singleton(CgDeploymentReleaseDetails.newBuilder()
                                          .setTaskDetailsId("taskDetailsId")
                                          .setInfraMappingType("DIRECT_KUBERNETES")
                                          .setInfraMappingId("infraMappingId")
                                          .setReleaseDetails(Any.pack(DirectK8sInstanceSyncTaskDetails.newBuilder()
                                                                          .setReleaseName("releaseName")
                                                                          .setNamespace("namespace")
                                                                          .setIsHelm(false)
                                                                          .setContainerServiceName("")
                                                                          .build()))
                                          .build()))
            .build();
    doReturn(call)
        .when(delegateAgentManagerClient)
        .publishInstanceSyncV2Result(anyString(), anyString(), any(CgInstanceSyncResponse.class));
    doReturn(retrofit2.Response.success("success")).when(call).execute();
    List<K8sPod> runningK8sPods = Collections.singletonList(K8sPod.builder().name("podName").build());
    doReturn(InstanceSyncData.newBuilder()
                 .setExecutionStatus(CommandExecutionStatus.SUCCESS.name())
                 .setInstanceCount(24)
                 .setTaskResponse(
                     ByteString.copyFrom(kryoSerializer.asBytes(K8sTaskExecutionResponse.builder()
                                                                    .k8sTaskResponse(K8sInstanceSyncResponse.builder()
                                                                                         .k8sPodInfoList(runningK8sPods)
                                                                                         .releaseName("releaseName")
                                                                                         .namespace("namespace")
                                                                                         .build())
                                                                    .commandExecutionStatus(SUCCESS)
                                                                    .build())))
                 .build())
        .when(instanceDetailsFetcher)
        .fetchRunningInstanceDetails(anyString(), any(CgDeploymentReleaseDetails.class));

    doReturn(instanceDetailsFetcher).when(instanceDetailsFetcherFactory).getFetcher(anyString());
    doReturn(callFetch).when(delegateAgentManagerClient).fetchTrackedReleaseDetails(anyString(), anyString());
    doReturn(retrofit2.Response.success(instanceSyncTrackedDeploymentDetails)).when(callFetch).execute();

    ArgumentCaptor<CgInstanceSyncResponse> captor = ArgumentCaptor.forClass(CgInstanceSyncResponse.class);

    executor.runOnce(PerpetualTaskId.newBuilder().setId("id").build(), getK8sPerpetualTaskParams(), Instant.now());

    verify(delegateAgentManagerClient, times(1))
        .publishInstanceSyncV2Result(eq("id"), eq("AccountId"), captor.capture());

    CgInstanceSyncResponse cgInstanceSyncResponse = captor.getValue();
    assertThat(cgInstanceSyncResponse.getInstanceData(0).getExecutionStatus())
        .isEqualTo(CommandExecutionStatus.SUCCESS.name());
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void runOnceWithK8sCallBatchingLogicReleaseCount() throws Exception {
    InstanceSyncTrackedDeploymentDetails instanceSyncTrackedDeploymentDetails =
        InstanceSyncTrackedDeploymentDetails.newBuilder()
            .setPerpetualTaskId("perpetualTaskId")
            .setAccountId("AccountId")
            .setResponseBatchConfig(ResponseBatchConfig.newBuilder().setReleaseCount(15).setInstanceCount(500).build())
            .addAllDeploymentDetails(listOfDeploymentDetails(16))
            .build();
    doReturn(call)
        .when(delegateAgentManagerClient)
        .publishInstanceSyncV2Result(anyString(), anyString(), any(CgInstanceSyncResponse.class));
    doReturn(retrofit2.Response.success("success")).when(call).execute();
    List<K8sPod> runningK8sPods = Collections.singletonList(K8sPod.builder().name("podName").build());
    doReturn(InstanceSyncData.newBuilder()
                 .setExecutionStatus(CommandExecutionStatus.SUCCESS.name())
                 .setInstanceCount(501)
                 .setTaskResponse(
                     ByteString.copyFrom(kryoSerializer.asBytes(K8sTaskExecutionResponse.builder()
                                                                    .k8sTaskResponse(K8sInstanceSyncResponse.builder()
                                                                                         .k8sPodInfoList(runningK8sPods)
                                                                                         .releaseName("releaseName")
                                                                                         .namespace("namespace")
                                                                                         .build())
                                                                    .commandExecutionStatus(SUCCESS)
                                                                    .build())))
                 .build())
        .when(instanceDetailsFetcher)
        .fetchRunningInstanceDetails(anyString(), any(CgDeploymentReleaseDetails.class));
    doReturn(InstanceSyncData.newBuilder().setExecutionStatus(CommandExecutionStatus.SUCCESS.name()).build())
        .when(instanceDetailsFetcher)
        .fetchRunningInstanceDetails(anyString(), any(CgDeploymentReleaseDetails.class));
    doReturn(instanceDetailsFetcher).when(instanceDetailsFetcherFactory).getFetcher(anyString());
    doReturn(callFetch).when(delegateAgentManagerClient).fetchTrackedReleaseDetails(anyString(), anyString());
    doReturn(retrofit2.Response.success(instanceSyncTrackedDeploymentDetails)).when(callFetch).execute();

    executor.runOnce(PerpetualTaskId.newBuilder().setId("id").build(), getK8sPerpetualTaskParams(), Instant.now());

    verify(delegateAgentManagerClient, times(2)).publishInstanceSyncV2Result(eq("id"), eq("AccountId"), any());
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void runOnceWithK8sCallBatchingLogicInstanceCount() throws Exception {
    InstanceSyncTrackedDeploymentDetails instanceSyncTrackedDeploymentDetails =
        InstanceSyncTrackedDeploymentDetails.newBuilder()
            .setPerpetualTaskId("perpetualTaskId")
            .setAccountId("AccountId")
            .setResponseBatchConfig(ResponseBatchConfig.newBuilder().setReleaseCount(15).setInstanceCount(500).build())
            .addAllDeploymentDetails(listOfDeploymentDetails(2))
            .build();

    doReturn(call)
        .when(delegateAgentManagerClient)
        .publishInstanceSyncV2Result(anyString(), anyString(), any(CgInstanceSyncResponse.class));
    doReturn(retrofit2.Response.success("success")).when(call).execute();
    List<K8sPod> runningK8sPods = listOfDeploymentInstances(501);
    doReturn(InstanceSyncData.newBuilder()
                 .setExecutionStatus(CommandExecutionStatus.SUCCESS.name())
                 .setInstanceCount(501)
                 .setTaskResponse(
                     ByteString.copyFrom(kryoSerializer.asBytes(K8sTaskExecutionResponse.builder()
                                                                    .k8sTaskResponse(K8sInstanceSyncResponse.builder()
                                                                                         .k8sPodInfoList(runningK8sPods)
                                                                                         .releaseName("releaseName")
                                                                                         .namespace("namespace")
                                                                                         .build())
                                                                    .commandExecutionStatus(SUCCESS)
                                                                    .build())))
                 .build())
        .when(instanceDetailsFetcher)
        .fetchRunningInstanceDetails(anyString(), any(CgDeploymentReleaseDetails.class));

    doReturn(instanceDetailsFetcher).when(instanceDetailsFetcherFactory).getFetcher(anyString());
    doReturn(callFetch).when(delegateAgentManagerClient).fetchTrackedReleaseDetails(anyString(), anyString());
    doReturn(retrofit2.Response.success(instanceSyncTrackedDeploymentDetails)).when(callFetch).execute();

    executor.runOnce(PerpetualTaskId.newBuilder().setId("id").build(), getK8sPerpetualTaskParams(), Instant.now());

    verify(delegateAgentManagerClient, times(3)).publishInstanceSyncV2Result(eq("id"), eq("AccountId"), any());
  }

  private List<CgDeploymentReleaseDetails> listOfDeploymentDetails(int count) {
    List<CgDeploymentReleaseDetails> cgDeploymentReleaseDetails = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      cgDeploymentReleaseDetails.add(CgDeploymentReleaseDetails.newBuilder()
                                         .setTaskDetailsId("taskDetailsId-" + i)
                                         .setInfraMappingType("DIRECT_KUBERNETES")
                                         .setInfraMappingId("infraMappingId")
                                         .setReleaseDetails(Any.pack(DirectK8sInstanceSyncTaskDetails.newBuilder()
                                                                         .setReleaseName("releaseName" + i)
                                                                         .setNamespace("namespace")
                                                                         .setIsHelm(false)
                                                                         .setContainerServiceName("")
                                                                         .build()))
                                         .build());
    }
    return cgDeploymentReleaseDetails;
  }

  private List<K8sPod> listOfDeploymentInstances(int count) {
    List<K8sPod> k8sPodInfos = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      k8sPodInfos.add(K8sPod.builder().name("podName" + i).releaseName("releaseName").build());
    }
    return k8sPodInfos;
  }

  private PerpetualTaskExecutionParams getK8sPerpetualTaskParams() {
    CgInstanceSyncTaskParams params =
        CgInstanceSyncTaskParams.newBuilder().setAccountId("AccountId").setCloudProviderType("K8s").build();
    return PerpetualTaskExecutionParams.newBuilder().setCustomizedParams(Any.pack(params)).build();
  }
}
