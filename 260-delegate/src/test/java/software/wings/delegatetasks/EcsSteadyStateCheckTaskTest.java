/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.SATYAM;

import static software.wings.utils.WingsTestConstants.DELEGATE_ID;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.container.ContainerInfo;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.TimeoutException;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.container.EcsSteadyStateCheckParams;
import software.wings.beans.container.EcsSteadyStateCheckResponse;
import software.wings.cloudprovider.aws.EcsContainerService;
import software.wings.service.impl.AwsHelperService;

import com.amazonaws.services.ecs.model.DescribeServicesResult;
import com.amazonaws.services.ecs.model.Service;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDP)
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class EcsSteadyStateCheckTaskTest extends WingsBaseTest {
  @Mock private AwsHelperService mockAwsHelperService;
  @Mock private DelegateLogService mockDelegateLogService;
  @Mock private EcsContainerService mockEcsContainerService;

  @InjectMocks
  private EcsSteadyStateCheckTask task =
      new EcsSteadyStateCheckTask(DelegateTaskPackage.builder()
                                      .delegateId(DELEGATE_ID)
                                      .data(TaskData.builder().async(true).timeout(DEFAULT_ASYNC_CALL_TIMEOUT).build())
                                      .build(),
          null, notifyResponseData -> {}, () -> true);
  @Before
  public void setUp() throws Exception {
    on(task).set("awsHelperService", mockAwsHelperService);
    on(task).set("delegateLogService", mockDelegateLogService);
    on(task).set("ecsContainerService", mockEcsContainerService);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testRun() {
    doReturn(new DescribeServicesResult().withServices(
                 new Service().withServiceName("Name").withClusterArn("Cluster").withDesiredCount(1)))
        .when(mockAwsHelperService)
        .describeServices(any(), any(), any(), any());
    doNothing().when(mockEcsContainerService).waitForTasksToBeInRunningStateWithHandledExceptions(any());
    doNothing().when(mockEcsContainerService).waitForServiceToReachSteadyState(eq(0), any());
    doReturn(singletonList(ContainerInfo.builder().containerId("cid").hostName("host").newContainer(true).build()))
        .when(mockEcsContainerService)
        .getContainerInfosAfterEcsWait(any(), any(), any(), any(), any(), any(), any());
    EcsSteadyStateCheckResponse response = task.run(new Object[] {EcsSteadyStateCheckParams.builder().build()});
    assertThat(response).isNotNull();
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(response.getContainerInfoList()).isNotNull();
    assertThat(1).isEqualTo(response.getContainerInfoList().size());
    assertThat("host").isEqualTo(response.getContainerInfoList().get(0).getHostName());
    assertThat("cid").isEqualTo(response.getContainerInfoList().get(0).getContainerId());
    assertThat(response.isTimeoutFailure()).isFalse();
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testRun_ValidateTimeout() {
    doReturn(new DescribeServicesResult().withServices(
                 new Service().withServiceName("Name").withClusterArn("Cluster").withDesiredCount(1)))
        .when(mockAwsHelperService)
        .describeServices(any(), any(), any(), any());
    doThrow(TimeoutException.class)
        .when(mockEcsContainerService)
        .waitForTasksToBeInRunningStateWithHandledExceptions(any());
    doNothing().when(mockEcsContainerService).waitForServiceToReachSteadyState(eq(0), any());
    doReturn(singletonList(ContainerInfo.builder().containerId("cid").hostName("host").newContainer(true).build()))
        .when(mockEcsContainerService)
        .getContainerInfosAfterEcsWait(any(), any(), any(), any(), any(), any(), any());
    EcsSteadyStateCheckResponse response = task.run(new Object[] {EcsSteadyStateCheckParams.builder().build()});
    assertThat(response).isNotNull();
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
    assertThat(response.isTimeoutFailure()).isFalse();

    response = task.run(new Object[] {EcsSteadyStateCheckParams.builder().timeoutErrorSupported(true).build()});
    assertThat(response).isNotNull();
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
    assertThat(response.isTimeoutFailure()).isTrue();
  }
}
