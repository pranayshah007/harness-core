/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.YOGESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.DelegateTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.k8s.model.K8sPod;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.logging.CommandExecutionStatus;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.instancesync.ContainerInstanceSyncPerpetualTaskParams;
import io.harness.perpetualtask.instancesync.ContainerServicePerpetualTaskParams;
import io.harness.perpetualtask.instancesync.K8sContainerInstanceSyncPerpetualTaskParams;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.serializer.KryoSerializer;

import software.wings.beans.AwsConfig;
import software.wings.beans.dto.SettingAttribute;
import software.wings.beans.infrastructure.instance.info.ContainerInfo;
import software.wings.beans.infrastructure.instance.info.KubernetesContainerInfo;
import software.wings.delegatetasks.k8s.K8sTaskHelper;
import software.wings.helpers.ext.container.ContainerDeploymentDelegateHelper;
import software.wings.helpers.ext.k8s.request.K8sClusterConfig;
import software.wings.helpers.ext.k8s.response.K8sInstanceSyncResponse;
import software.wings.helpers.ext.k8s.response.K8sTaskExecutionResponse;
import software.wings.service.impl.ContainerServiceParams;
import software.wings.service.impl.instance.sync.response.ContainerSyncResponse;
import software.wings.service.intfc.ContainerService;
import software.wings.service.intfc.aws.delegate.AwsEcsHelperServiceDelegate;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import org.eclipse.jetty.server.Response;
import org.junit.Before;
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
public class ContainerInstanceSyncPerpetualTaskExecutorTest extends DelegateTestBase {
  @Mock private DelegateAgentManagerClient delegateAgentManagerClient;
  @Mock private transient K8sTaskHelper k8sTaskHelper;
  @Mock private K8sTaskHelperBase k8sTaskHelperBase;
  @Mock private transient ContainerDeploymentDelegateHelper containerDeploymentDelegateHelper;
  @Mock private transient ContainerService containerService;
  @Mock private AwsEcsHelperServiceDelegate awsEcsHelperServiceDelegate;
  @Mock private Call<RestResponse<Boolean>> call;
  @Inject KryoSerializer kryoSerializer;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;

  private ArgumentCaptor<ContainerSyncResponse> containerSyncResponseCaptor =
      ArgumentCaptor.forClass(ContainerSyncResponse.class);

  private ArgumentCaptor<K8sTaskExecutionResponse> k8TaskResponseCaptor =
      ArgumentCaptor.forClass(K8sTaskExecutionResponse.class);

  @InjectMocks private ContainerInstanceSyncPerpetualTaskExecutor executor;

  @Before
  public void setup() {
    on(executor).set("referenceFalseKryoSerializer", referenceFalseKryoSerializer);
  }

  @Test
  @Owner(developers = OwnerRule.ACASIAN)
  @Category(UnitTests.class)
  public void runOnceWithK8sCallSuccess() throws Exception {
    doReturn(KubernetesConfig.builder().accountId(null).build())
        .when(containerDeploymentDelegateHelper)
        .getKubernetesConfig(any(K8sClusterConfig.class), eq(true));

    final K8sPod pod = K8sPod.builder().namespace("namespace").releaseName("release").build();
    doReturn(Arrays.asList(pod))
        .when(k8sTaskHelperBase)
        .getPodDetails(any(KubernetesConfig.class), eq("namespace"), eq("release"), anyLong());
    doReturn(call)
        .when(delegateAgentManagerClient)
        .publishInstanceSyncResultV2(anyString(), anyString(), any(DelegateResponseData.class));
    doReturn(retrofit2.Response.success("success")).when(call).execute();

    PerpetualTaskResponse perpetualTaskResponse;
    perpetualTaskResponse =
        executor.runOnce(PerpetualTaskId.newBuilder().setId("id").build(), getK8sPerpetualTaskParams(), Instant.now());

    verify(delegateAgentManagerClient, times(1))
        .publishInstanceSyncResultV2(eq("id"), eq("accountId"), k8TaskResponseCaptor.capture());

    final K8sTaskExecutionResponse k8sTaskExecutionResponse = k8TaskResponseCaptor.getValue();

    verifyK8sCallSuccess(pod, perpetualTaskResponse, k8sTaskExecutionResponse);

    doThrow(new RuntimeException()).when(call).execute();
    perpetualTaskResponse =
        executor.runOnce(PerpetualTaskId.newBuilder().setId("id").build(), getK8sPerpetualTaskParams(), Instant.now());

    verifyK8sCallSuccess(pod, perpetualTaskResponse, k8sTaskExecutionResponse);
  }

