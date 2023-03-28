/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask;

import static io.harness.rule.OwnerRule.SATYAM;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.DelegateTestBase;
import io.harness.azure.model.AzureVMData;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.task.azure.response.AzureVMSSListVMDataResponse;
import io.harness.delegate.task.azure.response.AzureVMSSTaskExecutionResponse;
import io.harness.delegate.task.azure.response.AzureVMSSTaskResponse;
import io.harness.logging.CommandExecutionStatus;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.instancesync.AzureVmssInstanceSyncPerpetualTaskParams;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import software.wings.beans.AzureConfig;
import software.wings.delegatetasks.azure.taskhandler.AzureVMSSSyncTaskHandler;
import software.wings.service.intfc.security.EncryptionService;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.time.Instant;
import java.util.ArrayList;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import retrofit2.Call;

@RunWith(MockitoJUnitRunner.class)
public class AzureVMSSInstanceSyncDelegateExecutorTest extends DelegateTestBase {
  @Mock private EncryptionService mockEncryptionService;
  @Mock private AzureVMSSSyncTaskHandler mockAzureVMSSSyncTaskHandler;
  @Mock private DelegateAgentManagerClient mockDelegateAgentManagerClient;
  @Mock private Call<RestResponse<Boolean>> mockCall;

  @InjectMocks private AzureVMSSInstanceSyncDelegateExecutor executor;
  @Inject private KryoSerializer kryoSerializer;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;

  @Before
  public void setUp() {
    on(executor).set("referenceFalseKryoSerializer", referenceFalseKryoSerializer);
    when(mockDelegateAgentManagerClient.publishInstanceSyncResultV2(
             anyString(), anyString(), any(DelegateResponseData.class)))
        .thenReturn(mockCall);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testRunOnce() {
    doReturn(AzureVMSSTaskExecutionResponse.builder()
                 .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                 .azureVMSSTaskResponse(
                     AzureVMSSListVMDataResponse.builder()
                         .vmssId("vmss-id")
                         .vmData(singletonList(AzureVMData.builder().id("vm-id").ip("ip").publicDns("pub-dns").build()))
                         .build())
                 .build())
        .when(mockAzureVMSSSyncTaskHandler)
        .executeTask(any(), any());
    ArgumentCaptor<AzureVMSSTaskExecutionResponse> argumentCaptor =
        ArgumentCaptor.forClass(AzureVMSSTaskExecutionResponse.class);
    PerpetualTaskResponse perpetualTaskResponse = executor.runOnce(
        PerpetualTaskId.newBuilder().setId("task-id").build(), getPerpetualTaskParams(), Instant.now());
    verify(mockDelegateAgentManagerClient, times(1))
        .publishInstanceSyncResultV2(eq("task-id"), eq("acct-id"), argumentCaptor.capture());
    AzureVMSSTaskExecutionResponse response = argumentCaptor.getValue();
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    AzureVMSSTaskResponse azureVMSSTaskResponse = response.getAzureVMSSTaskResponse();
    assertThat(azureVMSSTaskResponse instanceof AzureVMSSListVMDataResponse).isTrue();
    AzureVMSSListVMDataResponse azureVMSSListVMDataResponse = (AzureVMSSListVMDataResponse) azureVMSSTaskResponse;
    assertThat(azureVMSSListVMDataResponse.getVmssId()).isEqualTo("vmss-id");
    assertThat(azureVMSSListVMDataResponse.getVmData()).isNotNull();
    assertThat(azureVMSSListVMDataResponse.getVmData().size()).isEqualTo(1);
    assertThat(azureVMSSListVMDataResponse.getVmData().get(0).getId()).isEqualTo("vm-id");
    assertThat(azureVMSSListVMDataResponse.getVmData().get(0).getIp()).isEqualTo("ip");
    assertThat(azureVMSSListVMDataResponse.getVmData().get(0).getPublicDns()).isEqualTo("pub-dns");
  }

  private PerpetualTaskExecutionParams getPerpetualTaskParams() {
    ByteString azureConfigBytes =
        ByteString.copyFrom(referenceFalseKryoSerializer.asBytes(AzureConfig.builder().accountId("acct-id").build()));
    ByteString azureEncryptedDetailsBytes =
        ByteString.copyFrom(referenceFalseKryoSerializer.asBytes(new ArrayList<>()));

    AzureVmssInstanceSyncPerpetualTaskParams taskParams = AzureVmssInstanceSyncPerpetualTaskParams.newBuilder()
                                                              .setAzureConfig(azureConfigBytes)
                                                              .setAzureEncryptedData(azureEncryptedDetailsBytes)
                                                              .setSubscriptionId("subs-id")
                                                              .setResourceGroupName("res-gp")
                                                              .setVmssId("vmss-id")
                                                              .build();
    return PerpetualTaskExecutionParams.newBuilder()
        .setCustomizedParams(Any.pack(taskParams))
        .setReferenceFalseKryoSerializer(true)
        .build();
  }
}
