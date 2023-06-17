/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.VITALIE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import io.harness.DelegateTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.task.spot.SpotConfig;
import io.harness.connector.task.spot.SpotNgConfigMapper;
import io.harness.connector.task.spot.SpotPermanentTokenCredential;
import io.harness.delegate.beans.connector.spotconnector.SpotConnectorDTO;
import io.harness.delegate.beans.instancesync.SpotInstanceSyncPerpetualTaskResponse;
import io.harness.delegate.beans.instancesync.info.SpotServerInstanceInfo;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.instancesync.SpotinstAmiInstanceSyncPerpetualTaskParamsNg;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.spotinst.SpotInstHelperServiceDelegate;
import io.harness.spotinst.model.ElastiGroupInstanceHealth;

import com.google.inject.Inject;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(CDP)
public class SpotinstPerpetualTaskExecutorNgTest extends DelegateTestBase {
  @Mock private DelegateAgentManagerClient delegateAgentManagerClient;
  @Mock private Call<RestResponse<Boolean>> call;
  @Mock protected SpotInstHelperServiceDelegate spotInstHelperServiceDelegate;
  @Mock private SpotNgConfigMapper ngConfigMapper;

  @Inject KryoSerializer kryoSerializer;

  @InjectMocks private SpotinstPerpetualTaskExecutorNg executor;
  @Captor private ArgumentCaptor<SpotInstanceSyncPerpetualTaskResponse> perpetualTaskResponseCaptor;

  private static final String SUCCESS = "success";
  private static final String PERPETUAL_TASK_ID = "perpetualTaskId";
  private static final String ELASTIGROUP_ID = "elastigroup-id";

  @Before
  public void setUp() throws IOException {
    on(executor).set("kryoSerializer", kryoSerializer);
    doReturn(call)
        .when(delegateAgentManagerClient)
        .processInstanceSyncNGResult(any(), any(), perpetualTaskResponseCaptor.capture());
    doReturn(retrofit2.Response.success(SUCCESS)).when(call).execute();
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testRunOnce() throws Exception {
    doReturn(
        SpotConfig.builder()
            .credential(SpotPermanentTokenCredential.builder().appTokenId("random").spotAccountId("random").build())
            .build())
        .when(ngConfigMapper)
        .mapSpotConfigWithDecryption(any(), any());

    List<ElastiGroupInstanceHealth> elastiGroupInstanceHealthList =
        Arrays.asList(ElastiGroupInstanceHealth.builder().instanceId("id1").build(),
            ElastiGroupInstanceHealth.builder().instanceId("id2").build());

    doReturn(elastiGroupInstanceHealthList)
        .when(spotInstHelperServiceDelegate)
        .listElastiGroupInstancesHealth(anyString(), anyString(), anyString());

    PerpetualTaskExecutionParams perpetualTaskExecutionParams = getPerpetualTaskParams();
    PerpetualTaskResponse perpetualTaskResponse = executor.runOnce(
        PerpetualTaskId.newBuilder().setId(PERPETUAL_TASK_ID).build(), perpetualTaskExecutionParams, Instant.EPOCH);

    SpotInstanceSyncPerpetualTaskResponse value = perpetualTaskResponseCaptor.getValue();
    assertThat(value.getServerInstanceDetails()
                   .stream()
                   .map(instance -> ((SpotServerInstanceInfo) instance).getEc2InstanceId())
                   .collect(Collectors.toList()))
        .contains("id1", "id2");

    assertThat(((SpotServerInstanceInfo) value.getServerInstanceDetails().get(0)).getElastigroupId())
        .isEqualTo(ELASTIGROUP_ID);

    assertThat(perpetualTaskResponse.getResponseCode()).isEqualTo(200);
    assertThat(perpetualTaskResponse.getResponseMessage()).isEqualTo(SUCCESS);
  }

  private PerpetualTaskExecutionParams getPerpetualTaskParams() {
    ByteString spotConnectorDTOBytes = ByteString.copyFrom(kryoSerializer.asBytes(SpotConnectorDTO.builder().build()));
    ByteString encryptedDataDetailBytes = ByteString.copyFrom(kryoSerializer.asBytes(new ArrayList<>()));

    SpotinstAmiInstanceSyncPerpetualTaskParamsNg taskParams =
        SpotinstAmiInstanceSyncPerpetualTaskParamsNg.newBuilder()
            .addAllElastigroupIds(Collections.singletonList(ELASTIGROUP_ID))
            .setAccountId("accId")
            .setSpotinstConfig(spotConnectorDTOBytes)
            .setSpotinstEncryptedData(encryptedDataDetailBytes)
            .setInfrastructureKey("infraKey")
            .build();

    return PerpetualTaskExecutionParams.newBuilder().setCustomizedParams(Any.pack(taskParams)).build();
  }
}
