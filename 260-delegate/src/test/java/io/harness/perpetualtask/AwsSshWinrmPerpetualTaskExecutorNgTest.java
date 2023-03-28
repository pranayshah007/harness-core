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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.DelegateTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.instancesync.SshWinrmInstanceSyncPerpetualTaskResponse;
import io.harness.delegate.beans.instancesync.info.AwsSshWinrmServerInstanceInfo;
import io.harness.delegate.task.aws.AwsASGDelegateTaskHelper;
import io.harness.delegate.task.aws.AwsListEC2InstancesDelegateTaskHelper;
import io.harness.delegate.task.ssh.AwsSshInfraDelegateConfig;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.perpetualtask.instancesync.AwsSshInstanceSyncPerpetualTaskParamsNg;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import software.wings.service.impl.aws.model.AwsEC2Instance;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
public class AwsSshWinrmPerpetualTaskExecutorNgTest extends DelegateTestBase {
  @Mock private DelegateAgentManagerClient delegateAgentManagerClient;
  @Mock private Call<RestResponse<Boolean>> call;
  @Mock private AwsListEC2InstancesDelegateTaskHelper awsListEC2InstancesDelegateTaskHelper;
  @Mock private AwsASGDelegateTaskHelper awsASGDelegateTaskHelper;
  @Mock private KryoSerializer referenceFalseKryoSerializer;

  @InjectMocks private AwsSshWinrmPerpetualTaskExecutorNg executor;
  @Captor private ArgumentCaptor<SshWinrmInstanceSyncPerpetualTaskResponse> perpetualTaskResponseCaptor;
  private static final String SUCCESS = "success";
  private static final String PERPETUAL_TASK_ID = "perpetualTaskId";
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String HOST1 = "1.2.3.4";
  private static final String SERVICE = ServiceSpecType.SSH;
  byte[] bytes = {70};

  @Before
  public void setUp() throws IOException {
    doReturn(call)
        .when(delegateAgentManagerClient)
        .processInstanceSyncNGResult(any(), any(), perpetualTaskResponseCaptor.capture());
    doReturn(retrofit2.Response.success(SUCCESS)).when(call).execute();
    doReturn(AwsSshInfraDelegateConfig.sshAwsBuilder()
                 .encryptionDataDetails(new ArrayList<>())
                 .awsConnectorDTO(AwsConnectorDTO.builder().build())
                 .region("r1")
                 .tags(Collections.singletonMap("tag1", "value"))
                 .build())
        .when(referenceFalseKryoSerializer)
        .asObject(any(byte[].class));
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testRunOnce() {
    doReturn(Arrays.asList(AwsEC2Instance.builder().publicIp(HOST1).privateIp(HOST1).build()))
        .when(awsListEC2InstancesDelegateTaskHelper)
        .getInstances(any(), any(), anyString(), any(), any(), anyBoolean());

    PerpetualTaskExecutionParams perpetualTaskExecutionParams = getPerpetualTaskExecutionParams();
    PerpetualTaskResponse perpetualTaskResponse = executor.runOnce(
        PerpetualTaskId.newBuilder().setId(PERPETUAL_TASK_ID).build(), perpetualTaskExecutionParams, Instant.EPOCH);

    assertThat(perpetualTaskResponseCaptor.getValue()).isInstanceOf(SshWinrmInstanceSyncPerpetualTaskResponse.class);
    SshWinrmInstanceSyncPerpetualTaskResponse value = perpetualTaskResponseCaptor.getValue();
    System.out.println(value);
    assertThat(value.getServerInstanceDetails()
                   .stream()
                   .map(instance -> ((AwsSshWinrmServerInstanceInfo) instance).getHost())
                   .collect(Collectors.toList()))
        .contains(HOST1);

    assertThat(perpetualTaskResponse).isNotNull();
    assertThat(perpetualTaskResponse.getResponseCode()).isEqualTo(200);
    assertThat(perpetualTaskResponse.getResponseMessage()).isEqualTo(SUCCESS);
  }

  private PerpetualTaskExecutionParams getPerpetualTaskExecutionParams() {
    AwsSshInstanceSyncPerpetualTaskParamsNg message = AwsSshInstanceSyncPerpetualTaskParamsNg.newBuilder()
                                                          .addHosts(HOST1)
                                                          .setAccountId(ACCOUNT_ID)
                                                          .setServiceType(SERVICE)
                                                          .setInfraDelegateConfig(ByteString.copyFrom(bytes))
                                                          .setHostConnectionType("PublicIP")
                                                          .build();

    return PerpetualTaskExecutionParams.newBuilder()
        .setCustomizedParams(Any.pack(message))
        .setReferenceFalseKryoSerializer(true)
        .build();
  }
}
