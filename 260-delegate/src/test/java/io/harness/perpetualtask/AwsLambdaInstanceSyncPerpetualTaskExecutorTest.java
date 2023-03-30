/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.ExecutionStatus.FAILED;
import static io.harness.beans.ExecutionStatus.SUCCESS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.Matchers.any;
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
import io.harness.perpetualtask.instancesync.AwsLambdaInstanceSyncPerpetualTaskParams;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.serializer.KryoSerializer;

import software.wings.beans.AwsConfig;
import software.wings.service.impl.aws.model.request.AwsCloudWatchStatisticsRequest;
import software.wings.service.impl.aws.model.request.AwsLambdaDetailsRequest;
import software.wings.service.impl.aws.model.response.AwsCloudWatchStatisticsResponse;
import software.wings.service.impl.aws.model.response.AwsLambdaDetailsMetricsResponse;
import software.wings.service.impl.aws.model.response.AwsLambdaDetailsResponse;
import software.wings.service.intfc.aws.delegate.AwsCloudWatchHelperServiceDelegate;
import software.wings.service.intfc.aws.delegate.AwsLambdaHelperServiceDelegate;

import com.google.inject.Inject;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import org.eclipse.jetty.server.Response;
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
@OwnedBy(CDP)
public class AwsLambdaInstanceSyncPerpetualTaskExecutorTest extends DelegateTestBase {
  @Mock private DelegateAgentManagerClient delegateAgentManagerClient;
  @Mock private AwsLambdaHelperServiceDelegate awsLambdaHelperServiceDelegate;
  @Mock private AwsCloudWatchHelperServiceDelegate awsCloudWatchHelperServiceDelegate;
  @Mock private Call<RestResponse<Boolean>> call;

  @Inject KryoSerializer kryoSerializer;

  private ArgumentCaptor<AwsLambdaDetailsMetricsResponse> captor =
      ArgumentCaptor.forClass(AwsLambdaDetailsMetricsResponse.class);

  @InjectMocks private AwsLambdaInstanceSyncPerpetualTaskExecutor executor;

  @Before
  public void setup() {
    on(executor).set("kryoSerializer", kryoSerializer);
  }

  @Test
  @Owner(developers = OwnerRule.ACASIAN)
  @Category(UnitTests.class)
  public void runOnceWithAwsCallSuccess() throws IOException {
    doReturn(AwsLambdaDetailsResponse.builder().executionStatus(SUCCESS).build())
        .when(awsLambdaHelperServiceDelegate)
        .getFunctionDetails(any(AwsLambdaDetailsRequest.class), eq(true));

    doReturn(AwsCloudWatchStatisticsResponse.builder().executionStatus(SUCCESS).build())
        .when(awsCloudWatchHelperServiceDelegate)
        .getMetricStatistics(any(AwsCloudWatchStatisticsRequest.class));

    doReturn(call)
        .when(delegateAgentManagerClient)
        .publishInstanceSyncResult(anyString(), anyString(), any(DelegateResponseData.class));
    doReturn(retrofit2.Response.success("success")).when(call).execute();

    PerpetualTaskResponse perpetualTaskResponse;
    perpetualTaskResponse =
        executor.runOnce(PerpetualTaskId.newBuilder().setId("id").build(), getPerpetualTaskParams(), Instant.now());

    verify(delegateAgentManagerClient, times(1)).publishInstanceSyncResult(eq("id"), eq("accountId"), captor.capture());

    final AwsLambdaDetailsMetricsResponse awsResponse = captor.getValue();

    verifyAwsCall(perpetualTaskResponse, awsResponse, SUCCESS, "success");

    doThrow(new RuntimeException("Failed to publish execution result")).when(call).execute();
    perpetualTaskResponse =
        executor.runOnce(PerpetualTaskId.newBuilder().setId("id").build(), getPerpetualTaskParams(), Instant.now());

    verifyAwsCall(perpetualTaskResponse, awsResponse, SUCCESS, "success");
  }

