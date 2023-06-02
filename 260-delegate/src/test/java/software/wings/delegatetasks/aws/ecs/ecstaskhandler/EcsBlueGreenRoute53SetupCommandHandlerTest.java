/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.aws.ecs.ecstaskhandler;

import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.ARVIND;

import static software.wings.beans.command.EcsSetupParams.EcsSetupParamsBuilder.anEcsSetupParams;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.AwsConfig;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.delegatetasks.DelegateFileManager;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.helpers.ext.ecs.request.EcsBGRoute53ServiceSetupRequest;
import software.wings.helpers.ext.ecs.request.EcsServiceSetupRequest;
import software.wings.helpers.ext.ecs.response.EcsCommandExecutionResponse;
import software.wings.service.intfc.security.EncryptionService;

import com.amazonaws.services.ecs.model.TaskDefinition;
import com.google.inject.Inject;
import java.util.HashMap;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class EcsBlueGreenRoute53SetupCommandHandlerTest extends WingsBaseTest {
  @Mock private EcsSetupCommandTaskHelper mockEcsSetupCommandTaskHelper;
  @Mock private DelegateFileManager mockDelegateFileManager;
  @Mock private EncryptionService mockEncryptionService;
  @Mock private DelegateLogService mockDelegateLogService;

  @InjectMocks @Inject private EcsBlueGreenRoute53SetupCommandHandler handler;

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalFailure() {
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    EcsServiceSetupRequest request = EcsServiceSetupRequest.builder().build();
    EcsCommandExecutionResponse response = handler.executeTaskInternal(request, null, mockCallback);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(response.getEcsCommandResponse().getOutput())
        .isEqualTo("Invalid Request Type: Expected was : [EcsBGRoute53ServiceSetupRequest]");
    assertThat(response.getEcsCommandResponse().getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testExecuteTaskInternal() {
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);

    EcsBGRoute53ServiceSetupRequest request = EcsBGRoute53ServiceSetupRequest.builder()
                                                  .ecsSetupParams(anEcsSetupParams().build())
                                                  .awsConfig(AwsConfig.builder().build())
                                                  .serviceVariables(new HashMap<>())
                                                  .safeDisplayServiceVariables(new HashMap<>())
                                                  .build();
    doReturn(SERVICE_ID).when(mockEcsSetupCommandTaskHelper).createEcsService(any(), any(), any(), any(), any(), any());
    doReturn(new TaskDefinition())
        .when(mockEcsSetupCommandTaskHelper)
        .createTaskDefinition(eq(request.getAwsConfig()), any(), eq(request.getServiceVariables()),
            eq(request.getSafeDisplayServiceVariables()), any(), any());
    EcsCommandExecutionResponse response = handler.executeTaskInternal(request, null, mockCallback);

    verify(mockEcsSetupCommandTaskHelper)
        .createTaskDefinition(eq(request.getAwsConfig()), any(), eq(request.getServiceVariables()),
            eq(request.getSafeDisplayServiceVariables()), any(), any());

    verify(mockEcsSetupCommandTaskHelper).deleteExistingServicesOtherThanBlueVersion(any(), any(), any(), any());
    verify(mockEcsSetupCommandTaskHelper).createEcsService(any(), any(), any(), any(), any(), any());

    verify(mockEcsSetupCommandTaskHelper)
        .storeCurrentServiceNameAndCountInfo(eq(request.getAwsConfig()), any(), any(), any(), eq(SERVICE_ID));
    verify(mockEcsSetupCommandTaskHelper).backupAutoScalarConfig(any(), any(), any(), anyString(), any(), any());
    verify(mockEcsSetupCommandTaskHelper).logLoadBalancerInfo(any(), any());

    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getEcsCommandResponse().getCommandExecutionStatus()).isEqualTo(SUCCESS);
  }
}
