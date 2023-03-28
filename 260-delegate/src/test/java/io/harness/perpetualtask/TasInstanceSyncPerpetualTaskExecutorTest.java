/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import io.harness.DelegateTestBase;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.tasconnector.TasConnectorDTO;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.TasInstanceSyncPerpetualTaskResponse;
import io.harness.delegate.beans.instancesync.info.TasServerInstanceInfo;
import io.harness.delegate.task.pcf.TasTaskHelperBase;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.instancesync.TasDeploymentRelease;
import io.harness.perpetualtask.instancesync.TasInstanceSyncPerpetualTaskParams;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
public class TasInstanceSyncPerpetualTaskExecutorTest extends DelegateTestBase {
  private static final String PROJECT_IDENTIFIER = "project";
  private static final String ACCOUNT_IDENTIFIER = "account";
  private static final String ORG_IDENTIFIER = "org";
  private static final String CONNECTOR = "connector";
  private static final String APPLICATION_NAME = "application";
  private static final String APPLICATION_ID = "app_Id";
  private static final String SPACE = "space";
  private static final String PERPETUAL_TASK_ID = "perpetualTaskId";

  @Inject private KryoSerializer kryoSerializer;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Mock private TasTaskHelperBase tasTaskHelperBase;
  @Mock private DelegateAgentManagerClient delegateAgentManagerClient;
  @Mock private Call<RestResponse<Boolean>> call;

  @Captor private ArgumentCaptor<TasInstanceSyncPerpetualTaskResponse> perpetualTaskResponseCaptor;
  @InjectMocks TasInstanceSyncPerpetualTaskExecuter tasInstanceSyncPerpetualTaskExecuter;

  @Before
  public void setUp() throws IOException {
    on(tasInstanceSyncPerpetualTaskExecuter).set("referenceFalseKryoSerializer", referenceFalseKryoSerializer);
    doReturn(call)
        .when(delegateAgentManagerClient)
        .processInstanceSyncNGResult(anyString(), anyString(), perpetualTaskResponseCaptor.capture());
    doReturn(retrofit2.Response.success("success")).when(call).execute();
  }

  @Test
  @Owner(developers = OwnerRule.SOURABH)
  @Category(UnitTests.class)
  public void runOnceWithDifferentAppNames() {
    TasInfraConfig tasInfraConfig = TasInfraConfig.builder()
                                        .space(SPACE)
                                        .tasConnectorDTO(TasConnectorDTO.builder().build())
                                        .organization(ORG_IDENTIFIER)
                                        .build();
    List<TasDeploymentRelease> deploymentReleases =
        getTasDeploymentReleases(tasInfraConfig, Arrays.asList("app1", "app2"));
    List<TasServerInstanceInfo> tasServerInstanceInfos = getServerInstanceInfo(Arrays.asList("app1", "app2"));

    doReturn(tasServerInstanceInfos).when(tasTaskHelperBase).getTasServerInstanceInfos(any());
    doReturn(call)
        .when(delegateAgentManagerClient)
        .processInstanceSyncNGResult(anyString(), anyString(), perpetualTaskResponseCaptor.capture());
    TasInstanceSyncPerpetualTaskParams message = TasInstanceSyncPerpetualTaskParams.newBuilder()
                                                     .setAccountId(ACCOUNT_IDENTIFIER)
                                                     .addAllTasDeploymentReleaseList(deploymentReleases)
                                                     .build();
    PerpetualTaskExecutionParams perpetualTaskExecutionParams = PerpetualTaskExecutionParams.newBuilder()
                                                                    .setCustomizedParams(Any.pack(message))
                                                                    .setReferenceFalseKryoSerializer(true)
                                                                    .build();

    tasInstanceSyncPerpetualTaskExecuter.runOnce(
        PerpetualTaskId.newBuilder().setId(PERPETUAL_TASK_ID).build(), perpetualTaskExecutionParams, Instant.EPOCH);

    assertThat(perpetualTaskResponseCaptor.getValue()).isInstanceOf(TasInstanceSyncPerpetualTaskResponse.class);
    TasInstanceSyncPerpetualTaskResponse tasInstanceSyncPerpetualTaskResponse = perpetualTaskResponseCaptor.getValue();
    assertThat(tasInstanceSyncPerpetualTaskResponse.getCommandExecutionStatus())
        .isEqualTo(CommandExecutionStatus.SUCCESS);
    List<ServerInstanceInfo> serverInstanceDetails = tasInstanceSyncPerpetualTaskResponse.getServerInstanceDetails();
    assertThat(serverInstanceDetails.size()).isEqualTo(4);

    serverInstanceDetails.forEach(serverInstanceInfo -> {
      TasServerInstanceInfo tasServerInstanceInfo = (TasServerInstanceInfo) serverInstanceInfo;
      String app = tasServerInstanceInfo.getTasApplicationName();
      assertThat(Arrays.asList("app1", "app2").contains(app)).isTrue();
    });
  }

