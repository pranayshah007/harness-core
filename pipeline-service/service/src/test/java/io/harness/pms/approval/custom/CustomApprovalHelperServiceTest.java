/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.approval.custom;

import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;

import static software.wings.beans.TaskType.SHELL_SCRIPT_TASK_NG;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.shell.ShellScriptTaskParametersNG;
import io.harness.engine.pms.tasks.NgDelegate2TaskExecutor;
import io.harness.iterator.PersistenceIterator;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.steps.TaskRequestsUtils;
import io.harness.steps.approval.step.ApprovalInstanceService;
import io.harness.steps.approval.step.ApprovalProgressData;
import io.harness.steps.approval.step.beans.ApprovalType;
import io.harness.steps.approval.step.custom.entities.CustomApprovalInstance;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.shellscript.ShellScriptHelperService;
import io.harness.steps.shellscript.ShellType;
import io.harness.waiter.WaitNotifyEngine;
import io.harness.yaml.core.timeout.Timeout;

import java.time.Duration;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.CDC)
@RunWith(MockitoJUnitRunner.class)
public class CustomApprovalHelperServiceTest extends CategoryTest {
  @Mock private NgDelegate2TaskExecutor ngDelegate2TaskExecutor;
  @Mock private KryoSerializer kryoSerializer;
  @Mock private WaitNotifyEngine waitNotifyEngine;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  private String publisherName;
  @Mock private PmsGitSyncHelper pmsGitSyncHelper;
  @Mock private ShellScriptHelperService shellScriptHelperService;
  @Mock private ApprovalInstanceService approvalInstanceService;
  private ILogStreamingStepClient logStreamingStepClient;
  @InjectMocks private CustomApprovalHelperServiceImpl customApprovalHelperService;
  @Mock private StepHelper stepHelper;

  @Before
  public void setup() {
    logStreamingStepClient = mock(ILogStreamingStepClient.class);
    when(logStreamingStepClientFactory.getLogStreamingStepClient(any())).thenReturn(logStreamingStepClient);
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testSuccessfullyQueueShellScriptTask() {
    CustomApprovalInstance instance = CustomApprovalInstance.builder()
                                          .shellType(ShellType.Bash)
                                          .retryInterval(ParameterField.createValueField(Timeout.fromString("1m")))
                                          .scriptTimeout(ParameterField.createValueField(Timeout.fromString("1m")))
                                          .build();
    instance.setType(ApprovalType.CUSTOM_APPROVAL);
    instance.setId("__ID__");
    instance.setAmbiance(Ambiance.newBuilder()
                             .putSetupAbstractions(SetupAbstractionKeys.accountId, "__ACCOUNT_ID__")
                             .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "__ORG__")
                             .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "__PROJ__")
                             .build());