  private void verifyK8sCallSuccess(
      K8sPod pod, PerpetualTaskResponse perpetualTaskResponse, K8sTaskExecutionResponse k8sTaskExecutionResponse) {
    assertThat(k8sTaskExecutionResponse.getErrorMessage()).isNull();
    assertThat(k8sTaskExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(k8sTaskExecutionResponse.getK8sTaskResponse()).isNotNull();
    K8sInstanceSyncResponse taskResp = (K8sInstanceSyncResponse) k8sTaskExecutionResponse.getK8sTaskResponse();
    assertThat(taskResp.getK8sPodInfoList()).containsExactly(pod);

    assertThat(perpetualTaskResponse.getResponseMessage()).isEqualTo("success");
    assertThat(perpetualTaskResponse.getResponseCode()).isEqualTo(Response.SC_OK);
  }

  @Test
  @Owner(developers = OwnerRule.ACASIAN)
  @Category(UnitTests.class)
  public void runOnceWithK8sCallFailure() throws Exception {
    doReturn(KubernetesConfig.builder().accountId(null).build())
        .when(containerDeploymentDelegateHelper)
        .getKubernetesConfig(any(K8sClusterConfig.class), eq(true));

    doThrow(new RuntimeException("Failed to retrieve pod list"))
        .when(k8sTaskHelperBase)
        .getPodDetails(any(KubernetesConfig.class), eq("namespace"), eq("release"), anyLong());
    doReturn(call)
        .when(delegateAgentManagerClient)
        .publishInstanceSyncResultV2(anyString(), anyString(), any(DelegateResponseData.class));
    doReturn(retrofit2.Response.success("success")).when(call).execute();

    PerpetualTaskResponse perpetualTaskResponse;
    perpetualTaskResponse =
        executor.runOnce(PerpetualTaskId.newBuilder().setId("id").build(), getK8sPerpetualTaskParams(), Instant.now());

    verify(delegateAgentManagerClient, times(1))
        .publishInstanceSyncResultV2(eq("id"), eq("accountId"), k8TaskResponseCaptor.capture());

    final K8sTaskExecutionResponse k8sTaskExecutionResponse = k8TaskResponseCaptor.getValue();

    verifyK8sCallFailure(perpetualTaskResponse, k8sTaskExecutionResponse);

    doThrow(new RuntimeException()).when(call).execute();
    perpetualTaskResponse =
        executor.runOnce(PerpetualTaskId.newBuilder().setId("id").build(), getK8sPerpetualTaskParams(), Instant.now());

    verifyK8sCallFailure(perpetualTaskResponse, k8sTaskExecutionResponse);
  }

  private void verifyK8sCallFailure(
      PerpetualTaskResponse perpetualTaskResponse, K8sTaskExecutionResponse k8sTaskExecutionResponse) {
    assertThat(k8sTaskExecutionResponse.getErrorMessage()).isEqualTo("Failed to retrieve pod list");
    assertThat(k8sTaskExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(k8sTaskExecutionResponse.getK8sTaskResponse()).isNull();

    assertThat(perpetualTaskResponse.getResponseMessage()).isEqualTo("Failed to retrieve pod list");
    assertThat(perpetualTaskResponse.getResponseCode()).isEqualTo(Response.SC_OK);
  }

  @Test
  @Owner(developers = OwnerRule.ACASIAN)
  @Category(UnitTests.class)
  public void runOnceWithContainerServicesCallSuccess() throws Exception {
    final KubernetesContainerInfo containerInfo =
        KubernetesContainerInfo.builder().namespace("namespace").serviceName("service").build();
    doReturn(Arrays.asList(containerInfo))
        .when(containerService)
        .getContainerInfos(any(ContainerServiceParams.class), eq(true));
    doReturn(call)
        .when(delegateAgentManagerClient)
        .publishInstanceSyncResultV2(anyString(), anyString(), any(DelegateResponseData.class));
    doReturn(retrofit2.Response.success("success")).when(call).execute();
    doReturn(true).when(awsEcsHelperServiceDelegate).serviceExists(any(), any(), anyString(), anyString(), anyString());

    PerpetualTaskResponse perpetualTaskResponse;
    perpetualTaskResponse = executor.runOnce(
        PerpetualTaskId.newBuilder().setId("id").build(), getContainerInstancePerpetualTaskParams(), Instant.now());

    verify(delegateAgentManagerClient, times(1))
        .publishInstanceSyncResultV2(eq("id"), eq("accountId"), containerSyncResponseCaptor.capture());

    final ContainerSyncResponse containerSyncResponse = containerSyncResponseCaptor.getValue();

    assertThat(containerSyncResponse.isEcs()).isTrue();
    verifyContainerServicesCallSuccess(containerInfo, perpetualTaskResponse, containerSyncResponse);
    assertThat(containerSyncResponse.getControllerName()).isEqualTo("service");

    doThrow(new RuntimeException()).when(call).execute();
    perpetualTaskResponse = executor.runOnce(
        PerpetualTaskId.newBuilder().setId("id").build(), getContainerInstancePerpetualTaskParams(), Instant.now());

    verifyContainerServicesCallSuccess(containerInfo, perpetualTaskResponse, containerSyncResponse);
    assertThat(containerSyncResponse.getControllerName()).isEqualTo("service");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void runOnceWithContainerServicesHelmCallSuccess() throws Exception {
    final KubernetesContainerInfo containerInfo = KubernetesContainerInfo.builder()
                                                      .namespace("namespace")
                                                      .podName("test-pod")
                                                      .ip("10.20.30.40")
                                                      .releaseName("release")
                                                      .build();
    doReturn(Arrays.asList(containerInfo))
        .when(containerService)
        .getContainerInfos(any(ContainerServiceParams.class), eq(true));
    doReturn(call)
        .when(delegateAgentManagerClient)
        .publishInstanceSyncResultV2(anyString(), anyString(), any(DelegateResponseData.class));
    doReturn(retrofit2.Response.success("success")).when(call).execute();

    PerpetualTaskResponse perpetualTaskResponse;
    perpetualTaskResponse = executor.runOnce(
        PerpetualTaskId.newBuilder().setId("id").build(), getContainerInstancePerpetualTaskParamsHelm(), Instant.now());

    verify(delegateAgentManagerClient, times(1))
        .publishInstanceSyncResultV2(eq("id"), eq("accountId"), containerSyncResponseCaptor.capture());

    final ContainerSyncResponse containerSyncResponse = containerSyncResponseCaptor.getValue();

    verifyContainerServicesCallSuccess(containerInfo, perpetualTaskResponse, containerSyncResponse);

    doThrow(new RuntimeException()).when(call).execute();
    perpetualTaskResponse = executor.runOnce(
        PerpetualTaskId.newBuilder().setId("id").build(), getContainerInstancePerpetualTaskParams(), Instant.now());

    verifyContainerServicesCallSuccess(containerInfo, perpetualTaskResponse, containerSyncResponse);
    assertThat(containerSyncResponse.getReleaseName()).isEqualTo("release");
    assertThat(containerSyncResponse.getNamespace()).isEqualTo("namespace");
  }

  private void verifyContainerServicesCallSuccess(ContainerInfo containerInfo,
      PerpetualTaskResponse perpetualTaskResponse, ContainerSyncResponse containerSyncResponse) {
    assertThat(containerSyncResponse.getErrorMessage()).isNull();
    assertThat(containerSyncResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(containerSyncResponse.getContainerInfoList()).isNotNull();
    assertThat(containerSyncResponse.getContainerInfoList()).containsExactly(containerInfo);

    assertThat(perpetualTaskResponse.getResponseMessage()).isEqualTo("success");
    assertThat(perpetualTaskResponse.getResponseCode()).isEqualTo(Response.SC_OK);
  }

  @Test
  @Owner(developers = OwnerRule.ACASIAN)
  @Category(UnitTests.class)
  public void runOnceWithContainerServicesCallFailure() throws Exception {
    doThrow(new RuntimeException("Failed to retrieve container info"))
        .when(containerService)
        .getContainerInfos(any(ContainerServiceParams.class), eq(true));
    doReturn(call)
        .when(delegateAgentManagerClient)
        .publishInstanceSyncResultV2(anyString(), anyString(), any(DelegateResponseData.class));
    doReturn(retrofit2.Response.success("success")).when(call).execute();
    doReturn(true).when(awsEcsHelperServiceDelegate).serviceExists(any(), any(), anyString(), anyString(), anyString());

    PerpetualTaskResponse perpetualTaskResponse;
    perpetualTaskResponse = executor.runOnce(
        PerpetualTaskId.newBuilder().setId("id").build(), getContainerInstancePerpetualTaskParams(), Instant.now());

    verify(delegateAgentManagerClient, times(1))
        .publishInstanceSyncResultV2(eq("id"), eq("accountId"), containerSyncResponseCaptor.capture());

    final ContainerSyncResponse containerSyncResponse = containerSyncResponseCaptor.getValue();

    assertThat(containerSyncResponse.isEcs()).isFalse();
    verifyContainerServicesCallFailure(perpetualTaskResponse, containerSyncResponse);

    doThrow(new RuntimeException()).when(call).execute();
    perpetualTaskResponse = executor.runOnce(
        PerpetualTaskId.newBuilder().setId("id").build(), getContainerInstancePerpetualTaskParams(), Instant.now());

    verifyContainerServicesCallFailure(perpetualTaskResponse, containerSyncResponse);
  }

  private void verifyContainerServicesCallFailure(
      PerpetualTaskResponse perpetualTaskResponse, ContainerSyncResponse containerSyncResponse) {
    assertThat(containerSyncResponse.getErrorMessage()).isEqualTo("Failed to retrieve container info");
    assertThat(containerSyncResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(containerSyncResponse.getContainerInfoList()).isNull();

    assertThat(perpetualTaskResponse.getResponseMessage()).isEqualTo("Failed to retrieve container info");
    assertThat(perpetualTaskResponse.getResponseCode()).isEqualTo(Response.SC_OK);
  }

  @Test
  @Owner(developers = OwnerRule.ACASIAN)
  @Category(UnitTests.class)
  public void cleanup() {
    assertThat(executor.cleanup(PerpetualTaskId.newBuilder().setId("id").build(), getK8sPerpetualTaskParams()))
        .isFalse();
    assertThat(
        executor.cleanup(PerpetualTaskId.newBuilder().setId("id").build(), getContainerInstancePerpetualTaskParams()))
        .isFalse();
  }

  private PerpetualTaskExecutionParams getContainerInstancePerpetualTaskParams() {
    AwsConfig awsConfig = AwsConfig.builder().accountId("accountId").build();
    ByteString configBytes = ByteString.copyFrom(referenceFalseKryoSerializer.asBytes(
        SettingAttribute.builder().accountId("accountId").value(awsConfig).build()));
    ByteString encryptionDetailsBytes = ByteString.copyFrom(referenceFalseKryoSerializer.asBytes(new ArrayList<>()));

    ContainerInstanceSyncPerpetualTaskParams params =
        ContainerInstanceSyncPerpetualTaskParams.newBuilder()
            .setContainerType("")
            .setContainerServicePerpetualTaskParams(ContainerServicePerpetualTaskParams.newBuilder()
                                                        .setSettingAttribute(configBytes)
                                                        .setEncryptionDetails(encryptionDetailsBytes)
                                                        .setNamespace("namespace")
                                                        .setContainerSvcName("service")
                                                        .setRegion("us-east-1")
                                                        .setClusterName("cluster")
                                                        .build())
            .build();
    return PerpetualTaskExecutionParams.newBuilder()
        .setCustomizedParams(Any.pack(params))
        .setReferenceFalseKryoSerializer(true)
        .build();
  }

  private PerpetualTaskExecutionParams getContainerInstancePerpetualTaskParamsHelm() {
    AwsConfig awsConfig = AwsConfig.builder().accountId("accountId").build();
    ByteString configBytes = ByteString.copyFrom(referenceFalseKryoSerializer.asBytes(
        SettingAttribute.builder().accountId("accountId").value(awsConfig).build()));
    ByteString encryptionDetailsBytes = ByteString.copyFrom(referenceFalseKryoSerializer.asBytes(new ArrayList<>()));

    ContainerInstanceSyncPerpetualTaskParams params =
        ContainerInstanceSyncPerpetualTaskParams.newBuilder()
            .setContainerType("")
            .setContainerServicePerpetualTaskParams(ContainerServicePerpetualTaskParams.newBuilder()
                                                        .setSettingAttribute(configBytes)
                                                        .setEncryptionDetails(encryptionDetailsBytes)
                                                        .setNamespace("namespace")
                                                        .setRegion("us-east-1")
                                                        .setClusterName("cluster")
                                                        .setReleaseName("release")
                                                        .build())
            .build();
    return PerpetualTaskExecutionParams.newBuilder()
        .setCustomizedParams(Any.pack(params))
        .setReferenceFalseKryoSerializer(true)
        .build();
  }

  private PerpetualTaskExecutionParams getK8sPerpetualTaskParams() {
    ByteString configBytes = ByteString.copyFrom(
        referenceFalseKryoSerializer.asBytes(K8sClusterConfig.builder().namespace("namespace").build()));

    ContainerInstanceSyncPerpetualTaskParams params =
        ContainerInstanceSyncPerpetualTaskParams.newBuilder()
            .setContainerType("K8S")
            .setK8SContainerPerpetualTaskParams(K8sContainerInstanceSyncPerpetualTaskParams.newBuilder()
                                                    .setK8SClusterConfig(configBytes)
                                                    .setAccountId("accountId")
                                                    .setNamespace("namespace")
                                                    .setReleaseName("release")
                                                    .build())
            .build();
    return PerpetualTaskExecutionParams.newBuilder()
        .setCustomizedParams(Any.pack(params))
        .setReferenceFalseKryoSerializer(true)
        .build();
  }
}
