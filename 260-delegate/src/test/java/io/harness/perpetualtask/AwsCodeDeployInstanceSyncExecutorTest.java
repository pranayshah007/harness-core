/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;

import static java.util.Collections.singletonList;
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
import io.harness.exception.InvalidRequestException;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.instancesync.AwsCodeDeployInstanceSyncPerpetualTaskParams;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import software.wings.beans.AwsConfig;
import software.wings.service.impl.aws.model.AwsCodeDeployListDeploymentInstancesResponse;
import software.wings.service.intfc.aws.delegate.AwsEc2HelperServiceDelegate;

import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Instance;
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
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(CDP)
public class AwsCodeDeployInstanceSyncExecutorTest extends DelegateTestBase {
  @Mock private AwsEc2HelperServiceDelegate ec2ServiceDelegate;
  @Mock private DelegateAgentManagerClient delegateAgentManagerClient;
  @Mock private Call<RestResponse<Boolean>> call;

  @Inject KryoSerializer kryoSerializer;

  @InjectMocks private AwsCodeDeployInstanceSyncExecutor executor;

  @Before
  public void setup() throws IOException {
    on(executor).set("kryoSerializer", kryoSerializer);

    doReturn(singletonList(new Instance()))
        .when(ec2ServiceDelegate)
        .listEc2Instances(any(AwsConfig.class), anyList(), anyString(), anyList(), eq(true));

    doReturn(retrofit2.Response.success("success")).when(call).execute();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRunOnceSuccessful() throws IOException {
    PerpetualTaskExecutionParams perpetualTaskParams = getPerpetualTaskParams();
    ArgumentCaptor<AwsCodeDeployListDeploymentInstancesResponse> captor =
        ArgumentCaptor.forClass(AwsCodeDeployListDeploymentInstancesResponse.class);

    doReturn(call)
        .when(delegateAgentManagerClient)
        .publishInstanceSyncResult(anyString(), anyString(), any(DelegateResponseData.class));
    PerpetualTaskResponse perpetualTaskResponse =
        executor.runOnce(PerpetualTaskId.newBuilder().setId("id").build(), perpetualTaskParams, Instant.now());

    verify(ec2ServiceDelegate, times(1))
        .listEc2Instances(any(AwsConfig.class), anyList(), eq("us-east-1"), anyList(), eq(true));
    verify(delegateAgentManagerClient, times(1)).publishInstanceSyncResult(eq("id"), eq("accountId"), captor.capture());
    verifySuccessResponse(perpetualTaskResponse, captor.getValue());

    doThrow(new RuntimeException()).when(call).execute();
    perpetualTaskResponse =
        executor.runOnce(PerpetualTaskId.newBuilder().setId("id").build(), perpetualTaskParams, Instant.now());
    verifySuccessResponse(perpetualTaskResponse, captor.getValue());
  }

  private void verifySuccessResponse(
      PerpetualTaskResponse taskResponse, AwsCodeDeployListDeploymentInstancesResponse response) {
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(response.getInstances()).isNotEmpty();
    assertThat(response.getErrorMessage()).isNull();

    assertThat(taskResponse.getResponseCode()).isEqualTo(Response.SC_OK);
    assertThat(taskResponse.getResponseMessage()).contains("success");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRunOnceWhenInstancesFetchThrowException() {
    PerpetualTaskExecutionParams perpetualTaskParams = getPerpetualTaskParams();
    ArgumentCaptor<AwsCodeDeployListDeploymentInstancesResponse> captor =
        ArgumentCaptor.forClass(AwsCodeDeployListDeploymentInstancesResponse.class);

    doReturn(call)
        .when(delegateAgentManagerClient)
        .publishInstanceSyncResult(anyString(), anyString(), any(DelegateResponseData.class));
    doThrow(new InvalidRequestException("Invalid deployment id"))
        .when(ec2ServiceDelegate)
        .listEc2Instances(any(AwsConfig.class), anyList(), eq("us-east-1"), anyList(), eq(true));
    PerpetualTaskResponse perpetualTaskResponse =
        executor.runOnce(PerpetualTaskId.newBuilder().setId("id").build(), perpetualTaskParams, Instant.now());

    verify(ec2ServiceDelegate, times(1))
        .listEc2Instances(any(AwsConfig.class), anyList(), anyString(), anyList(), eq(true));
    verify(delegateAgentManagerClient, times(1)).publishInstanceSyncResult(eq("id"), eq("accountId"), captor.capture());
    verifyFailureResponse(perpetualTaskResponse, captor.getValue());
  }

  private void verifyFailureResponse(
      PerpetualTaskResponse taskResponse, AwsCodeDeployListDeploymentInstancesResponse response) {
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
    assertThat(response.getInstances()).isNullOrEmpty();
    assertThat(response.getErrorMessage()).isEqualTo("Invalid deployment id");

    assertThat(taskResponse.getResponseCode()).isEqualTo(Response.SC_OK);
    assertThat(taskResponse.getResponseMessage()).contains("Invalid deployment id");
  }

  private PerpetualTaskExecutionParams getPerpetualTaskParams() {
    ByteString configBytes =
        ByteString.copyFrom(kryoSerializer.asBytes(AwsConfig.builder().accountId("accountId").build()));
    ByteString encryptionDetailsBytes = ByteString.copyFrom(kryoSerializer.asBytes(new ArrayList<>()));
    AwsCodeDeployInstanceSyncPerpetualTaskParams.Builder paramsBuilder =
        AwsCodeDeployInstanceSyncPerpetualTaskParams.newBuilder();

    paramsBuilder.setRegion("us-east-1");
    paramsBuilder.setAwsConfig(configBytes);
    paramsBuilder.setEncryptedData(encryptionDetailsBytes);
    ByteString filterBytes = ByteString.copyFrom(kryoSerializer.asBytes(singletonList(new Filter())));
    paramsBuilder.setFilter(filterBytes);

    return PerpetualTaskExecutionParams.newBuilder().setCustomizedParams(Any.pack(paramsBuilder.build())).build();
  }
}
