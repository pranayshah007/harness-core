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
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.instancesync.GoogleFunctionInstanceSyncPerpetualTaskResponse;
import io.harness.delegate.beans.instancesync.info.GoogleFunctionServerInstanceInfo;
import io.harness.delegate.task.googlefunction.GoogleFunctionDeploymentReleaseData;
import io.harness.delegate.task.googlefunction.GoogleFunctionTaskHelperBase;
import io.harness.delegate.task.googlefunctionbeans.GcpGoogleFunctionInfraConfig;
import io.harness.delegate.task.googlefunctionbeans.GoogleFunctionInfraConfig;
import io.harness.logging.CommandExecutionStatus;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.instancesync.GoogleFunctionDeploymentRelease;
import io.harness.perpetualtask.instancesync.GoogleFunctionInstanceSyncPerpetualTaskParams;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import com.google.inject.name.Named;
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
public class GoogleFunctionInstanceSyncPerpetualTaskExecutorTest extends DelegateTestBase {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  private static final String PERPETUAL_TASK_ID = "perpetualTaskId";
  private static final String ACCOUNT_ID = "accountId";
  private final String FUNCTION = "fun";
  private final String PROJECT = "cd-play";
  private final String REGION = "us-east1";
  private final String MEMORY_SIZE = "512MiB";
  private final String RUN_TIME = "java8";
  private final String INFRA_KEY = "198398123";
  private final String SOURCE = "source";
  private final String REVISION = "function-867";
  private final long TIME = 74987321;

  @Inject private KryoSerializer kryoSerializer;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;

  @Mock private GoogleFunctionTaskHelperBase googleFunctionTaskHelperBase;
  @Mock private Call<RestResponse<Boolean>> call;
  @Mock private DelegateAgentManagerClient delegateAgentManagerClient;

  @Captor private ArgumentCaptor<GoogleFunctionInstanceSyncPerpetualTaskResponse> perpetualTaskResponseCaptor;

  @InjectMocks GoogleFunctionInstanceSyncPerpetualTaskExecutor googleFunctionInstanceSyncPerpetualTaskExecutor;

  @Before
  public void setUp() throws IOException {
    on(googleFunctionInstanceSyncPerpetualTaskExecutor)
        .set("referenceFalseKryoSerializer", referenceFalseKryoSerializer);
    doReturn(call)
        .when(delegateAgentManagerClient)
        .processInstanceSyncNGResult(anyString(), anyString(), perpetualTaskResponseCaptor.capture());
    doReturn(retrofit2.Response.success("success")).when(call).execute();
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void runOnceTest() throws InvalidProtocolBufferException {
    GoogleFunctionInfraConfig googleFunctionInfraConfig =
        GcpGoogleFunctionInfraConfig.builder().gcpConnectorDTO(GcpConnectorDTO.builder().build()).build();
    GoogleFunctionDeploymentRelease deploymentRelease =
        GoogleFunctionDeploymentRelease.newBuilder()
            .setGoogleFunctionsInfraConfig(
                ByteString.copyFrom(referenceFalseKryoSerializer.asBytes(googleFunctionInfraConfig)))
            .setFunction(FUNCTION)
            .setRegion(REGION)
            .build();

    GoogleFunctionInstanceSyncPerpetualTaskParams taskParams =
        GoogleFunctionInstanceSyncPerpetualTaskParams.newBuilder()
            .setAccountId(ACCOUNT_ID)
            .addGoogleFunctionsDeploymentReleaseList(deploymentRelease)
            .build();

    PerpetualTaskExecutionParams perpetualTaskExecutionParams =
        PerpetualTaskExecutionParams.newBuilder().setCustomizedParams(Any.pack(taskParams)).build();
    PerpetualTaskId taskId = PerpetualTaskId.newBuilder().setId(PERPETUAL_TASK_ID).build();
    GoogleFunctionDeploymentReleaseData deploymentReleaseData =
        GoogleFunctionDeploymentReleaseData.builder()
            .function(FUNCTION)
            .region(REGION)
            .googleFunctionInfraConfig(googleFunctionInfraConfig)
            .build();
    GoogleFunctionServerInstanceInfo googleFunctionServerInstanceInfo = GoogleFunctionServerInstanceInfo.builder()
                                                                            .revision(REVISION)
                                                                            .functionName(FUNCTION)
                                                                            .project(PROJECT)
                                                                            .region(REGION)
                                                                            .memorySize(MEMORY_SIZE)
                                                                            .runTime(RUN_TIME)
                                                                            .source(SOURCE)
                                                                            .updatedTime(TIME)
                                                                            .infraStructureKey(INFRA_KEY)
                                                                            .build();
    doReturn(Arrays.asList(googleFunctionServerInstanceInfo))
        .when(googleFunctionTaskHelperBase)
        .getGoogleFunctionServerInstanceInfo(deploymentReleaseData);

    googleFunctionInstanceSyncPerpetualTaskExecutor.runOnce(taskId, perpetualTaskExecutionParams, Instant.EPOCH);

    assertThat(perpetualTaskResponseCaptor.getValue())
        .isInstanceOf(GoogleFunctionInstanceSyncPerpetualTaskResponse.class);
    GoogleFunctionInstanceSyncPerpetualTaskResponse googleFunctionInstanceSyncPerpetualTaskResponse =
        perpetualTaskResponseCaptor.getValue();
    assertThat(googleFunctionInstanceSyncPerpetualTaskResponse.getCommandExecutionStatus())
        .isEqualTo(CommandExecutionStatus.SUCCESS);
  }
}
