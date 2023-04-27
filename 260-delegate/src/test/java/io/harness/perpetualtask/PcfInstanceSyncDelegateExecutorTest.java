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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;

import io.harness.DelegateTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.pcf.CfInternalConfig;
import io.harness.delegate.task.cf.PcfDelegateTaskHelper;
import io.harness.delegate.task.pcf.CfCommandResponse;
import io.harness.delegate.task.pcf.request.CfInstanceSyncRequest;
import io.harness.delegate.task.pcf.response.CfCommandExecutionResponse;
import io.harness.delegate.task.pcf.response.CfInstanceSyncResponse;
import io.harness.logging.CommandExecutionStatus;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.instancesync.PcfInstanceSyncPerpetualTaskParams;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
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
public class PcfInstanceSyncDelegateExecutorTest extends DelegateTestBase {
  private static final String PERPETUAL_TASK_ID = "perpetualTaskId";
  private static final String APP_NAME = "appName";
  private static final String ORG_NAME = "orgName";
  private static final String SPACE = "space";
  private static final String SUCCESS = "success";
  private static final String NULL_CF_INSTANCE_SYNC_RESPONSE_RETURNED = "Null cfInstanceSyncResponse returned";

  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Mock private DelegateAgentManagerClient delegateAgentManagerClient;
  @Mock private Call<RestResponse<Boolean>> call;
  @Mock private PcfDelegateTaskHelper pcfDelegateTaskHelper;

  @InjectMocks private PcfInstanceSyncDelegateExecutor pcfInstanceSyncDelegateExecutor;

  @Captor private ArgumentCaptor<CfCommandExecutionResponse> perpetualTaskResponseCaptor;

  @Before
  public void setUp() throws IOException {
    on(pcfInstanceSyncDelegateExecutor).set("referenceFalseKryoSerializer", referenceFalseKryoSerializer);
    doReturn(call)
        .when(delegateAgentManagerClient)
        .publishInstanceSyncResultV2(any(), any(), perpetualTaskResponseCaptor.capture());
    doReturn(retrofit2.Response.success(SUCCESS)).when(call).execute();
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testRunOnceWithCfInstanceSyncResponse() {
    PerpetualTaskExecutionParams perpetualTaskExecutionParams = getPerpetualTaskExecutionParams();

    doReturn(CfCommandExecutionResponse.builder()
                 .pcfCommandResponse(CfInstanceSyncResponse.builder()
                                         .name(APP_NAME)
                                         .organization(ORG_NAME)
                                         .space(SPACE)
                                         .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                         .build())
                 .build())
        .when(pcfDelegateTaskHelper)
        .getPcfCommandExecutionResponse(any(CfInstanceSyncRequest.class), any(), anyBoolean(), any());

    PerpetualTaskResponse perpetualTaskResponse = pcfInstanceSyncDelegateExecutor.runOnce(
        PerpetualTaskId.newBuilder().setId(PERPETUAL_TASK_ID).build(), perpetualTaskExecutionParams, Instant.EPOCH);

    assertThat(perpetualTaskResponseCaptor.getValue()).isInstanceOf(CfCommandExecutionResponse.class);
    CfCommandExecutionResponse cfCommandExecutionResponse = perpetualTaskResponseCaptor.getValue();
    CfCommandResponse pcfCommandResponse = cfCommandExecutionResponse.getPcfCommandResponse();

    assertThat(pcfCommandResponse instanceof CfInstanceSyncResponse).isTrue();
    CfInstanceSyncResponse cfInstanceSyncResponse = (CfInstanceSyncResponse) pcfCommandResponse;
    assertThat(cfInstanceSyncResponse.getName()).isEqualTo(APP_NAME);
    assertThat(cfInstanceSyncResponse.getOrganization()).isEqualTo(ORG_NAME);
    assertThat(cfInstanceSyncResponse.getSpace()).isEqualTo(SPACE);
    assertThat(cfInstanceSyncResponse.getOutput()).isNull();

    assertThat(perpetualTaskResponse).isNotNull();
    assertThat(perpetualTaskResponse.getResponseCode()).isEqualTo(200);
    assertThat(perpetualTaskResponse.getResponseMessage()).isEqualTo(SUCCESS);
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testRunOnceWithoutCfInstanceSyncResponse() {
    PerpetualTaskExecutionParams perpetualTaskExecutionParams = getPerpetualTaskExecutionParams();

    doReturn(CfCommandExecutionResponse.builder().pcfCommandResponse(null).build())
        .when(pcfDelegateTaskHelper)
        .getPcfCommandExecutionResponse(any(CfInstanceSyncRequest.class), any(), anyBoolean(), any());

    PerpetualTaskResponse perpetualTaskResponse = pcfInstanceSyncDelegateExecutor.runOnce(
        PerpetualTaskId.newBuilder().setId(PERPETUAL_TASK_ID).build(), perpetualTaskExecutionParams, Instant.EPOCH);

    assertThat(perpetualTaskResponseCaptor.getValue()).isInstanceOf(CfCommandExecutionResponse.class);
    CfCommandExecutionResponse cfCommandExecutionResponse = perpetualTaskResponseCaptor.getValue();
    CfCommandResponse pcfCommandResponse = cfCommandExecutionResponse.getPcfCommandResponse();

    assertThat(pcfCommandResponse instanceof CfInstanceSyncResponse).isTrue();
    CfInstanceSyncResponse cfInstanceSyncResponse = (CfInstanceSyncResponse) pcfCommandResponse;
    assertThat(cfInstanceSyncResponse.getName()).isEqualTo(APP_NAME);
    assertThat(cfInstanceSyncResponse.getOrganization()).isEqualTo(ORG_NAME);
    assertThat(cfInstanceSyncResponse.getSpace()).isEqualTo(SPACE);
    assertThat(cfInstanceSyncResponse.getOutput()).isEqualTo(NULL_CF_INSTANCE_SYNC_RESPONSE_RETURNED);

    assertThat(perpetualTaskResponse).isNotNull();
    assertThat(perpetualTaskResponse.getResponseCode()).isEqualTo(200);
    assertThat(perpetualTaskResponse.getResponseMessage()).isEqualTo(SUCCESS);
  }

  private PerpetualTaskExecutionParams getPerpetualTaskExecutionParams() {
    PcfInstanceSyncPerpetualTaskParams message =
        PcfInstanceSyncPerpetualTaskParams.newBuilder()
            .setApplicationName(APP_NAME)
            .setOrgName(ORG_NAME)
            .setSpace(SPACE)
            .setEncryptedData(ByteString.copyFrom(referenceFalseKryoSerializer.asBytes(Collections.emptyList())))
            .setPcfConfig(ByteString.copyFrom(referenceFalseKryoSerializer.asBytes(CfInternalConfig.builder().build())))
            .build();
    return PerpetualTaskExecutionParams.newBuilder()
        .setCustomizedParams(Any.pack(message))
        .setReferenceFalseKryoSerializer(true)
        .build();
  }
}
