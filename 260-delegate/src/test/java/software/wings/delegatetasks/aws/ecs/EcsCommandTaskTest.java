/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.aws.ecs;

import static io.harness.rule.OwnerRule.ARVIND;

import static software.wings.helpers.ext.ecs.request.EcsCommandRequest.EcsCommandType.BG_SERVICE_SETUP;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.TaskParameters;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.delegatetasks.aws.ecs.ecstaskhandler.EcsCommandTaskHandler;
import software.wings.helpers.ext.ecs.request.EcsCommandRequest;
import software.wings.helpers.ext.ecs.response.EcsCommandExecutionResponse;
import software.wings.service.impl.aws.model.AwsEcsRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class EcsCommandTaskTest extends WingsBaseTest {
  private final EcsCommandTask task = new EcsCommandTask(
      DelegateTaskPackage.builder().data(TaskData.builder().parameters(new Object[2]).build()).build(), null, null,
      null);
  @Mock private Map<String, EcsCommandTaskHandler> commandTaskTypeToTaskHandlerMap;

  @Before
  public void setUp() throws Exception {
    on(task).set("commandTaskTypeToTaskHandlerMap", commandTaskTypeToTaskHandlerMap);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testRunParameter() {
    assertThatThrownBy(() -> {
      TaskParameters parameters = new AwsEcsRequest(null, null, null, null);
      task.run(parameters);
    }).isInstanceOf(NotImplementedException.class);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testRunObjects() {
    EcsCommandTaskHandler mockEcsCommandTaskHandler = mock(EcsCommandTaskHandler.class);
    doReturn(mockEcsCommandTaskHandler).when(commandTaskTypeToTaskHandlerMap).get(any());
    doReturn(null).when(mockEcsCommandTaskHandler).executeTask(any(), any());

    Object[] inputParams = new Object[2];
    inputParams[0] = new EcsCommandRequest(null, null, null, null, null, null, null, BG_SERVICE_SETUP, false);
    inputParams[1] = new ArrayList<>();

    assertThat(task.run(inputParams)).isNull();
    verify(commandTaskTypeToTaskHandlerMap).get(BG_SERVICE_SETUP.name());
    verify(mockEcsCommandTaskHandler).executeTask((EcsCommandRequest) inputParams[0], (List) inputParams[1]);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testRunObjectsFailure() {
    EcsCommandTaskHandler mockEcsCommandTaskHandler = mock(EcsCommandTaskHandler.class);
    doReturn(mockEcsCommandTaskHandler).when(commandTaskTypeToTaskHandlerMap).get(any());
    doThrow(new InvalidRequestException("exeception")).when(mockEcsCommandTaskHandler).executeTask(any(), any());

    Object[] inputParams = new Object[2];
    inputParams[0] = new EcsCommandRequest(null, null, null, null, null, null, null, BG_SERVICE_SETUP, false);
    inputParams[1] = new ArrayList<>();

    EcsCommandExecutionResponse response = task.run(inputParams);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }
}
