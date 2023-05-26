/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.DelegateTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.task.spotinst.request.SpotInstTaskParameters;
import io.harness.delegate.task.spotinst.response.SpotInstListElastigroupInstancesResponse;
import io.harness.delegate.task.spotinst.response.SpotInstTaskExecutionResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.instancesync.SpotinstAmiInstanceSyncPerpetualTaskParams;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import software.wings.annotation.EncryptableSetting;
import software.wings.beans.AwsConfig;
import software.wings.beans.SpotInstConfig;
import software.wings.delegatetasks.spotinst.taskhandler.SpotInstSyncTaskHandler;
import software.wings.service.intfc.security.EncryptionService;

import com.amazonaws.services.ec2.model.Instance;
import com.google.inject.Inject;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
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
public class SpotinstAmiInstanceSyncDelegateExecutorTest extends DelegateTestBase {
  @Mock private EncryptionService encryptionService;
  @Mock private SpotInstSyncTaskHandler taskHandler;
  @Mock private DelegateAgentManagerClient delegateAgentManagerClient;
  @Mock private Call<RestResponse<Boolean>> call;

  @InjectMocks private SpotinstAmiInstanceSyncDelegateExecutor executor;

  @Inject KryoSerializer kryoSerializer;

  @Before
  public void setUp() {
    on(executor).set("kryoSerializer", kryoSerializer);
    when(
        delegateAgentManagerClient.publishInstanceSyncResult(anyString(), anyString(), any(DelegateResponseData.class)))
        .thenReturn(call);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRunOnceSuccessful() {
    ArgumentCaptor<SpotInstTaskExecutionResponse> argumentCaptor =
        ArgumentCaptor.forClass(SpotInstTaskExecutionResponse.class);

    when(taskHandler.executeTask(any(SpotInstTaskParameters.class), any(SpotInstConfig.class), any(AwsConfig.class)))
        .thenReturn(
            SpotInstTaskExecutionResponse.builder()
                .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                .spotInstTaskResponse(SpotInstListElastigroupInstancesResponse.builder()
                                          .elastigroupId("elastigroup-id")
                                          .elastigroupInstances(asList(new Instance().withInstanceId("ec-instance-0"),
                                              new Instance().withInstanceId("ec-instance-1")))
                                          .build())
                .build());
    PerpetualTaskResponse perpetualTaskResponse = executor.runOnce(
        PerpetualTaskId.newBuilder().setId("task-id").build(), getPerpetualTaskParams(), Instant.now());

    verify(encryptionService, times(2)).decrypt(any(EncryptableSetting.class), anyList(), eq(true));
    verify(taskHandler, times(1))
        .executeTask(any(SpotInstTaskParameters.class), any(SpotInstConfig.class), any(AwsConfig.class));
    verify(delegateAgentManagerClient, times(1))
        .publishInstanceSyncResult(eq("task-id"), eq("accountId"), argumentCaptor.capture());
    SpotInstTaskExecutionResponse sentTaskExecutionResponse = argumentCaptor.getValue();

    assertThat(perpetualTaskResponse.getResponseCode()).isEqualTo(Response.SC_OK);
    assertThat(perpetualTaskResponse.getResponseMessage()).isEqualTo("success");
    assertThat(sentTaskExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(sentTaskExecutionResponse.getSpotInstTaskResponse())
        .isInstanceOf(SpotInstListElastigroupInstancesResponse.class);
    SpotInstListElastigroupInstancesResponse sentInstancesResponse =
        (SpotInstListElastigroupInstancesResponse) sentTaskExecutionResponse.getSpotInstTaskResponse();
    assertThat(sentInstancesResponse.getElastigroupId()).isEqualTo("elastigroup-id");
    assertThat(sentInstancesResponse.getElastigroupInstances().stream().map(Instance::getInstanceId))
        .containsExactlyInAnyOrder("ec-instance-0", "ec-instance-1");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRunOnceFailure() {
    ArgumentCaptor<SpotInstTaskExecutionResponse> argumentCaptor =
        ArgumentCaptor.forClass(SpotInstTaskExecutionResponse.class);

    when(taskHandler.executeTask(any(SpotInstTaskParameters.class), any(SpotInstConfig.class), any(AwsConfig.class)))
        .thenReturn(SpotInstTaskExecutionResponse.builder()
                        .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                        .errorMessage("Unable to list instances")
                        .build());
    PerpetualTaskResponse perpetualTaskResponse = executor.runOnce(
        PerpetualTaskId.newBuilder().setId("task-id").build(), getPerpetualTaskParams(), Instant.now());

    verify(encryptionService, times(2)).decrypt(any(EncryptableSetting.class), anyList(), eq(true));
    verify(taskHandler, times(1))
        .executeTask(any(SpotInstTaskParameters.class), any(SpotInstConfig.class), any(AwsConfig.class));
    verify(delegateAgentManagerClient, times(1))
        .publishInstanceSyncResult(eq("task-id"), eq("accountId"), argumentCaptor.capture());
    SpotInstTaskExecutionResponse sentTaskExecutionResponse = argumentCaptor.getValue();

    assertThat(perpetualTaskResponse.getResponseMessage()).isEqualTo("Unable to list instances");
    assertThat(sentTaskExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(sentTaskExecutionResponse.getErrorMessage()).isEqualTo("Unable to list instances");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRunOnceFailureHandlerThrownException() {
    ArgumentCaptor<SpotInstTaskExecutionResponse> argumentCaptor =
        ArgumentCaptor.forClass(SpotInstTaskExecutionResponse.class);

    when(taskHandler.executeTask(any(SpotInstTaskParameters.class), any(SpotInstConfig.class), any(AwsConfig.class)))
        .thenThrow(new InvalidRequestException("Unable to fetch instance list"));

    PerpetualTaskResponse perpetualTaskResponse = executor.runOnce(
        PerpetualTaskId.newBuilder().setId("task-id").build(), getPerpetualTaskParams(), Instant.now());

    verify(encryptionService, times(2)).decrypt(any(EncryptableSetting.class), anyList(), eq(true));
    verify(taskHandler, times(1))
        .executeTask(any(SpotInstTaskParameters.class), any(SpotInstConfig.class), any(AwsConfig.class));
    verify(delegateAgentManagerClient, times(1))
        .publishInstanceSyncResult(eq("task-id"), eq("accountId"), argumentCaptor.capture());
    SpotInstTaskExecutionResponse sentTaskExecutionResponse = argumentCaptor.getValue();

    assertThat(perpetualTaskResponse.getResponseMessage()).isEqualTo("Unable to fetch instance list");
    assertThat(sentTaskExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(sentTaskExecutionResponse.getErrorMessage()).isEqualTo("Unable to fetch instance list");
  }

  private PerpetualTaskExecutionParams getPerpetualTaskParams() {
    ByteString awsConfigBytes =
        ByteString.copyFrom(kryoSerializer.asBytes(AwsConfig.builder().accountId("accountId").build()));
    ByteString spotinstConfigBytes =
        ByteString.copyFrom(kryoSerializer.asBytes(SpotInstConfig.builder().accountId("accountId").build()));
    ByteString awsEncryptionDetailsBytes = ByteString.copyFrom(kryoSerializer.asBytes(new ArrayList<>()));
    ByteString spotinstEncryptionDetailsBytes = ByteString.copyFrom(kryoSerializer.asBytes(new ArrayList<>()));

    SpotinstAmiInstanceSyncPerpetualTaskParams taskParams =
        SpotinstAmiInstanceSyncPerpetualTaskParams.newBuilder()
            .setElastigroupId("elastigroup-id")
            .setRegion("us-east-1a")
            .setAwsConfig(awsConfigBytes)
            .setAwsEncryptedData(awsEncryptionDetailsBytes)
            .setSpotinstConfig(spotinstConfigBytes)
            .setSpotinstEncryptedData(spotinstEncryptionDetailsBytes)
            .build();

    return PerpetualTaskExecutionParams.newBuilder().setCustomizedParams(Any.pack(taskParams)).build();
  }
}
