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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.DelegateTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.instancesync.AwsAmiInstanceSyncPerpetualTaskParams;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.serializer.KryoSerializer;

import software.wings.beans.AwsConfig;
import software.wings.service.impl.aws.model.AwsAsgListInstancesResponse;
import software.wings.service.intfc.aws.delegate.AwsAsgHelperServiceDelegate;

import com.amazonaws.services.ec2.model.Instance;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.io.IOException;
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
public class AwsAmiInstanceSyncPerpetualTaskExecutorTest extends DelegateTestBase {
  @Mock private AwsAsgHelperServiceDelegate awsAsgHelperServiceDelegate;
  @Mock private DelegateAgentManagerClient delegateAgentManagerClient;
  @Mock private Call<RestResponse<Boolean>> call;

  private ArgumentCaptor<AwsAsgListInstancesResponse> captor =
      ArgumentCaptor.forClass(AwsAsgListInstancesResponse.class);

  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;

  @InjectMocks private AwsAmiInstanceSyncPerpetualTaskExecutor executor;

  @Before
  public void setup() {
    on(executor).set("referenceFalseKryoSerializer", referenceFalseKryoSerializer);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void runOnceWithAwsCallSuccess() throws IOException {
    final Instance instance = new Instance();
    doReturn(Arrays.asList(instance))
        .when(awsAsgHelperServiceDelegate)
        .listAutoScalingGroupInstances(any(AwsConfig.class), anyList(), eq("us-east-1"), eq("asg-1"), eq(true));
    doReturn(call)
        .when(delegateAgentManagerClient)
        .publishInstanceSyncResultV2(anyString(), anyString(), any(DelegateResponseData.class));
    doReturn(retrofit2.Response.success("success")).when(call).execute();

    PerpetualTaskResponse perpetualTaskResponse;
    perpetualTaskResponse =
        executor.runOnce(PerpetualTaskId.newBuilder().setId("id").build(), getPerpetualTaskParams(), Instant.now());

    verify(delegateAgentManagerClient, times(1))
        .publishInstanceSyncResultV2(eq("id"), eq("accountId"), captor.capture());

    final AwsAsgListInstancesResponse awsResponse = captor.getValue();

    verifyAwsCallSuccess(instance, perpetualTaskResponse, awsResponse);

    doThrow(new RuntimeException()).when(call).execute();
    perpetualTaskResponse =
        executor.runOnce(PerpetualTaskId.newBuilder().setId("id").build(), getPerpetualTaskParams(), Instant.now());

    verifyAwsCallSuccess(instance, perpetualTaskResponse, awsResponse);
  }

  private void verifyAwsCallSuccess(
      Instance instance, PerpetualTaskResponse perpetualTaskResponse, AwsAsgListInstancesResponse awsResponse) {
    assertThat(awsResponse.getErrorMessage()).isNull();
    assertThat(awsResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(awsResponse.getAsgName()).isEqualTo("asg-1");
    assertThat(awsResponse.getInstances()).containsExactly(instance);

    assertThat(perpetualTaskResponse.getResponseMessage()).isEqualTo("success");
    assertThat(perpetualTaskResponse.getResponseCode()).isEqualTo(Response.SC_OK);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void runOnceWithAwsCallFailure() throws IOException {
    doThrow(new RuntimeException("exception message"))
        .when(awsAsgHelperServiceDelegate)
        .listAutoScalingGroupInstances(any(AwsConfig.class), anyList(), eq("us-east-1"), eq("asg-1"), eq(true));
    doReturn(call)
        .when(delegateAgentManagerClient)
        .publishInstanceSyncResultV2(anyString(), anyString(), any(DelegateResponseData.class));
    doReturn(retrofit2.Response.success("success")).when(call).execute();

    PerpetualTaskResponse perpetualTaskResponse;
    perpetualTaskResponse =
        executor.runOnce(PerpetualTaskId.newBuilder().setId("id").build(), getPerpetualTaskParams(), Instant.now());

    verify(delegateAgentManagerClient, times(1))
        .publishInstanceSyncResultV2(eq("id"), eq("accountId"), captor.capture());

    final AwsAsgListInstancesResponse awsResponse = captor.getValue();

    veifyAwsCallFailure(perpetualTaskResponse, awsResponse);

    doThrow(new RuntimeException()).when(call).execute();
    perpetualTaskResponse =
        executor.runOnce(PerpetualTaskId.newBuilder().setId("id").build(), getPerpetualTaskParams(), Instant.now());

    veifyAwsCallFailure(perpetualTaskResponse, awsResponse);
  }

  private void veifyAwsCallFailure(
      PerpetualTaskResponse perpetualTaskResponse, AwsAsgListInstancesResponse awsResponse) {
    assertThat(awsResponse.getErrorMessage()).isEqualTo("exception message");
    assertThat(awsResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
    assertThat(awsResponse.getAsgName()).isEqualTo("asg-1");
    assertThat(awsResponse.getInstances()).isNull();

    assertThat(perpetualTaskResponse.getResponseMessage()).isEqualTo("exception message");
    assertThat(perpetualTaskResponse.getResponseCode()).isEqualTo(Response.SC_OK);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void cleanup() {
    assertThat(executor.cleanup(PerpetualTaskId.newBuilder().setId("id").build(), getPerpetualTaskParams())).isFalse();
  }

  private PerpetualTaskExecutionParams getPerpetualTaskParams() {
    ByteString configBytes =
        ByteString.copyFrom(referenceFalseKryoSerializer.asBytes(AwsConfig.builder().accountId("accountId").build()));
    ByteString encryptionDetailsBytes = ByteString.copyFrom(referenceFalseKryoSerializer.asBytes(new ArrayList<>()));

    AwsAmiInstanceSyncPerpetualTaskParams params = AwsAmiInstanceSyncPerpetualTaskParams.newBuilder()
                                                       .setAwsConfig(configBytes)
                                                       .setEncryptedData(encryptionDetailsBytes)
                                                       .setRegion("us-east-1")
                                                       .setAsgName("asg-1")
                                                       .build();
    return PerpetualTaskExecutionParams.newBuilder()
        .setCustomizedParams(Any.pack(params))
        .setReferenceFalseKryoSerializer(true)
        .build();
  }
}
