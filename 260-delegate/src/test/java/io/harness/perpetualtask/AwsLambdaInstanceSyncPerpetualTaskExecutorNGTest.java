/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import io.harness.DelegateTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.instancesync.AwsLambdaInstanceSyncPerpetualTaskResponse;
import io.harness.delegate.beans.instancesync.info.AwsLambdaServerInstanceInfo;
import io.harness.delegate.exception.AwsLambdaException;
import io.harness.delegate.task.aws.lambda.AwsLambdaDeploymentReleaseData;
import io.harness.delegate.task.aws.lambda.AwsLambdaFunctionsInfraConfig;
import io.harness.delegate.task.aws.lambda.AwsLambdaInfraConfig;
import io.harness.delegate.task.aws.lambda.AwsLambdaTaskHelperBase;
import io.harness.logging.CommandExecutionStatus;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.instancesync.AwsLambdaDeploymentRelease;
import io.harness.perpetualtask.instancesync.AwsLambdaInstanceSyncPerpetualTaskParamsNg;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.runners.MockitoJUnitRunner;
import retrofit2.Call;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(CDP)
public class AwsLambdaInstanceSyncPerpetualTaskExecutorNGTest extends DelegateTestBase {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  private static final String PERPETUAL_TASK_ID = "perpetualTaskId";
  private static final String ACCOUNT_ID = "accountId";
  private final String FUNCTION = "fun";
  private final String REGION = "us-east1";
  private final Integer MEMORY_SIZE = 512;
  private final String RUN_TIME = "java8";
  private final String INFRA_KEY = "198398123";
  private final String SOURCE = "source";
  private final String VERSION = "function-867";
  private final long TIME = 74987321;

  @Inject private KryoSerializer kryoSerializer;
  @Mock private AwsLambdaTaskHelperBase awsLambdaTaskHelperBase;
  @Mock private Call<RestResponse<Boolean>> call;
  @Mock private DelegateAgentManagerClient delegateAgentManagerClient;

  @Captor private ArgumentCaptor<AwsLambdaInstanceSyncPerpetualTaskResponse> perpetualTaskResponseCaptor;

  @InjectMocks AwsLambdaInstanceSyncPerpetualTaskExecutorNg awsLambdaInstanceSyncPerpetualTaskExecutor;

  @Before
  public void setUp() throws IOException {
    on(awsLambdaInstanceSyncPerpetualTaskExecutor).set("kryoSerializer", kryoSerializer);
    doReturn(call)
        .when(delegateAgentManagerClient)
        .processInstanceSyncNGResult(anyString(), anyString(), perpetualTaskResponseCaptor.capture());
    doReturn(retrofit2.Response.success("success")).when(call).execute();
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void runOnceTest() throws InvalidProtocolBufferException, AwsLambdaException {
    AwsLambdaInfraConfig awsLambdaInfraConfig =
        AwsLambdaFunctionsInfraConfig.builder().awsConnectorDTO(AwsConnectorDTO.builder().build()).build();
    AwsLambdaDeploymentRelease deploymentRelease =
        AwsLambdaDeploymentRelease.newBuilder()
            .setAwsLambdaInfraConfig(ByteString.copyFrom(kryoSerializer.asBytes(awsLambdaInfraConfig)))
            .setFunction(FUNCTION)
            .setRegion(REGION)
            .build();

    AwsLambdaInstanceSyncPerpetualTaskParamsNg taskParams = AwsLambdaInstanceSyncPerpetualTaskParamsNg.newBuilder()
                                                                .setAccountId(ACCOUNT_ID)
                                                                .addAwsLambdaDeploymentReleaseList(deploymentRelease)
                                                                .build();

    PerpetualTaskExecutionParams perpetualTaskExecutionParams =
        PerpetualTaskExecutionParams.newBuilder().setCustomizedParams(Any.pack(taskParams)).build();
    PerpetualTaskId taskId = PerpetualTaskId.newBuilder().setId(PERPETUAL_TASK_ID).build();
    AwsLambdaDeploymentReleaseData deploymentReleaseData = AwsLambdaDeploymentReleaseData.builder()
                                                               .function(FUNCTION)
                                                               .region(REGION)
                                                               .awsLambdaInfraConfig(awsLambdaInfraConfig)
                                                               .build();
    AwsLambdaServerInstanceInfo awsLambdaServerInstanceInfo = AwsLambdaServerInstanceInfo.builder()
                                                                  .version(VERSION)
                                                                  .functionName(FUNCTION)
                                                                  .region(REGION)
                                                                  .memorySize(MEMORY_SIZE)
                                                                  .runtime(RUN_TIME)
                                                                  .source(SOURCE)
                                                                  .infrastructureKey(INFRA_KEY)
                                                                  .build();
    doReturn(Arrays.asList(awsLambdaServerInstanceInfo))
        .when(awsLambdaTaskHelperBase)
        .getAwsLambdaServerInstanceInfo(deploymentReleaseData);

    awsLambdaInstanceSyncPerpetualTaskExecutor.runOnce(taskId, perpetualTaskExecutionParams, Instant.EPOCH);

    assertThat(perpetualTaskResponseCaptor.getValue()).isInstanceOf(AwsLambdaInstanceSyncPerpetualTaskResponse.class);
    AwsLambdaInstanceSyncPerpetualTaskResponse awsLambdaInstanceSyncPerpetualTaskResponse =
        perpetualTaskResponseCaptor.getValue();
    assertThat(awsLambdaInstanceSyncPerpetualTaskResponse.getCommandExecutionStatus())
        .isEqualTo(CommandExecutionStatus.SUCCESS);
  }
}