  @Test
  @Owner(developers = OwnerRule.ACASIAN)
  @Category(UnitTests.class)
  public void runOnceWithAwsCallSuccessAndCloudWatchCallFailure() throws IOException {
    doReturn(AwsLambdaDetailsResponse.builder().executionStatus(SUCCESS).build())
        .when(awsLambdaHelperServiceDelegate)
        .getFunctionDetails(any(AwsLambdaDetailsRequest.class), eq(true));

    doThrow(new RuntimeException("Failed to get cloudwatch statistics"))
        .when(awsCloudWatchHelperServiceDelegate)
        .getMetricStatistics(any(AwsCloudWatchStatisticsRequest.class));

    doReturn(call)
        .when(delegateAgentManagerClient)
        .publishInstanceSyncResult(anyString(), anyString(), any(DelegateResponseData.class));
    doReturn(retrofit2.Response.success("success")).when(call).execute();

    PerpetualTaskResponse perpetualTaskResponse;
    perpetualTaskResponse =
        executor.runOnce(PerpetualTaskId.newBuilder().setId("id").build(), getPerpetualTaskParams(), Instant.now());

    verify(delegateAgentManagerClient, times(1)).publishInstanceSyncResult(eq("id"), eq("accountId"), captor.capture());

    final AwsLambdaDetailsMetricsResponse awsResponse = captor.getValue();

    verifyAwsCall(perpetualTaskResponse, awsResponse, SUCCESS, "success");

    doThrow(new RuntimeException("Failed to publish execution result")).when(call).execute();
    perpetualTaskResponse =
        executor.runOnce(PerpetualTaskId.newBuilder().setId("id").build(), getPerpetualTaskParams(), Instant.now());

    verifyAwsCall(perpetualTaskResponse, awsResponse, SUCCESS, "success");
  }

  @Test
  @Owner(developers = OwnerRule.ACASIAN)
  @Category(UnitTests.class)
  public void runOnceWithAwsCallFailure() throws IOException {
    doThrow(new RuntimeException("Failed to execute lambda function"))
        .when(awsLambdaHelperServiceDelegate)
        .getFunctionDetails(any(AwsLambdaDetailsRequest.class), eq(true));

    doReturn(call)
        .when(delegateAgentManagerClient)
        .publishInstanceSyncResult(anyString(), anyString(), any(DelegateResponseData.class));
    doReturn(retrofit2.Response.success("success")).when(call).execute();

    PerpetualTaskResponse perpetualTaskResponse;
    perpetualTaskResponse =
        executor.runOnce(PerpetualTaskId.newBuilder().setId("id").build(), getPerpetualTaskParams(), Instant.now());

    verify(delegateAgentManagerClient, times(1)).publishInstanceSyncResult(eq("id"), eq("accountId"), captor.capture());

    final AwsLambdaDetailsMetricsResponse awsResponse = captor.getValue();

    verifyAwsCall(perpetualTaskResponse, awsResponse, FAILED, "Failed to execute lambda function");

    doThrow(new RuntimeException("Failed to publish execution result")).when(call).execute();
    perpetualTaskResponse =
        executor.runOnce(PerpetualTaskId.newBuilder().setId("id").build(), getPerpetualTaskParams(), Instant.now());

    verifyAwsCall(perpetualTaskResponse, awsResponse, FAILED, "Failed to execute lambda function");
  }

  @Test
  @Owner(developers = OwnerRule.ACASIAN)
  @Category(UnitTests.class)
  public void cleanup() {
    assertThat(executor.cleanup(PerpetualTaskId.newBuilder().setId("id").build(), getPerpetualTaskParams())).isFalse();
  }

  private void verifyAwsCall(PerpetualTaskResponse perpetualTaskResponse, AwsLambdaDetailsMetricsResponse awsResponse,
      ExecutionStatus executionStatus, String message) {
    if ("success".equals(message)) {
      assertThat(awsResponse.getErrorMessage()).isNull();
    } else {
      assertThat(awsResponse.getErrorMessage()).isEqualTo(message);
    }
    assertThat(awsResponse.getExecutionStatus()).isEqualTo(executionStatus);

    assertThat(perpetualTaskResponse.getResponseMessage()).isEqualTo(message);
    assertThat(perpetualTaskResponse.getResponseCode()).isEqualTo(Response.SC_OK);
  }

  private PerpetualTaskExecutionParams getPerpetualTaskParams() {
    ByteString configBytes =
        ByteString.copyFrom(kryoSerializer.asBytes(AwsConfig.builder().accountId("accountId").build()));
    ByteString encryptionDetailsBytes = ByteString.copyFrom(kryoSerializer.asBytes(new ArrayList<>()));

    AwsLambdaInstanceSyncPerpetualTaskParams params = AwsLambdaInstanceSyncPerpetualTaskParams.newBuilder()
                                                          .setAwsConfig(configBytes)
                                                          .setEncryptedData(encryptionDetailsBytes)
                                                          .setRegion("us-east-1")
                                                          .setFunctionName("function-1")
                                                          .setQualifier("version-1")
                                                          .build();
    return PerpetualTaskExecutionParams.newBuilder().setCustomizedParams(Any.pack(params)).build();
  }
}