    when(shellScriptHelperService.buildShellScriptTaskParametersNG(any(), any()))
        .thenReturn(ShellScriptTaskParametersNG.builder().build());
    when(ngDelegate2TaskExecutor.queueTask(any(), any(), eq(Duration.ofSeconds(0)))).thenReturn("__TASK_ID__");
    doNothing().when(waitNotifyEngine).progressOn(any(), any());
    try (MockedStatic<TaskRequestsUtils> aStatic = Mockito.mockStatic(TaskRequestsUtils.class)) {
      aStatic.when(() -> TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(TaskRequest.newBuilder().build());
      customApprovalHelperService.handlePollingEvent(null, instance);
      verify(approvalInstanceService, never()).resetNextIterations(any(), any());
      verify(ngDelegate2TaskExecutor).queueTask(any(), any(), eq(Duration.ofSeconds(0)));
      verify(waitNotifyEngine).waitForAllOn(any(), any(), any());
      verify(waitNotifyEngine)
          .progressOn("__ID__",
              ApprovalProgressData.builder()
                  .latestDelegateTaskId("__TASK_ID__")
                  .taskName(SHELL_SCRIPT_TASK_NG.getDisplayName())
                  .build());
    }

    // when progress update fails
    when(ngDelegate2TaskExecutor.queueTask(any(), any(), eq(Duration.ofSeconds(0)))).thenReturn("__TASK_ID__");
    doThrow(new RuntimeException()).when(waitNotifyEngine).progressOn(any(), any());
    try (MockedStatic<TaskRequestsUtils> aStatic = Mockito.mockStatic(TaskRequestsUtils.class)) {
      aStatic.when(() -> TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(TaskRequest.newBuilder().build());
      assertThatCode(() -> customApprovalHelperService.handlePollingEvent(null, instance)).doesNotThrowAnyException();
      verify(approvalInstanceService, never()).resetNextIterations(any(), any());
      verify(ngDelegate2TaskExecutor, times(2)).queueTask(any(), any(), eq(Duration.ofSeconds(0)));
      verify(waitNotifyEngine, times(2)).waitForAllOn(any(), any(), any());
      verify(waitNotifyEngine, times(2))
          .progressOn("__ID__",
              ApprovalProgressData.builder()
                  .latestDelegateTaskId("__TASK_ID__")
                  .taskName(SHELL_SCRIPT_TASK_NG.getDisplayName())
                  .build());
    }

    // when task id is empty, progress update shouldn't be called
    when(ngDelegate2TaskExecutor.queueTask(any(), any(), eq(Duration.ofSeconds(0)))).thenReturn("  ");
    try (MockedStatic<TaskRequestsUtils> aStatic = Mockito.mockStatic(TaskRequestsUtils.class)) {
      aStatic.when(() -> TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(TaskRequest.newBuilder().build());
      assertThatCode(() -> customApprovalHelperService.handlePollingEvent(null, instance)).doesNotThrowAnyException();
      verify(approvalInstanceService, never()).resetNextIterations(any(), any());
      verify(ngDelegate2TaskExecutor, times(3)).queueTask(any(), any(), eq(Duration.ofSeconds(0)));
      verify(waitNotifyEngine, times(3)).waitForAllOn(any(), any(), any());
      verifyNoMoreInteractions(waitNotifyEngine);
    }
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testUnsuccessulTaskQueue() {
    PersistenceIterator<ApprovalInstance> iterator = mock(PersistenceIterator.class);
    CustomApprovalInstance instance = CustomApprovalInstance.builder()
                                          .shellType(ShellType.Bash)
                                          .retryInterval(ParameterField.createValueField(Timeout.fromString("1m")))
                                          .scriptTimeout(ParameterField.createValueField(Timeout.fromString("1m")))
                                          .build();
    instance.setType(ApprovalType.CUSTOM_APPROVAL);
    instance.setId("__ID__");
    instance.setAmbiance(Ambiance.newBuilder()
                             .putSetupAbstractions(SetupAbstractionKeys.accountId, "__ACCOUNT_ID__")
                             .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "__ORG__")
                             .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "__PROJ__")
                             .build());

    when(shellScriptHelperService.buildShellScriptTaskParametersNG(any(), any()))
        .thenReturn(ShellScriptTaskParametersNG.builder().build());
    when(ngDelegate2TaskExecutor.queueTask(any(), any(), eq(Duration.ofSeconds(0)))).thenReturn("__TASK_ID__");
    when(ngDelegate2TaskExecutor.queueTask(any(), any(), eq(Duration.ofSeconds(0))))
        .thenThrow(new IllegalStateException());
    try (MockedStatic<TaskRequestsUtils> aStatic = Mockito.mockStatic(TaskRequestsUtils.class)) {
      aStatic.when(() -> TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(TaskRequest.newBuilder().build());
      customApprovalHelperService.handlePollingEvent(iterator, instance);
      verify(approvalInstanceService).resetNextIterations(any(), any());
      verify(iterator).wakeup();
      verify(ngDelegate2TaskExecutor).queueTask(any(), any(), eq(Duration.ofSeconds(0)));
      verify(waitNotifyEngine, never()).waitForAllOn(any(), any(), any());
    }
  }
}
