/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.azure.AzureTestUtils.APP_NAME;
import static io.harness.delegate.task.azure.AzureTestUtils.DEPLOYMENT_SLOT;
import static io.harness.delegate.task.azure.AzureTestUtils.RESOURCE_GROUP;
import static io.harness.delegate.task.azure.AzureTestUtils.SUBSCRIPTION_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.DelegateTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.model.AzureConfig;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.azureconnector.AzureAuthDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureClientKeyCertDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialType;
import io.harness.delegate.beans.connector.azureconnector.AzureManualDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureSecretType;
import io.harness.delegate.beans.instancesync.AzureWebAppInstanceSyncPerpetualTaskResponse;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.AzureWebAppServerInstanceInfo;
import io.harness.delegate.task.azure.AzureTestUtils;
import io.harness.delegate.task.azure.appservice.webapp.ng.AzureWebAppInfraDelegateConfig;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureAppDeploymentData;
import io.harness.delegate.task.azure.common.AzureAppServiceService;
import io.harness.delegate.task.azure.common.AzureConnectorMapper;
import io.harness.encryption.SecretRefData;
import io.harness.logging.CommandExecutionStatus;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.instancesync.AzureWebAppDeploymentRelease;
import io.harness.perpetualtask.instancesync.AzureWebAppNGInstanceSyncPerpetualTaskParams;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
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
public class AzureWebAppInstanceSyncPerpetualTaskExecutorTest extends DelegateTestBase {
  private static final String PERPETUAL_TASK_ID = "perpetualTaskId";
  private static final String ACCOUNT_ID = "accountId";

  @Inject private KryoSerializer kryoSerializer;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;

  @Mock private Call<RestResponse<Boolean>> call;
  @Mock private AzureAppServiceService azureAppServiceService;
  @Mock private AzureConnectorMapper azureConnectorMapper;
  @Mock private DelegateAgentManagerClient delegateAgentManagerClient;
  @Mock private SecretDecryptionService secretDecryptionService;

  @Captor private ArgumentCaptor<AzureWebAppInstanceSyncPerpetualTaskResponse> perpetualTaskResponseCaptor;
  private final AzureConfig azureConfig = AzureConfig.builder().build();

  @InjectMocks AzureWebAppInstanceSyncPerpetualTaskExecutor azureWebAppInstanceSyncPerpetualTaskExecutor;
  AzureAppDeploymentData azureAppDeploymentData = buildAzureAppDeploymentData();

  @Before
  public void setUp() throws IOException {
    on(azureWebAppInstanceSyncPerpetualTaskExecutor).set("referenceFalseKryoSerializer", referenceFalseKryoSerializer);
    doReturn(call)
        .when(delegateAgentManagerClient)
        .processInstanceSyncNGResult(anyString(), anyString(), perpetualTaskResponseCaptor.capture());
    doReturn(retrofit2.Response.success("success")).when(call).execute();

    doReturn(azureConfig).when(azureConnectorMapper).toAzureConfig(any(AzureConnectorDTO.class));
    doReturn(null).when(secretDecryptionService).decrypt(any(), anyList());
  }

  @Test
  @Owner(developers = OwnerRule.VLICA)
  @Category(UnitTests.class)
  public void testRunOnceAndPublishServerInstanceInfoList() {
    List<AzureWebAppDeploymentRelease> deploymentReleases = List.of(createAzureWebAppDeploymentRelease());

    doReturn(Collections.singletonList(azureAppDeploymentData))
        .when(azureAppServiceService)
        .fetchDeploymentData(any(), anyString());

    AzureWebAppNGInstanceSyncPerpetualTaskParams message =
        AzureWebAppNGInstanceSyncPerpetualTaskParams.newBuilder()
            .setAccountId(ACCOUNT_ID)
            .addAllAzureWebAppDeploymentReleaseList(deploymentReleases)
            .build();

    PerpetualTaskExecutionParams perpetualTaskExecutionParams = PerpetualTaskExecutionParams.newBuilder()
                                                                    .setCustomizedParams(Any.pack(message))
                                                                    .setReferenceFalseKryoSerializer(true)
                                                                    .build();

    azureWebAppInstanceSyncPerpetualTaskExecutor.runOnce(
        PerpetualTaskId.newBuilder().setId(PERPETUAL_TASK_ID).build(), perpetualTaskExecutionParams, Instant.EPOCH);

    assertThat(perpetualTaskResponseCaptor.getValue()).isInstanceOf(AzureWebAppInstanceSyncPerpetualTaskResponse.class);
    AzureWebAppInstanceSyncPerpetualTaskResponse azureWebAppInstanceSyncPerpetualTaskResponse =
        perpetualTaskResponseCaptor.getValue();
    assertThat(azureWebAppInstanceSyncPerpetualTaskResponse.getCommandExecutionStatus())
        .isEqualTo(CommandExecutionStatus.SUCCESS);
    List<ServerInstanceInfo> serverInstanceDetails =
        azureWebAppInstanceSyncPerpetualTaskResponse.getServerInstanceDetails();
    assertThat(serverInstanceDetails.size()).isEqualTo(1);

    AzureWebAppServerInstanceInfo azureWebAppServerInstanceInfo =
        (AzureWebAppServerInstanceInfo) serverInstanceDetails.get(0);
    assertThat(azureWebAppServerInstanceInfo.getInstanceType()).isEqualTo("Microsoft.Compute/webApp");
    assertThat(azureWebAppServerInstanceInfo.getInstanceName()).isEqualTo("instanceName");
    assertThat(azureWebAppServerInstanceInfo.getResourceGroup()).isEqualTo("resourceGroup");
    assertThat(azureWebAppServerInstanceInfo.getSubscriptionId()).isEqualTo("subscriptionId");
    assertThat(azureWebAppServerInstanceInfo.getAppName()).isEqualTo("appName");
    assertThat(azureWebAppServerInstanceInfo.getDeploySlot()).isEqualTo("deploySlotName");
    assertThat(azureWebAppServerInstanceInfo.getDeploySlotId()).isEqualTo("deploySlotId");
    assertThat(azureWebAppServerInstanceInfo.getHostName()).isEqualTo("hostName");
    assertThat(azureWebAppServerInstanceInfo.getInstanceIp()).isEqualTo("instanceIp");
    assertThat(azureWebAppServerInstanceInfo.getInstanceState()).isEqualTo("running");
    assertThat(azureWebAppServerInstanceInfo.getInstanceId()).isEqualTo("instanceId");
    assertThat(azureWebAppServerInstanceInfo.getAppServicePlanId()).isEqualTo("servicePlanId");

    verify(delegateAgentManagerClient, times(1)).processInstanceSyncNGResult(anyString(), anyString(), any());
  }

