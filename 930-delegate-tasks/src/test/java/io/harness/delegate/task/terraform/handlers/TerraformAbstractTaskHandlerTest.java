/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.terraform.handlers;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.VLICA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.terraform.TFTaskType;
import io.harness.delegate.task.terraform.TerraformBaseHelper;
import io.harness.delegate.task.terraform.TerraformTaskNGParameters;
import io.harness.delegate.task.terraform.TerraformTaskNGResponse;
import io.harness.exception.InterruptedRuntimeException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class TerraformAbstractTaskHandlerTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock TerraformBaseHelper terraformBaseHelper;
  @Mock LogCallback logCallback;

  @Inject
  @Spy
  @InjectMocks
  TerraformApplyTaskHandler terraformApplyTaskHandler = new TerraformApplyTaskHandler() {
    @Override
    public TerraformTaskNGResponse executeTaskInternal(
        TerraformTaskNGParameters taskParameters, String delegateId, String taskId, LogCallback logCallback) {
      return TerraformTaskNGResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build();
    }
  };

  @Inject
  @Spy
  @InjectMocks
  TerraformApplyTaskHandler terraformApplyTaskHandlerException = new TerraformApplyTaskHandler() {
    @Override
    public TerraformTaskNGResponse executeTaskInternal(
        TerraformTaskNGParameters taskParameters, String delegateId, String taskId, LogCallback logCallback) {
      throw new InterruptedRuntimeException(new InterruptedException());
    }
  };

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testTerraformApplyTaskHandler() throws Exception {
    TerraformTaskNGParameters taskNGParameters = getTerraformTaskParameters();

    TerraformTaskNGResponse response =
        terraformApplyTaskHandler.executeTask(taskNGParameters, "delegateId", "taskId", logCallback);

    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    verify(terraformBaseHelper, times(1)).performCleanupOfTfDirs(eq(taskNGParameters), eq(logCallback));
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testTerraformApplyTaskHandlerAndExceptionIsThrown() {
    TerraformTaskNGParameters taskNGParameters = getTerraformTaskParameters();

    assertThatExceptionOfType(InterruptedRuntimeException.class)
        .isThrownBy(()
                        -> terraformApplyTaskHandlerException.executeTask(
                            taskNGParameters, "delegateId", "taskId", logCallback));

    verify(terraformBaseHelper, times(1)).performCleanupOfTfDirs(eq(taskNGParameters), eq(logCallback));
    verify(logCallback).saveExecutionLog("Interrupt received.", LogLevel.ERROR, CommandExecutionStatus.RUNNING);
  }

  private TerraformTaskNGParameters getTerraformTaskParameters() {
    return TerraformTaskNGParameters.builder()
        .accountId("accountId")
        .taskType(TFTaskType.APPLY)
        .entityId("provisionerId123")
        .build();
  }
}
