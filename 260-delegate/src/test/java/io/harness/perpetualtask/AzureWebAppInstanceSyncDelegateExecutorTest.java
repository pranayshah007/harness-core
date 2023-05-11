/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask;

import static io.harness.rule.OwnerRule.IVAN;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.DelegateTestBase;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.task.azure.AzureTaskExecutionResponse;
import io.harness.delegate.task.azure.AzureTaskResponse;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureAppDeploymentData;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureWebAppListWebAppInstancesResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.instancesync.AzureWebAppInstanceSyncPerpetualProtoTaskParams;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import software.wings.beans.AzureConfig;
import software.wings.delegatetasks.azure.appservice.webapp.taskhandler.AzureWebAppListWebAppInstancesTaskHandler;
import software.wings.service.intfc.security.EncryptionService;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
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
public class AzureWebAppInstanceSyncDelegateExecutorTest extends DelegateTestBase {
  @Mock private EncryptionService mockEncryptionService;
  @Mock private AzureWebAppListWebAppInstancesTaskHandler mockListDeploymentDataTaskHandler;
  @Mock private DelegateAgentManagerClient mockDelegateAgentManagerClient;
  @Mock private Call<RestResponse<Boolean>> mockCall;

  @InjectMocks private AzureWebAppInstanceSyncDelegateExecutor executor;
  @Inject private KryoSerializer kryoSerializer;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;

  @Before
  public void setUp() throws IOException {
    on(executor).set("referenceFalseKryoSerializer", referenceFalseKryoSerializer);
    when(mockDelegateAgentManagerClient.publishInstanceSyncResultV2(
             anyString(), anyString(), any(DelegateResponseData.class)))
        .thenReturn(mockCall);
    doReturn(retrofit2.Response.success("success")).when(mockCall).execute();
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testRunOnce() {
    doReturn(AzureWebAppListWebAppInstancesResponse.builder()
                 .deploymentData(singletonList(AzureAppDeploymentData.builder()
                                                   .appName("appName")
                                                   .appServicePlanId("appServicePlanId")
                                                   .deploySlot("slotName")
                                                   .deploySlotId("deploySlotId")
                                                   .instanceId("instanceId")
                                                   .hostName("hostName")
                                                   .instanceIp("instanceIp")
                                                   .instanceName("instanceName")
                                                   .instanceState("running")
                                                   .instanceType("instanceType")
                                                   .resourceGroup("resourceGroup")
                                                   .subscriptionId("subscriptionId")
                                                   .build()))
                 .build())
        .when(mockListDeploymentDataTaskHandler)
        .executeTaskInternal(any(), any(), any());
    ArgumentCaptor<AzureTaskExecutionResponse> argumentCaptor =
        ArgumentCaptor.forClass(AzureTaskExecutionResponse.class);

    PerpetualTaskResponse perpetualTaskResponse = executor.runOnce(
        PerpetualTaskId.newBuilder().setId("task-id").build(), getPerpetualTaskParams(), Instant.now());

    verify(mockDelegateAgentManagerClient, times(1))
        .publishInstanceSyncResultV2(eq("task-id"), eq("acct-id"), argumentCaptor.capture());

    AzureTaskExecutionResponse response = argumentCaptor.getValue();
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    AzureTaskResponse azureTaskResponse = response.getAzureTaskResponse();
    assertThat(azureTaskResponse instanceof AzureWebAppListWebAppInstancesResponse).isTrue();
    AzureWebAppListWebAppInstancesResponse azureWebAppListWebAppInstancesResponse =
        (AzureWebAppListWebAppInstancesResponse) azureTaskResponse;
    assertThat(azureWebAppListWebAppInstancesResponse.getDeploymentData()).isNotNull();
    assertThat(azureWebAppListWebAppInstancesResponse.getDeploymentData().size()).isEqualTo(1);
    assertThat(azureWebAppListWebAppInstancesResponse.getDeploymentData().get(0).getAppName()).isEqualTo("appName");
    assertThat(azureWebAppListWebAppInstancesResponse.getDeploymentData().get(0).getDeploySlot()).isEqualTo("slotName");
    assertThat(azureWebAppListWebAppInstancesResponse.getDeploymentData().get(0).getAppServicePlanId())
        .isEqualTo("appServicePlanId");
    assertThat(azureWebAppListWebAppInstancesResponse.getDeploymentData().get(0).getDeploySlotId())
        .isEqualTo("deploySlotId");
    assertThat(azureWebAppListWebAppInstancesResponse.getDeploymentData().get(0).getInstanceId())
        .isEqualTo("instanceId");
    assertThat(azureWebAppListWebAppInstancesResponse.getDeploymentData().get(0).getHostName()).isEqualTo("hostName");
    assertThat(azureWebAppListWebAppInstancesResponse.getDeploymentData().get(0).getInstanceIp())
        .isEqualTo("instanceIp");
    assertThat(azureWebAppListWebAppInstancesResponse.getDeploymentData().get(0).getInstanceName())
        .isEqualTo("instanceName");
    assertThat(azureWebAppListWebAppInstancesResponse.getDeploymentData().get(0).getInstanceState())
        .isEqualTo("running");
    assertThat(azureWebAppListWebAppInstancesResponse.getDeploymentData().get(0).getInstanceType())
        .isEqualTo("instanceType");
    assertThat(azureWebAppListWebAppInstancesResponse.getDeploymentData().get(0).getResourceGroup())
        .isEqualTo("resourceGroup");
    assertThat(azureWebAppListWebAppInstancesResponse.getDeploymentData().get(0).getSubscriptionId())
        .isEqualTo("subscriptionId");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testRunOnceWithException() {
    doThrow(new InvalidRequestException("Unable to fetch instance list"))
        .when(mockListDeploymentDataTaskHandler)
        .executeTaskInternal(any(), any(), any());
    ArgumentCaptor<AzureTaskExecutionResponse> argumentCaptor =
        ArgumentCaptor.forClass(AzureTaskExecutionResponse.class);

    PerpetualTaskResponse perpetualTaskResponse = executor.runOnce(
        PerpetualTaskId.newBuilder().setId("task-id").build(), getPerpetualTaskParams(), Instant.now());

    verify(mockDelegateAgentManagerClient, times(1))
        .publishInstanceSyncResultV2(eq("task-id"), eq("acct-id"), argumentCaptor.capture());

    AzureTaskExecutionResponse response = argumentCaptor.getValue();
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(response.getErrorMessage()).isEqualTo("Unable to fetch instance list");
  }

  private PerpetualTaskExecutionParams getPerpetualTaskParams() {
    ByteString azureConfigBytes =
        ByteString.copyFrom(referenceFalseKryoSerializer.asBytes(AzureConfig.builder().accountId("acct-id").build()));
    ByteString azureEncryptedDetailsBytes =
        ByteString.copyFrom(referenceFalseKryoSerializer.asBytes(new ArrayList<>()));
    AzureWebAppInstanceSyncPerpetualProtoTaskParams taskParams =
        AzureWebAppInstanceSyncPerpetualProtoTaskParams.newBuilder()
            .setAzureConfig(azureConfigBytes)
            .setAzureEncryptedData(azureEncryptedDetailsBytes)
            .setSubscriptionId("subscriptionId")
            .setResourceGroupName("resourceGroupName")
            .setAppName("appName")
            .setSlotName("slotName")
            .build();
    return PerpetualTaskExecutionParams.newBuilder()
        .setCustomizedParams(Any.pack(taskParams))
        .setReferenceFalseKryoSerializer(true)
        .build();
  }
}
