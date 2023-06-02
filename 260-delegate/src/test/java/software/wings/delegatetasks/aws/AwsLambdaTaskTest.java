/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.aws;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.SATYAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.service.impl.aws.model.AwsLambdaExecuteFunctionRequest;
import software.wings.service.impl.aws.model.AwsLambdaExecuteFunctionResponse;
import software.wings.service.impl.aws.model.AwsLambdaExecuteWfRequest;
import software.wings.service.impl.aws.model.AwsLambdaExecuteWfResponse;
import software.wings.service.impl.aws.model.AwsLambdaFunctionRequest;
import software.wings.service.impl.aws.model.AwsLambdaFunctionResponse;
import software.wings.service.impl.aws.model.AwsLambdaRequest;
import software.wings.service.impl.aws.model.AwsResponse;
import software.wings.service.impl.aws.model.request.AwsLambdaDetailsRequest;
import software.wings.service.intfc.aws.delegate.AwsLambdaHelperServiceDelegate;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class AwsLambdaTaskTest extends WingsBaseTest {
  @Mock private DelegateLogService mockDelegateLogService;
  @Mock private AwsLambdaHelperServiceDelegate mockAwsLambdaHelperServiceDelegate;

  @InjectMocks
  private AwsLambdaTask task =
      new AwsLambdaTask(DelegateTaskPackage.builder()
                            .delegateId("delegateid")
                            .data(TaskData.builder().async(true).timeout(DEFAULT_ASYNC_CALL_TIMEOUT).build())
                            .build(),
          null, notifyResponseData -> {}, () -> true);

  @Before
  public void setUp() throws Exception {
    on(task).set("awsLambdaHelperServiceDelegate", mockAwsLambdaHelperServiceDelegate);
    on(task).set("delegateLogService", mockDelegateLogService);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testExecuteWf() {
    AwsLambdaRequest request = AwsLambdaExecuteWfRequest.builder().build();
    task.run(new Object[] {request});
    verify(mockAwsLambdaHelperServiceDelegate).executeWf(any(), any());
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testExecuteFunction() {
    AwsLambdaRequest request = AwsLambdaExecuteFunctionRequest.builder().build();
    task.run(new Object[] {request});
    verify(mockAwsLambdaHelperServiceDelegate).executeFunction(any());
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testExecuteFunctionWithException() {
    AwsLambdaExecuteFunctionRequest request = AwsLambdaExecuteFunctionRequest.builder().build();
    doThrow(new RuntimeException("Error msg")).when(mockAwsLambdaHelperServiceDelegate).executeFunction(request);

    AwsResponse awsResponse = task.run(new Object[] {request});

    verify(mockAwsLambdaHelperServiceDelegate).executeFunction(any());
    assertThat(awsResponse instanceof AwsLambdaExecuteFunctionResponse).isTrue();
    assertThat(((AwsLambdaExecuteFunctionResponse) awsResponse).getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
    assertThat(((AwsLambdaExecuteFunctionResponse) awsResponse).getErrorMessage())
        .isEqualTo("RuntimeException: Error msg");
    assertThatExceptionOfType(Exception.class)
        .isThrownBy(() -> mockAwsLambdaHelperServiceDelegate.executeFunction(request));
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testExecuteWfWithException() {
    AwsLambdaExecuteWfRequest request = AwsLambdaExecuteWfRequest.builder().build();
    doThrow(new RuntimeException("Error msg")).when(mockAwsLambdaHelperServiceDelegate).executeWf(any(), any());

    AwsResponse awsResponse = task.run(new Object[] {request});

    verify(mockAwsLambdaHelperServiceDelegate).executeWf(any(), any());
    assertThat(awsResponse instanceof AwsLambdaExecuteWfResponse).isTrue();
    assertThat(((AwsLambdaExecuteWfResponse) awsResponse).getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
    assertThat(((AwsLambdaExecuteWfResponse) awsResponse).getErrorMessage()).isEqualTo("RuntimeException: Error msg");
    assertThatExceptionOfType(Exception.class)
        .isThrownBy(() -> mockAwsLambdaHelperServiceDelegate.executeWf(any(), any()));
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetLambdaFunctions() {
    AwsLambdaFunctionRequest request = AwsLambdaFunctionRequest.builder().build();
    task.run(new Object[] {request});
    verify(mockAwsLambdaHelperServiceDelegate).getLambdaFunctions(request);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetLambdaFunctionsWithException() {
    AwsLambdaFunctionRequest request = AwsLambdaFunctionRequest.builder().build();
    doThrow(new RuntimeException("Error msg")).when(mockAwsLambdaHelperServiceDelegate).getLambdaFunctions(request);

    AwsResponse awsResponse = task.run(new Object[] {request});

    verify(mockAwsLambdaHelperServiceDelegate).getLambdaFunctions(any());
    assertThat(awsResponse instanceof AwsLambdaFunctionResponse).isTrue();
    assertThat(((AwsLambdaFunctionResponse) awsResponse).getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
    assertThat(((AwsLambdaFunctionResponse) awsResponse).getErrorMessage()).isEqualTo("RuntimeException: Error msg");
    assertThatExceptionOfType(Exception.class)
        .isThrownBy(() -> mockAwsLambdaHelperServiceDelegate.getLambdaFunctions(request));
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetFunctionDetails() {
    AwsLambdaDetailsRequest request = AwsLambdaDetailsRequest.builder().build();
    task.run(new Object[] {request});
    verify(mockAwsLambdaHelperServiceDelegate).getFunctionDetails(request, false);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetFunctionDetailsWithException() {
    AwsLambdaDetailsRequest request = AwsLambdaDetailsRequest.builder().build();
    doThrow(new RuntimeException("Error msg"))
        .when(mockAwsLambdaHelperServiceDelegate)
        .getFunctionDetails(request, false);

    AwsResponse awsResponse = task.run(new Object[] {request});

    verify(mockAwsLambdaHelperServiceDelegate).getFunctionDetails(any(), anyBoolean());
    assertThat(awsResponse instanceof AwsLambdaFunctionResponse).isTrue();
    assertThat(((AwsLambdaFunctionResponse) awsResponse).getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
    assertThat(((AwsLambdaFunctionResponse) awsResponse).getErrorMessage()).isEqualTo("RuntimeException: Error msg");
    assertThatExceptionOfType(Exception.class)
        .isThrownBy(() -> mockAwsLambdaHelperServiceDelegate.getFunctionDetails(request, false));
  }
}
