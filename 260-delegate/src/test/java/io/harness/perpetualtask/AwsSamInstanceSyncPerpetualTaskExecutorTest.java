/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ALLU_VAMSI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import io.harness.DelegateTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.instancesync.AwsSamInstanceSyncPerpetualTaskResponse;
import io.harness.delegate.beans.instancesync.info.AwsSamServerInstanceInfo;
import io.harness.delegate.task.awssam.AwsSamDeploymentReleaseData;
import io.harness.delegate.task.awssam.AwsSamInfraConfig;
import io.harness.delegate.task.awssam.AwsSamTaskHelperBase;
import io.harness.logging.CommandExecutionStatus;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.instancesync.AwsSamDeploymentRelease;
import io.harness.perpetualtask.instancesync.AwsSamInstanceSyncPerpetualTaskParams;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(CDP)
public class AwsSamInstanceSyncPerpetualTaskExecutorTest extends DelegateTestBase {
  private static final String PERPETUAL_TASK_ID = "perpetualTaskId";
  private static final String ACCOUNT_ID = "accountId";
  private final String FUNCTION = "fun";
  private final String REGION = "us-east1";
  private final String MEMORY_SIZE = "512MiB";
  private final String RUN_TIME = "java8";
  private final String INFRA_KEY = "198398123";
  private final String HANDLER = "index.handler";

  @Inject private KryoSerializer kryoSerializer;
  @Mock private Call<RestResponse<Boolean>> call;
  @Mock private DelegateAgentManagerClient delegateAgentManagerClient;
  @Mock private AwsSamTaskHelperBase awsSamTaskHelperBase;

  @Captor private ArgumentCaptor<AwsSamInstanceSyncPerpetualTaskResponse> perpetualTaskResponseCaptor;

  @InjectMocks @Spy AwsSamInstanceSyncPerpetualTaskExecutor awsSamInstanceSyncPerpetualTaskExecutor;

  @Before
  public void setUp() throws IOException {
    on(awsSamInstanceSyncPerpetualTaskExecutor).set("kryoSerializer", kryoSerializer);
    doReturn(call)
        .when(delegateAgentManagerClient)
        .processInstanceSyncNGResult(anyString(), anyString(), perpetualTaskResponseCaptor.capture());
    doReturn(retrofit2.Response.success("success")).when(call).execute();
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void runOnceTest() {
    AwsSamInfraConfig awsSamInfraConfig =
        AwsSamInfraConfig.builder().awsConnectorDTO(AwsConnectorDTO.builder().build()).build();
    AwsSamDeploymentRelease deploymentRelease =
        AwsSamDeploymentRelease.newBuilder()
            .setAwsSamInfraConfig(ByteString.copyFrom(kryoSerializer.asBytes(awsSamInfraConfig)))
            .addFunctions(FUNCTION)
            .setRegion(REGION)
            .build();

    AwsSamInstanceSyncPerpetualTaskParams taskParams = AwsSamInstanceSyncPerpetualTaskParams.newBuilder()
                                                           .setAccountId(ACCOUNT_ID)
                                                           .addAwsSamDeploymentReleaseList(deploymentRelease)
                                                           .build();

    PerpetualTaskExecutionParams perpetualTaskExecutionParams =
        PerpetualTaskExecutionParams.newBuilder().setCustomizedParams(Any.pack(taskParams)).build();
    PerpetualTaskId taskId = PerpetualTaskId.newBuilder().setId(PERPETUAL_TASK_ID).build();
    AwsSamDeploymentReleaseData deploymentReleaseData = AwsSamDeploymentReleaseData.builder()
                                                            .functions(Arrays.asList(FUNCTION))
                                                            .region(REGION)
                                                            .awsSamInfraConfig(awsSamInfraConfig)
                                                            .build();
    AwsSamServerInstanceInfo awsSamServerInstanceInfo = AwsSamServerInstanceInfo.builder()
                                                            .functionName(FUNCTION)
                                                            .region(REGION)
                                                            .memorySize(MEMORY_SIZE)
                                                            .runTime(RUN_TIME)
                                                            .handler(HANDLER)
                                                            .infraStructureKey(INFRA_KEY)
                                                            .build();

    doReturn(Arrays.asList(awsSamServerInstanceInfo))
        .when(awsSamTaskHelperBase)
        .getAwsSamServerInstanceInfos(deploymentReleaseData);

    awsSamInstanceSyncPerpetualTaskExecutor.runOnce(taskId, perpetualTaskExecutionParams, Instant.EPOCH);

    assertThat(perpetualTaskResponseCaptor.getValue()).isInstanceOf(AwsSamInstanceSyncPerpetualTaskResponse.class);
    AwsSamInstanceSyncPerpetualTaskResponse awsSamInstanceSyncPerpetualTaskResponse =
        perpetualTaskResponseCaptor.getValue();
    assertThat(awsSamInstanceSyncPerpetualTaskResponse.getCommandExecutionStatus())
        .isEqualTo(CommandExecutionStatus.SUCCESS);
  }
}
