/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.aws.ecs.ecstaskhandler;

import static io.harness.rule.OwnerRule.IVAN;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockConstruction;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.beans.command.ResizeCommandUnit;
import software.wings.helpers.ext.ecs.request.EcsServiceDeployRequest;
import software.wings.helpers.ext.ecs.response.EcsCommandExecutionResponse;

import com.google.inject.Injector;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class EcsServiceDeployCommandHandlerTest extends WingsBaseTest {
  @Mock private Injector injector;
  @Mock private ExecutionLogCallback executionLogCallback;
  @Mock private ResizeCommandUnit resizeCommandUnit;

  @InjectMocks private EcsServiceDeployCommandHandler ecsSetupCommandTaskHelper;

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  @Ignore("Disable top running CG tests")
  public void testExecuteTaskInternalWithException() throws Exception {
    try (MockedConstruction<ExecutionLogCallback> ignore = mockConstruction(ExecutionLogCallback.class,
             (mock, context) -> doNothing().when(mock).saveExecutionLog(anyString(), any()));
         MockedConstruction<ResizeCommandUnit> ignore1 = mockConstruction(ResizeCommandUnit.class)) {
      doThrow(new RuntimeException("Error msg")).when(resizeCommandUnit).execute(any());

      EcsServiceDeployRequest request = EcsServiceDeployRequest.builder().build();
      EcsCommandExecutionResponse ecsCommandExecutionResponse =
          ecsSetupCommandTaskHelper.executeTask(request, emptyList());

      assertThat(ecsCommandExecutionResponse).isNotNull();
      assertThat(ecsCommandExecutionResponse.getCommandExecutionStatus()).isNotNull();
      assertThat(ecsCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
      assertThat(ecsCommandExecutionResponse.getErrorMessage()).isEqualTo("RuntimeException: Error msg");
    }
  }
}
