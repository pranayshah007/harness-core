/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.aws.ecs.ecstaskhandler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.ARVIND;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.exception.TimeoutException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.beans.container.AwsAutoScalarConfig;
import software.wings.cloudprovider.aws.EcsContainerService;
import software.wings.delegatetasks.DelegateFileManager;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.helpers.ext.ecs.request.EcsBGRoute53DNSWeightUpdateRequest;
import software.wings.helpers.ext.ecs.request.EcsServiceSetupRequest;
import software.wings.helpers.ext.ecs.response.EcsCommandExecutionResponse;
import software.wings.service.intfc.aws.delegate.AwsRoute53HelperServiceDelegate;
import software.wings.service.intfc.aws.delegate.AwsServiceDiscoveryHelperServiceDelegate;
import software.wings.service.intfc.security.EncryptionService;

import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@OwnedBy(CDP)
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class EcsBlueGreenRoute53DNSWeightHandlerTest extends WingsBaseTest {
  private final EcsBlueGreenRoute53DNSWeightHandler task = new EcsBlueGreenRoute53DNSWeightHandler();

  private final String newSvcArn = "newSvcArn";
  private final String oldSvcArn = "oldSvcArn";
  private final String newValue = "newValue";
  private final String oldValue = "oldValue";
  @Mock private EcsContainerService mockEcsContainerService;
  @Mock private EcsSwapRoutesCommandTaskHelper mockEcsSwapRoutesCommandTaskHelper;
  @Mock private AwsRoute53HelperServiceDelegate mockAwsRoute53HelperServiceDelegate;
  @Mock private AwsServiceDiscoveryHelperServiceDelegate mockAwsServiceDiscoveryHelperServiceDelegate;
  @Mock private DelegateFileManager mockDelegateFileManager;
  @Mock private EncryptionService mockEncryptionService;
  @Mock private DelegateLogService mockDelegateLogService;

  @Before
  public void setUp() throws Exception {
    on(task).set("ecsContainerService", mockEcsContainerService);
    on(task).set("ecsSwapRoutesCommandTaskHelper", mockEcsSwapRoutesCommandTaskHelper);
    on(task).set("awsRoute53HelperServiceDelegate", mockAwsRoute53HelperServiceDelegate);
    on(task).set("awsServiceDiscoveryHelperServiceDelegate", mockAwsServiceDiscoveryHelperServiceDelegate);
    on(task).set("delegateFileManager", mockDelegateFileManager);
    on(task).set("encryptionService", mockEncryptionService);
    on(task).set("delegateLogService", mockDelegateLogService);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalFailure() {
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    EcsServiceSetupRequest request = EcsServiceSetupRequest.builder().build();
    EcsCommandExecutionResponse response = task.executeTaskInternal(request, null, mockCallback);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(response.getEcsCommandResponse().getOutput())
        .isEqualTo("Invalid Request Type: Expected was : [EcsBGRoute53DNSWeightUpdateRequest]");
    assertThat(response.getEcsCommandResponse().getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testExecuteTaskInternal_NoRollback_NoDownsizeOldService() {
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);

    doReturn(newValue)
        .when(mockAwsServiceDiscoveryHelperServiceDelegate)
        .getRecordValueForService(any(), any(), any(), eq(newSvcArn));

    doReturn(oldValue)
        .when(mockAwsServiceDiscoveryHelperServiceDelegate)
        .getRecordValueForService(any(), any(), any(), eq(oldSvcArn));

    EcsBGRoute53DNSWeightUpdateRequest request = EcsBGRoute53DNSWeightUpdateRequest.builder()
                                                     .rollback(false)
                                                     .downsizeOldService(false)
                                                     .newServiceDiscoveryArn(newSvcArn)
                                                     .newServiceWeight(60)
                                                     .oldServiceDiscoveryArn(oldSvcArn)
                                                     .oldServiceWeight(40)
                                                     .build();
    EcsCommandExecutionResponse response = task.executeTaskInternal(request, null, mockCallback);

    verify(mockCallback, times(2)).saveExecutionLog(any());
    verify(mockAwsRoute53HelperServiceDelegate)
        .upsertRoute53ParentRecord(
            any(), any(), any(), any(), any(), eq(60), eq(newValue), eq(40), eq(oldValue), anyInt());
    verify(mockEcsSwapRoutesCommandTaskHelper)
        .updateServiceTags(any(), any(), any(), any(), any(), any(), anyBoolean(), any());
    verify(mockEcsSwapRoutesCommandTaskHelper, times(0))
        .downsizeOlderService(any(), any(), any(), any(), any(), any(), anyInt());

    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getEcsCommandResponse().getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getEcsCommandResponse().isTimeoutFailure()).isFalse();
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testExecuteTaskInternal_NoRollback_DownsizeOldService() {
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);

    doReturn(newValue)
        .when(mockAwsServiceDiscoveryHelperServiceDelegate)
        .getRecordValueForService(any(), any(), any(), eq(newSvcArn));

    doReturn(oldValue)
        .when(mockAwsServiceDiscoveryHelperServiceDelegate)
        .getRecordValueForService(any(), any(), any(), eq(oldSvcArn));

    EcsBGRoute53DNSWeightUpdateRequest request = EcsBGRoute53DNSWeightUpdateRequest.builder()
                                                     .rollback(false)
                                                     .downsizeOldService(true)
                                                     .newServiceDiscoveryArn(newSvcArn)
                                                     .newServiceWeight(100)
                                                     .oldServiceDiscoveryArn(oldSvcArn)
                                                     .oldServiceWeight(0)
                                                     .build();
    EcsCommandExecutionResponse response = task.executeTaskInternal(request, null, mockCallback);

    verify(mockCallback, times(3)).saveExecutionLog(any());
    verify(mockAwsRoute53HelperServiceDelegate)
        .upsertRoute53ParentRecord(
            any(), any(), any(), any(), any(), eq(100), eq(newValue), eq(0), eq(oldValue), anyInt());
    verify(mockEcsSwapRoutesCommandTaskHelper)
        .updateServiceTags(any(), any(), any(), any(), any(), any(), anyBoolean(), any());
    verify(mockEcsSwapRoutesCommandTaskHelper).downsizeOlderService(any(), any(), any(), any(), any(), any(), anyInt());

    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getEcsCommandResponse().getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getEcsCommandResponse().isTimeoutFailure()).isFalse();
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testExecuteTaskInternal_Rollback() {
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);

    doReturn(newValue)
        .when(mockAwsServiceDiscoveryHelperServiceDelegate)
        .getRecordValueForService(any(), any(), any(), eq(newSvcArn));

    doReturn(oldValue)
        .when(mockAwsServiceDiscoveryHelperServiceDelegate)
        .getRecordValueForService(any(), any(), any(), eq(oldSvcArn));

    AwsAutoScalarConfig previousAwsAutoScalarConfig = AwsAutoScalarConfig.builder().resourceId("abc").build();
    EcsBGRoute53DNSWeightUpdateRequest request =
        EcsBGRoute53DNSWeightUpdateRequest.builder()
            .rollback(true)
            .newServiceDiscoveryArn(newSvcArn)
            .oldServiceDiscoveryArn(oldSvcArn)
            .previousAwsAutoScalarConfigs(Arrays.asList(previousAwsAutoScalarConfig))
            .build();
    EcsCommandExecutionResponse response = task.executeTaskInternal(request, null, mockCallback);

    int expectedNumberOfLogItems = 5;
    verify(mockCallback, times(expectedNumberOfLogItems)).saveExecutionLog(any());

    verify(mockEcsSwapRoutesCommandTaskHelper)
        .upsizeOlderService(any(), any(), any(), any(), anyInt(), any(), any(), anyInt(), anyBoolean());
    verify(mockAwsRoute53HelperServiceDelegate)
        .upsertRoute53ParentRecord(
            any(), any(), any(), any(), any(), eq(100), eq(oldValue), eq(0), eq(newValue), anyInt());
    verify(mockEcsSwapRoutesCommandTaskHelper)
        .updateServiceTags(any(), any(), any(), any(), any(), any(), anyBoolean(), any());
    verify(mockEcsSwapRoutesCommandTaskHelper)
        .restoreAwsAutoScalarConfig(
            any(), any(), any(), eq(Arrays.asList(previousAwsAutoScalarConfig)), eq(true), any());
    verify(mockEcsSwapRoutesCommandTaskHelper).downsizeOlderService(any(), any(), any(), any(), any(), any(), anyInt());

    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getEcsCommandResponse().getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getEcsCommandResponse().isTimeoutFailure()).isFalse();
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testExecuteTaskInternal_NoRollback_DownsizeOldService_TimeoutHandling() {
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);

    doReturn(newValue)
        .when(mockAwsServiceDiscoveryHelperServiceDelegate)
        .getRecordValueForService(any(), any(), any(), eq(newSvcArn));

    doReturn(oldValue)
        .when(mockAwsServiceDiscoveryHelperServiceDelegate)
        .getRecordValueForService(any(), any(), any(), eq(oldSvcArn));

    EcsBGRoute53DNSWeightUpdateRequest request = EcsBGRoute53DNSWeightUpdateRequest.builder()
                                                     .rollback(false)
                                                     .downsizeOldService(true)
                                                     .newServiceDiscoveryArn(newSvcArn)
                                                     .newServiceWeight(100)
                                                     .oldServiceDiscoveryArn(oldSvcArn)
                                                     .oldServiceWeight(0)
                                                     .timeoutErrorSupported(true)
                                                     .build();

    doThrow(new TimeoutException("", "", null))
        .when(mockEcsSwapRoutesCommandTaskHelper)
        .downsizeOlderService(any(), any(), any(), any(), any(), any(), anyInt());

    EcsCommandExecutionResponse response = task.executeTaskInternal(request, null, mockCallback);

    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(FAILURE);
    assertThat(response.getEcsCommandResponse().getCommandExecutionStatus()).isEqualTo(FAILURE);
    assertThat(response.getEcsCommandResponse().isTimeoutFailure()).isTrue();
  }
}
