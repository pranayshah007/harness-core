/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ARVIND;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.DelegateTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.azure.AzureConfigContext;
import io.harness.delegate.beans.azure.response.AzureHostResponse;
import io.harness.delegate.beans.azure.response.AzureHostsResponse;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.instancesync.SshWinrmInstanceSyncPerpetualTaskResponse;
import io.harness.delegate.beans.instancesync.info.AzureSshWinrmServerInstanceInfo;
import io.harness.delegate.task.ssh.AzureSshInfraDelegateConfig;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.perpetualtask.instancesync.AzureSshInstanceSyncPerpetualTaskParamsNg;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import software.wings.delegatetasks.azure.AzureAsyncTaskHelper;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.stream.Collectors;
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
public class AzureSshWinrmInstanceSyncPerpetualTaskExecutorTest extends DelegateTestBase {
  @Mock private DelegateAgentManagerClient delegateAgentManagerClient;
  @Mock private Call<RestResponse<Boolean>> call;
  @Mock private AzureAsyncTaskHelper azureAsyncTaskHelper;
  @Mock private KryoSerializer kryoSerializer;

  @InjectMocks private AzureSshWinrmInstanceSyncPerpetualTaskExecutor executor;
  @Captor private ArgumentCaptor<SshWinrmInstanceSyncPerpetualTaskResponse> perpetualTaskResponseCaptor;
  private static final String SUCCESS = "success";
  private static final String PERPETUAL_TASK_ID = "perpetualTaskId";
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String HOST1 = "HOST1";
  private static final String HOST2 = "HOST2";
  private static final String SERVICE = ServiceSpecType.SSH;
  byte[] bytes = {70};

  @Before
  public void setUp() throws IOException {
    doReturn(call)
        .when(delegateAgentManagerClient)
        .processInstanceSyncNGResult(any(), any(), perpetualTaskResponseCaptor.capture());
    doReturn(retrofit2.Response.success(SUCCESS)).when(call).execute();
    doReturn(AzureSshInfraDelegateConfig.sshAzureBuilder()
                 .encryptionDataDetails(new ArrayList<>())
                 .azureConnectorDTO(AzureConnectorDTO.builder().build())
                 .subscriptionId("S")
                 .resourceGroup("R")
                 .tags(new HashMap<>())
                 .hostConnectionType("PublicIP")
                 .build())
        .when(kryoSerializer)
        .asObject(any(byte[].class));
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testRunOnce() throws IOException {
    doReturn(
        AzureHostsResponse.builder().hosts(Arrays.asList(AzureHostResponse.builder().hostName(HOST1).build())).build())
        .when(azureAsyncTaskHelper)
        .listHosts(any(AzureConfigContext.class));

    PerpetualTaskExecutionParams perpetualTaskExecutionParams = getPerpetualTaskExecutionParams();
    PerpetualTaskResponse perpetualTaskResponse = executor.runOnce(
        PerpetualTaskId.newBuilder().setId(PERPETUAL_TASK_ID).build(), perpetualTaskExecutionParams, Instant.EPOCH);

    assertThat(perpetualTaskResponseCaptor.getValue()).isInstanceOf(SshWinrmInstanceSyncPerpetualTaskResponse.class);
    SshWinrmInstanceSyncPerpetualTaskResponse value = perpetualTaskResponseCaptor.getValue();
    System.out.println(value);
    assertThat(value.getServerInstanceDetails()
                   .stream()
                   .map(instance -> ((AzureSshWinrmServerInstanceInfo) instance).getHost())
                   .collect(Collectors.toList()))
        .contains(HOST1);

    assertThat(perpetualTaskResponse).isNotNull();
    assertThat(perpetualTaskResponse.getResponseCode()).isEqualTo(200);
    assertThat(perpetualTaskResponse.getResponseMessage()).isEqualTo(SUCCESS);
  }

  private PerpetualTaskExecutionParams getPerpetualTaskExecutionParams() {
    AzureSshInstanceSyncPerpetualTaskParamsNg message =
        AzureSshInstanceSyncPerpetualTaskParamsNg.newBuilder()
            .addHosts(HOST1)
            .addHosts(HOST2)
            .setAccountId(ACCOUNT_ID)
            .setServiceType(SERVICE)
            .setAzureSshWinrmInfraDelegateConfig(ByteString.copyFrom(bytes))
            .build();

    return PerpetualTaskExecutionParams.newBuilder().setCustomizedParams(Any.pack(message)).build();
  }
}