  @Test
  @Owner(developers = OwnerRule.SOURABH)
  @Category(UnitTests.class)
  public void runOnceWhenApplicationDoesNotExist() {
    TasInfraConfig tasInfraConfig = TasInfraConfig.builder()
                                        .space(SPACE)
                                        .tasConnectorDTO(TasConnectorDTO.builder().build())
                                        .organization(ORG_IDENTIFIER)
                                        .build();
    List<TasDeploymentRelease> deploymentReleases =
        getTasDeploymentReleases(tasInfraConfig, Arrays.asList("app1", "app2"));

    doThrow(InvalidRequestException.class).when(tasTaskHelperBase).getTasServerInstanceInfos(any());
    doReturn(call)
        .when(delegateAgentManagerClient)
        .processInstanceSyncNGResult(anyString(), anyString(), perpetualTaskResponseCaptor.capture());
    TasInstanceSyncPerpetualTaskParams message = TasInstanceSyncPerpetualTaskParams.newBuilder()
                                                     .setAccountId(ACCOUNT_IDENTIFIER)
                                                     .addAllTasDeploymentReleaseList(deploymentReleases)
                                                     .build();
    PerpetualTaskExecutionParams perpetualTaskExecutionParams = PerpetualTaskExecutionParams.newBuilder()
                                                                    .setCustomizedParams(Any.pack(message))
                                                                    .setReferenceFalseKryoSerializer(true)
                                                                    .build();

    tasInstanceSyncPerpetualTaskExecuter.runOnce(
        PerpetualTaskId.newBuilder().setId(PERPETUAL_TASK_ID).build(), perpetualTaskExecutionParams, Instant.EPOCH);

    assertThat(perpetualTaskResponseCaptor.getValue()).isInstanceOf(TasInstanceSyncPerpetualTaskResponse.class);
    TasInstanceSyncPerpetualTaskResponse tasInstanceSyncPerpetualTaskResponse = perpetualTaskResponseCaptor.getValue();
    assertThat(tasInstanceSyncPerpetualTaskResponse.getCommandExecutionStatus())
        .isEqualTo(CommandExecutionStatus.SUCCESS);
    List<ServerInstanceInfo> serverInstanceDetails = tasInstanceSyncPerpetualTaskResponse.getServerInstanceDetails();
    assertThat(serverInstanceDetails.size()).isZero();
  }

  @Test
  @Owner(developers = OwnerRule.SOURABH)
  @Category(UnitTests.class)
  public void runOnceWhenPublishInstanceSyncFails() {
    TasInfraConfig tasInfraConfig = TasInfraConfig.builder()
                                        .space(SPACE)
                                        .tasConnectorDTO(TasConnectorDTO.builder().build())
                                        .organization(ORG_IDENTIFIER)
                                        .build();
    List<TasDeploymentRelease> deploymentReleases =
        getTasDeploymentReleases(tasInfraConfig, Arrays.asList("app1", "app2"));
    List<TasServerInstanceInfo> tasServerInstanceInfos = getServerInstanceInfo(Arrays.asList("app1", "app2"));

    doReturn(tasServerInstanceInfos).when(tasTaskHelperBase).getTasServerInstanceInfos(any());
    doThrow(InvalidRequestException.class)
        .when(delegateAgentManagerClient)
        .processInstanceSyncNGResult(anyString(), anyString(), perpetualTaskResponseCaptor.capture());
    TasInstanceSyncPerpetualTaskParams message = TasInstanceSyncPerpetualTaskParams.newBuilder()
                                                     .setAccountId(ACCOUNT_IDENTIFIER)
                                                     .addAllTasDeploymentReleaseList(deploymentReleases)
                                                     .build();
    PerpetualTaskExecutionParams perpetualTaskExecutionParams = PerpetualTaskExecutionParams.newBuilder()
                                                                    .setCustomizedParams(Any.pack(message))
                                                                    .setReferenceFalseKryoSerializer(true)
                                                                    .build();

    PerpetualTaskResponse perpetualTaskResponse = tasInstanceSyncPerpetualTaskExecuter.runOnce(
        PerpetualTaskId.newBuilder().setId(PERPETUAL_TASK_ID).build(), perpetualTaskExecutionParams, Instant.EPOCH);

    assertThat(perpetualTaskResponseCaptor.getValue()).isInstanceOf(TasInstanceSyncPerpetualTaskResponse.class);
    assertThat(perpetualTaskResponse.getResponseMessage()).isNotEqualTo("success");
  }

  private List<TasServerInstanceInfo> getServerInstanceInfo(List<String> appList) {
    List<TasServerInstanceInfo> tasServerInstanceInfos = new java.util.ArrayList<>(Collections.emptyList());
    appList.forEach(app -> tasServerInstanceInfos.add(TasServerInstanceInfo.builder().tasApplicationName(app).build()));
    return tasServerInstanceInfos;
  }

  private List<TasDeploymentRelease> getTasDeploymentReleases(TasInfraConfig tasInfraConfig, List<String> appNames) {
    List<TasDeploymentRelease> tasDeploymentReleaseList = new java.util.ArrayList<>(Collections.emptyList());
    appNames.forEach(app
        -> tasDeploymentReleaseList.add(
            TasDeploymentRelease.newBuilder()
                .setApplicationName(app)
                .setTasInfraConfig(ByteString.copyFrom(referenceFalseKryoSerializer.asBytes(tasInfraConfig)))
                .build()));
    return tasDeploymentReleaseList;
  }
}
