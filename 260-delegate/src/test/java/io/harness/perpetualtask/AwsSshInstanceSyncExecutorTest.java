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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
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
import io.harness.perpetualtask.instancesync.AwsSshInstanceSyncPerpetualTaskParams;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.serializer.KryoSerializer;

import software.wings.beans.AwsConfig;
import software.wings.service.impl.aws.model.AwsEc2ListInstancesResponse;
import software.wings.service.intfc.aws.delegate.AwsEc2HelperServiceDelegate;

import com.amazonaws.services.ec2.model.Filter;
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
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;
import retrofit2.Call;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(CDP)
public class AwsSshInstanceSyncExecutorTest extends DelegateTestBase {
  @Mock private AwsEc2HelperServiceDelegate ec2ServiceDelegate;
  @Mock private DelegateAgentManagerClient delegateAgentManagerClient;
  @Mock private Call<RestResponse<Boolean>> call;
  @Inject KryoSerializer kryoSerializer;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;

  @InjectMocks private AwsSshInstanceSyncExecutor executor;

  private ArgumentCaptor<AwsEc2ListInstancesResponse> captor =
      ArgumentCaptor.forClass(AwsEc2ListInstancesResponse.class);

  @Before
  public void setup() {
    on(executor).set("referenceFalseKryoSerializer", referenceFalseKryoSerializer);
    MockitoAnnotations.initMocks(executor);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void runOnceWhenAwsCallSuccess() throws IOException {
    PerpetualTaskResponse perpetualTaskResponse;
    PerpetualTaskExecutionParams perpetualTaskParams = getPerpetualTaskParams();
    final Instance instance = new Instance();
    doReturn(Arrays.asList(instance))
        .when(ec2ServiceDelegate)
        .listEc2Instances(any(AwsConfig.class), anyList(), anyString(), anyList(), eq(true));
    doReturn(call)
        .when(delegateAgentManagerClient)
        .publishInstanceSyncResultV2(anyString(), anyString(), any(DelegateResponseData.class));
    doReturn(retrofit2.Response.success("success")).when(call).execute();

    perpetualTaskResponse =
        executor.runOnce(PerpetualTaskId.newBuilder().setId("id").build(), perpetualTaskParams, Instant.now());

    verify(ec2ServiceDelegate, Mockito.times(1))
        .listEc2Instances(any(AwsConfig.class), anyList(), Matchers.eq("us-east-1"), anyList(), eq(true));

    verify(delegateAgentManagerClient, times(1))
        .publishInstanceSyncResultV2(eq("id"), eq("accountId"), captor.capture());

    AwsEc2ListInstancesResponse response = captor.getValue();
    verifySuccessResponse(instance, perpetualTaskResponse, response);

    doThrow(new RuntimeException()).when(call).execute();
    perpetualTaskResponse =
        executor.runOnce(PerpetualTaskId.newBuilder().setId("id").build(), perpetualTaskParams, Instant.now());
    verifySuccessResponse(instance, perpetualTaskResponse, response);
  }

  private void verifySuccessResponse(
      Instance instance, PerpetualTaskResponse perpetualTaskResponse, AwsEc2ListInstancesResponse response) {
    assertThat(response.getInstances()).containsExactly(instance);
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(response.getErrorMessage()).isNull();

    assertThat(perpetualTaskResponse.getResponseCode()).isEqualTo(Response.SC_OK);
    assertThat(perpetualTaskResponse.getResponseMessage()).contains("success");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void runOnceWhenAwsCallThrows() throws IOException {
    PerpetualTaskResponse perpetualTaskResponse;
    PerpetualTaskExecutionParams perpetualTaskParams = getPerpetualTaskParams();
    final Instance instance = new Instance();
    doThrow(new RuntimeException("invalid credentials"))
        .when(ec2ServiceDelegate)
        .listEc2Instances(any(AwsConfig.class), anyList(), anyString(), anyList(), eq(true));
    doReturn(call)
        .when(delegateAgentManagerClient)
        .publishInstanceSyncResultV2(anyString(), anyString(), any(DelegateResponseData.class));
    doReturn(retrofit2.Response.success("success")).when(call).execute();

    perpetualTaskResponse =
        executor.runOnce(PerpetualTaskId.newBuilder().setId("id").build(), perpetualTaskParams, Instant.now());

    verify(ec2ServiceDelegate, Mockito.times(1))
        .listEc2Instances(any(AwsConfig.class), anyList(), Matchers.eq("us-east-1"), anyList(), eq(true));

    verify(delegateAgentManagerClient, times(1))
        .publishInstanceSyncResultV2(eq("id"), eq("accountId"), captor.capture());

    AwsEc2ListInstancesResponse response = captor.getValue();
    verifyFailureResponse(perpetualTaskResponse, response);

    doThrow(new RuntimeException()).when(call).execute();
    perpetualTaskResponse =
        executor.runOnce(PerpetualTaskId.newBuilder().setId("id").build(), perpetualTaskParams, Instant.now());
    verifyFailureResponse(perpetualTaskResponse, response);
  }

  private void verifyFailureResponse(
      PerpetualTaskResponse perpetualTaskResponse, AwsEc2ListInstancesResponse response) {
    assertThat(response.getInstances()).isNull();
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
    assertThat(response.getErrorMessage()).contains("invalid credentials");

    assertThat(perpetualTaskResponse.getResponseCode()).isEqualTo(Response.SC_OK);
    assertThat(perpetualTaskResponse.getResponseMessage()).contains("invalid credentials");
  }

  private PerpetualTaskExecutionParams getPerpetualTaskParams() {
    ByteString configBytes =
        ByteString.copyFrom(referenceFalseKryoSerializer.asBytes(AwsConfig.builder().accountId("accountId").build()));
    ByteString filterBytes = ByteString.copyFrom(referenceFalseKryoSerializer.asBytes(Arrays.asList(new Filter())));
    ByteString encryptionDetailsBytes = ByteString.copyFrom(referenceFalseKryoSerializer.asBytes(new ArrayList<>()));

    AwsSshInstanceSyncPerpetualTaskParams params = AwsSshInstanceSyncPerpetualTaskParams.newBuilder()
                                                       .setAwsConfig(configBytes)
                                                       .setFilter(filterBytes)
                                                       .setEncryptedData(encryptionDetailsBytes)
                                                       .setRegion("us-east-1")
                                                       .build();
    return PerpetualTaskExecutionParams.newBuilder()
        .setCustomizedParams(Any.pack(params))
        .setReferenceFalseKryoSerializer(true)
        .build();
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void cleanup() {
    assertThat(
        executor.cleanup(PerpetualTaskId.newBuilder().build(), PerpetualTaskExecutionParams.newBuilder().build()))
        .isFalse();
  }
}
