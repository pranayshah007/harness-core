/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.cloudformation.handlers;

import static io.harness.rule.OwnerRule.NGONZALEZ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.task.cloudformation.CloudformationBaseHelper;
import io.harness.delegate.task.cloudformation.CloudformationTaskNGParameters;
import io.harness.delegate.task.cloudformation.CloudformationTaskNGResponse;
import io.harness.delegate.task.cloudformation.CloudformationTaskType;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import com.amazonaws.services.cloudformation.model.Stack;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDP)
public class CloudformationDeleteStackTaskHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock LogCallback logCallback;
  @Mock CloudformationBaseHelper cloudformationBaseHelper;
  @InjectMocks @Spy private CloudformationDeleteStackTaskHandler handler;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testHandleHappyPath() throws Exception {
    CloudformationTaskNGParameters parameters = getCloudformationTaskNGParameters();
    doReturn(AwsInternalConfig.builder().build())
        .when(cloudformationBaseHelper)
        .getAwsInternalConfig(any(), any(), any());
    Optional<Stack> stack =
        Optional.of(new Stack().withStackName("stackName").withRoleARN("roleARN").withStackId("id"));
    doReturn(stack).when(handler).getIfStackExists(any(), any(), any());
    CloudformationTaskNGResponse response = handler.executeTask(parameters, "delegate_id", "task_id", logCallback);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    verify(handler, times(1)).getIfStackExists(any(), any(), any());
    verify(cloudformationBaseHelper, times(1)).getAwsInternalConfig(any(), any(), any());
    verify(cloudformationBaseHelper, times(1)).deleteStack(any(), any(), any(), any(), anyInt());
    verify(cloudformationBaseHelper, times(1)).waitForStackToBeDeleted(any(), any(), any(), any(), anyLong());
  }

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testHandleThrowInvalidException() throws IOException, TimeoutException, InterruptedException {
    CloudformationTaskNGParameters parameters = getCloudformationTaskNGParameters();
    doThrow(InvalidArgumentsException.class).when(cloudformationBaseHelper).getAwsInternalConfig(any(), any(), any());

    handler.executeTaskInternal(parameters, "delegate_id", "task_id", logCallback);
    verify(handler, times(0)).getIfStackExists(any(), any(), any());
    verify(cloudformationBaseHelper, times(1)).getAwsInternalConfig(any(), any(), any());
    verify(cloudformationBaseHelper, times(0)).deleteStack(any(), any(), any(), any(), anyInt());
    verify(cloudformationBaseHelper, times(0)).waitForStackToBeDeleted(any(), any(), any(), any(), anyLong());
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testHandleStackDoesntExists() throws Exception {
    CloudformationTaskNGParameters parameters = getCloudformationTaskNGParameters();
    doReturn(AwsInternalConfig.builder().build())
        .when(cloudformationBaseHelper)
        .getAwsInternalConfig(any(), any(), any());
    Optional<Stack> stack = Optional.empty();
    doReturn(stack).when(handler).getIfStackExists(any(), any(), any());
    CloudformationTaskNGResponse response = handler.executeTask(parameters, "delegate_id", "task_id", logCallback);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    verify(handler, times(1)).getIfStackExists(any(), any(), any());
    verify(cloudformationBaseHelper, times(1)).getAwsInternalConfig(any(), any(), any());
    verify(cloudformationBaseHelper, times(0)).deleteStack(any(), any(), any(), any(), anyInt());
    verify(cloudformationBaseHelper, times(0)).waitForStackToBeDeleted(any(), any(), any(), any(), anyLong());
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testHandleDeleteStackTimeout() throws Exception {
    CloudformationTaskNGParameters parameters = getCloudformationTaskNGParameters();
    doReturn(AwsInternalConfig.builder().build())
        .when(cloudformationBaseHelper)
        .getAwsInternalConfig(any(), any(), any());
    Optional<Stack> stack =
        Optional.of(new Stack().withStackName("stackName").withRoleARN("roleARN").withStackId("id"));
    doReturn(stack).when(handler).getIfStackExists(any(), any(), any());
    doAnswer(invocationOnMock -> { throw new TimeoutException(); })
        .when(cloudformationBaseHelper)
        .deleteStack(any(), any(), any(), any(), anyInt());
    CloudformationTaskNGResponse response = handler.executeTask(parameters, "delegate_id", "task_id", logCallback);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    verify(handler, times(1)).getIfStackExists(any(), any(), any());
    verify(cloudformationBaseHelper, times(1)).getAwsInternalConfig(any(), any(), any());
    verify(cloudformationBaseHelper, times(1)).deleteStack(any(), any(), any(), any(), anyInt());
    verify(cloudformationBaseHelper, times(0)).waitForStackToBeDeleted(any(), any(), any(), any(), anyLong());
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testHandleWaitDeleteStackTimeout() throws Exception {
    CloudformationTaskNGParameters parameters = getCloudformationTaskNGParameters();
    doReturn(AwsInternalConfig.builder().build())
        .when(cloudformationBaseHelper)
        .getAwsInternalConfig(any(), any(), any());
    Optional<Stack> stack =
        Optional.of(new Stack().withStackName("stackName").withRoleARN("roleARN").withStackId("id"));
    doReturn(stack).when(handler).getIfStackExists(any(), any(), any());
    doAnswer(invocationOnMock -> { throw new TimeoutException(); })
        .when(cloudformationBaseHelper)
        .waitForStackToBeDeleted(any(), any(), any(), any(), anyLong());
    CloudformationTaskNGResponse response = handler.executeTask(parameters, "delegate_id", "task_id", logCallback);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    verify(handler, times(1)).getIfStackExists(any(), any(), any());
    verify(cloudformationBaseHelper, times(1)).getAwsInternalConfig(any(), any(), any());
    verify(cloudformationBaseHelper, times(1)).deleteStack(any(), any(), any(), any(), anyInt());
    verify(cloudformationBaseHelper, times(1)).waitForStackToBeDeleted(any(), any(), any(), any(), anyLong());
  }

  private CloudformationTaskNGParameters getCloudformationTaskNGParameters() {
    return CloudformationTaskNGParameters.builder()
        .awsConnector(AwsConnectorDTO.builder().build())
        .taskType(CloudformationTaskType.DELETE_STACK)
        .encryptedDataDetails(new ArrayList<>())
        .region("region")
        .stackName("stackNAme")
        .accountId("123456789012")
        .build();
  }
}
