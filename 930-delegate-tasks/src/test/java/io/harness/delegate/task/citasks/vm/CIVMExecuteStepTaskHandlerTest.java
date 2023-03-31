/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.citasks.vm;

import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.SHUBHAM;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.ci.CIInitializeTaskParams;
import io.harness.delegate.beans.ci.vm.CIVmExecuteStepTaskParams;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.delegate.beans.ci.vm.runner.ExecuteStepRequest;
import io.harness.delegate.beans.ci.vm.runner.ExecuteStepRequest.ExecuteStepRequestBuilder;
import io.harness.delegate.beans.ci.vm.steps.VmBackgroundStep;
import io.harness.delegate.beans.ci.vm.steps.VmRunStep;
import io.harness.delegate.task.citasks.vm.helper.HttpHelper;
import io.harness.delegate.task.citasks.vm.helper.StepExecutionHelper;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;
import io.harness.vm.VmExecuteStepUtils;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@Slf4j
@OwnedBy(HarnessTeam.CI)
public class CIVMExecuteStepTaskHandlerTest extends CategoryTest {
  @Mock private HttpHelper httpHelper;
  @Mock private StepExecutionHelper stepExecutionHelper;
  @Mock private VmExecuteStepUtils vmExecuteStepUtils;
  @InjectMocks private io.harness.delegate.task.citasks.vm.CIVMExecuteStepTaskHandler CIVMExecuteStepTaskHandler;
  // private VmInfraInfo vmInfraInfo = VmInfraInfo.builder().poolId("test").stageRuntimeId("stage").build();
  private static final CIInitializeTaskParams.Type vmInfraInfo = CIInitializeTaskParams.Type.VM;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test()
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void executeTaskInternal() throws IOException {
    CIVmExecuteStepTaskParams params = CIVmExecuteStepTaskParams.builder()
                                           .stageRuntimeId("stage")
                                           .stepRuntimeId("step")
                                           .stepInfo(VmRunStep.builder().build())
                                           .poolId("test")
                                           .infraInfo(vmInfraInfo)
                                           .build();
    ExecuteStepRequestBuilder builder = ExecuteStepRequest.builder();
    when(vmExecuteStepUtils.convertStep(any())).thenReturn(builder);

    when(stepExecutionHelper.callRunnerForStepExecution(any()))
        .thenReturn(VmTaskExecutionResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build());
    VmTaskExecutionResponse response = CIVMExecuteStepTaskHandler.executeTaskInternal(params, "");
    assertEquals(CommandExecutionStatus.SUCCESS, response.getCommandExecutionStatus());
  }

  @Test()
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void executeTaskInternalFailure() {
    CIVmExecuteStepTaskParams params = CIVmExecuteStepTaskParams.builder()
                                           .stageRuntimeId("stage")
                                           .stepRuntimeId("test")
                                           .stepInfo(VmRunStep.builder().build())
                                           .infraInfo(vmInfraInfo)
                                           .build();
    ExecuteStepRequestBuilder builder = ExecuteStepRequest.builder();
    when(vmExecuteStepUtils.convertStep(any())).thenReturn(builder);
    when(stepExecutionHelper.callRunnerForStepExecution(any()))
        .thenReturn(VmTaskExecutionResponse.builder()
                        .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                        .errorMessage("exit code 1")
                        .build());

    VmTaskExecutionResponse response = CIVMExecuteStepTaskHandler.executeTaskInternal(params, "");
    assertEquals(CommandExecutionStatus.FAILURE, response.getCommandExecutionStatus());
  }

  @Test()
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void executeBackgroundTaskInternal() throws IOException {
    Map<String, String> portBinding = new HashMap();
    portBinding.put("6379", "6379");

    VmBackgroundStep vmBackgroundStep = VmBackgroundStep.builder()
                                            .identifier("identifier")
                                            .name("name")
                                            .command(null)
                                            .entrypoint(Collections.singletonList("ls"))
                                            .pullPolicy("always")
                                            .runAsUser("0")
                                            .privileged(true)
                                            .portBindings(portBinding)
                                            .build();

    CIVmExecuteStepTaskParams params = CIVmExecuteStepTaskParams.builder()
                                           .stageRuntimeId("stage")
                                           .stepRuntimeId("step")
                                           .stepInfo(vmBackgroundStep)
                                           .infraInfo(vmInfraInfo)
                                           .build();

    when(vmExecuteStepUtils.convertStep(any())).thenCallRealMethod();

    when(stepExecutionHelper.callRunnerForStepExecution(any()))
        .thenReturn(VmTaskExecutionResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build());
    VmTaskExecutionResponse response = CIVMExecuteStepTaskHandler.executeTaskInternal(params, "");
    assertEquals(CommandExecutionStatus.SUCCESS, response.getCommandExecutionStatus());
  }
}
