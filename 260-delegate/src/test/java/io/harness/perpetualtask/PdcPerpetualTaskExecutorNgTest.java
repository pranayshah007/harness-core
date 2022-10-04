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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.DelegateTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.instancesync.SshWinrmInstanceSyncPerpetualTaskResponse;
import io.harness.delegate.beans.instancesync.info.PdcServerInstanceInfo;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.perpetualtask.instancesync.PdcPerpetualTaskParamsNg;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import software.wings.beans.HostReachabilityInfo;
import software.wings.utils.HostValidationService;

import com.google.protobuf.Any;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import retrofit2.Call;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(CDP)
public class PdcPerpetualTaskExecutorNgTest extends DelegateTestBase {
  @Mock private DelegateAgentManagerClient delegateAgentManagerClient;
  @Mock private Call<RestResponse<Boolean>> call;
  @Mock private HostValidationService hostValidationService;

  @InjectMocks private PdcPerpetualTaskExecutorNg pdcPerpetualTaskExecutorNg;
  @Captor private ArgumentCaptor<SshWinrmInstanceSyncPerpetualTaskResponse> perpetualTaskResponseCaptor;
  private static final String SUCCESS = "success";
  private static final String PERPETUAL_TASK_ID = "perpetualTaskId";
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String HOST1 = "HOST1";
  private static final String HOST2 = "HOST2";
  private static final String SERVICE = ServiceSpecType.SSH;

  @Before
  public void setUp() throws IOException {
    doReturn(call)
        .when(delegateAgentManagerClient)
        .processInstanceSyncNGResult(any(), any(), perpetualTaskResponseCaptor.capture());
    doReturn(retrofit2.Response.success(SUCCESS)).when(call).execute();
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testRunOnce() {
    List<HostReachabilityInfo> infos = new ArrayList<>();
    infos.add(HostReachabilityInfo.builder().hostName(HOST1).reachable(true).build());
    infos.add(HostReachabilityInfo.builder().hostName(HOST2).reachable(false).build());
    doReturn(infos).when(hostValidationService).validateReachability(any(), anyInt());

    PerpetualTaskExecutionParams perpetualTaskExecutionParams = getPerpetualTaskExecutionParams();
    PerpetualTaskResponse perpetualTaskResponse = pdcPerpetualTaskExecutorNg.runOnce(
        PerpetualTaskId.newBuilder().setId(PERPETUAL_TASK_ID).build(), perpetualTaskExecutionParams, Instant.EPOCH);

    assertThat(perpetualTaskResponseCaptor.getValue()).isInstanceOf(SshWinrmInstanceSyncPerpetualTaskResponse.class);
    SshWinrmInstanceSyncPerpetualTaskResponse value = perpetualTaskResponseCaptor.getValue();
    System.out.println(value);
    assertThat(value.getServerInstanceDetails()
                   .stream()
                   .map(instance -> ((PdcServerInstanceInfo) instance).getHost())
                   .collect(Collectors.toList()))
        .contains(HOST1);

    assertThat(perpetualTaskResponse).isNotNull();
    assertThat(perpetualTaskResponse.getResponseCode()).isEqualTo(200);
    assertThat(perpetualTaskResponse.getResponseMessage()).isEqualTo(SUCCESS);
  }

  private PerpetualTaskExecutionParams getPerpetualTaskExecutionParams() {
    PdcPerpetualTaskParamsNg message = PdcPerpetualTaskParamsNg.newBuilder()
                                           .addHosts(HOST1)
                                           .addHosts(HOST2)
                                           .setAccountId(ACCOUNT_ID)
                                           .setServiceType(SERVICE)
                                           .setPort(1234)
                                           .build();

    return PerpetualTaskExecutionParams.newBuilder().setCustomizedParams(Any.pack(message)).build();
  }
}