  @Test
  @Owner(developers = OwnerRule.VLICA)
  @Category(UnitTests.class)
  public void testRunOnceAndCatchErrorFromFetchDeploymentData() {
    doThrow(new IllegalArgumentException()).when(azureAppServiceService).fetchDeploymentData(any(), anyString());

    List<AzureWebAppDeploymentRelease> deploymentReleases = List.of(createAzureWebAppDeploymentRelease());
    AzureWebAppNGInstanceSyncPerpetualTaskParams message =
        AzureWebAppNGInstanceSyncPerpetualTaskParams.newBuilder()
            .setAccountId(ACCOUNT_ID)
            .addAllAzureWebAppDeploymentReleaseList(deploymentReleases)
            .build();

    PerpetualTaskExecutionParams perpetualTaskExecutionParams = PerpetualTaskExecutionParams.newBuilder()
                                                                    .setCustomizedParams(Any.pack(message))
                                                                    .setReferenceFalseKryoSerializer(true)
                                                                    .build();

    azureWebAppInstanceSyncPerpetualTaskExecutor.runOnce(
        PerpetualTaskId.newBuilder().setId(PERPETUAL_TASK_ID).build(), perpetualTaskExecutionParams, Instant.EPOCH);

    assertThat(perpetualTaskResponseCaptor.getValue()).isInstanceOf(AzureWebAppInstanceSyncPerpetualTaskResponse.class);
    AzureWebAppInstanceSyncPerpetualTaskResponse azureWebAppInstanceSyncPerpetualTaskResponse =
        perpetualTaskResponseCaptor.getValue();
    assertThat(azureWebAppInstanceSyncPerpetualTaskResponse.getCommandExecutionStatus())
        .isEqualTo(CommandExecutionStatus.SUCCESS);
    List<ServerInstanceInfo> serverInstanceDetails =
        azureWebAppInstanceSyncPerpetualTaskResponse.getServerInstanceDetails();
    verify(delegateAgentManagerClient, times(1)).processInstanceSyncNGResult(anyString(), anyString(), any());
    assertThat(serverInstanceDetails.size()).isEqualTo(0);
  }

  private AzureAppDeploymentData buildAzureAppDeploymentData() {
    return AzureAppDeploymentData.builder()
        .subscriptionId("subscriptionId")
        .resourceGroup("resourceGroup")
        .instanceState("running")
        .instanceType("Microsoft.Compute/webApp")
        .instanceName("instanceName")
        .appName("appName")
        .deploySlot("deploySlotName")
        .deploySlotId("deploySlotId")
        .hostName("hostName")
        .instanceIp("instanceIp")
        .instanceId("instanceId")
        .appServicePlanId("servicePlanId")
        .build();
  }

  private AzureWebAppDeploymentRelease createAzureWebAppDeploymentRelease() {
    AzureConnectorDTO azureConnectorDTO =
        AzureConnectorDTO.builder()
            .credential(AzureCredentialDTO.builder()
                            .azureCredentialType(AzureCredentialType.MANUAL_CREDENTIALS)
                            .config(AzureManualDetailsDTO.builder()
                                        .authDTO(AzureAuthDTO.builder()
                                                     .azureSecretType(AzureSecretType.KEY_CERT)
                                                     .credentials(AzureClientKeyCertDTO.builder()
                                                                      .clientCertRef(SecretRefData.builder().build())
                                                                      .build())
                                                     .build())
                                        .build())
                            .build())
            .build();

    AzureWebAppInfraDelegateConfig azureWebAppInfraDelegateConfig =
        AzureTestUtils.createTestWebAppInfraDelegateConfig();
    azureWebAppInfraDelegateConfig.setAzureConnectorDTO(azureConnectorDTO);
    azureWebAppInfraDelegateConfig.setEncryptionDataDetails(Collections.emptyList());

    return AzureWebAppDeploymentRelease.newBuilder()
        .setSubscriptionId(SUBSCRIPTION_ID)
        .setResourceGroupName(RESOURCE_GROUP)
        .setAppName(APP_NAME)
        .setSlotName(DEPLOYMENT_SLOT)
        .setAzureWebAppInfraDelegateConfig(
            ByteString.copyFrom(referenceFalseKryoSerializer.asBytes(azureWebAppInfraDelegateConfig)))
        .build();
  }
}
