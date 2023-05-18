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
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.DelegateTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.instancesync.PdcInstanceSyncPerpetualTaskParams;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import software.wings.beans.HostReachabilityInfo;
import software.wings.beans.dto.SettingAttribute;
import software.wings.service.impl.aws.model.response.HostReachabilityResponse;
import software.wings.utils.HostValidationService;

import com.google.inject.Inject;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
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
public class PdcInstanceSyncExecutorTest extends DelegateTestBase {
  @Inject private KryoSerializer kryoSerializer;
  @Mock private DelegateAgentManagerClient delegateAgentManagerClient;
  @Mock private Call<RestResponse<Boolean>> call;
  @Mock private HostValidationService hostValidationService;

  @InjectMocks private PdcInstanceSyncExecutor pdcInstanceSyncExecutor;
  @Captor private ArgumentCaptor<HostReachabilityResponse> perpetualTaskResponseCaptor;
  private static final String SUCCESS = "success";
  private static final String PERPETUAL_TASK_ID = "perpetualTaskId";

  @Before
  public void setUp() throws IOException {
    on(pdcInstanceSyncExecutor).set("kryoSerializer", kryoSerializer);
    doReturn(call)
        .when(delegateAgentManagerClient)
        .publishInstanceSyncResult(any(), any(), perpetualTaskResponseCaptor.capture());
    doReturn(retrofit2.Response.success(SUCCESS)).when(call).execute();
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testRunOnce() {
    List<HostReachabilityInfo> infos = new ArrayList<>();
    infos.add(HostReachabilityInfo.builder().hostName("h1").reachable(true).build());
    infos.add(HostReachabilityInfo.builder().hostName("h2").reachable(true).build());
    PerpetualTaskExecutionParams perpetualTaskExecutionParams = getPerpetualTaskExecutionParams();
    doReturn(infos).when(hostValidationService).validateReachability(any(), any());

    PerpetualTaskResponse perpetualTaskResponse = pdcInstanceSyncExecutor.runOnce(
        PerpetualTaskId.newBuilder().setId(PERPETUAL_TASK_ID).build(), perpetualTaskExecutionParams, Instant.EPOCH);

    assertThat(perpetualTaskResponseCaptor.getValue()).isInstanceOf(HostReachabilityResponse.class);
    HostReachabilityResponse value = perpetualTaskResponseCaptor.getValue();
    assertThat(value.getHostReachabilityInfoList()).isEqualTo(infos);

    assertThat(perpetualTaskResponse).isNotNull();
    assertThat(perpetualTaskResponse.getResponseCode()).isEqualTo(200);
    assertThat(perpetualTaskResponse.getResponseMessage()).isEqualTo(SUCCESS);
  }

  private PerpetualTaskExecutionParams getPerpetualTaskExecutionParams() {
    PdcInstanceSyncPerpetualTaskParams message =
        PdcInstanceSyncPerpetualTaskParams.newBuilder()
            .addHostNames("h1")
            .addHostNames("h2")
            .setEncryptedData(ByteString.copyFrom(kryoSerializer.asBytes(Collections.emptyList())))
            .setSettingAttribute(ByteString.copyFrom(kryoSerializer.asBytes(SettingAttribute.builder().build())))
            .build();

    return PerpetualTaskExecutionParams.newBuilder().setCustomizedParams(Any.pack(message)).build();
  }
}
