/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.cloudformation;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.rule.OwnerRule.RAGHVENDRA;
import static io.harness.rule.OwnerRule.SATYAM;

import static java.util.Collections.emptyList;
import static org.joor.Reflect.on;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.delegatetasks.cloudformation.cloudformationtaskhandler.CloudFormationCreateStackHandler;
import software.wings.delegatetasks.cloudformation.cloudformationtaskhandler.CloudFormationDeleteStackHandler;
import software.wings.delegatetasks.cloudformation.cloudformationtaskhandler.CloudFormationListStacksHandler;
import software.wings.helpers.ext.cloudformation.request.CloudFormationCommandRequest.CloudFormationCommandType;
import software.wings.helpers.ext.cloudformation.request.CloudFormationCreateStackRequest;
import software.wings.helpers.ext.cloudformation.request.CloudFormationDeleteStackRequest;
import software.wings.helpers.ext.cloudformation.request.CloudFormationListStacksRequest;
import software.wings.helpers.ext.cloudformation.response.CloudFormationCommandExecutionResponse;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class CloudFormationCommandTaskTest extends WingsBaseTest {
  @Mock private CloudFormationCreateStackHandler mockCreateStackHandler;
  @Mock private CloudFormationDeleteStackHandler mockDeleteStackHandler;
  @Mock private CloudFormationListStacksHandler mockListStacksHandler;

  @InjectMocks
  private CloudFormationCommandTask task = new CloudFormationCommandTask(
      DelegateTaskPackage.builder()
          .delegateId("delegateid")
          .data(TaskData.builder().async(true).timeout(DEFAULT_ASYNC_CALL_TIMEOUT).build())
          .build(),
      null, notifyResponseData -> {}, () -> true);

  @Before
  public void setUp() throws Exception {
    on(task).set("createStackHandler", mockCreateStackHandler);
    on(task).set("deleteStackHandler", mockDeleteStackHandler);
    on(task).set("listStacksHandler", mockListStacksHandler);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testRun() {
    CloudFormationCreateStackRequest createStackRequest =
        CloudFormationCreateStackRequest.builder().commandType(CloudFormationCommandType.CREATE_STACK).build();
    task.run(new Object[] {createStackRequest, emptyList()});
    verify(mockCreateStackHandler).execute(any(), any());
    CloudFormationDeleteStackRequest deleteStackRequest =
        CloudFormationDeleteStackRequest.builder().commandType(CloudFormationCommandType.DELETE_STACK).build();
    task.run(new Object[] {deleteStackRequest, emptyList()});
    verify(mockDeleteStackHandler).execute(any(), any());
    CloudFormationListStacksRequest listStacksRequest =
        CloudFormationListStacksRequest.builder().commandType(CloudFormationCommandType.GET_STACKS).build();
    task.run(new Object[] {listStacksRequest, emptyList()});
    verify(mockListStacksHandler).execute(any(), any());
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testRunWithNoExecute() {
    CloudFormationCreateStackRequest unknownRequest =
        CloudFormationCreateStackRequest.builder().commandType(CloudFormationCommandType.UNKNOWN_REQUEST).build();
    CloudFormationCommandExecutionResponse response = task.run(new Object[] {unknownRequest, emptyList()});
    assert (response.getCommandExecutionStatus()).equals(CommandExecutionStatus.FAILURE);
    verify(mockCreateStackHandler, never()).execute(any(), any());
    verify(mockDeleteStackHandler, never()).execute(any(), any());
    verify(mockListStacksHandler, never()).execute(any(), any());
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testRunWithException() {
    CloudFormationCreateStackRequest createStackRequest =
        CloudFormationCreateStackRequest.builder().commandType(CloudFormationCommandType.CREATE_STACK).build();
    doThrow(new RuntimeException("Exception")).when(mockCreateStackHandler).execute(any(), any());
    CloudFormationCommandExecutionResponse response = task.run(new Object[] {createStackRequest, emptyList()});
    assert (response.getCommandExecutionStatus()).equals(CommandExecutionStatus.FAILURE);
    verify(mockDeleteStackHandler, never()).execute(any(), any());
    verify(mockListStacksHandler, never()).execute(any(), any());
  }
}
