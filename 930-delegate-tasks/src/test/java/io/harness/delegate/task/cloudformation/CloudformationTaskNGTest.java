/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.cloudformation;

import static io.harness.rule.OwnerRule.VLICA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.cloudformation.handlers.CloudformationAbstractTaskHandler;
import io.harness.exception.UnexpectedTypeException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(HarnessTeam.CDP)
public class CloudformationTaskNGTest extends CategoryTest {
  @Spy private Map<CloudformationTaskType, CloudformationAbstractTaskHandler> handlersMap = new HashMap<>();
  @Mock private CloudformationAbstractTaskHandler cloudFormationCreateStackHandler;
  @Mock private ILogStreamingTaskClient logStreamingTaskClient;
  @Mock private ExecutorService executorService;

  @InjectMocks
  private CloudformationTaskNG cloudformationTaskNG =
      new CloudformationTaskNG(DelegateTaskPackage.builder().data(TaskData.builder().build()).build(),
          logStreamingTaskClient, mock(Consumer.class), mock(BooleanSupplier.class));

  private Future<?> future;
  CloudformationTaskNGParameters taskNGParameters;
  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    handlersMap.put(CloudformationTaskType.CREATE_STACK, cloudFormationCreateStackHandler);

    taskNGParameters = CloudformationTaskNGParameters.builder()
                           .taskType(CloudformationTaskType.CREATE_STACK)
                           .cfCommandUnit(CloudformationCommandUnit.CreateStack)
                           .accountId("test-account-id")
                           .awsConnector(AwsConnectorDTO.builder().build())
                           .region("test-region")
                           .stackName("test-stackName")
                           .encryptedDataDetails(Collections.singletonList(EncryptedDataDetail.builder().build()))
                           .build();

    doReturn(executorService).when(logStreamingTaskClient).obtainTaskProgressExecutor();
    doReturn(future).when(executorService).submit(any(Runnable.class));
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testCFTaskNGWithParams() throws Exception {
    doReturn(CloudformationTaskNGResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build())
        .when(cloudFormationCreateStackHandler)
        .executeTask(eq(taskNGParameters), any(), any(), any());

    CloudformationTaskNGResponse response = (CloudformationTaskNGResponse) cloudformationTaskNG.run(taskNGParameters);

    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testCFTaskNGWithParamsAndThrowsException() throws Exception {
    doThrow(new RuntimeException("Failed to create the stack"))
        .when(cloudFormationCreateStackHandler)
        .executeTask(eq(taskNGParameters), any(), any(), any());

    assertThatThrownBy(() -> cloudformationTaskNG.run(taskNGParameters)).isInstanceOf(TaskNGDataException.class);
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testCFTaskNGWithParamsAndNoTaskType() throws Exception {
    handlersMap.remove(CloudformationTaskType.CREATE_STACK);
    try {
      cloudformationTaskNG.run(taskNGParameters);
    } catch (UnexpectedTypeException exception) {
      assertThat(exception).isInstanceOf(UnexpectedTypeException.class);
      assertThat(exception.getParams().get("message")).isEqualTo("No handler found for task type CREATE_STACK");
    }
  }
}
